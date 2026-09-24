package com.newoether.agora.studio.podcast

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates a valid RSS 2.0 podcast feed compatible with Spotify, Apple Podcasts, and Deezer.
 * The feed includes iTunes extensions for podcast metadata.
 */
object PodcastRssGenerator {

    data class PodcastEpisode(
        val title: String,
        val description: String,
        val audioUrl: String,
        val audioLengthBytes: Long,
        val audioMimeType: String = "audio/mpeg",
        val publishedAt: Long = System.currentTimeMillis(),
        val durationSeconds: Int = 0,
        val episodeNumber: Int = 1,
    )

    data class PodcastFeed(
        val title: String,
        val description: String,
        val link: String,
        val author: String,
        val email: String = "",
        val language: String = "fr",
        val category: String = "Technology",
        val imageUrl: String = "",
        val copyright: String = "",
        val episodes: List<PodcastEpisode>,
    )

    fun generate(feed: PodcastFeed): String {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")

        val items = feed.episodes.joinToString("\n") { ep ->
            """    <item>
      <title>${escapeXml(ep.title)}</title>
      <description>${escapeXml(ep.description)}</description>
      <link>${feed.link}</link>
      <guid isPermaLink="false">${feed.title.lowercase().replace(" ", "-")}-ep-${ep.episodeNumber}</guid>
      <pubDate>${dateFormat.format(Date(ep.publishedAt))}</pubDate>
      <enclosure url="${ep.audioUrl}" length="${ep.audioLengthBytes}" type="${ep.audioMimeType}" />
      <itunes:duration>${formatDuration(ep.durationSeconds)}</itunes:duration>
      <itunes:author>${escapeXml(feed.author)}</itunes:author>
      <itunes:summary>${escapeXml(ep.description)}</itunes:summary>
      <itunes:explicit>false</itunes:explicit>
    </item>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">
  <channel>
    <title>${escapeXml(feed.title)}</title>
    <description>${escapeXml(feed.description)}</description>
    <link>${feed.link}</link>
    <language>${feed.language}</language>
    <copyright>${escapeXml(feed.copyright.ifBlank { feed.author })}</copyright>
    <itunes:author>${escapeXml(feed.author)}</itunes:author>
    <itunes:summary>${escapeXml(feed.description)}</itunes:summary>
    <itunes:category text="${escapeXml(feed.category)}" />
    <itunes:image href="${feed.imageUrl}" />
    <itunes:explicit>false</itunes:explicit>
    <itunes:owner>
      <itunes:name>${escapeXml(feed.author)}</itunes:name>
      <itunes:email>${escapeXml(feed.email)}</itunes:email>
    </itunes:owner>
    <lastBuildDate>${dateFormat.format(Date())}</lastBuildDate>
$items
  </channel>
</rss>"""
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
