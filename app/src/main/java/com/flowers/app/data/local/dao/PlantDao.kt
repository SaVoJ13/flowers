package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.domain.ReminderCalculator
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlantDao {
    @Query("SELECT * FROM plants WHERE isArchived = 0 ORDER BY nextCheckDate ASC")
    abstract fun getAllActivePlants(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE isArchived = 0 AND nextCheckDate <= :endOfDayTimestamp ORDER BY nextCheckDate ASC")
    abstract fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE id = :plantId LIMIT 1")
    abstract suspend fun getPlantById(plantId: Long): PlantEntity?

    @Query("SELECT * FROM plants WHERE id = :plantId LIMIT 1")
    abstract fun getPlantFlowById(plantId: Long): Flow<PlantEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlant(plant: PlantEntity): Long

    @Update
    abstract suspend fun updatePlant(plant: PlantEntity)

    @Delete
    abstract suspend fun deletePlant(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :plantId")
    abstract suspend fun deletePlantById(plantId: Long)

    @Insert
    abstract suspend fun insertCareEvent(event: CareEventEntity): Long

    @Query("UPDATE plants SET lastWateredDate = :lastWatered, nextCheckDate = :nextCheck WHERE id = :plantId")
    abstract suspend fun updateWateringDates(plantId: Long, lastWatered: Long, nextCheck: Long): Int

    @Query("UPDATE plants SET nextCheckDate = :nextCheck WHERE id = :plantId")
    abstract suspend fun updateNextCheckDate(plantId: Long, nextCheck: Long): Int

    /**
     * Atomic WATER: re-reads fresh plant state, updates only watering dates and logs the event
     * in one Room transaction. Returns false (nothing written) if the plant no longer exists.
     */
    @Transaction
    open suspend fun applyWatering(plantId: Long, now: Long, notes: String): Boolean {
        val fresh = getPlantById(plantId) ?: return false
        val next = ReminderCalculator.calculateNextCheckAfterWatering(fresh.checkIntervalDays, now)
        if (updateWateringDates(plantId, now, next) == 0) return false
        insertCareEvent(CareEventEntity(plantId = plantId, eventType = "WATER", timestamp = now, notes = notes))
        return true
    }

    /** Atomic DELAY: postpones the check by [days] calendar days; lastWateredDate untouched. */
    @Transaction
    open suspend fun applySnooze(plantId: Long, days: Int, now: Long, notes: String): Boolean {
        getPlantById(plantId) ?: return false
        val next = ReminderCalculator.calculateSnoozeDate(days, now)
        if (updateNextCheckDate(plantId, next) == 0) return false
        insertCareEvent(
            CareEventEntity(plantId = plantId, eventType = "DELAY", timestamp = now, daysDelayed = days, notes = notes)
        )
        return true
    }

    /**
     * Atomic SKIP ("Пропустити цей цикл"): finishes the current check without watering and moves
     * nextCheckDate by the plant's own interval; lastWateredDate untouched.
     */
    @Transaction
    open suspend fun applySkip(plantId: Long, now: Long, notes: String): Boolean {
        val fresh = getPlantById(plantId) ?: return false
        val next = ReminderCalculator.calculateNextCheckAfterWatering(fresh.checkIntervalDays, now)
        if (updateNextCheckDate(plantId, next) == 0) return false
        insertCareEvent(CareEventEntity(plantId = plantId, eventType = "SKIP", timestamp = now, notes = notes))
        return true
    }
}