package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import com.newoether.agora.ui.components.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.data.modelAliasDisplayName
import com.newoether.agora.data.providerDisplayName
import com.newoether.agora.model.ModelId
import com.newoether.agora.ui.components.providerIcon
import com.newoether.agora.util.Constants
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

private val VIDEO_MODEL_KEYWORDS = listOf(
    "veo", "sora", "gen2", "gen-2", "gen3", "gen-3", "kling", "luma", "runway", "pika",
    "hailuo", "minimax", "dream-machine", "dreammachine", "cogvideo", "hunyuan-video",
    "hunyuanvideo", "wan2", "wan-2", "mochi", "ltx-video", "ltxvideo", "fuyu", "animatediff",
    "svd", "stable-video", "stablevideo", "videocrafter", "vlogger", "dynami", "omni"
)

private fun isLikelyVideoModel(modelId: String): Boolean {
    val id = modelId.substringAfter(":").lowercase()
    return VIDEO_MODEL_KEYWORDS.any { id.contains(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsVideoGenPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val enabled by viewModel.settings.videoGenEnabled.collectAsState()
    val selectedModel by viewModel.settings.videoGenModel.collectAsState()
    val videoGenPrompt by viewModel.settings.videoGenPrompt.collectAsState()
    val videoGenNegativePrompt by viewModel.settings.videoGenNegativePrompt.collectAsState()
    val videoGenAspectRatio by viewModel.settings.videoGenAspectRatio.collectAsState()
    val videoGenDuration by viewModel.settings.videoGenDuration.collectAsState()
    val videoGenResolution by viewModel.settings.videoGenResolution.collectAsState()
    val availableModels by viewModel.settings.availableModels.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    var showModelDialog by remember { mutableStateOf(false) }
    var showAllModels by remember { mutableStateOf(false) }
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    val allModels = remember(availableModels) { availableModels.values.flatten().distinct().sorted() }
    val videoModels = remember(allModels) { allModels.filter { isLikelyVideoModel(it) } }
    val pickList = if (showAllModels || videoModels.isEmpty()) allModels else videoModels

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_video_gen),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("video-generation.md") }
    ) {
        SettingsGroupColumn {
            SettingsGroup(title = stringResource(R.string.settings_video_gen), items = listOf({
                SettingsItem(
                    headlineContent = { Text(stringResource(R.string.video_gen_enable)) },
                    supportingContent = { Text(stringResource(R.string.video_gen_enable_desc)) },
                    leadingContent = { Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(checked = enabled, onCheckedChange = { viewModel.settings.setVideoGenEnabled(it) })
                    },
                    modifier = Modifier.clickable { viewModel.settings.setVideoGenEnabled(!enabled) }
                )
            }))

            if (enabled) {
                SettingsGroup(title = stringResource(R.string.video_gen_model), items = listOf({
                    val parsed = selectedModel?.takeIf { it.contains(":") }?.let { ModelId.parse(it) }
                    val displayName = selectedModel
                        ?.takeIf { parsed != null }
                        ?.let { modelAliasDisplayName(it, modelAliases, customProviders) }
                        ?: stringResource(R.string.video_gen_no_model)
                    val providerName = parsed?.let {
                        providerDisplayName(it.providerName, customProviders)
                    }
                    val iconRes = providerName?.let { providerIcon(it) } ?: 0
                    SettingsItem(
                        headlineContent = {
                            Text(displayName, color = if (parsed == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        },
                        supportingContent = if (providerName != null) {
                            { Text(providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                        } else null,
                        leadingContent = {
                            when {
                                parsed == null -> Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                providerName.equals(Constants.PROVIDER_LOCAL, ignoreCase = true) -> Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(AgoraSpacing.Xxl))
                                iconRes != 0 -> Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(AgoraSpacing.Xxl))
                                else -> Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(AgoraSpacing.Xxl))
                            }
                        },
                        modifier = Modifier.heightIn(min = 64.dp).clickable { showModelDialog = true }
                    )
                }))

                // Format (Aspect Ratio) et Durée
                SettingsGroup(title = "Options vidéo", items = listOf({
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AspectRatio, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                            Column {
                                Text(stringResource(R.string.video_gen_aspect_ratio), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)) {
                                    listOf("16:9", "9:16", "1:1").forEach { ratio ->
                                        val isSelected = videoGenAspectRatio == ratio
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.settings.setVideoGenAspectRatio(ratio) },
                                            label = { Text(ratio) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }, {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                            Column {
                                Text(stringResource(R.string.video_gen_duration), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)) {
                                    listOf(5, 10).forEach { sec ->
                                        val isSelected = videoGenDuration == sec
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.settings.setVideoGenDuration(sec) },
                                            label = { Text("${sec}s") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }))

                // Résolution
                SettingsGroup(title = stringResource(R.string.video_gen_resolution), items = listOf({
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HighQuality, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.video_gen_resolution), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                val resState = remember { TextFieldState(videoGenResolution) }
                                LaunchedEffect(resState.text) {
                                    delay(500)
                                    val r = resState.text.toString().trim()
                                    if (r.isNotEmpty()) viewModel.settings.setVideoGenResolution(r)
                                }
                                OutlinedTextField(
                                    state = resState,
                                    placeholder = { Text("1080p") },
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = AgoraSpacing.Sm),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }))

                // Prompts adéquats (Prefix et Prompt Négatif)
                SettingsGroup(title = "Instructions et Prompts", items = listOf({
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = AgoraSpacing.Xs))
                            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.video_gen_prompt_instructions), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                val promptState = remember { TextFieldState(videoGenPrompt) }
                                LaunchedEffect(promptState.text) {
                                    delay(500)
                                    viewModel.settings.setVideoGenPrompt(promptState.text.toString())
                                }
                                OutlinedTextField(
                                    state = promptState,
                                    placeholder = { Text("e.g. cinematic lighting, photorealistic...") },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = AgoraSpacing.Sm),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }, {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = AgoraSpacing.Xs))
                            Spacer(modifier = Modifier.width(AgoraSpacing.Lg))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.video_gen_negative_prompt), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                val negState = remember { TextFieldState(videoGenNegativePrompt) }
                                LaunchedEffect(negState.text) {
                                    delay(500)
                                    viewModel.settings.setVideoGenNegativePrompt(negState.text.toString())
                                }
                                OutlinedTextField(
                                    state = negState,
                                    placeholder = { Text("e.g. blurry, low quality...") },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = AgoraSpacing.Sm),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }))
            }
        }
    }

    if (showModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.video_gen_select_model), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (videoModels.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showAllModels = !showAllModels }
                        ) {
                            Checkbox(checked = showAllModels, onCheckedChange = { showAllModels = it })
                            Text(stringResource(R.string.video_gen_show_all), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (pickList.isEmpty()) {
                        Text(stringResource(R.string.transcription_no_models_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val pickListByProvider = remember(pickList) {
                            pickList.groupBy { ModelId.parse(it).providerName }
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            pickListByProvider.forEach { (providerId, models) ->
                                item(key = "provider_$providerId") {
                                    val providerName = providerDisplayName(providerId, customProviders)
                                    Text(
                                        text = providerName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm)
                                    )
                                }
                                items(models, key = { it }) { model ->
                                    val displayName = modelAliasDisplayName(
                                        model,
                                        modelAliases,
                                        customProviders,
                                    )
                                    SettingsItem(
                                        headlineContent = { Text(displayName, fontWeight = if (selectedModel == model) FontWeight.Bold else FontWeight.Normal) },
                                        supportingContent = { Text(model, style = MaterialTheme.typography.bodySmall) },
                                        leadingContent = {
                                            RadioButton(selected = selectedModel == model, onClick = {
                                                viewModel.settings.setVideoGenModel(model); showModelDialog = false
                                            })
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.settings.setVideoGenModel(model); showModelDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelDialog = false }) { Text(stringResource(R.string.provider_cancel)) } }
        )
    }
}
