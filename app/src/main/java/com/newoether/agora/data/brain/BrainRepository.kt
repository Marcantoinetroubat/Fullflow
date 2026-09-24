package com.newoether.agora.data.brain

import com.newoether.agora.data.local.BrainChunkEntity
import com.newoether.agora.data.local.BrainItemDao
import com.newoether.agora.data.local.BrainChunkDao
import com.newoether.agora.data.local.BrainItemEntity
import com.newoether.agora.data.notes.NoteSemanticSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Orchestrates ingestion of PDFs and web pages into the Second Brain.
 * Each source is chunked, embedded (via FullFlow's real embedding models),
 * and stored in [brain_chunks] for unified semantic search alongside notes.
 *
 * Embedding generation is injected via [generateEmbedding] so the domain
 * layer stays decoupled from llama.cpp / OpenAI. When null, chunks are
 * stored without embeddings (full-text search only).
 */
class BrainRepository(
    private val itemDao: BrainItemDao,
    private val chunkDao: BrainChunkDao,
    private val generateEmbedding: (suspend (text: String) -> FloatArray?)? = null,
) {

    fun observeAllItems(): Flow<List<BrainItemEntity>> = itemDao.observeAllItems()

    suspend fun getReadyItems(): List<BrainItemEntity> = itemDao.getReadyItems()

    /**
     * Fetches, cleans, chunks, and indexes a web page into the Second Brain.
     * Returns the created BrainItem id, or null if the fetch failed.
     */
    suspend fun saveWebPage(url: String): Long? = withContext(Dispatchers.IO) {
        val page = WebPageIngester.fetch(url) ?: return@withContext null

        val item = BrainItemEntity(
            type = "WEB",
            sourceUrl = url,
            title = page.title,
            status = "PROCESSING",
        )
        val itemId = itemDao.upsertItem(item)
        itemDao.updateStatus(itemId, "PROCESSING", null)

        try {
            indexContent(itemId, page.text, pageNumber = 0, title = page.title)
            itemDao.updateStatus(itemId, "READY", null)
            itemId
        } catch (e: Exception) {
            itemDao.updateStatus(itemId, "FAILED", e.message?.take(200))
            itemId
        }
    }

    /**
     * Extracts text from a PDF page-by-page, chunks, and indexes each page
     * into the Second Brain. Preserves page numbers for citations.
     * Returns the created BrainItem id, or null if extraction failed.
     */
    suspend fun savePdf(filePath: String): Long? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        val item = BrainItemEntity(
            type = "PDF",
            sourceUrl = filePath,
            title = file.nameWithoutExtension,
            status = "PROCESSING",
        )
        val itemId = itemDao.upsertItem(item)

        try {
            val result = PdfIngester.extract(file) ?: run {
                itemDao.updateStatus(itemId, "FAILED", "PDF extraction returned null")
                return@withContext itemId
            }

            result.pages.forEach { page ->
                indexContent(itemId, page.text, pageNumber = page.pageNumber, title = result.title)
            }

            itemDao.upsertItem(item.copy(id = itemId, pageCount = result.pageCount, status = "READY"))
            itemId
        } catch (e: Exception) {
            itemDao.updateStatus(itemId, "FAILED", e.message?.take(200))
            itemId
        }
    }

    /**
     * Unified semantic search across all brain chunks (PDF + WEB).
     * Returns the top-K most similar chunks to the query embedding.
     */
    suspend fun searchSemantic(queryEmbedding: FloatArray): List<NoteSemanticSearch.SearchResult> {
        val chunks = chunkDao.getAllChunks()
        val pseudoEmbeddings = chunks.map { chunk ->
            // Reuse the SearchResult infrastructure by wrapping chunk content
            // as a lightweight embedding entity
            com.newoether.agora.data.local.NoteEmbeddingEntity(
                noteFilePath = "brain:${chunk.itemId}",
                chunkIndex = chunk.chunkIndex,
                chunkText = chunk.content,
                embedding = chunk.embedding,
                dimension = chunk.dimension,
            )
        }
        return NoteSemanticSearch.search(queryEmbedding, pseudoEmbeddings)
    }

    suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) {
        itemDao.deleteItem(id)
    }

    /** Full-text search across all brain chunks (PDF + WEB). */
    suspend fun searchContent(query: String): List<BrainChunkEntity> = chunkDao.searchContent(query)

    // ── Internal ──────────────────────────────────────────────────────

    private suspend fun indexContent(
        itemId: Long,
        content: String,
        pageNumber: Int,
        title: String,
    ) {
        val chunks = BrainTextChunker.chunk(content)
        val entities = chunks.mapIndexedNotNull { index, chunk ->
            val vector = generateEmbedding?.invoke(chunk)
            BrainChunkEntity(
                itemId = itemId,
                chunkIndex = index,
                pageNumber = pageNumber,
                content = chunk,
                embedding = vector?.let { toByteArray(it) } ?: ByteArray(0),
                dimension = vector?.size ?: 0,
            )
        }
        if (entities.isNotEmpty()) chunkDao.insertChunks(entities)
    }

    private fun toByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }
}
