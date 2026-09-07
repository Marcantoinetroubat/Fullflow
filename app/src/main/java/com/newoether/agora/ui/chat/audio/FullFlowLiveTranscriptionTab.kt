package com.newoether.agora.ui.chat.audio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.newoether.agora.R

@Composable
fun FullFlowLiveTranscriptionTab(
    manager: FullFlowSpeechRecognizerManager,
    onInsertIntoChat: (String) -> Unit,
    onSendToTts: (String) -> Unit
) {
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) manager.startListening()
    }

    val combinedTranscript = remember(manager.fullTranscript, manager.partialText) {
        if (manager.partialText.isNotBlank()) {
            if (manager.fullTranscript.isNotBlank()) {
                "${manager.fullTranscript} ${manager.partialText}"
            } else {
                manager.partialText
            }
        } else {
            manager.fullTranscript
        }
    }

    val wordCount = remember(combinedTranscript) {
        if (combinedTranscript.isBlank()) 0
        else combinedTranscript.trim().split("\\s+".toRegex()).size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visualizer Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131820)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF263242))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FullFlowAudioPulseDot(
                            isActive = manager.isListening && !manager.isPaused,
                            color = if (manager.isListening) Color(0xFF10B981) else Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                manager.isListening && !manager.isPaused -> stringResource(R.string.audio_listening)
                                manager.isPaused -> stringResource(R.string.audio_paused)
                                else -> stringResource(R.string.audio_ready)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Text(
                        text = "%02d:%02d · %d mots".format(
                            manager.elapsedSeconds / 60,
                            manager.elapsedSeconds % 60,
                            wordCount
                        ),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                FullFlowAudioWaveVisualizer(
                    isActive = manager.isListening && !manager.isPaused,
                    amplitude = manager.rmsLevel,
                    barCount = 24,
                    maxBarHeight = 52.dp,
                    barColorStart = Color(0xFF38BDF8),
                    barColorEnd = Color(0xFF818CF8)
                )
            }
        }

        // Live Transcript Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2836))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (combinedTranscript.isBlank()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Parlez au micro pour voir la transcription s'afficher en temps réel...",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (manager.fullTranscript.isNotBlank()) {
                            Text(
                                text = manager.fullTranscript,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = Color.White
                            )
                        }
                        if (manager.partialText.isNotBlank()) {
                            Text(
                                text = (if (manager.fullTranscript.isNotBlank()) " " else "") + manager.partialText,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = Color(0xFF7DD3FC),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (combinedTranscript.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val clip = ClipData.newPlainText("Transcription", combinedTranscript)
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.audio_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copier",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Action Toolbar
        if (combinedTranscript.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { manager.clearTranscript() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.audio_clear), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onSendToTts(combinedTranscript) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lire (TTS)", fontSize = 12.sp)
                }

                Button(
                    onClick = { onInsertIntoChat(combinedTranscript) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.audio_insert_to_chat), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Primary Microphone Control Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (manager.isListening) {
                IconButton(
                    onClick = {
                        if (manager.isPaused) manager.resumeListening() else manager.pauseListening()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = if (manager.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))
            }

            val micBg = if (manager.isListening && !manager.isPaused) {
                Brush.radialGradient(listOf(Color(0xFFEF4444), Color(0xFF991B1B)))
            } else {
                Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF1E293B)))
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(micBg)
                    .clickable {
                        if (!hasRecordPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (manager.isListening) manager.stopListening() else manager.startListening()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (manager.isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
