package com.newoether.agora.ui.chat.audio

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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

@Composable
fun FullFlowTtsTab(
    ttsEngine: FullFlowTtsEngine,
    initialText: String
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf(initialText) }

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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, "Coller", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }

                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = { textInput = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, "Effacer", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
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
                            color = Color.White.copy(alpha = 0.35f)
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
                        .heightIn(min = 100.dp, max = 160.dp)
                )
            }
        }

        // Quick Suggestions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { textInput = context.getString(R.string.audio_tts_sample_intro) },
                label = { Text("Intro FullFlow", fontSize = 11.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFF161E2A),
                    labelColor = Color.White.copy(alpha = 0.8f)
                ),
                border = null
            )
            SuggestionChip(
                onClick = { textInput = context.getString(R.string.audio_tts_sample_summary) },
                label = { Text("Résumé vocal", fontSize = 11.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFF161E2A),
                    labelColor = Color.White.copy(alpha = 0.8f)
                ),
                border = null
            )
        }

        // Spoken Visualizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101620)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF222E40))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (ttsEngine.isSpeaking) "Lecture vocale en cours..." else "Prêt pour la synthèse",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    FullFlowAudioPulseDot(
                        isActive = ttsEngine.isSpeaking,
                        color = Color(0xFF38BDF8)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                FullFlowAudioWaveVisualizer(
                    isActive = ttsEngine.isSpeaking,
                    amplitude = if (ttsEngine.isSpeaking) 0.85f else 0.05f,
                    barCount = 20,
                    maxBarHeight = 44.dp,
                    barColorStart = Color(0xFF38BDF8),
                    barColorEnd = Color(0xFF4ADE80)
                )
            }
        }

        // Voice Controls (Speed & Pitch)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2836))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Speech Rate Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.audio_tts_speed), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("%.1fx".format(ttsEngine.speechRate), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = ttsEngine.speechRate,
                    onValueChange = { ttsEngine.speechRate = it },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

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

        // Controls (Play, Stop)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ttsEngine.isSpeaking) {
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
                    if (ttsEngine.isSpeaking) {
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
                Icon(
                    imageVector = if (ttsEngine.isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (ttsEngine.isSpeaking) "Arrêter la lecture" else stringResource(R.string.audio_tts_play),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
