package com.flowers.app.ui

import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.data.preferences.UserPreferences
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.domain.ReminderCalculator
import com.flowers.app.ui.today.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeFlowersRepository : FlowersRepository {
        val plantsFlow = MutableStateFlow<List<PlantEntity>>(emptyList())
        val generalPodsFlow = MutableStateFlow<List<CarePodEntity>>(emptyList())
        val careEvents = mutableListOf<CareEventEntity>()
        var performWateringCallCount = 0
        var snoozePlantCallCount = 0

        override val userPreferences: UserPreferences get() = throw UnsupportedOperationException()
        override fun getAllActivePlants(): Flow<List<PlantEntity>> = plantsFlow
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = flowOf(emptyList())
        override suspend fun getPlantById(plantId: Long): PlantEntity? = plantsFlow.value.find { it.id == plantId }
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = flowOf(plantsFlow.value.find { it.id == plantId })
        override suspend fun insertPlant(plant: PlantEntity): Long = 1L
        override suspend fun updatePlant(plant: PlantEntity) {
            val list = plantsFlow.value.toMutableList()
            val index = list.indexOfFirst { it.id == plant.id }
            if (index != -1) {
                list[index] = plant
                plantsFlow.value = list
            }
        }
        override suspend fun deletePlant(plant: PlantEntity) {}
        override fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>> = flowOf(careEvents.filter { it.plantId == plantId })
        override suspend fun getLastWateringEvent(plantId: Long): CareEventEntity? = careEvents.findLast { it.plantId == plantId && it.eventType == "WATER" }
        override suspend fun logCareEvent(event: CareEventEntity): Long {
            careEvents.add(event)
            return event.id
        }
        override fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>> = flowOf(emptyList())
        override fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>> = flowOf(emptyList())
        override suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity? = null
        override fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>> = flowOf(emptyList())
        override fun getGeneralPods(): Flow<List<CarePodEntity>> = generalPodsFlow
        override suspend fun getSymptomTree(): SymptomTreeRoot = throw UnsupportedOperationException()

        override suspend fun performWatering(plant: PlantEntity, notes: String?) {
            performWateringCallCount++
            val now = System.currentTimeMillis()
            val nextCheck = ReminderCalculator.calculateNextCheckAfterWatering(plant.checkIntervalDays, now)
            val updated = plant.copy(lastWateredDate = now, nextCheckDate = nextCheck)
            updatePlant(updated)
            careEvents.add(
                CareEventEntity(
                    plantId = plant.id,
                    eventType = "WATER",
                    timestamp = now,
                    notes = notes ?: "Ґрунт перевірено, полив виконано"
                )
            )
        }

        override suspend fun snoozePlant(plant: PlantEntity, days: Int) {
            snoozePlantCallCount++
            val now = System.currentTimeMillis()
            val nextCheck = ReminderCalculator.calculateSnoozeDate(days, now)
            val updated = plant.copy(nextCheckDate = nextCheck)
            updatePlant(updated)
            careEvents.add(
                CareEventEntity(
                    plantId = plant.id,
                    eventType = "DELAY",
                    daysDelayed = days,
                    timestamp = now,
                    notes = "Відкладено на $days дн. (ґрунт ще вологий)"
                )
            )
        }

        override suspend fun skipPlant(plant: PlantEntity) {
            careEvents.add(CareEventEntity(plantId = plant.id, eventType = "SKIP"))
        }

        override suspend fun generatePlantWithAi(plantQuery: String): GeneratePlantResult = throw UnsupportedOperationException()
        override suspend fun identifyPlantFromPhoto(imageFile: File): IdentifyPlantResult = throw UnsupportedOperationException()
    }

    @Test
    fun uiState_categorizesDueAndUpcomingPlants() = runTest(testDispatcher) {
        val repo = FakeFlowersRepository()
        val now = System.currentTimeMillis()
        val duePlant = PlantEntity(
            id = 1L,
            customName = "Монстера",
            nextCheckDate = now - 1000L
        )
        val upcomingPlant = PlantEntity(
            id = 2L,
            customName = "Фікус",
            nextCheckDate = now + 86400000L * 5
        )
        repo.plantsFlow.value = listOf(duePlant, upcomingPlant)

        val viewModel = TodayViewModel(repo)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.duePlants.size)
        assertEquals(1L, state.duePlants.first().id)
        assertEquals(1, state.upcomingPlants.size)
        assertEquals(2L, state.upcomingPlants.first().id)

        collectJob.cancel()
    }

    @Test
    fun onWaterClicked_setsConfirmationState_withoutInvokingRepository() = runTest(testDispatcher) {
        val repo = FakeFlowersRepository()
        val plant = PlantEntity(id = 10L, customName = "Бегонія", nextCheckDate = System.currentTimeMillis())
        repo.plantsFlow.value = listOf(plant)

        val viewModel = TodayViewModel(repo)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onWaterClicked(plant)
        advanceUntilIdle()

        assertEquals(plant, viewModel.uiState.value.plantToConfirmWatering)
        assertEquals(0, repo.performWateringCallCount)

        // Dismiss without confirm
        viewModel.onWaterConfirmationDismissed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.plantToConfirmWatering)
        assertEquals(0, repo.performWateringCallCount)

        collectJob.cancel()
    }

    @Test
    fun onWaterConfirmed_updatesLastWateredDateAndSavesWaterEvent() = runTest(testDispatcher) {
        val repo = FakeFlowersRepository()
        val beforeTime = System.currentTimeMillis()
        val plant = PlantEntity(
            id = 20L,
            customName = "Сансев'єрія",
            checkIntervalDays = 14,
            lastWateredDate = null,
            nextCheckDate = beforeTime
        )
        repo.plantsFlow.value = listOf(plant)

        val viewModel = TodayViewModel(repo)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onWaterClicked(plant)
        advanceUntilIdle()
        viewModel.onWaterConfirmed()
        advanceUntilIdle()

        // Dialog dismissed
        assertNull(viewModel.uiState.value.plantToConfirmWatering)
        assertEquals(1, repo.performWateringCallCount)

        // Care event saved
        assertEquals(1, repo.careEvents.size)
        val event = repo.careEvents.first()
        assertEquals("WATER", event.eventType)
        assertEquals(20L, event.plantId)

        // Plant updated in repository
        val updatedPlant = repo.plantsFlow.value.first { it.id == 20L }
        assertNotNull(updatedPlant.lastWateredDate)
        assertTrue(updatedPlant.lastWateredDate!! >= beforeTime)
        assertTrue(updatedPlant.nextCheckDate > beforeTime)

        collectJob.cancel()
    }

    @Test
    fun onSnoozeTwoDaysClicked_postponesCheckWithoutModifyingLastWateredDate() = runTest(testDispatcher) {
        val repo = FakeFlowersRepository()
        val initialWateredDate: Long? = null
        val initialNextCheck = System.currentTimeMillis()
        val plant = PlantEntity(
            id = 30L,
            customName = "Спатифілум",
            lastWateredDate = initialWateredDate,
            nextCheckDate = initialNextCheck
        )
        repo.plantsFlow.value = listOf(plant)

        val viewModel = TodayViewModel(repo)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.onSnoozeTwoDaysClicked(plant)
        advanceUntilIdle()

        assertEquals(1, repo.snoozePlantCallCount)
        assertEquals(1, repo.careEvents.size)
        val event = repo.careEvents.first()
        assertEquals("DELAY", event.eventType)
        assertEquals(2, event.daysDelayed)
        assertEquals(30L, event.plantId)

        val updatedPlant = repo.plantsFlow.value.first { it.id == 30L }
        // Critical: lastWateredDate MUST NOT be changed to a fictitious watering!
        assertNull(updatedPlant.lastWateredDate)
        // nextCheckDate must be postponed
        assertTrue(updatedPlant.nextCheckDate > initialNextCheck)

        collectJob.cancel()
    }

    @Test
    fun debounceProtection_ignoresRepeatedActionsWhileProcessing() = runTest(testDispatcher) {
        val repo = FakeFlowersRepository()
        val plant = PlantEntity(id = 40L, customName = "Заміокулькас", nextCheckDate = System.currentTimeMillis())
        repo.plantsFlow.value = listOf(plant)

        val viewModel = TodayViewModel(repo)
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Double click snooze
        viewModel.onSnoozeTwoDaysClicked(plant)
        viewModel.onSnoozeTwoDaysClicked(plant)
        advanceUntilIdle()

        assertEquals(1, repo.snoozePlantCallCount)
        assertEquals(1, repo.careEvents.size)

        collectJob.cancel()
    }
}
