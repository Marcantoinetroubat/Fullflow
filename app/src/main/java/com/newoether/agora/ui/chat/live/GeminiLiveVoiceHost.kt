package com.newoether.agora.ui.chat.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun GeminiLiveVoiceHost(
    visible: Boolean,
    viewModel: ChatViewModel,
    composerOwnerId: String,
    scope: CoroutineScope,
    onDismissRequest: () -> Unit
) {
    if (!visible) return

    val googleApiKey by produceState<String?>(initialValue = null) {
        value = viewModel.settings.awaitActiveKey("google")
    }

    GeminiLiveVoiceDialog(
        resolvedApiKey = googleApiKey,
        onDismissRequest = onDismissRequest,
        onExportTranscriptsToChat = { transcripts ->
            scope.launch {
                transcripts.forEach { item ->
                    if (item.role == "user") {
                        viewModel.conversationComposerSubmission.submit(
                            ownerId = composerOwnerId,
                            text = item.text,
                            attachmentIds = emptyList(),
                        )
                    }
                }
            }
        }
    )
}
