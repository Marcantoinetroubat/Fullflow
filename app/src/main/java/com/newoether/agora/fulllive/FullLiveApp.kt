package com.newoether.agora.fulllive

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.ui.components.ConfigDialog
import com.newoether.agora.fulllive.ui.components.DEFAULT_TRANSLATE_LANGUAGES
import com.newoether.agora.fulllive.ui.components.InsightsDialog
import com.newoether.agora.fulllive.ui.components.PersonaEditorDialog
import com.newoether.agora.fulllive.ui.components.SupportedLanguage
import com.newoether.agora.fulllive.ui.screens.ConversationScreen
import com.newoether.agora.fulllive.ui.screens.HistoryDetailScreen
import com.newoether.agora.fulllive.ui.screens.HomeScreen
import com.newoether.agora.fulllive.ui.screens.TraducteurScreen
import com.newoether.agora.fulllive.ui.screens.TranscripteurScreen
import com.newoether.agora.fulllive.viewmodel.CurrentScreen
import com.newoether.agora.fulllive.viewmodel.FullLiveViewModel

/**
 * FullLive voice assistant root composable, hosted as a full-screen dialog by
 * [com.newoether.agora.ui.chat.live.FullLiveHost] and
 * [com.newoether.agora.ui.chat.live.LiveVoiceHost].
 *
 * Note: the legacy standalone `MainActivity` was removed (never declared in
 * AndroidManifest.xml, zero references). FullLive is only ever embedded.
 */
