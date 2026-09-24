package com.newoether.agora.api.gemini.live

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Pure, JVM-testable policy for the Gemini Live realtime session (Phase 1 hardening).
 *
 * No Android imports here on purpose: every rule below is covered by unit tests.
 */
object LiveConnectionPolicy {

    const val MAX_RECONNECT_ATTEMPTS = 5
    const val RECONNECT_BASE_DELAY_MS = 1_000L
    const val RECONNECT_MAX_DELAY_MS = 30_000L

    /**
     * Exponential backoff with ±25% jitter so a fleet of devices does not
     * retry in lockstep after a regional outage.
     *
     * @param jitter01 deterministic jitter input in [0, 1) — pass Random.nextDouble()
     *   in production, a fixed value in tests.
     */
    fun reconnectDelayMs(attempt: Int, jitter01: Double = 0.5): Long {
        val shift = attempt.coerceIn(0, 10)
        val backoff = RECONNECT_BASE_DELAY_MS shl shift.coerceAtMost(5)
        val capped = backoff.coerceAtMost(RECONNECT_MAX_DELAY_MS)
        val jitterFactor = 0.75 + (jitter01.coerceIn(0.0, 1.0) * 0.5)
        return (capped * jitterFactor).toLong().coerceAtLeast(0L)
    }

    /** Close codes that must NOT be retried. 1008 = policy violation (rejected key). */
    fun isFatalCloseCode(code: Int): Boolean = code == 1008

    /** Clean shutdown — no retry, just go Disconnected. */
    fun isCleanCloseCode(code: Int): Boolean = code == 1000

    /** HTTP codes that must NOT be retried (bad/revoked credential). */
    fun isFatalHttpCode(code: Int): Boolean = code == 401 || code == 403

    /**
     * Actionable French message for a transport failure. `httpCode` comes from
     * the OkHttp `Response` attached to the WebSocket failure when present.
     */
    fun friendlyErrorMessage(cause: Throwable?, httpCode: Int? = null): String {
        if (httpCode != null && isFatalHttpCode(httpCode)) {
            return "Clé API rejetée (HTTP $httpCode). Vérifiez la clé Google dans Réglages → Fournisseurs."
        }
        return when (cause) {
            is UnknownHostException ->
                "Pas de connexion Internet. Vérifiez le réseau puis touchez Reconnecter."
            is SocketTimeoutException ->
                "Délai de connexion dépassé. Réessayez, de préférence en Wi-Fi stable."
            is java.io.IOException ->
                "Connexion perdue (${cause.message?.take(80) ?: "réseau"}). Reconnexion…"
            else ->
                (cause?.message?.take(120)?.takeIf { it.isNotBlank() })
                    ?: "Erreur de connexion inconnue."
        }
    }
}

/**
 * Totally client-side speech-activity flag from the mic RMS level (0..1).
 * Speech frames speak immediately; silence keeps the flag for [hangoverMs]
 * after the last speech frame so word boundaries don't flicker.
 * Feed with monotonic [nowMs].
 */
class SpeechActivityDetector(
    private val threshold: Float = 0.12f,
    private val hangoverMs: Long = 600L,
) {
    private var lastSpeechMs: Long? = null

    fun update(rms: Float, nowMs: Long): Boolean {
        if (rms >= threshold) {
            lastSpeechMs = nowMs
            return true
        }
        val last = lastSpeechMs ?: return false
        return nowMs - last < hangoverMs
    }

    fun reset() {
        lastSpeechMs = null
    }
}
