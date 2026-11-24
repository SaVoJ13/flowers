package com.flowers.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderCalculatorTest {

    @Test
    fun calculateNextCheckAfterWatering_addsExpectedDays() {
        val baseTime = 1700000000000L // arbitrary timestamp
        val interval = 7
        val result = ReminderCalculator.calculateNextCheckAfterWatering(interval, baseTime)

        val calExpected = Calendar.getInstance().apply {
            timeInMillis = baseTime + 7 * 86400000L
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(calExpected.timeInMillis, result)
    }

    @Test
    fun calculateInitialCheckDate_normalizesToStartOfToday() {
        val baseTime = 1700000000000L
        val result = ReminderCalculator.calculateInitialCheckDate(baseTime)

        val calExpected = Calendar.getInstance().apply {
            timeInMillis = baseTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(calExpected.timeInMillis, result)
        assertTrue(ReminderCalculator.isDue(result, baseTime))
    }

    @Test
    fun calculateSnoozeDate_addsAccuratePostponeDays() {
        val baseTime = 1700000000000L
        val snoozeDays = 2
        val result = ReminderCalculator.calculateSnoozeDate(snoozeDays, baseTime)

        val calExpected = Calendar.getInstance().apply {
            timeInMillis = baseTime + 2 * 86400000L
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(calExpected.timeInMillis, result)
    }

    @Test
    fun isDue_returnsTrueForTodayOrPast() {
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000L
        val tomorrow = now + 86400000L * 2

        assertTrue(ReminderCalculator.isDue(yesterday, now))
        assertTrue(ReminderCalculator.isDue(now, now))
        assertFalse(ReminderCalculator.isDue(tomorrow, now))
    }
}
