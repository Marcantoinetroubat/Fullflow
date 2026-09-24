package com.newoether.agora.ui.chat.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global coordinator for the FullFlow Audio features:
 * - Studio views (Live Stream, TTS, Audio Files)
 * - In-place chat voice playback (reading assistant messages directly in chat)
 */
object FullFlowAudioController {
    var isStudioVisible by mutableStateOf(false)
    var activeTab by mutableStateOf(AudioStudioTab.PODCASTER)
    var pendingTtsText by mutableStateOf("")
    var shouldAutoPlayTts by mutableStateOf(false)

    // In-place playback for chat messages
    private var inPlaceEngine: FullFlowTtsEngine? = null
    var currentlySpeakingMessageId by mutableStateOf<String?>(null)
    var isSpeakingInChat by mutableStateOf(false)

    // Document Text-to-Speech playback
    var currentlyPlayingDocumentId by mutableStateOf<String?>(null)
    var isSpeakingDocument by mutableStateOf(false)

    fun playInChat(context: Context, messageId: String, text: String) {
        if (currentlySpeakingMessageId == messageId && isSpeakingInChat) {
            stopInChat()
            return
        }
        stopInChat()
        stopDocument()
        val engine = inPlaceEngine ?: FullFlowTtsEngine(context.applicationContext).also { inPlaceEngine = it }
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            val container = app?.requireContainer()
            val settings = container?.settingsRepository
            if (settings != null) {
                engine.syncWithSettingsRepository(settings)
            }
        } catch (_: Exception) {}
        currentlySpeakingMessageId = messageId
        isSpeakingInChat = true
        engine.speak(text) {
            if (currentlySpeakingMessageId == messageId) {
                currentlySpeakingMessageId = null
                isSpeakingInChat = false
            }
        }
    }

    fun stopInChat() {
        inPlaceEngine?.stop()
        currentlySpeakingMessageId = null
        isSpeakingInChat = false
    }

    fun playDocument(context: Context, docId: String, text: String, onFinished: (() -> Unit)? = null) {
        if (currentlyPlayingDocumentId == docId && isSpeakingDocument) {
            stopDocument()
            return
        }
        stopDocument()
        stopInChat()
        val engine = inPlaceEngine ?: FullFlowTtsEngine(context.applicationContext).also { inPlaceEngine = it }
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            val container = app?.requireContainer()
            val settings = container?.settingsRepository
            if (settings != null) {
                engine.syncWithSettingsRepository(settings)
            }
        } catch (_: Exception) {}
        currentlyPlayingDocumentId = docId
        isSpeakingDocument = true
        engine.speak(text) {
            if (currentlyPlayingDocumentId == docId) {
                currentlyPlayingDocumentId = null
                isSpeakingDocument = false
            }
            onFinished?.invoke()
        }
    }

    fun stopDocument() {
        inPlaceEngine?.stop()
        currentlyPlayingDocumentId = null
        isSpeakingDocument = false
    }

    fun openLiveTranscription() {
        // Live transcription replaced by FullLive voice assistant — redirect there
        com.newoether.agora.ui.chat.live.FullLiveController.open()
    }

    fun openAudioFiles() {
        // Audio files tab removed — open the podcaster instead
        activeTab = AudioStudioTab.PODCASTER
        isStudioVisible = true
    }

    fun close() {
        isStudioVisible = false
    }
}

