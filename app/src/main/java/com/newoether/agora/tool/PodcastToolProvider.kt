package com.newoether.agora.tool

import android.app.Application
import com.newoether.agora.api.DuckDuckGoScraper
import com.newoether.agora.api.ToolDefinition
import com.newoether.agora.api.ToolFunction
import com.newoether.agora.api.ToolParameters
import com.newoether.agora.api.ToolProperty
import com.newoether.agora.model.ToolImageAttachment
import com.newoether.agora.podcast.PersonalPodcasterPipeline
import com.newoether.agora.podcast.PodcastResult
import com.newoether.agora.ui.chat.audio.FullFlowTtsEngine
import com.newoether.agora.ui.chat.audio.TtsEngineMode
import com.newoether.agora.viewmodel.GenerationContext
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
import java.io.File

class PodcastToolProvider(private val app: Application) : ToolProvider {

    private val ttsEngine by lazy { FullFlowTtsEngine(app) }
    private val pipeline by lazy { PersonalPodcasterPipeline(app, ttsEngine) }
    private val webSearchProvider by lazy { WebSearchToolProvider() }

    override fun definitions(ctx: GenerationContext): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                function = ToolFunction(
                    name = "generate_podcast",
                    description = "Generate an engaging audio podcast covering the latest updates on any given topic and timeframe. Synthesizes a research report, writes an audio script, records multi-provider audio speech, and produces an interactive summary player webpage.",
                    parameters = ToolParameters(
                        properties = mapOf(
                            "topic" to ToolProperty(
                                type = "string",
                                description = "The topic or theme for the podcast (e.g. 'Intelligence Artificielle', 'Conquête spatiale', 'Énergie verte').",
                            ),
                            "timeframe" to ToolProperty(
                                type = "string",
                                description = "The timeframe for updates, e.g. 'cette semaine', 'ce mois-ci', 'dernières 24h', 'récent'.",
                            ),
                            "tts_provider" to ToolProperty(
                                type = "string",
                                description = "Optional TTS provider: 'gemini' (Gemini Cloud 3.1), 'openai' (OpenAI TTS-1), 'kokoro' (On-device offline), or 'system'.",
                            ),
                            "voice" to ToolProperty(
                                type = "string",
                                description = "Optional voice name (e.g. 'Kore', 'alloy', 'echo', 'af_heart').",
                            ),
                        ),
                        required = listOf("topic"),
                    ),
                ),
            ),
        )
    }

    override fun handles(name: String): Boolean = name == "generate_podcast"

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
        emit(ToolExecutionEvent.TargetResolved(target = "podcast_generation:pipeline"))
        emit(ToolExecutionEvent.Progress(message = "Lancement du pipeline Personal Podcaster..."))

        val argsStr = arguments.ifBlank { "{}" }
        val args = try {
            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(argsStr)
        } catch (_: Exception) { emptyMap() }

        val topic = (args["topic"] as? JsonPrimitive)?.content ?: "Actualités"
        val timeframe = (args["timeframe"] as? JsonPrimitive)?.content ?: "cette semaine"
        val ttsProviderStr = (args["tts_provider"] as? JsonPrimitive)?.content?.lowercase()
        val voice = (args["voice"] as? JsonPrimitive)?.content

        val ttsMode = when (ttsProviderStr) {
            "openai" -> TtsEngineMode.OPENAI_CLOUD
            "kokoro" -> TtsEngineMode.KOKORO_LOCAL
            "system" -> TtsEngineMode.SYSTEM
            "gemini" -> TtsEngineMode.GEMINI_CLOUD
            else -> ttsEngine.ttsEngineMode
        }

        val result = pipeline.execute(
            topic = topic,
            timeframe = timeframe,
            ttsMode = ttsMode,
            voice = voice,
            ttsModel = podcastTtsModelSetting(),
            searchExecutor = buildSearchExecutor(ctx),
            onProgress = { progress ->
                emit(ToolExecutionEvent.Progress(message = "[${progress.step}/4] ${progress.detailMessage}"))
            },
        )

        emit(ToolExecutionEvent.Completed(buildResult(result)))
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

        val topic = (args["topic"] as? JsonPrimitive)?.content ?: "Actualités"
        val timeframe = (args["timeframe"] as? JsonPrimitive)?.content ?: "cette semaine"
        val ttsProviderStr = (args["tts_provider"] as? JsonPrimitive)?.content?.lowercase()
        val voice = (args["voice"] as? JsonPrimitive)?.content

        val ttsMode = when (ttsProviderStr) {
            "openai" -> TtsEngineMode.OPENAI_CLOUD
            "kokoro" -> TtsEngineMode.KOKORO_LOCAL
            "system" -> TtsEngineMode.SYSTEM
            "gemini" -> TtsEngineMode.GEMINI_CLOUD
            else -> ttsEngine.ttsEngineMode
        }

        val result = pipeline.execute(
            topic = topic,
            timeframe = timeframe,
            ttsMode = ttsMode,
            voice = voice,
            ttsModel = podcastTtsModelSetting(),
            searchExecutor = buildSearchExecutor(ctx),
        )

        buildResult(result)
    }

    /** User-selected TTS model for the podcast voice (synced model id), null = engine default. */
    private fun podcastTtsModelSetting(): String? = runCatching {
        (app as? com.newoether.agora.AgoraApplication)
            ?.requireContainer()
            ?.settingsRepository
            ?.podcastGenTtsModel
            ?.value
    }.getOrNull()

    /**
     * Routes podcast research through the configured web search provider (with the
     * multi-backend fallback chain) when web search is enabled; null keeps the
     * pipeline's built-in DuckDuckGo behavior.
     */
    private fun buildSearchExecutor(
        ctx: GenerationContext,
    ): (suspend (String, Int) -> List<DuckDuckGoScraper.WebResult>)? {
        if (!ctx.webSearchEnabled) return null
        return { query, maxResults ->
            runCatching {
                val argsJson = buildJsonObject {
                    put("query", query)
                    put("num_results", maxResults)
                }.toString()
                val resultJson = webSearchProvider.execute("web_search", argsJson, ctx)
                val root = Json.parseToJsonElement(resultJson).jsonObject
                root["results"]?.jsonArray?.mapNotNull { element ->
                    val obj = element.jsonObject
                    val title = (obj["title"] as? JsonPrimitive)?.content.orEmpty()
                    val url = (obj["url"] as? JsonPrimitive)?.content.orEmpty()
                    val snippet = (obj["description"] as? JsonPrimitive)?.content
                        ?: (obj["content"] as? JsonPrimitive)?.content.orEmpty()
                    if (title.isBlank() && url.isBlank()) {
                        null
                    } else {
                        DuckDuckGoScraper.WebResult(title = title, url = url, snippet = snippet)
                    }
                }.orEmpty()
            }.getOrDefault(emptyList())
        }
    }

    private fun buildResult(result: PodcastResult): ToolExecutionResult {
        val jsonOutput = buildJsonObject {
            put("topic", result.topic)
            put("timeframe", result.timeframe)
            put("audio_path", result.audioFile?.absolutePath.orEmpty())
            put("html_path", result.htmlFile?.absolutePath.orEmpty())
            put("duration_ms", result.audioDurationMs)
            put("script", result.script)
            put("research_report", result.researchReport)
        }.toString()

        val textSummary = buildString {
            appendLine("🎙️ **Podcast Généré avec Succès**")
            appendLine("- **Sujet** : ${result.topic}")
            appendLine("- **Période** : ${result.timeframe}")
            val totalSecs = (result.audioDurationMs / 1000).toInt()
            appendLine(String.format("- **Durée** : %02d:%02d", totalSecs / 60, totalSecs % 60))
            if (result.audioFile != null) {
                appendLine("- **Fichier Audio** : ${result.audioFile.name}")
            }
            if (result.htmlFile != null) {
                appendLine("- **Page Web Résumé** : ${result.htmlFile.name}")
            }
            appendLine()
            appendLine("### Script du Podcast")
            appendLine(result.script)
        }

        val audioAttachments = if (result.audioFile != null && result.audioFile.exists()) {
            listOf(
                ToolImageAttachment(
                    path = result.audioFile.absolutePath,
                    mimeType = if (result.audioFile.extension == "mp3") "audio/mp3" else "audio/wav",
                    sizeBytes = result.audioFile.length(),
                    sha256 = "podcast_${result.audioFile.name}",
                ),
            )
        } else {
            emptyList()
        }

        return ToolExecutionResult(
            text = textSummary,
            images = audioAttachments,
            isError = false,
        )
    }
}
