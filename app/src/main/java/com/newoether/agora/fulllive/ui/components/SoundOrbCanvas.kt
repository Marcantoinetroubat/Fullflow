package com.newoether.agora.fulllive.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Orbe Acoustique Multidimensionnel FULLLIVE
 * Rendu performant sur Jetpack Compose Canvas réagissant en temps réel aux fréquences
 * audio de l'IA (volume, basses, médiums, aigus) avec morphing ondulatoire et halos.
 */
@Composable
fun SoundOrbCanvas(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    isActive: Boolean = false,
    isSpeaking: Boolean = false,
    volumeLevel: Float = 0f,
    bassLevel: Float = 0f,
    midLevel: Float = 0f,
    highLevel: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
            val baseRadius = min(canvasWidth, canvasHeight) * 0.26f

            // Dynamic color state
            val coreColor1 = when {
                isSpeaking -> Color(0xFFC084FC) // Purple-400
                isActive -> Color(0xFF38BDF8)   // Cyan-400
                else -> Color(0xFF64748B)       // Slate-500
            }
            val coreColor2 = when {
                isSpeaking -> Color(0xFF818CF8) // Indigo-400
                isActive -> Color(0xFF0284C7)   // Sky-600
                else -> Color(0xFF334155)       // Slate-700
            }
            val accentColor = when {
                isSpeaking -> Color(0xFFF43F5E) // Rose
                isActive -> Color(0xFFF59E0B)   // Amber
                else -> Color(0xFF475569)
            }

            val dynamicExpansion = if (isActive) (volumeLevel * 30f + bassLevel * 20f) else 0f

            // 1. Atmosphere Radial Glow
            val glowRadius = baseRadius * 1.85f + dynamicExpansion * 1.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor1.copy(alpha = if (isSpeaking) 0.40f else if (isActive) 0.22f else 0.08f),
                        coreColor2.copy(alpha = if (isSpeaking) 0.20f else if (isActive) 0.12f else 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // 2. Central Luminous Energy Sphere
            val innerRadius = baseRadius * 0.82f + dynamicExpansion * 0.7f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isSpeaking) 0.95f else if (isActive) 0.85f else 0.4f),
                        coreColor1.copy(alpha = if (isSpeaking) 0.85f else 0.65f),
                        coreColor2.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerRadius
                ),
                radius = innerRadius,
                center = center
            )

            // 3. Multi-Harmonic Morphing Rings (4 harmonic layers)
            val ringLayers = listOf(
                RingConfig(radiusMult = 1.05f, strokeWidth = 2.4f, speedMult = 1.0f, color = coreColor1, lobes = 3),
                RingConfig(radiusMult = 1.18f, strokeWidth = 2.0f, speedMult = -1.2f, color = coreColor2, lobes = 4),
                RingConfig(radiusMult = 1.30f, strokeWidth = 1.6f, speedMult = 1.5f, color = accentColor, lobes = 5),
                RingConfig(radiusMult = 1.42f, strokeWidth = 1.2f, speedMult = -0.8f, color = coreColor1.copy(alpha = 0.5f), lobes = 6)
            )

            val pointsCount = 120
            val phaseRad = Math.toRadians(idlePhase.toDouble()).toFloat()

            ringLayers.forEach { cfg ->
                val path = Path()
                val targetRadius = baseRadius * cfg.radiusMult + dynamicExpansion

                for (i in 0..pointsCount) {
                    val angle = (i.toFloat() / pointsCount) * 2f * Math.PI.toFloat()
                    val waveIdle = sin(angle * cfg.lobes + phaseRad * cfg.speedMult) * 3f
                    val waveAudio = if (isActive) {
                        (sin(angle * 2f - phaseRad) * bassLevel * 14f +
                         cos(angle * 3f + phaseRad) * midLevel * 10f +
                         sin(angle * 5f - phaseRad * 1.5f) * highLevel * 6f)
                    } else 0f

                    val r = targetRadius + waveIdle + waveAudio
                    val x = center.x + cos(angle) * r
                    val y = center.y + sin(angle) * r

                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                drawPath(
                    path = path,
                    color = cfg.color.copy(alpha = if (isActive) 0.85f else 0.45f),
                    style = Stroke(
                        width = cfg.strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

private data class RingConfig(
    val radiusMult: Float,
    val strokeWidth: Float,
    val speedMult: Float,
    val color: Color,
    val lobes: Int
)
