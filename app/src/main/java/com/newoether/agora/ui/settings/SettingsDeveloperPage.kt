package com.newoether.agora.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.newoether.agora.ui.ds.AgoraDialog
import com.newoether.agora.ui.ds.DesignSystemGallery
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.newoether.agora.R
import com.newoether.agora.diagnostics.DeveloperDiagnostics
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsDeveloperPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onDisabled: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val developerModeEnabled by viewModel.settings.developerOptionsEnabled.collectAsState()
    val debugModelEnabled by viewModel.settings.debugModelEnabled.collectAsState()
    var showCapturePage by rememberSaveable { mutableStateOf(false) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    val exportFailedMessage = stringResource(R.string.developer_options_export_failed)

    BackHandler(enabled = showCapturePage) {
        showCapturePage = false
    }
    BackHandler(enabled = showGallery) {
        showGallery = false
    }

    if (showGallery) {
        CollapsingSettingsScaffold(
            title = "Design System",
            onBack = { showGallery = false },
        ) {
            DesignSystemGallery()
        }
        return
    }

    if (showDisableDialog) {
        AgoraDialog(
            title = stringResource(R.string.developer_options_disable_title),
            onDismissRequest = { showDisableDialog = false },
            confirmText = stringResource(R.string.developer_options_disable_confirm),
            onConfirm = {
                showDisableDialog = false
                coroutineScope.launch {
                    DeveloperDiagnostics.disableAndClear()
                    viewModel.settings
                        .setDeveloperOptionsEnabled(false)
                        .join()
                    onDisabled()
                }
            },
            dismissText = stringResource(R.string.cancel),
            text = { Text(stringResource(R.string.developer_options_disable_message)) },
            destructive = true,
        )
    }

    GuardedAnimatedContent(
        targetState = showCapturePage,
        forward = showCapturePage,
    ) { captureVisible ->
        if (captureVisible) {
            SettingsDeveloperCapturePage(
                onBack = { showCapturePage = false },
                onExportFailed = { viewModel.emitSnackbar(exportFailedMessage) },
            )
        } else {
            CollapsingSettingsScaffold(
                title = stringResource(R.string.developer_options_title),
                onBack = onBack,
            ) {
                SettingsGroupColumn {
                    SettingsGroup(
                        title = stringResource(R.string.developer_options_features_group),
                        items = listOf(
                            {
                                SettingsItem(
                                    modifier = Modifier.clickable(enabled = developerModeEnabled) {
                                        showDisableDialog = true
                                    },
                                    headlineContent = {
                                        Text(stringResource(R.string.settings_developer))
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(
                                                R.string.developer_options_mode_description,
                                            ),
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.BugReport, contentDescription = null)
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = developerModeEnabled,
                                            onCheckedChange = null,
                                        )
                                    },
                                )
                            },
                            {
                                SettingsItem(
                                    modifier = Modifier.clickable {
                                        showCapturePage = true
                                    },
                                    headlineContent = {
                                        Text(stringResource(R.string.developer_options_capture))
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(
                                                R.string.developer_options_capture_description,
                                            ),
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Visibility, contentDescription = null)
                                    },
                                )
                            },
                            {
                                SettingsItem(
                                    modifier = Modifier.clickable {
                                        showGallery = true
                                    },
                                    headlineContent = {
                                        Text("Design System / Components")
                                    },
                                    supportingContent = {
                                        Text(
                                            "Galerie des composants canoniques Agora et leurs états.",
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Palette, contentDescription = "Design System")
                                    },
                                )
                            },
                            {
                                SettingsItem(
                                    modifier = Modifier.clickable(enabled = developerModeEnabled) {
                                        viewModel.settings.setDebugModelEnabled(!debugModelEnabled)
                                    },
                                    headlineContent = {
                                        Text(
                                            stringResource(
                                                R.string.developer_options_debug_model,
                                            ),
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(
                                                R.string.developer_options_debug_model_description,
                                            ),
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Science, contentDescription = null)
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = debugModelEnabled,
                                            enabled = developerModeEnabled,
                                            onCheckedChange = null,
                                        )
                                    },
                                )
                            },
                        ),
                    )
                }
            }
        }
    }
}
