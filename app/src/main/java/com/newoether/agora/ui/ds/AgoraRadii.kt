package com.newoether.agora.ui.ds

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Rayons canoniques. Interdit tout radius entier sans unité dp (bug px)
 * et les rayons 3/6/10/14/18/20/22/26dp hors tokens.
 * Exception documentée : [GroupJoint] 5dp, réservé aux jointures de
 * cartes groupées via [stackedShape] (Settings, Tasks).
 */
object AgoraRadii {
    val Xs = RoundedCornerShape(8.dp)
    val Sm = RoundedCornerShape(12.dp)
    val Md = RoundedCornerShape(16.dp)
    val Lg = RoundedCornerShape(24.dp)
    val Xl = RoundedCornerShape(28.dp)

    val Pill = CircleShape
    val Full = CircleShape

    // Sémantique
    val Card = Sm
    val Dialog = Lg
    val Sheet = Md
    val Field = Sm

    /** Jointure de cartes groupées — seul usage légitime du 5dp. */
    val GroupJoint = RoundedCornerShape(5.dp)

    /**
     * Coins d'une liste verticale de cartes groupées : Lg sur les bords
     * externes, joint 5dp là où deux cartes se touchent.
     */
    fun stackedShape(index: Int, count: Int): RoundedCornerShape = when {
        count <= 1 -> Lg
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp, bottomStart = 5.dp, bottomEnd = 5.dp
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = 5.dp, topEnd = 5.dp, bottomStart = 24.dp, bottomEnd = 24.dp
        )
        else -> GroupJoint
    }
}
