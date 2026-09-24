package com.newoether.agora.studio.video

import android.content.Context
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.genmedia.GenMediaHttp
import com.newoether.agora.api.genmedia.GenMediaKeys
import com.newoether.agora.model.ModelId
import com.newoether.agora.model.apiModelName
import com.newoether.agora.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Base64
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PendingVideoOperation(
    val videoId: String,
    val operationName: String,
    val prompt: String,
    val model: StudioVideoModel,
    val aspectRatio: StudioVideoAspectRatio,
    val duration: StudioVideoDuration,
    val customModelId: String? = null,
)

class GeminiVideoService private constructor() {

    companion object {
        val instance by lazy { GeminiVideoService() }

        private const val POLL_INTERVAL_MS = 10_000L
        private const val MAX_POLLS = 18 // ~3 minutes of server-side rendering
        private const val PREFS_NAME = "fullflow_video_ops"
        private const val OP_KEY_PREFIX = "op_"
        private const val VIDEO_RESOLUTION = "720p"
        private const val NEGATIVE_PROMPT = "blurry, jittery motion, distorted faces, watermark"

        suspend fun resolveApiKey(context: Context, apiKeyOverride: String?): String =
            GenMediaKeys.resolveApiKey(context, apiKeyOverride)

        fun resolveApiKeyBlocking(context: Context, apiKeyOverride: String?): String =
            GenMediaKeys.resolveApiKeyBlocking(context, apiKeyOverride)
    }

    suspend fun generateVideo(
        context: Context,
        prompt: String,
        model: StudioVideoModel = StudioVideoModel.DEFAULT,
        aspectRatio: StudioVideoAspectRatio = StudioVideoAspectRatio.LANDSCAPE,
        duration: StudioVideoDuration = StudioVideoDuration.SEC_5,
        apiKeyOverride: String? = null,
        useSettingsModelOverride: Boolean = false,
        customModelIdOverride: String? = null,
        onProgress: (String) -> Unit = {},
    ): GeneratedStudioVideo = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? AgoraApplication
        val container = runCatching { app?.requireContainer() }.getOrNull()
        val settingsRepo = container?.settingsRepository
        val providerRegistry = container?.providerRegistry

        val useSettingsVideoGen = useSettingsModelOverride || (settingsRepo != null && settingsRepo.videoGenEnabled.value && settingsRepo.videoGenModel.value?.contains(":") == true)

        var finalModelId = model.id
        var finalApiKey = apiKeyOverride ?: ""
        var finalBaseUrl = "https://generativelanguage.googleapis.com/v1beta"
        var finalProvider = "google"

        if (customModelIdOverride != null && settingsRepo != null && providerRegistry != null) {
            val providerName = providerRegistry.providerForModel(customModelIdOverride)
            finalProvider = providerName
            finalApiKey = apiKeyOverride
                ?: settingsRepo.awaitActiveKey(providerName)
                ?: settingsRepo.resolveActiveKey(providerName)
                ?: settingsRepo.apiKeys.value.firstOrNull { it.provider.equals(providerName, ignoreCase = true) }?.key
                ?: ""
            finalBaseUrl = providerRegistry.getEffectiveBaseUrl(providerName)
                ?: providerRegistry.getInstanceOrNull(providerName)?.defaultBaseUrl
                ?: ""
            finalModelId = ModelId.parse(providerRegistry.canonicalModelId(customModelIdOverride)).apiModelName
        } else if (useSettingsVideoGen && settingsRepo != null && providerRegistry != null) {
            val settingsModel = settingsRepo.videoGenModel.value
            if (!settingsModel.isNullOrBlank()) {
                val providerName = providerRegistry.providerForModel(settingsModel)
                finalProvider = providerName
                finalApiKey = apiKeyOverride
                    ?: settingsRepo.awaitActiveKey(providerName)
                    ?: settingsRepo.resolveActiveKey(providerName)
                    ?: settingsRepo.apiKeys.value.firstOrNull { it.provider.equals(providerName, ignoreCase = true) }?.key
                    ?: ""
                finalBaseUrl = providerRegistry.getEffectiveBaseUrl(providerName)
                    ?: providerRegistry.getInstanceOrNull(providerName)?.defaultBaseUrl
                    ?: ""
                finalModelId = ModelId.parse(providerRegistry.canonicalModelId(settingsModel)).apiModelName
            }
        } else {
            val googleKey = settingsRepo?.awaitActiveKey(Constants.PROVIDER_GOOGLE)
                ?: settingsRepo?.resolveActiveKey(Constants.PROVIDER_GOOGLE)
                ?: resolveApiKey(context, apiKeyOverride)
            if (!googleKey.isNullOrBlank()) {
                finalApiKey = googleKey
                finalProvider = Constants.PROVIDER_GOOGLE
            } else if (settingsRepo != null && providerRegistry != null) {
                val otherKey = settingsRepo.apiKeys.value.firstOrNull { it.key.isNotBlank() }
                if (otherKey != null) {
                    val pName = otherKey.provider
                    finalProvider = pName
                    finalApiKey = otherKey.key
                    finalBaseUrl = providerRegistry.getEffectiveBaseUrl(pName)
                        ?: providerRegistry.getInstanceOrNull(pName)?.defaultBaseUrl
                        ?: ""
                    val avail = settingsRepo.availableModels.value[pName]
                    val chosen = avail?.firstOrNull() ?: "default"
                    finalModelId = ModelId.parse(providerRegistry.canonicalModelId(chosen)).apiModelName
                }
            }
            if (finalApiKey.isBlank()) {
                finalApiKey = resolveApiKey(context, apiKeyOverride)
            }
        }

