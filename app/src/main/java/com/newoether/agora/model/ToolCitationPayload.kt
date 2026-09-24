package com.newoether.agora.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Channel letting local tools (web_search / web_fetch) publish citation candidates
 * alongside their model-facing text result. The producer (tool provider) serializes
 * candidates into [com.newoether.agora.tool.ToolExecutionResult.structuredContent];
 * the generation batch executor parses them back and upserts validated
 * [CitationRecord]s — so inline [n] chips and the sources sheet also work for local
 * search, not just provider-native grounded search.
 *
 * Pure Kotlin (no Android imports) so unit tests stay JVM-only.
 */
data class ToolCitationCandidate(
    val title: String,
    val url: String,
    val excerpt: String? = null,
    val provider: String = "web_search",
)

object ToolCitationPayload {

    private const val MAX_CANDIDATES = 10
    private const val MAX_EXCERPT_CHARS = 500

    /** Builds the structuredContent payload from a normalized tool JSON result, or null. */
    fun fromToolResult(toolName: String, toolJson: String): String? {
        val root = runCatching { Json.parseToJsonElement(toolJson) as? JsonObject }.getOrNull()
            ?: return null
        if ((root["error"] as? JsonPrimitive)?.content != null) return null

        val type = (root["type"] as? JsonPrimitive)?.content
        val providerUsed = (root["provider_used"] as? JsonPrimitive)?.content
        val candidates = mutableListOf<ToolCitationCandidate>()

        when {
            type == "web_search" || toolName == "web_search" -> {
                val results = root["results"] as? JsonArray ?: return null
                results.forEach { element ->
                    if (candidates.size >= MAX_CANDIDATES) return@forEach
                    val obj = element as? JsonObject ?: return@forEach
                    val url = (obj["url"] as? JsonPrimitive)?.content.orEmpty()
                    if (url.isBlank()) return@forEach
                    candidates += ToolCitationCandidate(
                        title = (obj["title"] as? JsonPrimitive)?.content.orEmpty(),
                        url = url,
                        excerpt = (
                            (obj["description"] as? JsonPrimitive)?.content
                                ?: (obj["content"] as? JsonPrimitive)?.content
                            )?.take(MAX_EXCERPT_CHARS),
                        provider = "web_search" + (providerUsed?.let { ":$it" }.orEmpty()),
                    )
                }
            }
            type == "web_fetch" || toolName == "web_fetch" -> {
                val url = (root["url"] as? JsonPrimitive)?.content.orEmpty()
                if (url.isBlank()) return null
                val host = runCatching {
                    java.net.URI(url).host?.removePrefix("www.")
                }.getOrNull().orEmpty()
                candidates += ToolCitationCandidate(
                    title = host.ifBlank { url },
                    url = url,
                    excerpt = (root["text"] as? JsonPrimitive)?.content?.take(MAX_EXCERPT_CHARS),
                    provider = "web_fetch",
                )
            }
            else -> return null
        }

        if (candidates.isEmpty()) return null
        return buildJsonObject {
            putJsonArray("citations") {
                candidates.forEach { candidate ->
                    add(
                        buildJsonObject {
                            put("title", candidate.title)
                            put("url", candidate.url)
                            candidate.excerpt?.let { put("excerpt", it) }
                            put("provider", candidate.provider)
                        },
                    )
                }
            }
        }.toString()
    }

    /** Parses a structuredContent payload back into citation candidates. */
    fun parse(payload: String): List<ToolCitationCandidate> {
        val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val array = root["citations"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = (obj["url"] as? JsonPrimitive)?.content.orEmpty()
            if (url.isBlank()) return@mapNotNull null
            ToolCitationCandidate(
                title = (obj["title"] as? JsonPrimitive)?.content.orEmpty(),
                url = url,
                excerpt = (obj["excerpt"] as? JsonPrimitive)?.content,
                provider = (obj["provider"] as? JsonPrimitive)?.content ?: "web_search",
            )
        }
    }

    /** Converts a candidate into a validated [CitationRecord] (null when policy rejects it). */
    fun toCitationRecord(candidate: ToolCitationCandidate): CitationRecord? =
        CitationPolicy.create(
            provider = candidate.provider,
            kind = "web",
            title = candidate.title.ifBlank { null },
            url = candidate.url,
            excerpt = candidate.excerpt,
        )
}
