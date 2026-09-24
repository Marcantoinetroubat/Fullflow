package com.newoether.agora.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDatePickerSourceContractTest {
    @Test
    fun `Once date picker keeps the Material modal height without taking over mode or IME`() {
        val source = sourceFile(
            "app/src/main/java/com/newoether/agora/ui/tasks/TaskEditorSupportingComponents.kt",
        )
        val picker = source
            .substringAfter("internal fun TaskDatePickerDialog(")
            .substringBefore("internal fun TaskTimePickerDialog(")

        assertTrue(picker.contains("modifier = Modifier.height(568.dp)"))
        assertTrue(picker.contains("showModeToggle = true"))
        assertFalse(picker.contains("pendingCalendarMode"))
        assertFalse(picker.contains("displayMode = DatePickerDisplayMode"))
        assertFalse(picker.contains("LocalSoftwareKeyboardController"))
        assertFalse(picker.contains("delay("))
    }

    private fun sourceFile(relativePath: String): String {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Reached filesystem root")
        }
        error("Unable to locate $relativePath")
    }
}
