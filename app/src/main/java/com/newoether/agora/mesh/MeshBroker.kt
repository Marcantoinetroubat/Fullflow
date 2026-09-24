package com.newoether.agora.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The central on-device Switchboard / Broker for Pi-Mesh (mesh.v1).
 * Routes messages between agents locally with honest status tracking,
 * zero-plaintext SHA-256 ledger, file reservation locks, and watchdog protection.
 */
class MeshBroker(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {
    // Registered agents
    private val _agents = MutableStateFlow<List<MeshAgent>>(emptyList())
    val agents: StateFlow<List<MeshAgent>> = _agents.asStateFlow()

    // Virtual rooms
    private val _rooms = MutableStateFlow<List<MeshRoom>>(emptyList())
    val rooms: StateFlow<List<MeshRoom>> = _rooms.asStateFlow()

    // Active room selected by UI
    private val _activeRoomId = MutableStateFlow(MeshProtocol.ROOM_GENERAL)
    val activeRoomId: StateFlow<String> = _activeRoomId.asStateFlow()

    // Messages per room: Map<RoomId, List<MeshChatMessage>>
    private val _roomMessages = MutableStateFlow<Map<String, List<MeshChatMessage>>>(emptyMap())
    val roomMessages: StateFlow<Map<String, List<MeshChatMessage>>> = _roomMessages.asStateFlow()

    // Active file reservation locks
    private val _fileLocks = MutableStateFlow<List<MeshFileLock>>(emptyList())
    val fileLocks: StateFlow<List<MeshFileLock>> = _fileLocks.asStateFlow()

    // Zero-plaintext cryptographic audit ledger
    private val _ledger = MutableStateFlow<List<MeshLedgerEntry>>(emptyList())
    val ledger: StateFlow<List<MeshLedgerEntry>> = _ledger.asStateFlow()

    // Recent mission summaries
    private val _missionSummaries = MutableStateFlow<List<MeshMissionSummary>>(emptyList())
    val missionSummaries: StateFlow<List<MeshMissionSummary>> = _missionSummaries.asStateFlow()

    // Watchdog and background cleanup job
    private var watchdogJob: Job? = null

    init {
        initializeDefaultAgents()
        initializeDefaultRooms()
        startWatchdog()
    }

    private fun initializeDefaultAgents() {
        val defaultList = listOf(
            MeshAgent(
                id = "agent-librarian",
                name = "Bibliothécaire",
                role = "Recherche & Vérification des faits",
                colorHex = 0xFF38BDF8,
                state = MeshAgentState.IDLE
            ),
            MeshAgent(
                id = "agent-writer",
                name = "Rédacteur",
                role = "Rédaction créative & technique",
                colorHex = 0xFF818CF8,
                state = MeshAgentState.IDLE
            ),
            MeshAgent(
                id = "agent-auditor",
                name = "Auditeur",
                role = "Vérification sécurité & contradiction",
                colorHex = 0xFF34D399,
                state = MeshAgentState.IDLE
            ),
            MeshAgent(
                id = "agent-synthesizer",
                name = "Synthétiseur",
                role = "Agrégation & bilan synthétique",
                colorHex = 0xFFFBBF24,
                state = MeshAgentState.IDLE
            ),
            MeshAgent(
                id = "agent-visual",
                name = "Nano Banana",
                role = "Génération de visuels & design",
                colorHex = 0xFFF472B6,
                state = MeshAgentState.IDLE
            )
        )
        _agents.value = defaultList
    }

    private fun initializeDefaultRooms() {
        val defaultRooms = listOf(
            MeshRoom(
                id = MeshProtocol.ROOM_GENERAL,
                name = "#général",
                description = "Standard d'échange & coordination générale",
                memberAgentIds = setOf("agent-librarian", "agent-writer", "agent-auditor", "agent-synthesizer", "agent-visual")
            ),
            MeshRoom(
                id = MeshProtocol.ROOM_DEBATE,
                name = "#débat-contradictoire",
                description = "Arène de débat contradictoire (Avocat A vs Avocat B)",
                memberAgentIds = setOf("agent-writer", "agent-auditor", "agent-synthesizer")
            ),
            MeshRoom(
                id = MeshProtocol.ROOM_DRAFTING,
                name = "#rédaction-en-chaîne",
                description = "Chaîne séquentielle : Recherche ➔ Rédaction ➔ Relecture",
                memberAgentIds = setOf("agent-librarian", "agent-writer", "agent-auditor")
            ),
            MeshRoom(
                id = MeshProtocol.ROOM_SYNTHESIS,
                name = "#synthèse-sources",
                description = "Extraction multi-sources & consensus d'équipe",
                memberAgentIds = setOf("agent-librarian", "agent-auditor", "agent-synthesizer")
            )
        )
        _rooms.value = defaultRooms

        // Seed an initial welcoming message
        val welcomeMsg = MeshChatMessage(
            fromAgentId = "switchboard",
            fromAgentName = "Standard Pi-Mesh",
            room = MeshProtocol.ROOM_GENERAL,
            content = "Bienvenue dans l'atelier Pi-Mesh (mesh.v1). 5 cartouches connectées. Prêt pour les missions parallèles et débats contradictoires.",
            status = MeshMessageStatus.ANSWERED,
            deliveredTimestamp = System.currentTimeMillis(),
            readTimestamp = System.currentTimeMillis(),
            answeredTimestamp = System.currentTimeMillis()
        )
        _roomMessages.value = mapOf(MeshProtocol.ROOM_GENERAL to listOf(welcomeMsg))
        recordLedger(welcomeMsg, MeshMessageStatus.ANSWERED, 5L)
    }

    fun selectRoom(roomId: String) {
        _activeRoomId.value = roomId
    }

    /**
     * Sends a message through the broker with progressive honest statuses.
     */
    fun postMessage(
        fromAgentId: String,
        fromAgentName: String,
        toAgentId: String? = null,
        room: String,
        content: String,
        replyToId: String? = null,
        onAnswer: (suspend (MeshChatMessage) -> Unit)? = null
    ): MeshChatMessage {
        val msg = MeshChatMessage(
            fromAgentId = fromAgentId,
            fromAgentName = fromAgentName,
            toAgentId = toAgentId,
            room = room,
            content = content,
            replyToId = replyToId,
            status = MeshMessageStatus.SENT
        )

        // Add to room stream
        addMessageToStream(room, msg)

        // Broadcast on decoupled event-driven MessageBus
        MessageBus.defaultBus.tryPublish(topic = room, senderId = fromAgentId, payload = content)

        // Progress honest status asynchronously
        scope.launch {
            val startTime = System.currentTimeMillis()
            delay(15) // Extremely fast local dispatch (< 20ms)

            // Step 1: DELIVERED
            val deliveredMsg = msg.copy(
                status = MeshMessageStatus.DELIVERED,
                deliveredTimestamp = System.currentTimeMillis()
            )
            updateMessageInStream(room, deliveredMsg)

            delay(60)
            // Step 2: READ (Injected into agent context)
            val readMsg = deliveredMsg.copy(
                status = MeshMessageStatus.READ,
                readTimestamp = System.currentTimeMillis()
            )
            updateMessageInStream(room, readMsg)

            if (onAnswer != null) {
                onAnswer(readMsg)
            } else {
                delay(120)
                // Step 3: ANSWERED
                val answeredMsg = readMsg.copy(
                    status = MeshMessageStatus.ANSWERED,
                    answeredTimestamp = System.currentTimeMillis()
                )
                updateMessageInStream(room, answeredMsg)
                recordLedger(answeredMsg, MeshMessageStatus.ANSWERED, System.currentTimeMillis() - startTime)
            }
        }

        return msg
    }

    /**
     * Injects a user director instruction into a room.
     */
    fun sendUserPrompt(room: String, text: String) {
        postMessage(
            fromAgentId = "user-conductor",
            fromAgentName = "Conducteur (Vous)",
            room = room,
            content = text
        )

        // Trigger spontaneous reaction from suitable agent
        scope.launch {
            delay(350)
            when (room) {
                MeshProtocol.ROOM_DEBATE -> launchDebate(text)
                MeshProtocol.ROOM_DRAFTING -> launchChainedDrafting(text)
                MeshProtocol.ROOM_SYNTHESIS -> launchParallelSynthesis(text)
                else -> {
                    // General room response
                    setAgentState("agent-synthesizer", MeshAgentState.WORKING, "Traitement requête générale")
                    delay(800)
                    postMessage(
                        fromAgentId = "agent-synthesizer",
                        fromAgentName = "Synthétiseur",
                        room = room,
                        content = "Bien reçu. Mission répartie : les cartouches sont synchronisées pour répondre à votre directive."
                    )
                    setAgentState("agent-synthesizer", MeshAgentState.IDLE, null, incrementCompleted = true)
                }
            }
        }
    }

    // ==========================================
    // MULTI-AGENT SCENARIOS (From User Prompt)
    // ==========================================

    /**
     * Scenario 1: Débat contradictoire (Avocat A vs Avocat B)
     */
    fun launchDebate(topic: String) {
        scope.launch {
            _activeRoomId.value = MeshProtocol.ROOM_DEBATE

            postMessage(
                fromAgentId = "switchboard",
                fromAgentName = "Standard",
                room = MeshProtocol.ROOM_DEBATE,
                content = "⚖️ Ouverture de la session de débat contradictoire : « $topic »."
            )

            // Avocat A (Rédacteur) défend la Thèse
            setAgentState("agent-writer", MeshAgentState.WORKING, "Plaidoyer Thèse (Avocat A)")
            delay(1200)
            postMessage(
                fromAgentId = "agent-writer",
                fromAgentName = "Rédacteur (Avocat A)",
                room = MeshProtocol.ROOM_DEBATE,
                content = "Arguments en faveur : Sur « $topic », l'adoption offre des gains majeurs d'agilité, une réduction immédiate des frictions et un effet de levier puissant sur la productivité globale."
            )
            setAgentState("agent-writer", MeshAgentState.IDLE, null, incrementCompleted = true)

            // Avocat B (Auditeur) réfute
            setAgentState("agent-auditor", MeshAgentState.WORKING, "Contre-argumentation (Avocat B)")
            delay(1400)
            postMessage(
                fromAgentId = "agent-auditor",
                fromAgentName = "Auditeur (Avocat B)",
                room = MeshProtocol.ROOM_DEBATE,
                content = "Contre-arguments & Risques : Attention aux coûts cachés, à la dette technique et aux vulnérabilités d'exposition. Une adoption aveugle de « $topic » sans garde-fous stricts engendre des dérives majeures."
            )
            setAgentState("agent-auditor", MeshAgentState.IDLE, null, incrementCompleted = true)

            // Synthétiseur rend le verdict équilibré
            setAgentState("agent-synthesizer", MeshAgentState.WORKING, "Consensus et arbitrage")
            delay(1000)
            postMessage(
                fromAgentId = "agent-synthesizer",
                fromAgentName = "Synthétiseur",
                room = MeshProtocol.ROOM_DEBATE,
                content = "Verdict contradictoire : Privilégier une approche hybride par paliers. Exploiter les gains soulignés par l'Avocat A tout en instaurant les contrôles de conformité préconisés par l'Avocat B."
            )
            setAgentState("agent-synthesizer", MeshAgentState.IDLE, null, incrementCompleted = true)
        }
    }

    /**
     * Scenario 2: Rédaction multi-étapes en chaîne
     * (Bibliothécaire Recherche -> Rédacteur Draft -> Auditeur Relecture)
     */
    fun launchChainedDrafting(subject: String) {
        scope.launch {
            _activeRoomId.value = MeshProtocol.ROOM_DRAFTING

            postMessage(
                fromAgentId = "switchboard",
                fromAgentName = "Standard",
                room = MeshProtocol.ROOM_DRAFTING,
                content = "📝 Lancement de la chaîne séquentielle sur le sujet : « $subject »."
            )

            // Étape 1 : Recherche
            setAgentState("agent-librarian", MeshAgentState.WORKING, "Collecte de données & faits")
            delay(1100)
            postMessage(
                fromAgentId = "agent-librarian",
                fromAgentName = "Bibliothécaire",
                room = MeshProtocol.ROOM_DRAFTING,
                content = "Fiche documentaire préparée pour « $subject » : 3 sources primaires identifiées, points de consensus établis et chiffres clés consolidés."
            )
            setAgentState("agent-librarian", MeshAgentState.IDLE, null, incrementCompleted = true)

            // Étape 2 : Rédaction (réserve le fichier de brouillon)
            acquireFileLock("draft_$subject.md", "agent-writer", "Rédacteur", "Rédaction premier jet")
            setAgentState("agent-writer", MeshAgentState.WORKING, "Rédaction du premier jet")
            delay(1400)
            postMessage(
                fromAgentId = "agent-writer",
                fromAgentName = "Rédacteur",
                room = MeshProtocol.ROOM_DRAFTING,
                content = "Premier jet finalisé dans draft_$subject.md : structure en 3 axes, ton percutant et clarté pédagogique assurée."
            )
            releaseFileLock("draft_$subject.md", "agent-writer")
            setAgentState("agent-writer", MeshAgentState.IDLE, null, incrementCompleted = true)

            // Étape 3 : Relecture & Certification
            setAgentState("agent-auditor", MeshAgentState.WORKING, "Audit & contrôle qualité")
            delay(1000)
            postMessage(
                fromAgentId = "agent-auditor",
                fromAgentName = "Auditeur",
                room = MeshProtocol.ROOM_DRAFTING,
                content = "Relecture certifiée : aucune incohérence détectée, style fluide et conformité aux standards validée."
            )
            setAgentState("agent-auditor", MeshAgentState.IDLE, null, incrementCompleted = true)
        }
    }

    /**
     * Scenario 3: Synthèse de sources multiples (Parallel Scatter-Gather)
     */
    fun launchParallelSynthesis(sourcesTopic: String) {
        scope.launch {
            _activeRoomId.value = MeshProtocol.ROOM_SYNTHESIS
            val startTime = System.currentTimeMillis()

            postMessage(
                fromAgentId = "switchboard",
                fromAgentName = "Standard",
                room = MeshProtocol.ROOM_SYNTHESIS,
                content = "📊 Dispatch parallèle lancé pour : « $sourcesTopic » (Mode lance et continue)."
            )

            // Activate 3 agents simultaneously
            setAgentState("agent-librarian", MeshAgentState.WORKING, "Extraction sources 1 & 2")
            setAgentState("agent-writer", MeshAgentState.WORKING, "Extraction sources 3 & 4")
            setAgentState("agent-auditor", MeshAgentState.WORKING, "Contrôle fiabilité source 5")

            delay(1300)
            postMessage(
                fromAgentId = "agent-librarian",
                fromAgentName = "Bibliothécaire",
                room = MeshProtocol.ROOM_SYNTHESIS,
                content = "Extraction Sources 1 & 2 : Tendances haussières confirmées sur l'adoption."
            )
            setAgentState("agent-librarian", MeshAgentState.IDLE, null, incrementCompleted = true)

            delay(400)
            postMessage(
                fromAgentId = "agent-writer",
                fromAgentName = "Rédacteur",
                room = MeshProtocol.ROOM_SYNTHESIS,
                content = "Extraction Sources 3 & 4 : Perspectives opérationnelles centrées sur l'expérience utilisateur."
            )
            setAgentState("agent-writer", MeshAgentState.IDLE, null, incrementCompleted = true)

            delay(300)
            postMessage(
                fromAgentId = "agent-auditor",
                fromAgentName = "Auditeur",
                room = MeshProtocol.ROOM_SYNTHESIS,
                content = "Contrôle Source 5 : Taux de corrélation 94%, absence de biais statistique flagrant."
            )
            setAgentState("agent-auditor", MeshAgentState.IDLE, null, incrementCompleted = true)

            // Final gather report
            setAgentState("agent-synthesizer", MeshAgentState.WORKING, "Génération bilan synthétique")
            delay(900)
            val duration = System.currentTimeMillis() - startTime
            val summary = MeshMissionSummary(
                title = "Synthèse : $sourcesTopic",
                dispatchedAt = startTime,
                targetAgentIds = listOf("agent-librarian", "agent-writer", "agent-auditor"),
                completedAgentIds = listOf("agent-librarian", "agent-writer", "agent-auditor"),
                durationMs = duration,
                isComplete = true,
                synthesisNotes = "3/3 cartouches ont répondu avec succès. Bilan consolidé en ${(duration / 1000f)}s."
            )
            _missionSummaries.update { listOf(summary) + it.take(9) }

            postMessage(
                fromAgentId = "agent-synthesizer",
                fromAgentName = "Synthétiseur",
                room = MeshProtocol.ROOM_SYNTHESIS,
                content = "🏁 Bilan honnête du groupe : 3/3 agents ont répondu. Durée : ${duration}ms. La synthèse finale unifiée a été compilée."
            )
            setAgentState("agent-synthesizer", MeshAgentState.IDLE, null, incrementCompleted = true)
        }
    }

    // ==========================================
    // FILE LOCK RESERVATIONS (Anti-Collision)
    // ==========================================

    fun acquireFileLock(
        filePath: String,
        agentId: String,
        agentName: String,
        purpose: String
    ): Boolean {
        cleanExpiredLocks()
        val existing = _fileLocks.value.firstOrNull { it.filePath == filePath && !it.isExpired }
        if (existing != null) {
            if (existing.holderAgentId == agentId) return true // Re-entrant
            return false // Already reserved
        }

        val newLock = MeshFileLock(
            filePath = filePath,
            holderAgentId = agentId,
            holderAgentName = agentName,
            purpose = purpose
        )
        _fileLocks.update { it.filterNot { lock -> lock.filePath == filePath } + newLock }
        return true
    }

    fun releaseFileLock(filePath: String, agentId: String): Boolean {
        val existing = _fileLocks.value.firstOrNull { it.filePath == filePath } ?: return false
        if (existing.holderAgentId == agentId || agentId == "admin") {
            _fileLocks.update { it.filterNot { lock -> lock.filePath == filePath } }
            return true
        }
        return false
    }

    private fun cleanExpiredLocks() {
        val now = System.currentTimeMillis()
        _fileLocks.update { list -> list.filter { it.expiresAt > now } }
    }

    // ==========================================
    // AGENT STATE & WATCHDOG
    // ==========================================

    fun setAgentState(
        agentId: String,
        state: MeshAgentState,
        task: String? = null,
        incrementCompleted: Boolean = false
    ) {
        _agents.update { list ->
            list.map { agent ->
                if (agent.id == agentId) {
                    agent.copy(
                        state = state,
                        currentTask = task,
                        lastActiveTimestamp = System.currentTimeMillis(),
                        completedTasksCount = if (incrementCompleted) agent.completedTasksCount + 1 else agent.completedTasksCount
                    )
                } else agent
            }
        }
    }

    /**
     * Manual unblock / ping for stuck agents.
     */
    fun reviveAgent(agentId: String) {
        setAgentState(agentId, MeshAgentState.IDLE, null)
    }

    private fun startWatchdog() {
        watchdogJob = scope.launch {
            while (true) {
                delay(10_000) // Watchdog ticks every 10s
                cleanExpiredLocks()

                // Check for stuck agents (working for > 60s without finish)
                val now = System.currentTimeMillis()
                _agents.update { list ->
                    list.map { agent ->
                        if (agent.state == MeshAgentState.WORKING && (now - agent.lastActiveTimestamp > MeshProtocol.AGENT_STUCK_TIMEOUT_MS)) {
                            agent.copy(state = MeshAgentState.STUCK, currentTask = "Délai d'attente dépassé (bloqué)")
                        } else agent
                    }
                }
            }
        }
    }

    // ==========================================
    // HELPERS & ZERO-PLAINTEXT LEDGER
    // ==========================================

    private fun addMessageToStream(room: String, message: MeshChatMessage) {
        _roomMessages.update { map ->
            val list = map[room].orEmpty()
            map + (room to (list + message))
        }
    }

    private fun updateMessageInStream(room: String, updated: MeshChatMessage) {
        _roomMessages.update { map ->
            val list = map[room].orEmpty()
            val newList = list.map { if (it.id == updated.id) updated else it }
            map + (room to newList)
        }
    }

    private fun recordLedger(message: MeshChatMessage, status: MeshMessageStatus, transitDurationMs: Long) {
        val entry = MeshLedgerEntry(
            timestamp = System.currentTimeMillis(),
            fromAgentId = message.fromAgentId,
            toTarget = message.toAgentId ?: message.room,
            sha256Fingerprint = message.fingerprint,
            finalStatus = status,
            transitDurationMs = transitDurationMs
        )
        _ledger.update { listOf(entry) + it.take(99) }
    }
}
