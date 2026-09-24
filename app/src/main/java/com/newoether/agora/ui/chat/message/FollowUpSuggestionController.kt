package com.newoether.agora.ui.chat.message

/**
 * Global coordinator connecting Perplexity-style follow-up suggestion chips
 * with the active chat composer input field.
 */
object FollowUpSuggestionController {
    var onSuggestionSelected: ((String) -> Unit)? = null

    /** Direct-send handler (1-tap); the chat layer falls back to the composer when busy. */
    var onSuggestionSend: ((String) -> Unit)? = null

    fun selectSuggestion(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            onSuggestionSelected?.invoke(trimmed)
        }
    }

    fun sendSuggestion(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            val handler = onSuggestionSend
            if (handler != null) {
                handler.invoke(trimmed)
            } else {
                onSuggestionSelected?.invoke(trimmed)
            }
        }
    }
}