        val isLocalProvider = finalProvider.equals(Constants.PROVIDER_LOCAL, ignoreCase = true) ||
                              finalBaseUrl.contains("localhost") ||
                              finalBaseUrl.contains("127.0.0.1")
        if (finalApiKey.isBlank() && !isLocalProvider) {
            throw IllegalStateException("Clé API introuvable pour le fournisseur ($finalProvider). Veuillez sélectionner un autre modèle ou configurer votre clé dans Paramètres > Clés API.")
        }

        val videoId = UUID.randomUUID().toString()
        val videoDir = File(context.filesDir, "studio_videos").apply { mkdirs() }
        val targetFile = File(videoDir, "video_${videoId}.mp4")

        // Read video settings from settingsRepo if available
        val promptPrefix = settingsRepo?.videoGenPrompt?.value ?: ""
        val finalPrompt = if (promptPrefix.isNotBlank()) {
            "$promptPrefix, $prompt"
        } else {
            prompt
        }

        val settingsRatio = settingsRepo?.videoGenAspectRatio?.value ?: "16:9"
        val resolvedRatio = if (useSettingsVideoGen) {
            when (settingsRatio) {
                "9:16" -> StudioVideoAspectRatio.PORTRAIT
                "1:1" -> StudioVideoAspectRatio.SQUARE
                else -> StudioVideoAspectRatio.LANDSCAPE
            }
        } else {
            aspectRatio
        }

        val settingsDur = settingsRepo?.videoGenDuration?.value ?: 5
        val resolvedDuration = if (useSettingsVideoGen) {
            if (settingsDur >= 10) StudioVideoDuration.SEC_10 else StudioVideoDuration.SEC_5
        } else {
            duration
        }

        val negativePrompt = settingsRepo?.videoGenNegativePrompt?.value?.takeIf { it.isNotBlank() } ?: NEGATIVE_PROMPT
        val resolution = settingsRepo?.videoGenResolution?.value?.takeIf { it.isNotBlank() } ?: VIDEO_RESOLUTION

        val displayModelName = customModelIdOverride ?: (if (useSettingsVideoGen) settingsRepo?.videoGenModel?.value else null) ?: model.displayName
        onProgress("Envoi au moteur $displayModelName...")

        val isGoogle = finalProvider.equals(Constants.PROVIDER_GOOGLE, ignoreCase = true) ||
                       finalBaseUrl.contains("generativelanguage.googleapis.com") ||
                       finalBaseUrl.contains("googleapis.com") ||
                       finalModelId.startsWith("veo", ignoreCase = true) ||
                       finalModelId.contains("omni", ignoreCase = true)

