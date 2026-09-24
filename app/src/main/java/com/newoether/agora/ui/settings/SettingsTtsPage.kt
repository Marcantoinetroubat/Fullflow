package com.newoether.agora.ui.settings

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.studio.tts.kokoro.KokoroModelManager
import com.newoether.agora.studio.tts.kokoro.KokoroModelStatus
import com.newoether.agora.studio.tts.kokoro.KokoroVoices
import com.newoether.agora.ui.chat.audio.FullFlowTtsEngine
import com.newoether.agora.ui.chat.audio.TtsEngineMode
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTtsPage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ttsEngineModeStr by viewModel.settings.ttsEngineMode.collectAsState()
    val ttsProviderModel by viewModel.settings.ttsProviderModel.collectAsState()
    val ttsVoice by viewModel.settings.ttsVoice.collectAsState()
    val ttsSpeed by viewModel.settings.ttsSpeed.collectAsState()

    val currentMode = remember(ttsEngineModeStr) {
        runCatching { TtsEngineMode.valueOf(ttsEngineModeStr) }.getOrDefault(TtsEngineMode.SYSTEM)
    }

    val kokoroManager = remember { KokoroModelManager.getInstance(context) }
    val kokoroStatus by kokoroManager.status.collectAsState()
    val kokoroProgress by kokoroManager.progress.collectAsState()

    // Test engine instance
    val testEngine = remember { FullFlowTtsEngine(context) }
    DisposableEffect(Unit) {
        onDispose {
            testEngine.shutdown()
        }
    }

    var showModelDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf(ttsProviderModel.orEmpty()) }

    val availableModels by viewModel.settings.availableModels.collectAsState()
    val apiKeys by viewModel.settings.apiKeys.collectAsState()

    val candidateTtsModels = remember(availableModels, apiKeys) {
        val list = linkedSetOf("tts-1", "tts-1-hd")
        val activeProviders = apiKeys.filter { it.key.isNotBlank() }.map { it.provider.lowercase() }.toSet()
        availableModels.forEach { (prov, models) ->
            if (prov.lowercase() in activeProviders || prov.lowercase() in listOf("openai", "openrouter", "zenmux", "groq")) {
                models.forEach { rawModel ->
                    val clean = if (rawModel.contains(":")) rawModel.substringAfter(":") else rawModel
                    val lower = clean.lowercase()
                    if (lower.contains("tts") || lower.contains("speech") || lower.contains("audio") || lower.contains("voice")) {
                        list.add(clean)
                    }
                }
            }
        }
        list.toList()
    }

    CollapsingSettingsScaffold(
        title = stringResource(R.string.settings_tts),
        onBack = {
            testEngine.stop()
            onBack()
        }
    ) {
        SettingsGroupColumn {
            // Group 1: Choix du moteur de synthèse vocale
            SettingsGroup(
                title = stringResource(R.string.tts_engine),
                items = listOf(
                    {
                        EngineOptionItem(
                            title = stringResource(R.string.tts_engine_system),
                            subtitle = stringResource(R.string.tts_engine_system_desc),
                            icon = Icons.Default.PhoneAndroid,
                            accentColor = Color(0xFFA78BFA),
                            isSelected = currentMode == TtsEngineMode.SYSTEM,
                            onClick = {
                                testEngine.stop()
                                viewModel.settings.setTtsEngineMode(TtsEngineMode.SYSTEM.name)
                                if (ttsVoice.isBlank() || ttsVoice in testEngine.geminiVoices || ttsVoice in testEngine.openAiVoices) {
                                    viewModel.settings.setTtsVoice("fr")
                                }
                            }
                        )
                    },
                    {
                        EngineOptionItem(
                            title = stringResource(R.string.tts_engine_gemini),
                            subtitle = stringResource(R.string.tts_engine_gemini_desc),
                            icon = Icons.Default.AutoAwesome,
                            accentColor = Color(0xFF38BDF8),
                            isSelected = currentMode == TtsEngineMode.GEMINI_CLOUD,
                            onClick = {
                                testEngine.stop()
                                viewModel.settings.setTtsEngineMode(TtsEngineMode.GEMINI_CLOUD.name)
                                if (ttsVoice !in testEngine.geminiVoices) {
                                    viewModel.settings.setTtsVoice("Kore")
                                }
                            }
                        )
                    },
                    {
                        EngineOptionItem(
                            title = stringResource(R.string.tts_engine_openai),
                            subtitle = stringResource(R.string.tts_engine_openai_desc),
                            icon = Icons.Default.Cloud,
                            accentColor = Color(0xFF10A37F),
                            isSelected = currentMode == TtsEngineMode.OPENAI_CLOUD,
                            onClick = {
                                testEngine.stop()
                                viewModel.settings.setTtsEngineMode(TtsEngineMode.OPENAI_CLOUD.name)
                                if (ttsVoice !in testEngine.openAiVoices) {
                                    viewModel.settings.setTtsVoice("alloy")
                                }
                            }
                        )
                    },
                    {
                        EngineOptionItem(
                            title = stringResource(R.string.tts_engine_kokoro),
                            subtitle = stringResource(R.string.tts_engine_kokoro_desc),
                            icon = Icons.Default.OfflineBolt,
                            accentColor = Color(0xFF34D399),
                            isSelected = currentMode == TtsEngineMode.KOKORO_LOCAL,
                            onClick = {
                                testEngine.stop()
                                viewModel.settings.setTtsEngineMode(TtsEngineMode.KOKORO_LOCAL.name)
                                if (KokoroVoices.VOICES.none { it.id == ttsVoice }) {
                                    viewModel.settings.setTtsVoice(KokoroVoices.DEFAULT_VOICE_ID)
                                }
                            }
                        )
                    }
                )
            )

            // Kokoro Download Banner if selected and not downloaded
            if (currentMode == TtsEngineMode.KOKORO_LOCAL && kokoroStatus != KokoroModelStatus.READY) {
                KokoroDownloadCard(
                    status = kokoroStatus,
                    progress = kokoroProgress,
                    onStartDownload = { kokoroManager.startDownload() },
                    onDeleteModel = { kokoroManager.deleteModel() }
                )
            }

            // Group 2: Modèle (si OpenAI / Multi-Provider)
            if (currentMode == TtsEngineMode.OPENAI_CLOUD) {
                SettingsGroup(
                    title = stringResource(R.string.tts_model),
                    items = listOf {
                        SettingsItem(
                            headlineContent = {
                                Text(
                                    text = if (ttsProviderModel.isNullOrBlank()) "tts-1 (Standard)" else ttsProviderModel!!,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.tts_model_desc),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF10A37F)
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                customModelInput = ttsProviderModel.orEmpty()
                                showModelDialog = true
                            }
                        )
                    }
                )
            }

            // Group 3: Voix sélectionnée
            val voiceLabel = when (currentMode) {
                TtsEngineMode.GEMINI_CLOUD -> ttsVoice.ifBlank { "Kore" }
                TtsEngineMode.OPENAI_CLOUD -> ttsVoice.ifBlank { "alloy" }.replaceFirstChar { it.uppercase() }
                TtsEngineMode.KOKORO_LOCAL -> {
                    val kv = KokoroVoices.VOICES.find { it.id == ttsVoice }
                    if (kv != null) "${kv.label} (${kv.lang})" else "Siwis (FR)"
                }
                TtsEngineMode.SYSTEM -> {
                    when (ttsVoice) {
                        "fr" -> "Français"
                        "en" -> "English"
                        "es" -> "Español"
                        "de" -> "Deutsch"
                        "it" -> "Italiano"
                        "ja" -> "日本語"
                        else -> "Français"
                    }
                }
            }

            SettingsGroup(
                title = stringResource(R.string.tts_voice),
                items = listOf {
                    SettingsItem(
                        headlineContent = {
                            Text(
                                text = voiceLabel,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.tts_voice_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = when (currentMode) {
                                    TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8)
                                    TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F)
                                    TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399)
                                    TtsEngineMode.SYSTEM -> Color(0xFFA78BFA)
                                }
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { showVoiceDialog = true }
                    )
                }
            )

            // Group 4: Vitesse d'élocution
            SettingsGroup(
                title = stringResource(R.string.tts_speed),
                items = listOf {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.tts_speed_desc),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "%.2fx".format(ttsSpeed),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
                        Slider(
                            value = ttsSpeed,
                            onValueChange = { viewModel.settings.setTtsSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }
                }
            )

            // Group 5: Aperçu et test en direct
            SettingsGroup(
                title = stringResource(R.string.tts_test_title),
                items = listOf {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AgoraSpacing.Lg)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.tts_test_sample),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(AgoraSpacing.Md)
                            )
                        }

                        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (currentMode) {
                                        TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                                        TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F).copy(alpha = 0.15f)
                                        TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399).copy(alpha = 0.15f)
                                        TtsEngineMode.SYSTEM -> Color(0xFFA78BFA).copy(alpha = 0.15f)
                                    }
                                ) {
                                    Text(
                                        text = currentMode.name.replace("_", " "),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (currentMode) {
                                            TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8)
                                            TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F)
                                            TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399)
                                            TtsEngineMode.SYSTEM -> Color(0xFFA78BFA)
                                        },
                                        modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)
                                    )
                                }
                            }

                            val isSpeaking = testEngine.isBusy
                            Button(
                                onClick = {
                                    if (isSpeaking) {
                                        testEngine.stop()
                                    } else {
                                        testEngine.syncWithSettingsRepository(viewModel.settings)
                                        val sampleText = context.getString(R.string.tts_test_sample)
                                        testEngine.speak(sampleText)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (testEngine.isSynthesizingCloud || testEngine.isSynthesizingKokoro) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(AgoraSpacing.Lg),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                } else {
                                    Icon(
                                        if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(AgoraSpacing.Xl)
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                }
                                Text(if (isSpeaking) "Arrêter" else stringResource(R.string.tts_test_button))
                            }
                        }
                    }
                }
            )

            // Group 6: Note explicative sur l'usage dans le chat
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(AgoraSpacing.Lg),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AgoraSpacing.Xl)
                    )
                    Text(
                        text = stringResource(R.string.tts_chat_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Dialog: Sélection du modèle OpenAI / Multi-Provider
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.tts_model)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)) {
                    Text(
                        "Choisissez un modèle parmi vos fournisseurs synchronisés ou saisissez un identifiant sur mesure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(candidateTtsModels.size) { index ->
                                val m = candidateTtsModels[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.settings.setTtsProviderModel(m)
                                            showModelDialog = false
                                        }
                                        .padding(vertical = AgoraSpacing.Sm, horizontal = AgoraSpacing.Xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (ttsProviderModel ?: "tts-1") == m,
                                        onClick = {
                                            viewModel.settings.setTtsProviderModel(m)
                                            showModelDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                    Text(m, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        label = { Text(stringResource(R.string.tts_model_custom)) },
                        placeholder = { Text("ex: tts-1, eleventts, etc.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = customModelInput.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.settings.setTtsProviderModel(trimmed)
                        }
                        showModelDialog = false
                    }
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialog: Sélection de la voix
    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text(stringResource(R.string.tts_voice)) },
            text = {
                Box(modifier = Modifier.heightIn(max = 360.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        when (currentMode) {
                            TtsEngineMode.GEMINI_CLOUD -> {
                                items(testEngine.geminiVoices) { voice ->
                                    val isSelected = ttsVoice == voice
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.settings.setTtsVoice(voice)
                                                showVoiceDialog = false
                                            }
                                            .padding(vertical = AgoraSpacing.Sm, horizontal = AgoraSpacing.Xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.settings.setTtsVoice(voice)
                                                showVoiceDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                        Text(voice, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                            TtsEngineMode.OPENAI_CLOUD -> {
                                items(testEngine.openAiVoices) { voice ->
                                    val isSelected = ttsVoice == voice
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.settings.setTtsVoice(voice)
                                                showVoiceDialog = false
                                            }
                                            .padding(vertical = AgoraSpacing.Sm, horizontal = AgoraSpacing.Xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.settings.setTtsVoice(voice)
                                                showVoiceDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                        Text(
                                            voice.replaceFirstChar { it.uppercase() },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                            TtsEngineMode.KOKORO_LOCAL -> {
                                items(KokoroVoices.VOICES) { kv ->
                                    val isSelected = ttsVoice == kv.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.settings.setTtsVoice(kv.id)
                                                showVoiceDialog = false
                                            }
                                            .padding(vertical = AgoraSpacing.Sm, horizontal = AgoraSpacing.Xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.settings.setTtsVoice(kv.id)
                                                showVoiceDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                        Column {
                                            Text(
                                                kv.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                "Langue: ${kv.lang} · id: ${kv.id}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            TtsEngineMode.SYSTEM -> {
                                val systemLangs = listOf(
                                    "fr" to "Français",
                                    "en" to "English",
                                    "es" to "Español",
                                    "de" to "Deutsch",
                                    "it" to "Italiano",
                                    "ja" to "日本語"
                                )
                                items(systemLangs) { (code, name) ->
                                    val isSelected = ttsVoice == code
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.settings.setTtsVoice(code)
                                                showVoiceDialog = false
                                            }
                                            .padding(vertical = AgoraSpacing.Sm, horizontal = AgoraSpacing.Xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.settings.setTtsVoice(code)
                                                showVoiceDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                        Text(name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
internal fun EngineOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    SettingsItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor
            )
        },
        trailingContent = {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun KokoroDownloadCard(
    status: KokoroModelStatus,
    progress: Float,
    onStartDownload: () -> Unit,
    onDeleteModel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Xs),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF13231B)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E3A2B)),
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(AgoraSpacing.Xl)
                    )
                    Text(
                        "Modèle Kokoro (~350 Mo)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                when (status) {
                    KokoroModelStatus.DOWNLOADING -> {
                        TextButton(onClick = onDeleteModel) {
                            Text("Annuler", color = Color(0xFFF87171), fontSize = 12.sp)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onStartDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399))
                        ) {
                            Text("Télécharger", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (status == KokoroModelStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AgoraSpacing.Sm)
                        .clip(RoundedCornerShape(8.dp)),
                    color = Color(0xFF34D399),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
                Text(
                    text = "Téléchargement en cours : ${(progress * 100).toInt()}% (Wi-Fi recommandé)",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                Text(
                    text = "Téléchargement requis pour le fonctionnement 100% hors-ligne.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = AgoraSpacing.Xs)
                )
            }
        }
    }
}
