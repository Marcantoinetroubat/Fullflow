package com.newoether.agora.ui.chat.message.chart

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON contract for ```chart fenced blocks (dashboards & dynamic graphs).
 *
 * Expected payload (all fields optional except `series`):
 * ```json
 * {
 *   "type": "bar" | "line" | "pie",
 *   "title": "Titre du graphique",
 *   "labels": ["Jan", "Fév", "Mar"],
 *   "series": [{ "name": "Ventes", "values": [12.5, 9.0, 14.2] }]
 * }
 * ```
 *
 * Pure Kotlin (no Android imports) so unit tests stay JVM-only.
 */
@Serializable
data class ChartSpec(
    val type: String = "bar",
    val title: String = "",
    val labels: List<String> = emptyList(),
    val series: List<ChartSeries> = emptyList(),
) {
    val chartType: ChartType
        get() = ChartType.fromWire(type)
}

@Serializable
data class ChartSeries(
    val name: String = "",
    val values: List<Double> = emptyList(),
)

enum class ChartType(val wire: String) {
    BAR("bar"),
    LINE("line"),
    PIE("pie"),
    ;

    companion object {
        fun fromWire(raw: String?): ChartType = when (raw?.trim()?.lowercase()) {
            "line", "courbe", "courbes" -> LINE
            "pie", "donut", "camembert" -> PIE
            else -> BAR
        }
    }
}

object ChartSpecParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Parses a ```chart block body into a validated [ChartSpec].
     * Tolerates surrounding markdown fences and prose around the JSON object.
     * Returns null when no usable data can be extracted (caller falls back to code view).
     */
    fun parse(raw: String): ChartSpec? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val withoutFence = trimmed
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```chart")
            .removePrefix("```").removeSuffix("```")
            .trim()

        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null

        val spec = runCatching {
            json.decodeFromString<ChartSpec>(withoutFence.substring(start, end + 1))
        }.getOrNull() ?: return null

        return sanitize(spec)
    }

    /** Drops empty series, pads/truncates labels, and rejects specs with no usable value. */
    internal fun sanitize(spec: ChartSpec): ChartSpec? {
        val usableSeries = spec.series
            .map { it.copy(values = it.values.filter { v -> v.isFinite() }) }
            .filter { it.values.isNotEmpty() }
        if (usableSeries.isEmpty()) return null

        val pointCount = usableSeries.maxOf { it.values.size }
        val labels = when {
            spec.labels.size >= pointCount -> spec.labels.take(pointCount)
            else -> spec.labels + ((spec.labels.size + 1)..pointCount).map { "Pt $it" }
        }

        // A pie chart only makes sense with a single series (first one wins).
        return if (spec.chartType == ChartType.PIE) {
            spec.copy(labels = labels, series = listOf(usableSeries.first()))
        } else {
            spec.copy(labels = labels, series = usableSeries)
        }
    }
}
