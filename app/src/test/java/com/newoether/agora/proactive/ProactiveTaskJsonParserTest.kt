package com.newoether.agora.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveTaskJsonParserTest {

    private fun taskJson(badge: String, title: String, subtitle: String = "sub", prompt: String = "prompt") =
        """{"badge":"$badge","title":"$title","subtitle":"$subtitle","prompt":"$prompt"}"""

    @Test
    fun `parses a valid 3-task array`() {
        val raw = """
            [
              ${taskJson("Reprendre & Poursuivre", "Continuer le projet")},
              ${taskJson("Approfondir & Prototyper", "Cas pratique")},
              ${taskJson("Synergies Second Cerveau", "Croiser avec une note")}
            ]
        """.trimIndent()

        val tasks = ProactiveTaskJsonParser.parse(raw)
        assertEquals(3, tasks.size)
        assertEquals("Reprendre & Poursuivre", tasks[0].badge)
        assertEquals("Continuer le projet", tasks[0].title)
    }

    @Test
    fun `tolerates markdown fences and surrounding prose`() {
        val fenced = "```json\n[${taskJson("A", "T1")}]\n```"
        assertEquals(1, ProactiveTaskJsonParser.parse(fenced).size)

        val prosaic = "Voici mes suggestions :\n[${taskJson("A", "T1")}]\nJ'espère que cela aide."
        assertEquals(1, ProactiveTaskJsonParser.parse(prosaic).size)
    }

    @Test
    fun `filters out tasks with blank title or prompt and caps at 5`() {
        val raw = """
            [
              ${taskJson("A", "T1")},
              ${taskJson("A", "")},
              ${taskJson("A", "T2", prompt = "")},
              ${taskJson("A", "T3")},
              ${taskJson("A", "T4")},
              ${taskJson("A", "T5")},
              ${taskJson("A", "T6")},
              ${taskJson("A", "T7")}
            ]
        """.trimIndent()

        val tasks = ProactiveTaskJsonParser.parse(raw)
        assertEquals(5, tasks.size)
        assertEquals(listOf("T1", "T3", "T4", "T5", "T6"), tasks.map { it.title })
    }

    @Test
    fun `returns empty list on blank malformed or arrayless input`() {
        assertTrue(ProactiveTaskJsonParser.parse("").isEmpty())
        assertTrue(ProactiveTaskJsonParser.parse("   ").isEmpty())
        assertTrue(ProactiveTaskJsonParser.parse("aucun tableau ici").isEmpty())
        assertTrue(ProactiveTaskJsonParser.parse("[{broken").isEmpty())
        assertTrue(ProactiveTaskJsonParser.parse("{}").isEmpty())
        assertTrue(ProactiveTaskJsonParser.parse("[1,2,3]").isEmpty())
    }

    @Test
    fun `ignores unknown fields in task objects`() {
        val raw = """[{"badge":"A","title":"T","subtitle":"S","prompt":"P","extra":42}]"""
        val tasks = ProactiveTaskJsonParser.parse(raw)
        assertEquals(1, tasks.size)
        assertEquals("P", tasks.first().prompt)
    }
}
