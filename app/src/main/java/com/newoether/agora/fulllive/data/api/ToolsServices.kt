package com.newoether.agora.fulllive.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ToolsServices {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getCurrentDateTime(): String {
        val now = Date()
        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH)
        val timeFormat = SimpleDateFormat("HH'h'mm", Locale.FRENCH)
        val fullIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(now)

        return JSONObject().apply {
            put("date", dateFormat.format(now).replaceFirstChar { it.uppercase() })
            put("heure", timeFormat.format(now))
            put("iso", fullIso)
        }.toString()
    }

    suspend fun getWeather(location: String): String = withContext(Dispatchers.IO) {
        try {
            val sanitized = if (location.isBlank()) "Paris" else location.trim()
            val url = "https://wttr.in/${java.net.URLEncoder.encode(sanitized, "UTF-8")}?format=j1&lang=fr"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "{\"erreur\": \"Météo temporairement indisponible (code ${response.code})\"}"
            }
            val body = response.body?.string() ?: return@withContext "{\"erreur\": \"Réponse vide\"}"
            val root = JSONObject(body)
            val current = root.optJSONArray("current_condition")?.optJSONObject(0)
            val area = root.optJSONArray("nearest_area")?.optJSONObject(0)
            val weatherDays = root.optJSONArray("weather")
            val today = weatherDays?.optJSONObject(0)

            val areaName = area?.optJSONArray("areaName")?.optJSONObject(0)?.optString("value") ?: sanitized
            val tempC = current?.optString("temp_C", "-") ?: "-"
            val feelsLikeC = current?.optString("FeelsLikeC", "-") ?: "-"
            val desc = current?.optJSONArray("lang_fr")?.optJSONObject(0)?.optString("value")
                ?: current?.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value") ?: "Clair"
            val humidity = current?.optString("humidity", "-") ?: "-"
            val wind = current?.optString("windspeedKmph", "-") ?: "-"

            val minTemp = today?.optString("mintempC", "-") ?: "-"
            val maxTemp = today?.optString("maxtempC", "-") ?: "-"

            JSONObject().apply {
                put("lieu", areaName)
                put("temperature", "${tempC}°C (ressenti ${feelsLikeC}°C)")
                put("conditions", desc)
                put("humidite", "${humidity}%")
                put("vent", "${wind} km/h")
                put("minAujourdhui", "${minTemp}°C")
                put("maxAujourdhui", "${maxTemp}°C")
            }.toString()
        } catch (e: Exception) {
            "{\"erreur\": \"Impossible de joindre le service météo: ${e.message ?: "Erreur réseau"}\"}"
        }
    }

    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@withContext "Aucune requête fournie."
            val url = "https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(cleanQuery, "UTF-8")}&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "Recherche non disponible pour l'instant."
            }
            val body = response.body?.string() ?: return@withContext "Aucun résultat."
            val json = JSONObject(body)
            val abstractText = json.optString("AbstractText", "")
            val answer = json.optString("Answer", "")
            val heading = json.optString("Heading", "")

            if (abstractText.isNotBlank()) {
                return@withContext "$heading: $abstractText"
            } else if (answer.isNotBlank()) {
                return@withContext answer
            } else {
                val related = json.optJSONArray("RelatedTopics")
                if (related != null && related.length() > 0) {
                    val first = related.optJSONObject(0)
                    val text = first?.optString("Text", "") ?: ""
                    if (text.isNotBlank()) return@withContext text
                }
                return@withContext "Résultats trouvés pour '$cleanQuery'."
            }
        } catch (e: Exception) {
            "Information pour '$query' momentanément indisponible."
        }
    }
}
