package com.newoether.agora.ui.chat.fullflow

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.studio.image.GeminiImageStudioController
import com.newoether.agora.studio.image.GeminiImageStudioScreen
import com.newoether.agora.workspace.drive.GoogleDriveRepository
import com.newoether.agora.workspace.drive.GoogleDriveWorkspaceController
import com.newoether.agora.workspace.drive.GoogleWorkspaceDashboard
import kotlinx.coroutines.launch

@Composable
fun FullFlowHomeHost(
    isNewChatMode: Boolean,
    conversationsCount: Int,
    conversations: List<com.newoether.agora.model.ChatConversation> = emptyList(),
    viewModel: com.newoether.agora.viewmodel.ChatViewModel? = null,
    onOpenSearch: () -> Unit,
    onOpenDrawer: () -> Unit,
    onActivateComposer: (String?) -> Unit,
    onSendTask: ((String) -> Unit)? = null,
    onResetHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isNewChatMode) return

    FullFlowHomeScreen(
        conversationsCount = conversationsCount,
        conversations = conversations,
        viewModel = viewModel,
        onOpenSearch = onOpenSearch,
        onOpenDrawer = onOpenDrawer,
        onActivateComposer = onActivateComposer,
        onSendTask = onSendTask,
        onResetHome = onResetHome,
        modifier = modifier,
    )
}

@Composable
fun FullFlowDialogsHost(
    showGenMail: Boolean,
    onDismissGenMail: () -> Unit,
    onGenerateMail: (String) -> Unit,
    showPlus: Boolean,
    onDismissPlus: () -> Unit,
    onLaunchGeminiLive: () -> Unit,
    onNewChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val showDriveDashboard by GoogleDriveWorkspaceController.showDriveDashboard.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        GeminiImageStudioController.imageSentToChat.collect { (prompt, file) ->
            android.widget.Toast.makeText(context, "Image exportée : $prompt", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        com.newoether.agora.studio.video.GeminiVideoStudioController.videoSentToChat.collect { video ->
            android.widget.Toast.makeText(context, "Vidéo exportée : ${video.prompt}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    FullFlowGenMailDialog(
        visible = showGenMail,
        onDismissRequest = onDismissGenMail,
        onGenerateMail = onGenerateMail,
    )

    FullFlowPlusSheet(
        visible = showPlus,
        onDismissRequest = onDismissPlus,
        onLaunchGeminiLive = onLaunchGeminiLive,
        onLaunchFullLive = { com.newoether.agora.ui.chat.live.FullLiveController.open() },
        onNewChat = onNewChat,
        onOpenTasks = onOpenTasks,
        onOpenSettings = onOpenSettings,
    )

    // FullLive Voice Assistant (WebView-based, full-screen)
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    com.newoether.agora.ui.chat.live.FullLiveHost()
    // Second Brain screen (notes + brain items viewer)
    com.newoether.agora.ui.chat.live.SecondBrainHost()
    // Live Voice — persona picker from the input bar mic icon
    com.newoether.agora.ui.chat.live.LiveVoiceHost()
    // Web Research — Perplexity-style search page
    com.newoether.agora.ui.webresearch.WebResearchHost()

    if (showDriveDashboard) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val repo = remember { GoogleDriveRepository.getInstance(context) }
        Dialog(
            onDismissRequest = { GoogleDriveWorkspaceController.closeDriveDashboard() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GoogleWorkspaceDashboard(
                repository = repo,
                onImportFileToChat = { file ->
                    coroutineScope.launch {
                        GoogleDriveWorkspaceController.importFile(context, file)
                    }
                },
                onClose = { GoogleDriveWorkspaceController.closeDriveDashboard() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val showImageStudio by GeminiImageStudioController.showStudio.collectAsState()
    if (showImageStudio) {
        Dialog(
            onDismissRequest = { GeminiImageStudioController.closeStudio() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GeminiImageStudioScreen(
                onClose = { GeminiImageStudioController.closeStudio() },
                onInsertToChat = { image ->
                    GeminiImageStudioController.sendToChat(image)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val showVideoStudio by com.newoether.agora.studio.video.GeminiVideoStudioController.showStudio.collectAsState()
    if (showVideoStudio) {
        Dialog(
            onDismissRequest = { com.newoether.agora.studio.video.GeminiVideoStudioController.closeStudio() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.newoether.agora.studio.video.GeminiVideoStudioScreen(
                onClose = { com.newoether.agora.studio.video.GeminiVideoStudioController.closeStudio() },
                onInsertToChat = { video ->
                    com.newoether.agora.studio.video.GeminiVideoStudioController.sendToChat(video)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
