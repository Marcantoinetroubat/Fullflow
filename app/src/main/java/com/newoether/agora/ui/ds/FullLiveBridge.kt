package com.newoether.agora.ui.ds

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Pont de migration FullLive -> Design System + palette dark premium unifiée.
 *
 * Objectif 2026 : harmoniser les fonds/accents sombres entre les studios
 * (image, vidéo), Mesh, Wand, fulllive, et les écrans Agora standard —
 * en préservant le caractère "premium sombre" (pas d'aplanissement vers
 * MaterialTheme de base). Les écrans standards utilisent [MaterialTheme];
 * les surfaces héro/créatives utilisent [StudioDark] via ce bridge.
 *
 * Helpers [fullLiveAccent]/[fullLiveSecondary]/[fullLiveTertiary] : rôles
 * MaterialTheme ; les hex restent ici comme fallback documenté pour les
 * écrans en cours de migration.
 */
object FullLiveBridge {
    val Cyan = Color(0xFF38BDF8)
    val Indigo = Color(0xFF6366F1)
    val Amber = Color(0xFFF59E0B)
    val Purple = Color(0xFF8B5CF6)
    val Rose = Color(0xFFF43F5E)
    val Emerald = Color(0xFF10B981)
}

/**
 * Palette dark partagée (studios + Mesh + fulllive + Wand) : unifie les fonds
 * et surfaces "créatives" de l'app. Ne remplace PAS les fonds d'écrans
 * standards (ceux-ci restent sur MaterialTheme.colorScheme). Usage : fonds
 * hero, surfaces de studio, bordures d'accent.
 */
object StudioDark {
    val BackgroundDeep = Color(0xFF07090E)
    val SurfaceDark = Color(0xFF0F172A)
    val SurfaceVariantDark = Color(0xFF1E293B)
    val OutlineDark = Color(0xFF334155)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val AccentCyan = FullLiveBridge.Cyan
    val AccentPurple = FullLiveBridge.Purple
    val AccentAmber = FullLiveBridge.Amber
}

@Composable
fun fullLiveAccent(): Color = MaterialTheme.colorScheme.primary

@Composable
fun fullLiveSecondary(): Color = MaterialTheme.colorScheme.secondary

@Composable
fun fullLiveTertiary(): Color = MaterialTheme.colorScheme.tertiary
