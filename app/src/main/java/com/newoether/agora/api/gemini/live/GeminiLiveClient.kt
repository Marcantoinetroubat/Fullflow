package com.newoether.agora.api.gemini.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
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

class GeminiLiveClient(
    private val clientScope: CoroutineScope
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        // Model mandated by Gemini API guidelines for real-time audio conversation
        const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val WS_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val REST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-native-audio-preview-12-2025:generateContent"

        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var audioPlaybackJob: Job? = null

    private val _sessionState = MutableStateFlow<GeminiLiveState>(GeminiLiveState.Disconnected)
    val sessionState: StateFlow<GeminiLiveState> = _sessionState.asStateFlow()

    private val _userVolumeLevel = MutableStateFlow(0f)
    val userVolumeLevel: StateFlow<Float> = _userVolumeLevel.asStateFlow()

    private val _modelVolumeLevel = MutableStateFlow(0f)
    val modelVolumeLevel: StateFlow<Float> = _modelVolumeLevel.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _transcripts = MutableStateFlow<List<GeminiLiveTranscript>>(emptyList())
    val transcripts: StateFlow<List<GeminiLiveTranscript>> = _transcripts.asStateFlow()

    private var currentModelTurnText = StringBuilder()
    private var activeVoice: GeminiLiveVoice = GeminiLiveVoice.DEFAULT
    private var activeApiKey: String = ""
    private var activePersona: GeminiLivePersona = GeminiLivePersona.DEFAULT_PRESETS.first()

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
        _sessionState.value = GeminiLiveState.Connecting
        currentModelTurnText.clear()

        initAudioTrack()

        val wsUrl = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini Live WebSocket connected")
                _sessionState.value = GeminiLiveState.Connected
                sendSetupMessage(webSocket, voice, persona)
                startAudioRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                if (_sessionState.value !is GeminiLiveState.Error) {
                    _sessionState.value = GeminiLiveState.Disconnected
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                val errMsg = t.message ?: "Erreur de connexion inconnue"
                _sessionState.value = GeminiLiveState.Error(errMsg)
                // Stop audio recording on failure
                stopAudioRecording()
            }
        })
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
            }
        }

        val jsonStr = setupPayload.toString()
        Log.d(TAG, "Sending setup message: $jsonStr")
        ws.send(jsonStr)
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = jsonParser.parseToJsonElement(text).jsonObject

            if (root.containsKey("setupComplete")) {
                Log.d(TAG, "Gemini Live setup complete")
                _sessionState.value = GeminiLiveState.Listening
                return
            }

            val serverContent = root["serverContent"]?.jsonObject ?: return

            val isInterrupted = serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isInterrupted) {
                Log.d(TAG, "Model speech interrupted by user")
                interruptPlayback()
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

            val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull ?: false
            if (turnComplete) {
                val completedText = currentModelTurnText.toString().trim()
                if (completedText.isNotEmpty()) {
                    appendTranscript("model", completedText)
                    currentModelTurnText.clear()
                }
                _sessionState.value = GeminiLiveState.Listening
                _modelVolumeLevel.value = 0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message", e)
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
                    .url("$REST_BASE_URL?key=$activeApiKey")
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
                Log.e(TAG, "Rest fallback error", e)
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
        val bufferSize = maxOf(minBufferSize, 3200)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
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

                    if (!_isMuted.value && webSocket != null && (_sessionState.value is GeminiLiveState.Listening || _sessionState.value is GeminiLiveState.Connected)) {
                        val base64Data = Base64.encodeToString(audioBuffer, 0, readBytes, Base64.NO_WRAP)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init AudioTrack", e)
        }
    }

    private fun playAudioChunk(base64Audio: String) {
        clientScope.launch(Dispatchers.IO) {
            try {
                val pcmData = Base64.decode(base64Audio, Base64.NO_WRAP)
                val vol = calculateRmsVolume(pcmData, pcmData.size)
                _modelVolumeLevel.value = vol

                val track = audioTrack ?: return@launch
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                track.write(pcmData, 0, pcmData.size)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play audio chunk", e)
            }
        }
    }

    fun interruptPlayback() {
        try {
            audioTrack?.let { track ->
                track.pause()
                track.flush()
                track.play()
            }
            _modelVolumeLevel.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting playback", e)
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
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            _userVolumeLevel.value = 0f
        }
    }

    private fun stopAudioPlayback() {
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
            _modelVolumeLevel.value = 0f
        }
    }

    fun stopSession() {
        try {
            webSocket?.close(1000, "Session ended by user")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        } finally {
            webSocket = null
        }
        stopAudioRecording()
        stopAudioPlayback()
        _sessionState.value = GeminiLiveState.Disconnected
    }
}
