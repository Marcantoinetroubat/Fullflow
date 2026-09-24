package com.newoether.agora.data.notes

import com.newoether.agora.data.local.NoteEmbeddingEntity
import kotlin.math.sqrt

/**
 * Cosine similarity search over note chunk embeddings.
 *
 * Works with any embedding dimension (FullFlow uses llama.cpp or OpenAI
 * remote embeddings — NOT the 64-dim hashing approximation from the original
 * second-cerveau project).
 *
 * Pure Kotlin — JVM-testable.
 */
object NoteSemanticSearch {

    data class SearchResult(
        val noteFilePath: String,
        val chunkText: String,
        val score: Float,
    )

    /**
     * Searches note embeddings for the most similar chunks to the query embedding.
     * Returns results above [minScore] sorted by descending similarity.
     */
    fun search(
        queryEmbedding: FloatArray,
        noteEmbeddings: List<NoteEmbeddingEntity>,
        topK: Int = 5,
        minScore: Float = 0.25f,
    ): List<SearchResult> {
        if (queryEmbedding.isEmpty() || noteEmbeddings.isEmpty()) return emptyList()

        val queryNorm = l2Norm(queryEmbedding)
        if (queryNorm == 0f) return emptyList()

        return noteEmbeddings
            .map { entity ->
                val embedding = toFloatArray(entity.embedding, entity.dimension)
                val score = cosineSimilarity(queryEmbedding, queryNorm, embedding)
                SearchResult(entity.noteFilePath, entity.chunkText, score)
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun cosineSimilarity(query: FloatArray, queryNorm: Float, target: FloatArray): Float {
        val targetNorm = l2Norm(target)
        if (targetNorm == 0f) return 0f

        var dot = 0f
        val len = minOf(query.size, target.size)
        for (i in 0 until len) {
            dot += query[i] * target[i]
        }
        return dot / (queryNorm * targetNorm)
    }

    private fun l2Norm(vector: FloatArray): Float {
        var sum = 0f
        for (v in vector) sum += v * v
        return sqrt(sum)
    }

    private fun toFloatArray(blob: ByteArray, dimension: Int): FloatArray {
        val result = FloatArray(dimension)
        val floatBytes = FloatArray(dimension)
        val expected = dimension * 4
        if (blob.size < expected) return result
        java.nio.ByteBuffer.wrap(blob, 0, expected).asFloatBuffer().get(floatBytes)
        return floatBytes
    }
}
