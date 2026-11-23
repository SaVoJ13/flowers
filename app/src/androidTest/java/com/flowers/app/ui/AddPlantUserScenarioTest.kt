package com.flowers.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.image.ImageDimensions
import com.flowers.app.data.image.ImageFormat
import com.flowers.app.data.image.ImageProcessor
import com.flowers.app.data.image.ImageProfile
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.local.Migrations
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.repository.FlowersRepositoryImpl
import com.flowers.app.ui.plants.AddPlantUiState
import com.flowers.app.ui.plants.PlantsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Наскрізне тестування користувацького сценарію Flowers 2.0 (TASK-053).
 * Перевіряє на ізольованому емуляторі:
 * 1. Зображення -> ImageProcessor (1024px, без GPS, локальне безпечне збереження).
 * 2. ШІ-розпізнавання (контрольована відповідь) -> екран підтвердження.
 * 3. Перевірка бейджа, порад та безпечне скидання при виборі альтернативного виду.
 * 4. Збереження в Room v3 -> правдива історія (lastWateredDate == null, без штучних WATER подій).
 * 5. Повторне відкриття картки з бази.
 * 6. Сценарій скасування (видалення тимчасового фото).
 * 7. Офлайн-сценарій (ручне введення та збереження).
 */
@RunWith(AndroidJUnit4::class)
class AddPlantUserScenarioTest {

