package com.newoether.agora.studio.editorial

import android.content.Context
import com.newoether.agora.AgoraApplication
import com.newoether.agora.studio.image.GeminiImageService
import com.newoether.agora.studio.image.StudioAspectRatio
import com.newoether.agora.studio.image.StudioResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object EditorialImageInjector {

    /**
     * Generates an illustration image for a specific article section based on its title and text,
     * utilizing the model and prompt template configured in the Editorial settings.
     */
    suspend fun generateImageForSection(
        context: Context,
        sectionTitle: String,
        sectionContent: String
    ): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? AgoraApplication ?: return@withContext null
        val container = app.requireContainer()
        val settingsRepo = container.settingsRepository

        // 1. Resolve configured image model for editorial
        val configuredModel = settingsRepo.editorialImageModel.value
        val fallbackModel = settingsRepo.imageGenModel.value?.takeIf { it.isNotBlank() }
            ?: "openai:dall-e-3"
        val activeModelId = if (!configuredModel.isNullOrBlank()) configuredModel else fallbackModel

        // 2. Construct final prompt
        val basePromptTemplate = settingsRepo.editorialImagePrompt.value.ifBlank {
            "Illustration éditoriale de style magazine premium, artistique, détaillée, sans texte."
        }
        val cleanContent = sectionContent.take(200) // Take a concise snippet to guide prompt creation
        val finalPrompt = "$basePromptTemplate\n\nSujet/Concepts clés de l'illustration : $sectionTitle · $cleanContent"

        try {
            // 3. Call image generation service
            val results = GeminiImageService.instance.generateOrEditImages(
                context = context,
                prompt = finalPrompt,
                aspectRatio = StudioAspectRatio.LANDSCAPE, // Always landscape for elegant magazine hero/section banners
                resolution = StudioResolution.RES_1K,
                customModelIdOverride = activeModelId,
                burstCount = 1
            )
            
            // 4. Return the file path of the first successful image
            results.firstOrNull()?.filePath
        } catch (e: Exception) {
            null
        }
    }
}
