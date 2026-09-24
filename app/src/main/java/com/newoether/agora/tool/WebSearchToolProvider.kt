package com.newoether.agora.tool

import androidx.core.text.HtmlCompat
import com.newoether.agora.api.DuckDuckGoScraper
import com.newoether.agora.api.HttpClient
import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolFunction
import com.newoether.agora.api.ToolParameters
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.util.Constants
import com.newoether.agora.data.normalizeWebSearchMode
import com.newoether.agora.data.normalizeWebSearchProvider
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

internal fun searxngSearchUrl(configuredBaseUrl: String, query: String): String {
    val baseUrl = configuredBaseUrl.ifBlank { "https://searx.be" }.trimEnd('/')
    val encodedQuery = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
    return "$baseUrl/search?q=$encodedQuery&format=json"
}

internal fun kagiSearchRequestBody(query: String, numResults: Int): String =
    Json.encodeToString(
        buildJsonObject {
            put("query", query)
            put("workflow", "search")
            put("limit", numResults.coerceIn(1, 10))
        }
    )

internal fun exaSearchRequestBody(query: String, numResults: Int, searchType: String): String =
    Json.encodeToString(
        buildJsonObject {
            put("query", query)
            put("numResults", numResults.coerceIn(1, 10))
            put("type", searchType)
            putJsonObject("contents") {
                putJsonObject("text") { put("maxCharacters", 1000) }
            }
        }
    )

internal fun normalizeKagiSearchResponse(
    responseBody: String,
    query: String,
    numResults: Int,
): String {
    val root = Json.parseToJsonElement(responseBody) as? JsonObject
    val data = root?.get("data") as? JsonObject
    val searchResults = data?.get("search") as? JsonArray
        ?: return buildJsonObject {
            put("type", "web_search")
            put("query", query)
            put("error", "no_results")
        }.toString()

    val normalizedResults = buildJsonArray {
        var added = 0
        for (element in searchResults) {
            if (added >= numResults.coerceIn(1, 10)) break
            val result = element as? JsonObject ?: continue
            val url = (result["url"] as? JsonPrimitive)?.content.orEmpty()
            if (url.isBlank()) continue
            add(
                buildJsonObject {
                    put("title", (result["title"] as? JsonPrimitive)?.content.orEmpty())
                    put("url", url)
                    put("description", (result["snippet"] as? JsonPrimitive)?.content.orEmpty())
                }
            )
            added++
        }
    }
    if (normalizedResults.isEmpty()) {
        return buildJsonObject {
            put("type", "web_search")
            put("query", query)
            put("error", "no_results")
        }.toString()
    }

    return buildJsonObject {
        put("type", "web_search")
        put("query", query)
        put("results", normalizedResults)
    }.toString()
}

