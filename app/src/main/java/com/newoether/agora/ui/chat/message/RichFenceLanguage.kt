package com.newoether.agora.ui.chat.message

/**
 * Detection of rich, non-plaintext Markdown code fence languages that FullFlow
 * renders as interactive visual blocks instead of raw code.
 *
 * Pure Kotlin (no Android imports) so unit tests stay JVM-only.
 */
object RichFenceLanguage {

    const val MERMAID = "mermaid"
    const val CHART = "chart"

    fun isMermaid(language: String?): Boolean =
        language?.trim()?.equals(MERMAID, ignoreCase = true) == true

    fun isChart(language: String?): Boolean =
        language?.trim()?.equals(CHART, ignoreCase = true) == true

    fun isRichFence(language: String?): Boolean =
        isMermaid(language) || isChart(language)
}
