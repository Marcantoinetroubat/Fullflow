package com.newoether.agora.ui.chat.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.R
import com.newoether.agora.ui.theme.OutfitFamily

enum class AudioStudioTab {
    PODCASTER
}

@Composable
fun FullFlowAudioStudioDialog(
    visible: Boolean,
    resolvedApiKey: String?,
    initialTab: AudioStudioTab = AudioStudioTab.PODCASTER,
    initialTtsText: String = "",
    onDismissRequest: () -> Unit,
    onInsertIntoChat: (String) -> Unit,
    onOpenGenMailWithText: ((String) -> Unit)? = null,
) {
    if (!visible) return

    val context = LocalContext.current
    val ttsEngine = remember { FullFlowTtsEngine(context) }

    DisposableEffect(Unit) {
        onDispose {
            ttsEngine.shutdown()
        }
    }

    Dialog(
        onDismissRequest = {
            ttsEngine.stop()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Podcaster",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Générez un podcast audio personnalisé",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    IconButton(
                        onClick = {
                            ttsEngine.stop()
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
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Content — Podcaster only
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    PersonalPodcasterTab(
                        ttsEngine = ttsEngine,
                        onInsertIntoChat = { text ->
                            onInsertIntoChat(text)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}
