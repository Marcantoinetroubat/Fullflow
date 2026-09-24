package com.newoether.agora.studio.podcast

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.newoether.agora.AgoraApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Distributes podcast episodes: writes the RSS feed to disk, prepares email distribution,
 * and optionally shares the episode via Android Intent.
 */
class PodcastDistributor(private val context: Context) {

    private val podcastsDir = File(context.filesDir, "podcasts").also { it.mkdirs() }

    /**
     * Generates and writes the RSS feed for a podcast series.
     * Returns the local file path of the RSS feed.
     */
    suspend fun writeRssFeed(
        feedTitle: String,
        feedDescription: String,
        episodes: List<PodcastRssGenerator.PodcastEpisode>,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val feed = PodcastRssGenerator.PodcastFeed(
                title = feedTitle,
                description = feedDescription,
                link = "https://fullflow.app/podcast",
                author = "FullFlow",
                episodes = episodes,
            )
            val rssXml = PodcastRssGenerator.generate(feed)
            val rssFile = File(podcastsDir, "feed_${System.currentTimeMillis()}.xml")
            rssFile.writeText(rssXml)
            rssFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prepares an email with the podcast episode for distribution.
     * Opens the default email client with a pre-filled message.
     */
    fun shareEpisodeByEmail(
        episodeTitle: String,
        episodeDescription: String,
        audioUrl: String,
        recipients: List<String> = emptyList(),
    ) {
        val subject = "Podcast : $episodeTitle"
        val body = """
Bonjour,

Voici votre épisode de podcast : $episodeTitle

$episodeDescription

Écoutez-le ici : $audioUrl

---
Envoyé depuis FullFlow
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Shares the podcast episode via Android share sheet.
     */
    fun shareEpisode(title: String, description: String, audioUrl: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Podcast : $title")
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$description\n\nÉcouter : $audioUrl")
        }
        val chooser = Intent.createChooser(intent, "Partager l'épisode")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
