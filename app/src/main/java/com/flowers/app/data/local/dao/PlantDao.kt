package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flowers.app.data.local.entity.PlantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants WHERE isArchived = 0 ORDER BY nextCheckDate ASC")
    fun getAllActivePlants(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE isArchived = 0 AND nextCheckDate <= :endOfDayTimestamp ORDER BY nextCheckDate ASC")
    fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants WHERE id = :plantId LIMIT 1")
    suspend fun getPlantById(plantId: Long): PlantEntity?

    @Query("SELECT * FROM plants WHERE id = :plantId LIMIT 1")
    fun getPlantFlowById(plantId: Long): Flow<PlantEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlant(plant: PlantEntity): Long

    @Update
    suspend fun updatePlant(plant: PlantEntity)

    @Delete
    suspend fun deletePlant(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :plantId")
    suspend fun deletePlantById(plantId: Long)
}
