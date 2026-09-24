package com.newoether.agora.ui.chat.audio

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.focus.FocusRequester
import com.newoether.agora.viewmodel.ChatViewModel

@Composable
fun FullFlowAudioHost(
    viewModel: ChatViewModel,
    textFieldState: TextFieldState,
    focusRequester: FocusRequester?,
    onOpenGenMail: ((String) -> Unit)? = null,
) {
    if (!FullFlowAudioController.isStudioVisible) return

    val googleApiKey by produceState<String?>(initialValue = null) {
        value = viewModel.settings.awaitActiveKey("google")
    }

    FullFlowAudioStudioDialog(
        visible = FullFlowAudioController.isStudioVisible,
        resolvedApiKey = googleApiKey,
        initialTab = FullFlowAudioController.activeTab,
        initialTtsText = FullFlowAudioController.pendingTtsText,
        onDismissRequest = {
            FullFlowAudioController.close()
        },
        onInsertIntoChat = { textToInsert ->
            val trimmed = textToInsert.trim()
            if (trimmed.isNotBlank()) {
                val currentText = textFieldState.text.toString()
                val updated = if (currentText.isBlank()) trimmed else "$currentText $trimmed"
                textFieldState.edit {
                    replace(0, length, updated)
                }
                focusRequester?.requestFocus()
            }
        },
        onOpenGenMailWithText = onOpenGenMail
    )
}
