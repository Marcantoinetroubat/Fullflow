package com.newoether.agora.ui.chat.bottombar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal val CHAT_BOTTOM_BAR_OUTER_RADIUS = 28.dp
internal val CHAT_BOTTOM_BAR_OUTER_SHAPE = RoundedCornerShape(CHAT_BOTTOM_BAR_OUTER_RADIUS)
internal val CHAT_DROPDOWN_MENU_SHAPE = RoundedCornerShape(16.dp)

internal fun contextUsageExceedsCompactThreshold(
    estimatedTokens: Int,
    tokenBudget: Int,
    thresholdPercent: Int,
): Boolean {
    val normalizedBudget = tokenBudget.coerceAtLeast(1)
    val normalizedPercent = thresholdPercent.coerceIn(50, 100)
    val threshold = ((normalizedBudget.toLong() * normalizedPercent + 99L) / 100L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return tokenBudget > 0 && estimatedTokens > threshold
}
