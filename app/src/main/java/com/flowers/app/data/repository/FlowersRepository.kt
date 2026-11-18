package com.flowers.app.data.repository

import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import kotlinx.coroutines.flow.Flow

interface FlowersRepository {
    fun getAllActivePlants(): Flow<List<PlantEntity>>
    fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>>
    suspend fun getPlantById(plantId: Long): PlantEntity?
    fun getPlantFlowById(plantId: Long): Flow<PlantEntity?>
    suspend fun insertPlant(plant: PlantEntity): Long
    suspend fun updatePlant(plant: PlantEntity)
    suspend fun deletePlant(plant: PlantEntity)

    fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>>
    suspend fun getLastWateringEvent(plantId: Long): CareEventEntity?
    suspend fun logCareEvent(event: CareEventEntity): Long

    fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>>
    fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>>
    suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity?

    fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>>
    fun getGeneralPods(): Flow<List<CarePodEntity>>

    suspend fun getSymptomTree(): SymptomTreeRoot

    suspend fun performWatering(plant: PlantEntity, notes: String? = null)
    suspend fun snoozePlant(plant: PlantEntity, days: Int)
    suspend fun skipPlant(plant: PlantEntity)
}
