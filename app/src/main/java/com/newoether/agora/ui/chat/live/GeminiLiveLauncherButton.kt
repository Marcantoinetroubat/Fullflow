package com.newoether.agora.ui.chat.live

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R

@Composable
fun GeminiLiveLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_btn")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val gradientBorder = Brush.sweepGradient(
        listOf(
            primaryColor.copy(alpha = borderAlpha),
            tertiaryColor.copy(alpha = borderAlpha),
            primaryColor.copy(alpha = borderAlpha)
        )
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .border(1.5.dp, gradientBorder, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), CircleShape)
            .clip(CircleShape)
            .testTag("gemini_live_button")
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = stringResource(R.string.gemini_live_button_label),
            tint = primaryColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
