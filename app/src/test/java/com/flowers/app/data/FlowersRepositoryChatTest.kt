package com.flowers.app.data

import android.content.Context
import android.content.ContextWrapper
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.ChatMessageDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.PlantChatResult
import com.flowers.app.data.repository.FlowersRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class FlowersRepositoryChatTest {

    private open class FakePlantDao : PlantDao() {
        var plantToReturn: PlantEntity? = null
        var getPlantByIdCalled = false

        override fun getAllActivePlants(): Flow<List<PlantEntity>> = emptyFlow()
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = emptyFlow()
        override suspend fun getPlantById(plantId: Long): PlantEntity? {
            getPlantByIdCalled = true
            return plantToReturn
        }
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = emptyFlow()
        override suspend fun insertPlant(plant: PlantEntity): Long = 1L
        override suspend fun updatePlant(plant: PlantEntity) {}
        override suspend fun deletePlant(plant: PlantEntity) {}
        override suspend fun deletePlantById(plantId: Long) {}
        override suspend fun insertCareEvent(event: CareEventEntity): Long = 1L
        override suspend fun updateWateringDates(plantId: Long, lastWatered: Long, nextCheck: Long): Int = 1
        override suspend fun updateNextCheckDate(plantId: Long, nextCheck: Long): Int = 1
    }

    private open class FakeChatMessageDao : ChatMessageDao() {
        var lastSentMessagesToReturn: List<ChatMessageEntity> = emptyList()
        var insertMessageCalledWith: ChatMessageEntity? = null
        var saveAssistantResponseCalled = false
        var beginRetryForPlantCalledWith: Pair<Long, Long>? = null
        var markSendingAsErrorForPlantCalledWith: Pair<Long, String>? = null
        var deleteAllForPlantCalledWith: Long? = null
        val messagesMap = mutableMapOf<Long, ChatMessageEntity>()

        override fun observeMessages(plantId: Long): Flow<List<ChatMessageEntity>> = emptyFlow()
        override suspend fun getMessages(plantId: Long): List<ChatMessageEntity> = messagesMap.values.filter { it.plantId == plantId }
        override suspend fun getLastMessages(plantId: Long, limit: Int): List<ChatMessageEntity> = emptyList()
        override suspend fun getLastSentMessages(plantId: Long, limit: Int): List<ChatMessageEntity> = lastSentMessagesToReturn
        override suspend fun getById(id: Long): ChatMessageEntity? = messagesMap[id]
        override suspend fun insertMessage(message: ChatMessageEntity): Long {
            insertMessageCalledWith = message
            val id = if (message.id == 0L) (messagesMap.keys.maxOrNull() ?: 0L) + 1L else message.id
            messagesMap[id] = message.copy(id = id)
            return id
        }
        override suspend fun updateStatus(id: Long, status: MessageStatus, errorMessage: String?): Int {
            val msg = messagesMap[id] ?: return 0
            messagesMap[id] = msg.copy(status = status, errorMessage = errorMessage)
            return 1
        }
        override suspend fun beginRetry(id: Long): Int = 1
        override suspend fun beginRetryForPlant(id: Long, plantId: Long): Int {
            beginRetryForPlantCalledWith = Pair(id, plantId)
            val msg = messagesMap[id] ?: return 0
            if (msg.plantId == plantId && msg.status == MessageStatus.ERROR) {
                messagesMap[id] = msg.copy(status = MessageStatus.SENDING, errorMessage = null)
                return 1
            }
            return 0
        }
        override suspend fun markInterruptedSendingAsError(errorMessage: String): Int = 0
        override suspend fun markSendingAsErrorForPlant(plantId: Long, errorMessage: String): Int {
            markSendingAsErrorForPlantCalledWith = Pair(plantId, errorMessage)
            var count = 0
            messagesMap.forEach { (id, msg) ->
                if (msg.plantId == plantId && msg.status == MessageStatus.SENDING) {
                    messagesMap[id] = msg.copy(status = MessageStatus.ERROR, errorMessage = errorMessage)
                    count++
                }
            }
            return count
        }
        override suspend fun deleteAllForPlant(plantId: Long): Int {
            deleteAllForPlantCalledWith = plantId
            val before = messagesMap.size
            messagesMap.entries.removeIf { it.value.plantId == plantId }
            return before - messagesMap.size
        }
        override suspend fun saveAssistantResponse(userMessageId: Long, modelMessage: ChatMessageEntity): Long {
            saveAssistantResponseCalled = true
            val userMsg = messagesMap[userMessageId] ?: return -1L
            if (userMsg.status != MessageStatus.SENDING) return -1L
            messagesMap[userMessageId] = userMsg.copy(status = MessageStatus.SENT, errorMessage = null)
            val modelId = insertMessage(modelMessage.copy(plantId = userMsg.plantId, status = MessageStatus.SENT))
            return modelId
        }
    }

    private lateinit var plantDao: FakePlantDao
    private lateinit var careEventDao: CareEventDao
    private lateinit var chatMessageDao: FakeChatMessageDao
    private lateinit var catalogDao: CatalogDao
    private lateinit var carePodDao: CarePodDao
    private lateinit var context: Context

    private inline fun <reified T : Any> stubProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            if (m.name == "getPlantsCount") 1 else null
        } as T

    @Before
    fun setUp() {
        plantDao = FakePlantDao()
        careEventDao = stubProxy()
        chatMessageDao = FakeChatMessageDao()
        catalogDao = stubProxy()
        carePodDao = stubProxy()
        context = object : ContextWrapper(null) {}
    }

    private fun testPlant(id: Long = 1L) = PlantEntity(
        id = id,
        customName = "Фікус",
        roomLocation = "Зал",
        lightLevel = "BRIGHT",
        checkIntervalDays = 5
    )

    @Test
    fun `blankQuestion returnsServiceErrorWithoutQueryingDb`() = runBlocking {
        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(catalogDao, carePodDao, object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            })
        )

        val result = repo.sendPlantChatMessage(1L, "   ", "uk")
        assertTrue(result is PlantChatResult.ServiceError)
        assertEquals("Запитання не може бути порожнім.", (result as PlantChatResult.ServiceError).message)
        assertFalse("plantDao не мав викликатися", plantDao.getPlantByIdCalled)
    }

    @Test
    fun `plantNotFound returnsServiceError`() = runBlocking {
        plantDao.plantToReturn = null

        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(catalogDao, carePodDao, object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            })
        )

        val result = repo.sendPlantChatMessage(999L, "Чи потрібен полив?", "uk")
        assertTrue(result is PlantChatResult.ServiceError)
        assertTrue((result as PlantChatResult.ServiceError).message.contains("не знайдено"))
        assertTrue("plantDao мав бути опитаний", plantDao.getPlantByIdCalled)
    }

    @Test
    fun `validQuestion delegatesToGeminiPlantService`() = runBlocking {
        plantDao.plantToReturn = testPlant(1L)

        careEventDao = Proxy.newProxyInstance(CareEventDao::class.java.classLoader, arrayOf(CareEventDao::class.java)) { _, m, _ ->
            if (m.name == "getRecentEventsForPlant") emptyList<CareEventEntity>() else null
        } as CareEventDao

        chatMessageDao = FakeChatMessageDao()

        val geminiService = GeminiPlantService(
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            networkChecker = object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            },
            generateChatText = { _, _ -> "Фікус почувається чудово." }
        )

        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = geminiService
        )

        val result = repo.sendPlantChatMessage(1L, "Як справи?", "uk")
        assertTrue(result is PlantChatResult.Success)
        assertEquals("Фікус почувається чудово.", (result as PlantChatResult.Success).reply)
    }

    @Test
    fun `chatRepositoryMethods delegateCorrectlyToDao`() = runBlocking {
        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(catalogDao, carePodDao, object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            })
        )

        // 1. insertChatMessage & getChatMessageById
        val msg = ChatMessageEntity(
            id = 10L,
            plantId = 1L,
            role = com.flowers.app.data.local.entity.MessageRole.USER,
            content = "Тест",
            status = MessageStatus.SENDING,
            timestamp = 1000L
        )
        val insertedId = repo.insertChatMessage(msg)
        assertEquals(10L, insertedId)
        val fetched = repo.getChatMessageById(10L)
        assertEquals("Тест", fetched?.content)

        // 2. saveAssistantResponse
        val modelMsg = ChatMessageEntity(
            id = 11L,
            plantId = 1L,
            role = com.flowers.app.data.local.entity.MessageRole.MODEL,
            content = "Відповідь",
            status = MessageStatus.SENT,
            timestamp = 1010L
        )
        val savedModelId = repo.saveAssistantResponse(10L, modelMsg)
        assertEquals(11L, savedModelId)
        assertTrue(chatMessageDao.saveAssistantResponseCalled)
        assertEquals(MessageStatus.SENT, repo.getChatMessageById(10L)?.status)

        // 3. beginChatMessageRetry
        chatMessageDao.messagesMap[12L] = ChatMessageEntity(
            id = 12L,
            plantId = 1L,
            role = com.flowers.app.data.local.entity.MessageRole.USER,
            content = "Помилкове",
            status = MessageStatus.ERROR,
            timestamp = 1020L
        )
        val retrySuccess = repo.beginChatMessageRetry(12L, 1L)
        assertTrue(retrySuccess)
        assertEquals(MessageStatus.SENDING, repo.getChatMessageById(12L)?.status)
        assertEquals(Pair(12L, 1L), chatMessageDao.beginRetryForPlantCalledWith)

        // 4. recoverStaleSendingMessages when NOT sending
        chatMessageDao.messagesMap[13L] = ChatMessageEntity(
            id = 13L,
            plantId = 1L,
            role = com.flowers.app.data.local.entity.MessageRole.USER,
            content = "Зависле",
            status = MessageStatus.SENDING,
            timestamp = 1030L
        )
        val recoveredCount = repo.recoverStaleSendingMessages(1L)
        assertEquals(2, recoveredCount)
        assertEquals(MessageStatus.ERROR, repo.getChatMessageById(12L)?.status)
        assertEquals(MessageStatus.ERROR, repo.getChatMessageById(13L)?.status)

        // 5. clearChatHistory
        val deletedCount = repo.clearChatHistory(1L)
        assertEquals(1L, chatMessageDao.deleteAllForPlantCalledWith)
        assertTrue(repo.getChatMessageById(10L) == null)
    }

    @Test
    fun `isPlantChatSending tracksActiveRequestsAndPreventsStaleRecoveryDuringRequest`() = runBlocking {
        plantDao.plantToReturn = testPlant(1L)
        val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()

        careEventDao = Proxy.newProxyInstance(CareEventDao::class.java.classLoader, arrayOf(CareEventDao::class.java)) { _, m, _ ->
            if (m.name == "getRecentEventsForPlant") emptyList<CareEventEntity>() else null
        } as CareEventDao

        val geminiService = GeminiPlantService(
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            networkChecker = object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            },
            generateChatText = { _, _ ->
                deferred.await()
                "Відповідь після очікування"
            }
        )

        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = geminiService
        )

        assertFalse(repo.isPlantChatSending(1L))

        chatMessageDao.messagesMap[100L] = ChatMessageEntity(
            id = 100L,
            plantId = 1L,
            role = com.flowers.app.data.local.entity.MessageRole.USER,
            content = "Активне",
            status = MessageStatus.SENDING,
            timestamp = 1000L
        )

        val job = CoroutineScope(Dispatchers.IO).launch {
            repo.sendPlantChatMessage(1L, "Привіт", "uk")
        }

        // Чекаємо поки почнеться виконання
        while (!repo.isPlantChatSending(1L)) {
            delay(10)
        }
        assertTrue(repo.isPlantChatSending(1L))

        // Під час активного надсилання recoverStaleSendingMessages повертає 0 і не чіпає повідомлення
        val recovered = repo.recoverStaleSendingMessages(1L)
        assertEquals(0, recovered)
        assertEquals(MessageStatus.SENDING, repo.getChatMessageById(100L)?.status)

        // Завершуємо запит
        deferred.complete(Unit)
        job.join()

        assertFalse(repo.isPlantChatSending(1L))
    }

    @Test
    fun `concurrentOverlappingRequestsForSamePlant_maintainAtomicCounter`() = runBlocking {
        withTimeout(5_000L) {
            val entered1 = CompletableDeferred<Unit>()
            val deferred1 = CompletableDeferred<Unit>()
            val entered2 = CompletableDeferred<Unit>()
            val proceed2 = CompletableDeferred<Unit>()

            val pauseSecondRequest = java.util.concurrent.atomic.AtomicBoolean(false)
            val synchronizedPlantDao = object : FakePlantDao() {
                override suspend fun getPlantById(plantId: Long): PlantEntity? {
                    if (pauseSecondRequest.get()) {
                        entered2.complete(Unit)
                        proceed2.await()
                    }
                    return testPlant(plantId)
                }
            }

            careEventDao = Proxy.newProxyInstance(CareEventDao::class.java.classLoader, arrayOf(CareEventDao::class.java)) { _, m, _ ->
                if (m.name == "getRecentEventsForPlant") emptyList<CareEventEntity>() else null
            } as CareEventDao

            val geminiService = GeminiPlantService(
                catalogDao = catalogDao,
                carePodDao = carePodDao,
                networkChecker = object : com.flowers.app.data.remote.NetworkChecker {
                    override fun isOnline(): Boolean = true
                },
                generateChatText = { _, _ ->
                    entered1.complete(Unit)
                    deferred1.await()
                    "Відповідь 1"
                }
            )

            val repo = FlowersRepositoryImpl(
                plantDao = synchronizedPlantDao,
                careEventDao = careEventDao,
                catalogDao = catalogDao,
                carePodDao = carePodDao,
                context = context,
                chatMessageDao = chatMessageDao,
                geminiPlantService = geminiService
            )

            // 1. Запускаємо job1
            val job1 = launch(Dispatchers.IO) {
                repo.sendPlantChatMessage(1L, "Запит 1", "uk")
            }

            try {
                // Очікуємо гарантованого входу job1 у generateChatText (вже захопив Mutex у GeminiPlantService)
                entered1.await()
                assertTrue(repo.isPlantChatSending(1L))

                // Вмикаємо паузу для job2 перед запуском другого запиту
                pauseSecondRequest.set(true)

                // 2. Запускаємо job2
                val job2 = launch(Dispatchers.IO) {
                    repo.sendPlantChatMessage(1L, "Запит 2", "uk")
                }

                try {
                    // Очікуємо гарантованого входу job2 у sendPlantChatMessage (вже інкрементував counter)
                    entered2.await()

                    // Обидва запити одночасно активні
                    assertTrue(repo.isPlantChatSending(1L))

                    // Завершуємо перший запит
                    deferred1.complete(Unit)
                    job1.join()

                    // Другий запит ще активний (очікує в getPlantById), тому repo.isPlantChatSending залишається true!
                    assertTrue("Лічильник повинен утримувати plantId активним, поки другий запит працює", repo.isPlantChatSending(1L))

                    // Відпускаємо другий запит
                    proceed2.complete(Unit)
                    job2.join()

                    // Тепер обидва запити завершені
                    assertFalse(repo.isPlantChatSending(1L))
                } finally {
                    proceed2.complete(Unit)
                    job2.cancel()
                }
            } finally {
                deferred1.complete(Unit)
                job1.cancel()
            }
        }
    }
}
