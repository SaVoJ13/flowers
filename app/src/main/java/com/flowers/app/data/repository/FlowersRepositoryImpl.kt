package com.flowers.app.data.repository

import android.content.Context
import com.flowers.app.data.image.ChatPhotoAvailability
import com.flowers.app.data.image.ChatPhotoException
import com.flowers.app.data.image.ChatPhotoManager
import com.flowers.app.data.image.ImageSource
import com.flowers.app.data.image.OrphanCleanupReport
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.ChatAttachmentDao
import com.flowers.app.data.local.dao.ChatMessageDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.ChatAttachmentEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.ChatMessageWithAttachments
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.data.preferences.UserPreferences
import com.flowers.app.data.remote.AndroidNetworkChecker
import com.flowers.app.data.remote.DiagnosePlantHealthResult
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.domain.ReminderCalculator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FlowersRepositoryImpl(
    private val plantDao: PlantDao,
    private val careEventDao: CareEventDao,
    private val catalogDao: CatalogDao,
    private val carePodDao: CarePodDao,
    private val context: Context,
    private val chatMessageDao: ChatMessageDao? = null,
    private val geminiPlantService: GeminiPlantService = GeminiPlantService(catalogDao, carePodDao, AndroidNetworkChecker(context)),
    private val chatAttachmentDao: ChatAttachmentDao? = null,
    private val chatPhotoManager: ChatPhotoManager = ChatPhotoManager(
        filesDir = runCatching { context.applicationContext?.filesDir ?: context.filesDir }.getOrNull()
            ?: File(System.getProperty("java.io.tmpdir"), "flowers_fallback_files")
    ),
    private val transactionRunner: (suspend (suspend () -> Unit) -> Unit)? = null
) : FlowersRepository {

    override val userPreferences: UserPreferences by lazy { UserPreferences(context) }
    private var cachedSymptomTree: SymptomTreeRoot? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            ensurePopulated()
        }
    }

    private suspend fun ensurePopulated() {
        try {
            if (catalogDao.count() == 0) {
                val catalogJson = context.assets.open("content/PLANT_CATALOG.json")
                    .bufferedReader().use { it.readText() }
                val catalogType = object : TypeToken<List<CatalogPlantEntity>>() {}.type
                val catalogList: List<CatalogPlantEntity> = Gson().fromJson(catalogJson, catalogType)
                catalogDao.insertAll(catalogList)
            }
            if (carePodDao.count() == 0) {
                val podsJson = context.assets.open("content/CARE_PODS.json")
                    .bufferedReader().use { it.readText() }
                val podsType = object : TypeToken<List<CarePodEntity>>() {}.type
                val podsList: List<CarePodEntity> = Gson().fromJson(podsJson, podsType)
                carePodDao.insertAll(podsList)
            }
        } catch (e: Exception) {
            android.util.Log.e("FlowersRepository", "Failed to populate catalog/pods", e)
        }
    }

    override fun getAllActivePlants(): Flow<List<PlantEntity>> = plantDao.getAllActivePlants()

    override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> =
        plantDao.getPlantsDueForCheck(endOfDayTimestamp)

    override suspend fun getPlantById(plantId: Long): PlantEntity? = plantDao.getPlantById(plantId)

    override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = plantDao.getPlantFlowById(plantId)

    override suspend fun insertPlant(plant: PlantEntity): Long {
        val id = plantDao.insertPlant(plant)
        if (id > 0L) {
            deletedPlantIds.remove(id)
        }
        return id
    }

    override suspend fun updatePlant(plant: PlantEntity) = plantDao.updatePlant(plant)

    override suspend fun deletePlant(plant: PlantEntity) = withContext(Dispatchers.IO) {
        val plantId = plant.id
        val mutex = getPlantMutex(plantId)
        val filePaths = mutex.withLock {
            deletedPlantIds.add(plantId)
            val paths = runCatching { chatAttachmentDao?.getAllFilePathsForPlant(plantId) }.getOrNull().orEmpty()
            chatMessageDao?.deleteAllForPlant(plantId)
            plantDao.deletePlant(plant)
            paths
        }
        for (path in filePaths) {
            chatPhotoManager.deletePhotoForPlant(plantId, path)
        }
        chatPhotoManager.deleteAllPhotosForPlant(plantId)
        Unit
    }

    override fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>> =
        careEventDao.getEventsForPlant(plantId)

    override suspend fun getLastWateringEvent(plantId: Long): CareEventEntity? =
        careEventDao.getLastWateringEvent(plantId)

    override suspend fun logCareEvent(event: CareEventEntity): Long =
        careEventDao.insertEvent(event)

    override fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>> =
        catalogDao.getAllCatalogPlants()

    override fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>> =
        catalogDao.getAllCatalogPlants().map { plants ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) plants
            else plants.filter { plant ->
                plant.nameUk.contains(trimmed, ignoreCase = true) ||
                plant.nameLatin.contains(trimmed, ignoreCase = true) ||
                plant.popularName.contains(trimmed, ignoreCase = true)
            }
        }

    override suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity? =
        catalogDao.getCatalogPlantById(speciesId)

    override fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>> =
        carePodDao.getPodsForSpecies(speciesId)

    override fun getGeneralPods(): Flow<List<CarePodEntity>> =
        carePodDao.getGeneralPods()

    override suspend fun getSymptomTree(): SymptomTreeRoot = withContext(Dispatchers.IO) {
        cachedSymptomTree ?: run {
            val json = context.assets.open("content/SYMPTOM_DECISION_TREE.json")
                .bufferedReader().use { it.readText() }
            val tree = Gson().fromJson(json, SymptomTreeRoot::class.java)
            cachedSymptomTree = tree
            tree
        }
    }

    override suspend fun performWatering(plant: PlantEntity, notes: String?) {
        plantDao.applyWatering(plant.id, System.currentTimeMillis(), notes ?: "Ґрунт перевірено, полив виконано")
    }

    override suspend fun snoozePlant(plant: PlantEntity, days: Int) {
        plantDao.applySnooze(plant.id, days, System.currentTimeMillis(), "Відкладено на $days дн. (ґрунт ще вологий)")
    }

    override suspend fun skipPlant(plant: PlantEntity) {
        plantDao.applySkip(plant.id, System.currentTimeMillis(), "Пропущено в цьому циклі")
    }
    override suspend fun generatePlantWithAi(plantQuery: String): GeneratePlantResult {
        return geminiPlantService.generatePlantCareCard(
            plantQuery = plantQuery
        )
    }

    override suspend fun identifyPlantFromPhoto(imageFile: File): IdentifyPlantResult {
        return geminiPlantService.identifyPlantFromPhoto(imageFile)
    }

    override suspend fun diagnosePlantHealth(imageFile: File, plant: PlantEntity): DiagnosePlantHealthResult {
        val lastWatering = careEventDao.getLastWateringEvent(plant.id)
        return geminiPlantService.diagnosePlantHealth(imageFile, plant, lastWatering?.timestamp)
    }

    private val activeSendingCounters = java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger>()
    private val plantMutexes = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()
    private val deletedPlantIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    private fun getPlantMutex(plantId: Long): Mutex {
        return plantMutexes.computeIfAbsent(plantId) { Mutex() }
    }

    private suspend fun executeInTransaction(block: suspend () -> Unit) {
        val runner = transactionRunner
        if (runner != null) {
            runner(block)
        } else {
            block()
        }
    }

    override fun isPlantChatSending(plantId: Long): Boolean {
        return (activeSendingCounters[plantId]?.get() ?: 0) > 0
    }

    override suspend fun <T> withChatSending(plantId: Long, block: suspend () -> T): T {
        val mutex = getPlantMutex(plantId)
        mutex.withLock {
            activeSendingCounters.compute(plantId) { _, existing ->
                (existing ?: java.util.concurrent.atomic.AtomicInteger(0)).apply { incrementAndGet() }
            }
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    activeSendingCounters.computeIfPresent(plantId) { _, counter ->
                        if (counter.decrementAndGet() <= 0) null else counter
                    }
                }
            }
        }
    }

    override fun getChatMessages(plantId: Long): Flow<List<ChatMessageEntity>> {
        return chatMessageDao?.observeMessages(plantId) ?: flowOf(emptyList())
    }

    override fun getChatMessagesWithAttachments(plantId: Long): Flow<List<ChatMessageWithAttachments>> {
        return chatMessageDao?.observeMessagesWithAttachments(plantId) ?: flowOf(emptyList())
    }

    override suspend fun getChatMessageById(messageId: Long): ChatMessageEntity? {
        return chatMessageDao?.getById(messageId)
    }

    override suspend fun getChatAttachmentForMessage(messageId: Long): ChatAttachmentEntity? {
        return chatAttachmentDao?.getAttachmentForMessage(messageId)
    }

    override suspend fun insertChatMessage(message: ChatMessageEntity): Long {
        val mutex = getPlantMutex(message.plantId)
        return mutex.withLock {
            if (deletedPlantIds.contains(message.plantId)) {
                return@withLock -1L
            }
            chatMessageDao?.insertMessage(message) ?: -1L
        }
    }

    /**
     * Двофазне атомарне збереження повідомлення користувача з фотографією (TASK-056 / PR №44):
     * 1. Фаза 1: обробка та атомарне збереження JPEG через [ChatPhotoManager] у `filesDir/chat_photos/plant_<plantId>/`.
     * 2. Фаза 2: створення записів [ChatMessageEntity] та [ChatAttachmentEntity] у транзакції Room.
     * 3. Якщо транзакція Room завершується помилкою або рослину було видалено — щойно створений JPEG негайно
     *    видаляється (або стає на облік для Orphan Cleanup).
     */
    override suspend fun saveUserMessageWithPhoto(
        plantId: Long,
        content: String,
        photoSource: ImageSource,
        timestamp: Long
    ): Result<ChatMessageWithAttachments> = withContext(Dispatchers.IO) {
        if (plantId <= 0L) {
            return@withContext Result.failure(
                ChatPhotoException.SecurityViolation("Некоректний ідентифікатор рослини: $plantId")
            )
        }

        val mutex = getPlantMutex(plantId)
        val plantExistsInitially = mutex.withLock {
            !deletedPlantIds.contains(plantId) && plantDao.getPlantById(plantId) != null
        }
        if (!plantExistsInitially) {
            return@withContext Result.failure(
                ChatPhotoException.PlantNotFound("Рослину з ID $plantId не знайдено або вона була видалена")
            )
        }

        val msgDao = chatMessageDao
        val attDao = chatAttachmentDao
        if (msgDao == null || attDao == null) {
            return@withContext Result.failure(
                ChatPhotoException.DatabaseFailure("DAO чату або вкладень не ініціалізовано")
            )
        }

        // Фаза 1: обробка зображення та атомарне збереження постійного JPEG
        val savedPhoto = chatPhotoManager.saveChatPhoto(plantId, photoSource).getOrElse { err ->
            return@withContext Result.failure(err)
        }

        chatPhotoManager.markInFlightSaveStart(savedPhoto.canonicalPath)
        try {
            // Фаза 2: короткочасна критична секція під м'ютексом рослини та транзакція Room
            mutex.withLock {
                if (deletedPlantIds.contains(plantId) || plantDao.getPlantById(plantId) == null) {
                    chatPhotoManager.markInFlightSaveEnd(savedPhoto.canonicalPath)
                    chatPhotoManager.deletePhotoForPlant(plantId, savedPhoto.canonicalPath)
                    return@withContext Result.failure(
                        ChatPhotoException.PlantNotFound("Рослину з ID $plantId було видалено під час збереження фото")
                    )
                }

                // Не створюємо запис про вкладення, якщо постійний файл не збережений або недоступний
                if (!chatPhotoManager.isPhotoAvailable(savedPhoto.canonicalPath, expectedPlantId = plantId)) {
                    chatPhotoManager.markInFlightSaveEnd(savedPhoto.canonicalPath)
                    return@withContext Result.failure(
                        ChatPhotoException.PhotoMissing("Постійний файл фотографії відсутній на диску перед записом у Room")
                    )
                }

                var createdMessage: ChatMessageEntity? = null
                var createdAttachment: ChatAttachmentEntity? = null

                try {
                    executeInTransaction {
                        val userMsg = ChatMessageEntity(
                            plantId = plantId,
                            role = MessageRole.USER,
                            content = content,
                            status = MessageStatus.SENDING,
                            timestamp = timestamp
                        )
                        val msgId = msgDao.insertMessage(userMsg)
                        if (msgId <= 0L) {
                            throw ChatPhotoException.DatabaseFailure("Не вдалося створити повідомлення в Room")
                        }
                        createdMessage = userMsg.copy(id = msgId)

                        val attachment = ChatAttachmentEntity(
                            messageId = msgId,
                            filePath = savedPhoto.canonicalPath,
                            mimeType = savedPhoto.mimeType,
                            fileSizeBytes = savedPhoto.fileSizeBytes,
                            width = savedPhoto.width,
                            height = savedPhoto.height,
                            createdAt = savedPhoto.createdAt
                        )
                        val attId = attDao.insertAttachment(attachment)
                        if (attId <= 0L) {
                            throw ChatPhotoException.DatabaseFailure("Не вдалося створити запис вкладення в Room")
                        }
                        createdAttachment = attachment.copy(id = attId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (dbError: Throwable) {
                    // Якщо використовується тестовий Fake DAO без реальної транзакції SQLite, очищуємо створене повідомлення
                    if (transactionRunner == null && createdMessage != null && createdAttachment == null) {
                        runCatching { msgDao.deleteAllForPlant(plantId) }
                    }
                    throw dbError
                }

                Result.success(
                    ChatMessageWithAttachments(
                        message = createdMessage!!,
                        attachments = listOf(createdAttachment!!)
                    )
                )
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                chatPhotoManager.markInFlightSaveEnd(savedPhoto.canonicalPath)
                chatPhotoManager.deletePhotoForPlant(plantId, savedPhoto.canonicalPath)
            }
            throw e
        } catch (t: Throwable) {
            // При помилці Room негайно очищуємо щойно створений файл (або реєструємо для повторного очищення сиріт)
            withContext(NonCancellable) {
                chatPhotoManager.markInFlightSaveEnd(savedPhoto.canonicalPath)
                chatPhotoManager.deletePhotoForPlant(plantId, savedPhoto.canonicalPath)
            }
            val mapped = if (t is ChatPhotoException) {
                t
            } else {
                ChatPhotoException.DatabaseFailure(
                    "Помилка транзакції Room після запису JPEG: ${t.message}",
                    t
                )
            }
            Result.failure(mapped)
        } finally {
            chatPhotoManager.markInFlightSaveEnd(savedPhoto.canonicalPath)
        }
    }

    override suspend fun saveUserMessageWithPhoto(
        plantId: Long,
        content: String,
        sourceFile: File,
        timestamp: Long
    ): Result<ChatMessageWithAttachments> {
        return saveUserMessageWithPhoto(plantId, content, ImageSource.FromFile(sourceFile), timestamp)
    }

    override fun checkChatPhotoAvailability(
        filePath: String,
        expectedPlantId: Long?
    ): ChatPhotoAvailability {
        return chatPhotoManager.checkPhotoAvailability(filePath, expectedPlantId)
    }

    override fun getSavedChatPhotoFile(
        filePath: String,
        expectedPlantId: Long?
    ): Result<File> {
        return chatPhotoManager.getSavedPhotoFile(filePath, expectedPlantId)
    }

    override suspend fun <T> withChatPhotoInUse(
        filePath: String,
        expectedPlantId: Long?,
        block: suspend (File) -> T
    ): T {
        return chatPhotoManager.withPhotoInUse(filePath, expectedPlantId, block)
    }

    override suspend fun cleanupOrphanChatPhotos(): OrphanCleanupReport = withContext(Dispatchers.IO) {
        chatPhotoManager.cleanupOrphanFiles(
            fetchStoredPaths = {
                chatAttachmentDao?.getAllStoredAttachmentPaths()?.toSet() ?: emptySet()
            },
            isPlantActive = { plantId ->
                isPlantChatSending(plantId)
            }
        )
    }

    override suspend fun retryPendingChatPhotoDeletions(): Int = withContext(Dispatchers.IO) {
        chatPhotoManager.retryPendingDeletions(
            fetchStoredPaths = {
                chatAttachmentDao?.getAllStoredAttachmentPaths()?.toSet() ?: emptySet()
            }
        )
    }

    override suspend fun updateChatMessageStatus(
        messageId: Long,
        status: MessageStatus,
        errorMessage: String?
    ): Boolean {
        val rows = chatMessageDao?.updateStatus(messageId, status, errorMessage) ?: 0
        return rows > 0
    }

    override suspend fun saveAssistantResponse(
        userMessageId: Long,
        modelMessage: ChatMessageEntity
    ): Long {
        val mutex = getPlantMutex(modelMessage.plantId)
        return mutex.withLock {
            if (deletedPlantIds.contains(modelMessage.plantId)) {
                return@withLock -1L
            }
            chatMessageDao?.saveAssistantResponse(userMessageId, modelMessage) ?: -1L
        }
    }

    override suspend fun beginChatMessageRetry(messageId: Long, plantId: Long): Boolean {
        val rows = chatMessageDao?.beginRetryForPlant(messageId, plantId) ?: 0
        return rows > 0
    }

    override suspend fun clearChatHistory(plantId: Long): Int = withContext(Dispatchers.IO) {
        val mutex = getPlantMutex(plantId)
        val (deletedRows, filePaths) = mutex.withLock {
            val paths = runCatching { chatAttachmentDao?.getAllFilePathsForPlant(plantId) }.getOrNull().orEmpty()
            val rows = chatMessageDao?.deleteAllForPlant(plantId) ?: 0
            Pair(rows, paths)
        }
        for (path in filePaths) {
            chatPhotoManager.deletePhotoForPlant(plantId, path)
        }
        chatPhotoManager.deleteAllPhotosForPlant(plantId)
        deletedRows
    }

    override suspend fun recoverStaleSendingMessages(plantId: Long): Int {
        val mutex = getPlantMutex(plantId)
        return mutex.withLock {
            if (isPlantChatSending(plantId)) {
                return@withLock 0
            }
            chatMessageDao?.markSendingAsErrorForPlant(
                plantId,
                "Запит перервано через перезапуск застосунку"
            ) ?: 0
        }
    }

    override suspend fun sendPlantChatMessage(
        plantId: Long,
        userQuestion: String,
        language: String,
        currentMessageId: Long?
    ): com.flowers.app.data.remote.PlantChatResult = withContext(Dispatchers.IO) {
        val mutex = getPlantMutex(plantId)
        mutex.withLock {
            activeSendingCounters.compute(plantId) { _, existing ->
                (existing ?: java.util.concurrent.atomic.AtomicInteger(0)).apply { incrementAndGet() }
            }
        }
        try {
            if (userQuestion.isBlank()) {
                return@withContext com.flowers.app.data.remote.PlantChatResult.ServiceError("Запитання не може бути порожнім.")
            }

            val plant = plantDao.getPlantById(plantId)
                ?: return@withContext com.flowers.app.data.remote.PlantChatResult.ServiceError("Рослину з ID $plantId не знайдено.")

            val careEvents = careEventDao.getRecentEventsForPlant(plantId, limit = com.flowers.app.domain.PlantChatContextBuilder.MAX_CARE_EVENTS)
            val chatHistory = chatMessageDao?.getLastSentMessages(plantId, limit = com.flowers.app.domain.PlantChatContextBuilder.MAX_CHAT_MESSAGES) ?: emptyList()

            val prompt = com.flowers.app.domain.PlantChatContextBuilder.buildPrompt(
                plant = plant,
                careEvents = careEvents,
                chatHistory = chatHistory,
                userQuestion = userQuestion,
                language = language,
                currentMessageId = currentMessageId
            )

            geminiPlantService.sendPlantChatMessage(prompt, plantId)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    activeSendingCounters.computeIfPresent(plantId) { _, counter ->
                        if (counter.decrementAndGet() <= 0) null else counter
                    }
                }
            }
        }
    }
}
