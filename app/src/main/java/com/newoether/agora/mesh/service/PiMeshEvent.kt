package com.newoether.agora.mesh.service

import com.newoether.agora.mesh.MeshAgentState
import com.newoether.agora.mesh.MeshFileLock
import com.newoether.agora.mesh.MeshMessage
import com.newoether.agora.mesh.MeshMessageStatus

/**
 * Event hierarchy for the event-driven Pi-Mesh coordination engine.
 * Emitted across the background service bus for decoupled agent communication.
 */
sealed interface PiMeshEvent {
    val timestamp: Long

    data class MessageSent(
        val message: MeshMessage,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class MessageStatusChanged(
        val messageId: String,
        val room: String,
        val status: MeshMessageStatus,
        val latencyMs: Long,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class AgentStateChanged(
        val agentId: String,
        val previousState: MeshAgentState,
        val newState: MeshAgentState,
        val currentTask: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class LockAcquired(
        val lock: MeshFileLock,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class LockReleased(
        val filePath: String,
        val releasedBy: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class MissionDispatched(
        val missionId: String,
        val title: String,
        val targetAgents: List<String>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class MissionCompleted(
        val missionId: String,
        val durationMs: Long,
        val allAnswered: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class WatchdogAlert(
        val agentId: String,
        val reason: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent

    data class LoopDetected(
        val fromAgentId: String,
        val toAgentId: String,
        val iterations: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PiMeshEvent
}
