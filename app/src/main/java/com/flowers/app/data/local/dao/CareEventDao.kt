package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowers.app.data.local.entity.CareEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CareEventDao {
    @Query("SELECT * FROM care_events WHERE plantId = :plantId ORDER BY timestamp DESC")
    fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>>

    @Query("SELECT * FROM care_events WHERE plantId = :plantId AND eventType = 'WATER' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastWateringEvent(plantId: Long): CareEventEntity?

    @Query("SELECT * FROM care_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<CareEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CareEventEntity): Long
}
