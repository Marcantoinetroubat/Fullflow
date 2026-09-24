package com.newoether.agora.wand

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bridge letting any chat message action invoke the Magic Wand on an existing
 * text (post-response reformulation / prompt amplification). The chat layer
 * observes [pendingSourceText], shows the type picker, then compiles through
 * the regular [WandController] pipeline — preview and composer insertion stay
 * byte-identical to the composer flow (no silent substitution).
 */
object WandMessageTrigger {

    var pendingSourceText by mutableStateOf<String?>(null)
        private set

    fun request(sourceText: String) {
        val trimmed = sourceText.trim()
        if (trimmed.isNotEmpty()) {
            // Post-response invocations default to the reformulation structure.
            WandController.selectedType = RequestType.REFORMULATION
            pendingSourceText = trimmed
        }
    }

    fun clear() {
        pendingSourceText = null
    }
}
