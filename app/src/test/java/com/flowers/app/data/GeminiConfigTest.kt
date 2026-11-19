package com.flowers.app.data

import com.flowers.app.data.remote.GEMINI_MODEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiConfigTest {
    @Test
    fun `gemini model is constant and uses supported flash lite family`() {
        assertEquals("gemini-3.5-flash-lite", GEMINI_MODEL)
        assertFalse("Must not use deprecated 1.5 family", GEMINI_MODEL.contains("1.5"))
        assertFalse("Must not use volatile -latest tag", GEMINI_MODEL.contains("latest"))
        assertTrue("Model identifier must not be blank", GEMINI_MODEL.isNotBlank())
    }
}
