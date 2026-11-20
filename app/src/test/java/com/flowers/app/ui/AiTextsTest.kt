package com.flowers.app.ui

import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.ui.components.AiTexts
import com.flowers.app.ui.components.isAiGenerated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextsTest {

    private fun plant(id: String) = CatalogPlantEntity(
        id = id, nameUk = "н", nameLatin = "n", popularName = "п", difficulty = "Легко",
        light = "л", defaultCheckIntervalDays = 7, soilType = "г", toxicToPets = false
    )

    @Test
    fun `disclaimer starts with required phrase`() {
        assertTrue(AiTexts.DISCLAIMER.startsWith("Порада ШІ, не гарантія"))
    }

    @Test
    fun `privacy hint asks not to enter personal data`() {
        assertTrue(AiTexts.PRIVACY_HINT.contains("Не вказуйте особисті дані"))
        assertTrue(AiTexts.PRIVACY_HINT.contains("Google"))
    }

    @Test
    fun `ai generated cards are detected by id prefix and catalog cards are not`() {
        assertTrue(plant("ai_1700000000_ficus_lyrata").isAiGenerated())
        assertFalse(plant("monstera_deliciosa").isAiGenerated())
        assertFalse(AiTexts.isAiGeneratedId(null))
        assertEquals("ai_", AiTexts.AI_ID_PREFIX)
    }
}
