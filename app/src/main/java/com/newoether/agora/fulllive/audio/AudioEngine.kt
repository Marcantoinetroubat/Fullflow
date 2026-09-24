package com.newoether.agora.fulllive.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Moteur Audio Full-Duplex pour FULLLIVE :
 * - Capture microphone PCM 16-bit haute fidélité (16kHz / 24kHz)
 * - Calcul en temps réel de l'amplitude (RMS) pour l'Onde
 * - Rendu AudioTrack PCM 16-bit et extraction fréquentielle pour l'Orbe
 * - Interruption instantanée lors de la prise de parole utilisateur
 * - Synthèse vocale TTS Android avec gestionnaire de voix
 */
class AudioEngine(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var isRecording = false

    private var audioTrack: AudioTrack? = null
    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private var playbackJob: Job? = null
    private var isPlaying = false

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    var onUserAudioLevel: ((Float) -> Unit)? = null
    var onAiAudioLevel: ((Float, Float, Float, Float) -> Unit)? = null // volume, bass, mid, high
    var onPcmChunkRecorded: ((ByteArray) -> Unit)? = null
    var onUserStartedSpeaking: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                isTtsReady = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(sampleRate: Int = 16000, scope: CoroutineScope) {
        if (isRecording) return
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = max(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            2048
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            audioRecord?.startRecording()
            isRecording = true

            recordJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1024)
                var speechFrames = 0
                val silenceThreshold = 500

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Calculate RMS amplitude
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += (buffer[i] * buffer[i]).toDouble()
                        }
                        val rms = sqrt(sum / read)
                        val normalizedLevel = min(1.0f, (rms / 32767.0f * 6.5f).toFloat())

                        onUserAudioLevel?.invoke(normalizedLevel)

                        // VAD speech trigger for interruption
                        if (normalizedLevel > 0.08f) {
                            speechFrames++
                            if (speechFrames >= 3) {
                                onUserStartedSpeaking?.invoke()
                            }
                        } else {
                            speechFrames = 0
                        }

                        // Convert to ByteArray
                        val byteBuffer = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val v = buffer[i].toInt()
                            byteBuffer[i * 2] = (v and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                        }
                        onPcmChunkRecorded?.invoke(byteBuffer)
                    }
                }
            }
        } catch (e: Exception) {
            isRecording = false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
        onUserAudioLevel?.invoke(0f)
    }

    fun initAudioTrack(sampleRate: Int = 24000) {
        if (audioTrack != null) return
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    fun playPcmChunk(pcmData: ByteArray, scope: CoroutineScope) {
        if (audioTrack == null) {
            initAudioTrack()
        }
        playbackQueue.add(pcmData)

        if (!isPlaying) {
            isPlaying = true
            playbackJob = scope.launch(Dispatchers.IO) {
                while (isActive && isPlaying) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null && chunk.isNotEmpty()) {
                        audioTrack?.write(chunk, 0, chunk.size)

                        // Calculate energy spectrum for the Orb
                        val shortsCount = chunk.size / 2
                        var sum = 0.0
                        var bassSum = 0.0
                        var midSum = 0.0
                        var highSum = 0.0

                        for (i in 0 until shortsCount) {
                            val sample = ((chunk[i * 2 + 1].toInt() shl 8) or (chunk[i * 2].toInt() and 0xFF)).toShort()
                            val absVal = kotlin.math.abs(sample.toInt())
                            sum += absVal
                            if (i % 3 == 0) bassSum += absVal
                            else if (i % 3 == 1) midSum += absVal
                            else highSum += absVal
                        }

                        val volume = min(1.0f, (sum / shortsCount / 12000f).toFloat())
                        val bass = min(1.0f, (bassSum / (shortsCount / 3 + 1) / 14000f).toFloat())
                        val mid = min(1.0f, (midSum / (shortsCount / 3 + 1) / 12000f).toFloat())
                        val high = min(1.0f, (highSum / (shortsCount / 3 + 1) / 10000f).toFloat())

                        onAiAudioLevel?.invoke(volume, bass, mid, high)
                    } else {
                        onAiAudioLevel?.invoke(0f, 0f, 0f, 0f)
                        kotlinx.coroutines.delay(15)
                    }
                }
            }
        }
    }

    fun speakText(text: String, voiceName: String? = null, onDone: (() -> Unit)? = null) {
        if (!isTtsReady || text.isBlank()) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onAiAudioLevel?.invoke(0.6f, 0.5f, 0.4f, 0.3f)
            }
            override fun onDone(utteranceId: String?) {
                onAiAudioLevel?.invoke(0f, 0f, 0f, 0f)
                onDone?.invoke()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onAiAudioLevel?.invoke(0f, 0f, 0f, 0f)
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
    }

    /**
     * Interruption immédiate (Full-duplex barge-in) :
     * Coupe immédiatement le buffer et la lecture de l'IA dès que l'utilisateur prend la parole.
     */
    fun stopPlayback() {
        playbackQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {}
        try {
            tts?.stop()
        } catch (e: Exception) {}
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        onAiAudioLevel?.invoke(0f, 0f, 0f, 0f)
    }

    fun release() {
        stopRecording()
        stopPlayback()
        try {
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        try {
            tts?.shutdown()
        } catch (e: Exception) {}
        tts = null
    }
}
