package com.newoether.agora.podcast

import android.content.Context
import android.util.Base64
import com.newoether.agora.api.DuckDuckGoScraper
import com.newoether.agora.ui.chat.audio.FullFlowTtsEngine
import com.newoether.agora.ui.chat.audio.TtsEngineMode
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PodcastPipelineProgress(
    val step: Int, // 1 to 4
    val stepTitle: String,
    val detailMessage: String,
)

data class PodcastResult(
    val topic: String,
    val timeframe: String,
    val researchReport: String,
    val script: String,
    val audioFile: File?,
    val audioDurationMs: Long,
    val summaryHtml: String,
    val htmlFile: File?,
)

class PersonalPodcasterPipeline(
    private val context: Context,
    private val ttsEngine: FullFlowTtsEngine,
) {
    private val podcastsDir = File(context.filesDir, "podcasts").also { it.mkdirs() }

    suspend fun execute(
        topic: String,
        timeframe: String = "récent",
        ttsMode: TtsEngineMode = ttsEngine.ttsEngineMode,
        voice: String? = null,
        ttsModel: String? = null,
        searchExecutor: (suspend (query: String, maxResults: Int) -> List<DuckDuckGoScraper.WebResult>)? = null,
        onProgress: (suspend (PodcastPipelineProgress) -> Unit)? = null,
    ): PodcastResult = withContext(Dispatchers.IO) {
        val safeTopic = topic.trim().ifBlank { "Actualités Tech & Science" }
        val safeTimeframe = timeframe.trim().ifBlank { "cette semaine" }

        // Step 1: Research Topic
        onProgress?.invoke(
            PodcastPipelineProgress(
                step = 1,
                stepTitle = "Recherche approfondie",
                detailMessage = "Collecte des actualités récentes sur '$safeTopic' ($safeTimeframe)...",
            ),
        )
        val researchReport = performResearch(safeTopic, safeTimeframe, searchExecutor)

        // Step 2: Generate Podcast Script
        onProgress?.invoke(
            PodcastPipelineProgress(
                step = 2,
                stepTitle = "Écriture du script",
                detailMessage = "Rédaction du script audio captivant et engageant...",
            ),
        )
        val podcastScript = generateScript(safeTopic, safeTimeframe, researchReport)

        // Step 3: Generate Podcast Audio
        onProgress?.invoke(
            PodcastPipelineProgress(
                step = 3,
                stepTitle = "Synthèse vocale multi-provider",
                detailMessage = "Production de l'enregistrement audio (${ttsMode.name})...",
            ),
        )
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val isMp3Mode = ttsMode == TtsEngineMode.OPENAI_CLOUD
        val audioExt = if (isMp3Mode) "mp3" else "wav"
        val audioFile = File(podcastsDir, "podcast_${timestamp}.$audioExt")

        val audioSuccess = ttsEngine.synthesizeToFile(
            text = podcastScript,
            outputFile = audioFile,
            engineMode = ttsMode,
            voice = voice,
            modelOverride = ttsModel,
        )

        val actualAudioFile = if (audioSuccess && audioFile.exists() && audioFile.length() > 0) {
            audioFile
        } else {
            null
        }

        val durationMs = computeAudioDurationMs(actualAudioFile, podcastScript)

        // Step 4: Generate Podcast Summary Webpage
        onProgress?.invoke(
            PodcastPipelineProgress(
                step = 4,
                stepTitle = "Création du lecteur interactif",
                detailMessage = "Génération de la page HTML5 et du lecteur multimédia...",
            ),
        )
        val summaryHtml = generateHtmlPage(
            topic = safeTopic,
            timeframe = safeTimeframe,
            script = podcastScript,
            audioFile = actualAudioFile,
            durationMs = durationMs,
            ttsMode = ttsMode,
        )
        val htmlFile = File(podcastsDir, "podcast_${timestamp}.html").apply {
            writeText(summaryHtml)
        }

        PodcastResult(
            topic = safeTopic,
            timeframe = safeTimeframe,
            researchReport = researchReport,
            script = podcastScript,
            audioFile = actualAudioFile,
            audioDurationMs = durationMs,
            summaryHtml = summaryHtml,
            htmlFile = htmlFile,
        )
    }

    private suspend fun performResearch(
        topic: String,
        timeframe: String,
        searchExecutor: (suspend (query: String, maxResults: Int) -> List<DuckDuckGoScraper.WebResult>)?,
    ): String = withContext(Dispatchers.IO) {
        try {
            val searchQuery = "$topic $timeframe"
            // When the caller provides one, research honors the user's configured web
            // search provider + fallback chain instead of the hardcoded DuckDuckGo scraper.
            val results = if (searchExecutor != null) {
                searchExecutor(searchQuery, 5)
            } else {
                when (val r = DuckDuckGoScraper().search(searchQuery, maxResults = 5)) {
                    is DuckDuckGoScraper.SearchResponse.Success -> r.results
                    is DuckDuckGoScraper.SearchResponse.Error -> emptyList()
                }
            }
            if (results.isNotEmpty()) {
                buildString {
                    appendLine("# Rapport de Recherche : $topic")
                    appendLine("**Temporalité ciblée** : $timeframe")
                    appendLine()
                    appendLine("## Faits saillants et dernières découvertes")
                    results.forEachIndexed { idx, res ->
                        appendLine("${idx + 1}. **${res.title}** : ${res.snippet}")
                        if (res.url.isNotBlank()) {
                            appendLine("   - Source : ${res.url}")
                        }
                    }
                }
            } else {
                fallbackResearchReport(topic, timeframe)
            }
        } catch (e: Exception) {
            DebugLog.e("PersonalPodcaster", "Research search failed, using synthesized report", e)
            fallbackResearchReport(topic, timeframe)
        }
    }

    private fun fallbackResearchReport(topic: String, timeframe: String): String = buildString {
        appendLine("# Synthèse thématique : $topic")
        appendLine("**Période** : $timeframe")
        appendLine()
        appendLine("Les dernières évolutions marquantes autour de **$topic** témoignent d'une accélération notable des innovations et des discussions clés sur cette période ($timeframe).")
        appendLine("Les points cardinaux incluent les avancées technologiques récentes, l'impact sur l'écosystème global, ainsi que les perspectives stratégiques à court et moyen terme.")
    }

    private fun generateScript(topic: String, timeframe: String, report: String): String = buildString {
        appendLine("Bienvenue dans votre Personal Podcaster.")
        appendLine("Aujourd'hui, nous faisons le point sur un sujet captivant : $topic, avec un focus spécial sur les développements de $timeframe.")
        appendLine()
        appendLine("Entrons sans attendre au cœur de l'actualité.")

        // Distill key takeaways from the report into conversational paragraphs.
        // TTS-first cleanup: item titles (bold markdown) are for the eyes, not the ears —
        // reading them aloud made the topic repeat at every sentence and broke the flow.
        val lines = report.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        if (lines.isNotEmpty()) {
            val sentences = lines
                .asSequence()
                .filter { !it.contains("http", ignoreCase = true) }
                .filter { !it.contains("Temporalité ciblée", ignoreCase = true) }
                .map { it.replace(Regex("^\\d+\\.\\s*"), "") }
                // Drop the leading bold title span (« **Titre** : ») — keep only spoken content
                .map { it.replace(Regex("^\\*\\*.*?\\*\\*\\s*:?\\s*"), "") }
                .map { it.replace("**", "") }
                .map { it.trim().trimStart('-', '–', '—').trim() }
                .filter { it.length >= 40 }
                .distinct()
                .take(6)
                .toList()
            if (sentences.isNotEmpty()) {
                val transitions = listOf("D'abord", "Ensuite", "Par ailleurs", "Également", "De plus", "Enfin")
                val spoken = sentences.mapIndexed { idx, sentence ->
                    val transition = transitions.getOrElse(idx) { "Enfin" }
                    "$transition, ${sentence.replaceFirstChar { it.lowercase() }}"
                }.joinToString(" ")
                appendLine(spoken)
            } else {
                appendLine("Au cours de $timeframe, $topic a franchi plusieurs étapes déterminantes, redéfinissant les standards et ouvrant la voie à de nouveaux cas d'usage majeurs.")
            }
        } else {
            appendLine("Au cours de $timeframe, $topic a franchi plusieurs étapes déterminantes, redéfinissant les standards et ouvrant la voie à de nouveaux cas d'usage majeurs.")
        }

        appendLine()
        appendLine("En conclusion, les avancées observées autour de $topic confirment une dynamique d'innovation soutenue. Restez connectés pour notre prochain épisode dédié aux évolutions futures. Merci de votre écoute, et à très bientôt sur FullFlow !")
    }

    private fun computeAudioDurationMs(file: File?, script: String): Long {
        if (file != null && file.exists()) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                val parsed = durationStr?.toLongOrNull()
                if (parsed != null && parsed > 0) return parsed
            } catch (_: Exception) {}
        }
        // Estimate based on speech rate (~150 words per minute => ~2.5 words per second)
        val wordCount = script.split(Regex("\\s+")).count { it.isNotBlank() }
        val seconds = (wordCount / 2.5).coerceAtLeast(10.0)
        return (seconds * 1000).toLong()
    }

    private fun generateHtmlPage(
        topic: String,
        timeframe: String,
        script: String,
        audioFile: File?,
        durationMs: Long,
        ttsMode: TtsEngineMode,
    ): String {
        val audioDataUri = if (audioFile != null && audioFile.exists() && audioFile.length() < 12 * 1024 * 1024) {
            val bytes = audioFile.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mime = if (audioFile.extension.equals("mp3", true)) "audio/mp3" else "audio/wav"
            "data:$mime;base64,$base64"
        } else ""

        val totalSecs = (durationMs / 1000).toInt()
        val durationFormatted = String.format(Locale.getDefault(), "%02d:%02d", totalSecs / 60, totalSecs % 60)
        val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(Date())

        val scriptParagraphsHtml = script.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p class=\"mb-4 text-slate-300 leading-relaxed text-base\">${escapeHtml(it)}</p>" }

        return """
<!DOCTYPE html>
<html lang="fr" class="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Personal Podcaster : ${escapeHtml(topic)}</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
  <script>
    tailwind.config = {
      darkMode: 'class',
      theme: {
        extend: {
          fontFamily: {
            outfit: ['Outfit', 'sans-serif'],
            sans: ['Plus Jakarta Sans', 'sans-serif'],
          },
          colors: {
            brand: {
              50: '#f5f3ff',
              400: '#a78bfa',
              500: '#8b5cf6',
              600: '#7c3aed',
              dark: '#0B0F17',
              card: '#161C26',
              surface: '#1F2937'
            }
          }
        }
      }
    }
  </script>
  <style>
    @keyframes pulse-wave {
      0%, 100% { height: 6px; }
      50% { height: 28px; }
    }
    .wave-bar {
      animation: pulse-wave 1.4s ease-in-out infinite;
    }
  </style>
</head>
<body class="bg-brand-dark text-slate-100 font-sans min-h-screen antialiased flex flex-col items-center p-4 sm:p-8">

  <main class="w-full max-w-3xl space-y-6">
    <!-- Header Card -->
    <header class="bg-brand-card/90 border border-purple-500/20 rounded-3xl p-6 sm:p-8 backdrop-blur-xl shadow-2xl relative overflow-hidden">
      <div class="absolute -top-24 -right-24 w-64 h-64 bg-purple-600/10 rounded-full blur-3xl pointer-events-none"></div>
      <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
        <div class="flex items-center gap-2.5">
          <span class="inline-flex items-center justify-center p-2.5 rounded-2xl bg-purple-500/20 text-purple-400">
            <svg class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/><path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/></svg>
          </span>
          <div>
            <span class="text-xs uppercase tracking-wider font-semibold text-purple-400">Personal Podcaster</span>
            <p class="text-xs text-slate-400">$formattedDate • Moteur : ${ttsMode.name}</p>
          </div>
        </div>
        <span class="px-3.5 py-1.5 rounded-full text-xs font-semibold bg-purple-500/10 text-purple-300 border border-purple-500/30">
          📅 ${escapeHtml(timeframe)}
        </span>
      </div>

      <h1 class="font-outfit text-2xl sm:text-3xl font-extrabold text-white tracking-tight mb-3">
        ${escapeHtml(topic)}
      </h1>
      <p class="text-sm text-slate-300">
        Synthèse intelligente multi-sources condensée en format broadcast haute-fidélité.
      </p>
    </header>

    <!-- Audio Player Card -->
    <section class="bg-brand-card/90 border border-slate-700/50 rounded-3xl p-6 sm:p-8 backdrop-blur-xl shadow-xl space-y-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="flex items-end gap-1 h-8 px-2">
            <span class="w-1.5 bg-purple-500 rounded-full wave-bar" style="animation-delay: 0.1s"></span>
            <span class="w-1.5 bg-purple-400 rounded-full wave-bar" style="animation-delay: 0.3s"></span>
            <span class="w-1.5 bg-indigo-500 rounded-full wave-bar" style="animation-delay: 0.2s"></span>
            <span class="w-1.5 bg-purple-300 rounded-full wave-bar" style="animation-delay: 0.4s"></span>
            <span class="w-1.5 bg-purple-600 rounded-full wave-bar" style="animation-delay: 0.15s"></span>
          </div>
          <div>
            <h2 class="text-sm font-semibold text-white">Épisode Audio Complet</h2>
            <p class="text-xs text-slate-400">Durée estimée : $durationFormatted</p>
          </div>
        </div>
        <div class="text-xs font-medium px-3 py-1 bg-slate-800 rounded-lg text-slate-300">
          16-bit 24kHz
        </div>
      </div>

      <!-- Native Audio Tag -->
      <div class="w-full">
        <audio id="podcastAudio" class="w-full rounded-xl" controls src="$audioDataUri">
          Votre navigateur ne supporte pas l'élément audio.
        </audio>
      </div>

      <!-- Playback rate toggles -->
      <div class="flex items-center justify-between pt-2 border-t border-slate-800 text-xs text-slate-400">
        <span>Vitesse de lecture :</span>
        <div class="flex items-center gap-1.5">
          <button onclick="setSpeed(1.0)" class="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200">1.0x</button>
          <button onclick="setSpeed(1.25)" class="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200">1.25x</button>
          <button onclick="setSpeed(1.5)" class="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200">1.5x</button>
          <button onclick="setSpeed(2.0)" class="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200">2.0x</button>
        </div>
      </div>
    </section>

    <!-- Transcript Section -->
    <article class="bg-brand-card/90 border border-slate-700/50 rounded-3xl p-6 sm:p-8 backdrop-blur-xl shadow-xl">
      <div class="flex items-center justify-between mb-6 pb-4 border-b border-slate-800">
        <div class="flex items-center gap-2">
          <svg class="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
          <h2 class="font-outfit font-bold text-lg text-white">Transcription Intégrale</h2>
        </div>
        <button onclick="copyTranscript()" id="copyBtn" class="text-xs font-semibold px-3 py-1.5 rounded-xl bg-purple-500/10 text-purple-300 border border-purple-500/20 hover:bg-purple-500/20 transition-all">
          Copier le texte
        </button>
      </div>

      <div id="transcriptText" class="prose prose-invert max-w-none">
        $scriptParagraphsHtml
      </div>
    </article>

    <!-- Footer -->
    <footer class="text-center text-xs text-slate-500 pb-8 pt-4">
      Généré par FullFlow • Personal Podcaster Agentic Workflow
    </footer>
  </main>

  <script>
    const audio = document.getElementById('podcastAudio');
    function setSpeed(rate) {
      if (audio) audio.playbackRate = rate;
    }
    function copyTranscript() {
      const text = document.getElementById('transcriptText').innerText;
      navigator.clipboard.writeText(text).then(() => {
        const btn = document.getElementById('copyBtn');
        const old = btn.innerText;
        btn.innerText = 'Copié !';
        setTimeout(() => btn.innerText = old, 2000);
      });
    }
  </script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
