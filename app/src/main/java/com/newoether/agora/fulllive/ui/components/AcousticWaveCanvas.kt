package com.newoether.agora.fulllive.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ligne d'Onde Acoustique Utilisateur FULLLIVE
 * Oscille au centre en réponse directe au microphone de l'utilisateur,
 * atténuée aux bords par une fenêtre sinusoïdale (sin²).
 */
@Composable
fun AcousticWaveCanvas(
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    amplitudeLevel: Float = 0f, // 0.0 to 1.0
    waveColor: Color = Color(0xFFF59E0B) // Amber accent
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_oscillation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val width = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        val path = Path()
        val steps = 120
        val maxAmp = (canvasHeight / 2f - 6f) * (0.15f + amplitudeLevel * 0.85f)

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val x = progress * width
            // sin^2 windowing envelope for seamless edges at 0
            val envelope = sin(progress * PI).toFloat()
            val windowed = envelope * envelope

            val wave1 = sin(progress * 14f + phase) * 0.55f
            val wave2 = sin(progress * 26f - phase * 1.3f) * 0.30f
            val wave3 = sin(progress * 42f + phase * 1.8f) * 0.15f

            val y = centerY + (wave1 + wave2 + wave3) * maxAmp * windowed

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Glow pass
        drawPath(
            path = path,
            color = waveColor.copy(alpha = 0.35f),
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        // Core bright line
        drawPath(
            path = path,
            color = waveColor.copy(alpha = 0.95f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round)
        )
    }
}
