package com.fishadopt.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveMaskerTest {

    @Test
    fun masksApiKey() {
        val out = SensitiveMasker.mask("key: sk-abcdef123456")
        assertFalse(out.contains("sk-abcdef123456"))
        assertTrue(out.contains(SensitiveMasker.MASK))
    }

    @Test
    fun masksAuthorizationHeader() {
        val out = SensitiveMasker.mask("Authorization: Bearer abc123xyz")
        assertEquals("Authorization: Bearer ${SensitiveMasker.MASK}", out)
    }

    @Test
    fun masksCookieHeader() {
        val out = SensitiveMasker.mask("Cookie: session=abcdef; path=/")
        assertEquals("Cookie: ${SensitiveMasker.MASK}", out)
    }

    @Test
    fun masksApiKeyKeyValue() {
        val out = SensitiveMasker.mask("api_key=secret_value_123")
        assertEquals("api_key=${SensitiveMasker.MASK}", out)
    }

    @Test
    fun leavesPlainTextUnchanged() {
        val plain = "正在启动容器，端口 3080 就绪"
        assertEquals(plain, SensitiveMasker.mask(plain))
    }
}
