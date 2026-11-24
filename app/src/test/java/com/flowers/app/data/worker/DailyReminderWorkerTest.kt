package com.flowers.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyReminderWorkerTest {

    @Test
    fun formatReminderMessage_singlePlant_containsPlantName() {
        val result = DailyReminderWorker.formatReminderMessage(listOf("Фікус Бенджаміна"))
        assertEquals("Час перевірити ґрунт у Фікус Бенджаміна", result)
    }

    @Test
    fun formatReminderMessage_twoPlants_containsBothNames() {
        val result = DailyReminderWorker.formatReminderMessage(listOf("Фікус Бенджаміна", "Монстера"))
        assertEquals("Час перевірити ґрунт у Фікус Бенджаміна та Монстера", result)
    }

    @Test
    fun formatReminderMessage_threePlants_formatsWithOneRemaining() {
        val result = DailyReminderWorker.formatReminderMessage(listOf("Фікус", "Монстера", "Заміокулькас"))
        assertEquals("Час перевірити ґрунт у Фікус, Монстера та ще 1 рослини", result)
    }

    @Test
    fun formatReminderMessage_fivePlants_formatsWithPluralRemaining() {
        val result = DailyReminderWorker.formatReminderMessage(listOf("Фікус", "Монстера", "Заміокулькас", "Алое", "Пеперомія"))
        assertEquals("Час перевірити ґрунт у Фікус, Монстера та ще 3 рослин", result)
    }

    @Test
    fun formatReminderMessage_emptyList_returnsEmpty() {
        val result = DailyReminderWorker.formatReminderMessage(emptyList())
        assertEquals("", result)
    }
}
