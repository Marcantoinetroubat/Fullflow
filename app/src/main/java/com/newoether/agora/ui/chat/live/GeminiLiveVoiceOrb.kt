package com.newoether.agora.ui.chat.live

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.newoether.agora.api.gemini.live.GeminiLiveState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeminiLiveVoiceOrb(
    sessionState: GeminiLiveState,
    userVolume: Float,
    modelVolume: Float,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_orb")

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = minOf(size.width, size.height)
        val baseRadius = minDim * 0.22f

        val effectiveVolume = when (sessionState) {
            is GeminiLiveState.Speaking -> modelVolume.coerceIn(0f, 1f)
            is GeminiLiveState.Listening -> if (isMuted) 0f else userVolume.coerceIn(0f, 1f)
            else -> 0.05f
        }

        val volumeBoost = effectiveVolume * baseRadius * 0.7f
        val dynamicRadius = baseRadius + volumeBoost + sin(pulsePhase) * 6f

        // Theme colors based on state
        val (glowStart, glowMid, glowEnd) = when (sessionState) {
            is GeminiLiveState.Speaking -> Triple(
                tertiaryColor.copy(alpha = 0.85f),
                secondaryColor.copy(alpha = 0.5f),
                primaryColor.copy(alpha = 0.15f)
            )
            is GeminiLiveState.Listening -> if (isMuted) {
                Triple(
                    surfaceVariant.copy(alpha = 0.6f),
                    surfaceVariant.copy(alpha = 0.3f),
                    Color.Transparent
                )
            } else {
                Triple(
                    primaryColor.copy(alpha = 0.85f),
                    tertiaryColor.copy(alpha = 0.45f),
                    secondaryColor.copy(alpha = 0.15f)
                )
            }
            is GeminiLiveState.Thinking -> Triple(
                secondaryColor.copy(alpha = 0.75f),
                primaryColor.copy(alpha = 0.45f),
                tertiaryColor.copy(alpha = 0.15f)
            )
            is GeminiLiveState.Connecting -> Triple(
                primaryColor.copy(alpha = 0.4f),
                secondaryColor.copy(alpha = 0.2f),
                Color.Transparent
            )
            is GeminiLiveState.Error -> Triple(
                errorColor.copy(alpha = 0.8f),
                errorContainerColor.copy(alpha = 0.4f),
                Color.Transparent
            )
            else -> Triple(
                primaryColor.copy(alpha = 0.3f),
                surfaceVariant.copy(alpha = 0.15f),
                Color.Transparent
            )
        }

        // Outer ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowEnd, Color.Transparent),
                center = center,
                radius = dynamicRadius * 2.2f
            ),
            center = center,
            radius = dynamicRadius * 2.2f
        )

        // Multiple dynamic harmonic rings
        val ringCount = 4
        for (i in 1..ringCount) {
            val ringProgress = (i.toFloat() / ringCount)
            val ringRadius = dynamicRadius * (1f + ringProgress * 0.55f) +
                sin(pulsePhase + i * 1.3f) * (6f + effectiveVolume * 15f)
            val ringAlpha = (1f - ringProgress) * (0.3f + effectiveVolume * 0.5f)

            val ringColor = if (i % 2 == 0) glowMid else glowStart
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha.coerceIn(0.05f, 0.8f)),
                center = center,
                radius = ringRadius,
                style = Stroke(width = (2f + ringProgress * 2f + effectiveVolume * 3f))
            )
        }

        // Orbital nodes when thinking or speaking
        if (sessionState is GeminiLiveState.Thinking || sessionState is GeminiLiveState.Speaking) {
            val nodeCount = 5
            for (i in 0 until nodeCount) {
                val nodeAngle = Math.toRadians((rotationAngle + (i * 360f / nodeCount)).toDouble())
                val orbitRadius = dynamicRadius * 1.35f
                val nodeOffset = Offset(
                    center.x + (orbitRadius * cos(nodeAngle)).toFloat(),
                    center.y + (orbitRadius * sin(nodeAngle)).toFloat()
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.7f),
                    center = nodeOffset,
                    radius = 4f + effectiveVolume * 6f
                )
            }
        }

        // Central glowing orb core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowStart, glowMid, glowEnd),
                center = center,
                radius = dynamicRadius
            ),
            center = center,
            radius = dynamicRadius
        )

        // Inner highlight sheen
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(center.x - dynamicRadius * 0.25f, center.y - dynamicRadius * 0.25f),
                radius = dynamicRadius * 0.6f
            ),
            center = center,
            radius = dynamicRadius * 0.8f
        )
    }
}
