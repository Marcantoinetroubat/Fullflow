package com.newoether.agora.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.newoether.agora.data.ConversationSettings
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.model.Participant
import com.newoether.agora.studio.image.GeminiImageService
import com.newoether.agora.studio.image.StudioAspectRatio
import com.newoether.agora.studio.image.StudioResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Service orchestrating AI-generated background illustrations for chat conversations.
 * Extracts conversation context, resolves user-configured image models & prompt templates,
 * generates an atmospheric vertical wallpaper via [GeminiImageService], and links the result
 * to the conversation's [ConversationSettings].
 */
class ConversationBackgroundGenerator(
    private val context: Context,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
    private val providers: ProviderRegistry,
) {
    sealed interface Result {
        data class Success(val filePath: String) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun generateAndPersist(conversationId: String): Result = withContext(Dispatchers.IO) {
        settings.awaitInitialLoad()
        providers.awaitInitialSync()

        val conversation = conversations.getConversation(conversationId)
            ?: return@withContext Result.Failure("Conversation introuvable")

        // Extract global multi-turn context (up to 3,500 chars) across the conversation path
        var topic = conversation.title
        try {
            val snapshot = conversations.getMessageTopologySnapshot(conversationId)
            val path = ConversationUiState.resolvePath(
                allMessages = snapshot.map { it.toUiChatMessageStub() },
                streamingMsg = null,
                selectedChildren = conversations.restoreBranchSelections(conversationId),
            )
            val turns = mutableListOf<String>()
            var totalChars = 0
            for (item in path) {
                val entity = conversations.getMessage(item.id) ?: continue
                val text = entity.text?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val prefix = if (item.participant == Participant.USER) "User" else "Assistant"
                val snippet = text.take(600)
                turns.add("$prefix: $snippet")
                totalChars += snippet.length
                if (totalChars > 3500) break
            }

            if (turns.isNotEmpty()) {
                val fullContext = turns.joinToString("\n").take(3500)
                topic = "\"${conversation.title.ifBlank { "Discussion" }}\"\n$fullContext"
            }
        } catch (_: Exception) {
            // Keep conversation.title as fallback topic
        }

        val promptTemplate = settings.backgroundGenPrompt.value.ifBlank {
            DEFAULT_BACKGROUND_PROMPT_TEMPLATE
        }
        val prompt = if (promptTemplate.contains("%s")) {
            promptTemplate.replace("%s", topic)
        } else {
            "$promptTemplate\nThème : $topic"
        }

        val selectedBgModel = settings.backgroundGenModel.value?.takeIf { it.isNotBlank() }
        val imageService = GeminiImageService.instance

        try {
            val generatedList = imageService.generateOrEditImages(
                context = context,
                prompt = prompt,
                aspectRatio = StudioAspectRatio.PORTRAIT_STORY,
                resolution = StudioResolution.RES_1K,
                customModelIdOverride = selectedBgModel,
                useSettingsModelOverride = selectedBgModel == null,
            )

            val firstImg = generatedList.firstOrNull()
                ?: return@withContext Result.Failure("Aucune image n'a été générée par le modèle")

            val bgDir = File(context.filesDir, "conversation_backgrounds").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(bgDir, "bg_${conversationId}_${System.currentTimeMillis()}.jpg")

            val sourceFile = firstImg.filePath?.let { File(it) }
            if (sourceFile != null && sourceFile.exists()) {
                sourceFile.copyTo(destFile, overwrite = true)
            } else if (firstImg.bitmap != null) {
                FileOutputStream(destFile).use { out ->
                    firstImg.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            } else {
                return@withContext Result.Failure("Impossible de récupérer les données visuelles générées")
            }

            // Update conversation settings with background file path
            val currentSettings = settings.conversationSettings.value[conversationId] ?: ConversationSettings()
            currentSettings.backgroundImageUri?.let { oldPath ->
                try {
                    val oldFile = File(oldPath)
                    if (oldFile.exists() && oldFile.absolutePath != destFile.absolutePath) {
                        oldFile.delete()
                    }
                } catch (_: Exception) {}
            }

            val updatedSettings = currentSettings.copy(backgroundImageUri = destFile.absolutePath)
            settings.setConversationSettings(conversationId, updatedSettings)

            Result.Success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Erreur inattendue lors de la génération de l'arrière-plan")
        }
    }

    suspend fun removeBackground(conversationId: String) = withContext(Dispatchers.IO) {
        val current = settings.conversationSettings.value[conversationId] ?: return@withContext
        current.backgroundImageUri?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        val updated = current.copy(backgroundImageUri = null)
        settings.setConversationSettings(conversationId, updated)
    }

    companion object {
        const val DEFAULT_BACKGROUND_PROMPT_TEMPLATE =
            "Atmospheric, elegant, and aesthetic background wallpaper illustration without any text, depicting the mood, environment and core concepts of: %s. Subtle digital concept art, clean composition, cinematic soft lighting, vertical phone wallpaper."
    }
}
