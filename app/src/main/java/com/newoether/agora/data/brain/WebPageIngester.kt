package com.newoether.agora.data.brain

import com.newoether.agora.util.SsrfGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Fetches a web page, strips non-content DOM elements, and extracts
 * clean text for ingestion into the Second Brain. Uses Jsoup (works
 * on Android). Pages rendered by JavaScript (SPAs) return limited text.
 *
 * Anti-SSRF: validates the URL via [SsrfGuard] before fetching.
 */
object WebPageIngester {

    data class WebPageContent(
        val title: String,
        val text: String,
        val url: String,
    )

    suspend fun fetch(url: String): WebPageContent? = withContext(Dispatchers.IO) {
        if (!SsrfGuard.isSafeUrl(url)) return@withContext null
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .timeout(15_000)
                .maxBodySize(5_000_000)
                .get()

            doc.select("script, style, nav, footer, header, aside, noscript, iframe").remove()
            val title = doc.title().ifBlank { url }
            val text = doc.body().text().take(50_000)

            if (text.isBlank()) null else WebPageContent(title, text, url)
        } catch (_: Exception) {
            null
        }
    }
}
