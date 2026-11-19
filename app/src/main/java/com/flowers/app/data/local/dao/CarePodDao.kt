package com.flowers.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowers.app.data.local.entity.CarePodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CarePodDao {
    @Query("SELECT * FROM care_pods WHERE plantSpeciesId = :speciesId OR plantSpeciesId = 'general_beginner'")
    fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>>

    @Query("SELECT * FROM care_pods WHERE plantSpeciesId = 'general_beginner'")
    fun getGeneralPods(): Flow<List<CarePodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pods: List<CarePodEntity>)

    @Query("SELECT COUNT(*) FROM care_pods")
    suspend fun count(): Int
}
