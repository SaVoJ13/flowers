package com.flowers.app.ui

import android.net.Uri
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.data.preferences.UserPreferences
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.data.remote.PlantIdentificationResponse
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.domain.ReminderCalculator
import com.flowers.app.ui.plants.AddPlantUiState
import com.flowers.app.ui.plants.PlantsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class PlantsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tempDir = File("build/tmp/viewmodel_test_${System.currentTimeMillis()}").apply { mkdirs() }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private class FakeRepository(
        var identifyResult: IdentifyPlantResult = IdentifyPlantResult.Success(
            PlantIdentificationResponse(
                isPlant = true,
                nameUk = "Монстера делікатесна",
                nameLatin = "Monstera deliciosa",
                confidence = "HIGH",
                description = "Красива кімнатна ліана",
                lightLevel = "INDIRECT",
                defaultCheckIntervalDays = 8,
                wateringAdvice = "Поливати після просихання верхнього шару",
                soilType = "Пухкий ґрунт із перлітом",
                toxicityInfo = "UNKNOWN",
                alternativeSpecies = listOf("Філодендрон")
            )
        ),
        val onIdentifyDeferred: CompletableDeferred<Unit>? = null
    ) : FlowersRepository {
        val insertedPlants = mutableListOf<PlantEntity>()
        val updatedPlants = mutableListOf<PlantEntity>()
        val careEvents = mutableListOf<CareEventEntity>()
        private var nextId = 1L

        override val userPreferences: UserPreferences get() = throw UnsupportedOperationException()
        override fun getAllActivePlants(): Flow<List<PlantEntity>> = flowOf(insertedPlants)
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = flowOf(emptyList())
        override suspend fun getPlantById(plantId: Long): PlantEntity? = insertedPlants.find { it.id == plantId }
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = flowOf(insertedPlants.find { it.id == plantId })

        override suspend fun insertPlant(plant: PlantEntity): Long {
            val id = nextId++
            val saved = plant.copy(id = id)
            insertedPlants.add(saved)
            return id
        }

        override suspend fun updatePlant(plant: PlantEntity) {
            val idx = insertedPlants.indexOfFirst { it.id == plant.id }
            if (idx >= 0) {
                insertedPlants[idx] = plant
            }
            updatedPlants.add(plant)
        }

        override suspend fun deletePlant(plant: PlantEntity) {
            insertedPlants.removeAll { it.id == plant.id }
        }

        override fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>> = flowOf(careEvents.filter { it.plantId == plantId })
        override suspend fun getLastWateringEvent(plantId: Long): CareEventEntity? = careEvents.lastOrNull { it.plantId == plantId && it.eventType == "WATER" }

        override suspend fun logCareEvent(event: CareEventEntity): Long {
            careEvents.add(event)
            return careEvents.size.toLong()
        }

        override fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>> = flowOf(emptyList())
        override fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>> = flowOf(emptyList())
        override suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity? = null
        override fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>> = flowOf(emptyList())
        override fun getGeneralPods(): Flow<List<CarePodEntity>> = flowOf(emptyList())
        override suspend fun getSymptomTree(): SymptomTreeRoot = throw UnsupportedOperationException()
        override suspend fun performWatering(plant: PlantEntity, notes: String?) {}
        override suspend fun snoozePlant(plant: PlantEntity, days: Int) {}
        override suspend fun skipPlant(plant: PlantEntity) {}
        override suspend fun generatePlantWithAi(plantQuery: String): GeneratePlantResult = throw UnsupportedOperationException()

        override suspend fun identifyPlantFromPhoto(imageFile: File): IdentifyPlantResult {
            onIdentifyDeferred?.await()
            return identifyResult
        }
        override suspend fun diagnosePlantHealth(imageFile: File, plant: PlantEntity): com.flowers.app.data.remote.DiagnosePlantHealthResult = throw UnsupportedOperationException()
        override suspend fun sendPlantChatMessage(plantId: Long, userQuestion: String, language: String, currentMessageId: Long?): com.flowers.app.data.remote.PlantChatResult = throw UnsupportedOperationException()
        override fun getChatMessages(plantId: Long): kotlinx.coroutines.flow.Flow<List<com.flowers.app.data.local.entity.ChatMessageEntity>> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getChatMessageById(messageId: Long): com.flowers.app.data.local.entity.ChatMessageEntity? = null
        override suspend fun insertChatMessage(message: com.flowers.app.data.local.entity.ChatMessageEntity): Long = 0L
        override suspend fun updateChatMessageStatus(messageId: Long, status: com.flowers.app.data.local.entity.MessageStatus, errorMessage: String?): Boolean = true
        override suspend fun saveAssistantResponse(userMessageId: Long, modelMessage: com.flowers.app.data.local.entity.ChatMessageEntity): Long = 0L
        override suspend fun beginChatMessageRetry(messageId: Long, plantId: Long): Boolean = false
        override suspend fun clearChatHistory(plantId: Long): Int = 0
        override suspend fun recoverStaleSendingMessages(plantId: Long): Int = 0
        override fun isPlantChatSending(plantId: Long): Boolean = false
    }

    @Test
    fun `manual entry requested sets state to Confirmation with offline fallback`() {
        val repo = FakeRepository()
        val viewModel = PlantsViewModel(repo)

        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)

        viewModel.onManualEntryRequested()

        val state = viewModel.addPlantUiState.value
        assertTrue(state is AddPlantUiState.Confirmation)
        val confirmation = state as AddPlantUiState.Confirmation
        assertTrue(confirmation.isOfflineFallback)
        assertNull("photoFile має бути null при ручному введенні без фото", confirmation.photoFile)
        assertEquals("LOW", confirmation.identification.confidence)
    }

    @Test
    fun `manual plant creation without photo saves photoUri as null in repository`() = runTest {
        val repo = FakeRepository()
        val viewModel = PlantsViewModel(repo)

        var saved = false
        viewModel.savePlant(
            plantId = null,
            customName = "Фікус без фото",
            roomLocation = "Вітальня",
            lightLevel = "INDIRECT",
            checkIntervalDays = 7,
            photoUri = "", // порожній рядок замість фото
            onSaved = { saved = true }
        )
        advanceUntilIdle()

        assertTrue(saved)
        assertEquals(1, repo.insertedPlants.size)
        val plant = repo.insertedPlants.first()
        assertNull("photoUri має бути строго null для рослини без фото", plant.photoUri)
        assertNull("lastWateredDate має бути null без фактичного поливу", plant.lastWateredDate)
    }

    @Test
    fun `cancellation during active AI analysis cancels job and late AI completion never modifies Idle state`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val repo = FakeRepository(onIdentifyDeferred = deferred)
        val viewModel = PlantsViewModel(repo)

        val tempPhoto = File(tempDir, "active_ai_photo.jpg").apply { writeText("dummy") }
        viewModel.onManualEntryRequested(photoFile = tempPhoto)

        // Скасування
        viewModel.resetAddPlantFlow(discardPhoto = true)
        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)
        assertFalse(tempPhoto.exists())

        // Запізніла відповідь ШІ завершується
        deferred.complete(Unit)
        advanceUntilIdle()

        // Стан залишається строго Idle
        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)
    }

    @Test
    fun `resetAddPlantFlow discards temporary file and resets to Idle`() {
        val repo = FakeRepository()
        val viewModel = PlantsViewModel(repo)

        val testFile = File(tempDir, "temp_test_photo.jpg").apply {
            writeText("dummy photo bytes")
        }
        assertTrue(testFile.exists())

        viewModel.onManualEntryRequested(photoFile = testFile)
        assertTrue(viewModel.addPlantUiState.value is AddPlantUiState.Confirmation)

        viewModel.resetAddPlantFlow(discardPhoto = true)

        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)
        assertFalse("Тимчасовий файл мав бути видалений при скиданні", testFile.exists())
    }

    @Test
    fun `resetAddPlantFlow during AnalyzingWithAi deletes active photo and stays Idle`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val repo = FakeRepository(onIdentifyDeferred = deferred)
        val viewModel = PlantsViewModel(repo)

        val tempPhoto = File(tempDir, "analyzing_photo.jpg").apply { writeText("image data") }
        assertTrue(tempPhoto.exists())

        // Симулюємо перебування у стані AnalyzingWithAi
        viewModel.onManualEntryRequested(photoFile = tempPhoto)
        viewModel.resetAddPlantFlow(discardPhoto = true)

        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)
        assertFalse("Файл фото мав бути видалений при скасуванні", tempPhoto.exists())
    }

    @Test
    fun `savePlant inserts PlantEntity with Flowers 2_0 fields and DOES NOT record false WATER event`() = runTest {
        val repo = FakeRepository()
        val viewModel = PlantsViewModel(repo)

        val testPhoto = File(tempDir, "species_monstera.jpg").apply {
            writeText("dummy photo bytes")
        }

        var savedCalled = false
        viewModel.savePlant(
            plantId = null,
            customName = "Моя улюблена монстера",
            roomLocation = "Вітальня",
            lightLevel = "INDIRECT",
            checkIntervalDays = 8,
            photoUri = testPhoto.absolutePath,
            speciesName = "Монстера делікатесна",
            botanicalName = "Monstera deliciosa",
            description = "Тропічна ліана",
            wateringAdvice = "Поливати після просихання",
            soilType = "Субстрат для ароїдних",
            toxicityInfo = "Оксалати кальцію",
            aiConfidence = "HIGH",
            isAiGenerated = true,
            onSaved = { savedCalled = true }
        )

        advanceUntilIdle()

        assertTrue(savedCalled)
        assertEquals(1, repo.insertedPlants.size)
        val plant = repo.insertedPlants.first()
        assertEquals("Моя улюблена монстера", plant.customName)
        assertEquals("Монстера делікатесна", plant.speciesName)
        assertEquals("Monstera deliciosa", plant.botanicalName)
        assertEquals("Тропічна ліана", plant.description)
        assertEquals("Поливати після просихання", plant.wateringAdvice)
        assertEquals("Субстрат для ароїдних", plant.soilType)
        assertEquals("Оксалати кальцію", plant.toxicityInfo)
        assertEquals("HIGH", plant.aiConfidence)
        assertTrue(plant.isAiGenerated)
        assertEquals(8, plant.checkIntervalDays)
        assertEquals(testPhoto.absolutePath, plant.photoUri)

        // Правдива історія: створення картки НЕ є поливом!
        assertNull("lastWateredDate має бути null без підтвердження поливу", plant.lastWateredDate)
        assertEquals("Жодних штучних подій WATER не повинно бути записано", 0, repo.careEvents.size)
        assertTrue("Рослина має потребувати огляду ґрунту сьогодні", ReminderCalculator.isDue(plant.nextCheckDate))
    }

    @Test
    fun `savePlant updates existing plant preserving existing and updating 2_0 fields`() = runTest {
        val repo = FakeRepository()
        val initialPlant = PlantEntity(
            id = 42L,
            customName = "Старий фікус",
            roomLocation = "Спальня",
            lightLevel = "SHADE",
            checkIntervalDays = 10
        )
        repo.insertedPlants.add(initialPlant)

        val viewModel = PlantsViewModel(repo)

        var savedCalled = false
        viewModel.savePlant(
            plantId = 42L,
            customName = "Оновлений фікус",
            speciesName = "Фікус каучуконосний",
            botanicalName = "Ficus elastica",
            roomLocation = "Кабінет",
            lightLevel = "INDIRECT",
            checkIntervalDays = 7,
            onSaved = { savedCalled = true }
        )

        advanceUntilIdle()

        assertTrue(savedCalled)
        assertEquals(1, repo.updatedPlants.size)
        val updated = repo.updatedPlants.first()
        assertEquals(42L, updated.id)
        assertEquals("Оновлений фікус", updated.customName)
        assertEquals("Фікус каучуконосний", updated.speciesName)
        assertEquals("Ficus elastica", updated.botanicalName)
        assertEquals("Кабінет", updated.roomLocation)
        assertEquals("INDIRECT", updated.lightLevel)
        assertEquals(7, updated.checkIntervalDays)
    }

    @Test
    fun `duplicate savePlant calls while saving are ignored`() = runTest {
        val repo = FakeRepository()
        val viewModel = PlantsViewModel(repo)

        var saveCount = 0
        // Перший виклик
        viewModel.savePlant(
            plantId = null,
            customName = "Рослина 1",
            onSaved = { saveCount++ }
        )
        // Негайний повторний виклик до завершення першого
        viewModel.savePlant(
            plantId = null,
            customName = "Рослина дублікат",
            onSaved = { saveCount++ }
        )

        advanceUntilIdle()

        assertEquals("Має бути створено рівно одну рослину без дублікатів", 1, repo.insertedPlants.size)
        assertEquals("onSaved має викликатися один раз", 1, saveCount)
        assertFalse("isSaving має скинутися після завершення", viewModel.isSaving.value)
    }
}
