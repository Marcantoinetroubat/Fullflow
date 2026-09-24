package com.newoether.agora.viewmodel

import com.newoether.agora.data.ConversationSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBackgroundGeneratorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultPromptTemplateFormatsTopicCorrectly() {
        val template = ConversationBackgroundGenerator.DEFAULT_BACKGROUND_PROMPT_TEMPLATE
        assertTrue(template.contains("%s"))

        val topic = "Physique quantique et voyage spatial"
        val formatted = template.replace("%s", topic)

        assertTrue(formatted.contains(topic))
        assertTrue(formatted.contains("wallpaper"))
        assertFalse(formatted.contains("%s"))
    }

    @Test
    fun fallbackFormattingWhenNoFormatSpecifier() {
        val customTemplate = "Style cyberpunk néon"
        val topic = "Architecture logicielle"

        val prompt = if (customTemplate.contains("%s")) {
            customTemplate.replace("%s", topic)
        } else {
            "$customTemplate\nThème : $topic"
        }

        assertEquals("Style cyberpunk néon\nThème : Architecture logicielle", prompt)
    }

    @Test
    fun conversationSettingsRetainsBackgroundImageUriSerialization() {
        val initial = ConversationSettings(temperature = 0.7f, backgroundImageUri = "/data/user/0/app/bg_123.jpg")
        val serialized = json.encodeToString(initial)

        assertTrue(serialized.contains("backgroundImageUri"))
        assertTrue(serialized.contains("bg_123.jpg"))

        val deserialized = json.decodeFromString<ConversationSettings>(serialized)
        assertEquals("/data/user/0/app/bg_123.jpg", deserialized.backgroundImageUri)
        assertEquals(0.7f, deserialized.temperature)
        assertFalse(deserialized.isAllNull())
    }

    @Test
    fun conversationSettingsDeserializationWithoutBackgroundUriDefaultsToNull() {
        val legacyJson = """{"temperature":0.8,"maxTokens":2048}"""
        val deserialized = json.decodeFromString<ConversationSettings>(legacyJson)

        assertNull(deserialized.backgroundImageUri)
        assertEquals(0.8f, deserialized.temperature)
        assertEquals(2048, deserialized.maxTokens)
    }

    @Test
    fun conversationSettingsIsAllNullIncludesBackgroundUri() {
        val emptySettings = ConversationSettings()
        assertTrue(emptySettings.isAllNull())

        val withBgOnly = ConversationSettings(backgroundImageUri = "/path/to/bg.jpg")
        assertFalse(withBgOnly.isAllNull())
    }
}
