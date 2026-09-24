package com.newoether.agora.wand

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.newoether.agora.data.SkillManager
import com.newoether.agora.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-scoped coordinator for the Magic Wand flow: chosen type + skills →
 * cloud compilation → preview state (original / optimized / declared
 * connections & skills). UI observes plain Compose state; the caller decides
 * between "send optimized" and "keep original" — never a silent substitution.
 */
object WandController {

    /** Bound on MCP connection pre-fetch so a hung server can't freeze the wand UI. */
    private const val CONNECTION_PREFETCH_TIMEOUT_MS = 8_000L

    /** Hard ceiling for the whole compile phase (blocking HTTP doesn't cancel eagerly). */
    private const val COMPILE_OVERALL_TIMEOUT_MS = 30_000L

    /** Default connection mapping per type (seed §8 of the spec; editable later). */
    val DEFAULT_STRUCTURES: List<PromptStructure> = listOf(
        PromptStructure(
            id = "brainstorming",
            type = RequestType.BRAINSTORMING,
            name = "Brainstorming structuré",
            template = "Propose 8 idées variées et hiérarchisées autour du sujet, " +
                "avec pour chacune l'intérêt, le risque principal et un premier pas concret.",
            defaultConnections = listOf(ConnectionId.WEB_SERPER),
        ),
        PromptStructure(
            id = "recherche_veille",
            type = RequestType.RECHERCHE_VEILLE,
            name = "Veille sourcée",
            template = "Produis une veille factuelle, datée et sourcée, " +
                "en distinguant faits établis, estimations et opinions.",
            defaultConnections = listOf(
                ConnectionId.NEWS,
                ConnectionId.WEB_SERPER,
                ConnectionId.YOUTUBE,
            ),
        ),
        PromptStructure(
            id = "synthese",
            type = RequestType.SYNTHESE,
            name = "Synthèse structurée",
            template = "Résume fidèlement le contenu fourni en points clés hiérarchisés, " +
                "sans rien ajouter au document source.",
            defaultConnections = listOf(ConnectionId.DRIVE, ConnectionId.DOCS),
        ),
        PromptStructure(
            id = "arbitrage",
            type = RequestType.ARBITRAGE,
            name = "Grille d'arbitrage",
            template = "Compare les options sur une grille de critères pondérés, " +
                "explicite les hypothèses, puis recommande avec le niveau de confiance.",
            defaultConnections = emptyList(),
        ),
        PromptStructure(
            id = "cahier_des_charges",
            type = RequestType.CAHIER_DES_CHARGES,
            name = "Cahier des charges",
            template = "Rédige un cahier des charges complet : contexte, objectifs, périmètre, " +
                "exigences, contraintes, critères d'acceptation, planning.",
            defaultConnections = listOf(ConnectionId.GITHUB, ConnectionId.DOCS),
        ),
        PromptStructure(
            id = "audit",
            type = RequestType.AUDIT,
            name = "Audit et diagnostic",
            template = "Diagnostique l'existant, identifie les écarts et les risques, " +
                "puis propose des corrections priorisées.",
            defaultConnections = listOf(ConnectionId.GITHUB, ConnectionId.DRIVE),
        ),
        PromptStructure(
            id = "plan_action",
            type = RequestType.PLAN_ACTION,
            name = "Plan d'action",
            template = "Décompose l'objectif en étapes ordonnées, " +
                "avec responsables, échéances et jalons vérifiables.",
            defaultConnections = emptyList(),
        ),
        PromptStructure(
            id = "traduction_accessible",
            type = RequestType.TRADUCTION_ACCESSIBLE,
            name = "Traduction accessible",
            template = "Traduis en français clair et accessible, " +
                "en conservant le sens et en expliquant les termes techniques.",
            defaultConnections = emptyList(),
        ),
        PromptStructure(
            id = "redaction",
            type = RequestType.REDACTION,
            name = "Rédaction et communication",
            template = "Rédige un texte clair, adapté au destinataire et au canal, " +
                "avec objet, corps structuré et appel à l'action.",
            defaultConnections = listOf(ConnectionId.GMAIL, ConnectionId.CALENDAR),
        ),
        PromptStructure(
            id = "analyse_financiere",
            type = RequestType.ANALYSE_FINANCIERE,
            name = "Analyse financière",
            template = "Chiffre les coûts, revenus et scénarios, " +
                "explicite les hypothèses, puis conclus par une recommandation argumentée.",
            defaultConnections = listOf(ConnectionId.GOOGLE_FINANCE),
        ),
        PromptStructure(
            id = "reformulation",
            type = RequestType.REFORMULATION,
            name = "Reformulation & amplification",
            template = "Reformule le texte fourni en un prompt optimal, clair et actionnable : " +
                "précise l'intention, le livrable attendu (analyse structurée, mindmap Mermaid, " +
                "tableau de bord graphique, plan d'action, slides), le niveau de détail et les " +
                "contraintes, sans rien perdre ni ajouter au sens original.",
            defaultConnections = emptyList(),
        ),
    )

    data class WandPreview(
        val rawText: String,
        val optimized: String,
        val ambiguities: List<String>,
        val structureName: String,
        val connections: List<ConnectionId>,
        val skills: List<String>,
        val costUsd: Double,
        val modelUsed: String,
    )

