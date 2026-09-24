package com.newoether.agora.wand

import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.mcp.McpRegistry
import com.newoether.agora.tool.ToolExecutionEvent
import com.newoether.agora.tool.ToolExecutionResult
import com.newoether.agora.tool.ToolPresentationMetadata
import com.newoether.agora.tool.ToolProvider
import com.newoether.agora.util.DebugLog
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Magic Wand connector whitelist (cahier des charges §4.4) backed by Zapier MCP.
 *
 * Only the connectors checked in the wand preview ([GenerationContext.wandConnections])
 * are exposed to the deep model for the current generation, and they are exposed as
 * the REAL Zapier actions discovered on the connected MCP servers — never as stubs.
 * A connector with no matching wired action exposes nothing (truth of UI).
 */
class WandConnectionToolProvider(
    private val registry: McpRegistry,
) : ToolProvider {

    companion object {
        private const val TAG = "WandConnectionTools"
        private const val PREFETCH_TIMEOUT_MS = 6_000L
        private const val PREFETCH_MAX_CHARS = 1_500
    }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (ctx.wandConnections.isEmpty()) return emptyList()
        return ctx.wandConnections.flatMap { raw ->
            val id = runCatching { ConnectionId.valueOf(raw) }.getOrNull()
                ?: return@flatMap emptyList()
            WandConnectionResolver.toolsFor(registry, id).map { it.asToolDefinition() }
        }.distinctBy { it.function.name }
    }

    override fun handles(name: String): Boolean = registry.descriptor(name) != null

    override fun presentationMetadata(name: String): ToolPresentationMetadata? =
        registry.descriptor(name)?.let { descriptor ->
            ToolPresentationMetadata(
                displayName = descriptor.remote.name,
                target = descriptor.serverName,
            )
        }

    override suspend fun execute(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): String = registry.execute(name, arguments).text

    override fun executeEvents(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): Flow<ToolExecutionEvent> = flow {
        val descriptor = registry.descriptor(name)
        // Whitelist enforcement: the tool must belong to a checked connector.
        val allowed = descriptor != null && ctx.wandConnections.any { raw ->
            runCatching { ConnectionId.valueOf(raw) }.getOrNull()?.let { id ->
                WandConnectionResolver.toolsFor(registry, id).any { it.publicName == name }
            } == true
        }
        if (!allowed) {
            emit(
                ToolExecutionEvent.Completed(
                    ToolExecutionResult(
                        "Outil '$name' hors liste blanche des connexions déclarées.",
                        isError = true,
                    ),
                ),
            )
            return@flow
        }
        descriptor?.serverName?.let { emit(ToolExecutionEvent.TargetResolved(it)) }
        emit(ToolExecutionEvent.Progress("Interrogation via ${descriptor?.serverName}"))
        emit(ToolExecutionEvent.Completed(registry.execute(name, arguments)))
    }

    /**
     * Pre-fetch context for the wand compilation (§4.4 usage a): for every checked,
     * wired connector, call its best read/search action with the raw text as query.
     * Bounded per connector (timeout + truncation); failures are silent (compile
     * must never block on a connector).
     */
    suspend fun prefetchContext(
        connections: List<ConnectionId>,
        rawText: String,
    ): String = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext ""
        buildString {
            connections.forEach { id ->
                val tool = WandConnectionResolver.bestReadTool(registry, id) ?: return@forEach
                val result = withTimeoutOrNull(PREFETCH_TIMEOUT_MS) {
                    runCatching {
                        registry.execute(
                            tool.publicName,
                            """{"query": ${'"'}$rawText${'"'}}""",
                        )
                    }.getOrNull()
                }
                val text = result?.text?.takeIf { it.isNotBlank() } ?: return@forEach
                appendLine("── ${id.label} (${tool.remote.name}) ──")
                appendLine(text.take(PREFETCH_MAX_CHARS))
                appendLine()
            }
        }.trim()
    }

    /** Live wiring status per connector, for the preview truth label. */
    fun wiringStatus(connections: List<ConnectionId>): Map<ConnectionId, Boolean> =
        WandConnectionResolver.wiringStatus(registry, connections)

    init {
        DebugLog.d(TAG, "WandConnectionToolProvider bound to MCP registry")
    }
}
