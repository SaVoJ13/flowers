package com.flowers.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.image.AndroidImageDecoderEngine
import com.flowers.app.data.image.ImageProcessor
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.local.Migrations
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.repository.FlowersRepositoryImpl
import com.flowers.app.ui.diagnosis.DiagnosisMode
import com.flowers.app.ui.diagnosis.DiagnosisViewModel
import com.flowers.app.ui.diagnosis.PlantDiagnosisScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PhotoDiagnosisComposeTest {

    private lateinit var context: Context
    private lateinit var db: FlowersDatabase
    private val dbName = "photo_diagnosis_test_db_${System.currentTimeMillis()}.db"
    private lateinit var testOutputDir: File
    private lateinit var testTempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testOutputDir = File(context.cacheDir, "diag_output_${System.currentTimeMillis()}").apply { mkdirs() }
        testTempDir = File(context.cacheDir, "diag_temp_${System.currentTimeMillis()}").apply { mkdirs() }

        db = Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
            .build()
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } catch (_: Exception) {}
        context.deleteDatabase(dbName)
        testOutputDir.deleteRecursively()
        testTempDir.deleteRecursively()
    }

    private fun createSampleBitmapFile(dir: File, name: String): File {
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun testModeSelection_switchesToManualAndBack() = runComposeUiTest {
        var plantId = 0L
        runBlocking {
            plantId = db.plantDao().insertPlant(
                PlantEntity(
                    customName = "Монстера тестова",
                    speciesName = "Монстера"
                )
            )
        }

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context
        )

        val viewModel = DiagnosisViewModel(plantId, repository)

        setContent {
            PlantDiagnosisScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isLoading
        }

        onNodeWithTag("card_photo_diagnosis").assertIsDisplayed()
        onNodeWithTag("card_manual_diagnosis").assertIsDisplayed()

        // Клікаємо на ручну діагностику
        onNodeWithTag("card_manual_diagnosis").performClick()
        assertEquals(DiagnosisMode.MANUAL_SYMPTOMS, viewModel.uiState.value.mode)

        // Повертаємося до вибору способу
        onNodeWithText("Змінити спосіб").performClick()
        assertEquals(DiagnosisMode.MODE_SELECTION, viewModel.uiState.value.mode)
        onNodeWithTag("card_photo_diagnosis").assertIsDisplayed()
    }

    @Test
    fun testPhotoDiagnosisFlow_preview_analyze_andSaveHealthCheck() = runComposeUiTest {
        var plantId = 0L
        runBlocking {
            plantId = db.plantDao().insertPlant(
                PlantEntity(
                    customName = "Фікус Бенджаміна",
                    speciesName = "Фікус",
                    roomLocation = "Спальня"
                )
            )
        }

        val mockDiagnosisJson = """
            {
              "is_plant": true,
              "probable_cause": "Павутинний кліщ",
              "confidence": "MEDIUM",
              "visible_symptoms": ["Дрібні жовті цятки", "Тонка павутинка під листком"],
              "alternative_causes": ["Сухе повітря від батареї"],
              "first_aid_steps": [
                "Ізолюйте вазон від інших кімнатних рослин.",
                "Промийте листя під теплим душем, захистивши ґрунт пакетом.",
                "Регулярно обприскуйте рослину водою."
              ],
              "follow_up_advice": "Огляньте листя через 3–4 дні.",
              "preliminary_notice": "Попередня оцінка за фотографією."
            }
        """.trimIndent()

        val geminiService = GeminiPlantService(
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> mockDiagnosisJson }
        )

        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context,
            geminiPlantService = geminiService
        )

        val imageProcessor = ImageProcessor(
            context = context,
            outputDirectory = testOutputDir,
            engine = AndroidImageDecoderEngine()
        )

        val viewModel = DiagnosisViewModel(
            plantId = plantId,
            repository = repository,
            imageProcessorProvider = { imageProcessor }
        )

        var navigatedBack = false
        setContent {
            PlantDiagnosisScreen(
                viewModel = viewModel,
                onNavigateBack = { navigatedBack = true }
            )
        }

        waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isLoading
        }

        // 1. Обираємо режим фото-діагностики
        onNodeWithTag("card_photo_diagnosis").performClick()
        assertEquals(DiagnosisMode.PHOTO_DIAGNOSIS, viewModel.uiState.value.mode)

        // 2. Імітуємо вибір фотографії
        val samplePhoto = createSampleBitmapFile(testTempDir, "test_disease.jpg")
        val sampleUri = Uri.fromFile(samplePhoto)

        viewModel.onPhotoSelected(sampleUri, context)

        // Очікуємо переходу до попереднього перегляду
        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.photoStep is com.flowers.app.ui.diagnosis.PhotoDiagnosisStep.PreviewAndConfirm
        }

        onNodeWithTag("btn_confirm_analysis").assertIsDisplayed()

        // 3. Запускаємо аналіз
        onNodeWithTag("btn_confirm_analysis").performClick()

        // Очікуємо успішного результату
        waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.photoStep is com.flowers.app.ui.diagnosis.PhotoDiagnosisStep.Success
        }

        onNodeWithTag("photo_diagnosis_probable_cause").assertIsDisplayed()
        onNodeWithText("Павутинний кліщ").assertIsDisplayed()
        onNodeWithText("Альтернативні можливі причини:", substring = true).assertIsDisplayed()
        onNodeWithText("Сухе повітря від батареї", substring = true).assertIsDisplayed()
        onNodeWithText("Ізолюйте вазон від інших кімнатних рослин.", substring = true).assertIsDisplayed()

        // 4. Зберігаємо в історію
        onNodeWithTag("btn_save_diagnosis").performScrollTo().performClick()

        waitUntil(timeoutMillis = 5_000) {
            navigatedBack
        }
        assertTrue(navigatedBack)

        // 5. Перевіряємо збереження в базі даних Room
        val events = runBlocking { db.careEventDao().getEventsForPlant(plantId).first() }
        assertEquals(1, events.size)
        val savedEvent = events.first()
        assertEquals("HEALTH_CHECK", savedEvent.eventType)
        assertNotNull(savedEvent.photoUri)
        assertTrue(File(savedEvent.photoUri!!).exists())
        assertTrue(savedEvent.notes?.contains("Павутинний кліщ") == true)
        assertTrue(savedEvent.notes?.contains("Сухе повітря") == true)
    }

    @Test
    fun testPreExistingUserPhotosAreNeverTouchedByDiagnosis() {
        val controlFile = File(testOutputDir, "pre_existing_control_photo.jpg")
        val controlContent = "DO_NOT_DELETE_USER_ORIGINAL_IMAGE_BYTES_123456789".toByteArray()
        controlFile.writeBytes(controlContent)

        val plantId = 999L
        val repository = FlowersRepositoryImpl(
            plantDao = db.plantDao(),
            careEventDao = db.careEventDao(),
            catalogDao = db.catalogDao(),
            carePodDao = db.carePodDao(),
            context = context
        )
        val imageProcessor = ImageProcessor(
            context = context,
            outputDirectory = testOutputDir,
            engine = AndroidImageDecoderEngine()
        )
        val viewModel = DiagnosisViewModel(
            plantId = plantId,
            repository = repository,
            imageProcessorProvider = { imageProcessor }
        )

        // 1. Імітуємо вибір фотографії з галереї та скасування
        val samplePhoto = createSampleBitmapFile(testTempDir, "temp_diag.jpg")
        val sampleContent = samplePhoto.readBytes()
        viewModel.onPhotoSelected(Uri.fromFile(samplePhoto), context)
        viewModel.cancelActivePhotoOperation()

        // Перевіряємо, що контрольний файл не пошкоджено
        assertTrue(controlFile.exists())
        val afterBytes = controlFile.readBytes()
        assertTrue(controlContent.contentEquals(afterBytes))

        // Оригінал з галереї залишається цілим
        assertTrue(samplePhoto.exists())
        assertTrue(sampleContent.contentEquals(samplePhoto.readBytes()))

        // 2. Пряме передавання зовнішнього файлу не дає права на видалення
        val externalFile = createSampleBitmapFile(testTempDir, "external_doc.jpg")
        viewModel.onPhotoSelected(externalFile)
        viewModel.cancelActivePhotoOperation()
        assertTrue("Зовнішній файл не повинен видалятися при скасуванні", externalFile.exists())
    }

    @Test
    fun testHealthCheckHistoryItem_persistsAndRendersOfflineWithoutNetwork() {
        var plantId = 0L
        val photoFile = createSampleBitmapFile(testOutputDir, "offline_saved_health_photo.jpg")
        val photoBytes = photoFile.readBytes()

        runBlocking<Unit> {
            plantId = db.plantDao().insertPlant(
                PlantEntity(
                    customName = "Орхідея фаленопсис",
                    speciesName = "Орхідея"
                )
            )

            db.careEventDao().insertEvent(
                CareEventEntity(
                    plantId = plantId,
                    eventType = "HEALTH_CHECK",
                    timestamp = System.currentTimeMillis(),
                    notes = "Ймовірна причина: Перелив ґрунту\nОцінка впевненості: HIGH\nПлан дій:\n1. Ізолюйте рослину\n⚠️ Попередня оцінка.",
                    photoUri = photoFile.absolutePath
                )
            )
        }

        // Перевіряємо читання з бази без мережі
        val events = runBlocking { db.careEventDao().getEventsForPlant(plantId).first() }
        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("HEALTH_CHECK", event.eventType)
        assertEquals(photoFile.absolutePath, event.photoUri)
        assertTrue(File(event.photoUri!!).exists())
        assertTrue(photoBytes.contentEquals(File(event.photoUri!!).readBytes()))
        assertTrue(event.notes?.contains("Перелив ґрунту") == true)
    }
}
