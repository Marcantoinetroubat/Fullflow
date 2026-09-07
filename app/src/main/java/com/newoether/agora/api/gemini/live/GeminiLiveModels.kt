package com.newoether.agora.api.gemini.live

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
enum class GeminiLiveVoice(val id: String, val displayName: String, val tone: String) {
    AOEDE("Aoede", "Aoede", "Expressive & mélodieuse"),
    PUCK("Puck", "Puck", "Énergique & enjouée"),
    CHARON("Charon", "Charon", "Calme & posée"),
    KORE("Kore", "Kore", "Chaleureuse & douce"),
    FENRIR("Fenrir", "Fenrir", "Assurée & profonde");

    companion object {
        val DEFAULT = AOEDE
        fun fromId(id: String): GeminiLiveVoice =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

sealed interface GeminiLiveState {
    data object Disconnected : GeminiLiveState
    data object Connecting : GeminiLiveState
    data object Connected : GeminiLiveState
    data object Listening : GeminiLiveState
    data object Thinking : GeminiLiveState
    data object Speaking : GeminiLiveState
    data class Error(val message: String) : GeminiLiveState
}

@Immutable
data class GeminiLiveTranscript(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class GeminiLivePersona(
    val id: String,
    val title: String,
    val systemPrompt: String
) {
    companion object {
        val DEFAULT_PRESETS = listOf(
            GeminiLivePersona(
                id = "assistant",
                title = "Assistant conversationnel",
                systemPrompt = "Tu es un assistant vocal interactif intelligent, chaleureux, concis et réactif. Tu t'exprimes avec fluidité et naturel, en évitant les réponses trop longues pour favoriser un échange parlé vivant."
            ),
            GeminiLivePersona(
                id = "coach",
                title = "Coach & Sparring Partner",
                systemPrompt = "Tu es un coach personnel dynamique et stimulant. Tu aides l'utilisateur à clarifier ses idées, poses de bonnes questions et encourages l'action de façon constructive."
            ),
            GeminiLivePersona(
                id = "tutor",
                title = "Professeur de langues",
                systemPrompt = "Tu es un tuteur linguistique interactif et bienveillant. Engage la conversation naturellement, aide à enrichir le vocabulaire et corrige avec délicatesse si nécessaire."
            ),
            GeminiLivePersona(
                id = "creative",
                title = "Brainstorming & Créativité",
                systemPrompt = "Tu es un collaborateur créatif inspirant. Propose des angles inédits, rebondis sur les idées de l'utilisateur avec inventivité et enthousiasme."
            )
        )
    }
}
