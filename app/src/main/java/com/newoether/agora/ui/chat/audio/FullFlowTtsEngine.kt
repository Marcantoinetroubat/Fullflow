package com.newoether.agora.ui.chat.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.newoether.agora.util.DebugLog
import androidx.compose.runtime.*
import com.newoether.agora.api.genmedia.GenMediaHttp
import com.newoether.agora.api.genmedia.GenMediaKeys
import com.newoether.agora.studio.tts.kokoro.KokoroLocalTts
import com.newoether.agora.studio.tts.kokoro.KokoroModelManager
import com.newoether.agora.studio.tts.kokoro.KokoroVoices
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Text-to-Speech manager for FullFlow.
 * Three engines: high-fidelity Google Gemini Cloud TTS (gemini-3.1-flash-tts-preview),
 * on-device Kokoro TTS (sherpa-onnx, 100% offline once the model is downloaded),
 * and native Android system TextToSpeech fallback.
 */
enum class TtsEngineMode { GEMINI_CLOUD, OPENAI_CLOUD, KOKORO_LOCAL, SYSTEM }

class FullFlowTtsEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("fullflow_tts_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isInitialized by mutableStateOf(false)
        private set

    var isSpeaking by mutableStateOf(false)
        private set

    var isSynthesizingCloud by mutableStateOf(false)
        private set

    var isSynthesizingKokoro by mutableStateOf(false)
        private set

    /** True while any synthesis or playback is running. */
    val isBusy: Boolean
        get() = isSpeaking || isSynthesizingCloud || isSynthesizingKokoro

    var ttsEngineMode by mutableStateOf(readInitialMode())

    /** Legacy boolean kept for callers: true ⇔ [TtsEngineMode.GEMINI_CLOUD]. */
    var useGeminiCloudTts: Boolean
        get() = ttsEngineMode == TtsEngineMode.GEMINI_CLOUD
        set(value) {
            ttsEngineMode = if (value) TtsEngineMode.GEMINI_CLOUD else TtsEngineMode.SYSTEM
        }

    // On-device Kokoro (sherpa-onnx). Model is downloaded on demand, never bundled.
    val kokoroManager: KokoroModelManager = KokoroModelManager.getInstance(context)
    private val kokoroTts = KokoroLocalTts(context)

    var selectedKokoroVoiceId by mutableStateOf(
        prefs.getString("kokoro_voice", KokoroVoices.DEFAULT_VOICE_ID) ?: KokoroVoices.DEFAULT_VOICE_ID,
    )

    var playbackSpeed by mutableFloatStateOf(
        prefs.getFloat("playback_speed", prefs.getFloat("speech_rate", 1.0f))
    )

    var kokoroSpeed by mutableFloatStateOf(prefs.getFloat("kokoro_speed", 1.0f))

    var selectedGeminiVoice by mutableStateOf(prefs.getString("gemini_voice", "Kore") ?: "Kore")

    val openAiVoices get() = OPENAI_VOICES

    var selectedOpenAiVoice by mutableStateOf(
        prefs.getString("openai_voice", "alloy") ?: "alloy"
    )

    val activeVoice: String
        get() = when (ttsEngineMode) {
            TtsEngineMode.GEMINI_CLOUD -> selectedGeminiVoice
            TtsEngineMode.OPENAI_CLOUD -> selectedOpenAiVoice
            TtsEngineMode.KOKORO_LOCAL -> selectedKokoroVoiceId
            TtsEngineMode.SYSTEM -> selectedLocaleTag
        }

    var selectedOpenAiModel by mutableStateOf(
        prefs.getString("openai_model", "tts-1") ?: "tts-1"
    )

    /** Gemini Cloud TTS model id (user-selectable among synced models, e.g. flash-tts). */
    var selectedGeminiModel by mutableStateOf(
        prefs.getString("gemini_model", "gemini-3.1-flash-tts-preview") ?: "gemini-3.1-flash-tts-preview"
    )

    // Multi-speaker dialogue mode (max 2 speakers per request, skill: speech-generation).
    var ttsDialogueMode by mutableStateOf(prefs.getBoolean("dialogue_mode", false))
    var speaker1Name by mutableStateOf(prefs.getString("speaker1_name", "Speaker1") ?: "Speaker1")
    var speaker1Voice by mutableStateOf(prefs.getString("speaker1_voice", "Kore") ?: "Kore")
    var speaker2Name by mutableStateOf(prefs.getString("speaker2_name", "Speaker2") ?: "Speaker2")
    var speaker2Voice by mutableStateOf(prefs.getString("speaker2_voice", "Puck") ?: "Puck")

    var speechRate by mutableFloatStateOf(prefs.getFloat("speech_rate", 1.0f))

    fun updatePlaybackSpeed(newSpeed: Float) {
        playbackSpeed = newSpeed
        kokoroSpeed = newSpeed
        speechRate = newSpeed
        saveSettings()
    }

    var pitch by mutableFloatStateOf(prefs.getFloat("pitch", 1.0f))

    var selectedLocaleTag by mutableStateOf(prefs.getString("locale_tag", "fr") ?: "fr")

    var availableLocales by mutableStateOf<List<TtsLanguage>>(emptyList())
        private set

    private var tts: TextToSpeech? = null
    private var audioTrack: AudioTrack? = null
    private var activeMediaPlayer: android.media.MediaPlayer? = null
    private var currentUtteranceId = 0L
    private val activeCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private var cloudJob: Job? = null
    private var kokoroJob: Job? = null
    private var openAiJob: Job? = null

    fun syncWithSettingsRepository(settings: com.newoether.agora.data.repository.SettingsRepository) {
        val modeStr = settings.ttsEngineMode.value
        ttsEngineMode = runCatching { TtsEngineMode.valueOf(modeStr) }.getOrDefault(ttsEngineMode)
        val voice = settings.ttsVoice.value
        if (voice.isNotBlank()) {
            when (ttsEngineMode) {
                TtsEngineMode.GEMINI_CLOUD -> selectedGeminiVoice = voice
                TtsEngineMode.OPENAI_CLOUD -> selectedOpenAiVoice = voice
                TtsEngineMode.KOKORO_LOCAL -> selectedKokoroVoiceId = voice
                TtsEngineMode.SYSTEM -> {}
            }
        }
        val model = settings.ttsProviderModel.value
        if (!model.isNullOrBlank()) {
            selectedOpenAiModel = model
        }
        val speed = settings.ttsSpeed.value
        if (speed in 0.5f..2.0f) {
            updatePlaybackSpeed(speed)
        }
    }

    data class TtsLanguage(val code: String, val displayName: String, val locale: Locale)

    val geminiVoices get() = GEMINI_VOICES

    val commonAudioTags = listOf(
        "[excited]", "[whispers]", "[laughs]", "[serious]",
        "[sighs]", "[tired]", "[amazed]", "[mischievously]"
    )

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
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)
                        }
                    }
                })
            }
        }
    }

    fun saveSettings() {
        prefs.edit()
            .putString("tts_engine_mode", ttsEngineMode.name)
            .putBoolean("use_gemini_cloud", ttsEngineMode == TtsEngineMode.GEMINI_CLOUD)
            .putString("gemini_voice", selectedGeminiVoice)
            .putString("openai_voice", selectedOpenAiVoice)
            .putString("openai_model", selectedOpenAiModel)
            .putString("gemini_model", selectedGeminiModel)
            .putString("kokoro_voice", selectedKokoroVoiceId)
            .putFloat("kokoro_speed", kokoroSpeed)
            .putBoolean("dialogue_mode", ttsDialogueMode)
            .putString("speaker1_name", speaker1Name)
            .putString("speaker1_voice", speaker1Voice)
            .putString("speaker2_name", speaker2Name)
            .putString("speaker2_voice", speaker2Voice)
            .putFloat("speech_rate", speechRate)
            .putFloat("playback_speed", playbackSpeed)
            .putFloat("pitch", pitch)
            .putString("locale_tag", selectedLocaleTag)
            .apply()
    }

    private fun readInitialMode(): TtsEngineMode {
        val stored = prefs.getString("tts_engine_mode", null)
        if (stored != null) {
            return runCatching { TtsEngineMode.valueOf(stored) }.getOrDefault(TtsEngineMode.GEMINI_CLOUD)
        }
        // Migration from the legacy boolean.
        return if (prefs.getBoolean("use_gemini_cloud", true)) {
            TtsEngineMode.GEMINI_CLOUD
        } else {
            TtsEngineMode.SYSTEM
        }
    }

    /** speech_config array: single voice or 2 labeled speakers (labels must match the prompt). */
    private fun buildSpeechConfig(): JSONArray {
        if (!ttsDialogueMode) {
            return JSONArray().put(JSONObject().apply { put("voice", selectedGeminiVoice) })
        }
        val s1 = speaker1Name.trim().ifBlank { "Speaker1" }
        val s2 = speaker2Name.trim().ifBlank { "Speaker2" }
        return JSONArray()
            .put(JSONObject().apply {
                put("speaker", s1)
                put("voice", speaker1Voice)
            })
            .put(JSONObject().apply {
                put("speaker", s2)
                put("voice", speaker2Voice)
            })
    }

    /**
     * Long-form guard: quality drifts past a few minutes of audio, so transcripts are
     * split on sentence boundaries and synthesized chunk by chunk, then played back
     * sequentially. Mirrors the batch-caching spirit: never fire one giant payload.
     */
    private fun splitForTts(text: String, maxChars: Int = 1500): List<String> {
        val clean = text.trim()
        if (clean.length <= maxChars) return listOf(clean)
        val sentences = clean.split(Regex("(?<=[.!?…\\n])\\s+")).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > maxChars) {
                if (current.isNotBlank()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                sentence.chunked(maxChars).forEach { chunks.add(it.trim()) }
            } else if (current.length + sentence.length + 1 > maxChars) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks.ifEmpty { listOf(clean) }
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

    fun speak(text: String, apiKeyOverride: String? = null, onDone: () -> Unit = {}) {
        if (text.isBlank()) return
        saveSettings()

        // Auto-detect the best available TTS provider from the user's configured API keys.
        // Priority: OpenAI-compatible (includes ZenMux, OpenRouter, Groq) → Gemini → System fallback.
        val openAiKey = resolveOpenAiKey(apiKeyOverride)
        if (openAiKey.isNotBlank()) {
            speakWithOpenAiCloud(text, openAiKey, onDone)
            return
        }

        val geminiKey = GenMediaKeys.resolveApiKeyBlocking(context, apiKeyOverride)
        if (geminiKey.isNotBlank()) {
            speakWithGeminiCloud(text, geminiKey, onDone)
            return
        }

        // No configured cloud provider — use system TTS as fallback
        speakWithSystemTts(text, onDone)
    }

    fun resolveOpenAiKey(apiKeyOverride: String? = null): String {
        if (!apiKeyOverride.isNullOrBlank()) return apiKeyOverride.trim()
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            val container = app?.requireContainer()
            val settings = container?.settingsRepository
            if (settings != null) {
                val allKeys = settings.apiKeys.value.filter { it.key.isNotBlank() }
                if (allKeys.isEmpty()) return ""

                // Prefer the key from the same provider as the selected TTS model
                // (e.g. if user picked "openrouter:...", use the OpenRouter key)
                val selectedModel = selectedOpenAiModel.ifBlank { null }
                    ?: settings.ttsProviderModel.value
                if (!selectedModel.isNullOrBlank() && selectedModel.contains(":")) {
                    val modelProvider = selectedModel.substringBefore(":").trim()
                    val match = allKeys.find { it.provider.equals(modelProvider, true) }
                    if (match != null) return match.key.trim()
                }

                // Fallback: use the first available key from any provider
                return allKeys.first().key.trim()
            }
        } catch (_: Exception) {}
        return ""
    }

    fun resolveOpenAiBaseUrl(): String {
        try {
            val app = context.applicationContext as? com.newoether.agora.AgoraApplication
            val container = app?.requireContainer()
            val settings = container?.settingsRepository
            if (settings != null) {
                val allKeys = settings.apiKeys.value.filter { it.key.isNotBlank() }
                if (allKeys.isNotEmpty()) {
                    // Use the same provider as the selected TTS model (not just the first key)
                    val selectedModel = selectedOpenAiModel.ifBlank { null }
                        ?: settings.ttsProviderModel.value
                    val provider = if (!selectedModel.isNullOrBlank() && selectedModel.contains(":")) {
                        selectedModel.substringBefore(":").trim()
                    } else {
                        allKeys.first().provider
                    }
                    val customUrls = settings.providerBaseUrls.value
                    // Check for a custom base URL for this provider
                    val url = customUrls[provider]
                        ?: customUrls.entries.firstOrNull {
                            it.key.equals(provider, true)
                        }?.value
                    if (!url.isNullOrBlank()) {
                        val clean = url.trim().removeSuffix("/")
                        return if (clean.endsWith("/v1")) clean else "$clean/v1"
                    }
                    // Known default base URLs per provider
                    return when {
                        provider.equals("openrouter", true) -> "https://openrouter.ai/api/v1"
                        provider.equals("groq", true) -> "https://api.groq.com/openai/v1"
                        provider.equals("deepseek", true) -> "https://api.deepseek.com/v1"
                        else -> "https://api.openai.com/v1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "https://api.openai.com/v1"
    }

    private fun speakWithOpenAiCloud(text: String, apiKey: String, onDone: () -> Unit) {
        openAiJob?.cancel()
        openAiJob = scope.launch(Dispatchers.IO) {
            isSynthesizingCloud = true
            isSpeaking = true
            try {
                val chunks = splitForTts(text, maxChars = 2000)
                val baseUrl = resolveOpenAiBaseUrl()
                val model = selectedOpenAiModel.ifBlank { "tts-1" }
                val voice = selectedOpenAiVoice.ifBlank { "alloy" }
                val speed = playbackSpeed.coerceIn(0.5f, 2.0f)

                for (chunk in chunks) {
                    ensureActive()
                    val audioBytes = requestOpenAiAudio(chunk, apiKey, baseUrl, model, voice, speed)
                        ?: throw IllegalStateException("Réponse audio vide du moteur OpenAI/Multi-Provider.")
                    withContext(Dispatchers.Main) {
                        isSynthesizingCloud = false
                    }
                    if (isMp3(audioBytes)) {
                        playAudioBytesWithMediaPlayer(audioBytes, speed)
                    } else {
                        playPcmAudio(audioBytes, sampleRate = 24000, applyTrackSpeed = false)
                    }
                }
                withContext(Dispatchers.Main) {
                    isSpeaking = false
                    onDone()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLog.e("FullFlowTts", "OpenAI TTS error, falling back to system TTS", e)
                withContext(Dispatchers.Main) {
                    isSynthesizingCloud = false
                    speakWithSystemTts(text, onDone)
                }
            }
        }
    }

    private suspend fun requestOpenAiAudio(
        text: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        voice: String,
        speed: Float,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}audio/speech" else "$baseUrl/audio/speech"
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", "pcm")
            put("speed", speed)
        }.toString()

        val pcmBytes = GenMediaHttp.postJsonBytes(
            url = endpoint,
            apiKey = apiKey,
            bodyJson = bodyJson,
        )
        if (pcmBytes != null && pcmBytes.isNotEmpty()) return@withContext pcmBytes

        // Fallback without response_format in case provider requires mp3
        val fallbackJson = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("speed", speed)
        }.toString()

        GenMediaHttp.postJsonBytes(
            url = endpoint,
            apiKey = apiKey,
            bodyJson = fallbackJson,
        )
    }

    private fun isMp3(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) ||
               (bytes[0].toInt() and 0xFF == 0xFF && (bytes[1].toInt() and 0xE0 == 0xE0))
    }

    private fun playAudioBytesWithMediaPlayer(bytes: ByteArray, speed: Float) {
        var tempFile: java.io.File? = null
        var mp: android.media.MediaPlayer? = null
        try {
            tempFile = java.io.File.createTempFile("tts_preview_", ".mp3", context.cacheDir)
            tempFile.writeBytes(bytes)
            mp = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                if (speed != 1.0f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        val params = playbackParams
                        params.speed = speed
                        playbackParams = params
                    } catch (_: Exception) {}
                }
                start()
            }
            activeMediaPlayer = mp
            while (mp.isPlaying && isSpeaking) {
                Thread.sleep(100)
            }
        } catch (e: Exception) {
            DebugLog.e("FullFlowTts", "MediaPlayer playback error", e)
        } finally {
            try {
                mp?.stop()
                mp?.release()
            } catch (_: Exception) {}
            activeMediaPlayer = null
            tempFile?.delete()
        }
    }

    private fun speakWithGeminiCloud(text: String, apiKey: String, onDone: () -> Unit) {
        cloudJob?.cancel()
        cloudJob = scope.launch(Dispatchers.IO) {
            isSynthesizingCloud = true
            isSpeaking = true
            try {
                val speechConfig = withContext(Dispatchers.Main) { buildSpeechConfig() }
                val chunks = splitForTts(text)
                for (chunk in chunks) {
                    ensureActive()
                    val pcmBytes = requestCloudAudio(chunk, apiKey, speechConfig)
                        ?: throw IllegalStateException("Réponse audio vide du moteur Gemini.")
                    withContext(Dispatchers.Main) {
                        isSynthesizingCloud = false
                    }
                    playPcmAudio(pcmBytes, applyTrackSpeed = true)
                }
                withContext(Dispatchers.Main) {
                    isSpeaking = false
                    onDone()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLog.e("FullFlowTts", "Gemini Cloud TTS error, falling back to system TTS", e)
                // Fallback to system TTS on network or key issue
                withContext(Dispatchers.Main) {
                    isSynthesizingCloud = false
                    speakWithSystemTts(text, onDone)
                }
            }
        }
    }

    /**
     * Fully offline synthesis with the on-device Kokoro model. Falls back to the
     * Android system engine when the model is not downloaded yet or fails.
     */
    private fun speakWithKokoro(text: String, onDone: () -> Unit) {
        kokoroJob?.cancel()
        if (!kokoroManager.isReady()) {
            speakWithSystemTts(text, onDone)
            return
        }
        kokoroJob = scope.launch(Dispatchers.IO) {
            isSynthesizingKokoro = true
            isSpeaking = true
            try {
                val sid = KokoroVoices.sidFor(selectedKokoroVoiceId)
                val speed = playbackSpeed.coerceIn(0.5f, 2.0f)
                for (chunk in splitForTts(text)) {
                    ensureActive()
                    val pcm = kokoroTts.synthesize(chunk, sid, speed)
                        ?: throw IllegalStateException("Réponse audio vide du moteur Kokoro.")
                    withContext(Dispatchers.Main) {
                        isSynthesizingKokoro = false
                    }
                    playPcmAudio(pcm.samples16, pcm.sampleRate)
                }
                withContext(Dispatchers.Main) {
                    isSpeaking = false
                    onDone()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DebugLog.e("FullFlowTts", "Kokoro local TTS error, falling back to system TTS", e)
                withContext(Dispatchers.Main) {
                    isSynthesizingKokoro = false
                    speakWithSystemTts(text, onDone)
                }
            }
        }
    }

    /** One Interactions call per chunk; returns null PCM when the response carries no audio. */
    private suspend fun requestCloudAudio(
        text: String,
        apiKey: String,
        speechConfig: JSONArray,
        model: String = selectedGeminiModel,
    ): ByteArray? = withContext(Dispatchers.IO) {
        // Official Gemini TTS Interactions endpoint
        val url = "https://generativelanguage.googleapis.com/v1beta/interactions"
        val bodyJson = JSONObject().apply {
            put("model", model.ifBlank { "gemini-3.1-flash-tts-preview" })
            put("input", text)
            put("response_format", JSONObject().put("type", "audio"))
            put("generation_config", JSONObject().apply {
                put("speech_config", speechConfig)
            })
        }.toString()

        val response = GenMediaHttp.postJson(
            url = url,
            apiKey = apiKey,
            bodyJson = bodyJson,
            extraHeaders = mapOf("Api-Revision" to "2026-05-20"),
        )
        if (!response.successful) return@withContext null
        val audioData = JSONObject(response.body).optJSONObject("output_audio")?.optString("data")
        if (audioData.isNullOrBlank()) return@withContext null
        Base64.decode(audioData, Base64.DEFAULT)
    }

    private suspend fun playPcmAudio(
        pcmBytes: ByteArray,
        sampleRate: Int = 24000,
        applyTrackSpeed: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val safeRate = if (sampleRate > 0) sampleRate else 24000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                maxOf(minBuf, pcmBytes.size),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack = track

            if (applyTrackSpeed) {
                val speed = playbackSpeed.coerceIn(0.5f, 2.0f)
                if (speed != 1.0f) {
                    try {
                        val params = android.media.PlaybackParams()
                        params.speed = speed
                        track.playbackParams = params
                    } catch (e: Exception) {
                        DebugLog.e("FullFlowTts", "Error setting playback params on AudioTrack", e)
                    }
                }
            }

            track.play()

            var offset = 0
            val chunkSize = 4096
            while (offset < pcmBytes.size && isSpeaking) {
                val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                track.write(pcmBytes, offset, toWrite)
                offset += toWrite
            }
        } catch (e: Exception) {
            DebugLog.e("FullFlowTts", "AudioTrack playback error", e)
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
        }
    }

    private fun speakWithSystemTts(text: String, onDone: () -> Unit) {
        if (!isInitialized || text.isBlank()) return
        val engine = tts ?: return
        val chosenLocale = availableLocales.find { it.code == selectedLocaleTag }?.locale ?: Locale.getDefault()
        try {
            engine.language = chosenLocale
        } catch (_: Exception) {}
        engine.setSpeechRate(playbackSpeed)
        engine.setPitch(pitch)

        val id = (++currentUtteranceId).toString()
        activeCallbacks[id] = onDone
        isSpeaking = true

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        cloudJob?.cancel()
        cloudJob = null
        kokoroJob?.cancel()
        kokoroJob = null
        openAiJob?.cancel()
        openAiJob = null
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Exception) {}
        activeMediaPlayer = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        activeCallbacks.clear()
        tts?.stop()
        isSpeaking = false
        isSynthesizingCloud = false
        isSynthesizingKokoro = false
    }

    suspend fun synthesizeToFile(
        text: String,
        outputFile: java.io.File,
        engineMode: TtsEngineMode? = null,
        voice: String? = null,
        apiKeyOverride: String? = null,
        modelOverride: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext false
        val mode = engineMode ?: ttsEngineMode
        val chunks = splitForTts(text, maxChars = 1500)
        outputFile.parentFile?.mkdirs()

        when (mode) {
            TtsEngineMode.OPENAI_CLOUD -> {
                val apiKey = resolveOpenAiKey(apiKeyOverride)
                if (apiKey.isNotBlank()) {
                    try {
                        val baseUrl = resolveOpenAiBaseUrl()
                        val model = modelOverride?.takeIf { it.isNotBlank() } ?: selectedOpenAiModel.ifBlank { "tts-1" }
                        val selectedVoice = voice ?: selectedOpenAiVoice.ifBlank { "alloy" }
                        val speed = playbackSpeed.coerceIn(0.5f, 2.0f)
                        val pcmAccumulator = java.io.ByteArrayOutputStream()
                        var isMp3Format = false

                        for (chunk in chunks) {
                            val audioBytes = requestOpenAiAudio(chunk, apiKey, baseUrl, model, selectedVoice, speed)
                                ?: continue
                            if (isMp3(audioBytes)) {
                                isMp3Format = true
                                pcmAccumulator.write(audioBytes)
                            } else {
                                pcmAccumulator.write(audioBytes)
                            }
                        }
                        val accumulated = pcmAccumulator.toByteArray()
                        if (accumulated.isNotEmpty()) {
                            if (isMp3Format) {
                                outputFile.writeBytes(accumulated)
                            } else {
                                writePcmToWav(outputFile, accumulated, sampleRate = 24000)
                            }
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        DebugLog.e("FullFlowTts", "synthesizeToFile OpenAI error", e)
                    }
                }
            }
            TtsEngineMode.GEMINI_CLOUD -> {
                val effectiveKey = GenMediaKeys.resolveApiKeyBlocking(context, apiKeyOverride)
                if (effectiveKey.isNotBlank()) {
                    try {
                        val speechConfig = if (!voice.isNullOrBlank()) {
                            JSONArray().put(JSONObject().apply { put("voice", voice) })
                        } else {
                            buildSpeechConfig()
                        }
                        val pcmAccumulator = java.io.ByteArrayOutputStream()
                        val effectiveModel = modelOverride?.takeIf { it.isNotBlank() } ?: selectedGeminiModel
                        for (chunk in chunks) {
                            val pcmBytes = requestCloudAudio(chunk, effectiveKey, speechConfig, model = effectiveModel)
                                ?: continue
                            pcmAccumulator.write(pcmBytes)
                        }
                        val accumulated = pcmAccumulator.toByteArray()
                        if (accumulated.isNotEmpty()) {
                            writePcmToWav(outputFile, accumulated, sampleRate = 24000)
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        DebugLog.e("FullFlowTts", "synthesizeToFile Gemini error", e)
                    }
                }
            }
            TtsEngineMode.KOKORO_LOCAL -> {
                if (kokoroManager.isReady()) {
                    try {
                        val sid = KokoroVoices.sidFor(voice ?: selectedKokoroVoiceId)
                        val speed = kokoroSpeed.coerceIn(0.5f, 2.0f)
                        val pcmAccumulator = java.io.ByteArrayOutputStream()
                        var sampleRate = 24000
                        for (chunk in chunks) {
                            val pcm = kokoroTts.synthesize(chunk, sid, speed) ?: continue
                            sampleRate = pcm.sampleRate
                            val pcmBytes = pcm.samples16
                            pcmAccumulator.write(pcmBytes)
                        }
                        val accumulated = pcmAccumulator.toByteArray()
                        if (accumulated.isNotEmpty()) {
                            writePcmToWav(outputFile, accumulated, sampleRate = sampleRate)
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        DebugLog.e("FullFlowTts", "synthesizeToFile Kokoro error", e)
                    }
                }
            }
            TtsEngineMode.SYSTEM -> {}
        }

        // Fallback to Android System TTS synthesizeToFile
        synthesizeToFileWithSystemTts(text, outputFile)
    }

    private suspend fun synthesizeToFileWithSystemTts(text: String, outputFile: java.io.File): Boolean =
        suspendCancellableCoroutine { cont ->
            val engine = tts
            if (engine == null || !isInitialized) {
                cont.resume(false, null)
                return@suspendCancellableCoroutine
            }
            val id = (++currentUtteranceId).toString()
            activeCallbacks[id] = {
                cont.resume(outputFile.exists() && outputFile.length() > 0, null)
            }
            val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                engine.synthesizeToFile(text, null, outputFile, id)
            } else {
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = id
                @Suppress("DEPRECATION")
                engine.synthesizeToFile(text, params, outputFile.absolutePath)
            }
            if (result != TextToSpeech.SUCCESS) {
                activeCallbacks.remove(id)
                cont.resume(false, null)
            }
        }

    private fun shortArrayToByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        var bIdx = 0
        for (s in shorts) {
            bytes[bIdx++] = (s.toInt() and 0xFF).toByte()
            bytes[bIdx++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun writePcmToWav(
        outputFile: java.io.File,
        pcmData: ByteArray,
        sampleRate: Int = 24000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { out ->
            out.write(header)
            out.write(pcmData)
            out.flush()
        }
    }

    fun shutdown() {
        stop()
        kokoroTts.release()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        scope.cancel()
    }

    companion object {
        val GEMINI_VOICES = listOf(
            "Kore", "Puck", "Fenrir", "Aoede", "Zephyr",
            "Charon", "Leda", "Orus", "Achernar", "Sulafat"
        )
        val OPENAI_VOICES = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
    }
}
