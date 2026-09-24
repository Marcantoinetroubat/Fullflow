package com.newoether.agora.data.notes

import com.newoether.agora.data.local.ObsidianTaskEntity
import java.security.MessageDigest

/**
 * Parses Obsidian Tasks from Markdown and toggles their status bidirectionally.
 *
 * Pure Kotlin (no Android imports) so unit tests stay JVM-only.
 *
 * Supported syntax:
 * - ``- [ ]``  todo        → isChecked = false
 * - ``- [x]``  done        → isChecked = true
 * - ``- [/]``  in-progress  → isChecked = false (tracked separately)
 * - Priority:  🔺 ⏫ #p1 → HIGH,  🔼 #p2 → MEDIUM,  🔽 ⏬ #p3 → LOW
 * - Due date:  📅 YYYY-MM-DD
 * - Done date: ✅ YYYY-MM-DD
 */
object ObsidianTaskParser {

    private val CHECKBOX_REGEX = Regex("""^-\s*\[[ xX/]]\s*""")
    private val DATE_REGEX = Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val DONE_DATE_REGEX = Regex("✅\\s*(\\d{4}-\\d{2}-\\d{2})")
    private val PRIORITY_CHARS = setOf("🔺", "⏫", "🔼", "🔽", "⏬")

    data class ParsedTask(
        val lineIndex: Int,
        val text: String,
        val isChecked: Boolean,
        val priority: String?,
        val dueDate: String?,
        val rawLine: String,
    )

    /**
     * Extracts all Obsidian tasks from a Markdown note's content.
     */
    fun parseTasks(filePath: String, content: String): List<ObsidianTaskEntity> {
        val tasks = mutableListOf<ObsidianTaskEntity>()
        content.lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (!isTaskLine(trimmed)) return@forEachIndexed

            val isChecked = trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]")
            var desc = CHECKBOX_REGEX.replace(trimmed, "")

            val priority = extractPriority(desc)
            val dueDate = DATE_REGEX.find(desc)?.groupValues?.getOrNull(1)
            val cleanDesc = cleanDescription(desc)

            tasks.add(
                ObsidianTaskEntity(
                    noteFilePath = filePath,
                    lineIndex = index,
                    text = cleanDesc,
                    isChecked = isChecked,
                    priority = priority,
                    dueDate = dueDate,
                ),
            )
        }
        return tasks
    }

    /**
     * Toggles a task line in Markdown: ``- [ ]`` ↔ ``- [x]`` (with ✅ date on completion).
     * Returns the rewritten line, or the original if it's not a task line.
     */
    fun toggleLine(rawLine: String, isChecked: Boolean): String {
        val trimmed = rawLine.trimStart()
        if (!isTaskLine(trimmed)) return rawLine

        return if (isChecked) {
            // Mark as done: add ✅ date if not present
            val doneDate = DONE_DATE_REGEX.find(trimmed)
            if (doneDate != null) {
                trimmed.replaceFirst(Regex("""\[\s]"""), "[x]")
            } else {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())
                val base = CHECKBOX_REGEX.replace(trimmed, "")
                "- [x] $base ✅ $today"
            }
        } else {
            // Mark as todo: remove ✅ date, change [x] → [ ]
            val cleaned = trimmed
                .replaceFirst(Regex("""\[[xX]]"""), "[ ]")
                .replace(Regex("✅\\s*\\d{4}-\\d{2}-\\d{2}\\s*"), "")
                .trim()
            cleaned
        }
    }

    fun isTaskLine(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("- [ ]") || t.startsWith("- [x]") || t.startsWith("- [X]") || t.startsWith("- [/]")
    }

    private fun extractPriority(desc: String): String? = when {
        desc.contains("🔺") || desc.contains("⏫") || desc.contains("#p1") -> "HIGH"
        desc.contains("🔼") || desc.contains("#p2") -> "MEDIUM"
        desc.contains("🔽") || desc.contains("⏬") || desc.contains("#p3") -> "LOW"
        else -> null
    }

    private fun cleanDescription(desc: String): String {
        var result = desc
        result = DATE_REGEX.replace(result, "")
        result = DONE_DATE_REGEX.replace(result, "")
        for (ch in PRIORITY_CHARS) result = result.replace(ch, "")
        return result.trim()
    }

    fun contentHash(content: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
