package com.newoether.agora.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFallbackPlannerTest {

    // ── planBackendOrder ────────────────────────────────────────────────

    @Test
    fun `selected provider always comes first`() {
        val order = planBackendOrder(
            selectedProvider = "brave",
            apiKeys = mapOf("brave" to "k", "tavily" to "k"),
            fallbackEnabled = true,
        )
        assertEquals("brave", order.first())
    }

    @Test
    fun `fallback disabled yields only the selected provider`() {
        val order = planBackendOrder(
            selectedProvider = "tavily",
            apiKeys = mapOf("brave" to "k"),
            fallbackEnabled = false,
        )
        assertEquals(listOf("tavily"), order)
    }

    @Test
    fun `providers without api key are skipped except keyless ones`() {
        val order = planBackendOrder(
            selectedProvider = "duckduckgo",
            apiKeys = mapOf("tavily" to "key", "brave" to ""),
            fallbackEnabled = true,
        )
        // brave has a blank key => excluded; tavily has a key => included;
        // searxng and duckduckgo are keyless => included.
        assertTrue(order.contains("tavily"))
        assertFalse(order.contains("brave"))
        assertTrue(order.contains("searxng"))
        assertTrue(order.contains("duckduckgo"))
    }

    @Test
    fun `duckduckgo is always present as last resort and never duplicated`() {
        val withDdgSelected = planBackendOrder("duckduckgo", emptyMap(), true)
        assertEquals(1, withDdgSelected.count { it == "duckduckgo" })

        val withoutDdg = planBackendOrder("kagi", mapOf("kagi" to "k"), true)
        assertTrue(withoutDdg.contains("duckduckgo"))
        assertEquals(1, withoutDdg.count { it == "duckduckgo" })
        assertEquals("duckduckgo", withoutDdg.last())
    }

    @Test
    fun `order contains no duplicates and starts with selected`() {
        val order = planBackendOrder(
            selectedProvider = "exa",
            apiKeys = mapOf("exa" to "k", "tavily" to "k", "kagi" to "k"),
            fallbackEnabled = true,
        )
        assertEquals(order.size, order.distinct().size)
        assertEquals("exa", order.first())
    }

    @Test
    fun `unknown selected provider normalizes to duckduckgo`() {
        val order = planBackendOrder("not-a-provider", emptyMap(), true)
        assertEquals("duckduckgo", order.first())
    }

    // ── fallbackEligibleError ───────────────────────────────────────────

    @Test
    fun `technical failures and no results are fallback eligible`() {
        listOf("no_response", "search_error", "captcha", "network_error", "no_results", "no_api_key")
            .forEach { assertTrue("$it should be eligible", fallbackEligibleError(it)) }
    }

    @Test
    fun `usage errors and null are never fallback eligible`() {
        listOf("no_query", "no_url", "fetch_error", null)
            .forEach { assertFalse("$it should not be eligible", fallbackEligibleError(it)) }
    }

    // ── searchJsonError / annotateSearchJson ────────────────────────────

    @Test
    fun `searchJsonError extracts the error field and tolerates noise`() {
        assertEquals("no_results", searchJsonError("""{"type":"web_search","error":"no_results"}"""))
        assertNull(searchJsonError("""{"type":"web_search","results":[]}"""))
        assertNull(searchJsonError("not json"))
        assertNull(searchJsonError(""))
    }

    @Test
    fun `annotate stamps provider_used always and fallback fields only after attempts`() {
        val direct = annotateSearchJson("""{"type":"web_search","results":[]}""", "tavily", emptyList())
        assertTrue(direct.contains("\"provider_used\":\"tavily\""))
        assertFalse(direct.contains("\"fallback\""))
        assertFalse(direct.contains("\"attempted\""))

        val fellBack = annotateSearchJson("""{"type":"web_search","results":[]}""", "duckduckgo", listOf("tavily", "brave"))
        assertTrue(fellBack.contains("\"provider_used\":\"duckduckgo\""))
        assertTrue(fellBack.contains("\"fallback\":true"))
        assertTrue(fellBack.contains("tavily") && fellBack.contains("brave"))
    }

    @Test
    fun `annotate passes through unparseable payloads untouched`() {
        assertEquals("junk", annotateSearchJson("junk", "tavily", listOf("brave")))
    }
}
