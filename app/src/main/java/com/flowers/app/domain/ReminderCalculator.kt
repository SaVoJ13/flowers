package com.flowers.app.domain

import java.util.Calendar

object ReminderCalculator {

    const val MILLIS_PER_DAY = 86400000L

    /**
     * Calculates the next check date when user performs a completed watering.
     */
    fun calculateNextCheckAfterWatering(
        intervalDays: Int,
        fromTimestamp: Long = System.currentTimeMillis()
    ): Long {
        return normalizeToStartOfDay(fromTimestamp + intervalDays * MILLIS_PER_DAY)
    }

    /**
     * Calculates the initial check date for a newly added plant.
     * Marked due for soil check immediately (start of today) so the user
     * can inspect soil moisture before watering.
     */
    fun calculateInitialCheckDate(
        fromTimestamp: Long = System.currentTimeMillis()
    ): Long {
        return normalizeToStartOfDay(fromTimestamp)
    }

    /**
     * Calculates the postponed check date when user clicks "Snooze / Відкласти" (1 to 3 days).
     */
    fun calculateSnoozeDate(
        snoozeDays: Int,
        fromTimestamp: Long = System.currentTimeMillis()
    ): Long {
        return normalizeToStartOfDay(fromTimestamp + snoozeDays * MILLIS_PER_DAY)
    }

    /**
     * Normalizes a timestamp to the beginning of the day (00:00:00.000).
     */
    fun normalizeToStartOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Checks if a target timestamp is due today or overdue.
     */
    fun isDue(nextCheckTimestamp: Long, currentTimestamp: Long = System.currentTimeMillis()): Boolean {
        val todayEndOfDay = Calendar.getInstance().apply {
            timeInMillis = currentTimestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return nextCheckTimestamp <= todayEndOfDay
    }
}
