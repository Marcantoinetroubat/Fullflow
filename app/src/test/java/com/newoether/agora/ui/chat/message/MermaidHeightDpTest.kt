package com.newoether.agora.ui.chat.message

import org.junit.Assert.assertEquals
import org.junit.Test

class MermaidHeightDpTest {

    @Test
    fun `coerces below minimum to 120`() {
        assertEquals(120, mermaidHeightDp(0))
        assertEquals(120, mermaidHeightDp(-10))
        assertEquals(120, mermaidHeightDp(50))
        assertEquals(119, 119) // boundary just below
        assertEquals(120, mermaidHeightDp(120))
    }

    @Test
    fun `coerces above maximum to 440`() {
        assertEquals(440, mermaidHeightDp(441))
        assertEquals(440, mermaidHeightDp(1000))
        assertEquals(440, mermaidHeightDp(10000))
    }

    @Test
    fun `preserves values within bounds`() {
        assertEquals(180, mermaidHeightDp(180))
        assertEquals(300, mermaidHeightDp(300))
        assertEquals(440, mermaidHeightDp(440))
    }
}
