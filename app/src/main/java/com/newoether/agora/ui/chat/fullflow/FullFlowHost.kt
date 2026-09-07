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
    onOpenSearch: () -> Unit,
    onOpenDrawer: () -> Unit,
    onActivateComposer: () -> Unit,
    onResetHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isNewChatMode) return

    FullFlowHomeScreen(
        conversationsCount = conversationsCount,
        onOpenSearch = onOpenSearch,
        onOpenDrawer = onOpenDrawer,
        onActivateComposer = onActivateComposer,
        onResetHome = onResetHome,
        modifier = modifier,
    )
}

@Composable
fun FullFlowBottomBarHost(
    visible: Boolean,
    isNewChatMode: Boolean,
    onHomeClick: () -> Unit,
    onWorkspaceClick: () -> Unit = { GoogleDriveWorkspaceController.openDriveDashboard() },
    onGenMailClick: () -> Unit,
    onPlusClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    FullFlowBottomBar(
        currentTab = FullFlowTab.HOME,
        onTabSelected = { tab ->
            when (tab) {
                FullFlowTab.HOME -> onHomeClick()
                FullFlowTab.WORKSPACE -> onWorkspaceClick()
                FullFlowTab.GEN_MAIL -> onGenMailClick()
                FullFlowTab.PLUS -> onPlusClick()
                FullFlowTab.PROFILE -> onProfileClick()
            }
        },
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

    FullFlowGenMailDialog(
        visible = showGenMail,
        onDismissRequest = onDismissGenMail,
        onGenerateMail = onGenerateMail,
    )

    FullFlowPlusSheet(
        visible = showPlus,
        onDismissRequest = onDismissPlus,
        onLaunchGeminiLive = onLaunchGeminiLive,
        onNewChat = onNewChat,
        onOpenTasks = onOpenTasks,
        onOpenSettings = onOpenSettings,
    )

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
}
