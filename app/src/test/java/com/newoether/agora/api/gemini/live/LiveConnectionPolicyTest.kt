package com.newoether.agora.api.gemini.live

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveConnectionPolicyTest {

    @Test
    fun `backoff grows then caps at max`() {
        val d0 = LiveConnectionPolicy.reconnectDelayMs(0, 0.5)
        val d1 = LiveConnectionPolicy.reconnectDelayMs(1, 0.5)
        val d4 = LiveConnectionPolicy.reconnectDelayMs(4, 0.5)
        val d9 = LiveConnectionPolicy.reconnectDelayMs(9, 0.5)
        assertEquals(1_000L, d0)
        assertEquals(2_000L, d1)
        assertEquals(16_000L, d4)
        assertEquals(LiveConnectionPolicy.RECONNECT_MAX_DELAY_MS, d9)
    }

    @Test
    fun `jitter stays within minus25 to plus25 percent`() {
        val base = LiveConnectionPolicy.reconnectDelayMs(2, 0.0)
        val top = LiveConnectionPolicy.reconnectDelayMs(2, 1.0)
        // attempt 2 -> 4000ms capped; jitter 0.75x / 1.25x
        assertEquals(3_000L, base)
        assertEquals(5_000L, top)
    }

    @Test
    fun `close code classification`() {
        assertTrue(LiveConnectionPolicy.isFatalCloseCode(1008))
        assertFalse(LiveConnectionPolicy.isFatalCloseCode(1011))
        assertTrue(LiveConnectionPolicy.isCleanCloseCode(1000))
        assertFalse(LiveConnectionPolicy.isCleanCloseCode(1001))
        assertTrue(LiveConnectionPolicy.isFatalHttpCode(401))
        assertTrue(LiveConnectionPolicy.isFatalHttpCode(403))
        assertFalse(LiveConnectionPolicy.isFatalHttpCode(500))
    }

    @Test
    fun `friendly errors are actionable French`() {
        assertTrue(
            LiveConnectionPolicy.friendlyErrorMessage(UnknownHostException("dns"))
                .contains("Internet")
        )
        assertTrue(
            LiveConnectionPolicy.friendlyErrorMessage(SocketTimeoutException("t"))
                .contains("Délai")
        )
        assertTrue(
            LiveConnectionPolicy.friendlyErrorMessage(RuntimeException("x"), 401)
                .contains("Clé API")
        )
        assertTrue(
            LiveConnectionPolicy.friendlyErrorMessage(null, null).isNotBlank()
        )
    }
}

class SpeechActivityDetectorTest {

    @Test
    fun `quiet stays silent, speech latches, hangover releases`() {
        val detector = SpeechActivityDetector(threshold = 0.12f, hangoverMs = 600L)
        assertFalse(detector.update(0.01f, 0L)) // first quiet frame: silent (no prior speech)
        assertTrue(detector.update(0.5f, 100L))
        assertTrue(detector.update(0.01f, 200L)) // hangover
        assertFalse(detector.update(0.01f, 900L)) // 700ms of quiet > hangover
    }

    @Test
    fun `speech restarts hangover window`() {
        val detector = SpeechActivityDetector(threshold = 0.12f, hangoverMs = 600L)
        assertTrue(detector.update(0.5f, 0L))
        assertTrue(detector.update(0.01f, 500L))
        assertTrue(detector.update(0.5f, 550L)) // new speech resets quiet clock
        assertTrue(detector.update(0.01f, 1000L))
        assertFalse(detector.update(0.01f, 1200L))
    }
}