@Composable
fun FullLiveApp(
    viewModel: FullLiveViewModel,
    onShareText: (String, String) -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val personas by viewModel.personas.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val liveUiState by viewModel.liveUiState.collectAsState()
    val sessionMessages by viewModel.sessionMessages.collectAsState()
    val detectedInsights by viewModel.detectedInsights.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<PersonaEntity?>(null) }
    var showPersonaEditor by remember { mutableStateOf(false) }
    var targetLanguage by remember { mutableStateOf(DEFAULT_TRANSLATE_LANGUAGES.first { it.code == "en" }) }

    // Permission launcher for audio and camera
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!audioGranted) {
            // Note for audio
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        )
    }

    when (val screen = currentScreen) {
        is CurrentScreen.Home -> {
            HomeScreen(
                personas = personas,
                sessions = sessions,
                onSelectPersona = { persona ->
                    viewModel.startConversation(persona)
                },
                onStartTranscripteur = {
                    viewModel.startTranscripteur()
                },
                onStartTraducteur = {
                    viewModel.startTraducteur(targetLanguage)
                },
                onOpenConfig = { showConfigDialog = true },
                onCreateNewPersona = {
                    editingPersona = null
                    showPersonaEditor = true
                },
                onEditPersona = { persona ->
                    editingPersona = persona
                    showPersonaEditor = true
                },
                onDuplicatePersona = { persona ->
                    viewModel.duplicatePersona(persona)
                },
                onDeletePersona = { personaId ->
                    viewModel.deletePersona(personaId)
                },
                onOpenHistorySession = { sessionId ->
                    viewModel.navigateTo(CurrentScreen.HistoryDetail(sessionId))
                }
            )
        }

        is CurrentScreen.Conversation -> {
            val persona = personas.find { it.id == screen.personaId }
                ?: personas.firstOrNull()
                ?: PersonaEntity(
                    id = "default",
                    name = "Copilote",
                    description = "",
                    model = "",
                    voice = "Puck",
                    reactivity = "balanced",
                    creativity = "balanced",
                    greeting = "persona",
                    personaInfo = "",
                    prompt = "Tu es un assistant vocal d'exception."
                )

            ConversationScreen(
                persona = persona,
                uiState = liveUiState,
                messages = sessionMessages,
                layoutMode = viewModel.securityVault.getConversationLayout(),
                onSendSpeechText = { text -> viewModel.sendUserSpeech(text) },
                onToggleMute = { viewModel.toggleMute() },
                onStopSession = { viewModel.stopSession() },
                onAttachVisionImage = { bmp -> viewModel.attachCameraVisionImage(bmp) },
                onNavigateBack = { viewModel.navigateHome() },
                onExportTranscriptTxt = { content ->
                    onShareText(content, "Transcription-${persona.name}.txt")
                },
                onExportTranscriptMd = { content ->
                    onShareText(content, "Transcription-${persona.name}.md")
                },
                onShareTranscript = { content ->
                    onShareText(content, "Transcription FULLLIVE")
                }
            )
        }

        is CurrentScreen.Transcripteur -> {
            TranscripteurScreen(
                uiState = liveUiState,
                messages = sessionMessages,
                onAddTranscriptBlock = { text -> viewModel.addTranscriptBlock(text) },
                onTogglePause = { viewModel.toggleTranscripteurPause() },
                onStopSession = { viewModel.stopSession() },
                onNavigateBack = { viewModel.navigateHome() },
                onExportTranscriptTxt = { content ->
                    onShareText(content, "Transcription-vocale.txt")
                },
                onExportTranscriptMd = { content ->
                    onShareText(content, "Transcription-vocale.md")
                },
                onShareTranscript = { content ->
                    onShareText(content, "Transcription FULLLIVE")
                }
            )
        }

        is CurrentScreen.Traducteur -> {
            TraducteurScreen(
                uiState = liveUiState,
                messages = sessionMessages,
                targetLanguage = targetLanguage,
                onChangeTargetLanguage = { newLang ->
                    targetLanguage = newLang
                    viewModel.securityVault.setTargetTranslateLanguage(newLang.code)
                },
                onSendSpeechText = { text ->
                    viewModel.executeTranslationTurn(text, targetLanguage)
                },
                onStopSession = { viewModel.stopSession() },
                onNavigateBack = { viewModel.navigateHome() }
            )
        }

        is CurrentScreen.HistoryDetail -> {
            val session = sessions.find { it.id == screen.sessionId }
            val persona = personas.find { it.id == session?.personaId }

            HistoryDetailScreen(
                session = session,
                messages = sessionMessages,
                persona = persona,
                onStartNewSessionWithPersona = { p ->
                    viewModel.startConversation(p)
                },
                onDeleteSession = { sId ->
                    viewModel.deleteSession(sId)
                },
                onNavigateBack = { viewModel.navigateHome() },
                onExportTranscriptTxt = { content ->
                    onShareText(content, "Archive-Session.txt")
                },
                onExportTranscriptMd = { content ->
                    onShareText(content, "Archive-Session.md")
                },
                onShareTranscript = { content ->
                    onShareText(content, "Archive FULLLIVE")
                }
            )
        }
    }

    // Persona Editor Dialog
    if (showPersonaEditor) {
        PersonaEditorDialog(
            persona = editingPersona,
            onDismiss = { showPersonaEditor = false },
            onSave = { updated ->
                viewModel.savePersona(updated)
            },
            onExportJson = { personaToExport ->
                // Export JSON
                val json = """{"type":"kast-persona","name":"${personaToExport.name}","prompt":"${personaToExport.prompt}"}"""
                onShareText(json, "persona-${personaToExport.name}.json")
            }
        )
    }

    // Global App Configuration Dialog
    if (showConfigDialog) {
        ConfigDialog(
            securityVault = viewModel.securityVault,
            statsList = stats,
            onDismiss = { showConfigDialog = false },
            onSaveConfig = { geminiKey, openAiKey, xaiKey, customOpenAiKey, customOpenAiBaseUrl, customOpenAiModelId, userNotes, budget, defaultModel, theme, layout ->
                viewModel.securityVault.setGeminiKey(geminiKey)
                viewModel.securityVault.setOpenAiKey(openAiKey)
                viewModel.securityVault.setXaiKey(xaiKey)
                viewModel.securityVault.setCustomOpenAiKey(customOpenAiKey)
                viewModel.securityVault.setCustomOpenAiBaseUrl(customOpenAiBaseUrl)
                viewModel.securityVault.setCustomOpenAiModelId(customOpenAiModelId)
                viewModel.securityVault.setUserIdentityNotes(userNotes)
                viewModel.securityVault.setBudgetLimit(budget)
                viewModel.securityVault.setDefaultModel(defaultModel)
                viewModel.securityVault.setTheme(theme)
                viewModel.securityVault.setConversationLayout(layout)
            },
            onExportBackupJson = {
                val backup = """{"app":"FULLLIVE","version":2,"personasCount":${personas.size}}"""
                onShareText(backup, "FULLLIVE-Backup.json")
            },
            onImportBackupJson = {
                // Notice to user
            }
        )
    }

    // Insights Dialog (at end of session)
    if (detectedInsights.isNotEmpty()) {
        InsightsDialog(
            insights = detectedInsights,
            personaName = "Ce persona",
            onDismiss = { viewModel.dismissInsights() },
            onSaveInsights = { chosen ->
                viewModel.saveSelectedInsights(chosen)
            }
        )
    }
}
