package com.flowers.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.image.ImageProcessor
import com.flowers.app.data.image.ImageProfile
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.local.Migrations
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.repository.FlowersRepositoryImpl
import com.flowers.app.ui.plants.AddPlantUiState
import com.flowers.app.ui.plants.PlantsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
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
 * Наскрізне інструментальне тестування користувацького сценарію Flowers 2.0 (TASK-053).
 *
 * Виконується на ізольованому емуляторі:
 * 1. Зображення -> ImageProcessor (1024px, без GPS, унікальна тестова папка).
 * 2. ШІ-розпізнавання -> екран підтвердження.
 * 3. Збереження в Room v3 -> правдива історія (lastWateredDate == null, без штучних WATER подій).
 * 4. Захист фотографій користувача: сторонні фото у filesDir/plants гарантовано НЕ видаляються.
 * 5. Ручне додавання без фото: photoUri = null.
 * 6. Скасування під час активного аналізу ШІ (контрольована затримка, перевірка Idle, очищення тимчасового фото).
 * 7. Офлайн-фолбек.
 */
@RunWith(AndroidJUnit4::class)
class AddPlantUserScenarioTest {

    private lateinit var context: Context
    private lateinit var db: FlowersDatabase
    private val dbName = "scenario_test_db.db"

    // Ізольовані тестові директорії (заборонено видаляти загальні filesDir/plants чи cacheDir/camera)
    private lateinit var testOutputDir: File
    private lateinit var testCacheDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        val timestamp = System.currentTimeMillis()
        testOutputDir = File(context.cacheDir, "test_plants_$timestamp").apply { mkdirs() }
        testCacheDir = File(context.cacheDir, "test_source_$timestamp").apply { mkdirs() }

