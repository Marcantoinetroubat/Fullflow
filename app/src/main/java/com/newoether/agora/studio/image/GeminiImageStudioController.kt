package com.newoether.agora.studio.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.newoether.agora.studio.image.db.SavedPromptEntity
import com.newoether.agora.studio.image.db.StudioImageEntity
import com.newoether.agora.studio.image.db.StudioImageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object GeminiImageStudioController {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _showStudio = MutableStateFlow(false)
    val showStudio = _showStudio.asStateFlow()

    private val _selectedModel = MutableStateFlow(StudioImageModel.NANO_BANANA_2)
    val selectedModel = _selectedModel.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(StudioAspectRatio.SQUARE)
    val selectedAspectRatio = _selectedAspectRatio.asStateFlow()

    private val _selectedResolution = MutableStateFlow(StudioResolution.RES_1K)
    val selectedResolution = _selectedResolution.asStateFlow()

    private val _burstCount = MutableStateFlow(1)
    val burstCount = _burstCount.asStateFlow()

    private val _attachedBitmapForEdit = MutableStateFlow<Bitmap?>(null)
    val attachedBitmapForEdit = _attachedBitmapForEdit.asStateFlow()

    private val _attachedImageBase64 = MutableStateFlow<String?>(null)
    val attachedImageBase64 = _attachedImageBase64.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _generatedImages = MutableStateFlow<List<GeneratedStudioImage>>(emptyList())
    val generatedImages = _generatedImages.asStateFlow()

    private val _historyImages = MutableStateFlow<List<GeneratedStudioImage>>(emptyList())
    val historyImages = _historyImages.asStateFlow()

    private val _activeZoomImage = MutableStateFlow<GeneratedStudioImage?>(null)
    val activeZoomImage = _activeZoomImage.asStateFlow()

    private val _imageSentToChat = MutableSharedFlow<Pair<String, File>>(extraBufferCapacity = 5)
    val imageSentToChat = _imageSentToChat.asSharedFlow()

    // Selection Mode & Batch Download state
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedImageIds = _selectedImageIds.asStateFlow()

    private val _isBatchDownloading = MutableStateFlow(false)
    val isBatchDownloading = _isBatchDownloading.asStateFlow()

    // Room Database Cached History and Saved Prompts flows
    private val _cachedRoomImages = MutableStateFlow<List<StudioImageEntity>>(emptyList())
    val cachedRoomImages = _cachedRoomImages.asStateFlow()

    private val _savedPrompts = MutableStateFlow<List<SavedPromptEntity>>(emptyList())
    val savedPrompts = _savedPrompts.asStateFlow()

    fun init(context: Context) {
        val repo = StudioImageRepository.getInstance(context)
        scope.launch {
            repo.seedDefaultPromptsIfEmpty()
        }
        scope.launch {
            repo.allImages.collect { list ->
                _cachedRoomImages.value = list
            }
        }
        scope.launch {
            repo.allSavedPrompts.collect { list ->
                _savedPrompts.value = list
            }
        }
    }

    fun openStudio(initialPreset: StudioPresetTemplate? = null) {
        _errorMessage.value = null
        _showStudio.value = true
    }

    fun closeStudio() {
        _showStudio.value = false
        _activeZoomImage.value = null
        clearSelection()
        _isSelectionMode.value = false
    }

    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            clearSelection()
        }
    }

    fun toggleSelectImage(id: String) {
        val current = _selectedImageIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedImageIds.value = current
    }

    fun selectAll(ids: List<String>) {
        _selectedImageIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedImageIds.value = emptySet()
    }

    suspend fun batchDownloadSelected(context: Context, images: List<GeneratedStudioImage>): Int = withContext(Dispatchers.IO) {
        _isBatchDownloading.value = true
        val targetIds = _selectedImageIds.value
        val itemsToDownload = images.filter { targetIds.contains(it.id) }
        var successCount = 0

        for (item in itemsToDownload) {
            val ok = saveToDeviceGallery(context, item)
            if (ok) successCount++
        }

        withContext(Dispatchers.Main) {
            _isBatchDownloading.value = false
            clearSelection()
            _isSelectionMode.value = false
        }
        successCount
    }

    suspend fun deleteSelectedImages(context: Context) = withContext(Dispatchers.IO) {
        val targetIds = _selectedImageIds.value.toList()
        if (targetIds.isNotEmpty()) {
            val repo = StudioImageRepository.getInstance(context)
            repo.deleteImages(targetIds)
            withContext(Dispatchers.Main) {
                _generatedImages.value = _generatedImages.value.filterNot { targetIds.contains(it.id) }
                clearSelection()
                _isSelectionMode.value = false
            }
        }
    }

    suspend fun savePrompt(context: Context, title: String, prompt: String, category: String = "Style") = withContext(Dispatchers.IO) {
        val repo = StudioImageRepository.getInstance(context)
        repo.savePrompt(title, prompt, category)
    }

    suspend fun deleteSavedPrompt(context: Context, id: Long) = withContext(Dispatchers.IO) {
        val repo = StudioImageRepository.getInstance(context)
        repo.deleteSavedPrompt(id)
    }

    fun setModel(model: StudioImageModel) {
        _selectedModel.value = model
    }

    fun setAspectRatio(ratio: StudioAspectRatio) {
        _selectedAspectRatio.value = ratio
    }

    fun setResolution(resolution: StudioResolution) {
        _selectedResolution.value = resolution
    }

    fun setBurstCount(count: Int) {
        _burstCount.value = count.coerceIn(1, 4)
    }

    fun attachUriForEdit(context: Context, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val base64 = GeminiImageService.bitmapToBase64(bitmap)
                    withContext(Dispatchers.Main) {
                        _attachedBitmapForEdit.value = bitmap
                        _attachedImageBase64.value = base64
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Impossible de charger l'image sélectionnée: ${e.message}"
                }
            }
        }
    }

    fun attachBitmapForEdit(bitmap: Bitmap) {
        scope.launch(Dispatchers.IO) {
            val base64 = GeminiImageService.bitmapToBase64(bitmap)
            withContext(Dispatchers.Main) {
                _attachedBitmapForEdit.value = bitmap
                _attachedImageBase64.value = base64
            }
        }
    }

    fun clearAttachedImage() {
        _attachedBitmapForEdit.value = null
        _attachedImageBase64.value = null
    }

    fun prepareRetouchFromGenerated(image: GeneratedStudioImage) {
        val bmp = image.bitmap ?: (image.filePath?.let { BitmapFactory.decodeFile(it) })
        bmp?.let { attachBitmapForEdit(it) }
        _activeZoomImage.value = null
    }

    fun openZoomImage(image: GeneratedStudioImage) {
        _activeZoomImage.value = image
    }

    fun closeZoomImage() {
        _activeZoomImage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun generateOrEdit(
        context: Context,
        prompt: String,
        apiKeyOverride: String? = null,
        onSuccess: (() -> Unit)? = null,
    ) {
        if (prompt.isBlank() && _attachedImageBase64.value == null) {
            _errorMessage.value = "Veuillez saisir une description ou sélectionner une photo à retoucher."
            return
        }

        val effectivePrompt = if (prompt.isBlank() && _attachedImageBase64.value != null) {
            "Améliorer et retoucher cette image de manière esthétique et professionnelle"
        } else {
            prompt.trim()
        }

        _isGenerating.value = true
        _errorMessage.value = null
        _statusMessage.value = if (_attachedImageBase64.value != null) {
            "Retouche de votre image avec ${_selectedModel.value.displayName}…"
        } else {
            "Création de votre image avec ${_selectedModel.value.displayName}…"
        }

        scope.launch {
            try {
                val results = GeminiImageService.instance.generateOrEditImages(
                    context = context,
                    prompt = effectivePrompt,
                    model = _selectedModel.value,
                    aspectRatio = _selectedAspectRatio.value,
                    resolution = _selectedResolution.value,
                    inputImageBase64 = _attachedImageBase64.value,
                    burstCount = _burstCount.value,
                    apiKeyOverride = apiKeyOverride,
                )

                _generatedImages.value = results
                _historyImages.value = (results + _historyImages.value).distinctBy { it.id }.take(50)
                try {
                    val repo = StudioImageRepository.getInstance(context)
                    repo.persistGeneratedImages(context, results)
                } catch (_: Exception) {
                }
                _isGenerating.value = false
                onSuccess?.invoke()
            } catch (e: Exception) {
                _isGenerating.value = false
                _errorMessage.value = e.message ?: "Une erreur inattendue est survenue."
            }
        }
    }

    suspend fun saveToDeviceGallery(context: Context, image: GeneratedStudioImage): Boolean = withContext(Dispatchers.IO) {
        val bitmap = image.bitmap ?: (image.filePath?.let { BitmapFactory.decodeFile(it) }) ?: return@withContext false
        try {
            val filename = "NanoBanana_${System.currentTimeMillis()}_${image.id.take(4)}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FullFlow")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return@withContext true
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "FullFlow").apply { if (!exists()) mkdirs() }
                val file = File(appDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun shareImage(context: Context, image: GeneratedStudioImage) {
        val filePath = image.filePath ?: return
        try {
            val file = File(filePath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${image.prompt}\n(Généré avec ${image.model.displayName})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partager l'image"))
        } catch (_: Exception) {
        }
    }

    fun sendToChat(image: GeneratedStudioImage) {
        val filePath = image.filePath ?: return
        val file = File(filePath)
        _imageSentToChat.tryEmit(image.prompt to file)
        closeStudio()
    }
}
