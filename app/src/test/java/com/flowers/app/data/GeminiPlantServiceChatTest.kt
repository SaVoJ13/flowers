package com.flowers.app.data

import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.AiErrorMapper
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.NetworkChecker
import com.flowers.app.data.remote.PlantChatResult
import com.flowers.app.data.remote.PlantSafetyGuardrail
import com.flowers.app.domain.PlantChatContextBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class GeminiPlantServiceChatTest {

    private lateinit var catalogDao: CatalogDao
    private lateinit var carePodDao: CarePodDao

    private inline fun <reified T : Any> failingProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            throw AssertionError("DAO не мав викликатись: ${m.name}")
        } as T

    private fun testPlant(id: Long = 1L) = PlantEntity(
        id = id,
        customName = "Фікус Каучуконосний",
        speciesName = "Ficus elastica",
        roomLocation = "Спальня",
        lightLevel = "INDIRECT",
        checkIntervalDays = 7,
        lastWateredDate = 1700000000000L,
        nextCheckDate = 1700604800000L
    )

    @Before
    fun setUp() {
        catalogDao = failingProxy()
        carePodDao = failingProxy()
    }

    private fun service(
        isOnline: Boolean = true,
        chatGenerator: suspend (systemInstruction: String, userPrompt: String) -> String? = { _, _ -> "Порада: поливайте помірно." }
    ): GeminiPlantService {
        val fakeNetwork = object : NetworkChecker {
            override fun isOnline(): Boolean = isOnline
        }
        return GeminiPlantService(
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            networkChecker = fakeNetwork,
            generateChatText = chatGenerator
        )
    }

    @Test
    fun `chatSeparatesSystemInstructionAndUserPrompt`() = runBlocking {
        var capturedSystemInstruction: String? = null
        var capturedUserPrompt: String? = null

        val s = service(chatGenerator = { sys, user ->
            capturedSystemInstruction = sys
            capturedUserPrompt = user
            "Добре освітлене місце."
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Яке світло потрібне?", "en")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(result is PlantChatResult.Success)

        // 1. SystemInstruction передається окремо й містить системну роль та динамічну мову
        assertTrue(capturedSystemInstruction!!.contains("Ти — ботанічний помічник"))
        assertTrue(capturedSystemInstruction!!.contains("English"))
        assertFalse(capturedSystemInstruction!!.contains("Фікус Каучуконосний"))
        assertFalse(capturedSystemInstruction!!.contains("Спальня"))
        assertFalse(capturedSystemInstruction!!.contains("Яке світло потрібне?"))

        // 2. UserPrompt передається окремо й містить паспорт рослини та запит
        assertTrue(capturedUserPrompt!!.contains("<plant_passport>"))
        assertTrue(capturedUserPrompt!!.contains("Фікус Каучуконосний"))
        assertTrue(capturedUserPrompt!!.contains("Яке світло потрібне?"))
        assertFalse(capturedUserPrompt!!.contains("Ти — ботанічний помічник"))
    }

    @Test
    fun `chatSuccess returnsSuccessWithCleanModelReply`() = runBlocking {
        val s = service(chatGenerator = { _, _ -> "Фікус любить розсіяне світло і регулярне обприскування." })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Яке світло потрібне?", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)

        assertTrue(result is PlantChatResult.Success)
        val success = result as PlantChatResult.Success
        assertEquals("Фікус любить розсіяне світло і регулярне обприскування.", success.reply)
        assertFalse(success.isSafetySanitized)
    }

    @Test
    fun `offline returnsOffline withoutCallingGemini`() = runBlocking {
        var called = false
        val s = service(isOnline = false, chatGenerator = { _, _ ->
            called = true
            "Відповідь"
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)

        assertTrue(result is PlantChatResult.Offline)
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, (result as PlantChatResult.Offline).message)
        assertFalse("Модель не повинна викликатися при відсутності мережі", called)
    }

    @Test
    fun `emptyOrBlankResponse returnsInvalidResponse`() = runBlocking {
        val s1 = service(chatGenerator = { _, _ -> "" })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")
        val r1 = s1.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(r1 is PlantChatResult.InvalidResponse)

        val s2 = service(chatGenerator = { _, _ -> "    \n   " })
        val r2 = s2.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(r2 is PlantChatResult.InvalidResponse)

        val s3 = service(chatGenerator = { _, _ -> null })
        val r3 = s3.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(r3 is PlantChatResult.InvalidResponse)
    }

    @Test
    fun `cancellationException isRethrownAndNotSwallowed`() = runBlocking {
        val s = service(chatGenerator = { _, _ ->
            throw kotlinx.coroutines.CancellationException("User left chat screen")
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        var caughtCancellation = false
        try {
            s.sendPlantChatMessage(prompt, plantId = 1L)
        } catch (_: kotlinx.coroutines.CancellationException) {
            caughtCancellation = true
        }

        assertTrue("CancellationException має прокидатися далі", caughtCancellation)
    }

    @Test
    fun `timeout returnsTimeout`() = runBlocking {
        val s = service(chatGenerator = { _, _ ->
            throw SocketTimeoutException("Read timed out")
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(result is PlantChatResult.Timeout)
        assertEquals(AiErrorMapper.MSG_TIMEOUT, (result as PlantChatResult.Timeout).message)
    }

    @Test
    fun `timeoutCancellationException returnsTimeout`() = runBlocking {
        val s = service(chatGenerator = { _, _ ->
            kotlinx.coroutines.withTimeout(1L) {
                kotlinx.coroutines.delay(50L)
            }
            "Не дійде"
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(result is PlantChatResult.Timeout)
    }

    @Test
    fun `rateLimit429 returnsRateLimited`() = runBlocking {
        val s = service(chatGenerator = { _, _ ->
            throw SdkExceptions.quota()
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(result is PlantChatResult.RateLimited)
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, (result as PlantChatResult.RateLimited).message)
    }

    @Test
    fun `serviceError500 returnsServiceError`() = runBlocking {
        val s = service(chatGenerator = { _, _ ->
            throw SdkExceptions.server()
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)
        assertTrue(result is PlantChatResult.ServiceError)
        assertEquals(AiErrorMapper.MSG_SERVER, (result as PlantChatResult.ServiceError).message)
    }

    @Test
    fun `toxicChemicalAdvice isSanitizedByGuardrail`() = runBlocking {
        val toxicReply = "Терміново обробіть листя дихлофосом або розчином актари і додайте мідний купорос."
        val s = service(chatGenerator = { _, _ -> toxicReply })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Як позбутися комах?", "uk")

        val result = s.sendPlantChatMessage(prompt, plantId = 1L)

        assertTrue(result is PlantChatResult.Success)
        val success = result as PlantChatResult.Success
        assertTrue("Має спрацювати прапорець санітизації", success.isSafetySanitized)
        assertFalse("Токсичний засіб має бути усунений", success.reply.contains("дихлофос"))
        assertFalse("Токсичний засіб має бути усунений", success.reply.contains("актара"))
        assertTrue("Має бути додане попередження про зміну", success.reply.contains(PlantSafetyGuardrail.SAFETY_NOTICE_MODIFIED))
        assertTrue("Має містити безпечні кроки першої допомоги", success.reply.contains("Ізолюйте рослину"))
    }

    @Test
    fun `concurrentCallsForSamePlant rejectsSecondCall`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val s = service(chatGenerator = { _, _ ->
            gate.await()
            "Відповідь після затримки"
        })
        val prompt = PlantChatContextBuilder.buildPrompt(testPlant(), emptyList(), emptyList(), "Питання", "uk")

        val job1 = async { s.sendPlantChatMessage(prompt, plantId = 100L) }
        delay(50) // Даємо першому запиту захопити Mutex

        // Другий одночасний запит для тієї ж рослини
        val result2 = s.sendPlantChatMessage(prompt, plantId = 100L)
        assertTrue(result2 is PlantChatResult.ServiceError)
        assertTrue((result2 as PlantChatResult.ServiceError).message.contains("ще обробляється"))

        gate.complete(Unit)
        val result1 = job1.await()
        assertTrue(result1 is PlantChatResult.Success)
    }

    @Test
    fun `photoDiagnosisAndCardGeneration haveNoRegressions`() = runBlocking {
        // Переконуємось, що існуючі методи генерації карток та діагностики не зламалися
        val s = service()
        val offlineService = service(isOnline = false)

        val offlineCard = offlineService.generatePlantCareCard("Монстера")
        assertTrue(offlineCard is com.flowers.app.data.remote.GeneratePlantResult.Error)

        val offlineDiag = offlineService.diagnosePlantHealth(java.io.File.createTempFile("test", ".jpg"), testPlant())
        assertTrue(offlineDiag is com.flowers.app.data.remote.DiagnosePlantHealthResult.Error)
    }
}
