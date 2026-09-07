package com.newoether.agora.ui.chat.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.R
import com.newoether.agora.ui.theme.OutfitFamily

enum class AudioStudioTab {
    LIVE_STREAM,
    TEXT_TO_SPEECH,
    AUDIO_FILES
}

@Composable
fun FullFlowAudioStudioDialog(
    visible: Boolean,
    resolvedApiKey: String?,
    initialTab: AudioStudioTab = AudioStudioTab.LIVE_STREAM,
    initialTtsText: String = "",
    onDismissRequest: () -> Unit,
    onInsertIntoChat: (String) -> Unit,
    onOpenGenMailWithText: ((String) -> Unit)? = null,
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(initialTab) }
    val speechManager = remember { FullFlowSpeechRecognizerManager(context, scope) }
    val ttsEngine = remember { FullFlowTtsEngine(context) }
    val audioFileManager = remember { FullFlowAudioFileManager(context, scope, resolvedApiKey) }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.destroy()
            ttsEngine.shutdown()
            audioFileManager.cleanup()
        }
    }

    Dialog(
        onDismissRequest = {
            speechManager.stopListening()
            ttsEngine.stop()
            audioFileManager.stopPlayback()
            onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.audio_studio_title),
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.audio_studio_subtitle),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    IconButton(
                        onClick = {
                            speechManager.stopListening()
                            ttsEngine.stop()
                            audioFileManager.stopPlayback()
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141A22))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AudioStudioTabButton(
                        text = stringResource(R.string.tab_live_transcription),
                        icon = Icons.Default.Mic,
                        selected = activeTab == AudioStudioTab.LIVE_STREAM,
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = AudioStudioTab.LIVE_STREAM }
                    )
                    AudioStudioTabButton(
                        text = stringResource(R.string.tab_text_to_speech),
                        icon = Icons.Default.RecordVoiceOver,
                        selected = activeTab == AudioStudioTab.TEXT_TO_SPEECH,
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = AudioStudioTab.TEXT_TO_SPEECH }
                    )
                    AudioStudioTabButton(
                        text = stringResource(R.string.tab_audio_files),
                        icon = Icons.Default.AudioFile,
                        selected = activeTab == AudioStudioTab.AUDIO_FILES,
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = AudioStudioTab.AUDIO_FILES }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Active Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        AudioStudioTab.LIVE_STREAM -> {
                            FullFlowLiveTranscriptionTab(
                                manager = speechManager,
                                onInsertIntoChat = { text ->
                                    onInsertIntoChat(text)
                                    onDismissRequest()
                                },
                                onSendToTts = { text ->
                                    activeTab = AudioStudioTab.TEXT_TO_SPEECH
                                    ttsEngine.speak(text)
                                }
                            )
                        }
                        AudioStudioTab.TEXT_TO_SPEECH -> {
                            FullFlowTtsTab(
                                ttsEngine = ttsEngine,
                                initialText = initialTtsText
                            )
                        }
                        AudioStudioTab.AUDIO_FILES -> {
                            FullFlowAudioFilesTab(
                                manager = audioFileManager,
                                onInsertIntoChat = { text ->
                                    onInsertIntoChat(text)
                                    onDismissRequest()
                                },
                                onSendToGenMail = onOpenGenMailWithText?.let { callback ->
                                    { text ->
                                        callback(text)
                                        onDismissRequest()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioStudioTabButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.5f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
