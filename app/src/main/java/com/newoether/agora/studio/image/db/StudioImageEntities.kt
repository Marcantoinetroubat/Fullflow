package com.newoether.agora.studio.image.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.newoether.agora.studio.image.GeneratedStudioImage
import com.newoether.agora.studio.image.StudioAspectRatio
import com.newoether.agora.studio.image.StudioImageModel
import com.newoether.agora.studio.image.StudioResolution

@Entity(tableName = "studio_images")
data class StudioImageEntity(
    @PrimaryKey
    val id: String,
    val prompt: String,
    val modelId: String,
    val aspectRatio: String,
    val resolution: String,
    val localFilePath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRetouched: Boolean = false,
) {
    fun toGeneratedStudioImage(): GeneratedStudioImage {
        return GeneratedStudioImage(
            id = id,
            bitmap = null,
            filePath = localFilePath,
            prompt = prompt,
            model = StudioImageModel.fromId(modelId),
            aspectRatio = StudioAspectRatio.entries.firstOrNull { it.apiValue == aspectRatio } ?: StudioAspectRatio.SQUARE,
            resolution = StudioResolution.entries.firstOrNull { it.apiValue == resolution } ?: StudioResolution.RES_1K,
            timestamp = timestamp,
            base64Data = null,
            isRetouched = isRetouched,
        )
    }
}

@Entity(tableName = "saved_prompts")
data class SavedPromptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val prompt: String,
    val styleCategory: String = "Style",
    val timestamp: Long = System.currentTimeMillis(),
)
