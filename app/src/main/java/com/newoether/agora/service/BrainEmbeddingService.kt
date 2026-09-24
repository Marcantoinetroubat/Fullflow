package com.newoether.agora.service

import android.content.Context
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.EmbeddingClient
import com.newoether.agora.data.local.NoteEmbeddingEntity
import com.newoether.agora.data.notes.NoteChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object BrainEmbeddingService {
    /**
     * Resolves and returns a lambda that generates embeddings based on the user's active
     * embedding model and providers configured in Settings.
     */
    suspend fun getGenerator(context: Context): suspend (String) -> FloatArray? {
        val app = context.applicationContext as? AgoraApplication
        val repo = app?.requireContainer()?.settingsRepository ?: return { null }
        
        return { text ->
            val modelId = repo.activeEmbeddingModelId.value.ifBlank { "text-embedding-3-small" }
            val providerName = if (modelId.contains(":")) {
                modelId.substringBefore(":")
            } else {
                "openai"
            }
            val apiKey = repo.resolveActiveKey(providerName) 
                ?: repo.resolveActiveKey("openai") 
                ?: ""
            
            if (apiKey.isBlank()) {
                null
            } else {
                val customUrls = repo.providerBaseUrls.value
                val baseUrl = customUrls[providerName] ?: "https://api.openai.com/v1"
                val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
                val cleanUrl = if (cleanBaseUrl.endsWith("/v1")) cleanBaseUrl else "$cleanBaseUrl/v1"

                EmbeddingClient.computeEmbedding(
                    text = text,
                    apiKey = apiKey,
                    model = if (modelId.contains(":")) modelId.substringAfter(":") else modelId,
                    baseUrl = cleanUrl
                )
            }
        }
    }

    /**
     * Reindexes all existing notes and brain chunks using the active embedding model.
     * Reports progress via the [onProgress] callback.
     */
    suspend fun reindexAll(
        context: Context,
        onProgress: (index: Int, total: Int, currentItemTitle: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? AgoraApplication ?: return@withContext false
        val container = app.requireContainer()
        val db = container.database
        val generator = getGenerator(context)

        val notes = db.noteDao().getAllNotes()
        val total = notes.size
        if (total == 0) return@withContext true

        notes.forEachIndexed { idx, note ->
            withContext(Dispatchers.Main) {
                onProgress(idx + 1, total, note.title)
            }

            val file = File(File(context.filesDir, "notes"), note.filePath)
            if (file.exists()) {
                val content = file.readText()
                val chunks = NoteChunker.chunkText(note.title, content)
                
                db.noteEmbeddingDao().deleteEmbeddingsForNote(note.filePath)
                val embeddings = chunks.mapIndexedNotNull { chunkIdx, chunk ->
                    val vector = generator(chunk) ?: return@mapIndexedNotNull null
                    val buffer = ByteBuffer.allocate(vector.size * 4)
                    buffer.asFloatBuffer().put(vector)
                    NoteEmbeddingEntity(
                        noteFilePath = note.filePath,
                        chunkIndex = chunkIdx,
                        chunkText = chunk,
                        embedding = buffer.array(),
                        dimension = vector.size
                    )
                }
                if (embeddings.isNotEmpty()) {
                    db.noteEmbeddingDao().insertEmbeddings(embeddings)
                }
            }
        }
        true
    }
}
