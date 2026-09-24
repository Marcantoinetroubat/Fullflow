package com.newoether.agora.tool

import android.app.Application
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolFunction
import com.newoether.agora.api.ToolParameters
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.data.brain.BrainRepository
import com.newoether.agora.data.brain.WebPageIngester
import com.newoether.agora.data.local.BrainChunkEntity
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Exposes the brain ingestion layer to the LLM: save a web page (fetch +
 * chunk + embed) and search across all brain content (PDF chunks, web page
 * chunks, and notes) with full-text or semantic search.
 *
 * Follows the same lazy-container pattern as [NoteRagToolProvider] and
 * [PodcastToolProvider]: the [BrainRepository] is created on first use.
 */
class BrainToolProvider(private val app: Application) : ToolProvider {

    private var repository: BrainRepository? = null

    private fun ensureRepository(): BrainRepository? {
        repository?.let { return it }
        val container = runCatching {
            (app as? AgoraApplication)?.requireContainer()
        }.getOrNull() ?: return null
        val db = container.database
        repository = BrainRepository(
            itemDao = db.brainItemDao(),
            chunkDao = db.brainChunkDao(),
            generateEmbedding = { text ->
                val generator = com.newoether.agora.service.BrainEmbeddingService.getGenerator(app)
                generator(text)
            },
        )
        return repository
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> = listOf(
        ToolDefinition(
            function = ToolFunction(
                name = "save_web_page",
                description = "Fetch a web page, extract its text content, and save it into the Second Brain vault for later semantic search. The page is cleaned (scripts, navigation, and styling removed) and chunked automatically.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "url" to ToolProperty("string", "The full URL of the web page to save (must be http or https)."),
                    ),
                    required = listOf("url"),
                ),
            ),
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "search_brain",
                description = "Search across all Second Brain content (ingested web pages, PDF chunks, and notes) by text query. Returns matching chunks with their source type and page number.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to ToolProperty("string", "The search query (matches chunk content)."),
                    ),
                    required = listOf("query"),
                ),
            ),
        ),
    )

    override fun handles(name: String): Boolean = name in TOOLS

    override suspend fun execute(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): String = withContext(Dispatchers.IO) {
        val repo = ensureRepository()
            ?: return@withContext err("not_ready", "Second Brain not yet initialised.")

        val args = parseArgs(arguments)
        when (name) {
            "save_web_page" -> {
                val url = args["url"]?.trim().orEmpty()
                if (url.isBlank()) return@withContext err("no_url")
                if (!url.startsWith("http")) return@withContext err("bad_url", "URL must start with http:// or https://")

                val itemId = repo.saveWebPage(url)
                if (itemId == null) {
                    return@withContext err("fetch_failed", "Could not fetch or parse the page at $url.")
                }

                buildJsonObject {
                    put("type", "web_page_saved")
                    put("itemId", itemId)
                    put("url", url)
                    put("status", "indexed")
                    put("message", "Page ingested into the Second Brain. Use search_brain to find it later.")
                }.toString()
            }

            "search_brain" -> {
                val query = args["query"]?.trim().orEmpty()
                if (query.isBlank()) return@withContext err("no_query")

                val chunks = repo.searchContent(query)
                val items = repo.getReadyItems()
                val itemMap = items.associateBy { it.id }

                if (chunks.isEmpty()) {
                    return@withContext buildJsonObject {
                        put("type", "brain_search")
                        put("query", query)
                        put("error", "no_results")
                    }.toString()
                }

                buildJsonObject {
                    put("type", "brain_search")
                    put("query", query)
                    put("count", chunks.size)
                    putJsonArray("results") {
                        chunks.forEach { chunk ->
                            val item = itemMap[chunk.itemId]
                            add(buildJsonObject {
                                put("sourceType", item?.type ?: "UNKNOWN")
                                put("title", item?.title ?: "")
                                put("sourceUrl", item?.sourceUrl ?: "")
                                put("pageNumber", chunk.pageNumber)
                                put("snippet", chunk.content.take(300))
                            })
                        }
                    }
                }.toString()
            }

            else -> "Unknown tool: $name"
        }
    }

    private fun err(code: String, message: String? = null): String = buildJsonObject {
        put("type", "brain_tool")
        put("error", code)
        if (message != null) put("message", message)
    }.toString()

    private fun parseArgs(arguments: String): Map<String, String> {
        if (arguments.isBlank()) return emptyMap()
        return runCatching {
            val parsed = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(arguments)
            parsed.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private val TOOLS = setOf("save_web_page", "search_brain")
    }
}
