package com.newoether.agora.studio.tts.kokoro

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.newoether.agora.util.DebugLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 16-bit PCM produced by the on-device Kokoro model. */
data class KokoroPcm(val samples16: ByteArray, val sampleRate: Int)

/**
 * Thin wrapper around sherpa-onnx [OfflineTts] for Kokoro.
 * The native engine is created lazily on first synthesis and only when
 * [KokoroModelManager.isReady] — never on the main thread.
 */
class KokoroLocalTts(context: Context) {

    private val manager = KokoroModelManager.getInstance(context.applicationContext)
    private val lock = Any()

    @Volatile
    private var tts: OfflineTts? = null

    fun isAvailable(): Boolean = manager.isReady()

    suspend fun synthesize(text: String, sid: Int, speed: Float): KokoroPcm? =
        withContext(Dispatchers.Default) {
            try {
                val engine = getOrInit() ?: return@withContext null
                val audio = engine.generate(text = text, sid = sid, speed = speed)
                if (audio.samples.isEmpty()) return@withContext null
                KokoroPcm(floatToPcm16(audio.samples), audio.sampleRate)
            } catch (e: Exception) {
                DebugLog.e("KokoroTts", "Synthesis failed", e)
                null
            }
        }

    private fun getOrInit(): OfflineTts? = synchronized(lock) {
        tts?.let { return it }
        if (!manager.isReady()) return null
        try {
            val dir = manager.modelDir.absolutePath
            val dataDir = File(manager.modelDir, "espeak-ng-data").absolutePath
            val usLexicon = File(manager.modelDir, "lexicon-us-en.txt")
            val zhLexicon = File(manager.modelDir, "lexicon-zh.txt")
            val lexicon = when {
                usLexicon.exists() && zhLexicon.exists() ->
                    "${usLexicon.absolutePath},${zhLexicon.absolutePath}"
                usLexicon.exists() -> "lexicon-us-en.txt"
                else -> ""
            }
            val config = getOfflineTtsConfig(
                modelDir = dir,
                modelName = "model.onnx",
                acousticModelName = "",
                vocoder = "",
                voices = "voices.bin",
                lexicon = lexicon,
                dataDir = dataDir,
                dictDir = "",
                ruleFsts = "",
                ruleFars = "",
            )
            OfflineTts(assetManager = null, config = config).also { tts = it }
        } catch (e: Exception) {
            DebugLog.e("KokoroTts", "Engine init failed", e)
            null
        }
    }

    fun release() = synchronized(lock) {
        try {
            tts?.release()
        } catch (_: Exception) {
        }
        tts = null
    }

    companion object {
        /** Float [-1, 1] → little-endian PCM 16-bit mono. */
        fun floatToPcm16(samples: FloatArray): ByteArray {
            val out = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val clamped = samples[i].coerceIn(-1f, 1f)
                val v = (clamped * 32767f).toInt().coerceIn(-32768, 32767)
                out[i * 2] = (v and 0xFF).toByte()
                out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            return out
        }
    }
}
