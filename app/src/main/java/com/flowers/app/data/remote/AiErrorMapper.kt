package com.flowers.app.data.remote

import java.io.IOException
import java.net.UnknownHostException

/**
 * Мапінг винятків ШІ-генерації на українські тексти для користувача (TASK-020).
 * Порядок гілок = пріоритет: мережа → App Check/403 → 429/ліміт → 5xx → невідома помилка.
 */
object AiErrorMapper {

    const val MSG_NO_INTERNET = "Відсутнє підключення до інтернету. Перевірте зв'язок та спробуйте ще раз."
    const val MSG_APP_CHECK = "Помилка перевірки пристрою (App Check). Доступ тимчасово обмежено."
    const val MSG_RATE_LIMIT = "Перевищено ліміт запитів до ШІ. Будь ласка, зачекайте кілька хвилин і спробуйте знову."
    const val MSG_SERVER = "Сервіс ШІ тимчасово недоступний. Будь ласка, спробуйте пізніше."
    const val MSG_UNKNOWN = "Не вдалося згенерувати картку догляду. Будь ласка, перевірте назву рослини та спробуйте ще раз."

    fun map(e: Exception): String {
        val message = e.message.orEmpty()
        val className = e.javaClass.name

        return when {
            e is UnknownHostException || (e is IOException && (message.contains("Unable to resolve host", ignoreCase = true) || message.contains("network", ignoreCase = true))) -> MSG_NO_INTERNET
            message.contains("AppCheck", ignoreCase = true) || message.contains("app-check", ignoreCase = true) || message.contains("403") || message.contains("PERMISSION_DENIED", ignoreCase = true) || className.contains("AppCheck", ignoreCase = true) -> MSG_APP_CHECK
            message.contains("429") || message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || message.contains("quota", ignoreCase = true) || message.contains("rate limit", ignoreCase = true) -> MSG_RATE_LIMIT
            message.contains("500") || message.contains("503") || message.contains("UNAVAILABLE", ignoreCase = true) -> MSG_SERVER
            else -> MSG_UNKNOWN
        }
    }
}
