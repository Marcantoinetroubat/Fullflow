package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.chat.fullflow.GenMailController
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.workspace.drive.GoogleDriveWorkspaceController

/**
 * « Espace de travail » settings section: Google Drive dashboard and the Gmail
 * assistant (GenMail) live here instead of the removed home bottom bar. Both open
 * as dialogs hosted in the chat layer, which stays composed under the settings overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsWorkspacePage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_workspace),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("workspace.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.settings_workspace), items = buildList {
                add {
                    SettingsItem(
                        headlineContent = { Text("Second Cerveau sémantique") },
                        supportingContent = { Text("Gérer vos notes Markdown, vos tâches Obsidian et réindexer sémantiquement.") },
                        leadingContent = { Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { com.newoether.agora.ui.chat.live.SecondBrainController.open() }
                    )
                }
                add {
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.workspace_drive_item)) },
                        supportingContent = { Text(stringResource(R.string.workspace_drive_desc)) },
                        leadingContent = { Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { GoogleDriveWorkspaceController.openDriveDashboard() }
                    )
                }
                add {
                    SettingsItem(
                        headlineContent = { Text(stringResource(R.string.workspace_gmail_item)) },
                        supportingContent = { Text(stringResource(R.string.workspace_gmail_desc)) },
                        leadingContent = { Icon(Icons.Default.Mail, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { GenMailController.openGenMail() }
                    )
                }
            })
        }

        if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
