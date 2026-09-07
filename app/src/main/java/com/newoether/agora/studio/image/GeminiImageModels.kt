package com.newoether.agora.studio.image

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import com.newoether.agora.R

enum class StudioImageModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isPrimary: Boolean = false,
) {
    NANO_BANANA_2(
        id = "gemini-3.1-flash-image-preview",
        displayName = "Nano Banana 2",
        description = "gemini-3.1-flash-image-preview · Qualité supérieure, résolutions 512px à 4K, création et retouche",
        isPrimary = true,
    ),
    NANO_BANANA(
        id = "gemini-2.5-flash-image",
        displayName = "Nano Banana",
        description = "gemini-2.5-flash-image · Vitesse ultra-rapide pour génération et édition d'images",
    ),
    NANO_BANANA_PRO(
        id = "gemini-3-pro-image-preview",
        displayName = "Nano Banana Pro",
        description = "gemini-3-pro-image-preview · Raisonnement visuel avancé et retouche de haute fidélité",
    ),
    IMAGEN_3(
        id = "imagen-3.0-generate-002",
        displayName = "Imagen 3",
        description = "imagen-3.0-generate-002 · Rendu photoréaliste Google",
    );

    companion object {
        val DEFAULT = NANO_BANANA_2

        fun fromId(id: String): StudioImageModel {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class StudioAspectRatio(val label: String, val apiValue: String) {
    SQUARE("1:1", "1:1"),
    PORTRAIT_STORY("9:16", "9:16"),
    LANDSCAPE("16:9", "16:9"),
    PORTRAIT_PHOTO("3:4", "3:4"),
    LANDSCAPE_PHOTO("4:3", "4:3");
}

enum class StudioResolution(val label: String, val apiValue: String) {
    RES_512("512px", "512px"),
    RES_1K("1K", "1K"),
    RES_2K("2K", "2K"),
    RES_4K("4K", "4K");
}

data class StudioPresetTemplate(
    val id: String,
    val title: String,
    val prompt: String,
    @DrawableRes val drawableRes: Int? = null,
    val fallbackGradientColors: List<Long> = listOf(0xFF1E293B, 0xFF0F172A),
) {
    companion object {
        val PRESETS = listOf(
            StudioPresetTemplate(
                id = "premier_rang",
                title = "Premier rang",
                prompt = "Photographie de défilé de mode haute couture, mannequin au premier rang sous les flashs des projecteurs, bokeh élégant et stylé, 8k",
                drawableRes = null,
                fallbackGradientColors = listOf(0xFF2D1B36, 0xFF120E18),
            ),
            StudioPresetTemplate(
                id = "origami",
                title = "Origami",
                prompt = "Portrait origami géométrique en papier plié multicolore avec pull jaune chaud, pliages précis, ombres douces et éclairage studio soigné",
                drawableRes = R.drawable.img_preset_origami,
            ),
            StudioPresetTemplate(
                id = "aquarelle",
                title = "Aquarelle",
                prompt = "Peinture aquarelle lumineuse d'une femme souriante avec chapeau de paille dans une rue parisienne ensoleillée, lavis subtils, touches impressionnistes",
                drawableRes = R.drawable.img_preset_aquarelle,
            ),
            StudioPresetTemplate(
                id = "parachute",
                title = "Parachute",
                prompt = "Selfie grand-angle dynamique et joyeux d'un parachutiste en chute libre au-dessus d'un paysage verdoyant, vent dans le visage, ciel bleu éclatant",
                drawableRes = null,
                fallbackGradientColors = listOf(0xFF0F3B5F, 0xFF081C30),
            ),
            StudioPresetTemplate(
                id = "zen",
                title = "Zen",
                prompt = "Scène céleste surréaliste d'une personne flottant en apesanteur au-dessus d'un jardin zen aux ondulations parfaites sous un croissant de lune lumineux, paix absolue",
                drawableRes = R.drawable.img_preset_zen,
            ),
            StudioPresetTemplate(
                id = "claymation",
                title = "Pâte à modeler",
                prompt = "Personnage expressif sculpté en pâte à modeler avec lunettes rondes et pull en laine tricotée dans une bibliothèque chaleureuse, style stop-motion claymation",
                drawableRes = R.drawable.img_preset_claymation,
            ),
            StudioPresetTemplate(
                id = "cyberpunk",
                title = "Cyberpunk",
                prompt = "Ruelle futuriste sous une pluie nocturne illuminée d'enseignes holographiques néon rose et turquoise, reflets sur le sol humide, cinématographique",
                drawableRes = null,
                fallbackGradientColors = listOf(0xFF3B0764, 0xFF020617),
            ),
            StudioPresetTemplate(
                id = "rendu3d",
                title = "Rendu 3D",
                prompt = "Architecture futuriste isométrique en verre translucide et chrome poli, éclairage global illumination, réfraction réaliste, composition épurée",
                drawableRes = null,
                fallbackGradientColors = listOf(0xFF064E3B, 0xFF022C22),
            ),
        )
    }
}

data class GeneratedStudioImage(
    val id: String,
    val bitmap: Bitmap?,
    val filePath: String?,
    val prompt: String,
    val model: StudioImageModel,
    val aspectRatio: StudioAspectRatio,
    val resolution: StudioResolution,
    val timestamp: Long = System.currentTimeMillis(),
    val base64Data: String? = null,
    val isRetouched: Boolean = false,
)
