package com.newoether.agora.studio.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Transcodes a conversation article into platform-optimized social content.
 * Uses the configured LLM to adapt tone, length, and format per platform.
 */
class SocialExportEngine(private val context: Context) {

    private val app = context.applicationContext as? AgoraApplication
    private val container = app?.requireContainer()
    private val providerRegistry = container?.providerRegistry
    private val settingsRepo = container?.settingsRepository

    /**
     * Transcodes article text into a platform-specific format.
     * Returns the transcoded text ready for sharing.
     */
    suspend fun transcode(
        articleText: String,
        platform: SocialPlatform,
    ): SocialExportResult = withContext(Dispatchers.IO) {
        if (providerRegistry == null || settingsRepo == null) {
            return@withContext SocialExportResult(
                platform = platform,
                text = articleText,
                mimeType = SocialFormats.getMimeType(platform),
                success = false,
            )
        }

        // Use the selected chat model for transcoding
        val modelId = settingsRepo.selectedModel.value
        val providerName = providerRegistry.providerForModel(modelId)
        val provider = providerRegistry.getInstanceOrNull(providerName)
            ?: return@withContext SocialExportResult(platform, articleText, SocialFormats.getMimeType(platform), success = false)

        val apiKey = settingsRepo.resolveActiveKey(providerName) ?: ""
        val baseUrl = settingsRepo.providerBaseUrls.value[providerName]

        val prompt = SocialFormats.getTranscodePrompt(platform, articleText)

        val messages = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = prompt,
                participant = Participant.USER,
            )
        )

        val config = ProviderConfig(
            apiKey = apiKey,
            modelId = modelId,
            baseUrl = baseUrl,
            thinkingEnabled = false,
            systemPrompt = "Tu es un expert en communication digitale et en contenu adapté aux plateformes sociales.",
        )

        val outputBuilder = StringBuilder()
        provider.generateResponse(messages, config).collect { event ->
            if (event is StreamEvent.TextChunk) {
                outputBuilder.append(event.text)
            }
        }

        val transcodedText = outputBuilder.toString().ifBlank { articleText }

        SocialExportResult(
            platform = platform,
            text = transcodedText,
            mimeType = SocialFormats.getMimeType(platform),
            success = true,
        )
    }

    /**
     * Shares the transcoded content via Android Intent.
     * For platforms with specific package names, targets the app directly if installed.
     */
    fun share(result: SocialExportResult, images: List<String> = emptyList()) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType

            // Text content
            putExtra(Intent.EXTRA_TEXT, result.text)

            // Subject for email-style platforms
            if (result.platform == SocialPlatform.LINKEDIN) {
                putExtra(Intent.EXTRA_SUBJECT, "Article — FullFlow")
            }

            // Attach images if available
            if (images.isNotEmpty()) {
                val uris = images.mapNotNull { path ->
                    val file = File(path)
                    if (file.exists()) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else null
                }
                if (uris.isNotEmpty()) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            // Target specific app if installed
            result.platform.packageName?.let { pkg ->
                if (isAppInstalled(pkg)) {
                    setPackage(pkg)
                }
            }
        }

        // Launch share chooser
        val chooser = Intent.createChooser(intent, "Partager sur ${result.platform.displayName}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Saves the transcoded content to the Second Brain as a note.
     */
    suspend fun saveToBrain(result: SocialExportResult): Boolean = withContext(Dispatchers.IO) {
        if (container == null) return@withContext false

        try {
            val db = container.database
            val notesDir = File(context.filesDir, "notes").also { it.mkdirs() }
            val timestamp = System.currentTimeMillis()
            val title = result.text.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: "Export social"
            val filePath = "Social/${result.platform.displayName}_${timestamp}.md"
            val file = File(notesDir, filePath).apply { parentFile?.mkdirs() }
            file.writeText("# $title\n\n${result.text}")

            // Insert into Room
            val noteEntity = com.newoether.agora.data.local.NoteEntity(
                filePath = filePath,
                title = title,
                tags = "social,${result.platform.name.lowercase()}",
                contentHash = "",
                isPinned = false,
                lastModified = timestamp,
            )
            db.noteDao().upsertNote(noteEntity)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
