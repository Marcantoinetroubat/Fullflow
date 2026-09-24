package com.newoether.agora.studio.video

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiVideoServiceTest {

    @Test
    fun studioVideoModel_defaultIsVeo3Fast() {
        assertEquals(StudioVideoModel.VEO_3_FAST, StudioVideoModel.DEFAULT)
        assertTrue(StudioVideoModel.VEO_3_FAST.isPrimary)
    }

    @Test
    fun studioVideoModel_fromIdResolvesCorrectly() {
        assertEquals(
            StudioVideoModel.VEO_3_FAST,
            StudioVideoModel.fromId("veo-3.1-fast-generate-preview")
        )
        assertEquals(
            StudioVideoModel.VEO_3,
            StudioVideoModel.fromId("veo-3.1-generate-preview")
        )
        assertEquals(
            StudioVideoModel.VEO_2,
            StudioVideoModel.fromId("veo-2.0-generate-001")
        )
        assertEquals(
            StudioVideoModel.OMNI,
            StudioVideoModel.fromId("gemini-omni-1.1-flash")
        )
        // Fallback on unknown ID
        assertEquals(
            StudioVideoModel.DEFAULT,
            StudioVideoModel.fromId("unknown-model-id-xyz")
        )
    }

    @Test
    fun extractVideoUri_handlesMldevGeneratedSamples() {
        val json = JSONObject("""
            {
                "done": true,
                "response": {
                    "generateVideoResponse": {
                        "generatedSamples": [
                            {
                                "video": {
                                    "uri": "https://generativelanguage.googleapis.com/v1beta/files/sample123"
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent())

        val uri = GeminiVideoService.instance.extractVideoUri(json)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/sample123", uri)
    }

    @Test
    fun extractVideoUri_handlesGeneratedVideosShape() {
        val json = JSONObject("""
            {
                "done": true,
                "response": {
                    "generatedVideos": [
                        {
                            "video": {
                                "uri": "https://generativelanguage.googleapis.com/v1beta/files/veo789"
                            }
                        }
                    ]
                }
            }
        """.trimIndent())

        val uri = GeminiVideoService.instance.extractVideoUri(json)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/veo789", uri)
    }

    @Test
    fun extractVideoUri_handlesLegacyGeneratedVideosInGenerateVideoResponse() {
        val json = JSONObject("""
            {
                "done": true,
                "response": {
                    "generateVideoResponse": {
                        "generatedVideos": [
                            {
                                "video": {
                                    "uri": "https://generativelanguage.googleapis.com/v1beta/files/legacy456"
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent())

        val uri = GeminiVideoService.instance.extractVideoUri(json)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/legacy456", uri)
    }

    @Test
    fun extractVideoUri_handlesOmniInteractionsOutputVideo() {
        val json = JSONObject("""
            {
                "status": "completed",
                "output_video": {
                    "uri": "https://generativelanguage.googleapis.com/v1beta/files/omni123"
                }
            }
        """.trimIndent())

        val uri = GeminiVideoService.instance.extractVideoUri(json)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/omni123", uri)
    }

    @Test
    fun extractVideoUri_handlesOmniInteractionsStepsContent() {
        val json = JSONObject("""
            {
                "status": "completed",
                "steps": [
                    {
                        "type": "model_output",
                        "content": [
                            {
                                "type": "video",
                                "uri": "https://generativelanguage.googleapis.com/v1beta/files/stepvideo1"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent())

        val uri = GeminiVideoService.instance.extractVideoUri(json)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/files/stepvideo1", uri)
    }

    @Test
    fun extractVideoUri_handlesDirectUrlField() {
        val json = JSONObject("""
            {
                "status": "completed",
                "url": "https://api.openai.com/v1/videos/sora123/content"
            }
        """.trimIndent())

        val uri = GeminiVideoService.instance.extractVideoUri(json)
        assertEquals("https://api.openai.com/v1/videos/sora123/content", uri)
    }

    @Test
    fun extractVideoUri_returnsNullOnEmptyOrFailed() {
        val empty = JSONObject("{}")
        assertNull(GeminiVideoService.instance.extractVideoUri(empty))
    }
}
