package com.newoether.agora.ui.ds

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton canonique : placeholder animé pour états de chargement de contenu.
 * Remplace les `CircularProgressIndicator` utilisés comme placeholders de
 * contenu (studios, lists, cards) par une animation shimmer premium M3 Expressive.
 *
 * Variante [AgoraSkeletonText] pour lignes de texte (avatar + lignes).
 * Désactivé si `LocalAgoraMotionPolicy` interdit les animations continues.
 */
@Composable
fun AgoraSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    width: Dp? = null,
    shape: androidx.compose.ui.graphics.Shape = AgoraRadii.Xs,
    label: String = "Chargement",
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(AgoraDurations.Medium),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AgoraAlpha.Divider)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = AgoraAlpha.Subtle)
    val shimmerColor = lerp(base, highlight, progress)

    Box(
        modifier = modifier
            .height(height)
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(shape)
            .background(shimmerColor)
            .semantics {
                contentDescription = label
                liveRegion()
            },
    )
}

/**
 * Ligne de skeleton type "avatar + texte" pour listes en chargement.
 * Avatar circulaire + 2 lignes de largeur variable.
 */
@Composable
fun AgoraSkeletonTextRow(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
    line1Width: Dp = 120.dp,
    line2Width: Dp = 80.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgoraSkeleton(
            height = avatarSize,
            width = avatarSize,
            shape = CircleShape,
            modifier = Modifier.size(avatarSize),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = AgoraSpacing.Lg),
        ) {
            AgoraSkeleton(height = 14.dp, width = line1Width)
            AgoraSkeleton(
                height = 12.dp,
                width = line2Width,
                modifier = Modifier.padding(top = AgoraSpacing.Xs),
            )
        }
    }
}