        db = Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
        // Видаляємо ТІЛЬКИ власні тестові папки, ніколи не чіпаємо загальні папки застосунку
        if (::testOutputDir.isInitialized && testOutputDir.exists()) {
            testOutputDir.deleteRecursively()
        }
        if (::testCacheDir.isInitialized && testCacheDir.exists()) {
            testCacheDir.deleteRecursively()
        }
    }

    private fun createTestBitmapFile(name: String, width: Int = 1600, height: Int = 1200): File {
        val file = File(testCacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun testFullPhotoFlowToConfirmationAndSaveWithTruthfulWatering() = runBlocking {
        // 1. Тестове фото
        val sourceFile = createTestBitmapFile("monstera_photo.jpg", 1600, 1200)
        assertTrue(sourceFile.exists())

        // 2. Ізольований ImageProcessor
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)
        val processResult = processor.processFile(sourceFile, ImageProfile.SpeciesIdentification)
        assertTrue("ImageProcessor мав успішно завершитися", processResult.isSuccess)
        val imageResult = processResult.getOrThrow()
        assertTrue("Оброблений файл має бути в testOutputDir", imageResult.file.parentFile == testOutputDir)
        assertTrue("Файл існує", imageResult.file.exists())
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

        // 4. Запуск розпізнавання
        viewModel.onPhotoSelected(Uri.fromFile(sourceFile), context)

        val state = viewModel.addPlantUiState.first { it is AddPlantUiState.Confirmation }
        val confirmation = state as AddPlantUiState.Confirmation

        assertEquals("Монстера делікатесна", confirmation.identification.nameUk)
        assertEquals("Monstera deliciosa", confirmation.identification.nameLatin)
        assertEquals("HIGH", confirmation.identification.confidence)
        assertEquals("INDIRECT", confirmation.identification.lightLevel)
        assertEquals(8, confirmation.identification.defaultCheckIntervalDays)
        assertNotNull(confirmation.photoFile)
        assertTrue("Тимчасовий файл фото існує", confirmation.photoFile!!.exists())

        // 5. Збереження картки
        var saved = false
        viewModel.savePlant(
            plantId = null,
            customName = "Монстера на підвіконні",
            roomLocation = "Вітальня",
            lightLevel = "INDIRECT",
            checkIntervalDays = 8,
            photoUri = confirmation.photoFile!!.absolutePath,
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

        val startSave = System.currentTimeMillis()
        while (!saved && System.currentTimeMillis() - startSave < 5000) {
            delay(50)
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
    fun testPreExistingUserPhotosInPlantsDirectoryAreNeverTouched() = runBlocking {
        // Створюємо "користувацьку" фотографію в реальному каталозі filesDir/plants
        val plantsDir = File(context.filesDir, "plants").apply { mkdirs() }
        val realUserPhoto = File(plantsDir, "existing_user_photo_do_not_delete.jpg")
        val content = "USER_SECRET_PHOTO_PAYLOAD_${System.currentTimeMillis()}"
        realUserPhoto.writeText(content)
        val initialTimestamp = realUserPhoto.lastModified()
        assertTrue("Фото користувача має існувати до тесту", realUserPhoto.exists())

        try {
            // Виконуємо повний сценарій тесту в окремій директорії testOutputDir
            val processor = ImageProcessor(context, outputDirectory = testOutputDir)
            val sourceFile = createTestBitmapFile("flow_photo.jpg", 800, 600)

            val repository = FlowersRepositoryImpl(
                plantDao = db.plantDao(),
                careEventDao = db.careEventDao(),
                catalogDao = db.catalogDao(),
                carePodDao = db.carePodDao(),
                context = context,
                geminiPlantService = GeminiPlantService(
                    catalogDao = db.catalogDao(),
                    carePodDao = db.carePodDao(),
                    networkChecker = NetworkChecker { true },
                    generateMultimodal = { _, _ -> """{"is_plant": true, "name_uk": "Тест", "name_latin": "Test", "confidence": "HIGH", "default_check_interval_days": 7}""" }
                )
            )
            val viewModel = PlantsViewModel(repository, imageProcessorProvider = { processor })

            viewModel.onPhotoSelected(Uri.fromFile(sourceFile), context)
            val confirmation = viewModel.addPlantUiState.first { it is AddPlantUiState.Confirmation } as AddPlantUiState.Confirmation
            assertTrue(confirmation.photoFile!!.exists())

            // Скасовуємо з очищенням
            viewModel.resetAddPlantFlow(discardPhoto = true)
            assertEquals(AddPlantUiState.Idle, viewModel.addPlantUiState.value)

            // Перевіряємо, що стороннє фото користувача в filesDir/plants НЕ постраждало
            assertTrue("Стороннє фото користувача НЕ повинно бути видалене!", realUserPhoto.exists())
            assertEquals("Вміст стороннього фото не повинен бути модифікований!", content, realUserPhoto.readText())
            assertEquals("Дата модифікації стороннього фото не повинна змінитися!", initialTimestamp, realUserPhoto.lastModified())
        } finally {
            // Очищуємо ТІЛЬКИ свій створений тестовий маркер
            if (realUserPhoto.exists()) {
                realUserPhoto.delete()
            }
        }
    }

    @Test
    fun testManualPlantCreationWithoutPhotoSavesPhotoUriAsNull() = runBlocking {
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

        // Ручний вхід без фото
        viewModel.onManualEntryRequested(photoFile = null)

        val state = viewModel.addPlantUiState.value
        assertTrue(state is AddPlantUiState.Confirmation)
        val confirmation = state as AddPlantUiState.Confirmation
        assertNull("photoFile має бути null при вході без фото", confirmation.photoFile)
        assertTrue(confirmation.isOfflineFallback)

        var saved = false
        // Спроба зберегти: передаємо null або порожній рядок замість фото
        viewModel.savePlant(
            plantId = null,
            customName = "Без фото вазон",
            roomLocation = "Спальня",
            lightLevel = "DIRECT",
            checkIntervalDays = 7,
            photoUri = null,
            speciesName = "Сансевієрія",
            onSaved = { saved = true }
        )

        val startSave = System.currentTimeMillis()
        while (!saved && System.currentTimeMillis() - startSave < 5000) {
            delay(50)
        }
        assertTrue(saved)

        val activePlants = repository.getAllActivePlants().first()
        assertEquals(1, activePlants.size)
        val plant = activePlants.first()
        assertEquals("Без фото вазон", plant.customName)
        assertEquals("Сансевієрія", plant.speciesName)
        assertNull("photoUri в базі має бути строго null для рослини без фото", plant.photoUri)
        assertNull("lastWateredDate має бути null", plant.lastWateredDate)
    }

    @Test
    fun testCancellationDuringActiveAiAnalysisKeepsIdleAndCleansTempFiles() = runBlocking {
        val sourceFile = createTestBitmapFile("cancel_active_photo.jpg", 1000, 800)
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)

        val identifyGate = CompletableDeferred<Unit>()
        val plantService = GeminiPlantService(
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ ->
                identifyGate.await()
                """{"is_plant": true, "name_uk": "Монстера", "name_latin": "Monstera", "confidence": "HIGH", "default_check_interval_days": 7}"""
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

        // 1. Запускаємо розпізнавання
        viewModel.onPhotoSelected(Uri.fromFile(sourceFile), context)

        // 2. Чекаємо переходу в AnalyzingWithAi
        val analyzingState = viewModel.addPlantUiState.first { it is AddPlantUiState.AnalyzingWithAi } as AddPlantUiState.AnalyzingWithAi
        val tempPhotoFile = analyzingState.photoFile
        assertTrue("Тимчасовий файл фото створено і він існує", tempPhotoFile.exists())

        // 3. Користувач скасовує операцію ДО завершення відповіді ШІ
        viewModel.resetAddPlantFlow(discardPhoto = true)

        // Стан має негайно стати Idle
        assertEquals("Стан має стати Idle після скасування", AddPlantUiState.Idle, viewModel.addPlantUiState.value)
        assertFalse("Тимчасовий файл має бути видалений при скасуванні", tempPhotoFile.exists())

        // 4. Тепер ШІ повертає запізнілу відповідь
        identifyGate.complete(Unit)
        delay(200)

        // Перевіряємо, що запізніла відповідь ШІ НЕ змінила стан і НЕ відкрила Confirmation
        assertEquals("Стан повинен залишитися Idle навіть після запізнілої відповіді ШІ", AddPlantUiState.Idle, viewModel.addPlantUiState.value)
        assertFalse("Тимчасовий файл не повинен з'явитися знову", tempPhotoFile.exists())

        // 5. Перевіряємо, що новий наступний запит не пошкоджується попереднім скасуванням
        val secondSource = createTestBitmapFile("second_photo.jpg", 800, 600)
        val immediateService = GeminiPlantService(
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> """{"is_plant": true, "name_uk": "Фікус", "name_latin": "Ficus", "confidence": "HIGH", "default_check_interval_days": 5}""" }
        )
        val secondRepo = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = immediateService
        )
        val secondViewModel = PlantsViewModel(secondRepo, imageProcessorProvider = { processor })
        secondViewModel.onPhotoSelected(Uri.fromFile(secondSource), context)

        val secondState = secondViewModel.addPlantUiState.first { it is AddPlantUiState.Confirmation } as AddPlantUiState.Confirmation
        assertEquals("Фікус", secondState.identification.nameUk)
        assertTrue("Новий файл фото існує", secondState.photoFile!!.exists())
        secondViewModel.resetAddPlantFlow(discardPhoto = true)
        assertFalse(secondState.photoFile!!.exists())
    }

    @Test
    fun testOfflineFallbackAllowsManualPlantCreation() = runBlocking {
        val sourceFile = createTestBitmapFile("offline_test_photo.jpg", 800, 600)
        val processor = ImageProcessor(context, outputDirectory = testOutputDir)

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

        // Перехід у ручне введення з наявним фото
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
            photoUri = confirmationState.photoFile?.takeIf { it.exists() }?.absolutePath,
            onSaved = { saved = true }
        )

        val startSave = System.currentTimeMillis()
        while (!saved && System.currentTimeMillis() - startSave < 5000) {
            delay(50)
        }
        assertTrue("Рослина мала бути успішно збережена", saved)
        val plants = repository.getAllActivePlants().first()
        assertEquals(1, plants.size)
        assertEquals("Офлайн Рослина", plants[0].customName)
        assertEquals("Невідомий вид", plants[0].speciesName)
        assertNull(plants[0].lastWateredDate)
    }
}
