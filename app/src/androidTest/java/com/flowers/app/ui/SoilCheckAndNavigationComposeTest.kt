package com.flowers.app.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.repository.FlowersRepositoryImpl
import com.flowers.app.ui.components.BottomNavBar
import com.flowers.app.ui.today.TodayScreen
import com.flowers.app.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Android Compose UI інструментальні тести для TASK-054:
 * 1. Три розділи головної навігації (без Довідника).
 * 2. Адаптивні поради перевірки ґрунту на екрані Сьогодні.
 * 3. Дія «Ґрунт перевірено — Полито» з обов'язковим підтвердженням і записом WATER.
 * 4. Дія «Ще вологий — Відкласти на 2 дні» без зміни lastWateredDate і з записом DELAY.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SoilCheckAndNavigationComposeTest {

    private lateinit var context: Context
    private lateinit var db: FlowersDatabase
    private lateinit var testOutputDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testOutputDir = File(context.cacheDir, "soil_test_plants_${System.currentTimeMillis()}").apply { mkdirs() }

        db = Room.inMemoryDatabaseBuilder(context, FlowersDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        if (::testOutputDir.isInitialized && testOutputDir.exists()) {
            testOutputDir.deleteRecursively()
        }
    }

    @Test
    fun testBottomNavBarDisplaysThreeTabsOnly() = runComposeUiTest {
        setContent {
            val navController = rememberNavController()
            BottomNavBar(navController = navController)
        }

        onNodeWithText("Сьогодні").assertIsDisplayed()
        onNodeWithText("Мої рослини").assertIsDisplayed()
        onNodeWithText("Опції").assertIsDisplayed()
        // Перевіряємо, що Довідник не присутній у BottomNavBar
        onAllNodesWithText("Довідник").assertCountEquals(0)
    }

    @Test
    fun testTodayScreenDisplaysAdaptiveAdviceAndButtons() = runComposeUiTest {
        val now = System.currentTimeMillis()
        val plant = PlantEntity(
            customName = "Монстера тестова",
            speciesName = "Монстера",
            botanicalName = "Monstera deliciosa",
            roomLocation = "Спальня",
            checkIntervalDays = 7,
            lastWateredDate = null,
            nextCheckDate = now - 1000L
        )

        val insertedId = runBlocking { db.plantDao().insertPlant(plant) }

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context
        )
        val viewModel = TodayViewModel(repository)

        setContent {
            TodayScreen(
                viewModel = viewModel,
                onNavigateToAddPlant = {},
                onNavigateToPlantDetail = {},
                onNavigateToDiagnosis = {}
            )
        }

        // Очікуємо завантаження даних із бази Room у StateFlow
        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.duePlants.isNotEmpty()
        }

        onNodeWithText("Потребують перевірки ґрунту (1)").assertIsDisplayed()
        onNodeWithText("Монстера тестова").assertIsDisplayed()
        onNodeWithText("Перевірте верхні 2–3 см ґрунту", substring = true).assertIsDisplayed()
        onNodeWithTag("water_button_$insertedId").assertExists()
        onNodeWithTag("snooze_button_$insertedId").assertExists()
    }

    @Test
    fun testWateringFlowRequiresConfirmationAndSavesWaterEvent() = runComposeUiTest {
        val now = System.currentTimeMillis()
        val plant = PlantEntity(
            customName = "Фікус на вікні",
            speciesName = "Фікус",
            roomLocation = "Вітальня",
            checkIntervalDays = 5,
            lastWateredDate = null,
            nextCheckDate = now - 5000L
        )
        val plantId = runBlocking { db.plantDao().insertPlant(plant) }

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context
        )
        val viewModel = TodayViewModel(repository)

        setContent {
            TodayScreen(
                viewModel = viewModel,
                onNavigateToAddPlant = {},
                onNavigateToPlantDetail = {},
                onNavigateToDiagnosis = {}
            )
        }

        // Очікуємо готовності стану
        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.duePlants.isNotEmpty()
        }

        // 1. Натискаємо кнопку «Ґрунт перевірено — Полито»
        onNodeWithTag("water_button_$plantId").performClick()

        // 2. З'являється діалог явного підтвердження
        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.plantToConfirmWatering != null
        }
        onNodeWithText("Підтвердження поливу").assertIsDisplayed()
        onNodeWithTag("confirm_water_button").assertIsDisplayed()

        // 3. Підтверджуємо реальний полив
        onNodeWithTag("confirm_water_button").performClick()

        // 4. Очікуємо асинхронного збереження події в Room
        waitUntil(timeoutMillis = 5_000) {
            runBlocking { db.careEventDao().getEventsForPlant(plantId).first().isNotEmpty() }
        }

        // 5. Перевіряємо базу даних
        val updatedPlant = runBlocking { db.plantDao().getPlantById(plantId) }
        assertNotNull(updatedPlant)
        assertNotNull(updatedPlant!!.lastWateredDate)
        assertTrue(updatedPlant.nextCheckDate > now)

        val events = runBlocking { db.careEventDao().getEventsForPlant(plantId).first() }
        assertEquals(1, events.size)
        assertEquals("WATER", events.first().eventType)
    }

    @Test
    fun testSnoozeTwoDaysDoesNotModifyLastWateredDateAndPostponesCheck() = runComposeUiTest {
        val now = System.currentTimeMillis()
        val plant = PlantEntity(
            customName = "Заміокулькас офісний",
            speciesName = "Заміокулькас",
            roomLocation = "Кабінет",
            checkIntervalDays = 14,
            lastWateredDate = null,
            nextCheckDate = now - 2000L
        )
        val plantId = runBlocking { db.plantDao().insertPlant(plant) }

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context
        )
        val viewModel = TodayViewModel(repository)

        setContent {
            TodayScreen(
                viewModel = viewModel,
                onNavigateToAddPlant = {},
                onNavigateToPlantDetail = {},
                onNavigateToDiagnosis = {}
            )
        }

        // Очікуємо готовності стану
        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.duePlants.isNotEmpty()
        }

        // Натискаємо «Ще вологий (на 2 дні)»
        onNodeWithTag("snooze_button_$plantId").performClick()

        // Очікуємо асинхронного запису події в Room
        waitUntil(timeoutMillis = 5_000) {
            runBlocking { db.careEventDao().getEventsForPlant(plantId).first().isNotEmpty() }
        }

        // Перевіряємо базу даних
        val updatedPlant = runBlocking { db.plantDao().getPlantById(plantId) }
        assertNotNull(updatedPlant)
        // КРИТИЧНО: lastWateredDate НЕ повинна стати фіктивною датою, має лишитися null!
        assertNull(updatedPlant!!.lastWateredDate)
        assertTrue(updatedPlant.nextCheckDate > now)

        val events = runBlocking { db.careEventDao().getEventsForPlant(plantId).first() }
        assertEquals(1, events.size)
        assertEquals("DELAY", events.first().eventType)
        assertEquals(2, events.first().daysDelayed)
    }
}
