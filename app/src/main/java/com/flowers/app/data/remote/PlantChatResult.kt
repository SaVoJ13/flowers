package com.flowers.app.data.remote

/**
 * Типізований результат запиту до персонального ШІ-чату рослини (TASK-056 / Крок 2).
 */
sealed class PlantChatResult {
    /** Успішна відповідь моделі. */
    data class Success(
        val reply: String,
        val isSafetySanitized: Boolean = false
    ) : PlantChatResult()

    /** Відсутнє інтернет-з'єднання. */
    data class Offline(
        val message: String = AiErrorMapper.MSG_NO_INTERNET
    ) : PlantChatResult()

    /** Перевищено безкоштовний ліміт запитів (HTTP 429). */
    data class RateLimited(
        val message: String = AiErrorMapper.MSG_RATE_LIMIT
    ) : PlantChatResult()

    /** Таймаут очікування відповіді від сервісу. */
    data class Timeout(
        val message: String = AiErrorMapper.MSG_TIMEOUT
    ) : PlantChatResult()

    /** Отримано порожню, пропущену або некоректну відповідь від моделі. */
    data class InvalidResponse(
        val message: String = "Отримано порожню або некоректну відповідь від ШІ-сервісу."
    ) : PlantChatResult()

    /** Помилка виклику сервісу (App Check, 5xx, системний збій). */
    data class ServiceError(
        val message: String
    ) : PlantChatResult()
}
