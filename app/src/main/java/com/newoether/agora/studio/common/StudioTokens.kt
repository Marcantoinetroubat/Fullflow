package com.newoether.agora.studio.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraElevation
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.FullLiveBridge

/**
 * Shared design tokens for the FullFlow creative studios (image, video).
 * Migre vers le Design System : formes = AgoraRadii, bordure = Divider 0.12,
 * accents = FullLiveBridge. Hex conservés uniquement comme fallback documenté
 * en attendant la bascule complète sur MaterialTheme.colorScheme.
 */
object StudioTokens {
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFF94A3B8)

    val AccentGold = Color(0xFFFFD54F)
    val AccentViolet = FullLiveBridge.Purple
    val AccentBlue = FullLiveBridge.Cyan

    val CardBackground = Color(0xFF131822)
    val CardBorder = BorderStroke(
        AgoraElevation.CardTonal,
        Color.White.copy(alpha = AgoraAlpha.Divider),
    )

    val ScreenBackground: Brush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF07090E),
            Color(0xFF0A0F17),
            Color(0xFF0E1422),
            Color(0xFF0A101C),
        )
    )

    val PillShape = AgoraRadii.Pill
    val PresetCardShape = AgoraRadii.Pill
    val ResultCardShape = AgoraRadii.Dialog
    val SheetShape = AgoraRadii.Sheet
}
