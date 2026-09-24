package com.newoether.agora.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.modelAliasDisplayName
import com.newoether.agora.data.providerDisplayName
import com.newoether.agora.model.ModelId
import com.newoether.agora.ui.components.providerIcon
import com.newoether.agora.util.Constants
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.ConversationBackgroundGenerator

private val IMAGE_MODEL_KEYWORDS = listOf(
    "imagen", "dall-e", "dalle", "flux", "stable-diffusion", "sdxl", "midjourney",
    "recraft", "ideogram", "nano-banana", "image", "kolors", "wanx", "qwen-image"
)

private fun isLikelyImageModel(modelId: String): Boolean {
    val id = modelId.substringAfter(":").lowercase()
    return IMAGE_MODEL_KEYWORDS.any { id.contains(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackgroundGenPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val selectedModel by viewModel.settings.backgroundGenModel.collectAsState()
    val customPrompt by viewModel.settings.backgroundGenPrompt.collectAsState()
    val opacity by viewModel.settings.backgroundGenOpacity.collectAsState()
    val availableModels by viewModel.settings.availableModels.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    val modelProviderNames by viewModel.settings.modelProviderNames.collectAsState()

    var showModelDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showAllModels by remember { mutableStateOf(false) }

    val allModels = remember(availableModels) {
        availableModels.values.flatten().distinct().sorted()
    }
    val imageModels = remember(allModels) {
        allModels.filter { isLikelyImageModel(it) }
    }
    val pickList = if (showAllModels || imageModels.isEmpty()) allModels else imageModels

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_background_gen),
        onBack = onBack,
    ) {
        SettingsGroupColumn {
            // Group 1: Modèle d'illustration
            SettingsGroup(
                title = stringResource(R.string.background_gen_model),
                items = listOf {
                    val parsed = selectedModel?.takeIf { it.contains(":") }?.let { ModelId.parse(it) }
                    val displayName = if (selectedModel.isNullOrBlank()) {
                        stringResource(R.string.background_gen_default_model)
                    } else {
                        modelAliasDisplayName(selectedModel!!, modelAliases, customProviders)
                    }
                    val providerName = parsed?.let { providerDisplayName(it.providerName, customProviders) }
                    val iconRes = providerName?.let { providerIcon(it) } ?: 0

                    SettingsItem(
                        headlineContent = {
                            Text(
                                text = displayName,
                                fontWeight = if (selectedModel == null) FontWeight.Normal else FontWeight.Medium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (providerName != null) {
                                    providerName
                                } else {
                                    stringResource(R.string.background_gen_model_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingContent = {
                            when {
                                selectedModel == null -> Icon(Icons.Default.Wallpaper, null, tint = MaterialTheme.colorScheme.primary)
                                providerName?.equals(Constants.PROVIDER_LOCAL, ignoreCase = true) == true ->
                                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                iconRes != 0 -> Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                else -> Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable { showModelDialog = true }
                    )
                }
            )

            // Group 2: Prompt d'illustration personnalisé
            SettingsGroup(
                title = stringResource(R.string.background_gen_prompt),
                items = listOf {
                    PromptSettingItem(
                        title = stringResource(R.string.background_gen_prompt),
                        description = stringResource(R.string.background_gen_prompt_desc),
                        prompt = customPrompt.ifBlank { ConversationBackgroundGenerator.DEFAULT_BACKGROUND_PROMPT_TEMPLATE },
                        onClick = { showPromptDialog = true }
                    )
                }
            )

            // Group 3: Intensité & Transparence
            SettingsGroup(
                title = stringResource(R.string.background_gen_opacity),
                items = listOf {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Opacity,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.background_gen_opacity),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Text(
                                text = "${(opacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.background_gen_opacity_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = opacity,
                            onValueChange = { viewModel.settings.setBackgroundGenOpacity(it) },
                            valueRange = 0.10f..0.75f,
                            steps = 12
                        )
                    }
                }
            )

            // Preview card demonstrating readability
            SettingsGroup(
                title = "Aperçu de la lisibilité",
                items = listOf {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        // Simulated wallpaper overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 1f - opacity))
                        )
                        // Sample message bubble
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Bonjour ! Votre arrière-plan IA sublimera chaque discussion tout en préservant une lisibilité parfaite.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            )
        }
    }

    // Dialog for picking background model
    if (showModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.background_gen_select_model),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.background_gen_default_model),
                                    fontWeight = if (selectedModel == null) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedModel == null,
                                    onClick = {
                                        viewModel.settings.setBackgroundGenModel(null)
                                        showModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setBackgroundGenModel(null)
                                showModelDialog = false
                            }
                        )
                    }

                    if (!showAllModels && imageModels.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick = { showAllModels = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Afficher tous les modèles disponibles (${allModels.size})")
                            }
                        }
                    }

                    items(pickList, key = { it }) { model ->
                        val isSelected = selectedModel == model
                        val modelParsed = ModelId.parse(model)
                        val displayName = modelAliasDisplayName(
                            model,
                            modelAliases,
                            customProviders
                        )
                        val providerName = providerDisplayName(modelParsed.providerName, customProviders)

                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = providerName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.settings.setBackgroundGenModel(model)
                                        showModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setBackgroundGenModel(model)
                                showModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text(stringResource(R.string.provider_cancel))
                }
            }
        )
    }

    // Dialog for editing background generation prompt
    if (showPromptDialog) {
        PromptEditDialog(
            title = stringResource(R.string.background_gen_prompt),
            initialPrompt = customPrompt.ifBlank { ConversationBackgroundGenerator.DEFAULT_BACKGROUND_PROMPT_TEMPLATE },
            onDismiss = { showPromptDialog = false },
            onSave = { viewModel.settings.setBackgroundGenPrompt(it) }
        )
    }
}
