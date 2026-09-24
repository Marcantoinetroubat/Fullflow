package com.newoether.agora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.modelAliasDisplayName
import com.newoether.agora.data.providerDisplayName
import com.newoether.agora.model.ModelId
import com.newoether.agora.studio.tts.kokoro.KokoroVoices
import com.newoether.agora.ui.chat.audio.TtsEngineMode
import com.newoether.agora.ui.components.providerIcon
import com.newoether.agora.util.Constants
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.ConversationPodcastGenerator

private const val TTS_CHOICE_KOKORO = "local:kokoro"
private const val TTS_CHOICE_SYSTEM = "local:system"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPodcastGenPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val selectedModel by viewModel.settings.podcastGenModel.collectAsState()
    val customPrompt by viewModel.settings.podcastGenPrompt.collectAsState()
    val ttsEngineModeStr by viewModel.settings.podcastGenTtsEngine.collectAsState()
    val podcastTtsModel by viewModel.settings.podcastGenTtsModel.collectAsState()

    val availableModels by viewModel.settings.availableModels.collectAsState()
    val modelAliases by viewModel.settings.modelAliases.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    val showDocFab by viewModel.settings.showDocumentationFab.collectAsState()

    val currentEngineMode = remember(ttsEngineModeStr) {
        runCatching { TtsEngineMode.valueOf(ttsEngineModeStr) }.getOrDefault(TtsEngineMode.GEMINI_CLOUD)
    }

    var showModelDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showTtsModelDialog by remember { mutableStateOf(false) }

    val allModels = remember(availableModels) {
        availableModels.values.flatten().distinct().sorted()
    }

    // ALL synced models, with TTS-capable ones first — the user picks from their
    // actual synced models (Providers → Models), not from fixed categories.
    val ttsModels = remember(allModels) {
        val ttsKeywords = setOf("tts", "voice", "audio", "speech", "playai", "kokoro", "eleven")
        val (ttsFirst, others) = allModels.partition { modelId ->
            val l = modelId.lowercase()
            ttsKeywords.any { l.contains(it) }
        }
        ttsFirst + others
    }

    fun selectTtsModel(choice: String?) {
        when (choice) {
            null -> {
                viewModel.settings.setPodcastGenTtsEngine(TtsEngineMode.GEMINI_CLOUD.name)
                viewModel.settings.setPodcastGenTtsModel(null)
                viewModel.settings.setPodcastGenVoice("Kore")
            }
            TTS_CHOICE_KOKORO -> {
                viewModel.settings.setPodcastGenTtsEngine(TtsEngineMode.KOKORO_LOCAL.name)
                viewModel.settings.setPodcastGenTtsModel(null)
                viewModel.settings.setTtsProviderModel(null)
                viewModel.settings.setPodcastGenVoice(KokoroVoices.DEFAULT_VOICE_ID)
            }
            TTS_CHOICE_SYSTEM -> {
                viewModel.settings.setPodcastGenTtsEngine(TtsEngineMode.SYSTEM.name)
                viewModel.settings.setPodcastGenTtsModel(null)
                viewModel.settings.setTtsProviderModel(null)
                viewModel.settings.setPodcastGenVoice("fr")
            }
            else -> {
                val isGemini = choice.contains("gemini", ignoreCase = true) ||
                    choice.contains("flash-tts", ignoreCase = true)
                viewModel.settings.setPodcastGenTtsEngine(
                    if (isGemini) TtsEngineMode.GEMINI_CLOUD.name else TtsEngineMode.OPENAI_CLOUD.name
                )
                viewModel.settings.setPodcastGenTtsModel(choice)
                // Also set the global TTS model so the chat speaker uses the same model
                viewModel.settings.setTtsProviderModel(choice)
                viewModel.settings.setPodcastGenVoice(if (isGemini) "Kore" else "alloy")
            }
        }
        showTtsModelDialog = false
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_podcast_gen),
        onBack = onBack,
        floatingActionButton = { if (showDocFab) DocumentationFab("podcast_gen.md") }
    ) {
        SettingsGroupColumn {
            // Group 1: Modèle d'intelligence artificielle pour le podcast
            SettingsGroup(
                title = stringResource(R.string.podcast_gen_model),
                items = listOf {
                    val parsed = selectedModel?.takeIf { it.contains(":") }?.let { ModelId.parse(it) }
                    val displayName = if (selectedModel.isNullOrBlank()) {
                        stringResource(R.string.podcast_gen_default_model)
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
                                    stringResource(R.string.podcast_gen_model_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingContent = {
                            when {
                                selectedModel == null -> Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
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

            // Group 2: Prompt de scénarisation
            SettingsGroup(
                title = stringResource(R.string.podcast_gen_prompt),
                items = listOf {
                    PromptSettingItem(
                        title = stringResource(R.string.podcast_gen_prompt),
                        description = stringResource(R.string.podcast_gen_prompt_desc),
                        prompt = customPrompt.ifBlank { ConversationPodcastGenerator.DEFAULT_PODCAST_PROMPT_TEMPLATE },
                        onClick = { showPromptDialog = true }
                    )
                }
            )

            // Group 3: Modèle vocal de synthèse — single picker fed by synced models
            // (user feedback: fixed engine categories + timbre selector were noise).
            SettingsGroup(
                title = stringResource(R.string.podcast_gen_tts_model),
                items = listOf {
                    val currentLabel = when {
                        currentEngineMode == TtsEngineMode.KOKORO_LOCAL -> stringResource(R.string.podcast_gen_tts_kokoro)
                        currentEngineMode == TtsEngineMode.SYSTEM -> stringResource(R.string.podcast_gen_tts_system)
                        !podcastTtsModel.isNullOrBlank() -> modelAliasDisplayName(podcastTtsModel!!, modelAliases, customProviders)
                        else -> stringResource(R.string.podcast_gen_tts_default)
                    }

                    SettingsItem(
                        headlineContent = {
                            Text(
                                text = currentLabel,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.podcast_gen_tts_model_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { showTtsModelDialog = true }
                    )
                }
            )
        }
    }

    // Dialog for picking podcast model
    if (showModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showModelDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.podcast_gen_select_model),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.podcast_gen_default_model),
                                    fontWeight = if (selectedModel == null) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedModel == null,
                                    onClick = {
                                        viewModel.settings.setPodcastGenModel(null)
                                        showModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setPodcastGenModel(null)
                                showModelDialog = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(allModels) { modelId ->
                        val parsed = if (modelId.contains(":")) ModelId.parse(modelId) else null
                        val displayName = modelAliasDisplayName(modelId, modelAliases, customProviders)
                        val providerName = parsed?.let { providerDisplayName(it.providerName, customProviders) }

                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = displayName,
                                    fontWeight = if (selectedModel == modelId) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            supportingContent = providerName?.let {
                                { Text(it, style = MaterialTheme.typography.bodySmall) }
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedModel == modelId,
                                    onClick = {
                                        viewModel.settings.setPodcastGenModel(modelId)
                                        showModelDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.settings.setPodcastGenModel(modelId)
                                showModelDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog for picking the TTS voice model (synced models + offline fallbacks)
    if (showTtsModelDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { showTtsModelDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.podcast_gen_tts_model),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        val isSelected = podcastTtsModel == null && currentEngineMode == TtsEngineMode.GEMINI_CLOUD
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.podcast_gen_tts_default),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                RadioButton(selected = isSelected, onClick = { selectTtsModel(null) })
                            },
                            modifier = Modifier.clickable { selectTtsModel(null) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(ttsModels, key = { it }) { modelId ->
                        val isSelected = podcastTtsModel == modelId
                        val parsed = if (modelId.contains(":")) ModelId.parse(modelId) else null
                        val providerName = parsed?.let { providerDisplayName(it.providerName, customProviders) }
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = modelAliasDisplayName(modelId, modelAliases, customProviders),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            supportingContent = providerName?.let {
                                { Text(it, style = MaterialTheme.typography.bodySmall) }
                            },
                            leadingContent = {
                                RadioButton(selected = isSelected, onClick = { selectTtsModel(modelId) })
                            },
                            modifier = Modifier.clickable { selectTtsModel(modelId) }
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        val kokoroSelected = currentEngineMode == TtsEngineMode.KOKORO_LOCAL
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.podcast_gen_tts_kokoro),
                                    fontWeight = if (kokoroSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                RadioButton(selected = kokoroSelected, onClick = { selectTtsModel(TTS_CHOICE_KOKORO) })
                            },
                            modifier = Modifier.clickable { selectTtsModel(TTS_CHOICE_KOKORO) }
                        )
                        val systemSelected = currentEngineMode == TtsEngineMode.SYSTEM
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.podcast_gen_tts_system),
                                    fontWeight = if (systemSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingContent = {
                                RadioButton(selected = systemSelected, onClick = { selectTtsModel(TTS_CHOICE_SYSTEM) })
                            },
                            modifier = Modifier.clickable { selectTtsModel(TTS_CHOICE_SYSTEM) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTtsModelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog for prompt template
    if (showPromptDialog) {
        PromptEditDialog(
            title = stringResource(R.string.podcast_gen_prompt),
            initialPrompt = customPrompt.ifBlank { ConversationPodcastGenerator.DEFAULT_PODCAST_PROMPT_TEMPLATE },
            onDismiss = { showPromptDialog = false },
            onSave = { newPrompt ->
                viewModel.settings.setPodcastGenPrompt(newPrompt)
                showPromptDialog = false
            }
        )
    }
}
