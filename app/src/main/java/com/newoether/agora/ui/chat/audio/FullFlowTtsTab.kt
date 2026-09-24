package com.newoether.agora.ui.chat.audio

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.studio.tts.kokoro.KokoroModelStatus
import com.newoether.agora.studio.tts.kokoro.KokoroVoices

@Composable
fun FullFlowTtsTab(
    ttsEngine: FullFlowTtsEngine,
    initialText: String
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf(initialText) }

    LaunchedEffect(ttsEngine.isInitialized, initialText) {
        if (FullFlowAudioController.shouldAutoPlayTts && ttsEngine.isInitialized && textInput.isNotBlank()) {
            FullFlowAudioController.shouldAutoPlayTts = false
            ttsEngine.speak(textInput)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Text Input Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111722)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF222F42))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_tts_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row {
                        IconButton(
                            onClick = {
                                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                                val paste = clip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (paste.isNotBlank()) textInput = paste
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.ContentPaste, "Coller", tint = Color.White.copy(alpha = AgoraAlpha.Hint), modifier = Modifier.size(16.dp))
                        }

                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = { textInput = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Close, "Effacer", tint = Color.White.copy(alpha = AgoraAlpha.Hint), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.audio_tts_input_placeholder),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = AgoraAlpha.Disabled)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 150.dp)
                )
            }
        }

        // Engine Selector Card (Gemini Cloud vs Kokoro Local vs Android System)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101724)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E283A))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Moteur de synthèse",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TtsEngineOption(
                        selected = ttsEngine.ttsEngineMode == TtsEngineMode.GEMINI_CLOUD,
                        icon = Icons.Default.AutoAwesome,
                        title = "Gemini",
                        subtitle = "Cloud HD",
                        accent = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ttsEngine.ttsEngineMode = TtsEngineMode.GEMINI_CLOUD
                            ttsEngine.saveSettings()
                        }
                    )
                    TtsEngineOption(
                        selected = ttsEngine.ttsEngineMode == TtsEngineMode.OPENAI_CLOUD,
                        icon = Icons.Default.Cloud,
                        title = "OpenAI",
                        subtitle = "Multi-Prov.",
                        accent = Color(0xFF10A37F),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ttsEngine.ttsEngineMode = TtsEngineMode.OPENAI_CLOUD
                            ttsEngine.saveSettings()
                        }
                    )
                    TtsEngineOption(
                        selected = ttsEngine.ttsEngineMode == TtsEngineMode.KOKORO_LOCAL,
                        icon = Icons.Default.OfflineBolt,
                        title = "Kokoro",
                        subtitle = "Local off.",
                        accent = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ttsEngine.ttsEngineMode = TtsEngineMode.KOKORO_LOCAL
                            ttsEngine.saveSettings()
                        }
                    )
                    TtsEngineOption(
                        selected = ttsEngine.ttsEngineMode == TtsEngineMode.SYSTEM,
                        icon = Icons.Default.PhoneAndroid,
                        title = "Système",
                        subtitle = "Android",
                        accent = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ttsEngine.ttsEngineMode = TtsEngineMode.SYSTEM
                            ttsEngine.saveSettings()
                        }
                    )
                }
                Text(
                    text = when (ttsEngine.ttsEngineMode) {
                        TtsEngineMode.GEMINI_CLOUD -> "gemini-3.1-flash-tts-preview · requiert une clé API"
                        TtsEngineMode.OPENAI_CLOUD -> "OpenAI / Multi-Provider (tts-1) · requiert une clé API"
                        TtsEngineMode.KOKORO_LOCAL -> "kokoro multi-langue · 100 % hors-ligne après téléchargement"
                        TtsEngineMode.SYSTEM -> "Synthèse vocale Android intégrée"
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Playback Speed Slider Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101724)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E283A))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = when (ttsEngine.ttsEngineMode) {
                                TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8)
                                TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F)
                                TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399)
                                TtsEngineMode.SYSTEM -> Color(0xFFA78BFA)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vitesse de lecture",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "%.2fx".format(ttsEngine.playbackSpeed),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = ttsEngine.playbackSpeed,
                    onValueChange = { ttsEngine.updatePlaybackSpeed(it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = when (ttsEngine.ttsEngineMode) {
                            TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8)
                            TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F)
                            TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399)
                            TtsEngineMode.SYSTEM -> Color(0xFFA78BFA)
                        },
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }

        // Dialogue mode (2 speakers max, Cloud only)
        if (ttsEngine.ttsEngineMode == TtsEngineMode.GEMINI_CLOUD) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101724)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E283A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dialogue à 2 voix",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Les noms doivent correspondre aux étiquettes du texte",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = ttsEngine.ttsDialogueMode,
                            onCheckedChange = {
                                ttsEngine.ttsDialogueMode = it
                                ttsEngine.saveSettings()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF38BDF8)
                            )
                        )
                    }

                    if (ttsEngine.ttsDialogueMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DialogueSpeakerRow(
                            name = ttsEngine.speaker1Name,
                            onNameChange = {
                                ttsEngine.speaker1Name = it
                                ttsEngine.saveSettings()
                            },
                            selectedVoice = ttsEngine.speaker1Voice,
                            onVoiceChange = {
                                ttsEngine.speaker1Voice = it
                                ttsEngine.saveSettings()
                            },
                            voices = ttsEngine.geminiVoices
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DialogueSpeakerRow(
                            name = ttsEngine.speaker2Name,
                            onNameChange = {
                                ttsEngine.speaker2Name = it
                                ttsEngine.saveSettings()
                            },
                            selectedVoice = ttsEngine.speaker2Voice,
                            onVoiceChange = {
                                ttsEngine.speaker2Voice = it
                                ttsEngine.saveSettings()
                            },
                            voices = ttsEngine.geminiVoices
                        )
                    }
                }
            }
        }

        // Expressive Voice Selection, Kokoro Local options, or System Voice Controls
        if (ttsEngine.ttsEngineMode == TtsEngineMode.GEMINI_CLOUD) {
            // Gemini Cloud Voice options
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2836))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Voix Gemini AI Studio (30 disponibles)",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        items(ttsEngine.geminiVoices) { voice ->
                            val isSelected = voice == ttsEngine.selectedGeminiVoice
                            Surface(
                                onClick = {
                                    ttsEngine.selectedGeminiVoice = voice
                                    ttsEngine.saveSettings()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1B2330),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = AgoraAlpha.Subtle)
                                ),
                            ) {
                                Text(
                                    text = voice,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Audio Tags / Modificateurs émotionnels
                    Text(
                        text = "Nuances de style audio (insérer dans le prompt)",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ttsEngine.commonAudioTags) { tag ->
                            Surface(
                                onClick = {
                                    textInput = if (textInput.isBlank()) "$tag " else "$tag $textInput"
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF222C3C),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle))
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (ttsEngine.ttsEngineMode == TtsEngineMode.OPENAI_CLOUD) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2836))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Voix OpenAI / Multi-Fournisseur",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        items(ttsEngine.openAiVoices) { voice ->
                            val isSelected = voice == ttsEngine.selectedOpenAiVoice
                            Surface(
                                onClick = {
                                    ttsEngine.selectedOpenAiVoice = voice
                                    ttsEngine.saveSettings()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF10A37F) else Color(0xFF1E2836),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF10A37F) else Color.White.copy(alpha = AgoraAlpha.Subtle)
                                ),
                            ) {
                                Text(
                                    text = voice.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (ttsEngine.ttsEngineMode == TtsEngineMode.KOKORO_LOCAL) {
            KokoroLocalOptionsCard(ttsEngine)
        } else {
            // Android System Voice Controls
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2836))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.audio_tts_language),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        items(ttsEngine.availableLocales) { lang ->
                            val isSelected = lang.code == ttsEngine.selectedLocaleTag
                            Surface(
                                onClick = {
                                    ttsEngine.selectedLocaleTag = lang.code
                                    ttsEngine.saveSettings()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFA78BFA) else Color(0xFF1E2836),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFFA78BFA) else Color.White.copy(alpha = AgoraAlpha.Subtle)
                                ),
                            ) {
                                Text(
                                    text = lang.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.audio_tts_pitch), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text("%.1fx".format(ttsEngine.pitch), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = ttsEngine.pitch,
                        onValueChange = { ttsEngine.pitch = it },
                        valueRange = 0.5f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF818CF8),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }

        // Spoken Visualizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101620)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF222E40))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            ttsEngine.isSynthesizingCloud -> "Synthèse Gemini Cloud HD en cours..."
                            ttsEngine.isSynthesizingKokoro -> "Synthèse Kokoro locale en cours..."
                            ttsEngine.isSpeaking -> "Lecture vocale en cours..."
                            else -> "Prêt pour la synthèse"
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                    FullFlowAudioPulseDot(
                        isActive = ttsEngine.isBusy,
                        color = when (ttsEngine.ttsEngineMode) {
                            TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8)
                            TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F)
                            TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399)
                            TtsEngineMode.SYSTEM -> Color(0xFFA78BFA)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FullFlowAudioWaveVisualizer(
                    isActive = ttsEngine.isSpeaking,
                    amplitude = if (ttsEngine.isSpeaking) 0.85f else 0.05f,
                    barCount = 20,
                    maxBarHeight = 40.dp,
                    barColorStart = when (ttsEngine.ttsEngineMode) {
                        TtsEngineMode.GEMINI_CLOUD -> Color(0xFF38BDF8)
                        TtsEngineMode.OPENAI_CLOUD -> Color(0xFF10A37F)
                        TtsEngineMode.KOKORO_LOCAL -> Color(0xFF34D399)
                        TtsEngineMode.SYSTEM -> Color(0xFFA78BFA)
                    },
                    barColorEnd = Color(0xFF4ADE80)
                )
            }
        }

        // Controls (Play, Stop)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ttsEngine.isBusy) {
                OutlinedButton(
                    onClick = { ttsEngine.stop() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.audio_tts_stop), fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    if (ttsEngine.isBusy) {
                        ttsEngine.stop()
                    } else {
                        ttsEngine.speak(textInput)
                    }
                },
                enabled = textInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                if (ttsEngine.isSynthesizingCloud || ttsEngine.isSynthesizingKokoro) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (ttsEngine.isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (ttsEngine.isSpeaking) "Arrêter la lecture" else stringResource(R.string.audio_tts_play),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TtsEngineOption(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else Color(0xFF1B2330),
        border = BorderStroke(
            1.dp,
            if (selected) accent else Color.White.copy(alpha = AgoraAlpha.Subtle)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = AgoraAlpha.Hint)
            )
        }
    }
}

