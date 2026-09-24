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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Exposes Obsidian Tasks embedded in the Second Brain vault to the LLM:
 * list open tasks (with priority + due date) and toggle their checkbox status
 * bidirectionally (the Markdown file on disk is rewritten).
 */
class ObsidianTaskToolProvider(private val app: Application) : ToolProvider {

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
                name = "list_obsidian_tasks",
                description = "List all open Obsidian tasks from the Second Brain vault with their priority, due date, and source note.",
                parameters = ToolParameters(
                    properties = emptyMap(),
                    required = emptyList(),
                ),
            ),
        ),
        ToolDefinition(
            function = ToolFunction(
                name = "toggle_obsidian_task",
                description = "Toggle the checkbox status of an Obsidian task (todo↔done). The Markdown file on disk is updated bidirectionally.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "taskId" to ToolProperty("integer", "The task ID returned by list_obsidian_tasks."),
                    ),
                    required = listOf("taskId"),
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
            "list_obsidian_tasks" -> {
                val tasks = repo.getOpenTasksList()
                if (tasks.isEmpty()) return@withContext buildJsonObject {
                    put("type", "task_list"); put("count", 0); put("message", "No open tasks.")
                }.toString()
                buildJsonObject {
                    put("type", "task_list")
                    put("count", tasks.size)
                    putJsonArray("tasks") {
                        tasks.forEach { t ->
                            add(buildJsonObject {
                                put("id", t.id)
                                put("text", t.text)
                                put("note", t.noteFilePath)
                                put("priority", t.priority ?: "NONE")
                                put("dueDate", t.dueDate ?: "")
                            })
                        }
                    }
                }.toString()
            }

            "toggle_obsidian_task" -> {
                val taskId = args["taskId"]?.toLongOrNull()
                if (taskId == null) return@withContext err("no_task_id", "Missing or invalid taskId.")
                val task = repo.getTask(taskId)
                if (task == null) return@withContext err("not_found", "Task $taskId not found.")
                repo.toggleTask(task)
                buildJsonObject {
                    put("type", "task_toggled")
                    put("taskId", task.id)
                    put("newStatus", if (!task.isChecked) "done" else "todo")
                    put("text", task.text)
                    put("note", task.noteFilePath)
                }.toString()
            }

            else -> "Unknown tool: $name"
        }
    }

    private fun err(code: String, message: String? = null): String = buildJsonObject {
        put("type", "task_tool")
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
        private val TOOLS = setOf("list_obsidian_tasks", "toggle_obsidian_task")
    }
}
