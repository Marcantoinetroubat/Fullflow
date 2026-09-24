package com.newoether.agora.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteChunkerTest {

    @Test
    fun `short paragraph produces single chunk with title prefix`() {
        val chunks = NoteChunker.chunkText("My Note", "Short content here.")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("My Note"))
        assertTrue(chunks[0].contains("Short content"))
    }

    @Test
    fun `multiple paragraphs produce one chunk each`() {
        val content = "First paragraph.\n\nSecond paragraph.\n\nThird one."
        val chunks = NoteChunker.chunkText("Title", content)
        assertEquals(3, chunks.size)
        assertTrue(chunks[0].contains("First paragraph"))
        assertTrue(chunks[1].contains("Second paragraph"))
        assertTrue(chunks[2].contains("Third one"))
    }

    @Test
    fun `long paragraph splits into sentence-level chunks`() {
        val longPara = "Sentence one. " + "Word ".repeat(60) + ". Sentence two. " + "More ".repeat(60) + ". Sentence three."
        val chunks = NoteChunker.chunkText("Long", longPara)
        assertTrue("Expected multiple chunks for long paragraph, got ${chunks.size}", chunks.size > 1)
    }

    @Test
    fun `empty content falls back to title-prefixed chunk`() {
        val chunks = NoteChunker.chunkText("Title", "")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("Title"))
    }

    @Test
    fun `blank title and content produces empty list`() {
        val chunks = NoteChunker.chunkText("", "")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `each chunk includes title context`() {
        val chunks = NoteChunker.chunkText("Architecture", "Some content.\n\nMore content.")
        chunks.forEach { chunk ->
            assertTrue("Chunk should contain title context: $chunk", chunk.contains("Architecture"))
        }
    }
}
