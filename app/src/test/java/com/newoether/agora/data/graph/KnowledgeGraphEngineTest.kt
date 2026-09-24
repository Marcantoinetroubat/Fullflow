package com.newoether.agora.data.graph

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeGraphEngineTest {

    @Test
    fun `buildGraph creates bidirectional edges from wikilinks`() = runBlocking {
        val notes = listOf(
            NoteForGraph("a/note1.md", "Architecture", "", "Links to [[Design]] and [[Testing]]", 0L),
            NoteForGraph("b/design.md", "Design", "", "References [[Architecture]]", 0L),
            NoteForGraph("c/testing.md", "Testing", "", "No links here", 0L),
        )

        val graph = KnowledgeGraphEngine.buildGraph(notes)

        assertEquals(3, graph.nodes.size)
        // Architecture→Design (dedup: Design→Architecture is the same edge)
        // Architecture→Testing (one-directional, but bidirectional dedup means 1 edge)
        assertEquals(2, graph.edges.size)
    }

    @Test
    fun `buildGraph does not create self-loops`() = runBlocking {
        val notes = listOf(
            NoteForGraph("a/self.md", "Self", "", "Links to [[Self]]", 0L),
        )
        val graph = KnowledgeGraphEngine.buildGraph(notes)
        assertEquals(1, graph.nodes.size)
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `buildGraph resolves wikilinks by file name when title differs`() = runBlocking {
        val notes = listOf(
            NoteForGraph("a/note1.md", "My Title", "", "See [[note2]]", 0L),
            NoteForGraph("b/note2.md", "Different Title", "", "Content", 0L),
        )
        val graph = KnowledgeGraphEngine.buildGraph(notes)
        assertEquals(1, graph.edges.size)
        assertEquals("a/note1.md", graph.edges[0].sourceId)
        assertEquals("b/note2.md", graph.edges[0].targetId)
    }

    @Test
    fun `buildGraph handles empty list`() = runBlocking {
        val graph = KnowledgeGraphEngine.buildGraph(emptyList())
        assertTrue(graph.nodes.isEmpty())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `buildGraph assigns colors and radii based on connections`() = runBlocking {
        val notes = listOf(
            NoteForGraph("a/hub.md", "Hub", "", "Links to [[A]] and [[B]]", 0L),
            NoteForGraph("b/a.md", "A", "", "", 0L),
            NoteForGraph("c/b.md", "B", "", "", 0L),
        )
        val graph = KnowledgeGraphEngine.buildGraph(notes)
        val hub = graph.nodes.first { it.id == "a/hub.md" }
        val leaf = graph.nodes.first { it.id == "b/a.md" }
        assertTrue("Hub should have larger radius", hub.radius > leaf.radius)
        assertEquals(2, hub.linkCount)
        assertEquals(1, leaf.linkCount)
    }

    @Test
    fun `generateUnifiedMarkdownExport includes TOC and YAML`() = runBlocking {
        val notes = listOf(
            NoteForGraph("a/note.md", "My Note", "tag1,tag2", "# My Note\n\nContent here", 1000L),
        )
        val export = KnowledgeGraphEngine.generateUnifiedMarkdownExport(notes)
        assertTrue(export.contains("Table des Matières"))
        assertTrue(export.contains("My Note"))
        assertTrue(export.contains("path: \"a/note.md\""))
        assertTrue(export.contains("tags: [tag1,tag2]"))
        assertTrue(export.contains("Content here"))
    }

    @Test
    fun `generateRobotJsonExport produces valid JSON with graph topology`() = runBlocking {
        val notes = listOf(
            NoteForGraph("a/note.md", "Source", "tag", "Links to [[Target]]", 1000L),
            NoteForGraph("b/target.md", "Target", "", "Content", 2000L),
        )
        val json = KnowledgeGraphEngine.generateRobotJsonExport(notes)
        val export = kotlinx.serialization.json.Json.decodeFromString<RobotKnowledgeExport>(json)
        assertEquals(2, export.totalNotes)
        assertEquals(1, export.totalConnections)
        assertTrue(export.graphTopology.any { it.relationType == "wikilink" })
        assertTrue(export.notes.any { it.title == "Source" })
    }
}
