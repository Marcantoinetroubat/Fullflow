package com.newoether.agora.wand

import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

import com.newoether.agora.util.Constants

/**
 * Magic Wand compiler: turns raw dictated text into a single optimized prompt.
 * Uses the user's configured provider (Gemini, OpenAI, OpenRouter, Groq, ZenMux, etc.).
 * Any failure returns null so the caller keeps the original text.
 */
class WandCompiler(private val settings: SettingsRepository) {

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val FALLBACK_MODEL = "z-ai/glm-5.3-flash"
        const val DEFAULT_TTS_MODEL = "qwen/qwen-audio-3.0-tts-plus"
        const val TIMEOUT_MS = 12_000L
        const val COST_GUARD_USD = 0.05
        private const val GLM_INPUT_PER_M = 0.15
        private const val GLM_OUTPUT_PER_M = 0.50
        private const val CHARS_PER_TOKEN = 4
        private const val ESTIMATED_OUTPUT_TOKENS = 800
        private const val TAG = "WandCompiler"
    }

    data class CompiledPrompt(
        val optimized: String,
        val ambiguities: List<String>,
        val costUsd: Double,
        val modelUsed: String,
    )

    data class WandProviderAccess(
        val providerName: String,
        val apiKey: String,
        val baseUrl: String,
        val isGoogle: Boolean,
    )

    suspend fun compile(
        rawText: String,
        structure: PromptStructure,
        skillContents: List<String>,
        personaBrief: String,
        connectionContext: String = "",
        modelOverride: String? = null,
        providerOverride: String? = null,
        customMagicPrompt: String? = null,
    ): CompiledPrompt? = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) return@withContext null
        val access = resolveProviderAccess(providerOverride, modelOverride) ?: return@withContext null
        val model = resolveTargetModel(access, modelOverride)

        val systemPrompt = buildSystemPrompt(personaBrief, customMagicPrompt.orEmpty())
        val userPrompt = buildUserPrompt(rawText, structure, skillContents, connectionContext)

        val content = if (access.isGoogle) {
            compileWithGoogle(access.apiKey, model, systemPrompt, userPrompt)
        } else {
            compileWithOpenAiCompatible(access.baseUrl, access.apiKey, model, systemPrompt, userPrompt)
        } ?: return@withContext null

        val (optimized, ambiguities) = parseOutput(content)
        if (optimized.isBlank()) return@withContext null

        val estimatedInputTokens = ceil(
            (systemPrompt.length + userPrompt.length).toDouble() / CHARS_PER_TOKEN,
        ).toInt()
        val costUsd =
            estimatedInputTokens * GLM_INPUT_PER_M / 1_000_000.0 +
                ESTIMATED_OUTPUT_TOKENS * GLM_OUTPUT_PER_M / 1_000_000.0
        if (costUsd > COST_GUARD_USD) {
            DebugLog.e(TAG, "Wand compile cost guard hit: $costUsd USD")
            return@withContext null
        }
        DebugLog.d(
            TAG,
            "Wand compiled in $costUsd USD via ${access.providerName}:$model (in≈$estimatedInputTokens, out≈$ESTIMATED_OUTPUT_TOKENS)",
        )
        CompiledPrompt(
            optimized = optimized,
            ambiguities = ambiguities,
            costUsd = costUsd,
            modelUsed = model,
        )
    }

    private suspend fun compileWithGoogle(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): String? {
        val cleanModel = model.removePrefix("google:").removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userPrompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", ESTIMATED_OUTPUT_TOKENS)
            })
        }.toString()

        return try {
            withTimeout(TIMEOUT_MS) {
                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = OkHttpClient.Builder()
                    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        DebugLog.e(TAG, "Wand Google HTTP ${resp.code}")
                        return@withTimeout null
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Wand compile with Google failed", e)
            null
        }
    }

    private suspend fun compileWithOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): String? {
        val cleanModel = if (model.contains(":")) model.substringAfter(":") else model
        val body = JSONObject().apply {
            put("model", cleanModel)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("max_tokens", ESTIMATED_OUTPUT_TOKENS)
            put("temperature", 0.2)
        }.toString()

        return try {
            withTimeout(TIMEOUT_MS) {
                val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = OkHttpClient.Builder()
                    .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        DebugLog.e(TAG, "Wand OpenAI HTTP ${resp.code}")
                        return@withTimeout null
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Wand compile with OpenAI-compatible endpoint failed", e)
            null
        }
    }

    private suspend fun resolveProviderAccess(requestedProvider: String?, requestedModel: String?): WandProviderAccess? {
        settings.awaitInitialLoad()

        val candidateProviders = mutableListOf<String>()
        if (!requestedProvider.isNullOrBlank()) {
            candidateProviders.add(requestedProvider)
        }
        if (!requestedModel.isNullOrBlank() && requestedModel.contains(":")) {
            candidateProviders.add(requestedModel.substringBefore(":"))
        }
        // Fournisseurs configurés avec clés actives
        settings.customProviders.value.forEach { candidateProviders.add(it.name) }
        settings.apiKeys.value.filter { it.key.isNotBlank() }.forEach { candidateProviders.add(it.provider) }
        // Replis standards
        candidateProviders.addAll(listOf(Constants.PROVIDER_GOOGLE, "openrouter", "openai", "deepseek", "groq", "anthropic", "ZENMUX"))

        val checked = mutableSetOf<String>()
        for (providerName in candidateProviders) {
            val normalized = providerName.trim()
            if (normalized.isBlank() || !checked.add(normalized.lowercase())) continue

            val key = settings.awaitActiveKey(normalized)
                ?: settings.resolveActiveKey(normalized)
                ?: settings.apiKeys.value.firstOrNull { it.provider.equals(normalized, ignoreCase = true) }?.key

            if (!key.isNullOrBlank()) {
                val isGoogle = normalized.equals(Constants.PROVIDER_GOOGLE, ignoreCase = true) ||
                    normalized.equals("gemini", ignoreCase = true)
                val baseUrl = if (isGoogle) {
                    "https://generativelanguage.googleapis.com/v1beta"
                } else {
                    val custom = settings.customProviders.value.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                    (settings.providerBaseUrls.value[normalized]
                        ?: settings.providerBaseUrls.value.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }?.value
                        ?: custom?.let { settings.providerBaseUrls.value[it.name] }
                        ?: if (normalized.equals("ZENMUX", ignoreCase = true)) "https://zenmux.ai/api/v1"
                           else if (normalized.equals("openrouter", ignoreCase = true)) "https://openrouter.ai/api/v1"
                           else "https://api.openai.com/v1").trimEnd('/')
                }
                return WandProviderAccess(normalized, key, baseUrl, isGoogle)
            }
        }

        return null
    }

    private fun resolveTargetModel(access: WandProviderAccess, modelOverride: String?): String {
        if (!modelOverride.isNullOrBlank()) {
            return if (modelOverride.contains(":")) modelOverride.substringAfter(":") else modelOverride
        }
        return if (access.isGoogle) "gemini-2.5-flash" else DEFAULT_MODEL
    }

    /** SYSTEM: compiler role + absolute fidelity rule + persona brief. */
    internal fun buildSystemPrompt(personaBrief: String, customMagicPrompt: String = ""): String =
        WandPrompts.buildSystemPrompt(personaBrief, customMagicPrompt)

    /** USER: structure template + skills + raw dictated text (§4.7). */
    internal fun buildUserPrompt(
        rawText: String,
        structure: PromptStructure,
        skillContents: List<String>,
        connectionContext: String = "",
    ): String = WandPrompts.buildUserPrompt(rawText, structure, skillContents, connectionContext)

    /** Splits the model output into the optimized prompt and the ambiguity list. */
    internal fun parseOutput(content: String): Pair<String, List<String>> =
        WandPrompts.parseOutput(content)
}

