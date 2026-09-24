package com.newoether.agora.studio.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.HttpClient
import com.newoether.agora.api.genmedia.GenMediaHttp
import com.newoether.agora.api.genmedia.GenMediaKeys
import com.newoether.agora.model.ModelId
import com.newoether.agora.model.apiModelName
import com.newoether.agora.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GeminiImageService private constructor() {

    companion object {
        val instance by lazy { GeminiImageService() }

        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream)
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    suspend fun generateOrEditImages(
        context: Context,
        prompt: String,
        model: StudioImageModel = StudioImageModel.NANO_BANANA_2,
        aspectRatio: StudioAspectRatio = StudioAspectRatio.SQUARE,
        resolution: StudioResolution = StudioResolution.RES_1K,
        inputImageBase64: String? = null,
        burstCount: Int = 1,
        apiKeyOverride: String? = null,
        useSettingsModelOverride: Boolean = false,
        customModelIdOverride: String? = null,
    ): List<GeneratedStudioImage> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? AgoraApplication
        val container = app?.requireContainer()
        val settingsRepo = container?.settingsRepository
        val providerRegistry = container?.providerRegistry

        val useSettingsImageGen = useSettingsModelOverride || (settingsRepo != null && settingsRepo.imageGenEnabled.value && settingsRepo.imageGenModel.value?.contains(":") == true)

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
        } else if (useSettingsImageGen && settingsRepo != null && providerRegistry != null) {
            val settingsModel = settingsRepo.imageGenModel.value
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
                ?: GenMediaKeys.resolveApiKey(context, apiKeyOverride)
            if (!googleKey.isNullOrBlank()) {
                finalApiKey = googleKey
                finalProvider = Constants.PROVIDER_GOOGLE
            } else if (settingsRepo != null && providerRegistry != null) {
                // If Google key is missing, check if the user has configured keys for any other provider
                val otherKey = settingsRepo.apiKeys.value.firstOrNull { it.key.isNotBlank() }
                if (otherKey != null) {
                    val pName = otherKey.provider
                    finalProvider = pName
                    finalApiKey = otherKey.key
                    finalBaseUrl = providerRegistry.getEffectiveBaseUrl(pName)
                        ?: providerRegistry.getInstanceOrNull(pName)?.defaultBaseUrl
                        ?: ""
                    val avail = settingsRepo.availableModels.value[pName]
                    val chosen = avail?.firstOrNull { it.contains("dall", true) || it.contains("image", true) }
                        ?: avail?.firstOrNull()
                        ?: if (pName.equals("openai", true)) "dall-e-3" else "default"
                    finalModelId = ModelId.parse(providerRegistry.canonicalModelId(chosen)).apiModelName
                }
            }
            if (finalApiKey.isBlank()) {
                finalApiKey = GenMediaKeys.resolveApiKey(context, apiKeyOverride)
            }
        }

        val isLocalProvider = finalProvider.equals(Constants.PROVIDER_LOCAL, ignoreCase = true) ||
                              finalBaseUrl.contains("localhost") ||
                              finalBaseUrl.contains("127.0.0.1")
        if (finalApiKey.isBlank() && !isLocalProvider) {
            throw IllegalStateException("Clé API introuvable pour le fournisseur ($finalProvider). Veuillez sélectionner un autre modèle ou configurer votre clé dans Paramètres > Clés API.")
        }

        val clampedCount = burstCount.coerceIn(1, 4)
        val isGoogle = finalProvider.equals(Constants.PROVIDER_GOOGLE, ignoreCase = true) ||
                       finalBaseUrl.contains("generativelanguage.googleapis.com") ||
                       finalBaseUrl.contains("googleapis.com") ||
                       finalModelId.startsWith("imagen", ignoreCase = true) ||
                       finalModelId.startsWith("gemini", ignoreCase = true)

        if (!isGoogle && !inputImageBase64.isNullOrBlank()) {
            throw IllegalStateException("La retouche d'image n'est prise en charge qu'avec les modèles Google (Nano Banana / Gemini). Veuillez sélectionner un modèle Google pour modifier une image existante.")
        }

        if (clampedCount == 1) {
            val single = if (isGoogle) {
                callApiSingle(
                    context = context,
                    prompt = prompt,
                    modelId = finalModelId,
                    baseUrl = finalBaseUrl,
                    modelEnum = model,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    inputImageBase64 = inputImageBase64,
                    apiKey = finalApiKey,
                )
            } else {
                callOpenAiCompatible(
                    context = context,
                    prompt = prompt,
                    modelId = finalModelId,
                    baseUrl = finalBaseUrl,
                    apiKey = finalApiKey,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                )
            }
            return@withContext listOf(single)
        }

        coroutineScope {
            val tasks = (1..clampedCount).map { index ->
                async {
                    try {
                        val variantPrompt = if (index > 1) "$prompt (variation $index)" else prompt
                        if (isGoogle) {
                            callApiSingle(
                                context = context,
                                prompt = variantPrompt,
                                modelId = finalModelId,
                                baseUrl = finalBaseUrl,
                                modelEnum = model,
                                aspectRatio = aspectRatio,
                                resolution = resolution,
                                inputImageBase64 = inputImageBase64,
                                apiKey = finalApiKey,
                            )
                        } else {
                            callOpenAiCompatible(
                                context = context,
                                prompt = variantPrompt,
                                modelId = finalModelId,
                                baseUrl = finalBaseUrl,
                                apiKey = finalApiKey,
                                aspectRatio = aspectRatio,
                                resolution = resolution,
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val results = tasks.mapNotNull { it.await() }
            if (results.isEmpty()) {
                throw IllegalStateException("Échec de la génération en lot pour toutes les requêtes.")
            }
            results
        }
    }

    private suspend fun callApiSingle(
        context: Context,
        prompt: String,
        modelId: String,
        baseUrl: String,
        modelEnum: StudioImageModel,
        aspectRatio: StudioAspectRatio,
        resolution: StudioResolution,
        inputImageBase64: String?,
        apiKey: String,
    ): GeneratedStudioImage = withContext(Dispatchers.IO) {
        val isEditing = inputImageBase64 != null

        if (modelId.contains("imagen-3", ignoreCase = true) && !isEditing) {
            return@withContext callImagen3(
                context = context,
                prompt = prompt,
                modelId = modelId,
                baseUrl = baseUrl,
                aspectRatio = aspectRatio,
                resolution = resolution,
                apiKey = apiKey,
            )
        }

        val url = if (baseUrl.isBlank()) {
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        } else {
            "${baseUrl.trimEnd('/')}/models/$modelId:generateContent"
        }

        val partsArray = JSONArray().apply {
            val textPart = JSONObject().apply { put("text", prompt) }
            put(textPart)

            if (!inputImageBase64.isNullOrBlank()) {
                val imagePart = JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", inputImageBase64)
                    }
                    put("inlineData", inlineData)
                }
                put(imagePart)
            }
        }

        val contentObj = JSONObject().apply {
            put("parts", partsArray)
        }

        val contentsArray = JSONArray().apply {
            put(contentObj)
        }

        val imageConfig = JSONObject().apply {
            put("aspectRatio", aspectRatio.apiValue)
            put("imageSize", resolution.apiValue)
        }

        val responseModalities = JSONArray().apply {
            put("TEXT")
            put("IMAGE")
        }

        val generationConfig = JSONObject().apply {
            put("imageConfig", imageConfig)
            put("responseModalities", responseModalities)
        }

        val requestJson = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }

        val response = GenMediaHttp.postJson(url, apiKey, requestJson.toString())
        val responseBodyString = response.body

        if (!response.successful) {
            val errorMsg = try {
                val errJson = JSONObject(responseBodyString)
                errJson.optJSONObject("error")?.optString("message") ?: responseBodyString
            } catch (_: Exception) {
                responseBodyString
            }
            throw IllegalStateException("Erreur API ($modelId): $errorMsg")
        }

        val rootJson = JSONObject(responseBodyString)
        val candidates = rootJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("Aucune image n'a été retournée par le modèle.")
        }

        val firstCandidate = candidates.optJSONObject(0)
        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")

        var foundBase64: String? = null
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                if (inlineData != null) {
                    val data = inlineData.optString("data")
                    if (data.isNotBlank()) {
                        foundBase64 = data
                        break
                    }
                }
            }
        }

        if (foundBase64.isNullOrBlank()) {
            val textFallback = parts?.optJSONObject(0)?.optString("text").orEmpty()
            val hint = if (textFallback.isNotBlank()) ": $textFallback" else ""
            throw IllegalStateException("Le modèle n'a pas inclus d'image dans sa réponse$hint")
        }

        val imageBytes = Base64.decode(foundBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalStateException("Impossible de décoder les données de l'image générée.")

        val savedFile = saveBitmapToCache(context, bitmap)

        GeneratedStudioImage(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            filePath = savedFile.absolutePath,
            prompt = prompt,
            model = modelEnum,
            aspectRatio = aspectRatio,
            resolution = resolution,
            base64Data = foundBase64,
            isRetouched = isEditing,
        )
    }

    private suspend fun callImagen3(
        context: Context,
        prompt: String,
        modelId: String,
        baseUrl: String,
        aspectRatio: StudioAspectRatio,
        resolution: StudioResolution,
        apiKey: String,
    ): GeneratedStudioImage = withContext(Dispatchers.IO) {
        val url = if (baseUrl.isBlank()) {
            "https://generativelanguage.googleapis.com/v1beta/models/$modelId:predict"
        } else {
            "${baseUrl.trimEnd('/')}/models/$modelId:predict"
        }

        val instances = JSONArray().apply {
            put(JSONObject().apply { put("prompt", prompt) })
        }
        val parameters = JSONObject().apply {
            put("sampleCount", 1)
            put("aspectRatio", aspectRatio.apiValue)
            put("negativePrompt", "blurry, watermark, distorted text")
        }

        val requestJson = JSONObject().apply {
            put("instances", instances)
            put("parameters", parameters)
        }

        val response = GenMediaHttp.postJson(url, apiKey, requestJson.toString())
        val responseBodyString = response.body

        if (!response.successful) {
            throw IllegalStateException("Erreur Imagen 3 (${response.code}): $responseBodyString")
        }

        val rootJson = JSONObject(responseBodyString)
        val predictions = rootJson.optJSONArray("predictions")
        val base64Data = predictions?.optJSONObject(0)?.optString("bytesBase64Encoded").orEmpty()

        if (base64Data.isBlank()) {
            throw IllegalStateException("Aucune image retournée par Imagen 3.")
        }

        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalStateException("Impossible de décoder les données d'image Imagen 3.")

        val savedFile = saveBitmapToCache(context, bitmap)

        GeneratedStudioImage(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            filePath = savedFile.absolutePath,
            prompt = prompt,
            model = StudioImageModel.IMAGEN_3,
            aspectRatio = aspectRatio,
            resolution = resolution,
            base64Data = base64Data,
            isRetouched = false,
        )
    }

    private suspend fun callOpenAiCompatible(
        context: Context,
        prompt: String,
        modelId: String,
        baseUrl: String,
        apiKey: String,
        aspectRatio: StudioAspectRatio,
        resolution: StudioResolution,
    ): GeneratedStudioImage = withContext(Dispatchers.IO) {
        val sizeStr = mapAspectRatioAndResolutionToSize(aspectRatio, resolution)
        val effectiveBaseUrl = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://api.openai.com/v1"
        val cleanUrl = if (effectiveBaseUrl.endsWith("/images/generations")) {
            effectiveBaseUrl
        } else {
            "$effectiveBaseUrl/images/generations"
        }

        val body = JSONObject().apply {
            put("model", modelId)
            put("prompt", prompt)
            put("size", sizeStr)
            put("n", 1)
        }.toString()

        val response = HttpClient.post(
            cleanUrl,
            body,
            if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey"),
            callTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
            readTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
        ) ?: throw IllegalStateException("Aucune réponse reçue du fournisseur d'images.")

        val json = JSONObject(response)
        val dataArray = json.optJSONArray("data")
        if (dataArray == null || dataArray.length() == 0) {
            val errorObj = json.optJSONObject("error")
            val errorMsg = errorObj?.optString("message") ?: "L'API de génération d'images n'a retourné aucune donnée."
            throw IllegalStateException("Erreur API ($modelId): $errorMsg")
        }

        val firstObject = dataArray.getJSONObject(0)
        val b64 = firstObject.optString("b64_json")
        val imageBytes = if (!b64.isNullOrBlank()) {
            Base64.decode(b64, Base64.DEFAULT)
        } else {
            val url = firstObject.optString("url")
            if (url.isNullOrBlank()) {
                throw IllegalStateException("Aucune URL ou donnée b64_json retournée.")
            }
            HttpClient.getBytes(
                url,
                callTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
                readTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
            ) ?: throw IllegalStateException("Impossible de télécharger l'image depuis l'URL: $url")
        }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalStateException("Impossible de décoder l'image générée.")

        val savedFile = saveBitmapToCache(context, bitmap)
        val base64Data = if (!b64.isNullOrBlank()) b64 else bitmapToBase64(bitmap)

        GeneratedStudioImage(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            filePath = savedFile.absolutePath,
            prompt = prompt,
            model = StudioImageModel.DEFAULT,
            aspectRatio = aspectRatio,
            resolution = resolution,
            base64Data = base64Data,
            isRetouched = false,
        )
    }

    private fun mapAspectRatioAndResolutionToSize(
        aspectRatio: StudioAspectRatio,
        resolution: StudioResolution
    ): String {
        val baseSize = when (resolution) {
            StudioResolution.RES_512 -> 512
            StudioResolution.RES_1K -> 1024
            StudioResolution.RES_2K -> 1024
            StudioResolution.RES_4K -> 1024
        }

        return when (aspectRatio) {
            StudioAspectRatio.SQUARE -> "${baseSize}x${baseSize}"
            StudioAspectRatio.PORTRAIT_STORY -> {
                if (baseSize == 1024) "1024x1792" else "512x896"
            }
            StudioAspectRatio.LANDSCAPE -> {
                if (baseSize == 1024) "1792x1024" else "896x512"
            }
            StudioAspectRatio.PORTRAIT_PHOTO -> {
                if (baseSize == 1024) "768x1024" else "384x512"
            }
            StudioAspectRatio.LANDSCAPE_PHOTO -> {
                if (baseSize == 1024) "1024x768" else "512x384"
            }
        }
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "studio_images").apply { if (!exists()) mkdirs() }
        val file = File(dir, "studio_img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file
    }
}
