package com.newoether.agora.ui.chat.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.fulllive.FullLiveApp
import com.newoether.agora.fulllive.data.local.SecurityVault
import com.newoether.agora.fulllive.viewmodel.FullLiveViewModel

/**
 * Process-wide controller for showing/hiding the FullLive voice assistant.
 */
object FullLiveController {
    var visible by mutableStateOf(false)
        private set

    fun open() { visible = true }
    fun close() { visible = false }
}

/**
 * Full-screen dialog hosting the native FullLive voice assistant.
 *
 * API keys configured in FullFlow's Settings → Providers are automatically
 * bridged to FullLive's SecurityVault so the user never re-enters them.
 *
 * FullLive runs its own Room database ("fullive_database"), its own audio
 * engine, and its own UI — completely self-contained within FullFlow.
 */
@Composable
fun FullLiveHost() {
    if (!FullLiveController.visible) return

    val context = LocalContext.current
    val viewModel: FullLiveViewModel = viewModel()

    // Bridge API keys from FullFlow settings → FullLive SecurityVault (one-shot)
    LaunchedEffect(Unit) {
        val container = runCatching {
            (context.applicationContext as? com.newoether.agora.AgoraApplication)?.requireContainer()
        }.getOrNull() ?: return@LaunchedEffect

        val settings = container.settingsRepository
        val apiKeys = settings.apiKeys.value

        fun keyFor(providerName: String): String =
            apiKeys.firstOrNull { it.provider.equals(providerName, ignoreCase = true) }?.key.orEmpty()

        val vault = viewModel.securityVault
        val openaiKey = keyFor("openai")
        val geminiKey = keyFor("google").ifBlank { keyFor("gemini") }
        val xaiKey = keyFor("xai").ifBlank { keyFor("grok") }
        val zenmuxKey = keyFor("zenmux")

        // Only inject if FullLive doesn't already have a key (don't overwrite user choices)
        if (openaiKey.isNotBlank() && vault.getOpenAiKey().isBlank()) vault.setOpenAiKey(openaiKey)
        if (geminiKey.isNotBlank() && vault.getGeminiKey().isBlank()) vault.setGeminiKey(geminiKey)
        if (xaiKey.isNotBlank() && vault.getXaiKey().isBlank()) vault.setXaiKey(xaiKey)
        // ZenMux is OpenAI-compatible — inject as OpenAI key if OpenAI is empty
        if (zenmuxKey.isNotBlank() && vault.getOpenAiKey().isBlank() && openaiKey.isBlank()) {
            vault.setOpenAiKey(zenmuxKey)
        }

        // --- Second Brain bridge ---
        // Inject active memory + note catalog into every FullLive conversation
        val memoryManager = container.memoryManager
        viewModel.brainContextProvider = {
            val activeMemory = memoryManager.getActiveMemory().take(600)
            val notes = memoryManager.listFiles().take(10).joinToString("\n") { n ->
                val desc = n.description.ifBlank { memoryManager.readFile(n.name).take(80).replace("\n", " ") }
                "- ${n.name} : $desc"
            }
            buildString {
                if (activeMemory.isNotBlank()) {
                    appendLine("## Mémoire active")
                    appendLine(activeMemory)
                }
                if (notes.isNotBlank()) {
                    appendLine("## Notes archivées")
                    appendLine(notes)
                }
            }
        }

        // Save insights back to FullFlow's vault as notes
        val notesDir = java.io.File(context.filesDir, "notes")
        viewModel.saveInsightToBrain = { fact ->
            val timestamp = System.currentTimeMillis()
            val repo = com.newoether.agora.data.notes.NoteRepository(
                noteDao = container.database.noteDao(),
                taskDao = container.database.obsidianTaskDao(),
                embeddingDao = container.database.noteEmbeddingDao(),
                notesDir = notesDir,
                generateEmbedding = { text ->
                    val generator = com.newoether.agora.service.BrainEmbeddingService.getGenerator(context)
                    generator(text)
                },
            )
            val title = fact.take(50)
            val filePath = "Insights/Insight_$timestamp.md"
            val content = "# $title\n\n- $fact\n\n_Détecté lors d'une session FullLive._"
            repo.saveNote(filePath, content)
        }
    }

    Dialog(
        onDismissRequest = { FullLiveController.close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            FullLiveApp(
                viewModel = viewModel,
                onShareText = { content, title ->
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, content)
                        putExtra(android.content.Intent.EXTRA_TITLE, title)
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, title))
                },
            )
        }
    }
}
