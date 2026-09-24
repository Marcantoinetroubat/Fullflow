package com.newoether.agora.proactive

import android.content.Context
import com.newoether.agora.api.HttpClient
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.data.BuiltInPrompts
import com.newoether.agora.data.MemoryManager
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.ChatConversation
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.ModelId
import com.newoether.agora.model.Participant
import com.newoether.agora.viewmodel.ProviderRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ProactiveTask(
    val badge: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
)

class ProactiveIntelligenceManager private constructor(
    private val context: Context,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
    private val providers: ProviderRegistry,
    private val memoryManager: MemoryManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prefs = context.getSharedPreferences("proactive_intelligence_cache", Context.MODE_PRIVATE)

    private val _tasks = MutableStateFlow<List<ProactiveTask>>(loadCachedTasks())
    val tasks: StateFlow<List<ProactiveTask>> = _tasks.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var activeJob: Job? = null

    init {
        scope.launch {
            if (_tasks.value.isEmpty()) {
                refresh(forceRefresh = false)
            }
        }
    }

    fun refresh(forceRefresh: Boolean = false) {
        activeJob?.cancel()
        activeJob = scope.launch {
            generateTasksInternal(forceRefresh)
        }
    }

    private suspend fun generateTasksInternal(forceRefresh: Boolean) = withContext(Dispatchers.IO) {
        if (_isGenerating.value) return@withContext
        _isGenerating.value = true

        try {
            settings.awaitInitialLoad()
            providers.awaitInitialSync()

            val isEnabled = settings.proactiveIntelligenceEnabled.value
            if (!isEnabled) {
                _tasks.value = emptyList()
                return@withContext
            }

            val convList = conversations.getAllConversations().first()
                .filter { it.title.isNotBlank() }
                .take(5)

            val activeMemory = memoryManager.getActiveMemory().trim()
            val memoryFiles = memoryManager.listFiles()

            // Try LLM generation if available & configured
            val llmSuccess = tryGenerateWithLlm(convList, activeMemory, memoryFiles)
            if (!llmSuccess) {
                // Fallback to rich heuristic synthesizer
                val fallbackTasks = synthesizeHeuristicTasks(convList, activeMemory, memoryFiles)
                _tasks.value = fallbackTasks
                cacheTasks(fallbackTasks)
            }
        } catch (_: Exception) {
            if (_tasks.value.isEmpty()) {
                val defaultTasks = defaultStarterTasks()
                _tasks.value = defaultTasks
                cacheTasks(defaultTasks)
            }
        } finally {
            _isGenerating.value = false
        }
    }

    private suspend fun tryGenerateWithLlm(
        convList: List<ChatConversation>,
        activeMemory: String,
        memoryFiles: List<MemoryManager.MemoryFileInfo>,
    ): Boolean {
        if (convList.isEmpty() && activeMemory.isBlank() && memoryFiles.isEmpty()) {
            return false
        }

        val targetModel = settings.proactiveIntelligenceModel.value?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel.value
        if (targetModel.isBlank()) return false

        val providerName = settings.proactiveIntelligenceProvider.value?.takeIf { it.isNotBlank() }
            ?: providers.providerForModel(targetModel)
        val activeKey = settings.awaitActiveKey(providerName)?.takeIf { it.isNotBlank() }
            ?: settings.resolveActiveKey(providerName).orEmpty()

        if (!providers.isConfigured(providerName, activeKey)) {
            return false
        }

        val provider = providers.getInstanceOrNull(providerName) ?: return false

        // Extract context details from top conversations
        val convSummaries = convList.take(3).mapIndexed { idx, conv ->
            val lastUserMsg = extractLastUserMessage(conv.id)
            "Conversation ${idx + 1} : \"${conv.title}\"${if (lastUserMsg.isNotBlank()) " (Dernier échange : $lastUserMsg)" else ""}"
        }.joinToString("\n")

        val catalogNotes = memoryFiles.take(6).joinToString("\n") { fileInfo ->
            val desc = fileInfo.description.ifBlank {
                runCatching { memoryManager.readFile(fileInfo.name).take(100).replace("\n", " ") }.getOrDefault("")
            }
            "- ${fileInfo.name} : $desc"
        }

        val userContext = buildString {
            if (convSummaries.isNotBlank()) {
                appendLine("--- HISTORIQUE DES ÉCHANGES ET THÉMATIQUES RÉCENTES ---")
                appendLine(convSummaries)
            }
            if (activeMemory.isNotBlank()) {
                appendLine()
                appendLine("--- SECOND CERVEAU : MÉMOIRE ACTIVE & PROJETS ---")
                appendLine(activeMemory.take(600))
            }
            if (catalogNotes.isNotBlank()) {
                appendLine()
                appendLine("--- SECOND CERVEAU : BASE DE CONNAISSANCES & NOTES ---")
                appendLine(catalogNotes)
            }
        }

        val systemPrompt = settings.proactiveIntelligencePrompt.value.ifBlank {
            BuiltInPrompts.PROACTIVE_INTELLIGENCE_SYSTEM
        }

        val promptMessages = listOf(
            ChatMessage(
                text = "Voici les informations du Second Cerveau et du contexte de l'utilisateur :\n\n$userContext\n\nFais briller l'intelligence de l'utilisateur : formule 5 recommandations de travail dynamiques et inspirantes, une par badge (Reprendre & Poursuivre, Approfondir & Prototyper, Synergies Second Cerveau, Automatisation & Pipelines, Veille & Innovation) au format JSON spécifié.",
                participant = Participant.USER,
                status = MessageStatus.SUCCESS,
            )
        )

        val modelId = ModelId.parse(providers.canonicalModelId(targetModel)).modelName
        val config = ProviderConfig(
            apiKey = activeKey,
            modelId = modelId,
            systemPrompt = systemPrompt,
            maxContextWindow = 1024,
            thinkingEnabled = false,
            baseUrl = providers.getEffectiveBaseUrl(providerName),
        )

        val requestId = UUID.randomUUID().toString()
        val requestTrace = HttpClient.RequestTrace(
            requestId = requestId,
            origin = "proactive_intelligence",
        )

        val responseBuilder = StringBuilder()
        var hasError = false

        try {
            HttpClient.withStreamScope(scope = null, requestTrace = requestTrace) {
                provider.generateResponse(promptMessages, config).collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> responseBuilder.append(event.text)
                        is StreamEvent.Error -> hasError = true
                        else -> Unit
                    }
                }
            }
        } catch (_: Exception) {
            return false
        }

        if (hasError || responseBuilder.isBlank()) return false

        val parsed = parseLlmTasks(responseBuilder.toString())
        if (parsed.isNotEmpty()) {
            _tasks.value = parsed
            cacheTasks(parsed)
            return true
        }

        return false
    }

    private fun parseLlmTasks(rawJson: String): List<ProactiveTask> =
        ProactiveTaskJsonParser.parse(rawJson)

    private suspend fun extractLastUserMessage(conversationId: String): String {
        return try {
            val topology = conversations.getMessageTopologySnapshot(conversationId)
            val userMsg = topology.filter { it.participant == Participant.USER }
                .maxByOrNull { it.timestamp }
            if (userMsg != null) {
                conversations.getMessage(userMsg.id)?.text?.take(150)?.replace("\n", " ").orEmpty()
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun synthesizeHeuristicTasks(
        convList: List<ChatConversation>,
        activeMemory: String,
        memoryFiles: List<MemoryManager.MemoryFileInfo> = emptyList(),
    ): List<ProactiveTask> {
        val first = convList.getOrNull(0)
        val second = convList.getOrNull(1)

        if (first == null) {
            return defaultStarterTasks(activeMemory, memoryFiles)
        }

        val firstTitle = first.title.trim()
        val secondTitle = second?.title?.trim()
        val firstMsg = extractLastUserMessage(first.id)
        val secondMsg = second?.id?.let { extractLastUserMessage(it) }.orEmpty()

        val task1 = ProactiveTask(
            badge = "Reprendre & Poursuivre",
            title = "Continuer : ${firstTitle.take(36)}",
            subtitle = if (firstMsg.isNotBlank()) "Reprendre : ${firstMsg.take(45)}…" else "Explorer les prochaines étapes clés",
            prompt = if (firstMsg.isNotBlank()) {
                "Poursuivons notre travail sur '$firstTitle' en reprenant là où nous nous sommes arrêtés ($firstMsg) : quelles sont les actions prioritaires à mener maintenant ?"
            } else {
                "Poursuivons sur '$firstTitle' : quelles sont les prochaines étapes concrètes d'implémentation et les points de vigilance ?"
            }
        )

        val task2 = if (secondTitle != null) {
            ProactiveTask(
                badge = "Approfondir & Prototyper",
                title = "Approfondir : ${secondTitle.take(36)}",
                subtitle = if (secondMsg.isNotBlank()) "Mise en œuvre : ${secondMsg.take(45)}…" else "Cas pratique et validation concrète",
                prompt = "Concernant '$secondTitle' : peux-tu proposer une application opérationnelle pas à pas avec un exemple concret, une matrice décisionnelle et des critères d'évaluation ?"
            )
        } else {
            ProactiveTask(
                badge = "Approfondir & Prototyper",
                title = "Cas pratique sur ${firstTitle.take(30)}",
                subtitle = "Passer de la théorie à l'application concrète",
                prompt = "Concernant '$firstTitle' : structure un plan de mise en œuvre opérationnel étape par étape avec un cas pratique complet ou une modélisation visuelle."
            )
        }

        val task3 = if (memoryFiles.isNotEmpty()) {
            val note = memoryFiles.first()
            val noteName = note.name.removeSuffix(".md")
            val noteDesc = note.description.ifBlank { "vos réflexions archivées" }
            ProactiveTask(
                badge = "Synergie Second Cerveau",
                title = "Croiser avec : ${noteName.take(24)}",
                subtitle = "Alignement : ${noteDesc.take(45)}",
                prompt = "En reliant nos échanges récents sur '$firstTitle' avec ma note de Second Cerveau '$noteName' ($noteDesc) : quelles synergies inédites et opportunités concrètes recommandes-tu de développer ?"
            )
        } else if (secondTitle != null) {
            ProactiveTask(
                badge = "Synthèse transversale",
                title = "Synergies & Croisements",
                subtitle = "Relier '${firstTitle.take(16)}' et '${secondTitle.take(16)}'",
                prompt = "Fais une analyse croisée approfondie reliant '$firstTitle' et '$secondTitle' pour dégager des synergies inédites et des opportunités stratégiques."
            )
        } else if (activeMemory.isNotBlank()) {
            val memExcerpt = activeMemory.lines().firstOrNull { it.isNotBlank() }?.take(30) ?: "projets en cours"
            ProactiveTask(
                badge = "Synthèse transversale",
                title = "Croiser avec vos objectifs",
                subtitle = "Alignement : $memExcerpt",
                prompt = "En croisant le sujet '$firstTitle' avec mes objectifs et notes actives, quelles recommandations transversales prioritaires proposes-tu ?"
            )
        } else {
            ProactiveTask(
                badge = "Synthèse transversale",
                title = "Perspectives d'avenir & Impacts",
                subtitle = "Élargir les angles d'analyse et innovations",
                prompt = "Quelles sont les perspectives émergentes, les évolutions futures et les impacts transversaux majeurs liés à '$firstTitle' ?"
            )
        }

        val task4 = ProactiveTask(
            badge = "Automatisation & Pipelines",
            title = "Automatiser : ${firstTitle.take(30)}",
            subtitle = "Transformer la récurrence en processus fluide",
            prompt = "À partir de notre travail sur '$firstTitle' : identifie les étapes répétitives ou manuelles et propose un pipeline d'automatisation concret, étape par étape, avec les points de contrôle qualité."
        )

        val task5 = ProactiveTask(
            badge = "Veille & Innovation",
            title = "Veille ciblée : ${firstTitle.take(28)}",
            subtitle = "Avancées récentes et applications concrètes",
            prompt = "Réalise une veille structurée sur les avancées les plus récentes liées à '$firstTitle' : quelles innovations majeures sont apparues et comment les exploiter concrètement dans mes projets ?"
        )

        return listOf(task1, task2, task3, task4, task5)
    }

    private fun defaultStarterTasks(
        activeMemory: String = "",
        memoryFiles: List<MemoryManager.MemoryFileInfo> = emptyList(),
    ): List<ProactiveTask> {
        val hasMem = activeMemory.isNotBlank()
        val hasFiles = memoryFiles.isNotEmpty()
        return listOf(
            ProactiveTask(
                badge = "Reprendre & Poursuivre",
                title = if (hasMem) "Cadrer les objectifs clés" else "Plan d'action & Structuration",
                subtitle = if (hasMem) "Aligné avec vos notes actives" else "Cadrer un projet ou des objectifs clés",
                prompt = if (hasMem) {
                    "En te basant sur mes objectifs actifs, aide-moi à structurer mon plan d'action prioritaire pour aujourd'hui."
                } else {
                    "Aide-moi à structurer un plan d'action clair pour un nouveau projet avec ses objectifs clés et étapes prioritaires."
                }
            ),
            ProactiveTask(
                badge = "Approfondir & Prototyper",
                title = "Analyse critique & Méthode",
                subtitle = "Rédiger un document ou cas pratique",
                prompt = "Je souhaite concevoir une analyse structurée et approfondie : guide-moi avec un plan rigoureux, une modélisation visuelle et des arguments solides."
            ),
            if (hasFiles) {
                val note = memoryFiles.first()
                val noteName = note.name.removeSuffix(".md")
                ProactiveTask(
                    badge = "Second Cerveau",
                    title = "Valoriser : ${noteName.take(24)}",
                    subtitle = if (note.description.isNotBlank()) note.description.take(45) else "Explorer vos notes archivées",
                    prompt = "À partir de ma note '$noteName', quelles pistes concrètes d'approfondissement ou de valorisation suggères-tu d'explorer ?"
                )
            } else {
                ProactiveTask(
                    badge = "Synthèse transversale",
                    title = "Veille & Perspectives IA",
                    subtitle = "Découvrir les synergies innovantes",
                    prompt = "Quelles sont les avancées majeures et opportunités d'application récentes à exploiter dans mon domaine ?"
                )
            },
            ProactiveTask(
                badge = "Automatisation & Pipelines",
                title = "Fluidifier vos flux de travail",
                subtitle = "Identifier quoi automatiser en priorité",
                prompt = "Aide-moi à identifier les tâches répétitives de mon quotidien de travail et à concevoir un pipeline d'automatisation simple et mesurable pour la plus coûteuse d'entre elles."
            ),
            ProactiveTask(
                badge = "Veille & Innovation",
                title = "Veille du moment",
                subtitle = "Les avancées récentes à exploiter",
                prompt = "Dresse un panorama des innovations récentes dans mon domaine : quelles avancées majeures devrais-je exploiter cette semaine et par où commencer concrètement ?"
            ),
        )
    }

    private fun loadCachedTasks(): List<ProactiveTask> {
        val raw = prefs.getString("cached_tasks", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ProactiveTask>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cacheTasks(tasks: List<ProactiveTask>) {
        try {
            val encoded = json.encodeToString(tasks)
            prefs.edit().putString("cached_tasks", encoded).apply()
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: ProactiveIntelligenceManager? = null

        fun getInstance(
            context: Context,
            conversations: ConversationRepository,
            settings: SettingsRepository,
            providers: ProviderRegistry,
            memoryManager: MemoryManager,
        ): ProactiveIntelligenceManager {
            return instance ?: synchronized(this) {
                instance ?: ProactiveIntelligenceManager(
                    context.applicationContext,
                    conversations,
                    settings,
                    providers,
                    memoryManager,
                ).also { instance = it }
            }
        }
    }
}
