package com.newoether.agora.ui.chat.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.newoether.agora.BuildConfig
import com.newoether.agora.R
import com.newoether.agora.api.gemini.live.GeminiLiveClient
import com.newoether.agora.api.gemini.live.GeminiLivePersona
import com.newoether.agora.api.gemini.live.GeminiLiveState
import com.newoether.agora.api.gemini.live.GeminiLiveTranscript
import com.newoether.agora.api.gemini.live.GeminiLiveVoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiLiveVoiceDialog(
    resolvedApiKey: String?,
    onDismissRequest: () -> Unit,
    onExportTranscriptsToChat: ((List<GeminiLiveTranscript>) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedVoice by remember { mutableStateOf(GeminiLiveVoice.DEFAULT) }
    var selectedPersona by remember { mutableStateOf(GeminiLivePersona.DEFAULT_PRESETS.first()) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showPersonaMenu by remember { mutableStateOf(false) }
    var customApiKeyInput by remember { mutableStateOf("") }
    var showApiKeyPrompt by remember { mutableStateOf(false) }
    var userTypedMessage by remember { mutableStateOf("") }
    var showSubtitles by remember { mutableStateOf(true) }

    val effectiveApiKey = remember(resolvedApiKey, customApiKeyInput) {
        when {
            customApiKeyInput.isNotBlank() -> customApiKeyInput.trim()
            !resolvedApiKey.isNullOrBlank() -> resolvedApiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    val client = remember { GeminiLiveClient(scope) }

    val sessionState by client.sessionState.collectAsState()
    val userVolume by client.userVolumeLevel.collectAsState()
    val modelVolume by client.modelVolumeLevel.collectAsState()
    val isMuted by client.isMuted.collectAsState()
    val transcripts by client.transcripts.collectAsState()

    // Start session when permission and API key are available
    LaunchedEffect(hasAudioPermission, effectiveApiKey, selectedVoice, selectedPersona) {
        if (hasAudioPermission && effectiveApiKey.isNotBlank()) {
            client.startSession(effectiveApiKey, selectedVoice, selectedPersona)
        } else if (effectiveApiKey.isBlank()) {
            showApiKeyPrompt = true
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            client.stopSession()
        }
    }

    Dialog(
        onDismissRequest = {
            client.stopSession()
            onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gemini_live_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.gemini_live_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = selectedVoice.displayName + " · " + selectedPersona.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showSubtitles = !showSubtitles },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (showSubtitles) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Sous-titres",
                                tint = if (showSubtitles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                client.stopSession()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("gemini_live_close_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                // Status Badge
                StatusPill(sessionState = sessionState, isMuted = isMuted)

                Spacer(modifier = Modifier.height(12.dp))

                // Centerpiece: Dynamic Reactive Voice Orb
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasAudioPermission) {
                        PermissionCard(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    } else if (effectiveApiKey.isBlank()) {
                        ApiKeyCard(
                            onSaveKey = { key ->
                                customApiKeyInput = key
                                showApiKeyPrompt = false
                            }
                        )
                    } else {
                        GeminiLiveVoiceOrb(
                            sessionState = sessionState,
                            userVolume = userVolume,
                            modelVolume = modelVolume,
                            isMuted = isMuted,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("gemini_live_orb")
                        )
                    }
                }

                // Persona & Voice Configuration Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Selector Chip
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showVoiceMenu = true },
                            label = {
                                Text(
                                    "Voix: ${selectedVoice.displayName}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("gemini_live_voice_chip")
                        )
                        DropdownMenu(
                            expanded = showVoiceMenu,
                            onDismissRequest = { showVoiceMenu = false }
                        ) {
                            GeminiLiveVoice.entries.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.displayName, fontWeight = FontWeight.SemiBold)
                                            Text(voice.tone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        selectedVoice = voice
                                        showVoiceMenu = false
                                        if (effectiveApiKey.isNotBlank()) {
                                            client.startSession(effectiveApiKey, voice, selectedPersona)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Persona Selector Chip
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showPersonaMenu = true },
                            label = {
                                Text(
                                    selectedPersona.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("gemini_live_persona_chip")
                        )
                        DropdownMenu(
                            expanded = showPersonaMenu,
                            onDismissRequest = { showPersonaMenu = false }
                        ) {
                            GeminiLivePersona.DEFAULT_PRESETS.forEach { persona ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(persona.title, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                persona.systemPrompt.take(60) + "…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedPersona = persona
                                        showPersonaMenu = false
                                        if (effectiveApiKey.isNotBlank()) {
                                            client.startSession(effectiveApiKey, selectedVoice, persona)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Transcripts Subtitle View
                if (showSubtitles) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(transcripts.size) {
                        if (transcripts.isNotEmpty()) {
                            listState.animateScrollToItem(transcripts.size - 1)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.9f)
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        if (transcripts.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Parlez naturellement à Gemini. Vos échanges s'afficheront ici en direct.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(transcripts, key = { it.id }) { item ->
                                    val isUser = item.role == "user"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 14.dp,
                                                topEnd = 14.dp,
                                                bottomStart = if (isUser) 14.dp else 4.dp,
                                                bottomEnd = if (isUser) 4.dp else 14.dp
                                            ),
                                            color = if (isUser) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            modifier = Modifier.widthIn(max = 280.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                Text(
                                                    text = if (isUser) "Vous" else "Gemini",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = item.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Transfer transcripts to chat button (if conversation exists)
                if (transcripts.isNotEmpty() && onExportTranscriptsToChat != null) {
                    TextButton(
                        onClick = {
                            onExportTranscriptsToChat(transcripts)
                            client.stopSession()
                            onDismissRequest()
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.gemini_live_export_to_chat), style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Text Input Bar for Hybrid text+voice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = userTypedMessage,
                        onValueChange = { userTypedMessage = it },
                        placeholder = { Text("Écrire ou parler…", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )

                    IconButton(
                        onClick = {
                            if (userTypedMessage.isNotBlank()) {
                                client.sendTextMessage(userTypedMessage.trim())
                                userTypedMessage = ""
                            }
                        },
                        enabled = userTypedMessage.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (userTypedMessage.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Envoyer",
                            tint = if (userTypedMessage.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Primary Voice Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interrupt Button
                    FilledTonalIconButton(
                        onClick = { client.interruptPlayback() },
                        enabled = sessionState is GeminiLiveState.Speaking,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("gemini_live_interrupt_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.gemini_live_interrupt))
                    }

                    // Main Mute / Unmute Mic Button (Prominent Center)
                    val micBgColor = if (isMuted) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val micIconColor = if (isMuted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }

                    IconButton(
                        onClick = { client.toggleMute() },
                        modifier = Modifier
                            .size(68.dp)
                            .background(micBgColor, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                            .testTag("gemini_live_mute_button")
                    ) {
                        Icon(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) stringResource(R.string.gemini_live_unmute) else stringResource(R.string.gemini_live_mute),
                            tint = micIconColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // End Session Hangup Button
                    IconButton(
                        onClick = {
                            client.stopSession()
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            .testTag("gemini_live_end_button")
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.gemini_live_end),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    sessionState: GeminiLiveState,
    isMuted: Boolean
) {
    val (statusText, badgeColor) = when {
        isMuted -> "Microphone coupé" to MaterialTheme.colorScheme.error
        sessionState is GeminiLiveState.Connecting -> stringResource(R.string.gemini_live_status_connecting) to MaterialTheme.colorScheme.primary
        sessionState is GeminiLiveState.Listening -> stringResource(R.string.gemini_live_status_listening) to MaterialTheme.colorScheme.tertiary
        sessionState is GeminiLiveState.Thinking -> stringResource(R.string.gemini_live_status_thinking) to MaterialTheme.colorScheme.secondary
        sessionState is GeminiLiveState.Speaking -> stringResource(R.string.gemini_live_status_speaking) to MaterialTheme.colorScheme.primary
        sessionState is GeminiLiveState.Error -> (sessionState as GeminiLiveState.Error).message.take(30) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.gemini_live_status_connected) to MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = badgeColor.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badgeColor, CircleShape)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = badgeColor
            )
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = stringResource(R.string.gemini_live_mic_permission_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.gemini_live_mic_permission_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.gemini_live_mic_permission_grant))
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    onSaveKey: (String) -> Unit
) {
    var tempKey by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Clé API Gemini requise",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.gemini_live_no_api_key),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = tempKey,
                onValueChange = { tempKey = it },
                placeholder = { Text("Coller la clé API Google AI Studio…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { if (tempKey.isNotBlank()) onSaveKey(tempKey.trim()) },
                enabled = tempKey.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Démarrer la session")
            }
        }
    }
}
