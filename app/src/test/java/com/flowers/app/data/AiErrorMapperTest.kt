package com.flowers.app.data

import com.flowers.app.data.remote.AiErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class AiErrorMapperTest {

    private class FakeAppCheckException(msg: String?) : Exception(msg)

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
    fun `plain io exception without network hint is unknown`() {
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(IOException("stream closed")))
    }

    @Test
    fun `403 maps to app check`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(Exception("Server returned HTTP 403")))
    }

    @Test
    fun `permission denied maps to app check`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(Exception("PERMISSION_DENIED: blocked")))
    }

    @Test
    fun `app check message maps to app check`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(Exception("Firebase App-Check token invalid")))
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(Exception("AppCheck failed")))
    }

    @Test
    fun `app check exception class name maps to app check even without message`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(FakeAppCheckException(null)))
    }

    @Test
    fun `429 maps to rate limit`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(Exception("HTTP 429 Too Many Requests")))
    }

    @Test
    fun `rate limit text is the owner approved wording`() {
        assertEquals(
            "Безкоштовний ліміт ШІ-генерації зараз вичерпано. Спробуйте пізніше сьогодні або завтра. Решта функцій працює.",
            AiErrorMapper.map(Exception("429"))
        )
    }

    @Test
    fun `resource exhausted quota and rate limit map to rate limit`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(Exception("RESOURCE_EXHAUSTED")))
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(Exception("You exceeded your current quota")))
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(Exception("Rate limit reached")))
    }

    @Test
    fun `500 503 and unavailable map to server error`() {
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(Exception("HTTP 500")))
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(Exception("HTTP 503")))
        assertEquals(AiErrorMapper.MSG_SERVER, AiErrorMapper.map(Exception("UNAVAILABLE: overloaded")))
    }

    @Test
    fun `unknown and empty errors map to generic message`() {
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(Exception("something odd")))
        assertEquals(AiErrorMapper.MSG_UNKNOWN, AiErrorMapper.map(Exception()))
    }

    @Test
    fun `priority network beats app check and quota`() {
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.map(UnknownHostException("403 quota")))
    }

    @Test
    fun `priority 403 beats quota`() {
        assertEquals(AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.map(Exception("403 quota exceeded")))
    }

    @Test
    fun `priority quota beats server error`() {
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, AiErrorMapper.map(Exception("429 UNAVAILABLE")))
    }

    @Test
    fun `all messages are distinct and non blank`() {
        val all = listOf(
            AiErrorMapper.MSG_NO_INTERNET, AiErrorMapper.MSG_APP_CHECK, AiErrorMapper.MSG_RATE_LIMIT,
            AiErrorMapper.MSG_SERVER, AiErrorMapper.MSG_UNKNOWN
        )
        assertEquals(all.size, all.toSet().size)
        all.forEach { assert(it.isNotBlank()) }
    }
}
