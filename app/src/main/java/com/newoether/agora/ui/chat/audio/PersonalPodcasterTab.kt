package com.newoether.agora.ui.chat.audio

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.newoether.agora.R
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.podcast.PersonalPodcasterPipeline
import com.newoether.agora.podcast.PodcastResult
import com.newoether.agora.studio.tts.kokoro.KokoroVoices
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator as CircularProgressIndicator
import com.newoether.agora.ui.theme.OutfitFamily
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PersonalPodcasterTab(
    ttsEngine: FullFlowTtsEngine,
    onInsertIntoChat: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pipeline = remember { PersonalPodcasterPipeline(context, ttsEngine) }

    var topic by remember { mutableStateOf("Les avancées de l'IA multimodale et locale") }
    var timeframe by remember { mutableStateOf("cette semaine") }

    var isGenerating by remember { mutableStateOf(false) }
    var progressStep by remember { mutableIntStateOf(0) }
    var progressMessage by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PodcastResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // TTS engine and voice are read from Settings (Providers → Models → Podcast TTS)
    // No redundant selectors here — the Podcaster uses what's already configured.
    val ttsEngineMode = remember {
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            val settings = app?.requireContainer()?.settingsRepository
            val modeStr = settings?.ttsEngineMode?.value ?: "GEMINI_CLOUD"
            runCatching { TtsEngineMode.valueOf(modeStr) }.getOrDefault(TtsEngineMode.GEMINI_CLOUD)
        } catch (_: Exception) {
            TtsEngineMode.GEMINI_CLOUD
        }
    }
    val ttsVoice = remember {
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            val settings = app?.requireContainer()?.settingsRepository
            val voice = settings?.ttsVoice?.value ?: ""
            if (voice.isNotBlank()) voice
            else when (ttsEngineMode) {
                TtsEngineMode.GEMINI_CLOUD -> "Kore"
                TtsEngineMode.OPENAI_CLOUD -> "alloy"
                TtsEngineMode.KOKORO_LOCAL -> KokoroVoices.DEFAULT_VOICE_ID
                TtsEngineMode.SYSTEM -> "fr"
            }
        } catch (_: Exception) { "Kore" }
    }

    // Media player state
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Hero Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF131C2E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.personal_podcaster_title),
                            fontFamily = OutfitFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.personal_podcaster_desc),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = AgoraAlpha.Hint),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Configuration Form
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF141A22),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "1. Définir le sujet & la période",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )

                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Sujet ou thème du podcast") },
                        placeholder = { Text("Ex: Lancement spatial d'Artemis, Fusion nucléaire, etc.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = timeframe,
                        onValueChange = { timeframe = it },
                        label = { Text("Période ciblée pour la recherche") },
                        placeholder = { Text("Ex: cette semaine, ce mois-ci, 24 heures") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Show configured TTS settings — configured in Paramètres → Podcast
                    // (Modèle de rédaction du script + Modèle vocal de synthèse)
                    Text(
                        text = "2. Synthèse vocale (configurée dans Paramètres)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )

                    val engineLabel = when (ttsEngineMode) {
                        TtsEngineMode.GEMINI_CLOUD -> "Gemini Cloud"
                        TtsEngineMode.OPENAI_CLOUD -> "Provider API"
                        TtsEngineMode.KOKORO_LOCAL -> "Kokoro hors-ligne"
                        TtsEngineMode.SYSTEM -> "Système Android"
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.04f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Divider)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voix : $ttsVoice · $engineLabel",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Text(
                    text = "Modifiez le modèle vocal dans Paramètres → Podcast de conversation",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Generate Button
                    Button(
                        onClick = {
                            if (topic.isNotBlank() && !isGenerating) {
                                isGenerating = true
                                errorMessage = null
                                result = null
                                progressStep = 1
                                progressMessage = "Recherche web en direct sur le sujet..."

                                scope.launch {
                                    try {
                                        val res = pipeline.execute(
                                            topic = topic.trim(),
                                            timeframe = timeframe.trim(),
                                            ttsMode = ttsEngineMode,
                                            voice = ttsVoice,
                                            onProgress = { p ->
                                                progressStep = p.step
                                                progressMessage = p.detailMessage
                                            }
                                        )
                                        result = res
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Erreur de production du podcast"
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            }
                        },
                        enabled = !isGenerating && topic.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Production en cours...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Produire le Podcast", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // Progress Stepper when generating
        if (isGenerating) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Handle)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Étape $progressStep/4",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressStep / 4f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF38BDF8),
                            trackColor = Color.White.copy(alpha = AgoraAlpha.Divider)
                        )

                        Text(
                            text = progressMessage,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Error message if any
        if (errorMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Completed Podcast Result Card
        result?.let { pod ->
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0B132B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Podcast Prêt",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }

                            val totalSecs = (pod.audioDurationMs / 1000).toInt()
                            Text(
                                text = String.format("Durée : %02d:%02d", totalSecs / 60, totalSecs % 60),
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = pod.topic,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Audio Player Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = AgoraAlpha.Subtle))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        pod.audioFile?.let { audio ->
                                            if (audio.exists()) {
                                                try {
                                                    if (isPlaying) {
                                                        mediaPlayer.pause()
                                                        isPlaying = false
                                                    } else {
                                                        mediaPlayer.reset()
                                                        mediaPlayer.setDataSource(audio.absolutePath)
                                                        mediaPlayer.prepare()
                                                        mediaPlayer.setOnCompletionListener { isPlaying = false }
                                                        mediaPlayer.start()
                                                        isPlaying = true
                                                    }
                                                } catch (_: Exception) {
                                                    isPlaying = false
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPlaying) "En cours de lecture..." else "Écouter l'enregistrement",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = pod.audioFile?.name ?: "Fichier audio généré",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Open HTML Web Player
                            pod.htmlFile?.let { html ->
                                OutlinedButton(
                                    onClick = {
                                        if (html.exists()) {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    html
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "text/html")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Page Web", fontSize = 12.sp)
                                }
                            }

                            // Insert into Chat
                            Button(
                                onClick = {
                                    val formatted = buildString {
                                        appendLine("🎙️ **Podcast : ${pod.topic}** (${pod.timeframe})")
                                        appendLine()
                                        appendLine(pod.script)
                                    }
                                    onInsertIntoChat(formatted)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF38BDF8),
                                    contentColor = Color(0xFF0F172A)
                                )
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Vers le chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Transcript preview
                        Text(
                            text = "Script du podcast :",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = AgoraAlpha.Handle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pod.script,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
