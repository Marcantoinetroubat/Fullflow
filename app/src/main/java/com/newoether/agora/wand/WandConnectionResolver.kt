package com.newoether.agora.wand

import com.newoether.agora.mcp.McpRegistry
import com.newoether.agora.mcp.McpToolDescriptor

/**
 * Maps each wand [ConnectionId] to the Zapier MCP actions that back it, matched
 * against the tools actually discovered on the connected MCP servers.
 *
 * Truth of UI: a connection is "wired" only when at least one enabled remote
 * tool matches one of its patterns — never assumed, always measured.
 */
object WandConnectionResolver {

    /** Case-insensitive name fragments identifying each connector's Zapier actions. */
    private val PATTERNS: Map<ConnectionId, List<String>> = mapOf(
        ConnectionId.DRIVE to listOf("drive"),
        ConnectionId.DOCS to listOf("docs", "document"),
        ConnectionId.GITHUB to listOf("github"),
        ConnectionId.YOUTUBE to listOf("youtube"),
        ConnectionId.GOOGLE_FINANCE to listOf("finance", "stock", "ticker"),
        ConnectionId.NEWS to listOf("news"),
        ConnectionId.GMAIL to listOf("gmail", "mail"),
        ConnectionId.CALENDAR to listOf("calendar", "event"),
        ConnectionId.WEB_SERPER to listOf("serper", "search"),
    )

    /** Fragments identifying read/search actions usable for context pre-fetch. */
    private val READ_HINTS = listOf("search", "find", "list", "get", "read", "fetch", "look")

    /** Pure matcher (unit-tested): does [toolName] belong to connector [id]? */
    fun matches(toolName: String, id: ConnectionId): Boolean {
        val fragments = PATTERNS[id] ?: return false
        val name = toolName.lowercase()
        return fragments.any { fragment -> name.contains(fragment) }
    }

    fun toolsFor(registry: McpRegistry, id: ConnectionId): List<McpToolDescriptor> {
        return registry.enabledTools().filter { descriptor ->
            matches(descriptor.remote.name, id)
        }
    }

    fun isWired(registry: McpRegistry, id: ConnectionId): Boolean =
        toolsFor(registry, id).isNotEmpty()

    /** Per-connection wiring status for the preview (truth label, measured live). */
    fun wiringStatus(registry: McpRegistry, ids: List<ConnectionId>): Map<ConnectionId, Boolean> =
        ids.associateWith { isWired(registry, it) }

    /**
     * Picks the best read/search action for context pre-fetch: prefers a remote
     * name containing a read hint, then the first action of the connector.
     */
    fun bestReadTool(registry: McpRegistry, id: ConnectionId): McpToolDescriptor? {
        val tools = toolsFor(registry, id)
        if (tools.isEmpty()) return null
        return tools.firstOrNull { descriptor ->
            val name = descriptor.remote.name.lowercase()
            READ_HINTS.any { hint -> name.contains(hint) }
        } ?: tools.first()
    }
}
