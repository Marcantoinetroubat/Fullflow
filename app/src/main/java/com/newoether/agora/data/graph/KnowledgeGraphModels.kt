package com.newoether.agora.data.graph

import kotlinx.serialization.Serializable

/**
 * Knowledge graph data models — pure Kotlin (no Compose dependency) so the
 * engine and its tests stay JVM-only. The UI layer converts [GraphNode.position]
 * to Compose Offset at render time.
 */
data class GraphNode(
    val id: String,
    val title: String,
    val tags: List<String>,
    val linkCount: Int = 0,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val radius: Float = 28f,
    val colorValue: Long = 0xFF64FFDA,
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val weight: Float = 1f,
)

data class KnowledgeGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
)

/**
 * Lightweight note data passed to the graph engine (entity metadata + raw content).
 * Decouples the engine from both Room entities and the file system.
 */
data class NoteForGraph(
    val filePath: String,
    val title: String,
    val tags: String,
    val content: String,
    val lastModified: Long,
)

/** Robot/LLM export schema (JSON optimised for AI agents, RAG pipelines). */
@Serializable
data class RobotKnowledgeExport(
    val schemaVersion: String = "2.0.0",
    val generator: String = "FullFlow Second Brain",
    val exportTimestamp: Long = System.currentTimeMillis(),
    val totalNotes: Int = 0,
    val totalConnections: Int = 0,
    val notes: List<RobotNoteItem> = emptyList(),
    val graphTopology: List<RobotEdgeItem> = emptyList(),
)

@Serializable
data class RobotNoteItem(
    val id: String,
    val title: String,
    val filePath: String,
    val cleanMarkdown: String,
    val tags: List<String>,
    val outboundLinks: List<String>,
    val wordCount: Int,
    val chunkCount: Int,
    val lastModifiedIso: String,
)

@Serializable
data class RobotEdgeItem(
    val source: String,
    val target: String,
    val relationType: String = "wikilink",
)
