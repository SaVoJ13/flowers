package com.flowers.app.data.repository

import android.content.Context
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
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
    private val chatMessageDao: com.flowers.app.data.local.dao.ChatMessageDao? = null,
    private val geminiPlantService: GeminiPlantService = GeminiPlantService(catalogDao, carePodDao, AndroidNetworkChecker(context))
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

    override suspend fun insertPlant(plant: PlantEntity): Long = plantDao.insertPlant(plant)

    override suspend fun updatePlant(plant: PlantEntity) = plantDao.updatePlant(plant)

    override suspend fun deletePlant(plant: PlantEntity) = plantDao.deletePlant(plant)

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

    private fun getPlantMutex(plantId: Long): Mutex {
        return plantMutexes.computeIfAbsent(plantId) { Mutex() }
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

    override fun getChatMessages(plantId: Long): Flow<List<com.flowers.app.data.local.entity.ChatMessageEntity>> {
        return chatMessageDao?.observeMessages(plantId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override suspend fun getChatMessageById(messageId: Long): com.flowers.app.data.local.entity.ChatMessageEntity? {
        return chatMessageDao?.getById(messageId)
    }

    override suspend fun insertChatMessage(message: com.flowers.app.data.local.entity.ChatMessageEntity): Long {
        return chatMessageDao?.insertMessage(message) ?: -1L
    }

    override suspend fun updateChatMessageStatus(
        messageId: Long,
        status: com.flowers.app.data.local.entity.MessageStatus,
        errorMessage: String?
    ): Boolean {
        val rows = chatMessageDao?.updateStatus(messageId, status, errorMessage) ?: 0
        return rows > 0
    }

    override suspend fun saveAssistantResponse(
        userMessageId: Long,
        modelMessage: com.flowers.app.data.local.entity.ChatMessageEntity
    ): Long {
        return chatMessageDao?.saveAssistantResponse(userMessageId, modelMessage) ?: -1L
    }

    override suspend fun beginChatMessageRetry(messageId: Long, plantId: Long): Boolean {
        val rows = chatMessageDao?.beginRetryForPlant(messageId, plantId) ?: 0
        return rows > 0
    }

    override suspend fun clearChatHistory(plantId: Long): Int {
        return chatMessageDao?.deleteAllForPlant(plantId) ?: 0
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
