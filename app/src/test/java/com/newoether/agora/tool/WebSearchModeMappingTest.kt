package com.newoether.agora.tool

import com.newoether.agora.data.normalizeWebSearchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchModeMappingTest {

    @Test
    fun `mode normalization defaults to adaptive and is case-insensitive`() {
        assertEquals("adaptive", normalizeWebSearchMode(null))
        assertEquals("adaptive", normalizeWebSearchMode(""))
        assertEquals("adaptive", normalizeWebSearchMode("weird"))
        assertEquals("quick", normalizeWebSearchMode("Quick"))
        assertEquals("deep", normalizeWebSearchMode(" DEEP "))
        assertEquals("adaptive", normalizeWebSearchMode("adaptive"))
    }

    @Test
    fun `tavily depth is basic only in quick mode`() {
        assertEquals("basic", tavilySearchDepth("quick"))
        assertEquals("advanced", tavilySearchDepth("adaptive"))
        assertEquals("advanced", tavilySearchDepth("deep"))
    }

    @Test
    fun `exa type maps quick to instant deep to deep-lite else auto`() {
        assertEquals("instant", exaSearchType("quick"))
        assertEquals("auto", exaSearchType("adaptive"))
        assertEquals("deep-lite", exaSearchType("deep"))
    }

    @Test
    fun `quick mode caps result count at 3 other modes at 10`() {
        assertEquals(3, maxNumResultsForMode("quick"))
        assertEquals(10, maxNumResultsForMode("adaptive"))
        assertEquals(10, maxNumResultsForMode("deep"))
    }

    @Test
    fun `deep mode defaults to 10 results other modes keep the configured value`() {
        assertEquals(10, defaultNumResultsForMode("deep", 5))
        assertEquals(5, defaultNumResultsForMode("adaptive", 5))
        assertEquals(3, defaultNumResultsForMode("quick", 3))
        // Configured values stay clamped to the 1..10 contract.
        assertEquals(10, defaultNumResultsForMode("adaptive", 42))
        assertEquals(1, defaultNumResultsForMode("adaptive", 0))
    }
}
