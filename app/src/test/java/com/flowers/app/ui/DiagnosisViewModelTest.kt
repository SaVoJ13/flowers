package com.flowers.app.ui

import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomCategory
import com.flowers.app.data.model.SymptomNode
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.data.preferences.UserPreferences
import com.flowers.app.data.remote.DiagnosePlantHealthResult
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.data.remote.PlantHealthDiagnosisResponse
import com.flowers.app.data.repository.FlowersRepository
import com.flowers.app.ui.diagnosis.DiagnosisMode
import com.flowers.app.ui.diagnosis.DiagnosisViewModel
import com.flowers.app.ui.diagnosis.PhotoDiagnosisStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosisViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTestFlowersRepository(
        val plant: PlantEntity
    ) : FlowersRepository {
        val careEvents = mutableListOf<CareEventEntity>()
        var diagnoseHealthResult: DiagnosePlantHealthResult = DiagnosePlantHealthResult.Success(
            PlantHealthDiagnosisResponse(
                isPlant = true,
                probableCause = "Перелив ґрунту",
                confidence = "HIGH",
                visibleSymptoms = listOf("Жовте листя"),
                alternativeCauses = listOf("Брак світла"),
                firstAidSteps = listOf("Ізолювати рослину", "Дати просохнути ґрунту"),
                followUpAdvice = "Оглянути через 5 днів"
            )
        )
        var diagnoseCalledCount = 0
        var onDiagnoseDeferred: CompletableDeferred<Unit>? = null

        override val userPreferences: UserPreferences get() = throw UnsupportedOperationException()
        override fun getAllActivePlants(): Flow<List<PlantEntity>> = flowOf(listOf(plant))
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = flowOf(emptyList())
        override suspend fun getPlantById(plantId: Long): PlantEntity? = if (plant.id == plantId) plant else null
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = flowOf(plant)
        override suspend fun insertPlant(plant: PlantEntity): Long = plant.id
        override suspend fun updatePlant(plant: PlantEntity) {}
        override suspend fun deletePlant(plant: PlantEntity) {}
        override fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>> = flowOf(careEvents)
        override suspend fun getLastWateringEvent(plantId: Long): CareEventEntity? = null
        override suspend fun logCareEvent(event: CareEventEntity): Long {
            careEvents.add(event)
            return event.id
        }
        override fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>> = flowOf(emptyList())
        override fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>> = flowOf(emptyList())
        override suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity? = null
        override fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>> = flowOf(emptyList())
        override fun getGeneralPods(): Flow<List<CarePodEntity>> = flowOf(emptyList())
        override suspend fun getSymptomTree(): SymptomTreeRoot = SymptomTreeRoot(
            version = "1.0",
            disclaimer_uk = "Тестовий дисклеймер",
            categories = listOf(
                SymptomCategory(
                    id = "leaves",
                    name_uk = "Листя",
                    symptoms = listOf(
                        SymptomNode(id = "yellow_leaves", name_uk = "Жовтіє листя")
                    )
                )
            )
        )
        override suspend fun performWatering(plant: PlantEntity, notes: String?) {}
        override suspend fun snoozePlant(plant: PlantEntity, days: Int) {}
        override suspend fun skipPlant(plant: PlantEntity) {}
        override suspend fun generatePlantWithAi(plantQuery: String): GeneratePlantResult = throw UnsupportedOperationException()
        override suspend fun identifyPlantFromPhoto(imageFile: File): IdentifyPlantResult = throw UnsupportedOperationException()

        override suspend fun diagnosePlantHealth(imageFile: File, plant: PlantEntity): DiagnosePlantHealthResult {
            diagnoseCalledCount++
            onDiagnoseDeferred?.await()
            return diagnoseHealthResult
        }
    }

    @Test
    fun initialModeIsModeSelection_andLoadsPlantData() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 100L, customName = "Драцена")
        val repo = FakeTestFlowersRepository(plant)
        val viewModel = DiagnosisViewModel(plant.id, repo)

        testScheduler.runCurrent()
        val state = viewModel.uiState.value
        assertEquals(DiagnosisMode.MODE_SELECTION, state.mode)
        assertEquals("Драцена", state.plant?.customName)
        assertEquals(1, state.categories.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun selectMode_switchesBetweenPhotoAndManual() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 101L, customName = "Спатифілум")
        val repo = FakeTestFlowersRepository(plant)
        val viewModel = DiagnosisViewModel(plant.id, repo)
        testScheduler.runCurrent()

        viewModel.selectMode(DiagnosisMode.PHOTO_DIAGNOSIS)
        assertEquals(DiagnosisMode.PHOTO_DIAGNOSIS, viewModel.uiState.value.mode)

        viewModel.selectMode(DiagnosisMode.MANUAL_SYMPTOMS)
        assertEquals(DiagnosisMode.MANUAL_SYMPTOMS, viewModel.uiState.value.mode)

        viewModel.resetToModeSelection()
        assertEquals(DiagnosisMode.MODE_SELECTION, viewModel.uiState.value.mode)
    }

    @Test
    fun manualSymptomsFlow_evaluatesCorrelationAndSavesToJournal() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 102L, customName = "Монстера")
        val repo = FakeTestFlowersRepository(plant)
        val viewModel = DiagnosisViewModel(plant.id, repo)
        testScheduler.runCurrent()

        viewModel.selectMode(DiagnosisMode.MANUAL_SYMPTOMS)
        val category = viewModel.uiState.value.categories.first()
        viewModel.onCategorySelected(category)
        assertEquals(category, viewModel.uiState.value.selectedCategory)

        val symptom = category.symptoms.first()
        viewModel.onSymptomSelected(symptom)
        testScheduler.runCurrent()

        assertNotNull(viewModel.uiState.value.result)
        assertEquals(symptom, viewModel.uiState.value.selectedSymptom)

        var saved = false
        viewModel.saveDiagnosisToJournal { saved = true }
        testScheduler.runCurrent()

        assertTrue(saved)
        assertEquals(1, repo.careEvents.size)
        assertEquals("CHECK_STATE", repo.careEvents.first().eventType)
    }

    @Test
    fun photoDiagnosisFlow_processesPhoto_confirms_andSavesHealthCheck() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 103L, customName = "Антуріум")
        val repo = FakeTestFlowersRepository(plant)
        val viewModel = DiagnosisViewModel(plantId = plant.id, repository = repo)
        testScheduler.runCurrent()

        viewModel.selectMode(DiagnosisMode.PHOTO_DIAGNOSIS)

        // Select photo
        val dummyPhoto = tempFolder.newFile("sample_diag.jpg").apply { writeText("dummy") }
        viewModel.onPhotoSelected(dummyPhoto)
        testScheduler.runCurrent()

        val previewStep = viewModel.uiState.value.photoStep
        assertTrue("Expected PreviewAndConfirm but was $previewStep", previewStep is PhotoDiagnosisStep.PreviewAndConfirm)
        val photoFile = (previewStep as PhotoDiagnosisStep.PreviewAndConfirm).photoFile
        assertEquals(dummyPhoto.absolutePath, photoFile.absolutePath)

        // Confirm analysis
        viewModel.onConfirmAnalysis()
        testScheduler.runCurrent()

        val successStep = viewModel.uiState.value.photoStep
        assertTrue("Expected Success but was $successStep", successStep is PhotoDiagnosisStep.Success)
        val diagnosis = (successStep as PhotoDiagnosisStep.Success).diagnosis
        assertEquals("Перелив ґрунту", diagnosis.probableCause)
        assertEquals("HIGH", diagnosis.confidence)

        // Save to history
        var savedCalled = false
        viewModel.savePhotoDiagnosisToHistory { savedCalled = true }
        testScheduler.runCurrent()

        assertTrue(savedCalled)
        assertEquals(1, repo.careEvents.size)
        val event = repo.careEvents.first()
        assertEquals("HEALTH_CHECK", event.eventType)
        assertEquals(photoFile.absolutePath, event.photoUri)
        assertTrue(event.notes?.contains("Перелив ґрунту") == true)
        assertTrue(event.notes?.contains("HIGH") == true)
        assertTrue(photoFile.exists())
    }

    @Test
    fun savePhotoDiagnosisToHistory_debounceProtection_preventsDoubleSave() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 104L, customName = "Бегонія")
        val repo = FakeTestFlowersRepository(plant)
        val viewModel = DiagnosisViewModel(plantId = plant.id, repository = repo)
        testScheduler.runCurrent()

        val dummyPhoto = tempFolder.newFile("sample_diag2.jpg").apply { writeText("dummy") }
        viewModel.onPhotoSelected(dummyPhoto)
        testScheduler.runCurrent()
        viewModel.onConfirmAnalysis()
        testScheduler.runCurrent()

        var callCount = 0
        viewModel.savePhotoDiagnosisToHistory { callCount++ }
        // Attempt duplicate click while processing
        viewModel.savePhotoDiagnosisToHistory { callCount++ }
        testScheduler.runCurrent()

        assertEquals(1, callCount)
        assertEquals(1, repo.careEvents.size)
    }

    @Test
    fun cancelActivePhotoOperation_deletesPendingPhoto_andResetsToPickPhoto() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 105L, customName = "Фікус")
        val repo = FakeTestFlowersRepository(plant)
        val viewModel = DiagnosisViewModel(plantId = plant.id, repository = repo)
        testScheduler.runCurrent()

        val dummyPhoto = tempFolder.newFile("sample_diag3.jpg").apply { writeText("dummy") }
        viewModel.onPhotoSelected(dummyPhoto)
        testScheduler.runCurrent()

        val step = viewModel.uiState.value.photoStep as PhotoDiagnosisStep.PreviewAndConfirm
        val tempPhoto = step.photoFile
        assertTrue(tempPhoto.exists())

        viewModel.cancelActivePhotoOperation()
        testScheduler.runCurrent()

        assertEquals(PhotoDiagnosisStep.PickPhoto, viewModel.uiState.value.photoStep)
        assertFalse(tempPhoto.exists())
    }

    @Test
    fun photoDiagnosis_handlesNotAPlant_transitionsToError() = runTest(testDispatcher) {
        val plant = PlantEntity(id = 106L, customName = "Кактус")
        val repo = FakeTestFlowersRepository(plant)
        repo.diagnoseHealthResult = DiagnosePlantHealthResult.NotAPlant("На фото не виявлено рослину")
        val viewModel = DiagnosisViewModel(plantId = plant.id, repository = repo)
        testScheduler.runCurrent()

        val dummyPhoto = tempFolder.newFile("sample_diag4.jpg").apply { writeText("dummy") }
        viewModel.onPhotoSelected(dummyPhoto)
        testScheduler.runCurrent()

        viewModel.onConfirmAnalysis()
        testScheduler.runCurrent()

        val errorStep = viewModel.uiState.value.photoStep
        assertTrue(errorStep is PhotoDiagnosisStep.Error)
        assertEquals("На фото не виявлено рослину", (errorStep as PhotoDiagnosisStep.Error).message)
    }
}
