package com.newoether.agora.studio.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SocialPlatform(val displayName: String, val packageName: String?) {
    LINKEDIN("LinkedIn", "com.linkedin.android"),
    INSTAGRAM("Instagram", "com.instagram.android"),
    TIKTOK("TikTok", "com.zhiliaoapp.musically"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    BRAIN("Second Cerveau", null),
}

data class SocialExportResult(
    val platform: SocialPlatform,
    val text: String,
    val mimeType: String,
    val imagePaths: List<String> = emptyList(),
    val audioPath: String? = null,
    val success: Boolean = true,
)

object SocialFormats {

    fun getTranscodePrompt(platform: SocialPlatform, articleText: String): String {
        val baseInstruction = """Tu es un expert en communication digitale. Transforme l'article ci-dessous en un contenu parfaitement adapté à la plateforme "${platform.displayName}"."""

        val platformSpecific = when (platform) {
            SocialPlatform.LINKEDIN -> """Règles LinkedIn :
- Post professionnel : 3-4 paragraphes courts, engageants.
- Utilise 1-2 émojis professionnels discrets (💼 📊 🚀).
- Ajoute 3-5 hashtags pertinents à la fin.
- Termine par un appel à l'action (contact, commentaire, partage).
- Ajoute la signature : "Intelligence structurée par FullFlow"
- Ton : professionnel mais accessible, expert mais pas arrogant.
- Longueur totale : 300-500 mots maximum."""

            SocialPlatform.INSTAGRAM -> """Règles Instagram :
- Caption courte et percutante (max 150 mots).
- Utilise 3-5 émojis visuels (✨ 🎨 🔥 💡).
- Ajoute 8-12 hashtags tendance et pertinents.
- Ton : léger, visuel, émotionnel, aspirational.
- Le texte doit donner envie de swiper ou de commenter."""

            SocialPlatform.TIKTOK -> """Règles TikTok :
- Script vidéo court (30-60 secondes de lecture à voix haute).
- Phrases courtes, punchy, qui s'affichent bien à l'écran.
- Un hook fort dans la première phrase.
- Un appel à l'action final.
- Ton : dynamique, jeune, direct, fun.
- Inclus des suggestions de texte à afficher à l'écran (overlay)."""

            SocialPlatform.WHATSAPP -> """Règles WhatsApp :
- Message personnel, familial, chaleureux.
- Maximum 150 mots.
- Ton : décontracté, proche, simple.
- Un lien ou référence à la fin pour ceux qui veulent en savoir plus.
- Pas de hashtags, pas d'émojis professionnels."""

            SocialPlatform.BRAIN -> """Règles Second Cerveau :
- Format Markdown structuré pour l'archivage.
- Titre clair, sections avec ##, mots-clés en #tags.
- Prêt pour la recherche sémantique et le graphe de connaissances.
- Conserve toute la profondeur du contenu original."""
        }

        return """$baseInstruction

$platformSpecific

# Article à transcoder :
$articleText

Produis le contenu adapté maintenant. Uniquement le texte final, pas d'explication."""
    }

    fun getMimeType(platform: SocialPlatform): String = when (platform) {
        SocialPlatform.INSTAGRAM -> "image/*"
        SocialPlatform.TIKTOK -> "video/*"
        else -> "text/plain"
    }
}
