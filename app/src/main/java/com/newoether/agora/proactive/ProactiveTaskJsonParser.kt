package com.newoether.agora.proactive

import kotlinx.serialization.json.Json

/**
 * Parses the raw JSON array emitted by the proactive-intelligence LLM call into
 * validated [ProactiveTask]s. Tolerates markdown fences and prose around the array.
 * Pure Kotlin (no Android imports) so unit tests stay JVM-only.
 */
object ProactiveTaskJsonParser {

    /** Number of proactive orientations surfaced on the home screen. */
    const val MAX_TASKS = 5

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawJson: String): List<ProactiveTask> {
        val clean = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonStart = clean.indexOf('[')
        val jsonEnd = clean.lastIndexOf(']')
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return emptyList()

        val jsonSubstring = clean.substring(jsonStart, jsonEnd + 1)
        return try {
            val list = json.decodeFromString<List<ProactiveTask>>(jsonSubstring)
            list.filter { it.title.isNotBlank() && it.prompt.isNotBlank() }.take(MAX_TASKS)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
