package com.newoether.agora.fulllive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.ui.theme.ColorSchemePreset
import com.newoether.agora.ui.theme.ThemeMode

/**
 * Wrapper de migration : FullLive est désormais un preset OCEAN d'AgoraTheme
 * (source unique de vérité). Conservé pour compatibilité avec
 * fulllive/MainActivity et ui/chat/live/FullLiveHost.
 * Les palettes statiques Dark/Light ci-dessous sont supprimées au profit
 * de materialkolor ; voir ui/ds/FullLiveBridge pour le mapping Cyan->primary.
 */
@Deprecated(
    message = "Utiliser AgoraTheme(preset=OCEAN). Conservé comme alias de migration.",
    replaceWith = ReplaceWith(
        "AgoraTheme",
        "com.newoether.agora.ui.theme.AgoraTheme",
    ),
)
@Composable
fun FullLiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    AgoraTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        colorSchemePreset = ColorSchemePreset.OCEAN,
    ) {
        content()
    }
}
