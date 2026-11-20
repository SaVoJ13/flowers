package com.flowers.app.data

import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.remote.AiErrorMapper
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.NetworkChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.net.UnknownHostException

/** Перевірка мережі ДО запиту до ШІ і мапінг загорнутих винятків наскрізь через сервіс (виправлення дефекту). */
class GeminiPlantServiceNetworkTest {

    private inline fun <reified T : Any> failingProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            throw AssertionError("DAO не мав викликатись: ${m.name}")
        } as T

    private fun service(online: Boolean, generate: suspend (String) -> String?) =
        GeminiPlantService(failingProxy<CatalogDao>(), failingProxy<CarePodDao>(), NetworkChecker { online }, generate)

    @Test
    fun `offline returns no internet message without calling the model`() = runBlocking {
        var called = false
        val r = service(online = false) { called = true; "{}" }.generatePlantCareCard("тюльпан")
        assertFalse("модель не має викликатись без мережі", called)
        assertTrue(r is GeneratePlantResult.Error)
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, (r as GeneratePlantResult.Error).message)
    }

    @Test
    fun `online calls the model`() = runBlocking {
        var called = false
        service(online = true) { called = true; null }.generatePlantCareCard("тюльпан")
        assertTrue(called)
    }

    @Test
    fun `online but SDK wraps UnknownHostException still gives no internet message`() = runBlocking {
        val r = service(online = true) {
            throw SdkExceptions.unknown(UnknownHostException("firebasevertexai.googleapis.com"))
        }.generatePlantCareCard("тюльпан")
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, (r as GeneratePlantResult.Error).message)
    }

    @Test
    fun `blank query does not check network or call the model`() = runBlocking {
        var called = false
        val r = service(online = false) { called = true; null }.generatePlantCareCard("   ")
        assertFalse(called)
        assertEquals("Вкажіть назву рослини для пошуку.", (r as GeneratePlantResult.Error).message)
    }
}
