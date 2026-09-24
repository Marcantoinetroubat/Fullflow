package com.newoether.agora.api.gemini.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.random.Random

class GeminiLiveClient(
    private val clientScope: CoroutineScope,
    customClient: OkHttpClient? = null,
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        // Stable Live model for real-time audio conversation (skill: gemini-models-mastery)
        const val LIVE_MODEL = "models/gemini-3.1-flash-live-preview"
        private const val WS_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val REST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-live-preview:generateContent"

        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val okHttpClient = customClient ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private var audioPlaybackJob: Job? = null
    private val audioPlaybackChannel = Channel<ByteArray>(capacity = 128)

    private val _sessionState = MutableStateFlow<GeminiLiveState>(GeminiLiveState.Disconnected)
    val sessionState: StateFlow<GeminiLiveState> = _sessionState.asStateFlow()

    private val _userVolumeLevel = MutableStateFlow(0f)
    val userVolumeLevel: StateFlow<Float> = _userVolumeLevel.asStateFlow()

    private val _modelVolumeLevel = MutableStateFlow(0f)
    val modelVolumeLevel: StateFlow<Float> = _modelVolumeLevel.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /** Client-side speech activity (mic RMS + hangover) — instant "Vous parlez…" UI. */
    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

    /** Last turn latency: last mic chunk sent → first model audio chunk (ms). */
    private val _lastResponseLatencyMs = MutableStateFlow<Long?>(null)
    val lastResponseLatencyMs: StateFlow<Long?> = _lastResponseLatencyMs.asStateFlow()

    private val _transcripts = MutableStateFlow<List<GeminiLiveTranscript>>(emptyList())
    val transcripts: StateFlow<List<GeminiLiveTranscript>> = _transcripts.asStateFlow()

    private var currentModelTurnText = StringBuilder()
    private var activeVoice: GeminiLiveVoice = GeminiLiveVoice.DEFAULT
    private var activeApiKey: String = ""
    private var activePersona: GeminiLivePersona = GeminiLivePersona.DEFAULT_PRESETS.first()

    // Reconnect policy: exponential backoff with jitter, capped attempts, then degraded text mode.
    // Rules live in LiveConnectionPolicy (pure + unit-tested).
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var userInitiatedClose = false
    private var serverTranscriptDelivered = false

    // Per-turn latency + client VAD bookkeeping.
    private val speechDetector = SpeechActivityDetector()
    private var lastUplinkMs = 0L
    private var turnAudioStarted = false

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun startSession(
        apiKey: String,
        voice: GeminiLiveVoice = GeminiLiveVoice.DEFAULT,
        persona: GeminiLivePersona = GeminiLivePersona.DEFAULT_PRESETS.first()
    ) {
        if (apiKey.isBlank()) {
            _sessionState.value = GeminiLiveState.Error("Aucune clé API fournie.")
            return
        }

        stopSession()

        activeApiKey = apiKey
        activeVoice = voice
        activePersona = persona
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        userInitiatedClose = false
        serverTranscriptDelivered = false
        turnAudioStarted = false
        lastUplinkMs = 0L
        speechDetector.reset()
        _lastResponseLatencyMs.value = null
        _isUserSpeaking.value = false
        _sessionState.value = GeminiLiveState.Connecting
        currentModelTurnText.clear()

        initAudioTrack()

        val requestBuilder = Request.Builder()
        if (apiKey.startsWith("ya29.") || apiKey.startsWith("eph-") || (apiKey.contains(".") && !apiKey.startsWith("AIza"))) {
            // Ephemeral token or OAuth bearer token
            requestBuilder.url(WS_BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
        } else {
            // Standard API key via header only: never in the URL query string.
            requestBuilder.url(WS_BASE_URL)
                .addHeader("x-goog-api-key", apiKey)
        }
        val request = requestBuilder.build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                DebugLog.d(TAG, "Gemini Live WebSocket connected")
                _sessionState.value = GeminiLiveState.Connected
                sendSetupMessage(webSocket, voice, persona)
                startAudioRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                // Direct binary PCM audio payload from Gemini Live
                audioPlaybackChannel.trySend(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                DebugLog.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                DebugLog.d(TAG, "WebSocket closed: $code / $reason")
                if (userInitiatedClose) {
                    if (_sessionState.value !is GeminiLiveState.Error) {
                        _sessionState.value = GeminiLiveState.Disconnected
                    }
                    return
                }
                when {
                    LiveConnectionPolicy.isCleanCloseCode(code) -> {
                        _sessionState.value = GeminiLiveState.Disconnected
                    }
                    LiveConnectionPolicy.isFatalCloseCode(code) -> {
                        stopAudioRecording()
                        _sessionState.value = GeminiLiveState.Error(
                            "Session rejetée par le serveur ($code). " +
                                "Vérifiez la clé Google dans Réglages → Fournisseurs."
                        )
                    }
                    else -> handleConnectionDrop("Connexion interrompue ($code)")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                DebugLog.e(TAG, "WebSocket error", t)
                // Stop audio recording on failure
                stopAudioRecording()
                if (userInitiatedClose) {
                    _sessionState.value = GeminiLiveState.Disconnected
                    return
                }
                val httpCode = response?.code
                if (httpCode != null && LiveConnectionPolicy.isFatalHttpCode(httpCode)) {
                    _sessionState.value = GeminiLiveState.Error(
                        LiveConnectionPolicy.friendlyErrorMessage(t, httpCode)
                    )
                    return
                }
                handleConnectionDrop(LiveConnectionPolicy.friendlyErrorMessage(t, httpCode))
            }
        })
    }

    /** Manual retry from the UI after an exhausted or failed session. */
    fun reconnectNow() {
        if (activeApiKey.isBlank()) {
            _sessionState.value = GeminiLiveState.Error("Aucune clé API fournie.")
            return
        }
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        userInitiatedClose = false
        startSession(activeApiKey, activeVoice, activePersona)
    }

    private fun handleConnectionDrop(cause: String) {
        if (reconnectAttempts >= LiveConnectionPolicy.MAX_RECONNECT_ATTEMPTS) {
            // Exhausted: surface degraded mode. Text messages still flow via REST fallback.
            _sessionState.value = GeminiLiveState.Error(
                "$cause. Reconnexion automatique épuisée — le mode texte reste disponible."
            )
            stopAudioRecording()
            return
        }
        val attempt = reconnectAttempts++
        val delayMs = LiveConnectionPolicy.reconnectDelayMs(attempt, Random.nextDouble())
        DebugLog.d(TAG, "Scheduling reconnect attempt ${attempt + 1} in ${delayMs}ms")
        _sessionState.value = GeminiLiveState.Connecting
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(delayMs)
            if (!userInitiatedClose && activeApiKey.isNotBlank()) {
                // startSession resets the counter for fresh starts; preserve it on this path
                // so the exhaustion cap is actually reachable.
                val preservedAttempts = reconnectAttempts
                startSession(activeApiKey, activeVoice, activePersona)
                reconnectAttempts = preservedAttempts
            }
        }
    }

    private fun sendSetupMessage(
        ws: WebSocket,
        voice: GeminiLiveVoice,
        persona: GeminiLivePersona
    ) {
        val setupPayload = buildJsonObject {
            putJsonObject("setup") {
                put("model", LIVE_MODEL)
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        add("AUDIO")
                    }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", voice.id)
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", persona.systemPrompt)
                        }
                    }
                }
                // Server-side transcripts for both sides of the dialogue (skill: live-sessions).
                putJsonObject("inputAudioTranscription") { }
                putJsonObject("outputAudioTranscription") { }
            }
        }

        val jsonStr = setupPayload.toString()
        DebugLog.d(TAG, "Sending Live setup message")
        ws.send(jsonStr)
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = jsonParser.parseToJsonElement(text).jsonObject

            if (root.containsKey("setupComplete")) {
                DebugLog.d(TAG, "Gemini Live setup complete")
                // A full setup round-trip proves the network + key are healthy:
                // reset the reconnect budget for the rest of this session.
                reconnectAttempts = 0
                reconnectJob?.cancel()
                reconnectJob = null
                _sessionState.value = GeminiLiveState.Listening
                return
            }

            val serverContent = root["serverContent"]?.jsonObject ?: return

            val isInterrupted = serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isInterrupted) {
                DebugLog.d(TAG, "Model speech interrupted by user")
                interruptPlayback()
                turnAudioStarted = false
                _sessionState.value = GeminiLiveState.Listening
                return
            }

            val modelTurn = serverContent["modelTurn"]?.jsonObject
            if (modelTurn != null) {
                val parts = modelTurn["parts"]?.jsonArray
                parts?.forEach { partElement ->
                    val partObj = partElement.jsonObject
                    // Text piece
                    val textPart = partObj["text"]?.jsonPrimitive?.contentOrNull
                    if (!textPart.isNullOrBlank()) {
                        currentModelTurnText.append(textPart)
                    }

                    // Audio chunk
                    val inlineData = partObj["inlineData"]?.jsonObject
                    if (inlineData != null) {
                        val base64Audio = inlineData["data"]?.jsonPrimitive?.contentOrNull
                        if (!base64Audio.isNullOrBlank()) {
                            _sessionState.value = GeminiLiveState.Speaking
                            playAudioChunk(base64Audio)
                        }
                    }
                }
            }

            // Server-side user transcript: previously user speech produced no transcript at all.
            val inputTranscription = serverContent["inputTranscription"]?.jsonObject
            val inputText = inputTranscription?.get("text")?.jsonPrimitive?.contentOrNull
            if (!inputText.isNullOrBlank()) {
                appendTranscript("user", inputText.trim())
            }

            // Server-side model transcript: authoritative when present, avoids double-append.
            val outputTranscription = serverContent["outputTranscription"]?.jsonObject
            val outputText = outputTranscription?.get("text")?.jsonPrimitive?.contentOrNull
            val outputFinished = outputTranscription?.get("finished")?.jsonPrimitive?.booleanOrNull ?: false
            if (!outputText.isNullOrBlank() && outputFinished) {
                appendTranscript("model", outputText.trim())
                currentModelTurnText.clear()
                serverTranscriptDelivered = true
            }

            val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull ?: false
            if (turnComplete) {
                if (!serverTranscriptDelivered) {
                    val completedText = currentModelTurnText.toString().trim()
                    if (completedText.isNotEmpty()) {
                        appendTranscript("model", completedText)
                    }
                }
                currentModelTurnText.clear()
                serverTranscriptDelivered = false
                turnAudioStarted = false
                _sessionState.value = GeminiLiveState.Listening
                _modelVolumeLevel.value = 0f
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error handling incoming message", e)
        }
    }

    fun sendTextMessage(userText: String) {
        if (userText.isBlank()) return
        appendTranscript("user", userText)
        _sessionState.value = GeminiLiveState.Thinking

        val ws = webSocket
        if (ws != null && _sessionState.value !is GeminiLiveState.Disconnected && _sessionState.value !is GeminiLiveState.Error) {
            val clientContent = buildJsonObject {
                putJsonObject("clientContent") {
                    putJsonArray("turns") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                addJsonObject {
                                    put("text", userText)
                                }
                            }
                        }
                    }
                    put("turnComplete", true)
                }
            }
            ws.send(clientContent.toString())
        } else {
            // REST Fallback
            sendRestMessage(userText)
        }
    }

    private fun sendRestMessage(userText: String) {
        clientScope.launch(Dispatchers.IO) {
            try {
                _sessionState.value = GeminiLiveState.Thinking
                val requestBody = buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                addJsonObject {
                                    put("text", userText)
                                }
                            }
                        }
                    }
                    putJsonObject("systemInstruction") {
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", activePersona.systemPrompt)
                            }
                        }
                    }
                    putJsonObject("generationConfig") {
                        putJsonArray("responseModalities") {
                            add("AUDIO")
                            add("TEXT")
                        }
                        putJsonObject("speechConfig") {
                            putJsonObject("voiceConfig") {
                                putJsonObject("prebuiltVoiceConfig") {
                                    put("voiceName", activeVoice.id)
                                }
                            }
                        }
                    }
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val postBody = requestBody.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(REST_BASE_URL)
                    .addHeader("x-goog-api-key", activeApiKey)
                    .post(postBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: response.message
                    _sessionState.value = GeminiLiveState.Error("Erreur REST: $errBody")
                    return@launch
                }

                val respStr = response.body?.string() ?: ""
                val root = jsonParser.parseToJsonElement(respStr).jsonObject
                val candidates = root["candidates"]?.jsonArray
                val firstCand = candidates?.firstOrNull()?.jsonObject
                val parts = firstCand?.get("content")?.jsonObject?.get("parts")?.jsonArray

                var responseText = ""
                parts?.forEach { partElement ->
                    val partObj = partElement.jsonObject
                    val txt = partObj["text"]?.jsonPrimitive?.contentOrNull
                    if (!txt.isNullOrBlank()) responseText += txt

                    val inlineData = partObj["inlineData"]?.jsonObject
                    val b64Audio = inlineData?.get("data")?.jsonPrimitive?.contentOrNull
                    if (!b64Audio.isNullOrBlank()) {
                        _sessionState.value = GeminiLiveState.Speaking
                        playAudioChunk(b64Audio)
                    }
                }

                if (responseText.isNotBlank()) {
                    appendTranscript("model", responseText)
                }
                _sessionState.value = GeminiLiveState.Listening
            } catch (e: Exception) {
                DebugLog.e(TAG, "Rest fallback error", e)
                _sessionState.value = GeminiLiveState.Error(e.message ?: "Erreur réseau")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            _sessionState.value = GeminiLiveState.Error(
                "Microphone incompatible avec 16 kHz mono sur cet appareil."
            )
            return
        }
        val bufferSize = maxOf(minBufferSize, 3200)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            val record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord = null
                _sessionState.value = GeminiLiveState.Error(
                    "Impossible d'initialiser le microphone (autorisation refusée ?)."
                )
                return
            }

            attachVoiceEffects(record)
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                _sessionState.value = GeminiLiveState.Error(
                    "Le microphone ne démarre pas. Vérifiez qu'aucune autre app ne l'utilise."
                )
                return
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to start AudioRecord", e)
            _sessionState.value = GeminiLiveState.Error("Impossible d'initialiser le microphone: ${e.message}")
            return
        }

        recordingJob = clientScope.launch(Dispatchers.IO) {
            val audioBuffer = ByteArray(bufferSize / 2)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (readBytes > 0) {
                    val volume = calculateRmsVolume(audioBuffer, readBytes)
                    _userVolumeLevel.value = volume
                    _isUserSpeaking.value = speechDetector.update(volume, System.currentTimeMillis())

                    if (!_isMuted.value && webSocket != null && (_sessionState.value is GeminiLiveState.Listening || _sessionState.value is GeminiLiveState.Connected)) {
                        val base64Data = Base64.encodeToString(audioBuffer, 0, readBytes, Base64.NO_WRAP)
                        lastUplinkMs = System.currentTimeMillis()
                        val realtimeChunk = buildJsonObject {
                            putJsonObject("realtimeInput") {
                                putJsonArray("mediaChunks") {
                                    addJsonObject {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", base64Data)
                                    }
                                }
                            }
                        }
                        webSocket?.send(realtimeChunk.toString())
                    }
                }
            }
        }
    }

    /**
     * Hardware echo cancellation + noise suppression on the mic session when
     * the device exposes them. Best-effort: never fails session startup.
     */
    private fun attachVoiceEffects(record: AudioRecord) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler?.release()
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.also {
                    it.enabled = true
                    DebugLog.d(TAG, "AcousticEchoCanceler enabled")
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "AEC attach failed (non-fatal)", e)
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor?.release()
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.also {
                    it.enabled = true
                    DebugLog.d(TAG, "NoiseSuppressor enabled")
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "NS attach failed (non-fatal)", e)
        }
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize, 4800)

            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
            audioPlaybackJob?.cancel()
            audioPlaybackJob = clientScope.launch(Dispatchers.IO) {
                for (pcmData in audioPlaybackChannel) {
                    try {
                        val vol = calculateRmsVolume(pcmData, pcmData.size)
                        _modelVolumeLevel.value = vol
                        val track = audioTrack ?: continue
                        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            track.play()
                        }
                        track.write(pcmData, 0, pcmData.size)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        DebugLog.e(TAG, "AudioTrack write error", e)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to init AudioTrack", e)
        }
    }

    private fun playAudioChunk(base64Audio: String) {
        try {
            val pcmData = Base64.decode(base64Audio, Base64.NO_WRAP)
            if (!turnAudioStarted) {
                turnAudioStarted = true
                if (lastUplinkMs > 0L) {
                    _lastResponseLatencyMs.value = System.currentTimeMillis() - lastUplinkMs
                }
            }
            audioPlaybackChannel.trySend(pcmData)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Failed to play audio chunk", e)
        }
    }

    fun interruptPlayback() {
        try {
            while (audioPlaybackChannel.tryReceive().isSuccess) {}
            audioTrack?.let { track ->
                track.pause()
                track.flush()
                track.play()
            }
            _modelVolumeLevel.value = 0f
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error interrupting playback", e)
        }
    }

    private fun calculateRmsVolume(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val numSamples = length / 2
        if (numSamples <= 0) return 0f

        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        val rms = sqrt(sum / numSamples)
        // Normalize 0..32767 to 0..1
        val normalized = (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
        return normalized
    }

    private fun appendTranscript(role: String, text: String) {
        val current = _transcripts.value.toMutableList()
        current.add(GeminiLiveTranscript(role = role, text = text))
        _transcripts.value = current
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    private fun stopAudioRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            echoCanceler?.release()
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error releasing AEC", e)
        } finally {
            echoCanceler = null
        }
        try {
            noiseSuppressor?.release()
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error releasing NS", e)
        } finally {
            noiseSuppressor = null
        }
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            _userVolumeLevel.value = 0f
            _isUserSpeaking.value = false
            speechDetector.reset()
        }
    }

    private fun stopAudioPlayback() {
        while (audioPlaybackChannel.tryReceive().isSuccess) {}
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
            _modelVolumeLevel.value = 0f
        }
    }

    fun stopSession() {
        userInitiatedClose = true
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        try {
            webSocket?.close(1000, "Session ended by user")
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error closing websocket", e)
        } finally {
            webSocket = null
        }
        stopAudioRecording()
        stopAudioPlayback()
        _sessionState.value = GeminiLiveState.Disconnected
    }
}
