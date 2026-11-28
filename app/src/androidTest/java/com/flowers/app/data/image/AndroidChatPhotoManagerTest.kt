package com.flowers.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.repository.FlowersRepositoryImpl
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Інструментальні тести [ChatPhotoManager] та інтеграції з реальною базою Room v5 ([FlowersDatabase])
 * і нативним графічним рушієм Android ([AndroidImageDecoderEngine]) на емуляторі (TASK-056 / PR №44).
 */
@RunWith(AndroidJUnit4::class)
class AndroidChatPhotoManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var isolatedFilesDir: File
    private lateinit var sourceGalleryDir: File
    private lateinit var database: FlowersDatabase
    private lateinit var photoManager: ChatPhotoManager
    private lateinit var repository: FlowersRepositoryImpl
    private val clockMs = AtomicLong(1_700_000_000_000L)

    @Before
    fun setUp() {
        val baseTestDir = File(context.cacheDir, "chat_photo_instr_${System.currentTimeMillis()}_${System.nanoTime()}").apply { mkdirs() }
        isolatedFilesDir = File(baseTestDir, "files").apply { mkdirs() }
        sourceGalleryDir = File(baseTestDir, "gallery").apply { mkdirs() }

        database = Room.inMemoryDatabaseBuilder(context, FlowersDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        photoManager = ChatPhotoManager(
            filesDir = isolatedFilesDir,
            context = context,
            engine = AndroidImageDecoderEngine(),
            clock = { clockMs.get() }
        )

        repository = FlowersRepositoryImpl(
            plantDao = database.plantDao(),
            careEventDao = database.careEventDao(),
            catalogDao = database.catalogDao(),
            carePodDao = database.carePodDao(),
            context = context,
            chatMessageDao = database.chatMessageDao(),
            geminiPlantService = GeminiPlantService(
                catalogDao = database.catalogDao(),
                carePodDao = database.carePodDao(),
                networkChecker = object : NetworkChecker {
                    override fun isOnline(): Boolean = true
                },
                generateChatText = { _, _ -> "Реальна відповідь" }
            ),
            chatAttachmentDao = database.chatAttachmentDao(),
            chatPhotoManager = photoManager,
            transactionRunner = { block -> database.withTransaction { block() } }
        )
    }

    @After
    fun tearDown() {
        database.close()
        isolatedFilesDir.parentFile?.deleteRecursively()
    }

    private fun createRealAndroidJpeg(
        name: String,
        width: Int = 2400,
        height: Int = 1800,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
        hasGps: Boolean = false
    ): File {
        val file = File(sourceGalleryDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(46, 125, 50))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.flush()
        }
        bitmap.recycle()

        val exif = ExifInterface(file.absolutePath)
        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        }
        if (hasGps) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "50/1,27/1,1234/100")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "30/1,31/1,5678/100")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            exif.setAttribute(ExifInterface.TAG_MAKE, "Samsung")
            exif.setAttribute(ExifInterface.TAG_MODEL, "SM-S928B")
        }
        exif.saveAttributes()
        return file
    }

    @Test
    fun realRoomAndAndroidBitmap_savesPhotoStripsGpsAndCascadesOnClearAndDelete() = runBlocking {
        val plant1Id = repository.insertPlant(
            PlantEntity(customName = "Монстера", roomLocation = "Вітальня", checkIntervalDays = 7)
        )
        val plant2Id = repository.insertPlant(
            PlantEntity(customName = "Фікус", roomLocation = "Кабінет", checkIntervalDays = 5)
        )

        val src1 = createRealAndroidJpeg(
            name = "real_cam_1.jpg",
            width = 2400,
            height = 1800,
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
            hasGps = true
        )
        val src2 = createRealAndroidJpeg(
            name = "real_cam_2.jpg",
            width = 2000,
            height = 1500,
            hasGps = true
        )

        val res1 = repository.saveUserMessageWithPhoto(plant1Id, "Листок пожовтів", src1)
        val res2 = repository.saveUserMessageWithPhoto(plant2Id, "Новий пагін", src2)

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)

        val savedAtt1 = res1.getOrThrow().primaryPhoto!!
        val savedAtt2 = res2.getOrThrow().primaryPhoto!!

        // Перевірка реального декодування та повороту на 90° (2400x1800 -> 1152x1536)
        assertEquals(1152, savedAtt1.width)
        assertEquals(1536, savedAtt1.height)

        val decoded1 = BitmapFactory.decodeFile(savedAtt1.filePath)
        assertNotNull(decoded1)
        assertEquals(1152, decoded1.width)
        assertEquals(1536, decoded1.height)
        decoded1.recycle()

        // Перевірка повного видалення EXIF/GPS
        val outExif1 = ExifInterface(savedAtt1.filePath)
        assertNull(outExif1.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(outExif1.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(outExif1.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(outExif1.getAttribute(ExifInterface.TAG_MODEL))

        // Перевірка реактивного спостереження Room v5
        val messagesWithAtt1 = repository.getChatMessagesWithAttachments(plant1Id).first()
        assertEquals(1, messagesWithAtt1.size)
        assertEquals(savedAtt1.filePath, messagesWithAtt1[0].primaryPhoto?.filePath)

        // Очищення чату рослини 1 видаляє її фото і записи Room, не чіпаючи рослину 2
        repository.clearChatHistory(plant1Id)
        assertFalse(File(savedAtt1.filePath).exists())
        assertEquals(0, repository.getChatMessagesWithAttachments(plant1Id).first().size)
        assertTrue(File(savedAtt2.filePath).exists())
        assertEquals(1, repository.getChatMessagesWithAttachments(plant2Id).first().size)

        // Видалення рослини 2 видаляє її папку фотографій та каскадно очищує Room
        val plant2 = repository.getPlantById(plant2Id)!!
        repository.deletePlant(plant2)
        assertFalse(File(savedAtt2.filePath).exists())
        assertFalse(File(savedAtt2.filePath).parentFile!!.exists())
        assertEquals(0, database.chatAttachmentDao().count())
    }

    @Test
    fun realAndroid_orphanCleanupAndBackupRestoreMissingFile() = runBlocking {
        val plantId = repository.insertPlant(
            PlantEntity(customName = "Спатифілум", roomLocation = "Кухня", checkIntervalDays = 4)
        )
        val plantDir = photoManager.getValidatedPlantDirectory(plantId, createIfMissing = true)

        val oldOrphan = File(plantDir, "chat_old_orphan.jpg").apply {
            writeBytes(createRealAndroidJpeg("tmp_orphan.jpg", 400, 300).readBytes())
        }
        val youngOrphan = File(plantDir, "chat_young_orphan.jpg").apply {
            writeBytes(oldOrphan.readBytes())
        }

        // Зсуваємо годинник на 25 годин вперед лише відносно oldOrphan
        val baseTime = clockMs.get()
        oldOrphan.setLastModified(baseTime - 25L * 3600L * 1000L)
        youngOrphan.setLastModified(baseTime - 1L * 3600L * 1000L)

        val report = repository.cleanupOrphanChatPhotos()
        assertEquals(1, report.deletedFilesCount)
        assertEquals(1, report.skippedYoungFilesCount)
        assertFalse("Old orphan must be removed", oldOrphan.exists())
        assertTrue("Young orphan must be preserved", youngOrphan.exists())

        // Перевірка стану Missing для відсутнього після бекапу файла
        val missingPath = File(plantDir, "chat_missing_from_backup.jpg").absolutePath
        val availability = repository.checkChatPhotoAvailability(missingPath, expectedPlantId = plantId)
        assertTrue(availability is ChatPhotoAvailability.Missing)
    }
}
