package com.newoether.agora.mesh

import java.security.MessageDigest

/**
 * Protocol specifications for Pi-Mesh (mesh.v1) on Android.
 * Designed for zero-cloud, 100% on-device agent-to-agent coordination.
 */
object MeshProtocol {
    const val PROTOCOL_VERSION = "mesh.v1"
    const val BROKER_NAME = "Pi-Mesh Switchboard"

    // Default room channels
    const val ROOM_GENERAL = "general"
    const val ROOM_DEBATE = "debat-contradictoire"
    const val ROOM_DRAFTING = "redaction-chaine"
    const val ROOM_SYNTHESIS = "synthese-sources"

    // Default timeouts
    const val DEFAULT_LEASE_DURATION_MS = 15 * 60 * 1000L // 15 minutes lease
    const val AGENT_STUCK_TIMEOUT_MS = 60 * 1000L // 60s stuck watchdog
    const val MAX_LOOP_THRESHOLD = 5 // Max back-to-back ping-pongs before loop breaker

    /**
     * Computes a SHA-256 cryptographic fingerprint for zero-plaintext audit logging.
     */
    fun computeFingerprint(from: String, to: String, payload: String, timestamp: Long): String {
        val raw = "$from:$to:$timestamp:${payload.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Honest message states in Pi-Mesh.
 */
enum class MeshMessageStatus(val label: String, val badge: String) {
    SENT("Envoyé", "📤"),
    DELIVERED("Délivré", "📨"),
    READ("Lu (injecté)", "👁️"),
    ANSWERED("Répondu", "↩️"),
    EXPIRED("Expiré", "⏰")
}

/**
 * Visual activity state of an agent in the team.
 */
enum class MeshAgentState(val label: String, val symbol: String) {
    IDLE("Au repos", "⚪"),
    WORKING("En travail", "🔴"),
    STUCK("Coincé", "❌")
}
