package com.newoether.agora.wand

import android.widget.Toast
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.newoether.agora.data.SkillManager
import com.newoether.agora.data.repository.SettingsRepository

/**
 * Bridges the input bar to the Magic Wand: binds the controller, renders the
 * type picker + preview dialogs, and delivers the chosen text back into the
 * shared [TextFieldState] so the ordinary Send path stays byte-identical
 * (cahier des charges §11.6 — no silent substitution).
 */
@Composable
fun WandInputBarHost(
    textFieldState: TextFieldState,
    settings: SettingsRepository,
    skillManager: SkillManager,
    mcpRegistry: com.newoether.agora.mcp.McpRegistry? = null,
) {
    val context = LocalContext.current

    WandController.bind(
        settings = settings,
        skills = skillManager,
        registry = mcpRegistry,
        store = WandSettingsStore.getInstance(context),
    )

    val state = WandController.state
    val errorMessage = WandController.errorMessage

    if (state is WandController.State.Compiling) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color(0xFF101724),
            title = { Text("Baguette en cours…", color = Color.White) },
            text = { CircularProgressIndicator(color = Color(0xFFA78BFA)) },
            confirmButton = {},
        )
    }

    if (state is WandController.State.Ready) {
        WandPreviewDialog(
            preview = state.preview,
            wiringStatus = WandController.wiringStatus(state.preview.connections),
            onSendOptimized = { optimized ->
                // Declare the checked connections for the next generation (whitelist),
                // then replace the composer text and let the user press Send (explicit).
                WandController.declareConnectionsForNextSend(state.preview.connections)
                textFieldState.edit {
                    replace(0, length, optimized)
                }
                WandController.reset()
            },
            onKeepOriginal = {
                // Keep raw text untouched: nothing to write back, nothing declared.
                WandController.reset()
            },
            onDismiss = { WandController.reset() },
        )
    }

    if (errorMessage != null) {
        LaunchedEffect(errorMessage) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            WandController.dismissError()
        }
    }
}