        // Fail fast with an actionable message when the resolved Google model is a plain
        // text LLM: Veo endpoints would otherwise answer cryptic 400s (e.g. schema errors).
        val looksLikeTextLlm = isGoogle &&
            !finalModelId.contains("veo", ignoreCase = true) &&
            !finalModelId.contains("omni", ignoreCase = true) &&
            !finalModelId.contains("video", ignoreCase = true)
        if (looksLikeTextLlm) {
            throw IllegalStateException(
                "Le modèle « $finalModelId » n'est pas un modèle de génération vidéo. " +
                    "Sélectionnez un modèle Veo dans Paramètres > Génération vidéo.",
            )
        }

        if (isGoogle && finalModelId.contains("omni", ignoreCase = true)) {
            generateOmniVideo(
                prompt = finalPrompt,
                apiKey = finalApiKey,
                targetFile = targetFile,
                onProgress = onProgress
            )
            onProgress("Vidéo prête !")
            return@withContext GeneratedStudioVideo(
                id = videoId,
                filePath = targetFile.absolutePath,
                uri = targetFile.toURI().toString(),
                prompt = finalPrompt,
                model = model,
                aspectRatio = resolvedRatio,
                duration = resolvedDuration,
            )
        }

        val operationName = if (isGoogle) {
            submitVeoVideo(
                prompt = finalPrompt,
                modelId = finalModelId,
                aspectRatio = resolvedRatio,
                resolution = resolution,
                negativePrompt = negativePrompt,
                duration = resolvedDuration,
                baseUrl = finalBaseUrl,
                apiKey = finalApiKey
            )
        } else {
            submitOpenAiCompatibleVideo(
                prompt = finalPrompt,
                modelId = finalModelId,
                baseUrl = finalBaseUrl,
                apiKey = finalApiKey
            )
        }

        // Persist immediately so a process restart can resume the render.
        savePendingOperation(
            context = context,
            videoId = videoId,
            operationName = operationName,
            prompt = finalPrompt,
            model = model,
            aspectRatio = resolvedRatio,
            duration = resolvedDuration,
            customModelId = customModelIdOverride ?: (if (useSettingsVideoGen) settingsRepo?.videoGenModel?.value else null)
        )

        val videoUri = pollOperationForUri(operationName, finalBaseUrl, finalApiKey, onProgress)
        if (videoUri == null) {
            // Timeout: the pending operation is kept and resumes on next Studio open.
            throw IllegalStateException(
                "Rendu toujours en cours après ~3 minutes. " +
                    "L'opération est conservée et reprendra automatiquement à l'ouverture du Studio."
            )
        }

        onProgress("Téléchargement de la vidéo...")
        downloadToFile(videoUri, finalApiKey, targetFile)
        clearPendingOperation(context, videoId)

        onProgress("Vidéo prête !")

