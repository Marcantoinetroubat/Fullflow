package com.newoether.agora.ui.ds

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Ressorts M3 Expressive : remplacent les durées fixes (tween) pour les
 * interactions (press, dismiss, container resize). Ne remplace PAS
 * [LocalAgoraMotionPolicy] — celui-ci censure/reduced-motion avant l'usage.
 * Principe : spatial = mouvement, effects = couleur/opacité.
 */
object AgoraSprings {
    val Default = spring<Any>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val Snappy = spring<Any>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val Gentle = spring<Any>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    /** Ressort visuel pour "shrink/expand" de containers (bottom sheet, dialog). */
    val Container = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
