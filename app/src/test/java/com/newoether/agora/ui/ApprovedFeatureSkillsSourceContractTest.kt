package com.newoether.agora.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedFeatureSkillsSourceContractTest {
    @Test
    fun skillsAreSavedCatalogToolsWithRequestResolvedPromptAndNoActiveSkill() {
        val root = sourceRoot()
        val manager = source(root, "com/newoether/agora/data/SkillManager.kt")
        val provider = source(root, "com/newoether/agora/tool/SkillToolProvider.kt")
        val builder = source(
            root,
            "com/newoether/agora/viewmodel/GenerationRequestBuilder.kt",
        )
        val exporter = source(root, "com/newoether/agora/data/DataExporter.kt")
        val importer = source(root, "com/newoether/agora/data/DataImporter.kt")
        val settings = source(
            root,
            "com/newoether/agora/ui/settings/SettingsSkillsPage.kt",
        )

        assertTrue(manager.contains("File(context.filesDir, \"skill_db\")"))
        assertTrue(manager.contains("fun catalog(): String"))
        assertFalse(manager.contains("active_skill"))
        assertTrue(provider.contains("list_skill_files"))
        assertTrue(provider.contains("read_skill_file"))
        assertTrue(provider.contains("create_skill_file"))
        assertTrue(provider.contains("edit_skill_file"))
        assertTrue(provider.contains("delete_skill_file"))
        assertFalse(provider.contains("update_active_skill"))
        assertTrue(builder.contains("skillCatalog = if (skillReadAccess) skillManager.catalog()"))
        assertTrue(builder.contains("if (includeSkillCatalog) skillManager.catalog() else \"\""))
        assertTrue(builder.contains("PredefinedVariables.SKILL_CATALOG to skillCatalog"))
        assertTrue(builder.contains("skillCatalog = skillCatalogDeferred.await()"))
        assertFalse(builder.contains("effectiveSystemPromptWithSkills"))
        assertTrue(exporter.contains("memories/skill_db/"))
        assertTrue(importer.contains("memories/skill_db/"))
        assertTrue(settings.contains("settings.accessSkills.collectAsState()"))
        assertFalse(settings.contains("Active Skill"))
    }

    private fun source(root: File, path: String): String =
        File(root, path).readText().replace("\r\n", "\n")

    private fun sourceRoot(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            listOf(
                File(directory, "app/src/main/java"),
                File(directory, "src/main/java"),
            ).firstOrNull(File::isDirectory)?.let { return it }
            directory = directory.parentFile ?: error("Reached filesystem root")
        }
        error("Unable to locate source root")
    }
}
