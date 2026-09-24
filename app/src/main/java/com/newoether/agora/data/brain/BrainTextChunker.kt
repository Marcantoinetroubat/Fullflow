package com.newoether.agora.data.brain

/**
 * Chunks text with overlap for embedding generation. Inspired by the
 * morphic-mobile TextChunker (1800 chars, 250 overlap) — slightly larger
 * windows than [com.newoether.agora.data.notes.NoteChunker] which targets
 * paragraph-level granularity for short notes.
 *
 * Pure Kotlin — JVM-testable.
 */
object BrainTextChunker {

    private const val MAX_CHUNK = 1800
    private const val OVERLAP = 250

    fun chunk(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < content.length) {
            val end = minOf(start + MAX_CHUNK, content.length)
            val chunk = content.substring(start, end).trim()
            if (chunk.length >= 50 || chunks.isEmpty()) {
                chunks.add(chunk)
            }
            if (end >= content.length) break
            start = end - OVERLAP
            if (start < 0) start = 0
        }
        return chunks
    }
}