/** Pure prompt-building and output-parsing helpers — JVM-testable, no Android. */
object WandPrompts {

    const val DEFAULT_MAGIC_PROMPT =
        "Tu es un expert en ingénierie de prompt et compilateur de requêtes. " +
        "Ta mission est de transformer la requête brute de l'utilisateur en UN SEUL prompt optimisé, " +
        "riche, précis et parfaitement structuré, fidèle à son intention.\n" +
        "Règle de fidélité absolue : ne déforme jamais le sens, n'ajoute aucune information inventée, " +
        "ne tranche aucune ambiguïté — signale-les."

    fun buildSystemPrompt(personaBrief: String, customMagicPrompt: String = ""): String = buildString {
        val base = customMagicPrompt.trim().ifBlank { DEFAULT_MAGIC_PROMPT }
        appendLine(base)
        if (personaBrief.isNotBlank()) {
            appendLine()
            appendLine("BRIEF PERSONA :")
            appendLine(personaBrief.trim())
        }
    }

    /** USER: structure template + skills + raw dictated text (§4.7). */
    internal fun buildUserPrompt(
        rawText: String,
        structure: PromptStructure,
        skillContents: List<String>,
        connectionContext: String = "",
    ): String = buildString {
        appendLine("STRUCTURE À APPLIQUER (${structure.type.label}) :")
        appendLine(structure.template.trim())
        val skills = skillContents.filter { it.isNotBlank() }
        if (skills.isNotEmpty()) {
            appendLine()
            appendLine("COMPÉTENCES À MOBILISER :")
            skills.forEach { content ->
                appendLine("---")
                appendLine(content.trim())
            }
        }
        if (connectionContext.isNotBlank()) {
            appendLine()
            appendLine("CONTEXTE RÉCUPÉRÉ VIA LES CONNEXIONS :")
            appendLine(connectionContext.trim())
        }
        appendLine()
        appendLine("TEXTE BRUT DE L'UTILISATEUR :")
        appendLine(rawText.trim())
        appendLine()
        appendLine(
            "Sortie attendue : d'abord UN SEUL prompt optimisé (aucune introduction, " +
                "aucune explication), puis une ligne commençant exactement par « AMBIGUITÉS: » " +
                "suivie des ambiguïtés séparées par « ; », ou « AMBIGUITÉS: aucune ».",
        )
    }

    /** Splits the model output into the optimized prompt and the ambiguity list. */
    internal     fun parseOutput(content: String): Pair<String, List<String>> {
        // Accent-tolerant marker: matches AMBIGUITES / AMBIGUITÉS / ambiguïtés, any case.
        val marker = Regex("(?iu)ambigu.{1,3}t.{1,3}s\\s*:")
        val match = marker.find(content)
        if (match == null) return content.trim() to emptyList()
        val optimized = content.substring(0, match.range.first).trim()
        val rawAmbiguities = content.substring(match.range.last + 1).trim()
        val ambiguities = rawAmbiguities
            .split(';', '；')
            .map { it.trim().trimStart('-', '•').trim() }
            .filter { it.isNotBlank() && !it.equals("aucune", ignoreCase = true) }
        return optimized to ambiguities
    }
}
