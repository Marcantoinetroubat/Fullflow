package com.newoether.agora.tool

import android.app.Application
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolFunction
import com.newoether.agora.api.ToolParameters
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.data.notes.NoteRepository
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Exposes the Second Brain vault to the LLM: search notes (full-text),
 * read a note's content, list all notes, and create new notes — so the
 * model can query and enrich the user's PKM during a conversation.
 *
 * Follows the same lazy-container pattern as [PodcastToolProvider]: the
 * NoteRepository is created on first use via [AgoraApplication.requireContainer],
 * wrapped in runCatching so a not-yet-ready DB never crashes the tool call.
 */
class NoteRagToolProvider(private val app: Application) : ToolProvider {

    private var repository: NoteRepository? = null

    private fun ensureRepository(): NoteRepository? {
        repository?.let { return it }
        val container = runCatching {
            (app as? AgoraApplication)?.requireContainer()
        }.getOrNull() ?: return null
        val db = container.database
        repository = NoteRepository(
            noteDao = db.noteDao(),
            taskDao = db.obsidianTaskDao(),
            embeddingDao = db.noteEmbeddingDao(),
            notesDir = File(app.filesDir, "notes"),
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
                name = "search_notes",
                description = "Search the user's Second Brain vault (Obsidian-style notes) by title, tags, or content. Returns matching notes with snippets.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to ToolProperty("string", "The search query (matches note titles, tags, and content)."),
                    ),
                    required = listOf("query"),
                ),
            ),
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "read_note",
                description = "Read the full Markdown content of a note from the Second Brain vault by its file path.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "filePath" to ToolProperty("string", "The note's file path (e.g. '01-Architecture/Design.md')."),
                    ),
                    required = listOf("filePath"),
                ),
            ),
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "list_notes",
                description = "List all notes in the Second Brain vault with their titles, tags, and pin status.",
                parameters = ToolParameters(
                    properties = emptyMap(),
                    required = emptyList(),
                ),
            ),
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "create_note",
                description = "Create or overwrite a note in the Second Brain vault. The note is saved as Markdown on disk and indexed for search.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "filePath" to ToolProperty("string", "The file path for the note (e.g. 'Projects/Roadmap.md')."),
                        "content" to ToolProperty("string", "The full Markdown content of the note."),
                    ),
                    required = listOf("filePath", "content"),
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
            "search_notes" -> {
                val query = args["query"]?.trim().orEmpty()
                if (query.isBlank()) return@withContext err("no_query")
                val notes = repo.searchNotes(query)
                if (notes.isEmpty()) return@withContext buildJsonObject {
                    put("type", "note_search"); put("query", query); put("error", "no_results")
                }.toString()
                buildJsonObject {
                    put("type", "note_search")
                    put("query", query)
                    putJsonArray("results") {
                        notes.forEach { n ->
                            add(buildJsonObject {
                                put("title", n.title)
                                put("filePath", n.filePath)
                                put("tags", n.tags)
                                put("isPinned", n.isPinned)
                            })
                        }
                    }
                }.toString()
            }

            "read_note" -> {
                val filePath = args["filePath"]?.trim().orEmpty()
                if (filePath.isBlank()) return@withContext err("no_file_path")
                val note = repo.getNote(filePath)
                if (note == null) return@withContext err("not_found", "Note '$filePath' not found.")
                val (entity, content) = note
                buildJsonObject {
                    put("type", "note_read")
                    put("filePath", entity.filePath)
                    put("title", entity.title)
                    put("tags", entity.tags)
                    put("content", content)
                }.toString()
            }

            "list_notes" -> {
                val notes = repo.listAllNotes()
                if (notes.isEmpty()) return@withContext err("empty_vault", "No notes in the vault yet.")
                buildJsonObject {
                    put("type", "note_list")
                    put("count", notes.size)
                    putJsonArray("notes") {
                        notes.forEach { n ->
                            add(buildJsonObject {
                                put("title", n.title)
                                put("filePath", n.filePath)
                                put("tags", n.tags)
                                put("isPinned", n.isPinned)
                            })
                        }
                    }
                }.toString()
            }

            "create_note" -> {
                val filePath = args["filePath"]?.trim().orEmpty()
                val content = args["content"]?.trim().orEmpty()
                if (filePath.isBlank() || content.isBlank()) return@withContext err("missing_args")
                val entity = repo.saveNote(filePath, content)
                buildJsonObject {
                    put("type", "note_created")
                    put("filePath", entity.filePath)
                    put("title", entity.title)
                    put("tags", entity.tags)
                    put("contentHash", entity.contentHash)
                }.toString()
            }

            else -> "Unknown tool: $name"
        }
    }

    private fun err(code: String, message: String? = null): String = buildJsonObject {
        put("type", "note_tool")
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
        private val TOOLS = setOf("search_notes", "read_note", "list_notes", "create_note")
    }
}
