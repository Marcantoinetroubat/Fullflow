package com.newoether.agora.ui.ds

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Élévations canoniques : tonal vs shadow à ne plus intervertir. */
object AgoraElevation {
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 2.dp
    val Level3: Dp = 4.dp
    val Level4: Dp = 6.dp
    val Level5: Dp = 8.dp

    val CardTonal: Dp = Level1
    val FabTonal: Dp = Level3
    val SnackbarShadow: Dp = Level4
    val SheetShadow: Dp = Level5
}
