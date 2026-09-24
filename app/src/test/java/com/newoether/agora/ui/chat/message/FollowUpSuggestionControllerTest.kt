package com.newoether.agora.ui.chat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Test

class FollowUpSuggestionControllerTest {

    @After
    fun cleanup() {
        FollowUpSuggestionController.onSuggestionSelected = null
        FollowUpSuggestionController.onSuggestionSend = null
    }

    @Test
    fun `sendSuggestion falls back to selectSuggestion when onSuggestionSend is null`() {
        var selected: String? = null
        FollowUpSuggestionController.onSuggestionSelected = { selected = it }
        FollowUpSuggestionController.onSuggestionSend = null

        FollowUpSuggestionController.sendSuggestion("test prompt")

        assertEquals("test prompt", selected)
    }

    @Test
    fun `sendSuggestion uses onSuggestionSend when set`() {
        var sent: String? = null
        var selected: String? = null
        FollowUpSuggestionController.onSuggestionSend = { sent = it }
        FollowUpSuggestionController.onSuggestionSelected = { selected = it }

        FollowUpSuggestionController.sendSuggestion("direct send")

        assertEquals("direct send", sent)
        assertNull("selectSuggestion should not fire when onSuggestionSend handles it", selected)
    }

    @Test
    fun `sendSuggestion trims whitespace before dispatching`() {
        var received: String? = null
        FollowUpSuggestionController.onSuggestionSend = { received = it }

        FollowUpSuggestionController.sendSuggestion("  trimmed  ")

        assertEquals("trimmed", received)
    }

    @Test
    fun `sendSuggestion ignores blank text without calling any handler`() {
        var called = false
        FollowUpSuggestionController.onSuggestionSend = { called = true }
        FollowUpSuggestionController.onSuggestionSelected = { called = true }

        FollowUpSuggestionController.sendSuggestion("   ")

        assertFalse("No handler should fire for blank text", called)
    }

    @Test
    fun `selectSuggestion trims and dispatches to onSuggestionSelected`() {
        var received: String? = null
        FollowUpSuggestionController.onSuggestionSelected = { received = it }

        FollowUpSuggestionController.selectSuggestion("  hello  ")

        assertEquals("hello", received)
    }

    @Test
    fun `selectSuggestion ignores blank text`() {
        var called = false
        FollowUpSuggestionController.onSuggestionSelected = { called = true }

        FollowUpSuggestionController.selectSuggestion("")

        assertFalse(called)
    }
}
