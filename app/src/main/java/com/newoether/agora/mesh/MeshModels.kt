package com.newoether.agora.mesh

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable
import java.util.UUID

/**
 * Representation of a connected agent / cartridge in Pi-Mesh.
 */
data class MeshAgent(
    val id: String,
    val name: String,
    val role: String,
    val colorHex: Long,
    val state: MeshAgentState = MeshAgentState.IDLE,
    val currentTask: String? = null,
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val completedTasksCount: Int = 0,
    val loopCounter: Int = 0,
)

/**
 * Privacy-preserving serializable message for Pi-Mesh inter-agent coordination.
 * Specifically avoids the storage of message bodies to ensure data privacy,
 * storing only topic, senderId, timestamp, and a metadata hash / map.
 */
@Serializable
data class MeshMessage(
    val topic: String,
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val metadataHash: String = MeshProtocol.computeFingerprint(senderId, topic, metadata.toString(), timestamp)
) : JavaSerializable {

    constructor(
        topic: String,
        senderId: String,
        metadataHash: String,
        timestamp: Long = System.currentTimeMillis()
    ) : this(
        topic = topic,
        senderId = senderId,
        timestamp = timestamp,
        metadata = mapOf("hash" to metadataHash),
        metadataHash = metadataHash
    )
}

/**
 * Chat item displayed within the local Atelier UI room stream.
 */
data class MeshChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val fromAgentId: String,
    val fromAgentName: String,
    val toAgentId: String? = null, // null means room broadcast
    val room: String = MeshProtocol.ROOM_GENERAL,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MeshMessageStatus = MeshMessageStatus.SENT,
    val deliveredTimestamp: Long? = null,
    val readTimestamp: Long? = null,
    val answeredTimestamp: Long? = null,
    val replyToId: String? = null,
    val fingerprint: String = MeshProtocol.computeFingerprint(fromAgentId, toAgentId ?: room, content, timestamp)
)

/**
 * File reservation record with temporary lease and auto-expiration.
 */
data class MeshFileLock(
    val filePath: String,
    val holderAgentId: String,
    val holderAgentName: String,
    val purpose: String,
    val acquiredAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + MeshProtocol.DEFAULT_LEASE_DURATION_MS,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
    val remainingMinutes: Long get() = maxOf(0L, (expiresAt - System.currentTimeMillis()) / (60 * 1000L))
}

/**
 * Virtual meeting room / channel for multi-agent discussions.
 */
data class MeshRoom(
    val id: String,
    val name: String,
    val description: String,
    val memberAgentIds: Set<String> = emptySet(),
    val activeLocks: List<String> = emptyList(),
)

/**
 * Zero-plaintext cryptographic audit ledger entry.
 */
data class MeshLedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val fromAgentId: String,
    val toTarget: String,
    val sha256Fingerprint: String,
    val finalStatus: MeshMessageStatus,
    val transitDurationMs: Long = 0L,
)

/**
 * Scatter-gather group mission summary ("lance et continue" & gather).
 */
data class MeshMissionSummary(
    val missionId: String = UUID.randomUUID().toString(),
    val title: String,
    val dispatchedAt: Long = System.currentTimeMillis(),
    val targetAgentIds: List<String>,
    val completedAgentIds: List<String> = emptyList(),
    val missingAgentIds: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val isComplete: Boolean = false,
    val synthesisNotes: String = ""
)
