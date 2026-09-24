package com.newoether.agora.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.noOpBringIntoView(): Modifier = this.then(
    Modifier.bringIntoViewResponder(
        object : androidx.compose.foundation.relocation.BringIntoViewResponder {
            @ExperimentalFoundationApi
            override fun calculateRectForParent(localRect: Rect): Rect {
                return localRect
            }

            @ExperimentalFoundationApi
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // Swallow the request: do nothing!
            }
        }
    )
)

/**
 * Selection host for content inside a scroll container.
 *
 * Compose owns selection handles at the [SelectionContainer] boundary, so an interceptor placed
 * only on the content below it can be bypassed when selection begins. Keeping the interceptor on
 * the host itself guarantees that selection never repositions the surrounding conversation while
 * preserving normal user-driven scrolling.
 */
@Composable
fun NoAutoScrollSelectionContainer(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { latestContent.value() } }
    SelectionContainer(modifier = modifier.noOpBringIntoView()) {
        if (enabled) {
            movableContent()
        } else {
            DisableSelection(content = movableContent)
        }
    }
}
