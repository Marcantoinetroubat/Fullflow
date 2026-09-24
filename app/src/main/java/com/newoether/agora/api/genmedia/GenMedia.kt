package com.newoether.agora.api.genmedia

import android.content.Context
import com.newoether.agora.AgoraApplication
import com.newoether.agora.BuildConfig
import com.newoether.agora.util.Constants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Shared BYOK key resolution for Gemini media calls: runtime override first,
 * build-time key second. One owner instead of one copy per service.
 */
object GenMediaKeys {
    fun resolveApiKey(apiKeyOverride: String?): String = when {
        !apiKeyOverride.isNullOrBlank() -> apiKeyOverride.trim()
        BuildConfig.GEMINI_API_KEY.isNotBlank() -> BuildConfig.GEMINI_API_KEY.trim()
        else -> ""
    }

    suspend fun resolveApiKey(context: Context, apiKeyOverride: String?): String {
        if (!apiKeyOverride.isNullOrBlank()) return apiKeyOverride.trim()
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) return BuildConfig.GEMINI_API_KEY.trim()

        try {
            val app = context.applicationContext as? AgoraApplication
            val container = app?.awaitContainer() ?: app?.requireContainer()
            val settings = container?.settingsRepository
            if (settings != null) {
                val googleKey = settings.awaitActiveKey(Constants.PROVIDER_GOOGLE)
                    ?: settings.awaitActiveKey("google")
                    ?: settings.awaitActiveKey("Gemini")
                if (!googleKey.isNullOrBlank()) return googleKey.trim()

                val allKeys = settings.apiKeys.value
                val matchingKey = allKeys.find { 
                    it.provider.equals(Constants.PROVIDER_GOOGLE, true) ||
                    it.provider.equals("google", true) ||
                    it.provider.equals("gemini", true)
                }?.key ?: allKeys.firstOrNull()?.key
                if (!matchingKey.isNullOrBlank()) return matchingKey.trim()
            }
        } catch (_: Exception) {
        }

        return resolveApiKey(null)
    }

    fun resolveApiKeyBlocking(context: Context, apiKeyOverride: String?): String {
        if (!apiKeyOverride.isNullOrBlank()) return apiKeyOverride.trim()
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) return BuildConfig.GEMINI_API_KEY.trim()

        try {
            val app = context.applicationContext as? AgoraApplication
            val container = try { app?.requireContainer() } catch (_: Exception) { null }
            val settings = container?.settingsRepository
            if (settings != null) {
                val googleKey = settings.resolveActiveKey(Constants.PROVIDER_GOOGLE)
                    ?: settings.resolveActiveKey("google")
                    ?: settings.resolveActiveKey("Gemini")
                if (!googleKey.isNullOrBlank()) return googleKey.trim()

                val allKeys = settings.apiKeys.value
                val matchingKey = allKeys.find { 
                    it.provider.equals(Constants.PROVIDER_GOOGLE, true) ||
                    it.provider.equals("google", true) ||
                    it.provider.equals("gemini", true)
                }?.key ?: allKeys.firstOrNull()?.key
                if (!matchingKey.isNullOrBlank()) return matchingKey.trim()
            }
        } catch (_: Exception) {
        }

        return resolveApiKey(null)
    }
}

data class GenMediaResponse(val code: Int, val body: String, val successful: Boolean)

/**
 * Single shared HTTP seam for Gemini media calls (image, video, cloud TTS).
 * Key travels in the `x-goog-api-key` header only — never in the URL query.
 * Text responses are materialized and closed here; binary streaming stays with
 * the caller via the shared [client].
 */
object GenMediaHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun postJson(
        url: String,
        apiKey: String,
        bodyJson: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): GenMediaResponse {
        val builder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            if (url.contains("googleapis.com")) {
                builder.addHeader("x-goog-api-key", apiKey)
            } else {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
        extraHeaders.forEach { (key, value) -> builder.addHeader(key, value) }
        val request = builder.post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            return GenMediaResponse(
                code = response.code,
                body = response.body?.string().orEmpty(),
                successful = response.isSuccessful,
            )
        }
    }

    fun postJsonBytes(
        url: String,
        apiKey: String,
        bodyJson: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ByteArray? {
        val builder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            if (url.contains("googleapis.com")) {
                builder.addHeader("x-goog-api-key", apiKey)
            } else {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
        extraHeaders.forEach { (key, value) -> builder.addHeader(key, value) }
        val request = builder.post(bodyJson.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    fun get(url: String, apiKey: String): GenMediaResponse {
        val builder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            if (url.contains("googleapis.com")) {
                builder.addHeader("x-goog-api-key", apiKey)
            } else {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
        val request = builder
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            return GenMediaResponse(
                code = response.code,
                body = response.body?.string().orEmpty(),
                successful = response.isSuccessful,
            )
        }
    }
}
