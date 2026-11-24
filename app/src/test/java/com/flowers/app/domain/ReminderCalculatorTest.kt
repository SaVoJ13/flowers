package com.flowers.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderCalculatorTest {

    @Test
    fun calculateNextCheckAfterWatering_addsExpectedDays() {
        val baseTime = 1700000000000L // arbitrary timestamp
        val interval = 7
        val result = ReminderCalculator.calculateNextCheckAfterWatering(interval, baseTime)

        val calExpected = Calendar.getInstance().apply {
            timeInMillis = baseTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 7)
        }

        assertEquals(calExpected.timeInMillis, result)
    }

    @Test
    fun calculateNextCheckAfterWatering_handlesMonthBoundaries() {
        val tz = TimeZone.getTimeZone("UTC")
        // 2026-01-28 14:30:00 UTC
        val jan28 = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JANUARY, 28, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(intervalDays = 7, fromTimestamp = jan28, timeZone = tz)

        val calResult = Calendar.getInstance(tz).apply { timeInMillis = nextCheck }
        assertEquals(2026, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, calResult.get(Calendar.MONTH))
        assertEquals(4, calResult.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calResult.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calResult.get(Calendar.MINUTE))
        assertEquals(0, calResult.get(Calendar.SECOND))
    }

    @Test
    fun calculateNextCheckAfterWatering_handlesLeapYearFebruary() {
        val tz = TimeZone.getTimeZone("UTC")
        // 2024 is a leap year (Feb 29 exists)
        val feb28_2024 = Calendar.getInstance(tz).apply {
            set(2024, Calendar.FEBRUARY, 28, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(intervalDays = 3, fromTimestamp = feb28_2024, timeZone = tz)
        val calResult = Calendar.getInstance(tz).apply { timeInMillis = nextCheck }
        // 28 + 1 = 29 Feb, + 2 = 1 Mar, + 3 = 2 Mar
        assertEquals(2024, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, calResult.get(Calendar.MONTH))
        assertEquals(2, calResult.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun calculateNextCheckAfterWatering_handlesNonLeapYearFebruary() {
        val tz = TimeZone.getTimeZone("UTC")
        // 2025 is not a leap year (no Feb 29)
        val feb28_2025 = Calendar.getInstance(tz).apply {
            set(2025, Calendar.FEBRUARY, 28, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(intervalDays = 3, fromTimestamp = feb28_2025, timeZone = tz)
        val calResult = Calendar.getInstance(tz).apply { timeInMillis = nextCheck }
        // 28 + 1 = 1 Mar, + 2 = 2 Mar, + 3 = 3 Mar
        assertEquals(2025, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, calResult.get(Calendar.MONTH))
        assertEquals(3, calResult.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun calculateNextCheckAfterWatering_handlesYearBoundary() {
        val tz = TimeZone.getTimeZone("UTC")
        val dec30 = Calendar.getInstance(tz).apply {
            set(2026, Calendar.DECEMBER, 30, 20, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(intervalDays = 5, fromTimestamp = dec30, timeZone = tz)
        val calResult = Calendar.getInstance(tz).apply { timeInMillis = nextCheck }

        assertEquals(2027, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, calResult.get(Calendar.MONTH))
        assertEquals(4, calResult.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calResult.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun calculateNextCheckAfterWatering_handlesDstSpringForward() {
        // Europe/Kyiv: Spring forward transition is last Sunday in March (e.g. 2026-03-29, 23-hour day)
        val tz = TimeZone.getTimeZone("Europe/Kyiv")
        val march28 = Calendar.getInstance(tz).apply {
            set(2026, Calendar.MARCH, 28, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(intervalDays = 3, fromTimestamp = march28, timeZone = tz)
        val calResult = Calendar.getInstance(tz).apply { timeInMillis = nextCheck }

        assertEquals(2026, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, calResult.get(Calendar.MONTH))
        assertEquals(31, calResult.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calResult.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calResult.get(Calendar.MINUTE))
    }

    @Test
    fun calculateNextCheckAfterWatering_handlesDstFallBack() {
        // Europe/Kyiv: Fall back transition is last Sunday in October (e.g. 2026-10-25, 25-hour day)
        val tz = TimeZone.getTimeZone("Europe/Kyiv")
        val oct24 = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 24, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(intervalDays = 3, fromTimestamp = oct24, timeZone = tz)
        val calResult = Calendar.getInstance(tz).apply { timeInMillis = nextCheck }

        assertEquals(2026, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calResult.get(Calendar.MONTH))
        assertEquals(27, calResult.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calResult.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calResult.get(Calendar.MINUTE))
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
    fun calculateSnoozeDate_addsAccuratelyTwoCalendarDays() {
        val tz = TimeZone.getTimeZone("UTC")
        val baseTime = Calendar.getInstance(tz).apply {
            set(2026, Calendar.MAY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val snoozeDays = 2
        val result = ReminderCalculator.calculateSnoozeDate(snoozeDays, baseTime, tz)

        val calResult = Calendar.getInstance(tz).apply { timeInMillis = result }
        assertEquals(2026, calResult.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, calResult.get(Calendar.MONTH))
        assertEquals(17, calResult.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calResult.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calResult.get(Calendar.MINUTE))
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
