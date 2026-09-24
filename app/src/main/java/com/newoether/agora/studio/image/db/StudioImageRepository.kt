package com.newoether.agora.studio.image.db

import android.content.Context
import android.graphics.Bitmap
import com.newoether.agora.studio.image.GeneratedStudioImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class StudioImageRepository(
    private val studioImageDao: StudioImageDao,
    private val savedPromptDao: SavedPromptDao,
) {
    val allImages: Flow<List<StudioImageEntity>> = studioImageDao.getAllImages()
    val allSavedPrompts: Flow<List<SavedPromptEntity>> = savedPromptDao.getAllSavedPrompts()
    val imageCount: Flow<Int> = studioImageDao.getImageCount()

    suspend fun persistGeneratedImages(
        context: Context,
        images: List<GeneratedStudioImage>
    ): List<StudioImageEntity> = withContext(Dispatchers.IO) {
        val storageDir = File(context.filesDir, "studio_images").apply {
            if (!exists()) mkdirs()
        }

        val entities = images.map { generated ->
            var finalPath = generated.filePath
            // If the current file is in temporary cache or null, ensure a copy in filesDir
            if (generated.bitmap != null && (finalPath == null || finalPath.contains(context.cacheDir.path))) {
                val durableFile = File(storageDir, "studio_${generated.id}.jpg")
                try {
                    FileOutputStream(durableFile).use { out ->
                        generated.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    finalPath = durableFile.absolutePath
                } catch (_: Exception) {
                    // fallback to existing path
                }
            } else if (finalPath != null && !finalPath.startsWith(storageDir.absolutePath)) {
                val sourceFile = File(finalPath)
                if (sourceFile.exists()) {
                    val durableFile = File(storageDir, "studio_${generated.id}.jpg")
                    try {
                        sourceFile.copyTo(durableFile, overwrite = true)
                        finalPath = durableFile.absolutePath
                    } catch (_: Exception) {
                    }
                }
            }

            StudioImageEntity(
                id = generated.id,
                prompt = generated.prompt,
                modelId = generated.model.id,
                aspectRatio = generated.aspectRatio.apiValue,
                resolution = generated.resolution.apiValue,
                localFilePath = finalPath ?: "",
                timestamp = generated.timestamp,
                isRetouched = generated.isRetouched,
            )
        }

        studioImageDao.insertImages(entities)
        entities
    }

    suspend fun deleteImages(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        for (id in ids) {
            val entity = studioImageDao.getImageById(id)
            if (entity != null && entity.localFilePath.isNotBlank()) {
                try {
                    val file = File(entity.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {
                }
            }
        }
        studioImageDao.deleteImagesByIds(ids)
    }

    suspend fun savePrompt(
        title: String,
        prompt: String,
        category: String = "Style"
    ): Long = withContext(Dispatchers.IO) {
        val entity = SavedPromptEntity(
            title = title.trim(),
            prompt = prompt.trim(),
            styleCategory = category.trim().ifEmpty { "Style" },
            timestamp = System.currentTimeMillis()
        )
        savedPromptDao.insertPrompt(entity)
    }

    suspend fun deleteSavedPrompt(id: Long) = withContext(Dispatchers.IO) {
        savedPromptDao.deletePromptById(id)
    }

    suspend fun seedDefaultPromptsIfEmpty() = withContext(Dispatchers.IO) {
        if (savedPromptDao.getPromptCount() == 0) {
            val defaults = listOf(
                SavedPromptEntity(
                    title = "Aquarelle Délicate",
                    prompt = "Peinture aquarelle lumineuse aux lavis délicats et touches impressionnistes, palette pastel douce",
                    styleCategory = "Art & Peinture",
                    timestamp = System.currentTimeMillis() - 4000
                ),
                SavedPromptEntity(
                    title = "Portrait Studio 8K",
                    prompt = "Photographie portrait professionnel haute résolution avec éclairage de studio Rembrandt, bokeh velouté, 8k",
                    styleCategory = "Photo",
                    timestamp = System.currentTimeMillis() - 3000
                ),
                SavedPromptEntity(
                    title = "Cyberpunk Néon",
                    prompt = "Scène urbaine futuriste sous la pluie battante, reflets d'enseignes néons turquoise et magenta sur le bitume",
                    styleCategory = "Cinéma",
                    timestamp = System.currentTimeMillis() - 2000
                ),
                SavedPromptEntity(
                    title = "Sculpture Origami",
                    prompt = "Portrait origami géométrique en papier multicolore plié, ombres douces et précision millimétrique, studio lighting",
                    styleCategory = "Design",
                    timestamp = System.currentTimeMillis() - 1000
                ),
                SavedPromptEntity(
                    title = "Claymation Stop-Motion",
                    prompt = "Personnage expressif sculpté en pâte à modeler style stop-motion dans un décor chaleureux et miniature",
                    styleCategory = "Animation",
                    timestamp = System.currentTimeMillis()
                )
            )
            for (item in defaults) {
                savedPromptDao.insertPrompt(item)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: StudioImageRepository? = null

        fun getInstance(context: Context): StudioImageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = StudioImageDatabase.getInstance(context)
                    StudioImageRepository(db.studioImageDao(), db.savedPromptDao()).also {
                        INSTANCE = it
                    }
                }
            }
        }
    }
}
