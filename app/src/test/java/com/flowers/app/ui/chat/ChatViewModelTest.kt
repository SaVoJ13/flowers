package com.flowers.app.ui.chat

import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.data.preferences.UserPreferences
import com.flowers.app.data.remote.AiErrorMapper
import com.flowers.app.data.remote.DiagnosePlantHealthResult
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.data.remote.PlantChatResult
import com.flowers.app.data.repository.FlowersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeChatRepository : FlowersRepository {
        val plantsMap = mutableMapOf<Long, PlantEntity>()
        private val plantsFlowMap = mutableMapOf<Long, MutableStateFlow<PlantEntity?>>()

        val messagesList = mutableListOf<ChatMessageEntity>()
        private val messagesFlowMap = mutableMapOf<Long, MutableStateFlow<List<ChatMessageEntity>>>()
        private val idCounter = AtomicLong(1L)

        val activeSendingPlants = mutableSetOf<Long>()
        var chatResult: PlantChatResult = PlantChatResult.Success("Привіт! Я твій помічник.")
        var sendChatMessageCallCount = 0
        var lastSentPlantId: Long? = null
        var lastSentQuestion: String? = null
        var lastSentLanguage: String? = null
        var lastSentMessageId: Long? = null

        var sendDeferred: CompletableDeferred<Unit>? = null
        var simulateModelSaveError: Boolean = false
        var simulateInsertError: Boolean = false
        var sendChatMessageException: Throwable? = null

        fun setPlant(plant: PlantEntity) {
            plantsMap[plant.id] = plant
            getOrCreatePlantFlow(plant.id).value = plant
        }

        fun removePlant(plantId: Long) {
            plantsMap.remove(plantId)
            getOrCreatePlantFlow(plantId).value = null
            // Cascade delete messages
            messagesList.removeAll { it.plantId == plantId }
            getOrCreateMessagesFlow(plantId).value = emptyList()
        }

        private fun getOrCreatePlantFlow(plantId: Long): MutableStateFlow<PlantEntity?> {
            return plantsFlowMap.getOrPut(plantId) { MutableStateFlow(plantsMap[plantId]) }
        }

        private fun getOrCreateMessagesFlow(plantId: Long): MutableStateFlow<List<ChatMessageEntity>> {
            return messagesFlowMap.getOrPut(plantId) {
                MutableStateFlow(messagesList.filter { it.plantId == plantId }.sortedWith(compareBy({ it.timestamp }, { it.id })))
            }
        }

        private fun notifyMessagesChanged(plantId: Long) {
            getOrCreateMessagesFlow(plantId).value = messagesList.filter { it.plantId == plantId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
        }

        override val userPreferences: UserPreferences get() = throw UnsupportedOperationException()
        override fun getAllActivePlants(): Flow<List<PlantEntity>> = throw UnsupportedOperationException()
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = throw UnsupportedOperationException()
        override suspend fun getPlantById(plantId: Long): PlantEntity? = plantsMap[plantId]
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = getOrCreatePlantFlow(plantId).asStateFlow()
        override suspend fun insertPlant(plant: PlantEntity): Long = throw UnsupportedOperationException()
        override suspend fun updatePlant(plant: PlantEntity) = throw UnsupportedOperationException()
        override suspend fun deletePlant(plant: PlantEntity) {
            removePlant(plant.id)
        }

        override fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>> = throw UnsupportedOperationException()
        override suspend fun getLastWateringEvent(plantId: Long): CareEventEntity? = throw UnsupportedOperationException()
        override suspend fun logCareEvent(event: CareEventEntity): Long = throw UnsupportedOperationException()

        override fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>> = throw UnsupportedOperationException()
        override fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>> = throw UnsupportedOperationException()
        override suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity? = throw UnsupportedOperationException()

        override fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>> = throw UnsupportedOperationException()
        override fun getGeneralPods(): Flow<List<CarePodEntity>> = throw UnsupportedOperationException()

        override suspend fun getSymptomTree(): SymptomTreeRoot = throw UnsupportedOperationException()
        override suspend fun performWatering(plant: PlantEntity, notes: String?) = throw UnsupportedOperationException()
        override suspend fun snoozePlant(plant: PlantEntity, days: Int) = throw UnsupportedOperationException()
        override suspend fun skipPlant(plant: PlantEntity) = throw UnsupportedOperationException()

        override suspend fun generatePlantWithAi(plantQuery: String): GeneratePlantResult = throw UnsupportedOperationException()
        override suspend fun identifyPlantFromPhoto(imageFile: File): IdentifyPlantResult = throw UnsupportedOperationException()
        override suspend fun diagnosePlantHealth(imageFile: File, plant: PlantEntity): DiagnosePlantHealthResult = throw UnsupportedOperationException()

        override fun isPlantChatSending(plantId: Long): Boolean = activeSendingPlants.contains(plantId)

        override fun getChatMessages(plantId: Long): Flow<List<ChatMessageEntity>> = getOrCreateMessagesFlow(plantId).asStateFlow()

        override suspend fun getChatMessageById(messageId: Long): ChatMessageEntity? {
            return messagesList.find { it.id == messageId }
        }

        override suspend fun insertChatMessage(message: ChatMessageEntity): Long {
            if (simulateInsertError) {
                throw IllegalStateException("Simulated insert error")
            }
            val id = if (message.id == 0L) idCounter.getAndIncrement() else message.id
            val inserted = message.copy(id = id)
            messagesList.add(inserted)
            notifyMessagesChanged(message.plantId)
            return id
        }

        override suspend fun updateChatMessageStatus(messageId: Long, status: MessageStatus, errorMessage: String?): Boolean {
            val idx = messagesList.indexOfFirst { it.id == messageId }
            if (idx >= 0) {
                val updated = messagesList[idx].copy(status = status, errorMessage = errorMessage)
                messagesList[idx] = updated
                notifyMessagesChanged(updated.plantId)
                return true
            }
            return false
        }

        override suspend fun saveAssistantResponse(userMessageId: Long, modelMessage: ChatMessageEntity): Long {
            val userIdx = messagesList.indexOfFirst { it.id == userMessageId }
            if (userIdx < 0) return -1L
            val userMsg = messagesList[userIdx]

            if (userMsg.status == MessageStatus.SENT) return -1L
            if (userMsg.status != MessageStatus.SENDING) return -1L

            if (simulateModelSaveError) {
                throw IllegalStateException("Simulated DB error during assistant response save")
            }

            // Атомарне оновлення: USER -> SENT
            messagesList[userIdx] = userMsg.copy(status = MessageStatus.SENT, errorMessage = null)

            // Вставка MODEL
            val modelId = idCounter.getAndIncrement()
            val insertedModel = modelMessage.copy(
                id = modelId,
                plantId = userMsg.plantId,
                role = MessageRole.MODEL,
                status = MessageStatus.SENT,
                errorMessage = null
            )
            messagesList.add(insertedModel)
            notifyMessagesChanged(userMsg.plantId)
            return modelId
        }

        override suspend fun beginChatMessageRetry(messageId: Long, plantId: Long): Boolean {
            val idx = messagesList.indexOfFirst { it.id == messageId && it.plantId == plantId && it.status == MessageStatus.ERROR }
            if (idx >= 0) {
                val updated = messagesList[idx].copy(status = MessageStatus.SENDING, errorMessage = null)
                messagesList[idx] = updated
                notifyMessagesChanged(plantId)
                return true
            }
            return false
        }

        override suspend fun clearChatHistory(plantId: Long): Int {
            val count = messagesList.count { it.plantId == plantId }
            messagesList.removeAll { it.plantId == plantId }
            notifyMessagesChanged(plantId)
            return count
        }

        override suspend fun recoverStaleSendingMessages(plantId: Long): Int {
            if (activeSendingPlants.contains(plantId)) return 0
            var count = 0
            for (i in messagesList.indices) {
                if (messagesList[i].plantId == plantId && messagesList[i].status == MessageStatus.SENDING) {
                    messagesList[i] = messagesList[i].copy(
                        status = MessageStatus.ERROR,
                        errorMessage = "Запит перервано через перезапуск застосунку"
                    )
                    count++
                }
            }
            if (count > 0) {
                notifyMessagesChanged(plantId)
            }
            return count
        }

        override suspend fun sendPlantChatMessage(
            plantId: Long,
            userQuestion: String,
            language: String,
            currentMessageId: Long?
        ): PlantChatResult {
            sendChatMessageCallCount++
            lastSentPlantId = plantId
            lastSentQuestion = userQuestion
            lastSentLanguage = language
            lastSentMessageId = currentMessageId

            activeSendingPlants.add(plantId)
            try {
                sendChatMessageException?.let { throw it }
                sendDeferred?.await()
                return chatResult
            } finally {
                activeSendingPlants.remove(plantId)
            }
        }
    }

    private val testPlant = PlantEntity(
        id = 1L,
        customName = "Фікус Бенджаміна",
        speciesName = "Фікус Бенджаміна",
        botanicalName = "Ficus benjamina"
    )

    // 1. Відкриття порожнього чату
    @Test
    fun `openEmptyChat_initializesWithEmptyMessagesAndNotSending`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(testPlant, state.plant)
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isSending)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    // 2. Завантаження наявної історії офлайн
    @Test
    fun `loadExistingHistoryOffline_loadsMessagesInChronologicalOrder`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        repo.insertChatMessage(ChatMessageEntity(id = 10, plantId = 1L, role = MessageRole.USER, content = "Питання 1", status = MessageStatus.SENT, timestamp = 100))
        repo.insertChatMessage(ChatMessageEntity(id = 11, plantId = 1L, role = MessageRole.MODEL, content = "Відповідь 1", status = MessageStatus.SENT, timestamp = 200))
        repo.insertChatMessage(ChatMessageEntity(id = 12, plantId = 1L, role = MessageRole.USER, content = "Питання 2", status = MessageStatus.SENT, timestamp = 300))

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.messages.size)
        assertEquals(listOf(10L, 11L, 12L), state.messages.map { it.id })
        assertEquals("Питання 1", state.messages[0].content)
        assertEquals("Відповідь 1", state.messages[1].content)
        assertEquals("Питання 2", state.messages[2].content)
    }

    // 3. Надсилання й отримання відповіді Gemini
    @Test
    fun `sendMessage_successUpdatesUserToSentAndInsertsModelResponse`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        repo.chatResult = PlantChatResult.Success("Поливайте фікус раз на 7 днів.")

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Як часто поливати?")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSending)
        assertEquals(2, state.messages.size)

        val userMsg = state.messages[0]
        assertEquals(MessageRole.USER, userMsg.role)
        assertEquals("Як часто поливати?", userMsg.content)
        assertEquals(MessageStatus.SENT, userMsg.status)

        val modelMsg = state.messages[1]
        assertEquals(MessageRole.MODEL, modelMsg.role)
        assertEquals("Поливайте фікус раз на 7 днів.", modelMsg.content)
        assertEquals(MessageStatus.SENT, modelMsg.status)

        assertEquals(1L, repo.lastSentPlantId)
        assertEquals("Як часто поливати?", repo.lastSentQuestion)
    }

    // 4. Успішне атомарне збереження USER + MODEL
    @Test
    fun `atomicSaveAssistantResponse_updatesUserAndInsertsModelTogether`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val userMsgId = repo.insertChatMessage(
            ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "Скільки світла?", status = MessageStatus.SENDING)
        )

        val modelMsg = ChatMessageEntity(
            plantId = 1L,
            role = MessageRole.MODEL,
            content = "Потрібне яскраве розсіяне світло.",
            status = MessageStatus.SENT
        )

        val modelId = repo.saveAssistantResponse(userMsgId, modelMsg)
        assertTrue(modelId > 0)

        val savedUser = repo.getChatMessageById(userMsgId)
        assertNotNull(savedUser)
        assertEquals(MessageStatus.SENT, savedUser!!.status)

        val savedModel = repo.getChatMessageById(modelId)
        assertNotNull(savedModel)
        assertEquals(MessageRole.MODEL, savedModel!!.role)
        assertEquals("Потрібне яскраве розсіяне світло.", savedModel.content)
        assertEquals(MessageStatus.SENT, savedModel.status)
    }

    // 5. Помилка під час запису MODEL — немає USER SENT без відповіді
    @Test
    fun `errorDuringModelSave_rollsBackTransaction_noUserSentWithoutModel`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        repo.simulateModelSaveError = true

        val userMsgId = repo.insertChatMessage(
            ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "Тестове питання", status = MessageStatus.SENDING)
        )

        val modelMsg = ChatMessageEntity(
            plantId = 1L,
            role = MessageRole.MODEL,
            content = "Тестова відповідь",
            status = MessageStatus.SENT
        )

        try {
            repo.saveAssistantResponse(userMsgId, modelMsg)
            fail("Мала бути викинута помилка симуляції")
        } catch (_: IllegalStateException) {
            // Очікуваний виняток
        }

        val userMsgAfter = repo.getChatMessageById(userMsgId)
        assertNotNull(userMsgAfter)
        assertEquals(MessageStatus.SENDING, userMsgAfter!!.status)
        // Немає жодного повідомлення MODEL
        assertEquals(0, repo.messagesList.count { it.role == MessageRole.MODEL })

        // Перевірка обробки помилки Room крізь саму ChatViewModel
        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Питання з помилкою збереження MODEL")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSending)
        assertNotNull(vm.uiState.value.errorMessage)
        val userMsgVm = vm.uiState.value.messages.last()
        assertEquals(MessageStatus.ERROR, userMsgVm.status)
        assertTrue(userMsgVm.errorMessage?.contains("Simulated DB error") == true)
    }

    // 6. Порожній текст не надсилається
    @Test
    fun `emptyOrBlankText_isNotSent`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("")
        vm.sendMessage("   ")
        vm.sendMessage("\n\t")
        advanceUntilIdle()

        assertEquals(0, repo.sendChatMessageCallCount)
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertFalse(vm.uiState.value.isSending)
    }

    // 7. Подвійне натискання не створює дублікатів
    @Test
    fun `doubleClickSendMessage_isBlockedSynchronously_noDuplicateRequests`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val deferred = CompletableDeferred<Unit>()
        repo.sendDeferred = deferred

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Запитання 1")
        assertTrue(vm.uiState.value.isSending)
        testScheduler.runCurrent()
        assertEquals(1, repo.sendChatMessageCallCount)

        // Другий виклик під час активного надсилання
        vm.sendMessage("Запитання 2")
        assertEquals(1, repo.sendChatMessageCallCount)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSending)
        assertEquals(1, repo.sendChatMessageCallCount)
        // Лише одна пара повідомлень у чаті
        assertEquals(2, vm.uiState.value.messages.size)
    }

    // 8. Offline, Timeout, RateLimited, InvalidResponse, ServiceError
    @Test
    fun `errorResults_updateUserToErrorWithoutCreatingModelResponse`() = runTest {
        val errorCases = listOf(
            PlantChatResult.Offline("Немає зв'язку з інтернетом") to "Немає зв'язку з інтернетом",
            PlantChatResult.Timeout(AiErrorMapper.MSG_TIMEOUT) to AiErrorMapper.MSG_TIMEOUT,
            PlantChatResult.RateLimited(AiErrorMapper.MSG_RATE_LIMIT) to AiErrorMapper.MSG_RATE_LIMIT,
            PlantChatResult.InvalidResponse("Некоректна відповідь") to "Некоректна відповідь",
            PlantChatResult.ServiceError("Збій сервера 500") to "Збій сервера 500"
        )

        for ((result, expectedErrorMsg) in errorCases) {
            val repo = FakeChatRepository()
            repo.setPlant(testPlant)
            repo.chatResult = result

            val vm = ChatViewModel(plantId = 1L, repository = repo)
            advanceUntilIdle()

            vm.sendMessage("Питання")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(1, state.messages.size)
            val msg = state.messages[0]
            assertEquals(MessageRole.USER, msg.role)
            assertEquals(MessageStatus.ERROR, msg.status)
            assertEquals(expectedErrorMsg, msg.errorMessage)

            // Жодної відповіді MODEL не створено
            assertEquals(0, state.messages.count { it.role == MessageRole.MODEL })
        }
    }

    // 9. Retry після ERROR з тим самим ID
    @Test
    fun `retryMessage_reusesSameIdAndTextWithoutCreatingNewUserMessage`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        repo.chatResult = PlantChatResult.Offline("Офлайн")

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Чи можна обрізати взимку?")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.messages.size)
        val failedMsg = vm.uiState.value.messages[0]
        assertEquals(MessageStatus.ERROR, failedMsg.status)
        val originalId = failedMsg.id

        // Тепер відновлюємо мережу та робимо повтор
        repo.chatResult = PlantChatResult.Success("Так, можна виконати санітарну обрізку.")
        vm.retryMessage(originalId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.messages.size)

        val retriedMsg = state.messages[0]
        assertEquals(originalId, retriedMsg.id)
        assertEquals("Чи можна обрізати взимку?", retriedMsg.content)
        assertEquals(MessageStatus.SENT, retriedMsg.status)

        val modelMsg = state.messages[1]
        assertEquals(MessageRole.MODEL, modelMsg.role)
        assertEquals("Так, можна виконати санітарну обрізку.", modelMsg.content)
        assertEquals(originalId, repo.lastSentMessageId)
    }

    // 10. Подвійний Retry не створює двох відповідей
    @Test
    fun `doubleRetry_isBlocked_createsOnlyOneModelResponse`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val deferred = CompletableDeferred<Unit>()
        repo.sendDeferred = deferred

        val msgId = repo.insertChatMessage(
            ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "Питання для повтору", status = MessageStatus.ERROR)
        )

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.retryMessage(msgId)
        // Синхронне виставлення прапорця без очікування корутин
        assertTrue(vm.uiState.value.isSending)

        // Другий клік Retry одразу в тому самому кадрі
        vm.retryMessage(msgId)

        // Спроба відправити нове повідомлення під час активного retry також блокується
        vm.sendMessage("Спроба нового запиту під час повтору")

        testScheduler.runCurrent()
        assertEquals(1, repo.sendChatMessageCallCount)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSending)
        assertEquals(1, repo.sendChatMessageCallCount)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    // 11. Повторний callback не створює дубліката MODEL
    @Test
    fun `repeatedAssistantCallback_doesNotCreateSecondModelResponse`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val userMsgId = repo.insertChatMessage(
            ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "Питання", status = MessageStatus.SENDING)
        )

        val modelMsg = ChatMessageEntity(plantId = 1L, role = MessageRole.MODEL, content = "Відповідь", status = MessageStatus.SENT)

        val firstId = repo.saveAssistantResponse(userMsgId, modelMsg)
        assertTrue(firstId > 0)

        // Повторний виклик для того самого повідомлення користувача
        val secondId = repo.saveAssistantResponse(userMsgId, modelMsg)
        assertEquals(-1L, secondId)

        // У базі лише одна відповідь MODEL
        assertEquals(1, repo.messagesList.count { it.role == MessageRole.MODEL })
    }

    // 12. Відновлення після смерті процесу
    @Test
    fun `processDeathRecovery_marksStaleSendingAsError_allowsRetry`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        // Зависле повідомлення від попереднього мертвого процесу
        val staleMsgId = repo.insertChatMessage(
            ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "Зависле питання", status = MessageStatus.SENDING)
        )

        // Запуск нового ChatViewModel у новому процесі
        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.messages.size)
        val recoveredMsg = state.messages[0]
        assertEquals(staleMsgId, recoveredMsg.id)
        assertEquals(MessageStatus.ERROR, recoveredMsg.status)
        assertTrue(recoveredMsg.errorMessage!!.contains("перезапуск"))

        // Тепер користувач може повторити це повідомлення
        repo.chatResult = PlantChatResult.Success("Відповідь після відновлення")
        vm.retryMessage(staleMsgId)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.messages.size)
        assertEquals(MessageStatus.SENT, vm.uiState.value.messages[0].status)
        assertEquals(MessageRole.MODEL, vm.uiState.value.messages[1].role)
    }

    // 13. Одночасні чати двох різних рослин
    @Test
    fun `concurrentChatsOfTwoPlants_areCompletelyIndependent`() = runTest {
        val repo = FakeChatRepository()
        val plantA = PlantEntity(id = 1L, customName = "Рослина А")
        val plantB = PlantEntity(id = 2L, customName = "Рослина Б")
        repo.setPlant(plantA)
        repo.setPlant(plantB)

        val deferred = CompletableDeferred<Unit>()
        repo.sendDeferred = deferred

        val vmA = ChatViewModel(plantId = 1L, repository = repo)
        val vmB = ChatViewModel(plantId = 2L, repository = repo)
        advanceUntilIdle()

        vmA.sendMessage("Питання для А")
        vmB.sendMessage("Питання для Б")

        // Обидва чати знаходяться у стані відправки одночасно
        assertTrue(vmA.uiState.value.isSending)
        assertTrue(vmB.uiState.value.isSending)

        repo.chatResult = PlantChatResult.Success("Відповідь для А")
        deferred.complete(Unit)
        advanceUntilIdle()

        assertFalse(vmA.uiState.value.isSending)
        assertFalse(vmB.uiState.value.isSending)
        assertEquals(2, vmA.uiState.value.messages.size)
        assertEquals("Питання для А", vmA.uiState.value.messages[0].content)

        assertEquals(2, vmB.uiState.value.messages.size)
        assertEquals("Питання для Б", vmB.uiState.value.messages[0].content)
    }

    // 14. Відсутність витоку історії між рослинами
    @Test
    fun `noMessageLeakBetweenPlants_retryMessageOfOtherPlantIsRejected`() = runTest {
        val repo = FakeChatRepository()
        val plantA = PlantEntity(id = 1L, customName = "Рослина А")
        val plantB = PlantEntity(id = 2L, customName = "Рослина Б")
        repo.setPlant(plantA)
        repo.setPlant(plantB)

        val msgBId = repo.insertChatMessage(
            ChatMessageEntity(plantId = 2L, role = MessageRole.USER, content = "Питання рослини Б", status = MessageStatus.ERROR)
        )

        val vmA = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        // Спроба повторити повідомлення рослини Б із чату рослини А
        vmA.retryMessage(msgBId)
        advanceUntilIdle()

        // У чаті рослини А нічого не з'явилось
        assertTrue(vmA.uiState.value.messages.isEmpty())
        // Повідомлення рослини Б залишилось у статусі ERROR
        assertEquals(MessageStatus.ERROR, repo.getChatMessageById(msgBId)!!.status)
        assertEquals(0, repo.sendChatMessageCallCount)
    }

    // 15. Видалення рослини під час запиту
    @Test
    fun `plantDeletedDuringRequest_doesNotCreateRecordsForDeletedPlant`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val deferred = CompletableDeferred<Unit>()
        repo.sendDeferred = deferred

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Чи живе рослина?")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSending)

        // Користувач видаляє рослину
        repo.deletePlant(testPlant)
        advanceUntilIdle()

        // Запит завершується
        deferred.complete(Unit)
        advanceUntilIdle()

        // Оскільки рослину видалено, повідомлень для неї немає
        assertEquals(0, repo.messagesList.size)
    }

    // 16. Очищення історії під час активного запиту
    @Test
    fun `clearChatDuringActiveRequest_cancelsAndDoesNotRecreateMessages`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val deferred = CompletableDeferred<Unit>()
        repo.sendDeferred = deferred

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Питання до очищення")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSending)

        // Очищаємо чат
        vm.clearChat()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSending)
        assertTrue(vm.uiState.value.messages.isEmpty())

        // Завершуємо мережеву відповідь
        deferred.complete(Unit)
        advanceUntilIdle()

        // Чат залишається порожнім, жодна відповідь не воскресає
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertEquals(0, repo.messagesList.size)
    }

    // 17. CancellationException та правильне завершення корутин
    @Test
    fun `cancellationException_resetsSendingStateWithoutStuckStatus`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        repo.sendChatMessageException = CancellationException("Job was cancelled")

        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Запитання")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSending)
    }

    // 18. Відправка повідомлення коли рослина ще не завантажена або null
    @Test
    fun `sendMessage_whenPlantIsNull_doesNotInsertAndSetsError`() = runTest {
        val repo = FakeChatRepository()
        val vm = ChatViewModel(plantId = 999L, repository = repo)
        advanceUntilIdle()

        vm.sendMessage("Питання для неіснуючої рослини")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSending)
        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertEquals(0, repo.sendChatMessageCallCount)
    }

    // 19. Запитання більше ліміту символів (500) відхиляється
    @Test
    fun `questionExceedingMaxLength_isRejected`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)
        val vm = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        val longQuestion = "a".repeat(501)
        vm.sendMessage(longQuestion)

        assertFalse(vm.uiState.value.isSending)
        assertEquals("Запитання занадто довге (максимум 500 символів).", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertEquals(0, repo.sendChatMessageCallCount)
    }

    // 20. Збереження історії при створенні нової ViewModel
    @Test
    fun `recreatingViewModel_preservesCompletePersistedHistory`() = runTest {
        val repo = FakeChatRepository()
        repo.setPlant(testPlant)

        val vm1 = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        repo.chatResult = PlantChatResult.Success("Збережена відповідь")
        vm1.sendMessage("Початкове питання")
        advanceUntilIdle()

        assertEquals(2, vm1.uiState.value.messages.size)

        // Створюємо нову ViewModel (перезахід на екран)
        val vm2 = ChatViewModel(plantId = 1L, repository = repo)
        advanceUntilIdle()

        assertEquals(2, vm2.uiState.value.messages.size)
        assertEquals("Початкове питання", vm2.uiState.value.messages[0].content)
        assertEquals("Збережена відповідь", vm2.uiState.value.messages[1].content)
    }
}
