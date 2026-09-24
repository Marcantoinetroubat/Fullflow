package com.newoether.agora.ui.chat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSettingsTest {

    @Test
    fun ttsEngineModeEnumValuesContainsAllFourEngines() {
        val modes = TtsEngineMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(TtsEngineMode.SYSTEM))
        assertTrue(modes.contains(TtsEngineMode.GEMINI_CLOUD))
        assertTrue(modes.contains(TtsEngineMode.OPENAI_CLOUD))
        assertTrue(modes.contains(TtsEngineMode.KOKORO_LOCAL))
    }

    @Test
    fun ttsEngineModeValueOfResolvesCorrectly() {
        assertEquals(TtsEngineMode.SYSTEM, TtsEngineMode.valueOf("SYSTEM"))
        assertEquals(TtsEngineMode.GEMINI_CLOUD, TtsEngineMode.valueOf("GEMINI_CLOUD"))
        assertEquals(TtsEngineMode.OPENAI_CLOUD, TtsEngineMode.valueOf("OPENAI_CLOUD"))
        assertEquals(TtsEngineMode.KOKORO_LOCAL, TtsEngineMode.valueOf("KOKORO_LOCAL"))
    }

    @Test
    fun speedCoercionKeepsValidBounds() {
        val minSpeed = 0.3f.coerceIn(0.5f, 2.0f)
        val maxSpeed = 3.5f.coerceIn(0.5f, 2.0f)
        val normalSpeed = 1.25f.coerceIn(0.5f, 2.0f)

        assertEquals(0.5f, minSpeed, 0.001f)
        assertEquals(2.0f, maxSpeed, 0.001f)
        assertEquals(1.25f, normalSpeed, 0.001f)
    }

    @Test
    fun openAiStandardVoicesAreSupported() {
        val expectedVoices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
        assertEquals(6, expectedVoices.size)
        assertTrue(expectedVoices.contains("alloy"))
        assertTrue(expectedVoices.contains("nova"))
    }
}
