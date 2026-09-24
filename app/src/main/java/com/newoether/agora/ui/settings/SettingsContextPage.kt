package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.data.modelAliasDisplayName
import com.newoether.agora.data.modelDisplayName
import com.newoether.agora.data.providerDisplayName
import com.newoether.agora.model.ModelId
import com.newoether.agora.model.ContextBudget
import com.newoether.agora.ui.common.PersistedSliderFeedbackGate
import com.newoether.agora.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContextPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val window by viewModel.settings.maxContextWindow.collectAsState()
    val visualize by viewModel.settings.visualizeContextRollout.collectAsState()
    val compact by viewModel.settings.contextCompactEnabled.collectAsState()
    val thresholdPercent by
        viewModel.settings.contextCompactThresholdPercent.collectAsState()
    val compactModel by viewModel.settings.contextCompactModel.collectAsState()
    val compactPrompt by viewModel.settings.contextCompactPrompt.collectAsState()
    val retainCount by viewModel.settings.contextCompactRetainCount.collectAsState()
    val enabledModels by viewModel.settings.enabledModels.collectAsState()
    val aliases by viewModel.settings.modelAliases.collectAsState()
    val modelProviderNames by viewModel.settings.modelProviderNames.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()
    var modelDialog by remember { mutableStateOf(false) }
    var promptDialog by remember { mutableStateOf(false) }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.context_title),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("context.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(
                title = stringResource(R.string.context_window_default),
                items = listOf(
                    {
                        val presets = ContextBudget.PRESETS
                        fun nearestIndex(value: Int): Int = presets.indices.minByOrNull {
                            kotlin.math.abs(presets[it] - value)
                        } ?: 3
                        val sliderGate = remember {
                            PersistedSliderFeedbackGate(
                                initialPersisted = window,
                                toDisplay = { value ->
                                nearestIndex(value).toFloat()
                                },
                            )
                        }
                        LaunchedEffect(window) { sliderGate.reconcile(window) }
                        val draftIndex = sliderGate.displayed
                        val draft = presets[draftIndex.toInt().coerceIn(0, presets.lastIndex)]
                        com.newoether.agora.ui.ds.AgoraSliderRow(
                            icon = Icons.Default.Memory,
                            title = stringResource(R.string.context_window),
                            description = stringResource(R.string.context_window_desc),
                            valueLabel = ContextBudget.compactLabel(draft),
                            value = draftIndex,
                            valueRange = 0f..presets.lastIndex.toFloat(),
                            steps = presets.size - 2,
                            onValueChange = sliderGate::updateFromGesture,
                            onValueChangeFinished = {
                                if (draft != window) {
                                    sliderGate.expectPersisted(draft, draftIndex)
                                    viewModel.settings.setMaxContextWindow(draft)
                                } else {
                                    sliderGate.settleWithoutWrite(window, draftIndex)
                                }
                            },
                        )
                    },
                    {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.context_visualize)) },
                            supportingContent = { Text(stringResource(R.string.context_visualize_desc)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Visibility,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = visualize,
                                    onCheckedChange = viewModel.settings::setVisualizeContextRollout,
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setVisualizeContextRollout(!visualize)
                            },
                        )
                    },
                ),
            )
            SettingsGroup(
                title = stringResource(R.string.context_compact),
                items = buildList {
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.context_compact_model)) },
                            supportingContent = {
                                Text(
                                    compactModel?.let {
                                        modelDisplayName(it, aliases, customProviders, modelProviderNames[it] != false)
                                    } ?: stringResource(R.string.title_gen_current_model)
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Chat,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable { modelDialog = true },
                        )
                    }
                    add {
                        SettingsItem(
                            headlineContent = { Text(stringResource(R.string.context_compact_auto)) },
                            supportingContent = { Text(stringResource(R.string.context_compact_auto_desc)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Compress,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = compact,
                                    onCheckedChange = viewModel.settings::setContextCompactEnabled,
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setContextCompactEnabled(!compact)
                            },
                        )
                    }
                    if (compact) {
                        add {
                            val sliderGate = remember {
                                PersistedSliderFeedbackGate(
                                    initialPersisted = thresholdPercent,
                                    toDisplay = { it.toFloat() },
                                )
                            }
                            LaunchedEffect(thresholdPercent) {
                                sliderGate.reconcile(thresholdPercent)
                            }
                            val draft = sliderGate.displayed
                            com.newoether.agora.ui.ds.AgoraSliderRow(
                                icon = Icons.Default.Compress,
                                title = stringResource(R.string.context_compact_threshold),
                                description = stringResource(
                                    R.string.context_compact_threshold_desc,
                                ),
                                valueLabel = "${draft.toInt()}%",
                                value = draft,
                                valueRange = 50f..100f,
                                steps = 0,
                                onValueChange = { sliderGate.updateFromGesture(kotlin.math.round(it)) },
                                onValueChangeFinished = {
                                    val committed = draft.toInt().coerceIn(50, 100)
                                    if (committed != thresholdPercent) {
                                        sliderGate.expectPersisted(
                                            committed,
                                            committed.toFloat(),
                                        )
                                        viewModel.settings
                                            .setContextCompactThresholdPercent(committed)
                                    } else {
                                        sliderGate.settleWithoutWrite(
                                            thresholdPercent,
                                            committed.toFloat(),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    add {
                        val sliderGate = remember {
                            PersistedSliderFeedbackGate(
                                initialPersisted = retainCount,
                                toDisplay = { it.toFloat() },
                            )
                        }
                        LaunchedEffect(retainCount) { sliderGate.reconcile(retainCount) }
                        val draft = sliderGate.displayed
                        com.newoether.agora.ui.ds.AgoraSliderRow(
                            icon = Icons.Default.Memory,
                            title = stringResource(R.string.context_compact_retain),
                            description = stringResource(R.string.context_compact_retain_desc),
                            valueLabel = draft.toInt().toString(),
                            value = draft,
                            valueRange = 0f..20f,
                            steps = 19,
                            onValueChange = sliderGate::updateFromGesture,
                            onValueChangeFinished = {
                                val committed = draft.toInt()
                                if (committed != retainCount) {
                                    sliderGate.expectPersisted(committed, committed.toFloat())
                                    viewModel.settings.setContextCompactRetainCount(committed)
                                } else {
                                    sliderGate.settleWithoutWrite(retainCount, committed.toFloat())
                                }
                            },
                        )
                    }
                    add {
                        PromptSettingItem(
                            title = stringResource(R.string.context_compact_prompt),
                            description = stringResource(R.string.context_compact_prompt_desc),
                            prompt = compactPrompt,
                            onClick = { promptDialog = true },
                        )
                    }
                },
            )
        }
        if (showDocFab) { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (modelDialog) {
        com.newoether.agora.ui.ds.AgoraDialog(
            title = stringResource(R.string.context_compact_select_model),
            onDismissRequest = { modelDialog = false },
            confirmText = stringResource(R.string.provider_cancel),
            onConfirm = { modelDialog = false },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        CompactModelItem(
                            label = stringResource(R.string.title_gen_current_model),
                            provider = null,
                            selected = compactModel == null,
                            onClick = {
                                viewModel.settings.setContextCompactModel(null)
                                modelDialog = false
                            },
                        )
                    }
                    items(enabledModels.toList(), key = { it }) { model ->
                        val parsed = ModelId.parse(model)
                        CompactModelItem(
                            label = modelAliasDisplayName(
                                model,
                                aliases,
                                customProviders,
                            ),
                            provider = providerDisplayName(parsed.providerName, customProviders),
                            selected = compactModel == model,
                            onClick = {
                                viewModel.settings.setContextCompactModel(model)
                                modelDialog = false
                            },
                        )
                    }
                }
            },
        )
    }
    if (promptDialog) {
        PromptEditDialog(
            title = stringResource(R.string.context_compact_prompt),
            initialPrompt = compactPrompt,
            onDismiss = { promptDialog = false },
            onSave = viewModel.settings::setContextCompactPrompt,
        )
    }
}

@Composable
private fun CompactModelItem(
    label: String,
    provider: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SettingsItem(
        headlineContent = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        supportingContent = provider?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AgoraAlpha.Hint),
                )
            }
        },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
    )
}
