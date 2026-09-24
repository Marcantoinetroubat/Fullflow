package com.newoether.agora.studio.tts.kokoro

import android.content.Context
import com.newoether.agora.util.DebugLog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.util.concurrent.TimeUnit

/** Download state of the on-device Kokoro model (~350 Mo, Wi-Fi recommended). */
enum class KokoroModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

/**
 * Owns the sherpa-onnx Kokoro model files under `filesDir/kokoro/`.
 * The archive is fetched on demand (never bundled in the APK) and extracted
 * with commons-compress. All work happens off the main thread.
 */
class KokoroModelManager(private val context: Context) {

    companion object {
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
        const val MODEL_DIR_NAME = "kokoro-multi-lang-v1_0"
        const val EXPECTED_BYTES = 349_906_910L

        @Volatile
        private var shared: KokoroModelManager? = null

        fun getInstance(context: Context): KokoroModelManager =
            shared ?: synchronized(this) {
                shared ?: KokoroModelManager(context.applicationContext).also { shared = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _status = MutableStateFlow(
        if (isReady()) KokoroModelStatus.READY else KokoroModelStatus.NOT_DOWNLOADED,
    )
    val status: StateFlow<KokoroModelStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    val modelDir: File
        get() = File(File(context.filesDir, "kokoro"), MODEL_DIR_NAME)

    fun modelFile(name: String): File = File(modelDir, name)

    /** True when every file sherpa-onnx needs is present on disk. */
    fun isReady(): Boolean {
        val dir = modelDir
        return File(dir, "model.onnx").exists() &&
            File(dir, "voices.bin").exists() &&
            File(dir, "tokens.txt").exists() &&
            File(dir, "espeak-ng-data").isDirectory
    }

    fun refresh() {
        if (_status.value == KokoroModelStatus.DOWNLOADING) return
        _status.value = if (isReady()) KokoroModelStatus.READY else KokoroModelStatus.NOT_DOWNLOADED
    }

    fun startDownload() {
        if (_status.value == KokoroModelStatus.DOWNLOADING) return
        if (isReady()) {
            _status.value = KokoroModelStatus.READY
            return
        }
        scope.launch {
            _status.value = KokoroModelStatus.DOWNLOADING
            _progress.value = 0f
            try {
                downloadAndExtract()
                _status.value = KokoroModelStatus.READY
                _statusMessage.value = ""
            } catch (e: Exception) {
                DebugLog.e("KokoroModel", "Model download failed", e)
                _status.value = KokoroModelStatus.ERROR
                _statusMessage.value = e.message ?: "Échec du téléchargement."
            }
        }
    }

    fun deleteModel() {
        scope.launch {
            try {
                File(context.filesDir, "kokoro").deleteRecursively()
            } catch (_: Exception) {
            }
            _progress.value = 0f
            _status.value = KokoroModelStatus.NOT_DOWNLOADED
        }
    }

    private suspend fun downloadAndExtract() = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "kokoro").apply { mkdirs() }
        val archive = File(root, "kokoro-model.tar.bz2")
        try {
            _statusMessage.value = "Téléchargement du modèle (≈350 Mo)…"
            downloadToFile(MODEL_URL, archive)
            _statusMessage.value = "Extraction du modèle…"
            extractTarBz2(archive, root)
        } finally {
            try {
                if (archive.exists()) archive.delete()
            } catch (_: Exception) {
            }
        }
        if (!isReady()) throw IllegalStateException("Archive extraite mais fichiers incomplets.")
        _progress.value = 1f
    }

    private fun downloadToFile(url: String, target: File) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body == null) {
                throw IllegalStateException("Téléchargement impossible (HTTP ${response.code}).")
            }
            val body = response.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: EXPECTED_BYTES
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        _progress.value = (done.toFloat() / total.toFloat() * 0.85f).coerceIn(0f, 0.85f)
                    }
                }
            }
        }
    }

    private fun extractTarBz2(archive: File, root: File) {
        archive.inputStream().buffered().use { fileIn ->
            BZip2CompressorInputStream(fileIn).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        // Guard against path traversal inside the archive.
                        val out = File(root, entry.name).canonicalFile
                        if (!out.path.startsWith(root.canonicalPath)) {
                            throw IllegalStateException("Archive invalide.")
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output -> tarIn.copyTo(output) }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
        _progress.value = 0.95f
    }
}
