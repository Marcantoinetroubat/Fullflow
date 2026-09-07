package com.newoether.agora.studio.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.newoether.agora.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiImageService private constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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
    ): List<GeneratedStudioImage> = withContext(Dispatchers.IO) {
        val effectiveKey = when {
            !apiKeyOverride.isNullOrBlank() -> apiKeyOverride.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() -> BuildConfig.GEMINI_API_KEY.trim()
            else -> throw IllegalStateException("Clé API Gemini introuvable. Veuillez renseigner une clé API.")
        }

        val clampedCount = burstCount.coerceIn(1, 4)

        if (clampedCount == 1) {
            val single = callApiSingle(
                context = context,
                prompt = prompt,
                model = model,
                aspectRatio = aspectRatio,
                resolution = resolution,
                inputImageBase64 = inputImageBase64,
                apiKey = effectiveKey,
            )
            return@withContext listOf(single)
        }

        coroutineScope {
            val tasks = (1..clampedCount).map { index ->
                async {
                    try {
                        val variantPrompt = if (index > 1) "$prompt (variation $index)" else prompt
                        callApiSingle(
                            context = context,
                            prompt = variantPrompt,
                            model = model,
                            aspectRatio = aspectRatio,
                            resolution = resolution,
                            inputImageBase64 = inputImageBase64,
                            apiKey = effectiveKey,
                        )
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
        model: StudioImageModel,
        aspectRatio: StudioAspectRatio,
        resolution: StudioResolution,
        inputImageBase64: String?,
        apiKey: String,
    ): GeneratedStudioImage = withContext(Dispatchers.IO) {
        val isEditing = inputImageBase64 != null

        if (model == StudioImageModel.IMAGEN_3 && !isEditing) {
            return@withContext callImagen3(
                context = context,
                prompt = prompt,
                aspectRatio = aspectRatio,
                resolution = resolution,
                apiKey = apiKey,
            )
        }

        // Gemini REST API (gemini-3.1-flash-image-preview / gemini-2.5-flash-image / gemini-3-pro-image-preview)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.id}:generateContent?key=$apiKey"

        val partsArray = JSONArray().apply {
            // Text instruction / prompt
            val textPart = JSONObject().apply { put("text", prompt) }
            put(textPart)

            // Multimodal image retouching / editing
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

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseBodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JSONObject(responseBodyString)
                errJson.optJSONObject("error")?.optString("message") ?: responseBodyString
            } catch (_: Exception) {
                responseBodyString
            }
            throw IllegalStateException("Erreur API ($model): $errorMsg")
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
            model = model,
            aspectRatio = aspectRatio,
            resolution = resolution,
            base64Data = foundBase64,
            isRetouched = isEditing,
        )
    }

    private suspend fun callImagen3(
        context: Context,
        prompt: String,
        aspectRatio: StudioAspectRatio,
        resolution: StudioResolution,
        apiKey: String,
    ): GeneratedStudioImage = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict?key=$apiKey"

        val instances = JSONArray().apply {
            put(JSONObject().apply { put("prompt", prompt) })
        }
        val parameters = JSONObject().apply {
            put("sampleCount", 1)
            put("aspectRatio", aspectRatio.apiValue)
        }

        val requestJson = JSONObject().apply {
            put("instances", instances)
            put("parameters", parameters)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseBodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
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

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "studio_images").apply { if (!exists()) mkdirs() }
        val file = File(dir, "studio_img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file
    }
}
