package com.newoether.agora.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SsrfGuardTest {

    @Test
    fun `blocks localhost and local domains`() {
        assertTrue(SsrfGuard.isPrivateHost("localhost"))
        assertTrue(SsrfGuard.isPrivateHost(".local"))
        assertTrue(SsrfGuard.isPrivateHost("myhost.local"))
        assertTrue(SsrfGuard.isPrivateHost("service.internal"))
    }

    @Test
    fun `blocks loopback IPs`() {
        assertTrue(SsrfGuard.isPrivateHost("127.0.0.1"))
        assertTrue(SsrfGuard.isPrivateHost("127.0.0.2"))
    }

    @Test
    fun `blocks RFC1918 private ranges`() {
        assertTrue(SsrfGuard.isPrivateHost("10.0.0.1"))
        assertTrue(SsrfGuard.isPrivateHost("172.16.0.1"))
        assertTrue(SsrfGuard.isPrivateHost("192.168.1.1"))
    }

    @Test
    fun `blocks link-local and any-local`() {
        assertTrue(SsrfGuard.isPrivateHost("0.0.0.0"))
        assertTrue(SsrfGuard.isPrivateHost("169.254.1.1"))
    }

    @Test
    fun `fail-closed for unresolvable host`() {
        // A domain that definitely doesn't resolve
        val result = SsrfGuard.isPrivateHost("this-host-does-not-exist-xyz123.invalid")
        assertTrue("Unresolvable DNS should be blocked", result)
    }

    @Test
    fun `rejects non-http schemes`() {
        assertFalse(SsrfGuard.isSafeUrl("ftp://example.com/file"))
        assertFalse(SsrfGuard.isSafeUrl("file:///etc/passwd"))
        assertFalse(SsrfGuard.isSafeUrl("javascript:alert(1)"))
    }

    @Test
    fun `rejects malformed URLs`() {
        assertFalse(SsrfGuard.isSafeUrl(""))
        assertFalse(SsrfGuard.isSafeUrl("not a url"))
        assertFalse(SsrfGuard.isSafeUrl("://missing-scheme"))
    }

    @Test
    fun `blocks localhost URLs`() {
        assertFalse(SsrfGuard.isSafeUrl("http://localhost:8080/admin"))
        assertFalse(SsrfGuard.isSafeUrl("http://127.0.0.1/admin"))
        assertFalse(SsrfGuard.isSafeUrl("http://192.168.1.1/router"))
    }

    @Test
    fun `allows public domains`() {
        // These resolve to public IPs — should be safe
        assertTrue(SsrfGuard.isSafeUrl("https://example.com/page"))
        assertTrue(SsrfGuard.isSafeUrl("https://www.wikipedia.org"))
    }
}
