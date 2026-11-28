package com.flowers.app.data.image

import android.content.Context
import android.content.ContextWrapper
import androidx.exifinterface.media.ExifInterface
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.ChatAttachmentDao
import com.flowers.app.data.local.dao.ChatMessageDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.ChatAttachmentEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.ChatMessageWithAttachments
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.repository.FlowersRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Proxy
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Комплексний набір модульних JVM-тестів для [ChatPhotoManager] та інтеграції з [FlowersRepositoryImpl]
 * (TASK-056 / PR №44: покриття всіх 18 обов'язкових сценаріїв).
 */
class ChatPhotoManagerTest {

    private lateinit var testRootDir: File
    private lateinit var filesDir: File
    private lateinit var externalGalleryDir: File
    private lateinit var plantCardsDir: File
    private lateinit var currentClockMs: AtomicLong
    private lateinit var engine: ImageDecoderEngine
    private lateinit var photoManager: ChatPhotoManager

    private lateinit var plantDao: FakePlantDao
    private lateinit var chatMessageDao: FakeChatMessageDao
    private lateinit var chatAttachmentDao: FakeChatAttachmentDao
    private lateinit var repository: FlowersRepositoryImpl

    private class FakePlantDao : PlantDao() {
        val plants = ConcurrentHashMap<Long, PlantEntity>()

        override fun getAllActivePlants(): Flow<List<PlantEntity>> = emptyFlow()
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = emptyFlow()
        override suspend fun getPlantById(plantId: Long): PlantEntity? = plants[plantId]
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = emptyFlow()
        override suspend fun insertPlant(plant: PlantEntity): Long {
            val id = if (plant.id == 0L) (plants.keys.maxOrNull() ?: 0L) + 1L else plant.id
            plants[id] = plant.copy(id = id)
            return id
        }
        override suspend fun updatePlant(plant: PlantEntity) {
            plants[plant.id] = plant
        }
        override suspend fun deletePlant(plant: PlantEntity) {
            plants.remove(plant.id)
        }
        override suspend fun deletePlantById(plantId: Long) {
            plants.remove(plantId)
        }
        override suspend fun insertCareEvent(event: CareEventEntity): Long = 1L
        override suspend fun updateWateringDates(plantId: Long, lastWatered: Long, nextCheck: Long): Int = 1
        override suspend fun updateNextCheckDate(plantId: Long, nextCheck: Long): Int = 1
    }

    private class FakeChatMessageDao(
        private val attachmentDaoProvider: () -> FakeChatAttachmentDao
    ) : ChatMessageDao() {
        private val idGen = AtomicLong(1L)
        val messages = ConcurrentHashMap<Long, ChatMessageEntity>()
        private val stateFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
        var failOnInsert: Boolean = false

        fun refreshFlow() {
            stateFlow.value = messages.values.sortedWith(compareBy({ it.timestamp }, { it.id }))
        }

        override fun observeMessages(plantId: Long): Flow<List<ChatMessageEntity>> =
            stateFlow.map { list -> list.filter { it.plantId == plantId } }

        override fun observeMessagesWithAttachments(plantId: Long): Flow<List<ChatMessageWithAttachments>> =
            stateFlow.map { list ->
                val attDao = attachmentDaoProvider()
                list.filter { it.plantId == plantId }.map { msg ->
                    ChatMessageWithAttachments(
                        message = msg,
                        attachments = attDao.attachments.values
                            .filter { it.messageId == msg.id }
                            .sortedBy { it.id }
                    )
                }
            }

        override suspend fun getMessagesWithAttachments(plantId: Long): List<ChatMessageWithAttachments> {
            val attDao = attachmentDaoProvider()
            return messages.values
                .filter { it.plantId == plantId }
                .sortedWith(compareBy({ it.timestamp }, { it.id }))
                .map { msg ->
                    ChatMessageWithAttachments(
                        message = msg,
                        attachments = attDao.attachments.values
                            .filter { it.messageId == msg.id }
                            .sortedBy { it.id }
                    )
                }
        }

        override suspend fun getMessages(plantId: Long): List<ChatMessageEntity> =
            messages.values.filter { it.plantId == plantId }.sortedWith(compareBy({ it.timestamp }, { it.id }))

        override suspend fun getLastMessages(plantId: Long, limit: Int): List<ChatMessageEntity> =
            getMessages(plantId).takeLast(limit)

        override suspend fun getLastSentMessages(plantId: Long, limit: Int): List<ChatMessageEntity> =
            getMessages(plantId).filter { it.status == MessageStatus.SENT }.takeLast(limit)

        override suspend fun getById(id: Long): ChatMessageEntity? = messages[id]

        override suspend fun insertMessage(message: ChatMessageEntity): Long {
            if (failOnInsert) {
                throw IllegalStateException("Simulated SQLite error on insertMessage")
            }
            val id = if (message.id == 0L) idGen.getAndIncrement() else message.id
            messages[id] = message.copy(id = id)
            refreshFlow()
            return id
        }

        override suspend fun updateStatus(id: Long, status: MessageStatus, errorMessage: String?): Int {
            val msg = messages[id] ?: return 0
            messages[id] = msg.copy(status = status, errorMessage = errorMessage)
            refreshFlow()
            return 1
        }

        override suspend fun beginRetry(id: Long): Int = 1

        override suspend fun beginRetryForPlant(id: Long, plantId: Long): Int {
            val msg = messages[id] ?: return 0
            if (msg.plantId == plantId && msg.status == MessageStatus.ERROR && msg.role == MessageRole.USER) {
                messages[id] = msg.copy(status = MessageStatus.SENDING, errorMessage = null)
                refreshFlow()
                return 1
            }
            return 0
        }

        override suspend fun markInterruptedSendingAsError(errorMessage: String): Int = 0

        override suspend fun markSendingAsErrorForPlant(plantId: Long, errorMessage: String): Int {
            var count = 0
            messages.forEach { (id, msg) ->
                if (msg.plantId == plantId && msg.status == MessageStatus.SENDING) {
                    messages[id] = msg.copy(status = MessageStatus.ERROR, errorMessage = errorMessage)
                    count++
                }
            }
            refreshFlow()
            return count
        }

        override suspend fun deleteAllForPlant(plantId: Long): Int {
            val toRemove = messages.values.filter { it.plantId == plantId }.map { it.id }.toSet()
            toRemove.forEach { msgId ->
                messages.remove(msgId)
                attachmentDaoProvider().deleteByMessageId(msgId)
            }
            refreshFlow()
            return toRemove.size
        }
    }

    private class FakeChatAttachmentDao(
        private val messageDaoProvider: () -> FakeChatMessageDao
    ) : ChatAttachmentDao() {
        private val idGen = AtomicLong(1L)
        val attachments = ConcurrentHashMap<Long, ChatAttachmentEntity>()
        var failOnInsertAttachment: Boolean = false
        var getAllStoredAttachmentPathsCallCount = AtomicInteger(0)
        var onBeforeSecondFetchStoredPaths: (() -> Unit)? = null

        override suspend fun insertAttachment(attachment: ChatAttachmentEntity): Long {
            if (failOnInsertAttachment) {
                throw IllegalStateException("Simulated Room transaction failure on insertAttachment")
            }
            if (!messageDaoProvider().messages.containsKey(attachment.messageId)) {
                throw IllegalStateException("FOREIGN KEY constraint failed: messageId ${attachment.messageId} not found")
            }
            val id = if (attachment.id == 0L) idGen.getAndIncrement() else attachment.id
            attachments[id] = attachment.copy(id = id)
            return id
        }

        override suspend fun insertAttachments(attachmentsList: List<ChatAttachmentEntity>): List<Long> =
            attachmentsList.map { insertAttachment(it) }

        override suspend fun getAttachmentForMessage(messageId: Long): ChatAttachmentEntity? =
            attachments.values.filter { it.messageId == messageId }.minByOrNull { it.id }

        override suspend fun getAttachmentsForMessage(messageId: Long): List<ChatAttachmentEntity> =
            attachments.values.filter { it.messageId == messageId }.sortedBy { it.id }

        override suspend fun getAttachmentsForMessages(messageIds: List<Long>): List<ChatAttachmentEntity> =
            attachments.values.filter { it.messageId in messageIds }.sortedWith(compareBy({ it.messageId }, { it.id }))

        override suspend fun getAttachmentsForPlant(plantId: Long): List<ChatAttachmentEntity> {
            val plantMsgIds = messageDaoProvider().messages.values.filter { it.plantId == plantId }.map { it.id }.toSet()
            return attachments.values.filter { it.messageId in plantMsgIds }.sortedBy { it.id }
        }

        override suspend fun getAllFilePathsForPlant(plantId: Long): List<String> =
            getAttachmentsForPlant(plantId).map { it.filePath }

        override suspend fun getAllStoredAttachmentPaths(): List<String> {
            val callNum = getAllStoredAttachmentPathsCallCount.incrementAndGet()
            if (callNum == 2) {
                onBeforeSecondFetchStoredPaths?.invoke()
            }
            return attachments.values.map { it.filePath }
        }

        override suspend fun deleteByMessageId(messageId: Long): Int {
            val ids = attachments.values.filter { it.messageId == messageId }.map { it.id }
            ids.forEach { attachments.remove(it) }
            return ids.size
        }

        override suspend fun count(): Int = attachments.size

        override suspend fun getById(id: Long): ChatAttachmentEntity? = attachments[id]

        override fun observeAttachmentsForMessage(messageId: Long): Flow<List<ChatAttachmentEntity>> = emptyFlow()
    }

    private inline fun <reified T : Any> stubProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            when (m.name) {
                "count" -> 1
                "getRecentEventsForPlant" -> emptyList<CareEventEntity>()
                else -> null
            }
        } as T

    @Before
    fun setUp() {
        testRootDir = File(
            System.getProperty("java.io.tmpdir"),
            "flowers_chat_photo_test_${System.currentTimeMillis()}_${System.nanoTime()}"
        ).apply { mkdirs() }
        filesDir = File(testRootDir, "files").apply { mkdirs() }
        externalGalleryDir = File(testRootDir, "gallery").apply { mkdirs() }
        plantCardsDir = File(filesDir, "plants").apply { mkdirs() }

        currentClockMs = AtomicLong(1_700_000_000_000L)
        engine = TestImageDecoderEngine()

        photoManager = ChatPhotoManager(
            filesDir = filesDir,
            engine = engine,
            clock = { currentClockMs.get() }
        )

        plantDao = FakePlantDao().apply {
            plants[1L] = PlantEntity(id = 1L, customName = "Монстера", roomLocation = "Вітальня")
            plants[2L] = PlantEntity(id = 2L, customName = "Фікус", roomLocation = "Спальня")
        }
        lateinit var attDaoRef: FakeChatAttachmentDao
        chatMessageDao = FakeChatMessageDao { attDaoRef }
        chatAttachmentDao = FakeChatAttachmentDao { chatMessageDao }
        attDaoRef = chatAttachmentDao

        val careEventDao: CareEventDao = stubProxy()
        val catalogDao: CatalogDao = stubProxy()
        val carePodDao: CarePodDao = stubProxy()
        val dummyContext: Context = object : ContextWrapper(null) {}
        val geminiService = GeminiPlantService(
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            networkChecker = object : NetworkChecker {
                override fun isOnline(): Boolean = true
            },
            generateChatText = { _, _ -> "Відповідь асистента" }
        )

        repository = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = dummyContext,
            chatMessageDao = chatMessageDao,
            geminiPlantService = geminiService,
            chatAttachmentDao = chatAttachmentDao,
            chatPhotoManager = photoManager,
            transactionRunner = { block -> runAtomicFakeTransaction(block) }
        )
    }

    /**
     * Імітує атомарну транзакцію Room (`database.withTransaction`) для in-memory Fake DAO:
     * робить знімок таблиць `chat_messages` та `chat_attachments` перед транзакцією і у разі будь-якого
     * винятку або скасування корутини відновлює точний попередній стан без втрати існуючої історії.
     */
    private val transactionHookBeforeCommit = java.util.concurrent.atomic.AtomicReference<(suspend () -> Unit)?>(null)

    private suspend fun runAtomicFakeTransaction(block: suspend () -> Unit) {
        val messagesSnapshot = HashMap(chatMessageDao.messages)
        val attachmentsSnapshot = HashMap(chatAttachmentDao.attachments)
        try {
            block()
            transactionHookBeforeCommit.get()?.invoke()
        } catch (t: Throwable) {
            chatMessageDao.messages.clear()
            chatMessageDao.messages.putAll(messagesSnapshot)
            chatAttachmentDao.attachments.clear()
            chatAttachmentDao.attachments.putAll(attachmentsSnapshot)
            chatMessageDao.refreshFlow()
            throw t
        }
    }

    @After
    fun tearDown() {
        testRootDir.deleteRecursively()
    }

    private fun createSourceJpeg(
        name: String,
        width: Int = 2000,
        height: Int = 1500,
        orientation: Int = 1,
        hasGps: Boolean = false
    ): File {
        val file = File(externalGalleryDir, name)
        val bytes = MinimalJpeg.createBytes(width, height, orientation = orientation, hasGps = hasGps)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun calculateMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var r: Int
            while (stream.read(buf).also { r = it } > 0) {
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // 1. Збереження валідного JPEG + захист оригіналу в галереї
    @Test
    fun `scenario 1 - saving valid JPEG stores in plant directory and creates Room attachment`() = runBlocking {
        val sourceFile = createSourceJpeg("leaf_spot.jpg", width = 2400, height = 1800)
        val originalMd5 = calculateMd5(sourceFile)

        val saveResult = repository.saveUserMessageWithPhoto(
            plantId = 1L,
            content = "Що це за пляма на листі?",
            sourceFile = sourceFile
        )
        assertTrue("Save must succeed", saveResult.isSuccess)

        val msgWithAtt = saveResult.getOrThrow()
        assertEquals(1L, msgWithAtt.message.plantId)
        assertEquals("Що це за пляма на листі?", msgWithAtt.message.content)
        assertEquals(MessageStatus.SENDING, msgWithAtt.message.status)
        assertEquals(1, msgWithAtt.attachments.size)

        val attachment = msgWithAtt.primaryPhoto!!
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals(1536, attachment.width)
        assertEquals(1152, attachment.height)
        assertTrue(attachment.fileSizeBytes > 0L)

        val savedFile = File(attachment.filePath)
        assertTrue("Saved file must exist on disk", savedFile.exists())
        assertEquals("plant_1", savedFile.parentFile?.name)
        assertEquals("chat_photos", savedFile.parentFile?.parentFile?.name)
        assertTrue(savedFile.name.startsWith("chat_"))
        assertTrue(savedFile.name.endsWith(".jpg"))

        // Оригінал у галереї не змінений
        assertEquals("Gallery original must remain untouched", originalMd5, calculateMd5(sourceFile))
    }

    // 2. Перезапуск застосунку — файл зберігся і не видаляється службою Orphan Cleanup
    @Test
    fun `scenario 2 - app restart preserves saved file and orphan cleanup keeps Room-referenced photo`() = runBlocking {
        val sourceFile = createSourceJpeg("persisted_photo.jpg", width = 1600, height = 1200)
        val saved = repository.saveUserMessageWithPhoto(1L, "Перевір листок", sourceFile).getOrThrow()
        val storedPath = saved.primaryPhoto!!.filePath

        // Симулюємо минання 48 годин і перезапуск застосунку (новий екземпляр ChatPhotoManager)
        currentClockMs.addAndGet(48L * 3600L * 1000L)
        val restartedManager = ChatPhotoManager(
            filesDir = filesDir,
            engine = TestImageDecoderEngine(),
            clock = { currentClockMs.get() }
        )

        val availability = restartedManager.checkPhotoAvailability(storedPath, expectedPlantId = 1L)
        assertTrue("Photo must be available after restart", availability is ChatPhotoAvailability.Available)

        val fetchedFile = restartedManager.getSavedPhotoFile(storedPath, expectedPlantId = 1L)
        assertTrue(fetchedFile.isSuccess)
        assertEquals(File(storedPath).canonicalPath, fetchedFile.getOrThrow().canonicalPath)

        // Запускаємо Orphan Cleanup після перезапуску — прив'язаний до Room файл НЕ видаляється
        val cleanupReport = restartedManager.cleanupOrphanFiles(
            fetchStoredPaths = { chatAttachmentDao.getAllStoredAttachmentPaths().toSet() }
        )
        assertEquals(0, cleanupReport.deletedFilesCount)
        assertTrue("Referenced file must still exist on disk", File(storedPath).exists())
    }

    // 3. Відсутність EXIF/GPS та нормалізація орієнтації
    @Test
    fun `scenario 3 - EXIF and GPS metadata are completely stripped and orientation is normalized`() = runBlocking {
        val geotaggedSource = createSourceJpeg(
            name = "gps_rotated.jpg",
            width = 1920,
            height = 1080,
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
            hasGps = true
        )

        val srcExif = ExifInterface(geotaggedSource.absolutePath)
        assertNotNull("Source must have GPS latitude", srcExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(
            ExifInterface.ORIENTATION_ROTATE_90,
            srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        )

        val savedPhoto = photoManager.saveChatPhoto(1L, geotaggedSource).getOrThrow()

        // Після повороту на 90° розміри 1920x1080 стають вертикальними 864x1536
        assertEquals(864, savedPhoto.width)
        assertEquals(1536, savedPhoto.height)

        val outExif = ExifInterface(savedPhoto.file.absolutePath)
        assertNull("GPS latitude must be stripped", outExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS longitude must be stripped", outExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("Camera make must be stripped", outExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Camera model must be stripped", outExif.getAttribute(ExifInterface.TAG_MODEL))
        val outOrient = outExif.getAttribute(ExifInterface.TAG_ORIENTATION)
        assertTrue("EXIF orientation tag must be stripped", outOrient == null || outOrient == "0")
    }

    // 4. Надвелике вихідне фото (50 MP та 200 MP)
    @Test
    fun `scenario 4 - ultra high resolution 50MP and 200MP photos are subsampled and scaled to 1536px`() = runBlocking {
        val photo50Mp = createSourceJpeg("ultra_50mp.jpg", width = 8192, height = 6144)
        val saved50 = photoManager.saveChatPhoto(1L, photo50Mp).getOrThrow()
        assertEquals(1536, saved50.width)
        assertEquals(1152, saved50.height)
        assertTrue(saved50.file.exists())

        val photo200Mp = createSourceJpeg("ultra_200mp.jpg", width = 16384, height = 12288)
        val saved200 = photoManager.saveChatPhoto(1L, photo200Mp).getOrThrow()
        assertEquals(1536, saved200.width)
        assertEquals(1152, saved200.height)
        assertTrue(saved200.file.exists())
    }

    // 5. Пошкоджений вхідний файл
    @Test
    fun `scenario 5 - corrupt input file fails cleanly without creating files or Room records`() = runBlocking {
        val corruptFile = File(externalGalleryDir, "corrupted_stream.jpg").apply {
            writeText("NOT_A_JPEG_HEADER_CORRUPTED_BYTES_12345")
        }

        val result = repository.saveUserMessageWithPhoto(1L, "Пошкоджене фото", corruptFile)
        assertTrue("Must fail for corrupt image", result.isFailure)
        assertTrue(result.exceptionOrNull() is ChatPhotoException.InvalidImage)

        val plant1Dir = File(filesDir, "chat_photos/plant_1")
        val remainingFiles = plant1Dir.listFiles() ?: emptyArray()
        assertEquals("No files or .tmp files should exist", 0, remainingFiles.size)
        assertEquals("No message should be saved in Room", 0, chatMessageDao.messages.size)
        assertEquals("No attachment should be saved in Room", 0, chatAttachmentDao.count())
    }

    // 6. Недостатньо місця на диску (ENOSPC), помилка I/O та недоступність атомарного переміщення
    @Test
    fun `scenario 6 - disk full IO error and unsupported atomic move fail cleanly without partial files`() = runBlocking {
        val sourceFile = createSourceJpeg("valid_for_io_fail.jpg", width = 1200, height = 900)

        // 6A: Симуляція ENOSPC (No space left on device) під час запису
        val diskFullEngine = object : ImageDecoderEngine by engine {
            override fun compressToFile(
                bitmapHolder: DecodedBitmapHolder,
                format: ImageFormat,
                quality: Int,
                destinationFile: File
            ): Boolean {
                throw IOException("ENOSPC: No space left on device")
            }
        }
        val diskFullManager = ChatPhotoManager(filesDir = filesDir, engine = diskFullEngine)
        val resIo = diskFullManager.saveChatPhoto(1L, sourceFile)
        assertTrue(resIo.isFailure)
        assertTrue(resIo.exceptionOrNull() is ChatPhotoException.IoFailure)
        assertEquals(0, (File(filesDir, "chat_photos/plant_1").listFiles() ?: emptyArray()).size)

        // 6B: Симуляція недоступності атомарного переміщення (AtomicMoveNotSupportedException)
        val noAtomicMoveManager = ChatPhotoManager(
            filesDir = filesDir,
            engine = engine,
            atomicMover = { src, dst ->
                throw AtomicMoveNotSupportedException(src.toString(), dst.toString(), "Cross-filesystem or unsupported atomic move")
            }
        )
        val resAtomic = noAtomicMoveManager.saveChatPhoto(1L, sourceFile)
        assertTrue("Must fail when atomic move is unsupported", resAtomic.isFailure)
        assertTrue(
            "Error must be AtomicMoveFailed, not silent copy fallback",
            resAtomic.exceptionOrNull() is ChatPhotoException.AtomicMoveFailed
        )
        assertEquals(
            "Temporary file must be cleaned up when atomic move fails",
            0,
            (File(filesDir, "chat_photos/plant_1").listFiles() ?: emptyArray()).size
        )
    }

    // 7. Помилка транзакції Room після запису JPEG (із перевіркою збереження існуючої історії та фото!)
    @Test
    fun `scenario 7 - Room transaction failure preserves existing messages and JPEGs while rolling back failed operation`() = runBlocking {
        // 1. Створюємо попередню історію чату для рослини 1: два повідомлення з фото та відповідь асистента
        val existingSource1 = createSourceJpeg("existing_1.jpg", width = 1200, height = 900)
        val existingSource2 = createSourceJpeg("existing_2.jpg", width = 1000, height = 800)

        val existingMsg1 = repository.saveUserMessageWithPhoto(1L, "Перше старе фото-питання", existingSource1).getOrThrow()
        val assistantReplyId = repository.saveAssistantResponse(
            userMessageId = existingMsg1.message.id,
            modelMessage = ChatMessageEntity(
                plantId = 1L,
                role = MessageRole.MODEL,
                content = "Перша відповідь асистента",
                status = MessageStatus.SENT
            )
        )
        assertTrue(assistantReplyId > 0L)

        val existingMsg2 = repository.saveUserMessageWithPhoto(1L, "Друге старе фото-питання", existingSource2).getOrThrow()

        val existingFile1 = File(existingMsg1.primaryPhoto!!.filePath)
        val existingFile2 = File(existingMsg2.primaryPhoto!!.filePath)
        val md5Existing1 = calculateMd5(existingFile1)
        val md5Existing2 = calculateMd5(existingFile2)

        assertEquals(3, chatMessageDao.getMessages(1L).size)
        assertEquals(2, chatAttachmentDao.getAttachmentsForPlant(1L).size)

        // 2. Симулюємо помилку вставки нового вкладення в транзакції Room
        val newSource = createSourceJpeg("tx_rollback_new.jpg", width = 1400, height = 1000)
        chatAttachmentDao.failOnInsertAttachment = true

        val result = repository.saveUserMessageWithPhoto(1L, "Нове повідомлення з помилкою вкладення", newSource)
        assertTrue("Must fail when Room transaction throws", result.isFailure)
        assertTrue(result.exceptionOrNull() is ChatPhotoException.DatabaseFailure)

        // 3. Перевіряємо, що вся попередня історія (3 повідомлення і 2 вкладення) збережена на 100%,
        // а невдала операція не залишила часткового повідомлення!
        val remainingMessages = chatMessageDao.getMessages(1L)
        assertEquals("Existing 3 messages must be preserved without partial records", 3, remainingMessages.size)
        assertEquals(
            listOf("Перше старе фото-питання", "Перша відповідь асистента", "Друге старе фото-питання"),
            remainingMessages.map { it.content }
        )

        val remainingAttachments = chatAttachmentDao.getAttachmentsForPlant(1L)
        assertEquals("Existing 2 attachments must be preserved", 2, remainingAttachments.size)

        // 4. Старі фізичні JPEG файли цілі та непошкоджені, а новий JPEG невдалої операції видалений
        assertTrue("Existing JPEG 1 must still exist", existingFile1.exists())
        assertTrue("Existing JPEG 2 must still exist", existingFile2.exists())
        assertEquals(md5Existing1, calculateMd5(existingFile1))
        assertEquals(md5Existing2, calculateMd5(existingFile2))

        val plant1FilesOnDisk = File(filesDir, "chat_photos/plant_1").listFiles()?.map { it.canonicalPath }?.toSet() ?: emptySet()
        assertEquals(
            "Only the 2 pre-existing JPEGs must remain on disk",
            setOf(existingFile1.canonicalPath, existingFile2.canonicalPath),
            plant1FilesOnDisk
        )
    }

    // 7B. Відсутність транзакційного механізму та скасування корутини посеред транзакції
    @Test
    fun `scenario 7B - missing transactionRunner fails safely and coroutine cancellation rolls back without losing existing history`() = runBlocking {
        // Підготовка існуючого повідомлення з фото
        val existingSource = createSourceJpeg("existing_before_cancel.jpg", width = 1000, height = 800)
        val existingSaved = repository.saveUserMessageWithPhoto(1L, "Існуюче повідомлення", existingSource).getOrThrow()
        val existingFile = File(existingSaved.primaryPhoto!!.filePath)

        // 1. Якщо transactionRunner == null (транзакційний механізм недоступний):
        // повертається контрольована помилка без модифікації існуючих повідомлень чи файлів
        val repoWithoutTx = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = stubProxy(),
            catalogDao = stubProxy(),
            carePodDao = stubProxy(),
            context = object : ContextWrapper(null) {},
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(stubProxy(), stubProxy(), object : NetworkChecker {
                override fun isOnline(): Boolean = true
            }),
            chatAttachmentDao = chatAttachmentDao,
            chatPhotoManager = photoManager,
            transactionRunner = null
        )
        val noTxResult = repoWithoutTx.saveUserMessageWithPhoto(1L, "Спроба без транзакції", existingSource)
        assertTrue(noTxResult.isFailure)
        assertTrue(noTxResult.exceptionOrNull() is ChatPhotoException.DatabaseFailure)
        assertEquals(1, chatMessageDao.getMessages(1L).size)
        assertTrue(existingFile.exists())

        // 2. Детерміноване скасування корутини посеред транзакції (після вставки повідомлення та вкладення, до коміту)
        val enteredTxBeforeCommit = CompletableDeferred<Unit>()
        val awaitCancellation = CompletableDeferred<Unit>()
        transactionHookBeforeCommit.set {
            enteredTxBeforeCommit.complete(Unit)
            awaitCancellation.await()
        }

        val cancelSource = createSourceJpeg("to_be_cancelled.jpg", width = 1200, height = 900)
        var cancellationPropagated = false
        val job = launch(Dispatchers.IO) {
            try {
                repository.saveUserMessageWithPhoto(1L, "Повідомлення під час скасування", cancelSource)
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancellationPropagated = true
                throw e
            }
        }

        enteredTxBeforeCommit.await()
        job.cancel()
        job.join()
        transactionHookBeforeCommit.set(null)

        assertTrue("CancellationException must propagate properly", cancellationPropagated)
        assertEquals("Pre-existing message must be preserved and cancelled message rolled back", 1, chatMessageDao.getMessages(1L).size)
        assertEquals("Pre-existing attachment must be preserved", 1, chatAttachmentDao.getAttachmentsForPlant(1L).size)
        assertTrue("Pre-existing JPEG must remain intact", existingFile.exists())

        val filesInPlant1 = File(filesDir, "chat_photos/plant_1").listFiles()?.map { it.canonicalPath }?.toSet() ?: emptySet()
        assertEquals("Cancelled operation's new JPEG must be cleaned up", setOf(existingFile.canonicalPath), filesInPlant1)
    }

    // 8. Збій посеред запису тимчасового файла
    @Test
    fun `scenario 8 - crash or failure mid-write of temporary file cleans up temp file and preserves existing photos`() = runBlocking {
        // Спочатку зберігаємо валідне фото
        val firstSource = createSourceJpeg("first_valid.jpg", width = 1000, height = 800)
        val existingSaved = photoManager.saveChatPhoto(1L, firstSource).getOrThrow()

        // Симулюємо збій посеред запису тимчасового файла (записано обірваний/пошкоджений потік байтів)
        val midWriteCrashEngine = object : ImageDecoderEngine by engine {
            override fun compressToFile(
                bitmapHolder: DecodedBitmapHolder,
                format: ImageFormat,
                quality: Int,
                destinationFile: File
            ): Boolean {
                destinationFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x01)) // Обірваний JPEG без SOF0/EOI
                return true // Навіть якщо потік повернув true до перевірки цілісності
            }
        }
        val crashingManager = ChatPhotoManager(filesDir = filesDir, engine = midWriteCrashEngine)
        val failedResult = crashingManager.saveChatPhoto(1L, firstSource)
        assertTrue("Truncated temp file write must be rejected", failedResult.isFailure)

        val plant1Files = File(filesDir, "chat_photos/plant_1").listFiles()?.map { it.name } ?: emptyList()
        assertEquals("Only the first valid photo must remain; no .tmp or corrupt file", listOf(existingSaved.file.name), plant1Files)
    }

    // 9. Очищення сироти після 24 годин + повторна перевірка Room перед видаленням
    @Test
    fun `scenario 9 - orphan file older than 24 hours is deleted while Room recheck protects newly committed file`() = runBlocking {
        val plant1Dir = photoManager.getValidatedPlantDirectory(1L, createIfMissing = true)

        // Сирота №1: створена 25 годин тому, відсутня в Room
        val oldOrphan = File(plant1Dir, "chat_orphan_old.jpg").apply {
            writeBytes(MinimalJpeg.createBytes(400, 300))
            setLastModified(currentClockMs.get() - (25L * 3600L * 1000L))
        }

        // Валідний файл, прив'язаний до Room (також створений 30 годин тому)
        val sourceValid = createSourceJpeg("valid_old.jpg", width = 800, height = 600)
        val savedValid = repository.saveUserMessageWithPhoto(1L, "Старе валідне фото", sourceValid).getOrThrow()
        val validFile = File(savedValid.primaryPhoto!!.filePath).apply {
            setLastModified(currentClockMs.get() - (30L * 3600L * 1000L))
        }

        val report = repository.cleanupOrphanChatPhotos()
        assertEquals("Exactly 1 old orphan file must be deleted", 1, report.deletedFilesCount)
        assertFalse("Old orphan file must be deleted from disk", oldOrphan.exists())
        assertTrue("Room-referenced file must be preserved", validFile.exists())

        // Перевірка повторного опитування Room безпосередньо перед видаленням:
        // Файл відсутній у першій вибірці, але з'являється в Room перед другою перевіркою
        val lateCommittedFile = File(plant1Dir, "chat_late_committed.jpg").apply {
            writeBytes(MinimalJpeg.createBytes(400, 300))
            setLastModified(currentClockMs.get() - (26L * 3600L * 1000L))
        }
        var callCounter = 0
        val reportLate = photoManager.cleanupOrphanFiles(
            fetchStoredPaths = {
                callCounter++
                if (callCounter == 1) {
                    setOf(validFile.canonicalPath)
                } else {
                    // На момент повторної перевірки перед видаленням запис уже з'явився в Room
                    setOf(validFile.canonicalPath, lateCommittedFile.canonicalPath)
                }
            }
        )
        assertEquals("File committed before recheck must not be deleted", 0, reportLate.deletedFilesCount)
        assertTrue("Late committed file must remain on disk", lateCommittedFile.exists())
    }

    // 10. Збереження файлів, молодших за 24 години
    @Test
    fun `scenario 10 - unreferenced files younger than 24 hours are preserved by orphan cleanup`() = runBlocking {
        val plant1Dir = photoManager.getValidatedPlantDirectory(1L, createIfMissing = true)

        // Файл без запису в Room, створений 2 години тому (< 24 годин)
        val recentUnconfirmedFile = File(plant1Dir, "chat_recent_unconfirmed.jpg").apply {
            writeBytes(MinimalJpeg.createBytes(600, 400))
            setLastModified(currentClockMs.get() - (2L * 3600L * 1000L))
        }

        // Файл без запису в Room, створений 23 години 59 хвилин тому (< 24 годин)
        val almost24hFile = File(plant1Dir, "chat_almost_24h.jpg").apply {
            writeBytes(MinimalJpeg.createBytes(600, 400))
            setLastModified(currentClockMs.get() - (24L * 3600L * 1000L - 60_000L))
        }

        val report = repository.cleanupOrphanChatPhotos()
        assertEquals(0, report.deletedFilesCount)
        assertEquals(2, report.skippedYoungFilesCount)
        assertTrue("2h old file must be preserved", recentUnconfirmedFile.exists())
        assertTrue("23h59m old file must be preserved", almost24hFile.exists())
    }

    // 11. Path Traversal, чужі рослини, кореневий filesDir та символічні посилання
    @Test
    fun `scenario 11 - path traversal cross-plant access root filesDir and symlinks are strictly blocked`() = runBlocking {
        val plantCardPhoto = File(plantCardsDir, "monstera_avatar.jpg").apply {
            writeText("PRECIOUS_PLANT_CARD_PHOTO")
        }
        val source1 = createSourceJpeg("p1.jpg", 600, 400)
        val source2 = createSourceJpeg("p2.jpg", 600, 400)
        val saved1 = photoManager.saveChatPhoto(1L, source1).getOrThrow()
        val saved2 = photoManager.saveChatPhoto(2L, source2).getOrThrow()

        // 11A: Path traversal через .. до фото картки рослини
        val traversalPath = "${filesDir.absolutePath}/chat_photos/plant_1/../../plants/monstera_avatar.jpg"
        val delTraversal = photoManager.deletePhotoForPlant(1L, traversalPath)
        assertTrue(delTraversal.isFailure)
        assertTrue(delTraversal.exceptionOrNull() is ChatPhotoException.SecurityViolation)
        assertTrue("Plant card photo must not be deleted", plantCardPhoto.exists())

        // 11B: Спроба відкрити або видалити фото рослини 2 від імені рослини 1
        val crossPlantAvailability = photoManager.checkPhotoAvailability(saved2.canonicalPath, expectedPlantId = 1L)
        assertTrue(crossPlantAvailability is ChatPhotoAvailability.InvalidPath)

        val crossPlantDelete = photoManager.deletePhotoForPlant(1L, saved2.canonicalPath)
        assertTrue("Must block deleting plant 2 photo via plant 1", crossPlantDelete.isFailure)
        assertTrue(crossPlantDelete.exceptionOrNull() is ChatPhotoException.SecurityViolation)
        assertTrue("Plant 2 photo must remain intact", saved2.file.exists())

        // 11C: Спроба передати кореневий filesDir або chat_photos
        val rootDelete1 = photoManager.deletePhotoForPlant(1L, filesDir.absolutePath)
        assertTrue(rootDelete1.isFailure)
        assertTrue(filesDir.exists())

        val chatRootDir = File(filesDir, "chat_photos")
        val rootDelete2 = photoManager.deletePhotoForPlant(1L, chatRootDir.absolutePath)
        assertTrue(rootDelete2.isFailure)
        assertTrue(chatRootDir.exists())

        // 11D: Нульовий байт у шляху
        val nullByteCheck = photoManager.checkPhotoAvailability("${saved1.canonicalPath}\u0000.jpg", expectedPlantId = 1L)
        assertTrue(nullByteCheck is ChatPhotoAvailability.InvalidPath)

        // 11E: Символічне посилання (якщо ОС дозволяє створення symlink у тестовому середовищі)
        val symlinkFile = File(File(filesDir, "chat_photos/plant_1"), "symlink_attack.jpg")
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(symlinkFile.toPath(), plantCardPhoto.toPath())
            true
        }.getOrDefault(false)

        if (symlinkCreated) {
            val symAvail = photoManager.checkPhotoAvailability(symlinkFile.absolutePath, expectedPlantId = 1L)
            assertTrue("Symlink must be rejected as InvalidPath", symAvail is ChatPhotoAvailability.InvalidPath)
            val symDel = photoManager.deletePhotoForPlant(1L, symlinkFile.absolutePath)
            assertTrue("Symlink deletion must be rejected", symDel.isFailure)
            assertTrue("Target of symlink must remain untouched", plantCardPhoto.exists())
            Files.deleteIfExists(symlinkFile.toPath())
        }
    }

    // 12. Видалення чату без втрати фото інших рослин та карток рослин
    @Test
    fun `scenario 12 - clearing chat history deletes plant 1 photos and records without touching plant 2 or plant cards`() = runBlocking {
        val plantCardPhoto = File(plantCardsDir, "card_ficus.jpg").apply {
            writeText("PLANT_CARD_IMAGE_BYTES")
        }
        val src1A = createSourceJpeg("p1_a.jpg", 800, 600)
        val src1B = createSourceJpeg("p1_b.jpg", 800, 600)
        val src2A = createSourceJpeg("p2_a.jpg", 800, 600)

        val msg1A = repository.saveUserMessageWithPhoto(1L, "Питання 1А", src1A).getOrThrow()
        val msg1B = repository.saveUserMessageWithPhoto(1L, "Питання 1Б", src1B).getOrThrow()
        val msg2A = repository.saveUserMessageWithPhoto(2L, "Питання 2А", src2A).getOrThrow()

        val file1A = File(msg1A.primaryPhoto!!.filePath)
        val file1B = File(msg1B.primaryPhoto!!.filePath)
        val file2A = File(msg2A.primaryPhoto!!.filePath)

        assertTrue(file1A.exists())
        assertTrue(file1B.exists())
        assertTrue(file2A.exists())

        // Очищуємо історію чату лише для рослини 1
        val deletedMessages = repository.clearChatHistory(1L)
        assertEquals(2, deletedMessages)

        // Перевіряємо, що файли та записи рослини 1 повністю видалені
        assertFalse("Plant 1 photo A must be deleted", file1A.exists())
        assertFalse("Plant 1 photo B must be deleted", file1B.exists())
        assertEquals(0, chatMessageDao.getMessages(1L).size)
        assertEquals(0, chatAttachmentDao.getAttachmentsForPlant(1L).size)

        // Перевіряємо, що рослина 2 та фото карток рослин залишилися неушкодженими
        assertTrue("Plant 2 photo must be untouched", file2A.exists())
        assertEquals(1, chatMessageDao.getMessages(2L).size)
        assertEquals(1, chatAttachmentDao.getAttachmentsForPlant(2L).size)
        assertTrue("Plant card photo in filesDir/plants must be untouched", plantCardPhoto.exists())
    }

    // 13. Видалення рослини
    @Test
    fun `scenario 13 - deleting plant removes its chat photo folder and prevents recreating messages for deleted plant`() = runBlocking {
        val src1 = createSourceJpeg("del_plant1.jpg", 800, 600)
        val src2 = createSourceJpeg("keep_plant2.jpg", 800, 600)

        val msg1 = repository.saveUserMessageWithPhoto(1L, "Фото рослини 1", src1).getOrThrow()
        val msg2 = repository.saveUserMessageWithPhoto(2L, "Фото рослини 2", src2).getOrThrow()

        val file1 = File(msg1.primaryPhoto!!.filePath)
        val plant1Dir = file1.parentFile!!
        val file2 = File(msg2.primaryPhoto!!.filePath)

        val plant1 = plantDao.getPlantById(1L)!!
        repository.deletePlant(plant1)

        assertFalse("Plant 1 photo must be deleted", file1.exists())
        assertFalse("Plant 1 chat photo directory must be deleted", plant1Dir.exists())
        assertNull("Plant 1 must be deleted from PlantDao", repository.getPlantById(1L))
        assertEquals(0, chatMessageDao.getMessages(1L).size)
        assertEquals(0, chatAttachmentDao.getAttachmentsForPlant(1L).size)

        // Рослина 2 не зачеплена
        assertTrue("Plant 2 photo must remain intact", file2.exists())
        assertEquals(1, chatMessageDao.getMessages(2L).size)

        // Спроба повторно створити текстове або фото-повідомлення для видаленої рослини 1 блокується
        val blockedTextInsert = repository.insertChatMessage(
            ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "Привид", status = MessageStatus.SENDING)
        )
        assertEquals(-1L, blockedTextInsert)

        val blockedPhotoInsert = repository.saveUserMessageWithPhoto(1L, "Привид з фото", src1)
        assertTrue(blockedPhotoInsert.isFailure)
        assertTrue(blockedPhotoInsert.exceptionOrNull() is ChatPhotoException.PlantNotFound)
        assertFalse("Deleted plant directory must not remain on disk", plant1Dir.exists())
    }

    // 14. Невдале видалення JPEG і повторна спроба
    @Test
    fun `scenario 14 - failed JPEG deletion queues file for retry and succeeds on next cleanup pass`() = runBlocking {
        val failNextDelete = AtomicBoolean(false)
        val flakyPhotoManager = ChatPhotoManager(
            filesDir = filesDir,
            engine = engine,
            clock = { currentClockMs.get() },
            fileDeleter = { file ->
                if (failNextDelete.get() && file.isFile) {
                    false // Симулюємо тимчасову помилку видалення файла (наприклад, зайнятий дескриптор)
                } else {
                    file.delete()
                }
            }
        )
        val flakyRepo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = stubProxy(),
            catalogDao = stubProxy(),
            carePodDao = stubProxy(),
            context = object : ContextWrapper(null) {},
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(stubProxy(), stubProxy(), object : NetworkChecker {
                override fun isOnline(): Boolean = true
            }),
            chatAttachmentDao = chatAttachmentDao,
            chatPhotoManager = flakyPhotoManager,
            transactionRunner = { block -> runAtomicFakeTransaction(block) }
        )

        val src = createSourceJpeg("flaky_delete.jpg", 800, 600)
        val saved = flakyRepo.saveUserMessageWithPhoto(1L, "Тест повторного видалення", src).getOrThrow()
        val photoFile = File(saved.primaryPhoto!!.filePath)
        assertTrue(photoFile.exists())

        // Блокуємо фізичне видалення під час clearChatHistory
        failNextDelete.set(true)
        val deletedInDb = flakyRepo.clearChatHistory(1L)
        assertEquals("Room records must still be deleted even if File.delete() returns false", 1, deletedInDb)
        assertEquals(0, chatAttachmentDao.count())
        assertTrue("Physical file still exists because deletion failed", photoFile.exists())
        assertTrue("File must be registered in pending deletions", flakyPhotoManager.getPendingDeletionsCount() > 0)

        // Знімаємо блокування I/O та виконуємо повторне очищення
        failNextDelete.set(false)
        val retriedCount = flakyRepo.retryPendingChatPhotoDeletions()
        assertEquals("Retry must delete the pending file", 1, retriedCount)
        assertFalse("Physical file must now be deleted", photoFile.exists())
        assertEquals(0, flakyPhotoManager.getPendingDeletionsCount())
    }

    // 15. Паралельні файлові операції двох рослин
    @Test
    fun `scenario 15 - concurrent parallel photo operations across two plants are isolated without collisions`() = runBlocking {
        val src1 = createSourceJpeg("concurrent_p1.jpg", 1200, 900)
        val src2 = createSourceJpeg("concurrent_p2.jpg", 1200, 900)

        val perPlantCount = 8
        val jobsPlant1 = (1..perPlantCount).map { idx ->
            async(Dispatchers.IO) {
                repository.saveUserMessageWithPhoto(1L, "Рослина 1 запит #$idx", src1)
            }
        }
        val jobsPlant2 = (1..perPlantCount).map { idx ->
            async(Dispatchers.IO) {
                repository.saveUserMessageWithPhoto(2L, "Рослина 2 запит #$idx", src2)
            }
        }

        val res1 = jobsPlant1.map { it.await() }
        val res2 = jobsPlant2.map { it.await() }

        assertTrue("All Plant 1 saves must succeed", res1.all { it.isSuccess })
        assertTrue("All Plant 2 saves must succeed", res2.all { it.isSuccess })

        val paths1 = res1.map { it.getOrThrow().primaryPhoto!!.filePath }.toSet()
        val paths2 = res2.map { it.getOrThrow().primaryPhoto!!.filePath }.toSet()

        assertEquals(perPlantCount, paths1.size)
        assertEquals(perPlantCount, paths2.size)
        assertTrue("All Plant 1 files must be in plant_1 folder", paths1.all { File(it).parentFile?.name == "plant_1" && File(it).exists() })
        assertTrue("All Plant 2 files must be in plant_2 folder", paths2.all { File(it).parentFile?.name == "plant_2" && File(it).exists() })

        // Очищуємо рослину 1 паралельно з читанням рослини 2
        val clear1Job = async(Dispatchers.IO) { repository.clearChatHistory(1L) }
        val read2Job = async(Dispatchers.IO) {
            paths2.map { repository.checkChatPhotoAvailability(it, expectedPlantId = 2L) }
        }

        assertEquals(perPlantCount, clear1Job.await())
        val availabilities2 = read2Job.await()
        assertTrue(availabilities2.all { it is ChatPhotoAvailability.Available })
        assertTrue("Plant 1 files must all be deleted", paths1.none { File(it).exists() })
        assertTrue("Plant 2 files must all still exist", paths2.all { File(it).exists() })
    }

    // 16. Очищення під час активного запиту
    @Test
    fun `scenario 16 - clearing chat during active request does not deadlock protects in-use photo until completion and drops stale response`() = runBlocking {
        withTimeout(5_000L) {
            val src = createSourceJpeg("active_in_use.jpg", 1000, 800)
            val savedMsg = repository.saveUserMessageWithPhoto(1L, "Активний запит з фото", src).getOrThrow()
            val userMsgId = savedMsg.message.id
            val photoPath = savedMsg.primaryPhoto!!.filePath
            val photoFile = File(photoPath)

            val photoReadEntered = CompletableDeferred<Unit>()
            val allowNetworkFinish = CompletableDeferred<Unit>()
            var fileExistedDuringActiveRead = false

            // Симулюємо активний запит з withChatSending та withChatPhotoInUse
            val activeRequestJob = launch(Dispatchers.IO) {
                repository.withChatSending(1L) {
                    repository.withChatPhotoInUse(photoPath, expectedPlantId = 1L) { activeFile ->
                        photoReadEntered.complete(Unit)
                        allowNetworkFinish.await()
                        // Перевіряємо, що файл НЕ зник посеред активного читання!
                        fileExistedDuringActiveRead = activeFile.exists() && activeFile.length() > 0L
                    }
                    // Спроба зберегти запізнілу відповідь ШІ після очищення чату
                    repository.saveAssistantResponse(
                        userMessageId = userMsgId,
                        modelMessage = ChatMessageEntity(
                            plantId = 1L,
                            role = MessageRole.MODEL,
                            content = "Запізніла відповідь",
                            status = MessageStatus.SENT
                        )
                    )
                }
            }

            // Чекаємо входу в активне використання фото
            photoReadEntered.await()
            assertTrue(repository.isPlantChatSending(1L))
            assertTrue(photoManager.isPhotoInUse(photoFile.canonicalPath))

            // Викликаємо clearChatHistory під час активного запиту — не повинно зависнути (м'ютекс вільний!)
            val deletedCount = repository.clearChatHistory(1L)
            assertEquals(1, deletedCount)
            assertEquals("Room messages must be empty immediately after clearChatHistory", 0, chatMessageDao.getMessages(1L).size)

            // Поки withChatPhotoInUse ще триває, фізичний файл захищений від неконтрольованого зникнення
            assertTrue("Photo file must still exist while active reader holds withChatPhotoInUse", photoFile.exists())

            // Завершуємо активне читання/запит
            allowNetworkFinish.complete(Unit)
            activeRequestJob.join()

            assertTrue("File must have remained readable throughout active block", fileExistedDuringActiveRead)
            assertFalse("Photo file must be automatically deleted immediately after active use finishes", photoFile.exists())
            assertEquals("Stale assistant response must be dropped", 0, chatMessageDao.getMessages(1L).size)
            assertFalse(repository.isPlantChatSending(1L))
        }
    }

    // 17. Відсутній файл після відновлення з резервної копії
    @Test
    fun `scenario 17 - missing photo file after Google Auto Backup restore returns Missing state without crashing`() = runBlocking {
        // Симулюємо відновлену з бекапу БД Room: записи повідомлення та вкладення є, але папки chat_photos немає
        val restoredMsgId = chatMessageDao.insertMessage(
            ChatMessageEntity(
                plantId = 1L,
                role = MessageRole.USER,
                content = "Повідомлення з бекапу",
                status = MessageStatus.SENT,
                timestamp = 1_699_000_000_000L
            )
        )
        val unbackedUpFilePath = File(filesDir, "chat_photos/plant_1/chat_1699000000000_restored.jpg").absolutePath
        chatAttachmentDao.insertAttachment(
            ChatAttachmentEntity(
                messageId = restoredMsgId,
                filePath = unbackedUpFilePath,
                mimeType = "image/jpeg",
                fileSizeBytes = 245_000L,
                width = 1536,
                height = 1152
            )
        )

        // 1. Читання історії з вкладеннями через Flow працює стабільно без винятків
        val history = repository.getChatMessagesWithAttachments(1L).first()
        assertEquals(1, history.size)
        assertNotNull(history[0].primaryPhoto)
        assertEquals(unbackedUpFilePath, history[0].primaryPhoto?.filePath)

        // 2. Перевірка доступності повертає контрольований статус Missing (для відображення плейсхолдера в UI)
        val availability = repository.checkChatPhotoAvailability(unbackedUpFilePath, expectedPlantId = 1L)
        assertTrue("Must return Missing for unbacked-up file", availability is ChatPhotoAvailability.Missing)
        val missing = availability as ChatPhotoAvailability.Missing
        assertTrue(missing.reason.contains("резервної копії"))

        // 3. Спроба отримати File повертає контрольований Result.failure(PhotoMissing)
        val fileResult = repository.getSavedChatPhotoFile(unbackedUpFilePath, expectedPlantId = 1L)
        assertTrue(fileResult.isFailure)
        assertTrue(fileResult.exceptionOrNull() is ChatPhotoException.PhotoMissing)
    }

    // 18. Відсутність регресій текстового чату та захисту від перезапису
    @Test
    fun `scenario 18 - text-only chat lifecycle and existing photo overwrite protection work without regressions`() = runBlocking {
        // 18A: Текстовий чат працює без змін
        val textMsgId = repository.insertChatMessage(
            ChatMessageEntity(
                plantId = 1L,
                role = MessageRole.USER,
                content = "Як часто поливати монстеру взимку?",
                status = MessageStatus.SENDING
            )
        )
        assertTrue(textMsgId > 0L)
        assertNull("Text message must have no attachment", repository.getChatAttachmentForMessage(textMsgId))

        val modelMsgId = repository.saveAssistantResponse(
            userMessageId = textMsgId,
            modelMessage = ChatMessageEntity(
                plantId = 1L,
                role = MessageRole.MODEL,
                content = "Поливайте після просихання верхнього шару ґрунту.",
                status = MessageStatus.SENT
            )
        )
        assertTrue(modelMsgId > 0L)
        assertEquals(MessageStatus.SENT, repository.getChatMessageById(textMsgId)?.status)

        // 18B: Заборона перезапису існуючого фото з тим самим ім'ям
        val src = createSourceJpeg("no_overwrite.jpg", 800, 600)
        val firstSave = photoManager.saveChatPhoto(1L, src, customFileName = "unique_fixed_name.jpg")
        assertTrue(firstSave.isSuccess)
        val originalBytes = firstSave.getOrThrow().file.readBytes()

        val duplicateSave = photoManager.saveChatPhoto(1L, src, customFileName = "unique_fixed_name.jpg")
        assertTrue("Overwrite must be forbidden", duplicateSave.isFailure)
        assertTrue(duplicateSave.exceptionOrNull() is ChatPhotoException.FileAlreadyExists)
        assertTrue("Existing file bytes must remain unchanged", originalBytes.contentEquals(firstSave.getOrThrow().file.readBytes()))
    }

    // 19. Детермінований тест конкурентності: clearChatHistory паралельно зі створенням нового повідомлення з фото тієї самої рослини
    @Test
    fun `scenario 19 - deterministic concurrency between clearChatHistory and parallel new photo message on same plant preserves new message and JPEG`() = runBlocking {
        withTimeout(5_000L) {
            // 1. Створюємо стару історію для рослини 1 (2 повідомлення з фото) та історію для рослини 2
            val oldSrc1 = createSourceJpeg("old_p1_1.jpg", 900, 700)
            val oldSrc2 = createSourceJpeg("old_p1_2.jpg", 900, 700)
            val p2Src = createSourceJpeg("p2_untouched.jpg", 900, 700)

            val oldMsg1 = repository.saveUserMessageWithPhoto(1L, "Старе питання 1", oldSrc1).getOrThrow()
            val oldMsg2 = repository.saveUserMessageWithPhoto(1L, "Старе питання 2", oldSrc2).getOrThrow()
            val plant2Msg = repository.saveUserMessageWithPhoto(2L, "Питання рослини 2", p2Src).getOrThrow()

            val oldFile1 = File(oldMsg1.primaryPhoto!!.filePath)
            val oldFile2 = File(oldMsg2.primaryPhoto!!.filePath)
            val plant2File = File(plant2Msg.primaryPhoto!!.filePath)

            assertTrue(oldFile1.exists())
            assertTrue(oldFile2.exists())
            assertTrue(plant2File.exists())

            // 2. Налаштовуємо детермінований бар'єр у clearChatHistory:
            // Одразу після очищення старих записів у Room і звільнення plantMutex (але ДО видалення фізичних файлів)
            // паралельно створюється нове повідомлення з фото для тієї самої рослини 1!
            val clearDbCompletedBarrier = CompletableDeferred<Unit>()
            val resumeClearFileDeletionBarrier = CompletableDeferred<Unit>()

            repository.onAfterClearDbBeforeFileDelete = { clearedPlantId ->
                if (clearedPlantId == 1L) {
                    clearDbCompletedBarrier.complete(Unit)
                    resumeClearFileDeletionBarrier.await()
                }
            }

            val clearJob = async(Dispatchers.IO) {
                repository.clearChatHistory(1L)
            }

            // Очікуємо точного моменту завершення транзакції очищення Room у clearChatHistory
            clearDbCompletedBarrier.await()

            // У цей момент паралельно створюємо нове фото-повідомлення для тієї самої рослини 1
            val newSrc = createSourceJpeg("new_concurrent_p1.jpg", 1200, 900)
            val newMsgResult = repository.saveUserMessageWithPhoto(
                plantId = 1L,
                content = "Нове питання з фото після очищення",
                sourceFile = newSrc
            )
            assertTrue("Concurrent new photo message save must succeed", newMsgResult.isSuccess)
            val newMsg = newMsgResult.getOrThrow()
            val newPhotoFile = File(newMsg.primaryPhoto!!.filePath)
            assertTrue("New JPEG file must exist right after save", newPhotoFile.exists())

            // Відпускаємо бар'єр, щоб clearChatHistory завершив фізичне видалення старих файлів
            resumeClearFileDeletionBarrier.complete(Unit)
            val clearedRows = clearJob.await()
            repository.onAfterClearDbBeforeFileDelete = null

            assertEquals("Exactly 2 old messages must have been cleared", 2, clearedRows)

            // 3. Перевіряємо: старі JPEG видалені, але НОВИЙ Room-запис і його JPEG у тій самій папці plant_1 НЕДОТОРКАНІ!
            assertFalse("Old photo 1 must be deleted", oldFile1.exists())
            assertFalse("Old photo 2 must be deleted", oldFile2.exists())

            assertTrue("New photo created concurrently for plant 1 MUST NOT be deleted by clearChatHistory", newPhotoFile.exists())
            val availability = repository.checkChatPhotoAvailability(newPhotoFile.absolutePath, expectedPlantId = 1L)
            assertTrue("New photo must be Available", availability is ChatPhotoAvailability.Available)

            val currentMessagesPlant1 = repository.getChatMessagesWithAttachments(1L).first()
            assertEquals("New message must remain in Room", 1, currentMessagesPlant1.size)
            assertEquals("Нове питання з фото після очищення", currentMessagesPlant1[0].message.content)
            assertEquals(newPhotoFile.canonicalPath, currentMessagesPlant1[0].primaryPhoto?.filePath)

            // Рослина 2 повністю ізольована і не зачеплена
            assertTrue("Plant 2 photo must remain untouched", plant2File.exists())
            assertEquals(1, repository.getChatMessagesWithAttachments(2L).first().size)
        }
    }

    // 20. Регресійний тест Retry для повідомлення з фотографією після помилки мережі
    @Test
    fun `scenario 20 - retry after network error reuses existing saved photo and transitions status to SENT`() = runBlocking {
        val src = createSourceJpeg("retry_photo.jpg", 1600, 1200)
        val saved = repository.saveUserMessageWithPhoto(1L, "Чому сохнуть кінчики?", src).getOrThrow()
        val msgId = saved.message.id
        val photoPath = saved.primaryPhoto!!.filePath
        val photoFile = File(photoPath)
        val initialMd5 = calculateMd5(photoFile)

        // 1. Початковий запит завершився помилкою мережі -> статус переходить у ERROR
        assertTrue(repository.updateChatMessageStatus(msgId, MessageStatus.ERROR, "Відсутнє інтернет-з'єднання"))
        assertEquals(MessageStatus.ERROR, repository.getChatMessageById(msgId)?.status)
        assertTrue("Photo file must remain on disk in ERROR state", photoFile.exists())

        // 2. Користувач натискає Retry: beginChatMessageRetry переводить повідомлення в SENDING без створення дублікатів
        assertTrue(repository.beginChatMessageRetry(msgId, 1L))
        assertEquals(MessageStatus.SENDING, repository.getChatMessageById(msgId)?.status)

        val attachment = repository.getChatAttachmentForMessage(msgId)
        assertNotNull("Attachment record must remain linked to messageId", attachment)
        assertEquals(photoPath, attachment?.filePath)
        assertTrue(repository.checkChatPhotoAvailability(photoPath, expectedPlantId = 1L) is ChatPhotoAvailability.Available)
        assertEquals("Photo file must not be re-encoded or modified on retry", initialMd5, calculateMd5(photoFile))

        // 3. Відповідь від асистента успішно зберігається
        val modelId = repository.saveAssistantResponse(
            userMessageId = msgId,
            modelMessage = ChatMessageEntity(
                plantId = 1L,
                role = MessageRole.MODEL,
                content = "Підвищте вологість повітря.",
                status = MessageStatus.SENT
            )
        )
        assertTrue(modelId > 0L)

        val finalHistory = repository.getChatMessagesWithAttachments(1L).first()
        assertEquals(2, finalHistory.size)
        assertEquals(MessageStatus.SENT, finalHistory[0].message.status)
        assertEquals(photoPath, finalHistory[0].primaryPhoto?.filePath)
        assertEquals(MessageStatus.SENT, finalHistory[1].message.status)
    }
}
