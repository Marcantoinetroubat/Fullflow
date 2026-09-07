package com.newoether.agora.ui.chat.audio

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Modern dynamic visualizer that displays animated audio waveform bars
 * reacting to amplitude / RMS levels and animation states.
 */
@Composable
fun FullFlowAudioWaveVisualizer(
    isActive: Boolean,
    amplitude: Float = 0f, // 0f to 1f
    barCount: Int = 18,
    maxBarHeight: Dp = 48.dp,
    minBarHeight: Dp = 6.dp,
    modifier: Modifier = Modifier,
    barColorStart: Color = Color(0xFFE2E8F0),
    barColorEnd: Color = Color(0xFF64748B),
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxBarHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val safeAmp = amplitude.coerceIn(0f, 1f)

        for (i in 0 until barCount) {
            val offset = i.toFloat() / barCount * (2 * Math.PI).toFloat()
            val wave = (sin(animPhase + offset) + 1f) / 2f // 0f..1f

            val currentRatio = if (isActive) {
                val base = 0.2f + 0.35f * wave
                (base + safeAmp * 0.45f).coerceIn(0.12f, 1f)
            } else {
                0.12f
            }

            val barHeight = minBarHeight + (maxBarHeight - minBarHeight) * currentRatio

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(3.5.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(barColorStart, barColorEnd)
                        )
                    )
            )
        }
    }
}

/**
 * Compact circular pulse indicator for recording / speaking states.
 */
@Composable
fun FullFlowAudioPulseDot(
    isActive: Boolean,
    color: Color = Color(0xFFEF4444),
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.3f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = if (isActive) alpha else 0.4f))
    )
}
