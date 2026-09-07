package com.newoether.agora.ui.chat.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global coordinator for opening the FullFlow Audio Studio
 * across different views (Home, Chat, Message actions, Plus Sheet).
 */
object FullFlowAudioController {
    var isStudioVisible by mutableStateOf(false)
    var activeTab by mutableStateOf(AudioStudioTab.LIVE_STREAM)
    var pendingTtsText by mutableStateOf("")

    fun openLiveTranscription() {
        activeTab = AudioStudioTab.LIVE_STREAM
        isStudioVisible = true
    }

    fun openTts(text: String = "") {
        pendingTtsText = text
        activeTab = AudioStudioTab.TEXT_TO_SPEECH
        isStudioVisible = true
    }

    fun openAudioFiles() {
        activeTab = AudioStudioTab.AUDIO_FILES
        isStudioVisible = true
    }

    fun close() {
        isStudioVisible = false
    }
}
