package com.newoether.agora.util

import java.net.InetAddress
import java.net.URI

/**
 * Anti-SSRF guard: blocks fetches to private, loopback, link-local, or
 * multicast addresses. Ported from the morphic-mobile backend and hardened
 * for client-side use (fail-closed if DNS can't resolve).
 *
 * Pure Kotlin (uses only java.net) — JVM-testable.
 */
object SsrfGuard {

    /**
     * Returns true if the URL is safe to fetch (not pointing to a private
     * or local address). Fail-closed: unresolvable DNS → false.
     */
    fun isSafeUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase()?.trim()?.removeSurrounding("[", "]") ?: return false
        return !isPrivateHost(host)
    }

    /**
     * Returns true if the host resolves to at least one private/local address.
     * Fail-closed: unresolvable DNS → true (block).
     */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim().removeSurrounding("[", "]")
        if (h.isEmpty() || h == "localhost" || h.endsWith(".local") || h.endsWith(".internal")) {
            return true
        }
        return runCatching {
            InetAddress.getAllByName(h).any { addr ->
                addr.isAnyLocalAddress ||
                    addr.isLoopbackAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isMulticastAddress
            }
        }.getOrDefault(true)
    }
}
