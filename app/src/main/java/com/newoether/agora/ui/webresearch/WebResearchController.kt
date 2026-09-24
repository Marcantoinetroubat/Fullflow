package com.newoether.agora.ui.webresearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Controller for the Web Research page (Perplexity/Morphic-style search).
 */
object WebResearchController {
    var visible by mutableStateOf(false)
        private set

    fun open() { visible = true }
    fun close() { visible = false }
}
