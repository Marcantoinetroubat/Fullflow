package com.newoether.agora.data.notes

/**
 * Splits a Markdown note into semantic chunks for embedding generation.
 *
 * Strategy: paragraph-level split (double newline), then sentence-level
 * sub-split when a paragraph exceeds 300 chars, with a ~250 char window.
 * Each chunk is prefixed with the note title for context.
 *
 * Pure Kotlin — JVM-testable.
 */
object NoteChunker {

    private const val MAX_PARAGRAPH_CHARS = 300
    private const val CHUNK_WINDOW_CHARS = 250

    fun chunkText(title: String, content: String): List<String> {
        val chunks = mutableListOf<String>()
        val paragraphs = content.split(Regex("\n\n+"))

        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.length > MAX_PARAGRAPH_CHARS) {
                val sentences = trimmed.split(Regex("(?<=[.!?])\\s+"))
                val current = StringBuilder()
                for (sentence in sentences) {
                    if (current.length + sentence.length > CHUNK_WINDOW_CHARS) {
                        if (current.isNotEmpty()) chunks.add("[ $title ] ${current.toString().trim()}")
                        current.clear()
                        current.append(sentence)
                    } else {
                        if (current.isNotEmpty()) current.append(" ")
                        current.append(sentence)
                    }
                }
                if (current.isNotEmpty()) chunks.add("[ $title ] ${current.toString().trim()}")
            } else {
                chunks.add("[ $title ] $trimmed")
            }
        }

        if (chunks.isEmpty() && title.isNotBlank()) {
            chunks.add("[ $title ] $content")
        }

        return chunks
    }
}
