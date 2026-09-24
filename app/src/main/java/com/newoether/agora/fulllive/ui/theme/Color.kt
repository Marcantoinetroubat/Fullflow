package com.newoether.agora.fulllive.ui.theme

import com.newoether.agora.ui.ds.FullLiveBridge

/**
 * Alias de migration vers ui/ds/FullLiveBridge (source unique).
 * Les écrans doivent migrer vers MaterialTheme.colorScheme.primary/
 * secondary/tertiary. Ces vals restent pour compatibilité.
 */

@Deprecated("Utiliser MaterialTheme.colorScheme.primary via FullLiveBridge")
val FullLiveCyan = FullLiveBridge.Cyan

@Deprecated("Utiliser MaterialTheme.colorScheme.tertiary via FullLiveBridge")
val FullLiveIndigo = FullLiveBridge.Indigo

@Deprecated("Utiliser MaterialTheme.colorScheme.secondary via FullLiveBridge")
val FullLiveAmber = FullLiveBridge.Amber

@Deprecated("Utiliser MaterialTheme.colorScheme.tertiary via FullLiveBridge")
val FullLivePurple = FullLiveBridge.Purple

@Deprecated("Utiliser MaterialTheme.colorScheme.error via FullLiveBridge")
val FullLiveRose = FullLiveBridge.Rose

@Deprecated("Utiliser MaterialTheme.colorScheme.primary via FullLiveBridge")
val FullLiveEmerald = FullLiveBridge.Emerald

@Deprecated("Utiliser MaterialTheme.colorScheme.background")
val BackgroundDark = androidx.compose.ui.graphics.Color(0xFF070A14)

@Deprecated("Utiliser MaterialTheme.colorScheme.surface")
val SurfaceDark = androidx.compose.ui.graphics.Color(0xFF0F172A)

@Deprecated("Utiliser MaterialTheme.colorScheme.surfaceVariant")
val SurfaceVariantDark = androidx.compose.ui.graphics.Color(0xFF1E293B)

@Deprecated("Utiliser MaterialTheme.colorScheme.outline")
val CardBorderDark = androidx.compose.ui.graphics.Color(0xFF334155)

@Deprecated("Utiliser MaterialTheme.colorScheme.onBackground")
val TextPrimaryDark = androidx.compose.ui.graphics.Color(0xFFF8FAFC)

@Deprecated("Utiliser MaterialTheme.colorScheme.onSurfaceVariant")
val TextSecondaryDark = androidx.compose.ui.graphics.Color(0xFF94A3B8)

@Deprecated("Ne plus utiliser : contraste insuffisant")
val TextMutedDark = androidx.compose.ui.graphics.Color(0xFF64748B)

@Deprecated("Utiliser MaterialTheme.colorScheme.background")
val BackgroundLight = androidx.compose.ui.graphics.Color(0xFFF1F5F9)

@Deprecated("Utiliser MaterialTheme.colorScheme.surface")
val SurfaceLight = androidx.compose.ui.graphics.Color(0xFFFFFFFF)

@Deprecated("Utiliser MaterialTheme.colorScheme.surfaceVariant")
val SurfaceVariantLight = androidx.compose.ui.graphics.Color(0xFFE2E8F0)

@Deprecated("Utiliser MaterialTheme.colorScheme.outline")
val CardBorderLight = androidx.compose.ui.graphics.Color(0xFFCBD5E1)

@Deprecated("Utiliser MaterialTheme.colorScheme.onBackground")
val TextPrimaryLight = androidx.compose.ui.graphics.Color(0xFF0F172A)

@Deprecated("Utiliser MaterialTheme.colorScheme.onSurfaceVariant")
val TextSecondaryLight = androidx.compose.ui.graphics.Color(0xFF475569)

@Deprecated("Ne plus utiliser : contraste insuffisant")
val TextMutedLight = androidx.compose.ui.graphics.Color(0xFF94A3B8)
