package com.newoether.agora.data.graph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Builds a knowledge graph from Obsidian ``[[wikilinks]]`` between notes, and
 * exports the vault corpus as unified JSON or Markdown for LLM/RAG ingestion.
 *
 * Pure Kotlin (no Android or Compose imports) — JVM-testable.
 */
object KnowledgeGraphEngine {

    private val wikiLinkRegex = Regex("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]")

    private val nodeColors = longArrayOf(
        0xFF64FFDA, 0xFF7C4DFF, 0xFF00E5FF, 0xFFFFB300,
        0xFFFF5252, 0xFFB388FF, 0xFF69F0AE,
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Builds nodes and bidirectional edges from note content and wikilinks.
     * Nodes are laid out in a galaxy spiral pattern for initial display.
     */
    suspend fun buildGraph(
        notes: List<NoteForGraph>,
        containerWidth: Float = 1000f,
        containerHeight: Float = 1000f,
    ): KnowledgeGraph = withContext(Dispatchers.Default) {
        if (notes.isEmpty()) return@withContext KnowledgeGraph(emptyList(), emptyList())

        val edges = mutableListOf<GraphEdge>()
        val titleToPath = notes.associate { it.title.lowercase().trim() to it.filePath }
        val nameToPath = notes.associate {
            it.filePath.substringAfterLast("/").removeSuffix(".md").lowercase().trim() to it.filePath
        }

        // Find edges from wikilinks
        notes.forEach { note ->
            wikiLinkRegex.findAll(note.content).forEach { match ->
                val targetName = match.groupValues[1].lowercase().trim()
                val targetPath = titleToPath[targetName] ?: nameToPath[targetName]
                if (targetPath != null && targetPath != note.filePath) {
                    val exists = edges.any {
                        (it.sourceId == note.filePath && it.targetId == targetPath) ||
                            (it.sourceId == targetPath && it.targetId == note.filePath)
                    }
                    if (!exists) edges.add(GraphEdge(note.filePath, targetPath))
                }
            }
        }

        // Connection count for sizing
        val connectionCount = mutableMapOf<String, Int>()
        edges.forEach { edge ->
            connectionCount[edge.sourceId] = (connectionCount[edge.sourceId] ?: 0) + 1
            connectionCount[edge.targetId] = (connectionCount[edge.targetId] ?: 0) + 1
        }

        // Galaxy spiral layout
        val centerX = containerWidth / 2f
        val centerY = containerHeight / 2f
        val total = notes.size

        val nodes = notes.mapIndexed { index, note ->
            val angle = (index.toDouble() / total) * 2.0 * PI
            val distance = 180f + (index % 3) * 120f + Random.nextFloat() * 60f
            val links = connectionCount[note.filePath] ?: 0
            GraphNode(
                id = note.filePath,
                title = note.title,
                tags = note.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                linkCount = links,
                posX = (centerX + cos(angle) * distance).toFloat(),
                posY = (centerY + sin(angle) * distance).toFloat(),
                radius = 22f + (links * 7f).coerceAtMost(32f),
                colorValue = nodeColors[Math.abs(note.filePath.hashCode()) % nodeColors.size],
            )
        }

        KnowledgeGraph(nodes, edges)
    }

    /** Generates a JSON corpus export optimised for AI agents / RAG pipelines. */
    suspend fun generateRobotJsonExport(notes: List<NoteForGraph>): String = withContext(Dispatchers.Default) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")

        val noteItems = notes.map { note ->
            val outbound = wikiLinkRegex.findAll(note.content).map { it.groupValues[1] }.distinct().toList()
            val words = note.content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            RobotNoteItem(
                id = note.filePath.hashCode().toString(),
                title = note.title,
                filePath = note.filePath,
                cleanMarkdown = note.content,
                tags = note.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                outboundLinks = outbound,
                wordCount = words,
                chunkCount = (words / 80).coerceAtLeast(1),
                lastModifiedIso = dateFormat.format(java.util.Date(note.lastModified)),
            )
        }

        val graphEdges = noteItems.flatMap { item ->
            item.outboundLinks.map { target -> RobotEdgeItem(item.title, target) }
        }

        json.encodeToString(
            RobotKnowledgeExport(
                totalNotes = notes.size,
                totalConnections = graphEdges.size,
                notes = noteItems,
                graphTopology = graphEdges,
            ),
        )
    }

    /** Generates a single unified Markdown document with TOC + YAML per note. */
    suspend fun generateUnifiedMarkdownExport(notes: List<NoteForGraph>): String = withContext(Dispatchers.Default) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val sb = StringBuilder()

        sb.appendLine("# FullFlow Second Brain — Corpus Unifié pour IA")
        sb.appendLine()
        sb.appendLine("> **Généré le** : ${dateFormat.format(java.util.Date())}")
        sb.appendLine("> **Nombre total de documents** : ${notes.size}")
        sb.appendLine("> **Format** : Markdown standardisé UTF-8, optimisé pour ingestion par LLMs & agents RAG.")
        sb.appendLine()
        sb.appendLine("## Table des Matières")
        sb.appendLine()
        notes.forEachIndexed { i, note ->
            val anchor = note.title.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
            sb.appendLine("${i + 1}. [${note.title}](#$anchor) `[${note.tags}]`")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        notes.forEach { note ->
            sb.appendLine("### ${note.title}")
            sb.appendLine()
            sb.appendLine("```yaml")
            sb.appendLine("path: \"${note.filePath}\"")
            sb.appendLine("tags: [${note.tags}]")
            sb.appendLine("last_modified: \"${dateFormat.format(java.util.Date(note.lastModified))}\"")
            sb.appendLine("```")
            sb.appendLine()
            sb.appendLine(note.content.trim())
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        sb.toString()
    }
}
