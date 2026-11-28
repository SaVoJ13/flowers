package com.flowers.app.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.image.ImageProcessor
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.local.Migrations
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.repository.FlowersRepositoryImpl
import com.flowers.app.ui.plants.AddEditPlantScreen
import com.flowers.app.ui.plants.AddPlantUiState
import com.flowers.app.ui.plants.PlantsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Android Compose UI інструментальні тести екрана додавання рослини (TASK-053).
 *
 * Використовує runComposeUiTest на чистому Compose-оточенні без залежності від застарілого Espresso InputManager.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AddEditPlantComposeUiTest {

    private lateinit var context: Context
    private lateinit var db: FlowersDatabase
    private val dbName = "compose_test_db.db"
    private lateinit var testOutputDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        testOutputDir = File(context.cacheDir, "compose_test_plants_${System.currentTimeMillis()}").apply { mkdirs() }

        db = Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
        if (::testOutputDir.isInitialized && testOutputDir.exists()) {
            testOutputDir.deleteRecursively()
        }
    }

    @Test
    fun testIdleScreenDisplaysCameraGalleryAndManualButtons() = runComposeUiTest {
        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = GeminiPlantService(
                catalogDao = db.catalogDao(),
                carePodDao = db.carePodDao(),
                networkChecker = NetworkChecker { true }
            )
        )
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)
        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        setContent {
            AddEditPlantScreen(
                plantId = null,
                viewModel = viewModel,
                repository = repository,
                onNavigateBack = {}
            )
        }

        // Перевіряємо наявність заголовка та трьох основних кнопок додавання
        onNodeWithText("Нова рослина").assertIsDisplayed()
        onNodeWithText("Додавання рослини за фото").assertIsDisplayed()
        onNodeWithText("Камера").assertIsDisplayed()
        onNodeWithText("Галерея").assertIsDisplayed()
        onNodeWithText("Ввести дані вручну без фото").assertIsDisplayed()
    }

    @Test
    fun testClickManualEntryOpensConfirmationForm() = runComposeUiTest {
        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = GeminiPlantService(
                catalogDao = db.catalogDao(),
                carePodDao = db.carePodDao(),
                networkChecker = NetworkChecker { true }
            )
        )
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)
        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        setContent {
            AddEditPlantScreen(
                plantId = null,
                viewModel = viewModel,
                repository = repository,
                onNavigateBack = {}
            )
        }

        // Натискаємо кнопку введення вручну
        onNodeWithText("Ввести дані вручну без фото").performClick()

        // Перевіряємо форму підтвердження та створення
        onNodeWithText("Підтвердження рослини").assertIsDisplayed()
        onNodeWithText("Власна назва вазона *").assertIsDisplayed()
        onNodeWithText("Зберегти в мої рослини").performScrollTo().assertIsDisplayed()
        onNodeWithText("Скасувати").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testManualEntryFillFormAndSaveToRoom() = runComposeUiTest {
        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = GeminiPlantService(
                catalogDao = db.catalogDao(),
                carePodDao = db.carePodDao(),
                networkChecker = NetworkChecker { true }
            )
        )
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)
        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        var navigatedBack = false
        setContent {
            AddEditPlantScreen(
                plantId = null,
                viewModel = viewModel,
                repository = repository,
                onNavigateBack = { navigatedBack = true }
            )
        }

        // Переходимо у форму вручну
        onNodeWithText("Ввести дані вручну без фото").performClick()

        // Замінюємо назву у полі
        onNodeWithText("Власна назва вазона *").performTextReplacement("Мій кімнатний плющ")

        // Натискаємо "Зберегти в мої рослини"
        onNodeWithText("Зберегти в мої рослини").performScrollTo().performClick()

        // Очікуємо збереження
        waitUntil(timeoutMillis = 5000) {
            navigatedBack
        }
        assertTrue("onNavigateBack мав викликатися після збереження", navigatedBack)

        // Перевіряємо збереження в Room v3
        runBlocking {
            val plants = repository.getAllActivePlants().first()
            assertEquals(1, plants.size)
            val savedPlant = plants[0]
            assertEquals("Мій кімнатний плющ", savedPlant.customName)
            assertNull("photoUri має бути строго null для ручного створення без фото", savedPlant.photoUri)
            assertNull("lastWateredDate має бути null без фактичного поливу", savedPlant.lastWateredDate)
        }
    }

    @Test
    fun testCancelButtonInConfirmationResetsToIdle() = runComposeUiTest {
        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = GeminiPlantService(
                catalogDao = db.catalogDao(),
                carePodDao = db.carePodDao(),
                networkChecker = NetworkChecker { true }
            )
        )
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)
        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        setContent {
            AddEditPlantScreen(
                plantId = null,
                viewModel = viewModel,
                repository = repository,
                onNavigateBack = {}
            )
        }

        // Входимо у форму
        onNodeWithText("Ввести дані вручну без фото").performClick()
        onNodeWithText("Підтвердження рослини").assertIsDisplayed()

        // Натискаємо "Скасувати"
        onNodeWithText("Скасувати").performScrollTo().performClick()

        // Перевіряємо, що повернулися до початкового екрана
        onNodeWithText("Камера").assertIsDisplayed()
        onNodeWithText("Галерея").assertIsDisplayed()
        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)
    }
}