    sealed interface State {
        data object Idle : State
        data object Compiling : State
        data class Ready(val preview: WandPreview) : State
    }

    var state by androidx.compose.runtime.mutableStateOf<State>(State.Idle)
        private set

    /** Error message surfaced once (toast) when compilation fails. */
    var errorMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    var selectedType by androidx.compose.runtime.mutableStateOf(RequestType.BRAINSTORMING)
    var selectedSkillNames by androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())

    /** Whitelist consumed by the next generation (set at "Envoyer l'optimisé"). */
    @Volatile
    private var pendingConnections: Set<String> = emptySet()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var compiler: WandCompiler? = null
    private var skillManager: SkillManager? = null
    private var connectionTools: WandConnectionToolProvider? = null
    private var settingsStore: WandSettingsStore? = null

    /** Bound once from the UI layer with the process-scoped singletons. */
    fun bind(
        settings: SettingsRepository,
        skills: SkillManager,
        registry: com.newoether.agora.mcp.McpRegistry? = null,
        store: WandSettingsStore? = null,
    ) {
        if (compiler == null) compiler = WandCompiler(settings)
        if (skillManager == null) skillManager = skills
        if (registry != null && connectionTools == null) {
            connectionTools = WandConnectionToolProvider(registry)
        }
        if (store != null && settingsStore == null) settingsStore = store
    }

    /** Read + clear the session whitelist for the deep model (single-shot per send). */
    fun consumePendingConnections(): Set<String> {
        val current = pendingConnections
        pendingConnections = emptySet()
        return current
    }

    /** Preview confirmed: declare the checked connections to the next generation. */
    fun declareConnectionsForNextSend(connections: List<ConnectionId>) {
        pendingConnections = connections.map { it.name }.toSet()
    }

    fun availableSkills(): List<SkillManager.SkillFileInfo> =
        skillManager?.listFiles().orEmpty()

    /** Live wiring status for the preview truth label; empty when MCP is not bound. */
    fun wiringStatus(connections: List<ConnectionId>): Map<ConnectionId, Boolean> =
        connectionTools?.wiringStatus(connections).orEmpty()

    fun toggleSkill(name: String) {
        selectedSkillNames = if (name in selectedSkillNames) {
            selectedSkillNames - name
        } else {
            selectedSkillNames + name
        }
    }

    fun reset() {
        state = State.Idle
        errorMessage = null
    }

    fun dismissError() {
        errorMessage = null
    }

    fun compile(rawText: String) {
        val wand = compiler ?: run {
            setError("Baguette non initialisée.")
            return
        }
        if (rawText.isBlank()) return
        setCompiling()
        scope.launch(Dispatchers.Default) {
            val structures = settingsStore?.structures?.first()
                ?: DEFAULT_STRUCTURES
            val structure: PromptStructure = structures.firstOrNull { it.type == selectedType }
                ?: DEFAULT_STRUCTURES.firstOrNull { it.type == selectedType }
                ?: structures.first()
            val persona = settingsStore?.personaBrief?.first()?.content.orEmpty()
            val wandModel = settingsStore?.wandModel?.first().orEmpty()
            val wandProvider = settingsStore?.wandProvider?.first()?.orEmpty()
            val magicPrompt = settingsStore?.magicPrompt?.first()?.orEmpty()
            val skillContents = selectedSkillNames.mapNotNull { name ->
                try {
                    skillManager?.readFile(name)
                } catch (_: Exception) {
                    null
                }
            }
            // Pre-fetch context from checked, wired connections (Zapier MCP, §4.4-a).
            // Bounded: a hung MCP server must never freeze « Baguette en cours » forever.
            val connectionContext = try {
                kotlinx.coroutines.withTimeoutOrNull(CONNECTION_PREFETCH_TIMEOUT_MS) {
                    connectionTools?.prefetchContext(structure.defaultConnections, rawText)
                }.orEmpty()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (_: Exception) {
                ""
            }
            // Hard ceiling around the whole compile: blocking HTTP calls only cooperate
            // with cancellation on return, so we cap total wait and fall back cleanly.
            val compiled = try {
                kotlinx.coroutines.withTimeoutOrNull(COMPILE_OVERALL_TIMEOUT_MS) {
                    wand.compile(
                        rawText = rawText,
                        structure = structure,
                        skillContents = skillContents,
                        personaBrief = persona,
                        connectionContext = connectionContext,
                        modelOverride = wandModel.ifBlank { null },
                        providerOverride = wandProvider?.ifBlank { null },
                        customMagicPrompt = magicPrompt?.ifBlank { null },
                    )
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (_: Exception) {
                null
            }
            if (compiled == null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    setError("Baguette indisponible — votre texte est conservé tel quel.")
                }
                return@launch
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                setReady(
                WandPreview(
                    rawText = rawText,
                    optimized = compiled.optimized,
                    ambiguities = compiled.ambiguities,
                    structureName = structure.name,
                    connections = structure.defaultConnections,
                    skills = selectedSkillNames.toList(),
                    costUsd = compiled.costUsd,
                    modelUsed = compiled.modelUsed,
                )
                )
            }
        }
    }

    private fun setReady(preview: WandPreview) {
        state = State.Ready(preview)
    }

    private fun setCompiling() {
        state = State.Compiling
    }

    private fun setError(message: String) {
        errorMessage = message
        state = State.Idle
    }
}
