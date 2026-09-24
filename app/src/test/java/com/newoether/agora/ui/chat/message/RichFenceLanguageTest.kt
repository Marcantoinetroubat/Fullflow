package com.newoether.agora.ui.chat.message

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichFenceLanguageTest {

    @Test
    fun `detects mermaid language case-insensitively and with padding`() {
        assertTrue(RichFenceLanguage.isMermaid("mermaid"))
        assertTrue(RichFenceLanguage.isMermaid("Mermaid"))
        assertTrue(RichFenceLanguage.isMermaid("MERMAID"))
        assertTrue(RichFenceLanguage.isMermaid("  mermaid  "))
    }

    @Test
    fun `detects chart language case-insensitively and with padding`() {
        assertTrue(RichFenceLanguage.isChart("chart"))
        assertTrue(RichFenceLanguage.isChart("Chart"))
        assertTrue(RichFenceLanguage.isChart("  CHART "))
    }

    @Test
    fun `rejects null blank and other languages`() {
        assertFalse(RichFenceLanguage.isMermaid(null))
        assertFalse(RichFenceLanguage.isChart(null))
        assertFalse(RichFenceLanguage.isMermaid(""))
        assertFalse(RichFenceLanguage.isChart(""))
        assertFalse(RichFenceLanguage.isMermaid("kotlin"))
        assertFalse(RichFenceLanguage.isChart("json"))
        assertFalse(RichFenceLanguage.isMermaid("mermaidjs"))
        assertFalse(RichFenceLanguage.isChart("flowchart"))
    }

    @Test
    fun `isRichFence covers both supported languages only`() {
        assertTrue(RichFenceLanguage.isRichFence("mermaid"))
        assertTrue(RichFenceLanguage.isRichFence("chart"))
        assertFalse(RichFenceLanguage.isRichFence("python"))
        assertFalse(RichFenceLanguage.isRichFence(null))
    }
}
