package com.newoether.agora.ui.chat.audio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.AgoraAlpha

@Composable
fun FullFlowAudioFilesTab(
    manager: FullFlowAudioFileManager,
    onInsertIntoChat: (String) -> Unit,
    onSendToGenMail: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) manager.loadAudioFromUri(uri)
    }

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
        if (granted) manager.startRecordingMemo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AgoraSpacing.Lg)
            .verticalScroll(rememberScrollState())
    ) {
        // Selection Buttons (Import or Record)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AgoraSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
        ) {
            OutlinedButton(
                onClick = { audioPicker.launch("audio/*") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)__,
                border = BorderStroke(1.dp__, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(AgoraSpacing.Xl))
                Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                Text(stringResource(R.string.audio_import_file), fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (manager.isRecording) {
                        manager.stopRecordingMemo()
                    } else {
                        if (!hasRecordPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            manager.startRecordingMemo()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manager.isRecording) Color(0xFFEF4444) else Color(0xFF1E293B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)__,
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(
                    imageVector = if (manager.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(AgoraSpacing.Xl)
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                Text(
                    text = if (manager.isRecording) "Arrêter (${manager.recordingElapsedSeconds}s)" else stringResource(R.string.audio_record_memo),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Active Audio File Preview Card
        val currentAudio = manager.selectedAudio
        if (currentAudio != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AgoraSpacing.Sm),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131922)),
                shape = RoundedCornerShape(10.dp)__,
                border = BorderStroke(1.dp__, Color(0xFF243042))
            ) {
                Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp)__)
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(AgoraSpacing.Xl)
                                )
                            }
                            Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                            Column {
                                Text(
                                    text = currentAudio.displayName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "%s · %d Ko".format(
                                        currentAudio.mimeType.substringAfter("/").uppercase(),
                                        currentAudio.sizeBytes / 1024
                                    ),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { manager.togglePlayPausePreview() },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = AgoraAlpha.Divider))
                        ) {
                            Icon(
                                imageVector = if (manager.isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Lecture",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AgoraSpacing.Md))

                    Slider(
                        value = manager.playbackProgress,
                        onValueChange = { manager.seekPlayback(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = Color.White.copy(alpha = AgoraAlpha.Divider)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "%02d:%02d".format(
                                (manager.currentPositionMs / 1000) / 60,
                                (manager.currentPositionMs / 1000) % 60
                            ),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "%02d:%02d".format(
                                (currentAudio.durationMs / 1000) / 60,
                                (currentAudio.durationMs / 1000) % 60
                            ),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(AgoraSpacing.Md))

                    Button(
                        onClick = { manager.transcribeSelectedAudio() },
                        enabled = !manager.isTranscribing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)__,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        if (manager.isTranscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(AgoraSpacing.Xl),
                                color = Color.Black,
                                strokeWidth = 2.dp__
                            )
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(manager.transcriptionProgressText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(AgoraSpacing.Xl))
                            Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                            Text(stringResource(R.string.audio_transcribe_button), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AgoraSpacing.Md),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                shape = RoundedCornerShape(10.dp)__,
                border = BorderStroke(1.dp__, Color(0xFF1E2836))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = AgoraAlpha.Handle),
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(AgoraSpacing.Md))
                    Text(
                        text = stringResource(R.string.audio_no_file_selected),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.audio_supported_formats),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = AgoraSpacing.Xs)
                    )
                }
            }
        }

        if (manager.transcriptionError != null) {
            Text(
                text = manager.transcriptionError.orEmpty(),
                color = Color(0xFFF87171),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = AgoraSpacing.Xs)
            )
        }

        if (manager.transcriptionResult.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AgoraSpacing.Sm),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                shape = RoundedCornerShape(10.dp)__,
                border = BorderStroke(1.dp__, Color(0xFF2A3648))
            ) {
                Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.audio_transcription_result),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                val clip = ClipData.newPlainText("Transcription", manager.transcriptionResult)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.audio_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(AgoraSpacing.Xxxl)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copier", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(AgoraSpacing.Lg))
                        }
                    }

                    Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

                    Text(
                        text = manager.transcriptionResult,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    if (manager.transcriptionSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))
                        Text(
                            text = stringResource(R.string.audio_transcription_summary),
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                        Text(
                            text = manager.transcriptionSummary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }

                    Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)
                    ) {
                        onSendToGenMail?.let { sendGenMail ->
                            OutlinedButton(
                                onClick = { sendGenMail(manager.transcriptionResult) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)__,
                                border = BorderStroke(1.dp__, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.MailOutline, null, modifier = Modifier.size(AgoraSpacing.Lg))
                                Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                                Text("GenMail", fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { onInsertIntoChat(manager.transcriptionResult) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp)__,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(AgoraSpacing.Lg))
                            Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                            Text(stringResource(R.string.audio_insert_to_chat), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AgoraSpacing.Xl))
    }
}
