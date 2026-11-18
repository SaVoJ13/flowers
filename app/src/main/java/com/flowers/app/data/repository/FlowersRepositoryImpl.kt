package com.flowers.app.data.repository

import android.content.Context
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.domain.ReminderCalculator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FlowersRepositoryImpl(
    private val plantDao: PlantDao,
    private val careEventDao: CareEventDao,
    private val catalogDao: CatalogDao,
    private val carePodDao: CarePodDao,
    private val context: Context
) : FlowersRepository {

    private var cachedSymptomTree: SymptomTreeRoot? = null

    override fun getAllActivePlants(): Flow<List<PlantEntity>> = plantDao.getAllActivePlants()

    override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> =
        plantDao.getPlantsDueForCheck(endOfDayTimestamp)

    override suspend fun getPlantById(plantId: Long): PlantEntity? = plantDao.getPlantById(plantId)

    override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = plantDao.getPlantFlowById(plantId)

    override suspend fun insertPlant(plant: PlantEntity): Long = plantDao.insertPlant(plant)

    override suspend fun updatePlant(plant: PlantEntity) = plantDao.updatePlant(plant)

    override suspend fun deletePlant(plant: PlantEntity) = plantDao.deletePlant(plant)

    override fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>> =
        careEventDao.getEventsForPlant(plantId)

    override suspend fun getLastWateringEvent(plantId: Long): CareEventEntity? =
        careEventDao.getLastWateringEvent(plantId)

    override suspend fun logCareEvent(event: CareEventEntity): Long =
        careEventDao.insertEvent(event)

    override fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>> =
        catalogDao.getAllCatalogPlants()

    override fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>> =
        catalogDao.getAllCatalogPlants().map { plants ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) plants
            else plants.filter { plant ->
                plant.nameUk.contains(trimmed, ignoreCase = true) ||
                plant.nameLatin.contains(trimmed, ignoreCase = true) ||
                plant.popularName.contains(trimmed, ignoreCase = true)
            }
        }

    override suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity? =
        catalogDao.getCatalogPlantById(speciesId)

    override fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>> =
        carePodDao.getPodsForSpecies(speciesId)

    override fun getGeneralPods(): Flow<List<CarePodEntity>> =
        carePodDao.getGeneralPods()

    override suspend fun getSymptomTree(): SymptomTreeRoot = withContext(Dispatchers.IO) {
        cachedSymptomTree ?: run {
            val json = context.assets.open("content/SYMPTOM_DECISION_TREE.json")
                .bufferedReader().use { it.readText() }
            val tree = Gson().fromJson(json, SymptomTreeRoot::class.java)
            cachedSymptomTree = tree
            tree
        }
    }

    override suspend fun performWatering(plant: PlantEntity, notes: String?) {
        val now = System.currentTimeMillis()
        val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(plant.checkIntervalDays, now)
        val updatedPlant = plant.copy(
            lastWateredDate = now,
            nextCheckDate = nextCheck
        )
        plantDao.updatePlant(updatedPlant)
        careEventDao.insertEvent(
            CareEventEntity(
                plantId = plant.id,
                eventType = "WATER",
                timestamp = now,
                notes = notes
            )
        )
    }

    override suspend fun snoozePlant(plant: PlantEntity, days: Int) {
        val now = System.currentTimeMillis()
        val nextCheck = ReminderCalculator.calculateSnoozeDate(days, now)
        val updatedPlant = plant.copy(nextCheckDate = nextCheck)
        plantDao.updatePlant(updatedPlant)
        careEventDao.insertEvent(
            CareEventEntity(
                plantId = plant.id,
                eventType = "DELAY",
                timestamp = now,
                daysDelayed = days,
                notes = "Відкладено на $days дн. (ґрунт ще вологий)"
            )
        )
    }

    override suspend fun skipPlant(plant: PlantEntity) {
        val now = System.currentTimeMillis()
        careEventDao.insertEvent(
            CareEventEntity(
                plantId = plant.id,
                eventType = "SKIP",
                timestamp = now,
                notes = "Пропущено в цьому циклі"
            )
        )
    }
}
