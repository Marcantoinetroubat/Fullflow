package com.newoether.agora.studio.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object GeminiVideoStudioController {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _showStudio = MutableStateFlow(false)
    val showStudio: StateFlow<Boolean> = _showStudio.asStateFlow()

    private val _selectedModel = MutableStateFlow(StudioVideoModel.DEFAULT)
    val selectedModel: StateFlow<StudioVideoModel> = _selectedModel.asStateFlow()

    private val _useSettingsModel = MutableStateFlow(false)
    val useSettingsModel: StateFlow<Boolean> = _useSettingsModel.asStateFlow()

    private val _customModelId = MutableStateFlow<String?>(null)
    val customModelId: StateFlow<String?> = _customModelId.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(StudioVideoAspectRatio.LANDSCAPE)
    val selectedAspectRatio: StateFlow<StudioVideoAspectRatio> = _selectedAspectRatio.asStateFlow()

    private val _selectedDuration = MutableStateFlow(StudioVideoDuration.SEC_5)
    val selectedDuration: StateFlow<StudioVideoDuration> = _selectedDuration.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _generatedVideos = MutableStateFlow<List<GeneratedStudioVideo>>(emptyList())
    val generatedVideos: StateFlow<List<GeneratedStudioVideo>> = _generatedVideos.asStateFlow()

    private val _activePlayingVideo = MutableStateFlow<GeneratedStudioVideo?>(null)
    val activePlayingVideo: StateFlow<GeneratedStudioVideo?> = _activePlayingVideo.asStateFlow()

    private val _videoSentToChat = MutableSharedFlow<GeneratedStudioVideo>(extraBufferCapacity = 1)
    val videoSentToChat: SharedFlow<GeneratedStudioVideo> = _videoSentToChat.asSharedFlow()

    fun openStudio() {
        _showStudio.value = true
        _errorMessage.value = null
    }

    fun closeStudio() {
        _showStudio.value = false
        _activePlayingVideo.value = null
    }

    fun setModel(model: StudioVideoModel) {
        _selectedModel.value = model
        _useSettingsModel.value = false
        _customModelId.value = null
    }

    fun setUseSettingsModel(use: Boolean) {
        _useSettingsModel.value = use
        if (!use) {
            _customModelId.value = null
        }
    }

    fun setCustomModelId(modelId: String?) {
        _customModelId.value = modelId
        _useSettingsModel.value = modelId != null
    }

    fun autoSelectBestModel(context: Context) {
        val app = context.applicationContext as? com.newoether.agora.AgoraApplication
        val container = app?.requireContainer() ?: return
        val settingsRepo = container.settingsRepository ?: return

        if (!_customModelId.value.isNullOrBlank()) return

        val configured = settingsRepo.videoGenModel.value
        if (!configured.isNullOrBlank() && configured.contains(":")) {
            setCustomModelId(configured)
            return
        }

        val googleKey = settingsRepo.resolveActiveKey("google")
            ?: settingsRepo.resolveActiveKey(com.newoether.agora.util.Constants.PROVIDER_GOOGLE)
            ?: com.newoether.agora.BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
        if (!googleKey.isNullOrBlank()) {
            return
        }

        val nonGoogleKey = settingsRepo.apiKeys.value.firstOrNull {
            it.key.isNotBlank() && !it.provider.equals("google", ignoreCase = true) && !it.provider.equals("gemini", ignoreCase = true)
        }
        if (nonGoogleKey != null) {
            val p = nonGoogleKey.provider
            val available = settingsRepo.availableModels.value[p]
            val bestModel = available?.firstOrNull() ?: "$p:default"
            setCustomModelId(bestModel)
        }
    }

    fun setAspectRatio(ratio: StudioVideoAspectRatio) {
        _selectedAspectRatio.value = ratio
    }

    fun setDuration(duration: StudioVideoDuration) {
        _selectedDuration.value = duration
    }

    fun playVideo(video: GeneratedStudioVideo) {
        _activePlayingVideo.value = video
    }

    fun closeVideoPlayer() {
        _activePlayingVideo.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun generateVideo(
        context: Context,
        prompt: String,
        onSuccess: () -> Unit = {},
    ) {
        if (prompt.isBlank()) {
            _errorMessage.value = "Veuillez saisir une description pour votre vidéo."
            return
        }

        scope.launch {
            _isGenerating.value = true
            _errorMessage.value = null
            try {
                val video = GeminiVideoService.instance.generateVideo(
                    context = context,
                    prompt = prompt.trim(),
                    model = _selectedModel.value,
                    aspectRatio = _selectedAspectRatio.value,
                    duration = _selectedDuration.value,
                    useSettingsModelOverride = _useSettingsModel.value,
                    customModelIdOverride = _customModelId.value,
                    onProgress = { _statusMessage.value = it },
                )
                _generatedVideos.value = listOf(video) + _generatedVideos.value
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = "Erreur lors de la génération: ${e.localizedMessage ?: "Échec"}"
            } finally {
                _isGenerating.value = false
                _statusMessage.value = ""
            }
        }
    }

    fun resumePending(context: Context) {
        scope.launch {
            val pending = GeminiVideoService.instance.pendingOperations(context)
            if (pending.isEmpty()) return@launch
            _statusMessage.value = "Reprise de ${pending.size} rendu(s) en cours..."
            pending.forEach { op ->
                try {
                    val details = GeminiVideoService.instance.resolveProviderDetails(context, op.customModelId)
                    val apiKey = details.second.takeIf { it.isNotBlank() } ?: GeminiVideoService.resolveApiKey(context, null)
                    if (apiKey.isBlank()) return@forEach
                    val uri = GeminiVideoService.instance.pollOperationForUri(
                        operationName = op.operationName,
                        baseUrl = details.third,
                        apiKey = apiKey,
                        onProgress = { _statusMessage.value = it },
                    ) ?: return@forEach
                    val targetFile = File(context.filesDir, "studio_videos/video_${op.videoId}.mp4")
                    targetFile.parentFile?.mkdirs()
                    GeminiVideoService.instance.downloadToFile(uri, apiKey, targetFile)
                    GeminiVideoService.instance.clearPendingOperation(context, op.videoId)
                    val video = GeneratedStudioVideo(
                        id = op.videoId,
                        filePath = targetFile.absolutePath,
                        uri = targetFile.toURI().toString(),
                        prompt = op.prompt,
                        model = op.model,
                        aspectRatio = op.aspectRatio,
                        duration = op.duration,
                    )
                    _generatedVideos.value = listOf(video) + _generatedVideos.value
                } catch (_: Exception) {
                }
            }
            _statusMessage.value = ""
        }
    }

    suspend fun saveToDeviceGallery(context: Context, video: GeneratedStudioVideo): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(video.filePath)
                if (!file.exists()) return@withContext false

                val filename = "FullFlow_Video_${System.currentTimeMillis()}.mp4"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FullFlow")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false

                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } else {
                    val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "FullFlow")
                    destDir.mkdirs()
                    val destFile = File(destDir, filename)
                    FileInputStream(file).use { input -> destFile.outputStream().use { out -> input.copyTo(out) } }
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

    fun shareVideo(context: Context, video: GeneratedStudioVideo) {
        val file = File(video.filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager la vidéo"))
    }

    fun sendToChat(video: GeneratedStudioVideo) {
        _videoSentToChat.tryEmit(video)
        closeStudio()
    }
}