internal fun normalizeExaSearchResponse(
    responseBody: String,
    query: String,
    numResults: Int,
): String {
    val root = runCatching { Json.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
    val searchResults = root?.get("results") as? JsonArray
    if (searchResults == null || searchResults.isEmpty()) {
        return buildJsonObject {
            put("type", "web_search")
            put("query", query)
            put("error", "no_results")
        }.toString()
    }

    val normalizedResults = buildJsonArray {
        var added = 0
        for (element in searchResults) {
            if (added >= numResults.coerceIn(1, 10)) break
            val result = element as? JsonObject ?: continue
            val url = (result["url"] as? JsonPrimitive)?.content.orEmpty()
            if (url.isBlank()) continue
            add(
                buildJsonObject {
                    put("title", (result["title"] as? JsonPrimitive)?.content.orEmpty())
                    put("url", url)
                    put("description", (result["text"] as? JsonPrimitive)?.content.orEmpty().take(600))
                    val score = (result["score"] as? JsonPrimitive)?.content?.toFloatOrNull()
                    if (score != null) put("score", score)
                }
            )
            added++
        }
    }
    if (normalizedResults.isEmpty()) {
        return buildJsonObject {
            put("type", "web_search")
            put("query", query)
            put("error", "no_results")
        }.toString()
    }

    return buildJsonObject {
        put("type", "web_search")
        put("query", query)
        put("results", normalizedResults)
    }.toString()
}

internal fun webSearchProviderDisplayName(provider: String): String = when (provider) {
    "kagi" -> "Kagi"
    "serper" -> "Serper"
    "tavily" -> "Tavily"
    "searxng" -> "SearXNG"
    "duckduckgo" -> "DuckDuckGo"
    "exa" -> "Exa"
    else -> "Brave Search"
}

// ── Multi-backend fallback & depth modes (pure, JVM-testable) ───────────────

/** Errors that justify trying the next search backend (technical failures + no results). */
internal fun fallbackEligibleError(error: String?): Boolean = when (error) {
    "no_response", "search_error", "captcha", "network_error", "no_results", "no_api_key" -> true
    else -> false
}

/** Backends that work without an API key. */
private val KEYLESS_BACKENDS = setOf("duckduckgo", "searxng")

/** Preferred quality order when falling back (selected provider always comes first). */
private val FALLBACK_PREFERENCE_ORDER = listOf("tavily", "exa", "brave", "serper", "kagi", "searxng", "duckduckgo")

/**
 * Computes the ordered list of backends to try: the selected provider first, then the
 * other usable providers (API key present, or keyless) in preference order — DuckDuckGo
 * as the universal last resort. Fallback disabled => only the selected provider.
 */
internal fun planBackendOrder(
    selectedProvider: String,
    apiKeys: Map<String, String>,
    fallbackEnabled: Boolean,
): List<String> {
    val selected = normalizeWebSearchProvider(selectedProvider)
    if (!fallbackEnabled) return listOf(selected)

    val ordered = mutableListOf(selected)
    FALLBACK_PREFERENCE_ORDER.forEach { candidate ->
        if (candidate == selected || ordered.contains(candidate)) return@forEach
        val usable = candidate in KEYLESS_BACKENDS || apiKeys[candidate].orEmpty().isNotBlank()
        if (usable) ordered.add(candidate)
    }
    // Universal last resort: DuckDuckGo needs no key and must always be present.
    if (!ordered.contains("duckduckgo")) ordered.add("duckduckgo")
    return ordered
}

/** Tavily search_depth mapping: quick stays cheap, everything else keeps today's quality. */
internal fun tavilySearchDepth(mode: String): String =
    if (normalizeWebSearchMode(mode) == "quick") "basic" else "advanced"

/** Exa search type mapping: quick => instant, deep => deep-lite (deep is costly), else auto. */
internal fun exaSearchType(mode: String): String = when (normalizeWebSearchMode(mode)) {
    "quick" -> "instant"
    "deep" -> "deep-lite"
    else -> "auto"
}

/** Hard ceiling for num_results in the current mode. */
internal fun maxNumResultsForMode(mode: String): Int =
    if (normalizeWebSearchMode(mode) == "quick") 3 else 10

/** Default result count when the LLM does not request one explicitly. */
internal fun defaultNumResultsForMode(mode: String, configured: Int): Int =
    if (normalizeWebSearchMode(mode) == "deep") 10 else configured.coerceIn(1, 10)

/** Reads the "error" field of a normalized tool JSON payload, null when absent/unparseable. */
internal fun searchJsonError(toolJson: String): String? {
    val root = runCatching { Json.parseToJsonElement(toolJson) as? JsonObject }.getOrNull() ?: return null
    return (root["error"] as? JsonPrimitive)?.content
}

/**
 * Stamps the normalized tool JSON with the backend that actually served the request
 * (`provider_used`), plus `fallback: true` and the `attempted` list when earlier
 * backends failed — so the LLM (and the user) can tell a fallback happened.
 */
internal fun annotateSearchJson(toolJson: String, providerUsed: String, attempted: List<String>): String {
    val root = runCatching { Json.parseToJsonElement(toolJson) as? JsonObject }.getOrNull() ?: return toolJson
    return buildJsonObject {
        root.forEach { (key, value) -> put(key, value) }
        put("provider_used", providerUsed)
        if (attempted.isNotEmpty()) {
            put("fallback", true)
            putJsonArray("attempted") { attempted.forEach { add(JsonPrimitive(it)) } }
        }
    }.toString()
}

class WebSearchToolProvider : ToolProvider {
    private val webClient = HttpClient.client.newBuilder()
        .callTimeout(Constants.NETWORK_TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(Constants.NETWORK_TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.webSearchEnabled) return emptyList()
        val modeHint = when (normalizeWebSearchMode(ctx.webSearchMode)) {
            "quick" -> " Answer with a single targeted search."
            "deep" -> " Feel free to chain web_search and web_fetch calls to cross-check multiple sources."
            else -> ""
        }
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "web_search",
                description = "Search the web for current information. Use this to find facts, news, or data not in your training set.$modeHint",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to ToolProperty("string", "The search query to execute."),
                        "num_results" to ToolProperty("integer", "Number of results to return (1-10, default 5).")
                    ),
                    required = listOf("query")
                )
            )),
            ToolDefinition(function = ToolFunction(
                name = "web_fetch",
                description = "Fetch and read the full text content of a web page. Use this after web_search when you need more detail from a specific page.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "url" to ToolProperty("string", "The URL of the page to fetch."),
                        "maxChars" to ToolProperty("integer", "Maximum characters of text to return (default 8000, max 100000). If the result has \"truncated\": true, call again with a larger maxChars to get more.")
                    ),
                    required = listOf("url")
                )
            ))
        )
    }

    override suspend fun execute(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): String = withContext(Dispatchers.IO) {
        when (name) {
            "web_search" -> executeWebSearch(arguments, ctx)
            "web_fetch" -> executeWebFetch(arguments, ctx)
            else -> "Unknown tool: $name"
        }
    }

    override fun handles(name: String): Boolean = name in setOf("web_search", "web_fetch")

    override fun executeEvents(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): kotlinx.coroutines.flow.Flow<ToolExecutionEvent> = kotlinx.coroutines.flow.flow {
        val text = execute(name, arguments, ctx)
        // Publish citation candidates alongside the model-facing result so local search
        // also feeds inline [n] chips and the sources sheet.
        emit(
            ToolExecutionEvent.Completed(
                ToolExecutionResult(
                    text = text,
                    structuredContent = com.newoether.agora.model.ToolCitationPayload.fromToolResult(name, text),
                ),
            ),
        )
    }

    private fun executeWebSearch(arguments: String, ctx: GenerationContext): String {
        val argsStr = arguments.ifBlank { "{}" }
        val args = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        val query = (args["query"] as? JsonPrimitive)?.content
            ?: return buildJsonObject { put("type", "web_search"); put("error", "no_query") }.toString()

        val mode = normalizeWebSearchMode(ctx.webSearchMode)
        val requestedNum = (args["num_results"] as? JsonPrimitive)?.content?.toIntOrNull()
        val numResults = (requestedNum ?: defaultNumResultsForMode(mode, ctx.webSearchNumResults))
            .coerceIn(1, maxNumResultsForMode(mode))

        val order = planBackendOrder(ctx.webSearchProvider, ctx.webSearchApiKeys, ctx.webSearchFallbackEnabled)
        val attempted = mutableListOf<String>()
        var lastErrorJson: String? = null

        for ((index, candidate) in order.withIndex()) {
            val resultJson = executeSingleBackend(candidate, query, numResults, ctx)
            val error = searchJsonError(resultJson)
            if (error != null && fallbackEligibleError(error) && index < order.lastIndex) {
                attempted += candidate
                lastErrorJson = resultJson
                continue
            }
            return annotateSearchJson(resultJson, candidate, attempted)
        }
        // Every backend failed: surface the last error, annotated with the full attempt chain.
        val fallbackJson = lastErrorJson
            ?: buildJsonObject { put("type", "web_search"); put("query", query); put("error", "search_error") }.toString()
        return annotateSearchJson(fallbackJson, order.last(), attempted)
    }

    /** Executes one backend and returns its normalized tool JSON (error payloads included). */
    private fun executeSingleBackend(
        provider: String,
        query: String,
        numResults: Int,
        ctx: GenerationContext,
    ): String {
        return try {
            // DuckDuckGo is a scraper, not an API — handle it separately.
            if (provider == "duckduckgo") {
                val scraper = DuckDuckGoScraper(webClient)
                return when (val r = scraper.search(query, numResults)) {
                    is DuckDuckGoScraper.SearchResponse.Success -> {
                        val rawResults = buildJsonArray {
                            r.results.forEach { result ->
                                add(buildJsonObject {
                                    put("title", result.title)
                                    put("url", result.url)
                                    put("description", result.snippet)
                                })
                            }
                        }
                        buildJsonObject {
                            put("type", "web_search")
                            put("query", query)
                            put("results", rawResults)
                        }.toString()
                    }
                    is DuckDuckGoScraper.SearchResponse.Error -> {
                        buildJsonObject {
                            put("type", "web_search")
                            put("query", query)
                            put("error", r.type.name.lowercase())
                            put("message", r.message)
                        }.toString()
                    }
                }
            }

            val apiKey = ctx.webSearchApiKeys[provider].orEmpty()
            if (provider != "searxng" && apiKey.isBlank()) {
                return buildJsonObject {
                    put("type", "web_search")
                    put("query", query)
                    put("error", "no_api_key")
                    put("provider", webSearchProviderDisplayName(provider))
                }.toString()
            }
            val body = when (provider) {
                "kagi" -> HttpClient.post(
                    "https://kagi.com/api/v1/search",
                    kagiSearchRequestBody(query, numResults),
                    mapOf(
                        "Accept" to "application/json",
                        "Authorization" to "Bearer $apiKey",
                    ),
                    callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS,
                )
                "serper" -> HttpClient.post(
                    "https://google.serper.dev/search",
                    Json.encodeToString(buildJsonObject { put("q", query); put("num", numResults) }),
                    mapOf("X-API-KEY" to apiKey),
                    callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS,
                )
                "tavily" -> HttpClient.post(
                    "https://api.tavily.com/search",
                    Json.encodeToString(buildJsonObject {
                        put("api_key", apiKey)
                        put("query", query)
                        put("max_results", numResults)
                        put("search_depth", tavilySearchDepth(ctx.webSearchMode))
                        put("include_answer", true)
                    }),
                    emptyMap(),
                    callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS,
                )
                "exa" -> HttpClient.post(
                    "https://api.exa.ai/search",
                    exaSearchRequestBody(query, numResults, exaSearchType(ctx.webSearchMode)),
                    mapOf(
                        "x-api-key" to apiKey,
                        "Content-Type" to "application/json",
                    ),
                    callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS,
                )
                "searxng" -> {
                    // Don't pin engines=google,brave: many public/self-hosted SearXNG instances
                    // disable those engines (rate-limited/require config), and pinning them yields
                    // an empty result set. Letting the instance use its own default-enabled engines
                    // matches how other SearXNG clients behave. Send a browser-like User-Agent so
                    // bot-filtering instances don't 403 us (same reason web_fetch sets one).
                    HttpClient.fetchModels(
                        searxngSearchUrl(ctx.webSearchBaseUrl, query),
                        mapOf("User-Agent" to Constants.WEB_FETCH_USER_AGENT),
                        callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS,
                    )
                }
                else -> HttpClient.fetchModels(
                    "https://api.search.brave.com/res/v1/web/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&count=$numResults",
                    mapOf("Accept" to "application/json", "X-Subscription-Token" to apiKey),
                    callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS,
                )
            } ?: return buildJsonObject { put("type", "web_search"); put("query", query); put("error", "no_response") }.toString()

            if (provider == "kagi") {
                return normalizeKagiSearchResponse(body, query, numResults)
            }
            if (provider == "exa") {
                return normalizeExaSearchResponse(body, query, numResults)
            }

            val json: Map<String, kotlinx.serialization.json.JsonElement> = Json.decodeFromString(body)

            if (provider == "tavily") {
                val resultsArray = json["results"]?.jsonArray
                    ?: return buildJsonObject { put("type", "web_search"); put("query", query); put("error", "no_results") }.toString()
                if (resultsArray.isEmpty())
                    return buildJsonObject { put("type", "web_search"); put("query", query); put("error", "no_results") }.toString()
                val answer = (json["answer"] as? JsonPrimitive)?.content
                val rawResults = buildJsonArray {
                    for (element in resultsArray) {
                        val obj = element.jsonObject
                        add(buildJsonObject {
                            put("title", (obj["title"] as? JsonPrimitive)?.content ?: "")
                            put("url", (obj["url"] as? JsonPrimitive)?.content ?: "")
                            put("content", (obj["content"] as? JsonPrimitive)?.content ?: "")
                            val score = (obj["score"] as? JsonPrimitive)?.content?.toFloatOrNull()
                            if (score != null) put("score", score)
                        })
                    }
                }
                return buildJsonObject {
                    put("type", "web_search")
                    put("query", query)
                    if (!answer.isNullOrBlank()) put("answer", answer)
                    put("results", rawResults)
                }.toString()
            }

            val resultsArray = when {
                json.containsKey("organic") -> json["organic"]?.jsonArray
                json.containsKey("web") -> {
                    val web = json["web"]?.jsonObject
                    web?.get("results")?.jsonArray
                }
                json.containsKey("results") -> json["results"]?.jsonArray
                else -> null
            } ?: return buildJsonObject { put("type", "web_search"); put("query", query); put("error", "no_results") }.toString()

            if (resultsArray.isEmpty())
                return buildJsonObject { put("type", "web_search"); put("query", query); put("error", "no_results") }.toString()

            val rawResults = buildJsonArray {
                for (element in resultsArray) {
                    val obj = element.jsonObject
                    add(buildJsonObject {
                        put("title", (obj["title"] as? JsonPrimitive)?.content ?: "")
                        put("url", (obj["link"] as? JsonPrimitive)?.content ?: (obj["url"] as? JsonPrimitive)?.content ?: "")
                        put("description", (obj["snippet"] as? JsonPrimitive)?.content ?: (obj["content"] as? JsonPrimitive)?.content ?: (obj["description"] as? JsonPrimitive)?.content ?: "")
                    })
                }
            }
            buildJsonObject {
                put("type", "web_search")
                put("query", query)
                put("results", rawResults)
            }.toString()
        } catch (e: Exception) {
            buildJsonObject {
                put("type", "web_search")
                put("query", query)
                put("error", "search_error")
                put("message", e.message ?: "")
            }.toString()
        }
    }

    private suspend fun executeWebFetch(arguments: String, ctx: GenerationContext): String {
        val argsStr = arguments.ifBlank { "{}" }
        val args = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        val url = (args["url"] as? JsonPrimitive)?.content
            ?: return buildJsonObject { put("type", "web_fetch"); put("error", "no_url") }.toString()
        val maxChars = (try {
            (args["maxChars"] as? JsonPrimitive)?.content?.toIntOrNull()
        } catch (_: Exception) { null } ?: 8000).coerceIn(1, 100_000)

        return try {
            val html = HttpClient.fetchModels(url, mapOf(
                "User-Agent" to Constants.WEB_FETCH_USER_AGENT,
                "Accept" to "text/html,application/xhtml+xml,*/*"
            ), callTimeoutMillis = Constants.NETWORK_TOOL_TIMEOUT_MS)
                ?: return buildJsonObject { put("type", "web_fetch"); put("url", url); put("error", "no_response") }.toString()
            val fullText = htmlToReadableText(html)
            val text = fullText.take(maxChars)
            buildJsonObject {
                put("type", "web_fetch")
                put("url", url)
                put("text", text)
                put("truncated", fullText.length > text.length)
                put("totalChars", fullText.length)
            }.toString()
        } catch (e: Exception) {
            buildJsonObject {
                put("type", "web_fetch")
                put("url", url)
                put("error", "fetch_error")
                put("message", e.message ?: "")
            }.toString()
        }
    }

    /**
     * Extracts readable text from an HTML page.
     *
     * Strips non-content blocks (comments, script/style/noscript/svg/head), then lets
     * [HtmlCompat] decode entities and flatten the remaining markup while keeping block
     * elements as line breaks. Extraction runs over the whole (capped) HTML and the caller
     * truncates the resulting *text* — so article content past the page's boilerplate is no
     * longer cut off, and entities (—, ’, accents, numeric refs) are decoded correctly
     * instead of being dropped to spaces.
     */
    private fun htmlToReadableText(rawHtml: String): String {
        val stripped = rawHtml
            .take(Constants.MAX_WEB_FETCH_HTML_LENGTH)
            .replace(Regex("<!--[\\s\\S]*?-->"), " ")
            .replace(
                Regex("<(script|style|noscript|svg|head)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE),
                " "
            )
            // Drop common page chrome so navigation/menus/footers don't eat the text budget.
            .replace(
                Regex("<(nav|header|footer|aside)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE),
                " "
            )
        val text = HtmlCompat.fromHtml(stripped, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return text
            .replace(Regex("[ \\t\\x0B\\u000C\\r]+"), " ") // collapse intra-line whitespace
            .replace(Regex(" *\\n *"), "\n")               // trim around line breaks
            .replace(Regex("\\n{3,}"), "\n\n")             // collapse blank-line runs
            .trim()
    }
}
