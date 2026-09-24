package com.newoether.agora.ui.ds

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Source unique de vérité pour les espacements.
 * Bannit les valeurs arbitraires (10/14/18/20dp...) hors de cette liste.
 */
object AgoraSpacing {
    val Xxs: Dp = 2.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 20.dp
    val Xxl: Dp = 24.dp
    val Xxxl: Dp = 32.dp

    // Usages sémantiques
    val ScreenHorizontal: Dp = Lg
    val CardPadding: Dp = Lg
    val RowGap: Dp = Md
    val ItemGap: Dp = Sm
    val SectionGap: Dp = Xxl
}