@Composable
private fun KokoroLocalOptionsCard(ttsEngine: FullFlowTtsEngine) {
    val kokoroStatus by ttsEngine.kokoroManager.status.collectAsState()
    val kokoroProgress by ttsEngine.kokoroManager.progress.collectAsState()
    val kokoroMessage by ttsEngine.kokoroManager.statusMessage.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2836))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kokoro local · 100 % hors-ligne",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (kokoroStatus) {
                        KokoroModelStatus.READY -> Color(0xFF34D399).copy(alpha = 0.15f)
                        KokoroModelStatus.DOWNLOADING -> Color(0xFFFBBF24).copy(alpha = 0.15f)
                        KokoroModelStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        KokoroModelStatus.NOT_DOWNLOADED -> Color.White.copy(alpha = AgoraAlpha.Subtle)
                    }
                ) {
                    Text(
                        text = when (kokoroStatus) {
                            KokoroModelStatus.READY -> "Prêt"
                            KokoroModelStatus.DOWNLOADING -> "${(kokoroProgress * 100).toInt()} %"
                            KokoroModelStatus.ERROR -> "Erreur"
                            KokoroModelStatus.NOT_DOWNLOADED -> "Non installé"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (kokoroStatus) {
                            KokoroModelStatus.READY -> Color(0xFF34D399)
                            KokoroModelStatus.DOWNLOADING -> Color(0xFFFBBF24)
                            KokoroModelStatus.ERROR -> Color(0xFFEF4444)
                            KokoroModelStatus.NOT_DOWNLOADED -> Color.White.copy(alpha = AgoraAlpha.Hint)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (kokoroStatus) {
                KokoroModelStatus.READY -> {
                    Text(
                        text = "Modèle installé sur l'appareil. Aucune donnée envoyée.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextButton(
                        onClick = { ttsEngine.kokoroManager.deleteModel() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444).copy(alpha = 0.8f))
                    ) {
                        Text("Supprimer le modèle (libère ~800 Mo)", fontSize = 11.sp)
                    }
                }
                KokoroModelStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { kokoroProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        color = Color(0xFF34D399),
                        trackColor = Color.White.copy(alpha = AgoraAlpha.Divider)
                    )
                    Text(
                        text = kokoroMessage.ifBlank { "Téléchargement en cours…" },
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                KokoroModelStatus.ERROR -> {
                    Text(
                        text = kokoroMessage.ifBlank { "Échec du téléchargement." },
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Button(
                        onClick = { ttsEngine.kokoroManager.startDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Réessayer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                KokoroModelStatus.NOT_DOWNLOADED -> {
                    Text(
                        text = "Voix neuronale locale (~350 Mo, Wi-Fi recommandé). Une fois installé, fonctionne sans connexion.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = { ttsEngine.kokoroManager.startDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Télécharger le modèle Kokoro", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (kokoroStatus == KokoroModelStatus.READY) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Voix Kokoro (${KokoroVoices.VOICES.size} disponibles)",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(KokoroVoices.VOICES) { voice ->
                        val isSelected = voice.id == ttsEngine.selectedKokoroVoiceId
                        Surface(
                            onClick = {
                                ttsEngine.selectedKokoroVoiceId = voice.id
                                ttsEngine.saveSettings()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF34D399) else Color(0xFF1B2330),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF34D399) else Color.White.copy(alpha = AgoraAlpha.Subtle)
                            ),
                        ) {
                            Text(
                                text = "${voice.label} · ${voice.lang}",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Unified speed is controlled globally above.
            }
        }
    }
}

@Composable
private fun DialogueSpeakerRow(
    name: String,
    onNameChange: (String) -> Unit,
    selectedVoice: String,
    onVoiceChange: (String) -> Unit,
    voices: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nom du locuteur", fontSize = 11.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedLabelColor = Color(0xFF38BDF8),
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(voices) { voice ->
                val isSelected = voice == selectedVoice
                Surface(
                    onClick = { onVoiceChange(voice) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1B2330),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = AgoraAlpha.Subtle)
                    ),
                ) {
                    Text(
                        text = voice,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
