package com.newoether.agora.ui.chat.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import java.util.Locale

/**
 * Text-to-Speech manager for FullFlow.
 * Supports multi-language speech synthesis, pitch/speed modulation, and utterance monitoring.
 */
class FullFlowTtsEngine(private val context: Context) {
    var isInitialized by mutableStateOf(false)
        private set

    var isSpeaking by mutableStateOf(false)
        private set

    var speechRate by mutableFloatStateOf(1.0f)

    var pitch by mutableFloatStateOf(1.0f)

    var selectedLocaleTag by mutableStateOf("fr")

    var availableLocales by mutableStateOf<List<TtsLanguage>>(emptyList())
        private set

    private var tts: TextToSpeech? = null
    private var currentUtteranceId = 0L

    data class TtsLanguage(val code: String, val displayName: String, val locale: Locale)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                setupLocales()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                    }
                })
            }
        }
    }

    private fun setupLocales() {
        val list = mutableListOf(
            TtsLanguage("fr", "Français", Locale.FRENCH),
            TtsLanguage("en", "English", Locale.ENGLISH),
            TtsLanguage("es", "Español", Locale("es")),
            TtsLanguage("de", "Deutsch", Locale.GERMAN),
            TtsLanguage("it", "Italiano", Locale.ITALIAN),
            TtsLanguage("ja", "日本語", Locale.JAPANESE)
        )
        availableLocales = list
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!isInitialized || text.isBlank()) return

        val engine = tts ?: return
        val chosenLocale = availableLocales.find { it.code == selectedLocaleTag }?.locale ?: Locale.getDefault()
        engine.language = chosenLocale
        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)

        val id = (++currentUtteranceId).toString()
        isSpeaking = true

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isSpeaking = false
    }
}