        GeneratedStudioVideo(
            id = videoId,
            filePath = targetFile.absolutePath,
            uri = targetFile.toURI().toString(),
            prompt = finalPrompt,
            model = model,
            aspectRatio = resolvedRatio,
            duration = resolvedDuration,
        )
    }

    private fun buildOmniRequestBody(prompt: String, inputAsArray: Boolean): String =
        JSONObject().apply {
            put("model", "gemini-omni-1.1-flash")
            if (inputAsArray) {
                // Current Interactions schema: input is an array of content parts
                // (« content must be a non-empty array » when sent as a plain string).
                put("input", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                })
            } else {
                put("input", prompt)
            }
            put("response_format", JSONObject().apply {
                put("type", "video")
                put("delivery", "uri")
            })
        }.toString()

    private suspend fun generateOmniVideo(
        prompt: String,
        apiKey: String,
        targetFile: File,
        onProgress: (String) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        onProgress("Envoi au moteur Gemini Omni...")
        val url = "https://generativelanguage.googleapis.com/v1beta/interactions?key=$apiKey"

        // Primary: array-of-parts schema. Defensive fallback: legacy plain-string input.
        var resp = GenMediaHttp.postJson(url, apiKey, buildOmniRequestBody(prompt, inputAsArray = true))
        if (!resp.successful && resp.code == 400) {
            resp = GenMediaHttp.postJson(url, apiKey, buildOmniRequestBody(prompt, inputAsArray = false))
        }
        if (!resp.successful) {
            throw IllegalStateException("Erreur Omni (${resp.code}): ${resp.body}")
        }

        val json = JSONObject(resp.body)
        val inlineData = json.optJSONObject("output_video")?.optString("data")
        if (!inlineData.isNullOrBlank()) {
            val bytes = Base64.decode(inlineData, Base64.DEFAULT)
            targetFile.writeBytes(bytes)
            return@withContext
        }

        var videoUri = extractVideoUri(json)
        val interactionId = json.optString("id")

        if (videoUri.isNullOrBlank() && interactionId.isNotBlank()) {
            repeat(MAX_POLLS) { index ->
                onProgress("Rendu Omni en cours... (${(index + 1) * (POLL_INTERVAL_MS / 1000)}s)")
                delay(POLL_INTERVAL_MS)
                val pollResp = GenMediaHttp.get("https://generativelanguage.googleapis.com/v1beta/interactions/$interactionId", apiKey)
                if (pollResp.successful) {
                    val pollJson = JSONObject(pollResp.body)
                    val data = pollJson.optJSONObject("output_video")?.optString("data")
                    if (!data.isNullOrBlank()) {
                        val bytes = Base64.decode(data, Base64.DEFAULT)
                        targetFile.writeBytes(bytes)
                        return@withContext
                    }
                    val uri = extractVideoUri(pollJson)
                    if (!uri.isNullOrBlank()) {
                        videoUri = uri
                        return@repeat
                    }
                }
            }
        }

        if (videoUri.isNullOrBlank()) {
            throw IllegalStateException("Gemini Omni n'a pas retourné de vidéo. Réponse : ${resp.body.take(200)}")
        }

        onProgress("Téléchargement de la vidéo...")
        downloadToFile(videoUri, apiKey, targetFile)
    }

    private suspend fun submitOpenAiCompatibleVideo(
        prompt: String,
        modelId: String,
        baseUrl: String,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val effectiveBaseUrl = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://api.openai.com/v1"
        val url = if (effectiveBaseUrl.endsWith("/videos")) effectiveBaseUrl else "$effectiveBaseUrl/videos"
        val bodyJson = JSONObject().apply {
            put("prompt", prompt)
            put("model", modelId)
        }.toString()

        val submit = GenMediaHttp.postJson(url, apiKey, bodyJson)
        if (!submit.successful) {
            throw IllegalStateException("Erreur vidéo (${submit.code}): ${submit.body}")
        }
        val videoId = JSONObject(submit.body).optString("id")
        if (videoId.isBlank()) {
            throw IllegalStateException("Soumission vidéo sans identifiant.")
        }
        videoId
    }

    private suspend fun submitVeoVideo(
        prompt: String,
        modelId: String,
        aspectRatio: StudioVideoAspectRatio,
        resolution: String,
        negativePrompt: String,
        duration: StudioVideoDuration,
        baseUrl: String,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/')

        val isVeo2 = modelId.contains("veo-2", ignoreCase = true)
        val validDuration = when {
            duration.seconds >= 8 -> "8"
            isVeo2 && duration.seconds == 5 -> "5"
            duration.seconds <= 4 -> "4"
            else -> "6" // 6s is the standard valid duration for Veo 3.1 and Veo 2
        }

        val validAspectRatio = when (aspectRatio) {
            StudioVideoAspectRatio.PORTRAIT -> "9:16"
            else -> "16:9" // Veo strictly requires 16:9 or 9:16
        }

        // 1080p and 4k are ONLY supported when duration is 8s on Veo 3.1
        val validResolution = if (resolution.equals("1080p", ignoreCase = true) && validDuration == "8") {
            "1080p"
        } else {
            "720p"
        }

        // Try predictLongRunning first (standard Gemini Developer API route used by google-genai SDK)
        val predictUrl = "$cleanBase/models/$modelId:predictLongRunning"
        val predictBody = JSONObject().apply {
            put("instances", JSONArray().apply {
                put(JSONObject().apply {
                    put("prompt", prompt)
                })
            })
            put("parameters", JSONObject().apply {
                put("aspectRatio", validAspectRatio)
                put("durationSeconds", validDuration.toIntOrNull() ?: 6)
                put("personGeneration", "allow_adult")
                if (negativePrompt.isNotBlank()) {
                    put("negativePrompt", negativePrompt)
                }
                put("resolution", validResolution)
            })
        }.toString()

        val predictResp = GenMediaHttp.postJson(predictUrl, apiKey, predictBody)
        if (predictResp.successful) {
            val opName = JSONObject(predictResp.body).optString("name")
            if (opName.isNotBlank()) return@withContext opName
        }

        // Fallback to generateVideos
        val genVideosUrl = "$cleanBase/models/$modelId:generateVideos"
        val genVideosBody = JSONObject().apply {
            put("prompt", prompt)
            put("config", JSONObject().apply {
                put("aspectRatio", validAspectRatio)
                put("durationSeconds", validDuration.toIntOrNull() ?: 6)
                put("personGeneration", "allow_adult")
                if (negativePrompt.isNotBlank()) put("negativePrompt", negativePrompt)
                put("resolution", validResolution)
            })
        }.toString()

        val genResp = GenMediaHttp.postJson(genVideosUrl, apiKey, genVideosBody)
        if (genResp.successful) {
            val opName = JSONObject(genResp.body).optString("name")
            if (opName.isNotBlank()) return@withContext opName
        }

        val errResp = if (!predictResp.successful && predictResp.code != 404) predictResp else genResp
        throw IllegalStateException("Erreur Veo (${errResp.code}) sur le modèle $modelId : ${errResp.body}")
    }

    suspend fun pollOperationForUri(
        operationName: String,
        baseUrl: String,
        apiKey: String,
        onProgress: (String) -> Unit = {},
        maxPolls: Int = MAX_POLLS,
    ): String? = withContext(Dispatchers.IO) {
        val isGoogle = baseUrl.contains("googleapis.com") || operationName.contains("operations/")
        val baseForPoll = if (baseUrl.contains("googleapis.com")) {
            "https://generativelanguage.googleapis.com/v1beta"
        } else {
            if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://api.openai.com/v1"
        }
        repeat(maxPolls) { index ->
            onProgress("Rendu vidéo en cours... (${(index + 1) * (POLL_INTERVAL_MS / 1000)}s)")
            delay(POLL_INTERVAL_MS)
            val pollUrl = if (operationName.startsWith("http")) {
                operationName
            } else if (isGoogle) {
                if (operationName.startsWith("operations/") || operationName.startsWith("projects/")) {
                    "$baseForPoll/$operationName"
                } else {
                    "$baseForPoll/operations/$operationName"
                }
            } else {
                if (operationName.startsWith("videos/")) {
                    "$baseForPoll/$operationName"
                } else {
                    "$baseForPoll/videos/$operationName"
                }
            }
            val pollResp = GenMediaHttp.get(pollUrl, apiKey)
            if (pollResp.successful) {
                val pollJson = JSONObject(pollResp.body)
                if (isGoogle) {
                    if (pollJson.has("error")) {
                        val errMsg = pollJson.optJSONObject("error")?.optString("message")
                        throw IllegalStateException("Échec du rendu Veo: ${errMsg ?: "erreur inconnue"}")
                    }
                    if (pollJson.optBoolean("done", false)) {
                        return@withContext extractVideoUri(pollJson)
                            ?: throw IllegalStateException("Opération terminée mais aucune vidéo retournée.")
                    }
                } else {
                    val status = pollJson.optString("status")
                    if (status.equals("failed", ignoreCase = true)) {
                        val errMsg = pollJson.optJSONObject("error")?.optString("message")
                            ?: pollJson.optString("error").takeIf { it.isNotBlank() }
                        throw IllegalStateException("Échec du rendu vidéo: ${errMsg ?: "erreur inconnue"}")
                    }
                    if (status.equals("completed", ignoreCase = true) || pollJson.optBoolean("done", false)) {
                        val directUrl = pollJson.optString("url").takeIf { it.isNotBlank() }
                        val rawId = operationName.removePrefix("videos/")
                        return@withContext directUrl ?: "$baseForPoll/videos/$rawId/content"
                    }
                }
            }
        }
        null
    }

    fun extractVideoUri(pollJson: JSONObject): String? {
        val response = pollJson.optJSONObject("response") ?: pollJson.optJSONObject("result") ?: pollJson

        // Shape 1: response.generateVideoResponse.generatedSamples[0].video.uri
        response.optJSONObject("generateVideoResponse")
            ?.optJSONArray("generatedSamples")
            ?.optJSONObject(0)?.let { first ->
                val uri = first.optJSONObject("video")?.optString("uri")
                    ?: first.optString("uri")
                if (!uri.isNullOrBlank()) return uri
            }

        // Shape 2: response.generateVideoResponse.generatedVideos[0].video.uri
        response.optJSONObject("generateVideoResponse")
            ?.optJSONArray("generatedVideos")
            ?.optJSONObject(0)?.let { first ->
                val uri = first.optJSONObject("video")?.optString("uri")
                    ?: first.optString("uri")
                if (!uri.isNullOrBlank()) return uri
            }

        // Shape 3: response.generatedVideos[0].video.uri
        response.optJSONArray("generatedVideos")?.optJSONObject(0)?.let { first ->
            val uri = first.optJSONObject("video")?.optString("uri")
                ?: first.optString("uri")
            if (!uri.isNullOrBlank()) return uri
        }

        // Shape 4: response.generatedSamples[0].video.uri
        response.optJSONArray("generatedSamples")?.optJSONObject(0)?.let { first ->
            val uri = first.optJSONObject("video")?.optString("uri")
                ?: first.optString("uri")
            if (!uri.isNullOrBlank()) return uri
        }

        // Shape 5: response.videos[0].video.uri or response.videos[0].uri
        response.optJSONArray("videos")?.optJSONObject(0)?.let { first ->
            val uri = first.optJSONObject("video")?.optString("uri")
                ?: first.optString("uri")
            if (!uri.isNullOrBlank()) return uri
        }

        // Shape 6: output_video.uri (Interactions API)
        pollJson.optJSONObject("output_video")?.optString("uri")?.takeIf { it.isNotBlank() }?.let { return it }

        // Shape 7: steps[].content[].uri (Interactions API steps)
        pollJson.optJSONArray("steps")?.let { steps ->
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val contents = step.optJSONArray("content") ?: continue
                for (j in 0 until contents.length()) {
                    val item = contents.optJSONObject(j) ?: continue
                    val uri = item.optString("uri")
                    if (uri.isNotBlank()) return uri
                }
            }
        }

        // Shape 8: direct video.uri or uri or url
        val directUri = response.optJSONObject("video")?.optString("uri")
            ?: response.optString("uri").takeIf { it.isNotBlank() }
            ?: pollJson.optString("url").takeIf { it.isNotBlank() }
        if (!directUri.isNullOrBlank()) return directUri

        return null
    }

    suspend fun downloadToFile(videoUri: String, apiKey: String, targetFile: File) =
        withContext(Dispatchers.IO) {
            var downloadUrl = videoUri.trim()

            // If it's a relative files URI like "files/abc123"
            if (downloadUrl.startsWith("files/")) {
                downloadUrl = "https://generativelanguage.googleapis.com/v1beta/$downloadUrl:download?alt=media"
            }

            // If it's a Google API files URI without :download
            if (downloadUrl.contains("generativelanguage.googleapis.com") && downloadUrl.contains("/files/")) {
                if (!downloadUrl.contains(":download")) {
                    val base = downloadUrl.substringBefore('?')
                    val query = downloadUrl.substringAfter('?', "")
                    downloadUrl = if (query.isNotBlank()) {
                        "$base:download?alt=media&$query"
                    } else {
                        "$base:download?alt=media"
                    }
                }
            }

            // For Google API endpoints, append apiKey query param ONLY to generativelanguage.googleapis.com
            // NEVER append to storage.googleapis.com or googleusercontent.com (breaks signed URL signature)
            val isGoogleGenerative = downloadUrl.contains("generativelanguage.googleapis.com")
            if (isGoogleGenerative && apiKey.isNotBlank() && !downloadUrl.contains("key=")) {
                downloadUrl += if (downloadUrl.contains("?")) "&key=$apiKey" else "?key=$apiKey"
            }

            // Handle redirects manually to prevent sending x-goog-api-key to storage.googleapis.com
            val noRedirectClient = GenMediaHttp.client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            var currentUrl = downloadUrl
            var redirectCount = 0
            val maxRedirects = 5

            while (redirectCount < maxRedirects) {
                val isGoogleHost = currentUrl.contains("generativelanguage.googleapis.com")
                val reqBuilder = Request.Builder().url(currentUrl)

                if (isGoogleHost && apiKey.isNotBlank()) {
                    reqBuilder.addHeader("x-goog-api-key", apiKey)
                } else if (!isGoogleHost && apiKey.isNotBlank() && !currentUrl.contains("googleusercontent.com") && !currentUrl.contains("storage.googleapis.com")) {
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = noRedirectClient.newCall(reqBuilder.build()).execute()
                val statusCode = response.code

                if (statusCode in 301..308) {
                    val location = response.header("Location")
                    response.close()
                    if (location.isNullOrBlank()) {
                        throw IllegalStateException("Redirection HTTP $statusCode sans en-tête Location.")
                    }
                    currentUrl = location
                    redirectCount++
                    continue
                }

                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    response.close()
                    throw IllegalStateException("Échec du téléchargement vidéo (HTTP $statusCode): $errBody")
                }

                val body = response.body ?: throw IllegalStateException("Réponse vidéo vide (HTTP $statusCode).")
                FileOutputStream(targetFile).use { out ->
                    body.byteStream().copyTo(out)
                }
                response.close()
                break
            }

            if (targetFile.length() == 0L) {
                throw IllegalStateException("Fichier vidéo vide après téléchargement.")
            }
        }

    fun savePendingOperation(
        context: Context,
        videoId: String,
        operationName: String,
        prompt: String,
        model: StudioVideoModel,
        aspectRatio: StudioVideoAspectRatio,
        duration: StudioVideoDuration,
        customModelId: String? = null,
    ) {
        val payload = JSONObject().apply {
            put("operationName", operationName)
            put("prompt", prompt)
            put("modelId", model.id)
            put("aspectRatio", aspectRatio.name)
            put("duration", duration.name)
            if (customModelId != null) {
                put("customModelId", customModelId)
            }
        }.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(OP_KEY_PREFIX + videoId, payload)
            .apply()
    }

    fun clearPendingOperation(context: Context, videoId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(OP_KEY_PREFIX + videoId)
            .apply()
    }

    fun pendingOperations(context: Context): List<PendingVideoOperation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(OP_KEY_PREFIX) || value !is String) return@mapNotNull null
            try {
                val json = JSONObject(value)
                PendingVideoOperation(
                    videoId = key.removePrefix(OP_KEY_PREFIX),
                    operationName = json.optString("operationName"),
                    prompt = json.optString("prompt"),
                    model = StudioVideoModel.fromId(json.optString("modelId")),
                    aspectRatio = StudioVideoAspectRatio.valueOf(json.optString("aspectRatio")),
                    duration = StudioVideoDuration.valueOf(json.optString("duration")),
                    customModelId = json.optString("customModelId").takeIf { it.isNotBlank() }
                ).takeIf { it.operationName.isNotBlank() && it.prompt.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun resolveProviderDetails(context: Context, modelId: String?): Triple<String, String, String> {
        val app = context.applicationContext as? AgoraApplication
        val container = runCatching { app?.requireContainer() }.getOrNull()
        val settingsRepo = container?.settingsRepository
        val providerRegistry = container?.providerRegistry

        var finalModelId = modelId ?: StudioVideoModel.DEFAULT.id
        var finalApiKey = ""
        var finalBaseUrl = "https://generativelanguage.googleapis.com/v1beta"

        if (modelId != null && settingsRepo != null && providerRegistry != null) {
            val providerName = providerRegistry.providerForModel(modelId)
            finalApiKey = settingsRepo.resolveActiveKey(providerName)
                ?: settingsRepo.apiKeys.value.firstOrNull { it.provider.equals(providerName, ignoreCase = true) }?.key
                ?: ""
            finalBaseUrl = providerRegistry.getEffectiveBaseUrl(providerName)
                ?: providerRegistry.getInstanceOrNull(providerName)?.defaultBaseUrl
                ?: ""
            finalModelId = ModelId.parse(providerRegistry.canonicalModelId(modelId)).apiModelName
        }

        return Triple(finalModelId, finalApiKey, finalBaseUrl)
    }
}
