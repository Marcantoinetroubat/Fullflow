package com.newoether.agora.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianTaskParserTest {

    @Test
    fun `parseTasks extracts unchecked and checked tasks`() {
        val content = """
            # My Note
            
            - [ ] Buy groceries
            - [x] Done task
            - [/] In progress task
            Regular text line
        """.trimIndent()

        val tasks = ObsidianTaskParser.parseTasks("test/note.md", content)
        assertEquals(3, tasks.size)
        assertFalse(tasks[0].isChecked)
        assertTrue(tasks[1].isChecked)
        assertFalse(tasks[2].isChecked)
    }

    @Test
    fun `parseTasks extracts priority from emoji and hashtag`() {
        val content = """
            - [ ] 🔺 Urgent task
            - [ ] 🔼 Medium task
            - [ ] 🔽 Low task
            - [ ] #p1 Hash high
            - [ ] #p2 Hash medium
            - [ ] #p3 Hash low
        """.trimIndent()

        val tasks = ObsidianTaskParser.parseTasks("test/note.md", content)
        assertEquals("HIGH", tasks[0].priority)
        assertEquals("MEDIUM", tasks[1].priority)
        assertEquals("LOW", tasks[2].priority)
        assertEquals("HIGH", tasks[3].priority)
        assertEquals("MEDIUM", tasks[4].priority)
        assertEquals("LOW", tasks[5].priority)
    }

    @Test
    fun `parseTasks extracts due date`() {
        val content = "- [ ] Submit report 📅 2025-03-15"
        val tasks = ObsidianTaskParser.parseTasks("test/note.md", content)
        assertEquals(1, tasks.size)
        assertEquals("2025-03-15", tasks[0].dueDate)
    }

    @Test
    fun `parseTasks cleans description of metadata`() {
        val content = "- [ ] 🔺 Submit report 📅 2025-03-15"
        val tasks = ObsidianTaskParser.parseTasks("test/note.md", content)
        assertEquals("Submit report", tasks[0].text)
    }

    @Test
    fun `parseTasks returns empty for no tasks`() {
        val content = "# Just a note\n\nNo tasks here."
        val tasks = ObsidianTaskParser.parseTasks("test/note.md", content)
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `isTaskLine identifies checkbox lines`() {
        assertTrue(ObsidianTaskParser.isTaskLine("- [ ] task"))
        assertTrue(ObsidianTaskParser.isTaskLine("- [x] task"))
        assertTrue(ObsidianTaskParser.isTaskLine("- [/] task"))
        assertTrue(ObsidianTaskParser.isTaskLine("  - [ ] indented"))
        assertFalse(ObsidianTaskParser.isTaskLine("Regular text"))
        assertFalse(ObsidianTaskParser.isTaskLine("- list item"))
    }

    @Test
    fun `toggleLine marks todo as done with date`() {
        val result = ObsidianTaskParser.toggleLine("- [ ] my task", isChecked = true)
        assertTrue(result.startsWith("- [x]"))
        assertTrue(result.contains("my task"))
        assertTrue(result.contains("✅"))
    }

    @Test
    fun `toggleLine marks done as todo removing date`() {
        val result = ObsidianTaskParser.toggleLine("- [x] my task ✅ 2025-01-01", isChecked = false)
        assertTrue(result.startsWith("- [ ]"))
        assertFalse(result.contains("✅"))
        assertTrue(result.contains("my task"))
    }

    @Test
    fun `toggleLine preserves existing done date when re-checking`() {
        val result = ObsidianTaskParser.toggleLine("- [x] task ✅ 2025-01-01", isChecked = true)
        assertTrue(result.startsWith("- [x]"))
        assertTrue(result.contains("✅ 2025-01-01"))
    }

    @Test
    fun `toggleLine returns original for non-task lines`() {
        val result = ObsidianTaskParser.toggleLine("Just text", isChecked = true)
        assertEquals("Just text", result)
    }

    @Test
    fun `contentHash is deterministic and content-sensitive`() {
        val hash1 = ObsidianTaskParser.contentHash("hello")
        val hash2 = ObsidianTaskParser.contentHash("hello")
        val hash3 = ObsidianTaskParser.contentHash("world")
        assertEquals(hash1, hash2)
        assertTrue(hash1 != hash3)
        assertEquals(32, hash1.length)
    }

    @Test
    fun `null priority when no priority marker`() {
        val tasks = ObsidianTaskParser.parseTasks("n.md", "- [ ] plain task")
        assertNull(tasks[0].priority)
    }
}
