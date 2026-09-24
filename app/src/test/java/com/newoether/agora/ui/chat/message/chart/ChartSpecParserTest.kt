package com.newoether.agora.ui.chat.message.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSpecParserTest {

    @Test
    fun `parses a valid bar chart spec`() {
        val raw = """
            {
              "type": "bar",
              "title": "Ventes trimestrielles",
              "labels": ["T1", "T2", "T3"],
              "series": [{"name": "2026", "values": [12.5, 9.0, 14.2]}]
            }
        """.trimIndent()

        val spec = ChartSpecParser.parse(raw)
        assertNotNull(spec)
        spec!!
        assertEquals(ChartType.BAR, spec.chartType)
        assertEquals("Ventes trimestrielles", spec.title)
        assertEquals(listOf("T1", "T2", "T3"), spec.labels)
        assertEquals(1, spec.series.size)
        assertEquals(listOf(12.5, 9.0, 14.2), spec.series.first().values)
    }

    @Test
    fun `maps line pie and unknown types correctly`() {
        assertEquals(ChartType.LINE, ChartSpecParser.parse("""{"type":"line","series":[{"values":[1.0]}]}""")!!.chartType)
        assertEquals(ChartType.PIE, ChartSpecParser.parse("""{"type":"pie","series":[{"values":[1.0]}]}""")!!.chartType)
        assertEquals(ChartType.PIE, ChartSpecParser.parse("""{"type":"camembert","series":[{"values":[1.0]}]}""")!!.chartType)
        // Unknown types default to bar rather than failing.
        assertEquals(ChartType.BAR, ChartSpecParser.parse("""{"type":"radar","series":[{"values":[1.0]}]}""")!!.chartType)
    }

    @Test
    fun `tolerates markdown fences and surrounding prose`() {
        val raw = """
            Voici le tableau de bord demandé :
            ```json
            {"type":"bar","labels":["A","B"],"series":[{"name":"S","values":[1,2]}]}
            ```
        """.trimIndent()

        val spec = ChartSpecParser.parse(raw)
        assertNotNull(spec)
        assertEquals(listOf("A", "B"), spec!!.labels)
    }

    @Test
    fun `pads short labels and truncates extra ones`() {
        val padded = ChartSpecParser.parse("""{"series":[{"values":[1.0,2.0,3.0]}]}""")
        assertNotNull(padded)
        assertEquals(3, padded!!.labels.size)

        val truncated = ChartSpecParser.parse("""{"labels":["A","B","C","D"],"series":[{"values":[1.0,2.0]}]}""")
        assertNotNull(truncated)
        assertEquals(listOf("A", "B"), truncated!!.labels)
    }

    @Test
    fun `drops empty series and rejects specs without usable values`() {
        val mixed = ChartSpecParser.parse(
            """{"series":[{"name":"vide","values":[]},{"name":"ok","values":[3.0]}]}"""
        )
        assertNotNull(mixed)
        assertEquals(1, mixed!!.series.size)
        assertEquals("ok", mixed.series.first().name)

        assertNull(ChartSpecParser.parse("""{"type":"bar","series":[{"values":[]}]}"""))
        assertNull(ChartSpecParser.parse("""{"type":"bar","series":[]}"""))
    }

    @Test
    fun `pie keeps only the first series`() {
        val spec = ChartSpecParser.parse(
            """{"type":"pie","labels":["A","B"],"series":[{"values":[1.0,2.0]},{"values":[3.0,4.0]}]}"""
        )
        assertNotNull(spec)
        assertEquals(1, spec!!.series.size)
        assertEquals(listOf(1.0, 2.0), spec.series.first().values)
    }

    @Test
    fun `returns null on blank malformed or valueless input`() {
        assertNull(ChartSpecParser.parse(""))
        assertNull(ChartSpecParser.parse("   "))
        assertNull(ChartSpecParser.parse("pas de json ici"))
        assertNull(ChartSpecParser.parse("{broken json"))
        assertNull(ChartSpecParser.parse("[1,2,3]"))
        assertNull(ChartSpecParser.parse("""{"labels":["A"]}"""))
    }
}
