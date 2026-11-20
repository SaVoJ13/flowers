package com.flowers.app.data

import com.flowers.app.data.remote.AiErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiErrorMapperTest {

    @Test
    fun `unknown host maps to no internet`() {
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(UnknownHostException("firebasevertexai.googleapis.com")))
    }

    @Test
    fun `io exception with unable to resolve host maps to no internet`() {
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(IOException("Unable to resolve host \"x\"")))
    }

    @Test
    fun `io exception with network word maps to no internet`() {
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(IOException("Network is unreachable")))
    }

    @Test
    fun `plain io exception is a network error`() {
        // Раніше повертало MSG_UNKNOWN; після виправлення дефекту будь-який IOException = проблема мережі.
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(IOException("stream closed")))
    }

    // ---- HTTP-коди: за ТИПАМИ SDK (ServerException/QuotaExceededException тощо), TASK-046 ----

    private fun sdkServer(msg: String) = SdkExceptions.server(msg)

    @Test
    fun `server exception with 403 maps to app check`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(sdkServer("Server returned HTTP 403")))
    }

    @Test
    fun `server exception with permission denied or app check text maps to app check`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(sdkServer("PERMISSION_DENIED: blocked")))
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(sdkServer("Firebase App Check token is invalid.")))
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(sdkServer("AppCheck failed")))
    }

    @Test
    fun `access type exceptions map to app check without any message`() {
        listOf("InvalidAPIKeyException", "ServiceDisabledException", "PermissionMissingException").forEach {
            assertEquals(it, AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(SdkExceptions.byName(it, "x")))
        }
    }

    @Test
    fun `quota exceeded type maps to rate limit`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(SdkExceptions.quota()))
    }

    @Test
    fun `server exception with 429 or resource exhausted maps to rate limit`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(sdkServer("HTTP 429 Too Many Requests")))
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(sdkServer("RESOURCE_EXHAUSTED")))
    }

    @Test
    fun `rate limit text is the owner approved wording`() {
        assertEquals(
            "Безкоштовний ліміт ШІ-генерації зараз вичерпано. Спробуйте пізніше сьогодні або завтра. Решта функцій працює.",
            AiErrorMapper.map(SdkExceptions.quota())
        )
    }

    @Test
    fun `any other server exception maps to server error`() {
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(sdkServer("HTTP 500")))
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(sdkServer("HTTP 503")))
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(sdkServer("The service is currently unavailable.")))
    }

    // ---- Хибні спрацювання: цифри й слова в тексті НЕ-серверних винятків не мають значення ----

    @Test
    fun `status digits in non-sdk exception text are ignored`() {
        listOf("read 1500 bytes", "HTTP 500", "HTTP 403", "HTTP 429", "App Check failed", "quota", "RESOURCE_EXHAUSTED", "UNAVAILABLE")
            .forEach { assertEquals(it, AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(Exception(it))) }
    }

    @Test
    fun `read 1500 bytes does not produce server unavailable`() {
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(SdkExceptions.unknown(IllegalStateException("read 1500 bytes"))))
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(IllegalArgumentException("read 1500 bytes")))
    }

    @Test
    fun `status number embedded in a longer number does not match`() {
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(sdkServer("id 14039 and 4290 and v1.403.2")))
    }

    @Test
    fun `unknown and empty errors map to generic message`() {
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(Exception("something odd")))
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(Exception()))
    }

    @Test
    fun `priority network beats app check and quota`() {
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(SdkExceptions.unknown(UnknownHostException("403 quota"))))
    }

    @Test
    fun `priority 403 beats quota`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(sdkServer("403 quota exceeded")))
    }

    @Test
    fun `priority quota beats server error`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(sdkServer("429 UNAVAILABLE")))
    }

    // ---- Дефект «ШІ без мережі»: SDK загортає мережеві винятки. Використовуємо РЕАЛЬНІ класи firebase-ai 18.2.0. ----

    @Test
    fun `real SDK UnknownException wrapping UnknownHostException maps to no internet`() {
        val e = SdkExceptions.unknown(UnknownHostException("firebasevertexai.googleapis.com"))
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(e))
    }

    @Test
    fun `wrapped three levels deep maps to no internet`() {
        val e = SdkExceptions.unknown(
            RuntimeException("Request failed", IllegalStateException("io", ConnectException("Failed to connect")))
        )
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(e))
    }

    @Test
    fun `wrapped two levels with plain IOException maps to no internet`() {
        val e = SdkExceptions.unknown(RuntimeException("x", IOException("Software caused connection abort")))
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(e))
    }

    @Test
    fun `wrapped NoRouteToHost and SocketException map to no internet`() {
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(SdkExceptions.unknown(NoRouteToHostException("no route"))))
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(SdkExceptions.unknown(SocketException("Connection reset"))))
    }

    @Test
    fun `network exception found in suppressed`() {
        val top = SdkExceptions.unknown(null)
        top.addSuppressed(UnknownHostException("host"))
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(top))
    }

    @Test
    fun `wrapped SocketTimeoutException maps to timeout`() {
        val e = SdkExceptions.unknown(RuntimeException("x", SocketTimeoutException("timeout")))
        assertEquals(AiErrorMapper.MSG_TIMEOUT, AiErrorMapper.map(e))
    }

    @Test
    fun `real SDK RequestTimeoutException maps to timeout`() {
        assertEquals(AiErrorMapper.MSG_TIMEOUT, AiErrorMapper.map(SdkExceptions.requestTimeout()))
    }

    @Test
    fun `real SDK QuotaExceededException maps to rate limit even without message hints`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(SdkExceptions.quota()))
    }

    @Test
    fun `real SDK ServerException maps to server error`() {
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(SdkExceptions.server()))
    }

    @Test
    fun `wrapped 429 in cause chain maps to rate limit`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(SdkExceptions.unknown(sdkServer("HTTP 429"))))
    }

    @Test
    fun `wrapped exception without any network or status signal is generic`() {
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(SdkExceptions.unknown(IllegalArgumentException("bad json"))))
    }

    @Test
    fun `cyclic cause chain does not hang`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(a))
    }

    @Test
    fun `owner approved wording for no internet and generic`() {
        assertEquals("Немає підключення до інтернету. Перевірте Wi-Fi або мобільні дані і спробуйте ще раз.", AiErrorMapper.MSG_NO_INTERNET)
        assertEquals("Не вдалося згенерувати картку догляду. Перевірте назву рослини та підключення до інтернету і спробуйте ще раз.", AiErrorMapper.MSG_UNKNOWN)
    }

    @Test
    fun `all messages are distinct and non blank`() {
        val all = listOf(
            AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.MSG_RATE_LIMIT,
            AiErrorMapper.MSG_SERVER, AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.MSG_TIMEOUT
        )
        assertEquals(all.size, all.toSet().size)
        all.forEach { assert(it.isNotBlank()) }
    }
}
