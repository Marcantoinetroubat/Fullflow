package com.newoether.agora.tool

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bridge letting a chat message action save content into the Second Brain.
 * The chat layer observes [pendingSave], saves the text as a note via the
 * NoteRepository, and clears the request.
 */
object BrainSaveController {

    var pendingSave by mutableStateOf<String?>(null)
        private set

    fun requestSave(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) pendingSave = trimmed
    }

    fun clear() {
        pendingSave = null
    }
}
