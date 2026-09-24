package com.newoether.agora.tool

import android.app.Application
import com.newoether.agora.api.HttpClient
import com.newoether.agora.api.ProviderDefaults
import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolFunction
import com.newoether.agora.api.ToolParameters
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.util.Constants
import com.newoether.agora.util.DebugLog
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Tool that generates images through an OpenAI-compatible `/images/generations` endpoint.
 * Successful bytes are persisted by [ToolImageStore] and returned on the owning tool result.
 */
class ImageGenToolProvider(private val app: Application) : ToolProvider {

    private val imageStore = ToolImageStore(app)

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.imageGenEnabled) return emptyList()
        return listOf(
            ToolDefinition(function = ToolFunction(
                name = "generate_image",
                description = "Generate an image from a text prompt. The generated image is shown to the user automatically — do NOT attempt to embed or describe the raw image data. Use this whenever the user asks to create, draw, paint, or generate a picture.",
                parameters = ToolParameters(
                    properties = mapOf(
                        "prompt" to ToolProperty("string", "A detailed description of the image to generate."),
                        "size" to ToolProperty("string", "Optional image size, e.g. 1024x1024, 1024x1536, or 1536x1024.")
                    ),
                    required = listOf("prompt")
                )
            ))
        )
    }

    override fun handles(name: String): Boolean = name == "generate_image"

    override suspend fun execute(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): String = executeResult(name, arguments, ctx).text

    override fun executeEvents(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): Flow<ToolExecutionEvent> = flow {
        val model = ctx.imageGenModel.ifBlank { "image-model" }
        emit(ToolExecutionEvent.TargetResolved(target = "image_generation:$model"))
        emit(ToolExecutionEvent.Progress(message = "Création de l'image en cours..."))
        emit(ToolExecutionEvent.Completed(executeResult(name, arguments, ctx)))
    }

    private suspend fun executeResult(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val textAndImage = executeOffMain(name, arguments, ctx)
        ToolExecutionResult(
            text = textAndImage.first,
            images = listOfNotNull(textAndImage.second),
            isError = textAndImage.second == null,
        )
    }

    private suspend fun executeOffMain(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): Pair<String, com.newoether.agora.model.ToolImageAttachment?> {
        ctx.conversationId?.takeIf { it.isNotBlank() }
            ?: return err("missing_conversation", "Image generation requires a conversation.") to null
        val argsStr = arguments.ifBlank { "{}" }
        val args = try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        } catch (_: Exception) { emptyMap() }
        val prompt = (args["prompt"] as? JsonPrimitive)?.content
            ?: return err("no_prompt", null) to null
        val size = (args["size"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: ctx.imageGenSize.ifBlank { "1024x1024" }

        val apiKey = ctx.imageGenApiKey.ifBlank {
            com.newoether.agora.api.genmedia.GenMediaKeys.resolveApiKeyBlocking(app, null)
        }
        if (apiKey.isBlank()) return err("no_api_key", null) to null
        val baseUrl = ctx.imageGenBaseUrl.trimEnd('/')
        val model = ctx.imageGenModel.ifBlank {
            if (baseUrl.contains("openai") || apiKey.startsWith("sk-")) "dall-e-3" else "imagen-3.0-generate-002"
        }

        return withContext(Dispatchers.IO) {
            try {
                val bytes: ByteArray = if (isGoogleEndpoint(baseUrl, model)) {
                    fetchGoogleImageBytes(baseUrl, model, apiKey, prompt, size)
                        ?: return@withContext err("no_image", "Google API returned no image.") to null
                } else {
                    val effectiveBaseUrl = baseUrl.ifBlank { ProviderDefaults.OPENAI_BASE_URL }
                    val body = buildJsonObject {
                        put("model", model)
                        put("prompt", prompt)
                        put("size", size)
                        put("n", 1)
                    }.toString()
                    val response = HttpClient.post(
                        "$effectiveBaseUrl/images/generations",
                        body,
                        mapOf("Authorization" to "Bearer $apiKey"),
                        callTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
                        readTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
                    ) ?: return@withContext err("no_response", null) to null

                    val json = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response)
                    val first = json["data"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?: return@withContext err("no_image", "The endpoint returned no image data.") to null

                    val b64 = (first["b64_json"] as? JsonPrimitive)?.content
                    if (!b64.isNullOrBlank()) {
                        android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    } else {
                        val url = (first["url"] as? JsonPrimitive)?.content
                            ?: return@withContext err(
                                "no_image",
                                "No b64_json or url in the response.",
                            ) to null
                        HttpClient.getBytes(
                            url,
                            callTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
                            readTimeoutMillis = Constants.IMAGE_GENERATION_TIMEOUT_MS,
                        ) ?: return@withContext err("download_failed", null) to null
                    }
                }

                val attachment = imageStore.persistGeneratedBytes(
                    bytes = bytes,
                    filePrefix = "generated_image",
                )
                buildJsonObject {
                    put("type", "image_generation")
                    put("status", "ok")
                    put("size", size)
                }.toString() to attachment
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.e("ImageGenTool", "generate_image failed", e)
                err("generation_error", e.message) to null
            }
        }
    }

    private fun isGoogleEndpoint(baseUrl: String, model: String): Boolean {
        return baseUrl.contains("generativelanguage.googleapis.com") ||
            baseUrl.contains("googleapis.com") ||
            model.startsWith("imagen", ignoreCase = true) ||
            model.startsWith("gemini", ignoreCase = true) ||
            (baseUrl.isBlank() && !model.startsWith("dall-e", ignoreCase = true) && !model.startsWith("gpt-", ignoreCase = true))
    }

    private suspend fun fetchGoogleImageBytes(
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        size: String,
    ): ByteArray? {
        val root = if (baseUrl.isBlank() || baseUrl.contains("openai.com")) {
            "https://generativelanguage.googleapis.com/v1beta"
        } else {
            baseUrl
        }

        if (model.contains("imagen", ignoreCase = true) || model.isBlank() || model == "image-model" || model == "gpt-image-1") {
            val targetModel = if (model.isBlank() || !model.contains("imagen", ignoreCase = true)) {
                "imagen-3.0-generate-002"
            } else {
                model
            }
            val url = "$root/models/$targetModel:predict"
            val ratio = mapSizeToAspectRatio(size)
            val reqBody = buildJsonObject {
                put("instances", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("prompt", prompt) })
                })
                put("parameters", buildJsonObject {
                    put("sampleCount", 1)
                    put("aspectRatio", ratio)
                    put("personGeneration", "allow_adult")
                    put("negativePrompt", "blurry, watermark, distorted text")
                })
            }.toString()

            val response = HttpClient.post(
                url,
                reqBody,
                mapOf("Content-Type" to "application/json", "x-goog-api-key" to apiKey),
            ) ?: return null
            val rootObj = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response)
            val predictions = rootObj["predictions"]?.jsonArray
            val b64 = (predictions?.firstOrNull()?.jsonObject?.get("bytesBase64Encoded") as? JsonPrimitive)?.content
            return if (!b64.isNullOrBlank()) android.util.Base64.decode(b64, android.util.Base64.DEFAULT) else null
        } else {
            val targetModel = if (model.isBlank() || model == "gpt-image-1") "gemini-3.1-flash-image" else model
            val url = "$root/models/$targetModel:generateContent"
            val reqBody = buildJsonObject {
                put("contents", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("parts", kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("responseModalities", kotlinx.serialization.json.buildJsonArray {
                        add(JsonPrimitive("TEXT"))
                        add(JsonPrimitive("IMAGE"))
                    })
                })
            }.toString()

            val response = HttpClient.post(
                url,
                reqBody,
                mapOf("Content-Type" to "application/json", "x-goog-api-key" to apiKey),
            ) ?: return null
            val rootObj = Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response)
            val candidates = rootObj["candidates"]?.jsonArray
            val parts = candidates?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            for (part in parts.orEmpty()) {
                val pObj = part.jsonObject
                val inlineData = pObj["inlineData"]?.jsonObject ?: pObj["inline_data"]?.jsonObject
                val dataStr = (inlineData?.get("data") as? JsonPrimitive)?.content
                if (!dataStr.isNullOrBlank()) {
                    return android.util.Base64.decode(dataStr, android.util.Base64.DEFAULT)
                }
            }
            return null
        }
    }

    private fun mapSizeToAspectRatio(size: String): String {
        val parts = size.split("x", "X")
        if (parts.size == 2) {
            val w = parts[0].toIntOrNull() ?: 1024
            val h = parts[1].toIntOrNull() ?: 1024
            return when {
                w == h -> "1:1"
                w > h && (w.toFloat() / h.toFloat() > 1.5f) -> "16:9"
                w > h -> "4:3"
                h > w && (h.toFloat() / w.toFloat() > 1.5f) -> "9:16"
                h > w -> "3:4"
                else -> "1:1"
            }
        }
        return "1:1"
    }
}

private fun err(code: String, message: String?): String = buildJsonObject {
    put("type", "image_generation")
    put("error", code)
    if (!message.isNullOrBlank()) put("message", message)
}.toString()
