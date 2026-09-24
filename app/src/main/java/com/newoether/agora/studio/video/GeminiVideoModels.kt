package com.newoether.agora.studio.video

import androidx.annotation.DrawableRes

enum class StudioVideoModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isPrimary: Boolean = false,
) {
    VEO_3_FAST(
        id = "veo-3.1-fast-generate-preview",
        displayName = "Veo 3.1 Fast",
        description = "veo-3.1-fast-generate-preview · Génération vidéo cinématique haute fidélité ultra-rapide",
        isPrimary = true,
    ),
    VEO_3(
        id = "veo-3.1-generate-preview",
        displayName = "Veo 3.1",
        description = "veo-3.1-generate-preview · Qualité cinéma maximale avec audio natif",
    ),
    VEO_2(
        id = "veo-2.0-generate-001",
        displayName = "Veo 2.0",
        description = "veo-2.0-generate-001 · Modèle vidéo Veo 2.0 éprouvé",
    ),
    OMNI(
        id = "gemini-omni-1.1-flash",
        displayName = "Omni 1.1 Flash",
        description = "gemini-omni-1.1-flash · Édition et génération vidéo conversationnelle ultra-rapide",
    );

    companion object {
        val DEFAULT = VEO_3_FAST

        fun fromId(id: String): StudioVideoModel {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class StudioVideoAspectRatio(val label: String, val apiValue: String) {
    LANDSCAPE("16:9", "16:9"),
    PORTRAIT("9:16", "9:16"),
    SQUARE("1:1", "1:1");
}

enum class StudioVideoDuration(val label: String, val seconds: Int) {
    SEC_5("5s", 5),
    SEC_10("10s", 10);
}

data class StudioVideoPresetTemplate(
    val id: String,
    val title: String,
    val prompt: String,
    val isHero: Boolean = false,
    @DrawableRes val drawableRes: Int? = null,
    val fallbackGradientColors: List<Long> = listOf(0xFF0F172A, 0xFF020617),
) {
    companion object {
        val PRESETS = listOf(
            StudioVideoPresetTemplate(
                id = "monde_miniature",
                title = "Monde miniature",
                prompt = "Vue panoramique drone 360 degrés d'une petite planète miniature recouverte d'une forêt de sapins alpins verdoyante, randonneur avec sac à dos marchant au sommet sous un ciel bleu d'été, cinématique, 4K",
                isHero = true,
                fallbackGradientColors = listOf(0xFF0C4A6E, 0xFF065F46, 0xFF0F172A),
            ),
            StudioVideoPresetTemplate(
                id = "anime",
                title = "Anime",
                prompt = "Animation japonaise style Makoto Shinkai de deux jeunes amis devant un mur en béton orné d'un graffiti vert néon électrique, vent dans les cheveux, lumière du crépuscule cinématique",
                isHero = false,
                fallbackGradientColors = listOf(0xFF1E3A8A, 0xFF047857, 0xFF020617),
            ),
            StudioVideoPresetTemplate(
                id = "aventure_8bits",
                title = "Aventure 8 bits",
                prompt = "Jeu vidéo de plateforme cyberpunk pixel art rétro 8 bits, héros urbain courant sur des toits illuminés d'enseignes néon violettes et roses sous un ciel étoilé pixelisé",
                isHero = false,
                fallbackGradientColors = listOf(0xFF581C87, 0xFF831843, 0xFF0F172A),
            ),
        )
    }
}

data class GeneratedStudioVideo(
    val id: String,
    val filePath: String,
    val uri: String,
    val prompt: String,
    val model: StudioVideoModel,
    val aspectRatio: StudioVideoAspectRatio,
    val duration: StudioVideoDuration,
    val timestamp: Long = System.currentTimeMillis(),
)
