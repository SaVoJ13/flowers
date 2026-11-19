package com.flowers.app.data

import com.flowers.app.data.remote.AiPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptTest {
    private val prompt = AiPrompt.plantCareCard("Філодендрон Біркін")

    @Test
    fun `prompt does not ask for toxicity`() {
        assertFalse(prompt.contains("toxic", ignoreCase = true))
        assertFalse(prompt.contains("токсичн", ignoreCase = true))
        assertFalse(prompt.contains("pets", ignoreCase = true))
    }

    @Test
    fun `prompt still carries query and required care fields`() {
        assertTrue(prompt.contains("«Філодендрон Біркін»"))
        listOf("name_uk", "name_latin", "popular_name", "difficulty", "light", "default_check_interval_days", "soil_type", "care_tip")
            .forEach { assertTrue("missing $it", prompt.contains("\"$it\"")) }
    }
}
