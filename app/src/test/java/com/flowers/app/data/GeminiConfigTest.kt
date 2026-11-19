package com.flowers.app.data

import com.flowers.app.data.remote.GEMINI_MODEL
import com.flowers.app.data.remote.geminiGenerateContentUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiConfigTest {
    @Test
    fun `model is not the retired 1_5 family`() {
        assertEquals("gemini-3.5-flash-lite", GEMINI_MODEL)
        assertFalse(GEMINI_MODEL.contains("1.5"))
    }

    @Test
    fun `url is built from the single model constant and carries no key`() {
        val url = geminiGenerateContentUrl()
        assertTrue(url.endsWith("/models/$GEMINI_MODEL:generateContent"))
        assertFalse(url.contains("key="))
    }
}
