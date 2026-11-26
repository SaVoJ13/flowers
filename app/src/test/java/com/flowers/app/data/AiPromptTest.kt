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

    @Test
    fun `photo identification prompt contains required multimodal fields`() {
        val photoPrompt = AiPrompt.identifyPlantFromPhotoPrompt()
        listOf(
            "is_plant",
            "name_uk",
            "name_latin",
            "confidence",
            "description",
            "light_level",
            "light",
            "default_check_interval_days",
            "watering_advice",
            "soil_type",
            "toxicity_info",
            "alternative_species"
        ).forEach { field ->
            assertTrue("missing field: $field", photoPrompt.contains("\"$field\""))
        }
    }

    @Test
    fun `diagnosePlantHealthPrompt contains required disease diagnosis fields and safety requirements`() {
        val diagnosisPrompt = AiPrompt.diagnosePlantHealthPrompt("Фікус Бенджаміна")
        listOf(
            "is_plant",
            "probable_cause",
            "confidence",
            "visible_symptoms",
            "alternative_causes",
            "first_aid_steps",
            "follow_up_advice",
            "preliminary_notice"
        ).forEach { field ->
            assertTrue("missing field: $field", diagnosisPrompt.contains("\"$field\""))
        }
        assertTrue(diagnosisPrompt.contains("Фікус Бенджаміна"))
        // Safety requirements: no toxic pesticides, priority on isolation and gentle care
        assertTrue(diagnosisPrompt.contains("ізоляція", ignoreCase = true))
        assertTrue(diagnosisPrompt.contains("пестицид", ignoreCase = true))
    }
}
