package com.newoether.agora.ui.chat.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.util.Locale

/**
 * State holder and manager for live real-time speech-to-text transcription.
 */
class FullFlowSpeechRecognizerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var isListening by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var partialText by mutableStateOf("")
        private set

    var fullTranscript by mutableStateOf("")
        private set

    var rmsLevel by mutableStateOf(0f) // Normalized 0f..1f
        private set

    var elapsedSeconds by mutableIntStateOf(0)
        private set

    var selectedLocaleTag by mutableStateOf("fr-FR")

    var isAvailable by mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context))
        private set

    private var speechRecognizer: SpeechRecognizer? = null
    private var timerJob: Job? = null
    private var shouldContinueListening = false

    val supportedLanguages = listOf(
        SpeechLanguage("fr-FR", "Français (France)"),
        SpeechLanguage("en-US", "English (US)"),
        SpeechLanguage("en-GB", "English (UK)"),
        SpeechLanguage("es-ES", "Español"),
        SpeechLanguage("de-DE", "Deutsch"),
        SpeechLanguage("it-IT", "Italiano"),
        SpeechLanguage("ja-JP", "日本語"),
        SpeechLanguage("zh-CN", "中文")
    )

    data class SpeechLanguage(val tag: String, val displayName: String)

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB typically ranges from -2 to 10 dB
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            rmsLevel = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            rmsLevel = 0f
        }

        override fun onError(error: Int) {
            rmsLevel = 0f
            if (shouldContinueListening && !isPaused) {
                // Restart listening after brief pause to support continuous transcription
                scope.launch {
                    delay(300)
                    if (shouldContinueListening && !isPaused) {
                        startInternalListening()
                    }
                }
            } else {
                isListening = false
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognized = matches[0].trim()
                if (recognized.isNotEmpty()) {
                    fullTranscript = if (fullTranscript.isBlank()) {
                        recognized
                    } else {
                        "$fullTranscript $recognized"
                    }
                }
            }
            partialText = ""
            rmsLevel = 0f

            if (shouldContinueListening && !isPaused) {
                scope.launch {
                    delay(200)
                    if (shouldContinueListening && !isPaused) {
                        startInternalListening()
                    }
                }
            } else {
                isListening = false
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                partialText = matches[0].trim()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!isAvailable) return
        shouldContinueListening = true
        isPaused = false
        startTimer()
        startInternalListening()
    }

    private fun startInternalListening() {
        try {
            ensureRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLocaleTag)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (_: Exception) {
            isListening = false
        }
    }

    fun pauseListening() {
        isPaused = true
        shouldContinueListening = false
        stopInternalRecognizer()
        timerJob?.cancel()
        rmsLevel = 0f
    }

    fun resumeListening() {
        isPaused = false
        shouldContinueListening = true
        startTimer()
        startInternalListening()
    }

    fun stopListening() {
        shouldContinueListening = false
        isPaused = false
        isListening = false
        stopInternalRecognizer()
        timerJob?.cancel()
        rmsLevel = 0f
    }

    fun clearTranscript() {
        fullTranscript = ""
        partialText = ""
        elapsedSeconds = 0
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        }
    }

    private fun stopInternalRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused && isListening) {
                    elapsedSeconds++
                }
            }
        }
    }

    fun destroy() {
        stopListening()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
