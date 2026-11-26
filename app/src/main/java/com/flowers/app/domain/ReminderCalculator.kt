package com.flowers.app.domain

import java.util.Calendar
import java.util.TimeZone

object ReminderCalculator {

    const val MILLIS_PER_DAY = 86400000L

    /**
     * Calculates the next check date when user performs a completed watering.
     * Uses calendar-based addition to avoid DST drift and handle month/year boundaries accurately.
     */
    fun calculateNextCheckAfterWatering(
        intervalDays: Int,
        fromTimestamp: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = fromTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, intervalDays)
        }.timeInMillis
    }

    /**
     * Calculates the initial check date for a newly added plant.
     * Marked due for soil check immediately (start of today) so the user
     * can inspect soil moisture before watering.
     */
    fun calculateInitialCheckDate(
        fromTimestamp: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return normalizeToStartOfDay(fromTimestamp, timeZone)
    }

    /**
     * Calculates the postponed check date when user clicks "Відкласти на 2 дні" (or custom days).
     * Uses calendar-based addition to guarantee exactly N calendar days forward.
     */
    fun calculateSnoozeDate(
        snoozeDays: Int,
        fromTimestamp: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = fromTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, snoozeDays)
        }.timeInMillis
    }

    /**
     * Normalizes a timestamp to the beginning of the day (00:00:00.000) in the specified time zone.
     */
    fun normalizeToStartOfDay(
        timestamp: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone).apply {
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
    fun isDue(
        nextCheckTimestamp: Long,
        currentTimestamp: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val todayEndOfDay = Calendar.getInstance(timeZone).apply {
            timeInMillis = currentTimestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return nextCheckTimestamp <= todayEndOfDay
    }
}
