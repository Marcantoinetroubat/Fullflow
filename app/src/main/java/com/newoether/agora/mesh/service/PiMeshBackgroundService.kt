package com.newoether.agora.mesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.newoether.agora.MainActivity
import com.newoether.agora.R
import com.newoether.agora.mesh.MeshBroker
import com.newoether.agora.mesh.MeshController
import com.newoether.agora.mesh.MeshFileLock
import com.newoether.agora.mesh.MeshMessage
import com.newoether.agora.mesh.MeshMessageStatus
import com.newoether.agora.mesh.MeshProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android Background Service for the Pi-Mesh (mesh.v1) inter-agent communication system.
 *
 * Implements the core architectural principles of Pi-Mesh:
 * - Local, zero-cloud switchboard routing.
 * - Decoupled, event-driven message bus (SharedFlow).
 * - Honest status transitions (Sent -> Delivered -> Read -> Answered/Expired).
 * - Anti-collision file locking leases.
 * - Watchdog loop detection and stuck agent surveillance.
 * - Zero-plaintext cryptographic audit trail.
 */
class PiMeshBackgroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val binder = PiMeshBinder()

    // Shared event-driven bus
    private val _events = MutableSharedFlow<PiMeshEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PiMeshEvent> = _events.asSharedFlow()

    // Delegate to central broker
    val broker: MeshBroker get() = MeshController.broker

    private var watchdogJob: Job? = null
    private var isForegroundRunning = false

    inner class PiMeshBinder : Binder() {
        val service: PiMeshBackgroundService get() = this@PiMeshBackgroundService
        val broker: MeshBroker get() = this@PiMeshBackgroundService.broker
        val events: SharedFlow<PiMeshEvent> get() = this@PiMeshBackgroundService.events
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startEventDrivenWatchdog()
        emitEvent(
            PiMeshEvent.MissionDispatched(
                missionId = "sys-init",
                title = "Démarrage du standard local Pi-Mesh",
                targetAgents = broker.agents.value.map { it.id }
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_FOREGROUND -> startForegroundSwitchboard()
            ACTION_STOP_FOREGROUND -> stopForegroundSwitchboard()
            ACTION_LAUNCH_DEBATE -> {
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: "Orientation stratégique"
                launchDebateMission(topic)
            }
            ACTION_LAUNCH_DRAFTING -> {
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "Document de synthèse"
                launchDraftingMission(subject)
            }
            ACTION_LAUNCH_SYNTHESIS -> {
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: "Analyse multi-sources"
                launchSynthesisMission(topic)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
        serviceScope.cancel()
    }

    /**
     * Emits an event on the event-driven bus.
     */
    fun emitEvent(event: PiMeshEvent) {
        _events.tryEmit(event)
    }

    // ==========================================
    // MULTI-AGENT ORCHESTRATION IN BACKGROUND
    // ==========================================

    fun launchDebateMission(topic: String) {
        serviceScope.launch {
            emitEvent(
                PiMeshEvent.MissionDispatched(
                    missionId = "debate-${System.currentTimeMillis()}",
                    title = "Débat : $topic",
                    targetAgents = listOf("agent-writer", "agent-auditor", "agent-synthesizer")
                )
            )
            broker.launchDebate(topic)
        }
    }

    fun launchDraftingMission(subject: String) {
        serviceScope.launch {
            emitEvent(
                PiMeshEvent.MissionDispatched(
                    missionId = "draft-${System.currentTimeMillis()}",
                    title = "Chaîne de rédaction : $subject",
                    targetAgents = listOf("agent-librarian", "agent-writer", "agent-auditor")
                )
            )
            broker.launchChainedDrafting(subject)
        }
    }

    fun launchSynthesisMission(topic: String) {
        serviceScope.launch {
            emitEvent(
                PiMeshEvent.MissionDispatched(
                    missionId = "synth-${System.currentTimeMillis()}",
                    title = "Synthèse multi-sources : $topic",
                    targetAgents = listOf("agent-librarian", "agent-writer", "agent-auditor", "agent-synthesizer")
                )
            )
            broker.launchParallelSynthesis(topic)
        }
    }

    // ==========================================
    // WATCHDOG & EVENT-DRIVEN HEALTH MONITOR
    // ==========================================

    private fun startEventDrivenWatchdog() {
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(15_000)
                val now = System.currentTimeMillis()

                // Check stuck agents (> 60s working)
                broker.agents.value.forEach { agent ->
                    if (agent.state == com.newoether.agora.mesh.MeshAgentState.WORKING &&
                        (now - agent.lastActiveTimestamp > MeshProtocol.AGENT_STUCK_TIMEOUT_MS)
                    ) {
                        emitEvent(
                            PiMeshEvent.WatchdogAlert(
                                agentId = agent.id,
                                reason = "Agent inactif depuis plus de 60 secondes en tâche : ${agent.currentTask}"
                            )
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // FOREGROUND NOTIFICATION MANAGEMENT
    // ==========================================

    private fun startForegroundSwitchboard() {
        if (isForegroundRunning) return
        val notification = buildForegroundNotification("Standard Pi-Mesh actif (mesh.v1)")
        startForeground(NOTIFICATION_ID, notification)
        isForegroundRunning = true
    }

    private fun stopForegroundSwitchboard() {
        if (!isForegroundRunning) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundRunning = false
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Atelier Pi-Mesh · Coordination d'agents")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pi-Mesh Agent Switchboard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification de fond pour la coordination locale des agents Pi-Mesh"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "pi_mesh_switchboard_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START_FOREGROUND = "com.newoether.agora.mesh.ACTION_START_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.newoether.agora.mesh.ACTION_STOP_FOREGROUND"
        const val ACTION_LAUNCH_DEBATE = "com.newoether.agora.mesh.ACTION_LAUNCH_DEBATE"
        const val ACTION_LAUNCH_DRAFTING = "com.newoether.agora.mesh.ACTION_LAUNCH_DRAFTING"
        const val ACTION_LAUNCH_SYNTHESIS = "com.newoether.agora.mesh.ACTION_LAUNCH_SYNTHESIS"

        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_SUBJECT = "extra_subject"

        /**
         * Starts the background coordination service.
         */
        fun startService(context: Context, asForeground: Boolean = false) {
            val intent = Intent(context, PiMeshBackgroundService::class.java).apply {
                if (asForeground) action = ACTION_START_FOREGROUND
            }
            if (asForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PiMeshBackgroundService::class.java)
            context.stopService(intent)
        }
    }
}
