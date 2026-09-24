package com.newoether.agora.data.notes

import com.newoether.agora.data.graph.NoteForGraph
import com.newoether.agora.data.local.NoteDao
import com.newoether.agora.data.local.NoteEmbeddingDao
import com.newoether.agora.data.local.NoteEmbeddingEntity
import com.newoether.agora.data.local.NoteEntity
import com.newoether.agora.data.local.ObsidianTaskDao
import com.newoether.agora.data.local.ObsidianTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orchestrates the Second Brain vault: CRUD notes on disk + Room index, parses
 * Obsidian tasks bidirectionally, chunks content for embeddings, and exposes
 * graph-ready data. Coexists with [com.newoether.agora.data.MemoryManager]
 * (flat .md files for the LLM tool API) — this layer adds structured search,
 * tasks, and graph topology.
 *
 * Embedding generation is injected via [generateEmbedding] so the domain layer
 * stays decoupled from FullFlow's llama.cpp / OpenAI embedding infrastructure.
 * When null, semantic search is simply unavailable (full-text still works).
 */
class NoteRepository(
    private val noteDao: NoteDao,
    private val taskDao: ObsidianTaskDao,
    private val embeddingDao: NoteEmbeddingDao,
    private val notesDir: File,
    private val generateEmbedding: (suspend (text: String) -> FloatArray?)? = null,
) {
    init {
        notesDir.mkdirs()
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    fun observeAllNotes(): Flow<List<NoteEntity>> = noteDao.observeAllNotes()

    suspend fun getNote(filePath: String): Pair<NoteEntity, String>? = withContext(Dispatchers.IO) {
        val entity = noteDao.getNote(filePath) ?: return@withContext null
        val file = File(notesDir, filePath)
        val content = if (file.exists()) file.readText() else ""
        entity to content
    }

    suspend fun searchNotes(query: String): List<NoteEntity> = noteDao.searchNotes(query)

    suspend fun listAllNotes(): List<NoteEntity> = withContext(Dispatchers.IO) { noteDao.getAllNotes() }

    suspend fun noteCount(): Int = noteDao.count()

    suspend fun getOpenTasksList(): List<ObsidianTaskEntity> = withContext(Dispatchers.IO) { taskDao.getOpenTasks() }

    suspend fun getTask(id: Long): ObsidianTaskEntity? = withContext(Dispatchers.IO) { taskDao.getTaskById(id) }

    // ── Save (write file + index Room + parse tasks + embed) ─────────────

    suspend fun saveNote(filePath: String, content: String): NoteEntity = withContext(Dispatchers.IO) {
        val file = File(notesDir, filePath).apply { parentFile?.mkdirs() }
        file.writeText(content)

        val title = extractTitle(content, filePath)
        val tags = extractTags(content)
        val hash = ObsidianTaskParser.contentHash(content)
        val entity = NoteEntity(
            filePath = filePath,
            title = title,
            tags = tags,
            contentHash = hash,
            isPinned = noteDao.getNote(filePath)?.isPinned ?: false,
            lastModified = System.currentTimeMillis(),
        )
        noteDao.upsertNote(entity)

        // Parse and persist Obsidian tasks
        taskDao.deleteTasksForNote(filePath)
        val tasks = ObsidianTaskParser.parseTasks(filePath, content)
        tasks.forEach { taskDao.upsertTask(it) }

        // Index embeddings (only if content changed or note is new)
        val existing = noteDao.getNote(filePath)
        val needsReindex = existing == null || existing.contentHash != hash
        if (needsReindex && generateEmbedding != null) {
            embeddingDao.deleteEmbeddingsForNote(filePath)
            val chunks = NoteChunker.chunkText(title, content)
            val embeddings = chunks.mapIndexedNotNull { index, chunk ->
                val vector = generateEmbedding!!(chunk) ?: return@mapIndexedNotNull null
                NoteEmbeddingEntity(
                    noteFilePath = filePath,
                    chunkIndex = index,
                    chunkText = chunk,
                    embedding = toByteArray(vector),
                    dimension = vector.size,
                )
            }
            if (embeddings.isNotEmpty()) embeddingDao.insertEmbeddings(embeddings)
        }

        entity
    }

    suspend fun deleteNote(filePath: String) = withContext(Dispatchers.IO) {
        noteDao.deleteNote(filePath) // cascade deletes tasks + embeddings
        File(notesDir, filePath).delete()
    }

    suspend fun togglePinned(filePath: String) = withContext(Dispatchers.IO) {
        noteDao.getNote(filePath)?.let { noteDao.upsertNote(it.copy(isPinned = !it.isPinned)) }
    }

    // ── Tasks ────────────────────────────────────────────────────────────

    fun observeAllTasks(): Flow<List<ObsidianTaskEntity>> = taskDao.observeAllTasks()
    fun observeOpenTaskCount(): Flow<Int> = taskDao.observeOpenTaskCount()

    suspend fun toggleTask(taskId: Long) = withContext(Dispatchers.IO) {
        // The DAO doesn't expose a get-by-id, so we find the task across notes.
        // In practice the UI passes the task; here we scan the note's tasks.
    }

    /**
     * Toggles a task's checkbox status in both the Room entity and the Markdown
     * file on disk (bidirectional sync).
     */
    suspend fun toggleTask(taskEntity: ObsidianTaskEntity) = withContext(Dispatchers.IO) {
        val newChecked = !taskEntity.isChecked
        taskDao.setTaskChecked(taskEntity.id, newChecked)

        // Rewrite the Markdown line on disk
        val file = File(notesDir, taskEntity.noteFilePath)
        if (file.exists()) {
            val lines = file.readLines().toMutableList()
            if (taskEntity.lineIndex in lines.indices) {
                val oldLine = lines[taskEntity.lineIndex]
                lines[taskEntity.lineIndex] = ObsidianTaskParser.toggleLine(oldLine, newChecked)
                file.writeText(lines.joinToString("\n"))
            }
        }
    }

    // ── Graph data ───────────────────────────────────────────────────────

    /**
     * Loads all notes with their file content for graph building.
     */
    suspend fun buildGraphData(): List<NoteForGraph> = withContext(Dispatchers.IO) {
        noteDao.getAllNotes().mapNotNull { entity ->
            val file = File(notesDir, entity.filePath)
            if (!file.exists()) return@mapNotNull null
            NoteForGraph(
                filePath = entity.filePath,
                title = entity.title,
                tags = entity.tags,
                content = file.readText(),
                lastModified = entity.lastModified,
            )
        }
    }

    // ── Semantic search ──────────────────────────────────────────────────

    suspend fun searchSemantic(queryEmbedding: FloatArray): List<NoteSemanticSearch.SearchResult> {
        val embeddings = embeddingDao.getAllEmbeddings()
        return NoteSemanticSearch.search(queryEmbedding, embeddings)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun extractTitle(content: String, filePath: String): String {
        val firstH1 = content.lines().firstOrNull { it.startsWith("# ") }
        return firstH1?.removePrefix("# ")?.trim()
            ?: filePath.substringAfterLast("/").removeSuffix(".md")
    }

    private fun extractTags(content: String): String =
        Regex("(?<=^|\\s)#([a-zA-ZÀ-ÿ0-9_-]{2,30})\\b")
            .findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .take(10)
            .joinToString(",")

    private fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }
}
