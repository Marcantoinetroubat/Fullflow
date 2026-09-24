package com.newoether.agora.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCitationPayloadTest {

    @Test
    fun `web_search results become candidates with provider_used`() {
        val toolJson = """
            {
              "type": "web_search",
              "query": "llm",
              "results": [
                {"title": "A", "url": "https://a.example/x", "description": "desc A"},
                {"title": "B", "url": "https://b.example/y", "content": "content B"}
              ],
              "provider_used": "tavily"
            }
        """.trimIndent()

        val payload = ToolCitationPayload.fromToolResult("web_search", toolJson)
        assertNotNull(payload)
        val candidates = ToolCitationPayload.parse(payload!!)
        assertEquals(2, candidates.size)
        assertEquals("A", candidates[0].title)
        assertEquals("https://a.example/x", candidates[0].url)
        assertEquals("desc A", candidates[0].excerpt)
        assertEquals("web_search:tavily", candidates[0].provider)
        // content falls back into excerpt when description is absent
        assertEquals("content B", candidates[1].excerpt)
    }

    @Test
    fun `web_fetch produces a single host-titled candidate`() {
        val toolJson = """
            {"type": "web_fetch", "url": "https://blog.example.com/post", "text": "full page text"}
        """.trimIndent()

        val payload = ToolCitationPayload.fromToolResult("web_fetch", toolJson)
        assertNotNull(payload)
        val candidates = ToolCitationPayload.parse(payload!!)
        assertEquals(1, candidates.size)
        assertEquals("blog.example.com", candidates[0].title)
        assertEquals("web_fetch", candidates[0].provider)
        assertEquals("full page text", candidates[0].excerpt)
    }

    @Test
    fun `error payloads and unknown tools yield no payload`() {
        assertNull(ToolCitationPayload.fromToolResult("web_search", """{"type":"web_search","error":"no_results"}"""))
        assertNull(ToolCitationPayload.fromToolResult("web_search", """{"type":"web_search","results":[]}"""))
        assertNull(ToolCitationPayload.fromToolResult("shell", """{"type":"shell","stdout":"hi"}"""))
        assertNull(ToolCitationPayload.fromToolResult("web_search", "not json"))
        assertNull(ToolCitationPayload.fromToolResult("web_fetch", """{"type":"web_fetch","error":"no_response"}"""))
    }

    @Test
    fun `candidates without url are dropped`() {
        val toolJson = """
            {
              "type": "web_search",
              "results": [
                {"title": "no url"},
                {"title": "ok", "url": "https://ok.example"}
              ]
            }
        """.trimIndent()

        val candidates = ToolCitationPayload.parse(ToolCitationPayload.fromToolResult("web_search", toolJson)!!)
        assertEquals(1, candidates.size)
        assertEquals("ok", candidates[0].title)
    }

    @Test
    fun `toCitationRecord validates urls through CitationPolicy`() {
        val valid = ToolCitationPayload.toCitationRecord(
            ToolCitationCandidate(title = "A", url = "https://a.example/page", excerpt = "x"),
        )
        assertNotNull(valid)
        assertEquals("web", valid!!.kind)
        assertEquals("https://a.example/page", valid.url)

        val invalid = ToolCitationPayload.toCitationRecord(
            ToolCitationCandidate(title = "", url = "ftp://nope.example/x"),
        )
        assertNull(invalid)
    }

    @Test
    fun `parse tolerates malformed payloads`() {
        assertTrue(ToolCitationPayload.parse("").isEmpty())
        assertTrue(ToolCitationPayload.parse("junk").isEmpty())
        assertTrue(ToolCitationPayload.parse("""{"citations":"nope"}""").isEmpty())
        assertTrue(ToolCitationPayload.parse("""{"citations":[{"title":"sans url"}]}""").isEmpty())
    }
}
