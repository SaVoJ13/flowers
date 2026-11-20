package com.flowers.app.data.remote

import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
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
 * HTTP-коди розпізнаються за ТИПАМИ винятків SDK, а не підрядком у тексті будь-якого винятку (TASK-046).
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

    /** Типи SDK, що означають проблему доступу (ключ, API вимкнено, дозволи, регіон), а не мережу чи ліміт. */
    private fun isAccessType(t: Throwable) =
        t is InvalidAPIKeyException || t is ServiceDisabledException || t is APINotConfiguredException ||
            t is PermissionMissingException || t is UnsupportedUserLocationException

    // SDK 18.2.0 НЕ зберігає HTTP-код у виняткові: `validateResponse` будує виняток із тексту помилки сервера
    // (повідомлення містить «quota» → QuotaExceededException, усе інше → ServerException з сирим текстом сервера).
    // Тому код і ознаки App Check шукаємо ЛИШЕ в ServerException (його текст це відповідь сервера) і лише як ціле
    // слово: «read 1500 bytes» у будь-якому іншому винятку не дає ні «500», ні «403».
    private fun hasAppCheckSignal(t: Throwable): Boolean {
        if (isAccessType(t)) return true
        if (t !is ServerException) return false
        val m = t.message.orEmpty()
        return STATUS_403.containsMatchIn(m) || m.contains("App Check", ignoreCase = true) ||
            m.contains("AppCheck", ignoreCase = true) || m.contains("PERMISSION_DENIED", ignoreCase = true)
    }

    private fun hasQuotaSignal(t: Throwable): Boolean {
        if (t is QuotaExceededException) return true
        if (t !is ServerException) return false
        val m = t.message.orEmpty()
        return STATUS_429.containsMatchIn(m) || m.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
    }

    private fun hasServerSignal(t: Throwable) = t is ServerException

    private val STATUS_403 = Regex("""(?<![\d.])403(?![\d.])""")
    private val STATUS_429 = Regex("""(?<![\d.])429(?![\d.])""")
}
