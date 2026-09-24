package com.newoether.agora.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaSearchResponseTest {

    @Test
    fun `normalizes a valid exa response with text and score`() {
        val body = """
            {
              "requestId": "abc",
              "results": [
                {"title": "LLM Overview", "url": "https://arxiv.org/abs/1", "text": "A survey of LLMs", "score": 0.91},
                {"title": "Second", "url": "https://example.com/2", "text": "More content"}
              ]
            }
        """.trimIndent()

        val normalized = normalizeExaSearchResponse(body, "llm", 5)
        assertTrue(normalized.contains("\"type\":\"web_search\""))
        assertTrue(normalized.contains("\"query\":\"llm\""))
        assertTrue(normalized.contains("LLM Overview"))
        assertTrue(normalized.contains("https://arxiv.org/abs/1"))
        assertTrue(normalized.contains("A survey of LLMs"))
        assertTrue(normalized.contains("0.91"))
        assertFalse(normalized.contains("\"error\""))
    }

    @Test
    fun `empty or missing results map to no_results`() {
        assertTrue(normalizeExaSearchResponse("""{"results":[]}""", "q", 5).contains("\"error\":\"no_results\""))
        assertTrue(normalizeExaSearchResponse("""{"requestId":"x"}""", "q", 5).contains("\"error\":\"no_results\""))
        assertTrue(normalizeExaSearchResponse("not json", "q", 5).contains("\"error\":\"no_results\""))
    }

    @Test
    fun `results without url are skipped and numResults is honored`() {
        val body = """
            {
              "results": [
                {"title": "No URL", "text": "skip me"},
                {"title": "One", "url": "https://a.example", "text": "1"},
                {"title": "Two", "url": "https://b.example", "text": "2"},
                {"title": "Three", "url": "https://c.example", "text": "3"}
              ]
            }
        """.trimIndent()

        val normalized = normalizeExaSearchResponse(body, "q", 2)
        assertFalse(normalized.contains("No URL"))
        assertTrue(normalized.contains("One"))
        assertTrue(normalized.contains("Two"))
        assertFalse(normalized.contains("Three"))
    }

    @Test
    fun `request body carries query numResults type and text contents`() {
        val payload = exaSearchRequestBody("test query", 7, "auto")
        assertTrue(payload.contains("\"query\":\"test query\""))
        assertTrue(payload.contains("\"numResults\":7"))
        assertTrue(payload.contains("\"type\":\"auto\""))
        assertTrue(payload.contains("\"text\""))
        assertTrue(payload.contains("\"maxCharacters\":1000"))
    }
}
