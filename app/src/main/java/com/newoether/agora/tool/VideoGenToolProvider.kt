package com.newoether.agora.tool

import android.app.Application
import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolFunction
import com.newoether.agora.api.ToolParameters
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.studio.video.GeminiVideoService
import com.newoether.agora.studio.video.StudioVideoAspectRatio
import com.newoether.agora.studio.video.StudioVideoDuration
import com.newoether.agora.studio.video.StudioVideoModel
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VideoGenToolProvider(private val app: Application) : ToolProvider {

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        if (!ctx.videoGenEnabled) return emptyList()
        return listOf(
            ToolDefinition(
                function = ToolFunction(
                    name = "generate_video",
                    description = "Generate a video from a text prompt. The generated video is saved and made available for playback to the user automatically. Use this whenever the user asks to create, animate, film, or generate a video clip.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "prompt" to ToolProperty("string", "A detailed description of the video scene, camera motion, and action to generate."),
                            "aspect_ratio" to ToolProperty("string", "Optional aspect ratio: '16:9' (landscape), '9:16' (vertical/portrait), or '1:1' (square)."),
                            "duration_seconds" to ToolProperty("integer", "Optional duration in seconds (5 or 10).")
                        ),
                        required = listOf("prompt")
                    )
                )
            )
        )
    }

    override fun handles(name: String): Boolean = name == "generate_video"

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
        emit(ToolExecutionEvent.TargetResolved(target = "video_generation:veo"))
        emit(ToolExecutionEvent.Progress(message = "Génération de la vidéo cinématique en cours..."))
        emit(ToolExecutionEvent.Completed(executeResult(name, arguments, ctx)))
    }

    private suspend fun executeResult(
        name: String,
        arguments: String,
        ctx: GenerationContext,
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val argsStr = arguments.ifBlank { "{}" }
        val args = try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        } catch (_: Exception) { emptyMap() }

        val prompt = (args["prompt"] as? JsonPrimitive)?.content
        if (prompt.isNullOrBlank()) {
            return@withContext ToolExecutionResult(
                text = err("no_prompt", "Description de la vidéo manquante."),
                isError = true
            )
        }

        val ratioStr = (args["aspect_ratio"] as? JsonPrimitive)?.content ?: "16:9"
        val ratio = when (ratioStr) {
            "9:16" -> StudioVideoAspectRatio.PORTRAIT
            "1:1" -> StudioVideoAspectRatio.SQUARE
            else -> StudioVideoAspectRatio.LANDSCAPE
        }

        val durInt = (args["duration_seconds"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 5
        val duration = if (durInt >= 10) StudioVideoDuration.SEC_10 else StudioVideoDuration.SEC_5

        try {
            val video = GeminiVideoService.instance.generateVideo(
                context = app,
                prompt = prompt,
                model = StudioVideoModel.DEFAULT,
                aspectRatio = ratio,
                duration = duration,
                apiKeyOverride = null,
                useSettingsModelOverride = true,
            )

            val jsonResult = buildJsonObject {
                put("type", "video_generation")
                put("status", "ok")
                put("file_path", video.filePath)
                put("aspect_ratio", ratio.apiValue)
                put("duration", duration.label)
            }.toString()

            val file = java.io.File(video.filePath)
            val fileSize = if (file.exists()) file.length() else 0L
            val dummySha256 = "video_${video.id}"
            ToolExecutionResult(
                text = jsonResult,
                images = listOf(
                    com.newoether.agora.model.ToolImageAttachment(
                        path = video.filePath,
                        mimeType = "video/mp4",
                        sizeBytes = fileSize,
                        sha256 = dummySha256,
                    )
                ),
                displayText = "Vidéo générée avec succès : ${video.prompt}",
                isError = false
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                text = err("generation_failed", e.message),
                isError = true
            )
        }
    }

    private fun err(code: String, message: String?): String = buildJsonObject {
        put("type", "video_generation")
        put("error", code)
        if (!message.isNullOrBlank()) put("message", message)
    }.toString()
}
