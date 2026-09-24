package com.newoether.agora.studio.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.ui.theme.OutfitFamily

/**
 * En-tête héro canonique des studios créatifs (image, vidéo).
 *
 * Fusion de `StudioHeroHeader` (image) et `StudioVideoHeroHeader` (vidéo) :
 * même structure (médaillon icône + titre + sous-titre), seuls l'icône,
 * l'accent et les textes diffèrent.
 */
@Composable
fun StudioHeroHeaderCommon(
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    boxSize: Dp = 48.dp,
    boxCorner: Dp = 16.dp,
    titleHorizontalPadding: Dp = 24.dp,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(boxCorner))
                .background(Color(0xFF141924))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(boxCorner)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Text(
            text = title,
            fontFamily = OutfitFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
            modifier = Modifier.padding(horizontal = titleHorizontalPadding)
        )

        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )
    }
}
