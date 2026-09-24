package com.newoether.agora.mesh.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.newoether.agora.mesh.BusMessage
import com.newoether.agora.mesh.MessageBus
import com.newoether.agora.mesh.MeshMessage
import com.newoether.agora.mesh.MeshProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Status representation for connected agents.
 */
enum class AgentStatus(val label: String, val symbol: String) {
    IDLE("Au repos", "⚪"),
    WORKING("En travail", "🔴"),
    BLOCKED("Bloqué", "❌")
}

/**
 * Snapshot of an agent's current state.
 * Stores status, task description, and telemetry without storing any message bodies.
 */
data class AgentRecord(
    val agentId: String,
    val name: String,
    val role: String,
    val status: AgentStatus = AgentStatus.IDLE,
    val currentTask: String? = null,
    val lastHeartbeatMs: Long = System.currentTimeMillis(),
    val completedCount: Int = 0
)

/**
 * Cryptographic hash-only audit log entry.
 * Stores ONLY the SHA-256 fingerprint, timestamp, latency, and routing target.
 * Absolutely NO message body/plaintext is retained.
 */
data class HashOnlyAuditEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val senderId: String,
    val topic: String,
    val sha256Fingerprint: String,
    val transitDurationMs: Long
)

/**
 * Thread-safe registry tracking agent statuses (Working, Idle, Blocked)
 * without persisting message bodies.
 */
class ThreadSafeAgentRegistry {
    private val agentMap = ConcurrentHashMap<String, AgentRecord>()
    private val auditLedger = ConcurrentLinkedDeque<HashOnlyAuditEntry>()
    private val completedCounters = ConcurrentHashMap<String, AtomicInteger>()

    private val _agentsFlow = MutableStateFlow<List<AgentRecord>>(emptyList())
    val agentsFlow: StateFlow<List<AgentRecord>> = _agentsFlow.asStateFlow()

    init {
        // Pre-register default core agents
        registerAgent("agent-librarian", "Bibliothécaire", "Recherche & Vérification")
        registerAgent("agent-writer", "Rédacteur", "Rédaction & Restitution")
        registerAgent("agent-auditor", "Auditeur", "Sécurité & Contradiction")
        registerAgent("agent-synthesizer", "Synthétiseur", "Arbitrage & Agrégation")
        registerAgent("agent-visual", "Nano Banana", "Visuels & Prompts")
    }

    fun registerAgent(agentId: String, name: String, role: String) {
        agentMap.computeIfAbsent(agentId) {
            AgentRecord(
                agentId = agentId,
                name = name,
                role = role,
                status = AgentStatus.IDLE,
                lastHeartbeatMs = System.currentTimeMillis()
            )
        }
        completedCounters.computeIfAbsent(agentId) { AtomicInteger(0) }
        refreshFlow()
    }

    fun updateStatus(agentId: String, status: AgentStatus, currentTask: String? = null) {
        agentMap.compute(agentId) { _, existing ->
            val now = System.currentTimeMillis()
            if (existing != null) {
                val newCount = if (existing.status == AgentStatus.WORKING && status == AgentStatus.IDLE) {
                    completedCounters[agentId]?.incrementAndGet() ?: existing.completedCount
                } else {
                    existing.completedCount
                }
                existing.copy(
                    status = status,
                    currentTask = currentTask,
                    lastHeartbeatMs = now,
                    completedCount = newCount
                )
            } else {
                AgentRecord(
                    agentId = agentId,
                    name = agentId,
                    role = "Agent",
                    status = status,
                    currentTask = currentTask,
                    lastHeartbeatMs = now
                )
            }
        }
        refreshFlow()
    }

    fun updateHeartbeat(agentId: String) {
        agentMap.computeIfPresent(agentId) { _, existing ->
            existing.copy(lastHeartbeatMs = System.currentTimeMillis())
        }
        refreshFlow()
    }

    /**
     * Records a hash-only audit entry. Zero message body is stored.
     */
    fun recordAudit(senderId: String, topic: String, sha256Fingerprint: String, transitDurationMs: Long) {
        val entry = HashOnlyAuditEntry(
            timestamp = System.currentTimeMillis(),
            senderId = senderId,
            topic = topic,
            sha256Fingerprint = sha256Fingerprint,
            transitDurationMs = transitDurationMs
        )
        auditLedger.addFirst(entry)
        // Keep in-memory size bounded
        while (auditLedger.size > 200) {
            auditLedger.removeLast()
        }
    }

    fun getAgent(agentId: String): AgentRecord? = agentMap[agentId]

    fun getAllAgents(): List<AgentRecord> = agentMap.values.toList()

