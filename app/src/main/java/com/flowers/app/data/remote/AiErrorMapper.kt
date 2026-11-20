package com.flowers.app.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Мапінг винятків ШІ-генерації на українські тексти для користувача (TASK-020, виправлення дефекту мережі).
 *
 * Firebase AI SDK (firebase-ai 18.2.0, `FirebaseAIException.from`) загортає будь-який невідомий виняток
 * (зокрема UnknownHostException від Ktor) у `UnknownException("Something unexpected happened.", cause)`,
 * а таймаут Ktor у `RequestTimeoutException`. Тому розпізнавання йде по ВСЬОМУ ланцюжку cause і suppressed,
 * а не лише по верхньому винятку.
 *
 * Порядок гілок = пріоритет: таймаут → мережа → App Check/403 → 429/ліміт → 5xx → невідома помилка.
 */
object AiErrorMapper {

    const val MSG_NO_INTERNET = "Немає підключення до інтернету. Перевірте Wi-Fi або мобільні дані і спробуйте ще раз."
    const val MSG_TIMEOUT = "Запит до ШІ триває надто довго. Перевірте підключення до інтернету і спробуйте ще раз."
    const val MSG_APP_CHECK = "Помилка перевірки пристрою (App Check). Доступ тимчасово обмежено."
    const val MSG_RATE_LIMIT = "Безкоштовний ліміт ШІ-генерації зараз вичерпано. Спробуйте пізніше сьогодні або завтра. Решта функцій працює."
    const val MSG_SERVER = "Сервіс ШІ тимчасово недоступний. Будь ласка, спробуйте пізніше."
    const val MSG_UNKNOWN = "Не вдалося згенерувати картку догляду. Перевірте назву рослини та підключення до інтернету і спробуйте ще раз."

    private const val MAX_DEPTH = 16

    fun map(e: Throwable): String {
        val chain = flatten(e)

        return when {
            chain.any { isTimeout(it) } -> MSG_TIMEOUT
            chain.any { isNetwork(it) } -> MSG_NO_INTERNET
            chain.any { hasAppCheckSignal(it) } -> MSG_APP_CHECK
            chain.any { hasQuotaSignal(it) } -> MSG_RATE_LIMIT
            chain.any { hasServerSignal(it) } -> MSG_SERVER
            else -> MSG_UNKNOWN
        }
    }

    /** Усі винятки ланцюжка (cause і suppressed), у ширину, без циклів, з обмеженням глибини. */
    internal fun flatten(root: Throwable): List<Throwable> {
        val seen: MutableSet<Throwable> = Collections.newSetFromMap(IdentityHashMap())
        val result = ArrayList<Throwable>()
        var level = listOf(root)
        var depth = 0
        while (level.isNotEmpty() && depth < MAX_DEPTH) {
            val next = ArrayList<Throwable>()
            for (t in level) {
                if (!seen.add(t)) continue
                result.add(t)
                t.cause?.let { next.add(it) }
                next.addAll(t.suppressed)
            }
            level = next
            depth++
        }
        return result
    }

    private fun name(t: Throwable) = t.javaClass.name

    // java.net.SocketTimeoutException, Ktor ConnectTimeoutException/SocketTimeoutException/HttpRequestTimeoutException
    // і Firebase AI RequestTimeoutException (клас-обгортка таймауту Ktor) містять «Timeout» в імені класу.
    private fun isTimeout(t: Throwable) = name(t).contains("Timeout", ignoreCase = true)

    private fun isNetwork(t: Throwable): Boolean =
        t is UnknownHostException ||
            t is ConnectException ||
            t is NoRouteToHostException ||
            t is SocketException ||
            t is IOException ||
            t.message.orEmpty().let {
                it.contains("Unable to resolve host", ignoreCase = true) ||
                    it.contains("Network is unreachable", ignoreCase = true)
            }

    private fun hasAppCheckSignal(t: Throwable): Boolean {
        val m = t.message.orEmpty()
        return m.contains("AppCheck", ignoreCase = true) || m.contains("app-check", ignoreCase = true) ||
            m.contains("403") || m.contains("PERMISSION_DENIED", ignoreCase = true) ||
            name(t).contains("AppCheck", ignoreCase = true)
    }

    private fun hasQuotaSignal(t: Throwable): Boolean {
        val m = t.message.orEmpty()
        return m.contains("429") || m.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            m.contains("quota", ignoreCase = true) || m.contains("rate limit", ignoreCase = true) ||
            name(t).endsWith("QuotaExceededException")
    }

    private fun hasServerSignal(t: Throwable): Boolean {
        val m = t.message.orEmpty()
        return m.contains("500") || m.contains("503") || m.contains("UNAVAILABLE", ignoreCase = true) ||
            name(t).endsWith(".ServerException")
    }
}
