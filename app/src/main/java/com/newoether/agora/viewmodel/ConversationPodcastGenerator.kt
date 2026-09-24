package com.newoether.agora.viewmodel

import android.content.Context
import com.newoether.agora.api.HttpClient
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.diagnostics.DeveloperDiagnostics
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.ModelId
import com.newoether.agora.model.Participant
import com.newoether.agora.podcast.PersonalPodcasterPipeline
import com.newoether.agora.ui.chat.audio.FullFlowTtsEngine
import com.newoether.agora.ui.chat.audio.TtsEngineMode
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ConversationPodcastGenerator(
    private val context: Context,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
    private val providers: ProviderRegistry,
) {
    companion object {
        val DEFAULT_PODCAST_PROMPT_TEMPLATE = """
Tu es un podcasteur et journaliste d'analyse exceptionnel. Ton rôle est de synthétiser la conversation ci-dessous pour en créer un podcast broadcast audio captivant, fluide et enrichissant.
Instructions impératives :
1. Débute par une introduction chaleureuse et engageante annonçant le grand thème exploré.
2. Synthétise les échanges clés, les enseignements tirés, les questions résolues et les nuances abordées au fil du dialogue.
3. Adopte un ton oral naturel, dynamique et accessible, idéal pour une écoute audio en podcast.
4. Conclus par une synthèse inspirante et une ouverture sur les perspectives futures.
5. Produis UNIQUEMENT le texte du script à lire, sans didascalies complexes ni métadonnées.

Discussion à analyser :
%s
        """.trimIndent()
    }

    sealed interface Result {
        data class Success(
            val audioPath: String,
            val script: String,
            val htmlPath: String,
            val durationMs: Long,
        ) : Result
        data class Failure(val reason: String) : Result
    }

    private val ttsEngine by lazy { FullFlowTtsEngine(context) }
    private val podcasterPipeline by lazy { PersonalPodcasterPipeline(context, ttsEngine) }

    suspend fun generateAndPersist(conversationId: String): Result = withContext(Dispatchers.IO) {
        settings.awaitInitialLoad()
        providers.awaitInitialSync()

        val conversation = conversations.getConversation(conversationId)
            ?: return@withContext Result.Failure("Discussion introuvable")

        // 1. Extract global conversation dialogue
        val turns = mutableListOf<String>()
        var totalChars = 0
        try {
            val snapshot = conversations.getMessageTopologySnapshot(conversationId)
            val path = ConversationUiState.resolvePath(
                allMessages = snapshot.map { it.toUiChatMessageStub() },
                streamingMsg = null,
                selectedChildren = conversations.restoreBranchSelections(conversationId),
            )
            for (item in path) {
                val entity = conversations.getMessage(item.id) ?: continue
                val text = entity.text?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val prefix = if (item.participant == Participant.USER) "Utilisateur" else "Assistant"
                val snippet = text.take(800)
                turns.add("$prefix : $snippet")
                totalChars += snippet.length
                if (totalChars > 6000) break
            }
        } catch (e: Exception) {
            DebugLog.e("ConversationPodcast", "Failed to resolve conversation turns", e)
        }

        if (turns.isEmpty()) {
            return@withContext Result.Failure("La discussion ne contient aucun échange à transformer en podcast.")
        }

        val dialogueContext = turns.joinToString("\n\n")

        // 2. Resolve LLM Provider & Model for script generation
        val configuredModel = settings.podcastGenModel.value
        val prefixedModelId = configuredModel?.takeIf { it.isNotBlank() }
            ?: conversation.modelId?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel.value

        if (prefixedModelId.isBlank()) {
            return@withContext Result.Failure("Aucun modèle LLM sélectionné pour la génération du podcast.")
        }

        val providerName = providers.providerForModel(prefixedModelId)
        val activeKey = settings.awaitActiveKey(providerName)?.takeIf { it.isNotBlank() }
            ?: settings.resolveActiveKey(providerName).orEmpty()
        val provider = providers.getInstanceOrNull(providerName)
            ?: return@withContext Result.Failure("Fournisseur IA non disponible : $providerName")

        val promptTemplate = settings.podcastGenPrompt.value.ifBlank { DEFAULT_PODCAST_PROMPT_TEMPLATE }
        val promptText = if (promptTemplate.contains("%s")) {
            promptTemplate.replace("%s", dialogueContext)
        } else {
            "$promptTemplate\n\n$dialogueContext"
        }

        val userPrompt = listOf(
            ChatMessage(
                text = promptText,
                participant = Participant.USER,
                status = MessageStatus.SUCCESS,
            ),
        )

        val modelId = ModelId.parse(providers.canonicalModelId(prefixedModelId)).modelName
        val config = ProviderConfig(
            apiKey = activeKey,
            modelId = modelId,
            systemPrompt = "Tu es un podcasteur broadcast professionnel. Ne produis aucun préambule, fournis directement le texte du podcast.",
            maxContextWindow = com.newoether.agora.model.ContextBudget.MIN_TOKENS * 2,
            thinkingEnabled = false,
            baseUrl = providers.getEffectiveBaseUrl(providerName),
        )

        val requestId = UUID.randomUUID().toString()
        val requestTrace = HttpClient.RequestTrace(
            requestId = requestId,
            origin = "podcast",
            diagnosticContext = DeveloperDiagnostics.newRequestContext(
                requestId = requestId,
                conversationId = conversationId,
                runId = null,
                pass = null,
                provider = providerName,
                model = prefixedModelId,
                requestKind = "podcast",
            ),
        )

        var scriptText = ""
        var providerError: String? = null

        try {
            HttpClient.withStreamScope(scope = null, requestTrace = requestTrace) {
                provider.generateResponse(userPrompt, config).collect { event ->
                    requestTrace.recordParsedEvent(event)
                    when (event) {
                        is StreamEvent.TextChunk -> scriptText += event.text
                        is StreamEvent.Error -> providerError = event.message
                        else -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e("ConversationPodcast", "LLM Script generation error", e)
            providerError = e.message
        }

        if (scriptText.isBlank()) {
            return@withContext Result.Failure(providerError ?: "La génération du script de podcast a échoué.")
        }

        // 3. Audio Synthesis via selected TTS engine
        val ttsModeStr = settings.podcastGenTtsEngine.value
        val ttsMode = runCatching { TtsEngineMode.valueOf(ttsModeStr) }.getOrDefault(TtsEngineMode.GEMINI_CLOUD)
        val voice = settings.podcastGenVoice.value.takeIf { it.isNotBlank() }

        val podcastsDir = File(context.filesDir, "podcasts").apply { if (!exists()) mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val isMp3 = ttsMode == TtsEngineMode.OPENAI_CLOUD
        val audioExt = if (isMp3) "mp3" else "wav"
        val audioFile = File(podcastsDir, "podcast_conv_${conversationId}_${timestamp}.$audioExt")

        val audioSuccess = ttsEngine.synthesizeToFile(
            text = scriptText,
            outputFile = audioFile,
            engineMode = ttsMode,
            voice = voice,
        )

        val finalAudioFile = if (audioSuccess && audioFile.exists() && audioFile.length() > 0) audioFile else null

        // 4. Generate HTML Summary Page
        val durationMs = if (finalAudioFile != null) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(finalAudioFile.absolutePath)
                val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                dur
            } catch (_: Exception) { 60_000L }
        } else {
            60_000L
        }

        val pipelineResult = podcasterPipeline.execute(
            topic = conversation.title.ifBlank { "Synthèse de la discussion" },
            timeframe = "discussion complète",
            ttsMode = ttsMode,
            voice = voice,
        )

        Result.Success(
            audioPath = finalAudioFile?.absolutePath ?: pipelineResult.audioFile?.absolutePath.orEmpty(),
            script = scriptText,
            htmlPath = pipelineResult.htmlFile?.absolutePath.orEmpty(),
            durationMs = durationMs,
        )
    }
}
