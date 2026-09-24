package com.newoether.agora.fulllive.data.api

import android.graphics.Bitmap
import android.util.Base64
import com.newoether.agora.fulllive.data.local.SecurityVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class AiClientCoordinator(
    private val securityVault: SecurityVault,
    private val toolsServices: ToolsServices
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    // Determine active API key
    fun getEffectiveGeminiKey(): String {
        return securityVault.getGeminiKey()
    }

    fun getEffectiveOpenAiKey(): String = securityVault.getOpenAiKey()
    fun getEffectiveXaiKey(): String = securityVault.getXaiKey()

    /**
     * Executes a chat turn with the appropriate AI provider (Gemini, OpenAI, xAI Grok).
     * Handles tool calls (get_current_datetime, get_weather, web_search) seamlessly.
     */
    suspend fun executeConversationTurn(
        model: String,
        systemInstruction: String,
        conversationHistory: List<Pair<String, String>>, // sender ("user"/"ai"), text
        userMessage: String,
        imageBitmap: Bitmap? = null
    ): AIResponse = withContext(Dispatchers.IO) {
        val effectiveModel = if (model.isBlank()) securityVault.getDefaultModel() else model

        if (effectiveModel == "openai-custom") {
            return@withContext callOpenAiCompatible(
                baseUrl = securityVault.getCustomOpenAiBaseUrl(),
                apiKey = securityVault.getCustomOpenAiKey(),
                model = securityVault.getCustomOpenAiModelId(),
                systemInstruction = systemInstruction,
                history = conversationHistory,
                userMessage = userMessage,
                imageBitmap = imageBitmap
            )
        } else if (effectiveModel.startsWith("gemini")) {
            return@withContext callGemini(effectiveModel, systemInstruction, conversationHistory, userMessage, imageBitmap)
        } else if (effectiveModel.startsWith("grok")) {
            return@withContext callOpenAiCompatible(
                baseUrl = "https://api.x.ai/v1/chat/completions",
                apiKey = getEffectiveXaiKey(),
                model = effectiveModel,
                systemInstruction = systemInstruction,
                history = conversationHistory,
                userMessage = userMessage,
                imageBitmap = imageBitmap
            )
        } else {
            // OpenAI default
            return@withContext callOpenAiCompatible(
                baseUrl = "https://api.openai.com/v1/chat/completions",
                apiKey = getEffectiveOpenAiKey(),
                model = if (effectiveModel.contains("realtime") || effectiveModel.contains("live")) "gpt-4o-mini" else effectiveModel,
                systemInstruction = systemInstruction,
                history = conversationHistory,
                userMessage = userMessage,
                imageBitmap = imageBitmap
            )
        }
    }

    private suspend fun callGemini(
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        imageBitmap: Bitmap?
    ): AIResponse {
        val apiKey = getEffectiveGeminiKey()
        if (apiKey.isBlank()) {
            return AIResponse(
                text = "Clé API Gemini non configurée. Rendez-vous dans la Configuration pour renseigner votre clé API.",
                inputTokens = 0,
                outputTokens = 0,
                isError = true
            )
        }

        val resolvedModel = when {
            model.contains("thinking") -> "gemini-3.1-pro-preview"
            model.contains("flash") -> "gemini-3.5-flash"
            else -> "gemini-3.5-flash"
        }

        val contentsArray = JSONArray()

        // Inject past history
        history.takeLast(10).forEach { (sender, text) ->
            val role = if (sender == "user") "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", text))
                })
            })
        }

        // Current turn parts
        val userParts = JSONArray()
        userParts.put(JSONObject().put("text", userMessage))

        if (imageBitmap != null) {
            val stream = ByteArrayOutputStream()
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            userParts.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", b64)
                })
            })
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", userParts)
        })

        // Tools declaration
        val toolsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("function_declarations", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "get_current_datetime")
                        put("description", "Obtenir la date et l'heure actuelles précises.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "get_weather")
                        put("description", "Obtenir la météo actuelle et prévisions pour un lieu.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("location", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Ville ou lieu")
                                })
                            })
                            put("required", JSONArray().apply { put("location") })
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "web_search")
                        put("description", "Effectuer une recherche sur le web.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Terme ou sujet de recherche")
                                })
                            })
                            put("required", JSONArray().apply { put("query") })
                        })
                    })
                })
            })
        }

        val requestJson = JSONObject().apply {
            put("contents", contentsArray)
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply { put(JSONObject().put("text", systemInstruction)) })
            })
            put("tools", toolsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1200)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return AIResponse("Erreur Gemini (${response.code}): $responseBody", 0, 0, isError = true)
            }

            val json = JSONObject(responseBody)
            val usage = json.optJSONObject("usageMetadata")
            var inTokens = usage?.optLong("promptTokenCount") ?: 0L
            var outTokens = usage?.optLong("candidatesTokenCount") ?: 0L

            val candidates = json.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")

            // Check if function call requested
            var functionCallName: String? = null
            var functionCallArgs: JSONObject? = null
            var textResult = ""

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i)
                    if (p != null) {
                        if (p.has("functionCall")) {
                            val fc = p.getJSONObject("functionCall")
                            functionCallName = fc.optString("name")
                            functionCallArgs = fc.optJSONObject("args")
                        } else if (p.has("text")) {
                            textResult += p.optString("text")
                        }
                    }
                }
            }

            // Handle function call if triggered
            if (functionCallName != null) {
                val toolResult = when (functionCallName) {
                    "get_current_datetime" -> toolsServices.getCurrentDateTime()
                    "get_weather" -> toolsServices.getWeather(functionCallArgs?.optString("location") ?: "Paris")
                    "web_search" -> toolsServices.webSearch(functionCallArgs?.optString("query") ?: "")
                    else -> "{}"
                }

                // Append model's function call & user's function response, then re-call
                contentsArray.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionCall", JSONObject().apply {
                                put("name", functionCallName)
                                put("args", functionCallArgs ?: JSONObject())
                            })
                        })
                    })
                })

                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", functionCallName)
                                put("response", JSONObject(toolResult))
                            })
                        })
                    })
                })

                val followUpRequest = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val followUpResp = httpClient.newCall(followUpRequest).execute()
                val followUpBody = followUpResp.body?.string() ?: ""
                val followUpJson = JSONObject(followUpBody)
                val followUpUsage = followUpJson.optJSONObject("usageMetadata")
                inTokens += followUpUsage?.optLong("promptTokenCount") ?: 0L
                outTokens += followUpUsage?.optLong("candidatesTokenCount") ?: 0L

                val followUpParts = followUpJson.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")

                var followUpText = ""
                if (followUpParts != null) {
                    for (i in 0 until followUpParts.length()) {
                        followUpText += followUpParts.optJSONObject(i)?.optString("text") ?: ""
                    }
                }

                return AIResponse(followUpText.ifBlank { "Informations trouvées." }, inTokens, outTokens)
            }

            return AIResponse(textResult.ifBlank { "Je suis à votre écoute." }, inTokens, outTokens)
        } catch (e: Exception) {
            return AIResponse("Erreur de connexion : ${e.message}", 0, 0, isError = true)
        }
    }

    private suspend fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<Pair<String, String>>,
        userMessage: String,
        imageBitmap: Bitmap?
    ): AIResponse {
        if (apiKey.isBlank()) {
            return AIResponse(
                text = "Clé API non configurée pour $model. Ajoutez-la dans la Configuration.",
                inputTokens = 0,
                outputTokens = 0,
                isError = true
            )
        }

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemInstruction)
        })

        history.takeLast(10).forEach { (sender, text) ->
            messagesArray.put(JSONObject().apply {
                put("role", if (sender == "user") "user" else "assistant")
                put("content", text)
            })
        }

        if (imageBitmap != null) {
            val stream = ByteArrayOutputStream()
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", userMessage)
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$b64")
                        })
                    })
                })
            })
        } else {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val reqObj = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 800)
        }

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(reqObj.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return AIResponse("Erreur API (${response.code}): $body", 0, 0, isError = true)
            }
            val json = JSONObject(body)
            val usage = json.optJSONObject("usage")
            val inTokens = usage?.optLong("prompt_tokens") ?: 0L
            val outTokens = usage?.optLong("completion_tokens") ?: 0L

            val choices = json.optJSONArray("choices")
            val reply = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""

            return AIResponse(reply.ifBlank { "Bien reçu." }, inTokens, outTokens)
        } catch (e: Exception) {
            return AIResponse("Erreur : ${e.message}", 0, 0, isError = true)
        }
    }

    /**
     * Cognitive Memorization & Insights extraction at end of session.
     */
    suspend fun extractUserInsights(transcript: String, existingKnowledge: String): List<String> = withContext(Dispatchers.IO) {
        if (transcript.length < 40) return@withContext emptyList()
        val prompt = """Tu es un analyste cognitif expert. Analyse cette transcription de conversation vocale et extrait UNIQUEMENT les faits DURABLES et NOUVEAUX concernant l'utilisateur.

Faits déjà connus (ne pas répéter) :
$existingKnowledge

Règles :
- Extrait uniquement l'identité, les goûts permanents, la vie pro/famille, les projets réels
- N'inclus rien d'éphémère (météo, humeur passagère, questions posées)
- Format : chaque fait sur une ligne commençant par "- "
- Si aucun fait nouveau durable n'est détecté, réponds exactement : AUCUNE
""".trimIndent()

        val response = callGemini(
            model = "gemini-3.5-flash",
            systemInstruction = "Tu es un extracteur d'informations personnelles précises.",
            history = emptyList(),
            userMessage = "Transcription :\n$transcript\n\n$prompt",
            imageBitmap = null
        )

        val text = response.text.trim()
        if (text == "AUCUNE" || !text.contains("- ")) return@withContext emptyList()

        text.split("\n")
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Generate concise 3-5 word summary title for session.
     */
    suspend fun generateSessionTitle(transcript: String): String = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) return@withContext "Discussion libre"
        val prompt = "Donne un titre court de 3 à 5 mots en français résumant cette conversation. Réponds uniquement par le titre, sans guillemets ni ponctuation finale."

        val resp = callGemini(
            model = "gemini-3.5-flash",
            systemInstruction = "Tu es un générateur de titres courts et percutants.",
            history = emptyList(),
            userMessage = "$prompt\n\nTranscription:\n${transcript.take(1500)}",
            imageBitmap = null
        )
        val clean = resp.text.trim().replace("\"", "").replace(".", "").take(50)
        clean.ifBlank { "Discussion libre" }
    }
}

data class AIResponse(
    val text: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val isError: Boolean = false
)