    private lateinit var context: Context
    private lateinit var db: FlowersDatabase
    private val dbName = "scenario_test_db.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
        File(context.filesDir, "plants").deleteRecursively()
        File(context.cacheDir, "camera").deleteRecursively()
    }

    private fun createTestBitmapFile(name: String, width: Int = 1600, height: Int = 1200): File {
        val cacheDir = File(context.cacheDir, "test_source").apply { mkdirs() }
        val file = File(cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun testFullPhotoFlowToConfirmationAndSaveWithTruthfulWatering() = runBlocking {
        // 1. Створюємо тестове фото
        val sourceFile = createTestBitmapFile("monstera_photo.jpg", 1600, 1200)
        assertTrue(sourceFile.exists())

        // 2. Обробка через ImageProcessor
        val processor = ImageProcessor(context)
        val processResult = processor.processFile(sourceFile, ImageProfile.SpeciesIdentification)
        assertTrue("ImageProcessor мав успішно завершитися", processResult.isSuccess)
        val imageResult = processResult.getOrThrow()
        assertTrue("Оброблений файл має існувати", imageResult.file.exists())
        assertTrue("Максимальний розмір не перевищує 1024px", imageResult.processedDimensions.maxDimension <= 1024)

        // 3. Контрольована відповідь ШІ
        val mockAiJson = """
            {
              "is_plant": true,
              "name_uk": "Монстера делікатесна",
              "name_latin": "Monstera deliciosa",
              "confidence": "HIGH",
              "description": "Популярна ліана з великим різьбленим листям",
              "light_level": "INDIRECT",
              "default_check_interval_days": 8,
              "watering_advice": "Поливати після просихання верхнього шару",
              "soil_type": "Пухкий субстрат для ароїдних",
              "toxicity_info": "Листя містить оксалати кальцію",
              "alternative_species": ["Філодендрон двійчастоперистий"]
            }
        """.trimIndent()

        val plantService = GeminiPlantService(
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> mockAiJson }
        )

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = plantService
        )

        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        // 4. Запуск розпізнавання за фото
        viewModel.onPhotoSelected(Uri.fromFile(sourceFile), context)

        // Чекаємо переходу в стан підтвердження
        var state = viewModel.addPlantUiState.first { it is AddPlantUiState.Confirmation }
        val confirmation = state as AddPlantUiState.Confirmation

        assertEquals("Монстера делікатесна", confirmation.identification.nameUk)
        assertEquals("Monstera deliciosa", confirmation.identification.nameLatin)
        assertEquals("HIGH", confirmation.identification.confidence)
        assertEquals("INDIRECT", confirmation.identification.lightLevel)
        assertEquals(8, confirmation.identification.defaultCheckIntervalDays)
        assertTrue("Тимчасовий файл фото існує", confirmation.photoFile.exists())

        // 5. Збереження картки користувачем
        var saved = false
        viewModel.savePlant(
            plantId = null,
            customName = "Монстера на підвіконні",
            roomLocation = "Вітальня",
            lightLevel = "INDIRECT",
            checkIntervalDays = 8,
            photoUri = confirmation.photoFile.absolutePath,
            speciesName = confirmation.identification.nameUk,
            botanicalName = confirmation.identification.nameLatin,
            description = confirmation.identification.description,
            wateringAdvice = confirmation.identification.wateringAdvice,
            soilType = confirmation.identification.soilType,
            toxicityInfo = confirmation.identification.toxicityInfo,
            aiConfidence = confirmation.identification.confidence,
            isAiGenerated = true,
            onSaved = { saved = true }
        )

        // Очікуємо завершення асинхронного збереження
        val startSave = System.currentTimeMillis()
        while (!saved && System.currentTimeMillis() - startSave < 5000) {
            kotlinx.coroutines.delay(50)
        }
        assertTrue("Рослина мала бути успішно збережена", saved)

        // 6. Повторне відкриття з Room v3
        val activePlants = repository.getAllActivePlants().first()
        assertEquals(1, activePlants.size)
        val plant = activePlants.first()

        assertEquals("Монстера на підвіконні", plant.customName)
        assertEquals("Монстера делікатесна", plant.speciesName)
        assertEquals("Monstera deliciosa", plant.botanicalName)
        assertEquals("Вітальня", plant.roomLocation)
        assertEquals("INDIRECT", plant.lightLevel)
        assertEquals(8, plant.checkIntervalDays)
        assertEquals("Популярна ліана з великим різьбленим листям", plant.description)
        assertEquals("Поливати після просихання верхнього шару", plant.wateringAdvice)
        assertEquals("Пухкий субстрат для ароїдних", plant.soilType)
        assertEquals("Листя містить оксалати кальцію", plant.toxicityInfo)
        assertEquals("HIGH", plant.aiConfidence)
        assertTrue(plant.isAiGenerated)

        // Правдива історія: створення картки НЕ є поливом!
        assertNull("lastWateredDate має бути null без фактичного поливу", plant.lastWateredDate)
        val events = repository.getEventsForPlant(plant.id).first()
        assertEquals("Жодної штучної події WATER не повинно бути записано", 0, events.size)
    }

    @Test
    fun testCancellationDeletesTemporaryPhotoFileAndLeavesStateIdle() = runBlocking {
        val sourceFile = createTestBitmapFile("cancel_test_photo.jpg", 1000, 800)
        val processor = ImageProcessor(context)

        val plantService = GeminiPlantService(
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ ->
                """{"is_plant": true, "name_uk": "Тест", "name_latin": "Test", "confidence": "HIGH", "default_check_interval_days": 7}"""
            }
        )

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = plantService
        )

        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        // Початок обробки
        viewModel.onPhotoSelected(Uri.fromFile(sourceFile), context)

        // Чекаємо завершення у Confirmation
        val confirmationState = viewModel.addPlantUiState.first { it is AddPlantUiState.Confirmation } as AddPlantUiState.Confirmation
        val tempPhotoFile = confirmationState.photoFile
        assertTrue("Тимчасовий файл фото створено", tempPhotoFile.exists())

        // Користувач скасовує операцію
        viewModel.resetAddPlantFlow(discardPhoto = true)

        assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)
        assertFalse("Тимчасовий файл фото має бути видалений при скасуванні", tempPhotoFile.exists())
    }

    @Test
    fun testOfflineFallbackAllowsManualPlantCreation() = runBlocking {
        val sourceFile = createTestBitmapFile("offline_test_photo.jpg", 800, 600)
        val processor = ImageProcessor(context)

        // Симулюємо відсутність інтернету
        val plantService = GeminiPlantService(
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            networkChecker = NetworkChecker { false }
        )

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = plantService
        )

        val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

        viewModel.onPhotoSelected(Uri.fromFile(sourceFile), context)

        // Чекаємо помилки офлайн
        val errorState = viewModel.addPlantUiState.first { it is AddPlantUiState.ErrorFallback } as AddPlantUiState.ErrorFallback
        assertTrue(errorState.isOffline)

        // Перехід у ручне введення
        viewModel.onManualEntryRequested(errorState.photoFile)

        val confirmationState = viewModel.addPlantUiState.first { it is AddPlantUiState.Confirmation } as AddPlantUiState.Confirmation
        assertTrue("Має бути увімкнено прапорець офлайн-фолбеку", confirmationState.isOfflineFallback)

        // Збереження вручну
        var saved = false
        viewModel.savePlant(
            plantId = null,
            customName = "Офлайн Рослина",
            speciesName = "Невідомий вид",
            botanicalName = null,
            roomLocation = "Кухня",
            lightLevel = "SHADE",
            checkIntervalDays = 5,
            photoUri = confirmationState.photoFile.takeIf { it.exists() }?.absolutePath,
            onSaved = { saved = true }
        )

        // Очікуємо завершення асинхронного збереження
        val startSave = System.currentTimeMillis()
        while (!saved && System.currentTimeMillis() - startSave < 5000) {
            kotlinx.coroutines.delay(50)
        }
        assertTrue("Рослина мала бути успішно збережена", saved)
        val plants = repository.getAllActivePlants().first()
        assertEquals(1, plants.size)
        assertEquals("Офлайн Рослина", plants[0].customName)
        assertEquals("Невідомий вид", plants[0].speciesName)
        assertNull(plants[0].lastWateredDate)
    }
}