    fun getAuditTrail(): List<HashOnlyAuditEntry> = auditLedger.toList()

    fun getWorkingCount(): Int = agentMap.values.count { it.status == AgentStatus.WORKING }
    fun getIdleCount(): Int = agentMap.values.count { it.status == AgentStatus.IDLE }
    fun getBlockedCount(): Int = agentMap.values.count { it.status == AgentStatus.BLOCKED }

    private fun refreshFlow() {
        _agentsFlow.value = agentMap.values.toList().sortedBy { it.name }
    }
}

/**
 * Dispatch packet submitted to the non-blocking [Channel].
 * Specifically avoids the storage of message bodies to ensure data privacy.
 */
data class MeshDispatchPacket(
    val topic: String,
    val senderId: String,
    val metadataHash: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Central Android Service acting as the hub for local agent communication.
 * Uses Kotlin coroutine [Channel] for non-blocking message passing and a thread-safe
 * registry to track agent statuses (Working, Idle, Blocked) without storing message bodies.
 */
class MeshCoordinatorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    // Non-blocking Channel for incoming agent messages
    private val messageChannel = Channel<MeshDispatchPacket>(capacity = Channel.BUFFERED)

    // Decoupled pub/sub bus
    val messageBus = MessageBus.defaultBus

    // Thread-safe registry without message body persistence
    val registry = ThreadSafeAgentRegistry()

    // ConcurrentHashMap mapping agent IDs to their current AgentStatus
    val agentStatuses = ConcurrentHashMap<String, AgentStatus>()

    private val binder = MeshCoordinatorBinder()
    private var watchdogJob: Job? = null

    init {
        // Pre-populate core agent statuses
        agentStatuses["agent-librarian"] = AgentStatus.IDLE
        agentStatuses["agent-writer"] = AgentStatus.IDLE
        agentStatuses["agent-auditor"] = AgentStatus.IDLE
        agentStatuses["agent-synthesizer"] = AgentStatus.IDLE
        agentStatuses["agent-visual"] = AgentStatus.IDLE
    }

    inner class MeshCoordinatorBinder : Binder() {
        val service: MeshCoordinatorService get() = this@MeshCoordinatorService
        val messageBus: MessageBus get() = this@MeshCoordinatorService.messageBus
        val registry: ThreadSafeAgentRegistry get() = this@MeshCoordinatorService.registry
        val agentStatuses: ConcurrentHashMap<String, AgentStatus> get() = this@MeshCoordinatorService.agentStatuses

        /**
         * Routes a privacy-preserving MeshMessage through the coordinator.
         */
        fun routeMessage(message: MeshMessage): Boolean {
            return this@MeshCoordinatorService.routeMessage(message)
        }

        /**
         * Non-blocking message dispatch through the Channel.
         */
        fun postMessage(topic: String, senderId: String, metadataHash: String = ""): Boolean {
            val message = MeshMessage(topic = topic, senderId = senderId, metadataHash = metadataHash)
            return this@MeshCoordinatorService.routeMessage(message)
        }

        fun updateAgentStatus(agentId: String, status: AgentStatus, task: String? = null): AgentStatus {
            return this@MeshCoordinatorService.updateAgentStatus(agentId, status, task)
        }

        fun getAgentStatus(agentId: String): AgentStatus {
            return this@MeshCoordinatorService.getAgentStatus(agentId)
        }

        fun getAllAgentStatuses(): Map<String, AgentStatus> {
            return this@MeshCoordinatorService.getAllAgentStatuses()
        }

        fun compareAndSetAgentStatus(agentId: String, expected: AgentStatus, newStatus: AgentStatus, task: String? = null): Boolean {
            return this@MeshCoordinatorService.compareAndSetAgentStatus(agentId, expected, newStatus, task)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startChannelConsumer()
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_DISPATCH_MESSAGE -> {
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: MeshProtocol.ROOM_GENERAL
                val senderId = intent.getStringExtra(EXTRA_SENDER_ID) ?: "system"
                val metadataHash = intent.getStringExtra(EXTRA_METADATA_HASH)
                    ?: intent.getStringExtra(EXTRA_PAYLOAD)
                    ?: ""
                val message = MeshMessage(topic = topic, senderId = senderId, metadataHash = metadataHash)
                routeMessage(message)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        watchdogJob?.cancel()
        messageChannel.close()
        serviceScope.cancel()
    }

    /**
     * Thread-safely updates the status of an agent in the ConcurrentHashMap and the registry.
     */
    fun updateAgentStatus(agentId: String, status: AgentStatus, currentTask: String? = null): AgentStatus {
        agentStatuses[agentId] = status
        registry.updateStatus(agentId, status, currentTask)
        return status
    }

    /**
     * Thread-safely gets the current status of an agent.
     */
    fun getAgentStatus(agentId: String): AgentStatus {
        return agentStatuses[agentId] ?: AgentStatus.IDLE
    }

    /**
     * Thread-safely returns a snapshot of all agent statuses.
     */
    fun getAllAgentStatuses(): Map<String, AgentStatus> {
        return ConcurrentHashMap(agentStatuses)
    }

    /**
     * Thread-safely updates an agent's status during routing if their current status matches [expectedStatus].
     */
    fun compareAndSetAgentStatus(
        agentId: String,
        expectedStatus: AgentStatus,
        newStatus: AgentStatus,
        currentTask: String? = null
    ): Boolean {
        var transitioned = false
        agentStatuses.compute(agentId) { _, current ->
            val effective = current ?: AgentStatus.IDLE
            if (effective == expectedStatus) {
                transitioned = true
                newStatus
            } else {
                effective
            }
        }
        if (transitioned) {
            registry.updateStatus(agentId, newStatus, currentTask)
        }
        return transitioned
    }

    /**
     * Routes a privacy-preserving [MeshMessage] through the coordinator.
     * Thread-safely updates the sender's status to WORKING during message routing,
     * passes the message into the non-blocking Channel,
     * and tracks agent statuses without persisting message bodies.
     */
    fun routeMessage(message: MeshMessage): Boolean {
        // Thread-safely update sender status to WORKING during active message routing
        updateAgentStatus(message.senderId, AgentStatus.WORKING, "Routage vers ${message.topic}")

        val packet = MeshDispatchPacket(
            topic = message.topic,
            senderId = message.senderId,
            metadataHash = message.metadataHash.ifEmpty { message.metadata.toString() },
            timestamp = message.timestamp
        )
        val result = messageChannel.trySend(packet)
        if (!result.isSuccess) {
            // Revert status to IDLE if channel buffer rejected the send
            updateAgentStatus(message.senderId, AgentStatus.IDLE)
        }
        return result.isSuccess
    }

    /**
     * Continuous consumer loop reading from the non-blocking [Channel].
     * Decouples the sender from the receivers and publishes to the topic salon.
     * Thread-safely transitions sender status back to IDLE after routing completes.
     */
    private fun startChannelConsumer() {
        serviceScope.launch {
            for (packet in messageChannel) {
                val startMs = System.currentTimeMillis()

                // Update sender telemetry
                registry.updateHeartbeat(packet.senderId)

                // Publish privacy-preserving hash payload to the event-driven MessageBus
                val busMsg: BusMessage = messageBus.publish(
                    topic = packet.topic,
                    senderId = packet.senderId,
                    payload = "hash:${packet.metadataHash}"
                )

                // Record cryptographic audit entry (WITHOUT message bodies)
                val duration = System.currentTimeMillis() - startMs
                registry.recordAudit(
                    senderId = packet.senderId,
                    topic = packet.topic,
                    sha256Fingerprint = busMsg.fingerprint,
                    transitDurationMs = duration
                )

                // Thread-safely transition sender status back to IDLE after routing
                updateAgentStatus(packet.senderId, AgentStatus.IDLE)
            }
        }
    }

    /**
     * Watchdog loop monitoring for blocked or stuck agents.
     * Transitions agents from WORKING to BLOCKED if inactive past timeout.
     */
    private fun startWatchdog() {
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(10_000)
                val now = System.currentTimeMillis()
                registry.getAllAgents().forEach { agent ->
                    if (getAgentStatus(agent.agentId) == AgentStatus.WORKING &&
                        (now - agent.lastHeartbeatMs > MeshProtocol.AGENT_STUCK_TIMEOUT_MS)
                    ) {
                        updateAgentStatus(
                            agent.agentId,
                            AgentStatus.BLOCKED,
                            "Délai d'inactivité dépassé (> 60s)"
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_DISPATCH_MESSAGE = "com.newoether.agora.mesh.ACTION_DISPATCH_MESSAGE"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_SENDER_ID = "extra_sender_id"
        const val EXTRA_PAYLOAD = "extra_payload"
        const val EXTRA_METADATA_HASH = "extra_metadata_hash"

        private var instance: MeshCoordinatorService? = null

        fun getRunningCoordinator(): MeshCoordinatorService? = instance

        fun startService(context: Context) {
            val intent = Intent(context, MeshCoordinatorService::class.java)
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MeshCoordinatorService::class.java)
            context.stopService(intent)
        }
    }
}
