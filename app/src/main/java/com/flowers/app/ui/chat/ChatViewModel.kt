package com.flowers.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.PlantChatResult
import com.flowers.app.data.repository.FlowersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Стан користувацького інтерфейсу персонального ШІ-чату рослини (TASK-056 / Крок 3).
 */
data class ChatUiState(
    val plant: PlantEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel персонального ШІ-чату для конкретної рослини.
 *
 * Відповідає за:
 * - реактивне спостереження за історією повідомлень та даними рослини через Room Flow;
 * - захист від повторних надсилань (синхронний дебаунс);
 * - атомарне збереження відповідей асистента;
 * - відновлення повідомлень після перезапуску процесу;
 * - ідемпотентний повтор (Retry) помилкових реплік користувача;
 * - безпечне очищення історії чату обраної рослини.
 */
class ChatViewModel(
    val plantId: Long,
    private val repository: FlowersRepository,
    private val language: String = "uk"
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestToken: Long = 0L

    init {
        loadPlantAndMessages()
    }

    private fun loadPlantAndMessages() {
        // 1. Спостереження за даними рослини
        viewModelScope.launch {
            repository.getPlantFlowById(plantId)
                .catch { /* ігноруємо помилки або залишаємо попередній стан */ }
                .collect { plant ->
                    _uiState.update { it.copy(plant = plant, isLoading = false) }
                }
        }

        // 2. Безпечне відновлення завислих реплік SENDING після перезапуску процесу
        viewModelScope.launch {
            try {
                repository.recoverStaleSendingMessages(plantId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
            }
        }

        // 3. Спостереження за історією повідомлень через Flow
        viewModelScope.launch {
            repository.getChatMessages(plantId)
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
                }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages, isLoading = false) }
                }
        }
    }

    /**
     * Надсилає запитання користувача до асистента.
     */
    fun sendMessage(questionText: String) {
        val trimmed = questionText.trim()
        if (trimmed.isBlank()) {
            return
        }

        if (trimmed.length > MAX_QUESTION_LENGTH) {
            _uiState.update {
                it.copy(errorMessage = "Запитання занадто довге (максимум $MAX_QUESTION_LENGTH символів).")
            }
            return
        }

        if (_uiState.value.plant == null) {
            _uiState.update {
                it.copy(errorMessage = "Дані про рослину ще завантажуються або рослину видалено.")
            }
            return
        }

        // Синхронне блокування повторного натискання
        if (_uiState.value.isSending) {
            return
        }
        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        val token = ++activeRequestToken
        activeJob = viewModelScope.launch {
            var createdMessageId = -1L
            try {
                // 1. Збереження повідомлення USER зі статусом SENDING у Room
                val userMessage = ChatMessageEntity(
                    plantId = plantId,
                    role = MessageRole.USER,
                    content = trimmed,
                    status = MessageStatus.SENDING,
                    errorMessage = null,
                    timestamp = System.currentTimeMillis()
                )
                createdMessageId = repository.insertChatMessage(userMessage)
                if (createdMessageId <= 0) {
                    return@launch
                }

                // 2. Виклик FlowersRepository.sendPlantChatMessage
                executeChatRequest(createdMessageId, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (createdMessageId > 0) {
                    try {
                        repository.updateChatMessageStatus(
                            createdMessageId,
                            MessageStatus.ERROR,
                            e.message ?: "Помилка збереження або обробки повідомлення"
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                    }
                }
                _uiState.update { it.copy(errorMessage = e.message ?: "Помилка відправки повідомлення") }
            } finally {
                if (activeRequestToken == token) {
                    _uiState.update { it.copy(isSending = false) }
                }
            }
        }
    }

    /**
     * Повторює відправку невдалого повідомлення користувача (статус ERROR).
     * Використовує наявний id репліки без створення нового запису.
     */
    fun retryMessage(messageId: Long) {
        // Синхронне блокування повторного натискання
        if (_uiState.value.isSending) {
            return
        }
        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        val token = ++activeRequestToken
        activeJob = viewModelScope.launch {
            try {
                // Перевіряємо, що повідомлення належить цій рослині, є USER і має статус ERROR
                val message = repository.getChatMessageById(messageId)
                if (message == null || message.plantId != plantId || message.role != MessageRole.USER || message.status != MessageStatus.ERROR) {
                    return@launch
                }

                // Атомарна зміна статусу ERROR -> SENDING у БД
                val retryStarted = repository.beginChatMessageRetry(messageId, plantId)
                if (!retryStarted) {
                    return@launch
                }

                executeChatRequest(messageId, message.content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    repository.updateChatMessageStatus(
                        messageId,
                        MessageStatus.ERROR,
                        e.message ?: "Помилка повтору повідомлення"
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                }
                _uiState.update { it.copy(errorMessage = e.message ?: "Помилка повтору повідомлення") }
            } finally {
                if (activeRequestToken == token) {
                    _uiState.update { it.copy(isSending = false) }
                }
            }
        }
    }

    private suspend fun executeChatRequest(userMessageId: Long, questionText: String) {
        val result = repository.sendPlantChatMessage(
            plantId = plantId,
            userQuestion = questionText,
            language = language,
            currentMessageId = userMessageId
        )

        when (result) {
            is PlantChatResult.Success -> {
                val reply = result.reply.trim()
                if (reply.isNotEmpty()) {
                    val modelMessage = ChatMessageEntity(
                        plantId = plantId,
                        role = MessageRole.MODEL,
                        content = reply,
                        status = MessageStatus.SENT,
                        errorMessage = null,
                        timestamp = System.currentTimeMillis()
                    )
                    // Атомарна транзакція Room: USER -> SENT + вставка MODEL
                    repository.saveAssistantResponse(userMessageId, modelMessage)
                } else {
                    repository.updateChatMessageStatus(
                        userMessageId,
                        MessageStatus.ERROR,
                        "Отримано порожню відповідь від ШІ-сервісу."
                    )
                }
            }
            is PlantChatResult.Offline -> {
                repository.updateChatMessageStatus(userMessageId, MessageStatus.ERROR, result.message)
            }
            is PlantChatResult.RateLimited -> {
                repository.updateChatMessageStatus(userMessageId, MessageStatus.ERROR, result.message)
            }
            is PlantChatResult.Timeout -> {
                repository.updateChatMessageStatus(userMessageId, MessageStatus.ERROR, result.message)
            }
            is PlantChatResult.InvalidResponse -> {
                repository.updateChatMessageStatus(userMessageId, MessageStatus.ERROR, result.message)
            }
            is PlantChatResult.ServiceError -> {
                repository.updateChatMessageStatus(userMessageId, MessageStatus.ERROR, result.message)
            }
        }
    }

    /**
     * Очищає історію чату для поточної рослини.
     * Скасовує активні запити та гарантує, що жодне повідомлення не буде створене після очищення.
     */
    fun clearChat() {
        val previousJob = activeJob
        previousJob?.cancel()
        activeJob = null
        val token = ++activeRequestToken
        _uiState.update { it.copy(isSending = false) }
        viewModelScope.launch {
            try {
                previousJob?.join()
            } catch (_: CancellationException) {
            }
            if (activeRequestToken == token) {
                repository.clearChatHistory(plantId)
            }
        }
    }

    /**
     * Скидає поточне повідомлення про помилку в UI.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        const val MAX_QUESTION_LENGTH = 500

        fun provideFactory(
            plantId: Long,
            repository: FlowersRepository,
            language: String = "uk"
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(plantId, repository, language) as T
            }
        }
    }
}
