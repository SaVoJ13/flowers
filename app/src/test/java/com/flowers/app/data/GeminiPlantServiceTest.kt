package com.flowers.app.data

import android.graphics.Bitmap
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.remote.AiErrorMapper
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.GeneratedPlantResponse
import com.flowers.app.data.remote.IdentifyPlantResult
import com.flowers.app.data.remote.NetworkChecker
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class GeminiPlantServiceTest {

    private val gson = Gson()

    private inline fun <reified T : Any> failingProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            throw AssertionError("DAO не мав викликатись: ${m.name}")
        } as T

    @Test
    fun `parse mock Gemini response with markdown fences and structured fields`() {
        val geminiRawResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\n{\n  \"name_uk\": \"Філодендрон Біркін\",\n  \"name_latin\": \"Philodendron Birkin\",\n  \"popular_name\": \"Біркін\",\n  \"difficulty\": \"Легко\",\n  \"light\": \"Яскраве розсіяне світло без прямих сонячних променів\",\n  \"default_check_interval_days\": 7,\n  \"soil_type\": \"Пухкий, повітропроникний субстрат із перлітом і корою\",\n  \"toxic_to_pets\": true,\n  \"care_tip\": \"Поливати після просихання верхнього шару на 2-3 см, протирати листя від пилу.\"\n}\n```"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val root = gson.fromJson(geminiRawResponse, Map::class.java)
        val candidates = root["candidates"] as List<*>
        val firstCandidate = candidates[0] as Map<*, *>
        val content = firstCandidate["content"] as Map<*, *>
        val parts = content["parts"] as List<*>
        val firstPart = parts[0] as Map<*, *>
        val text = firstPart["text"] as String

        val cleanedText = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val generated = gson.fromJson(cleanedText, GeneratedPlantResponse::class.java)

        assertNotNull(generated)
        assertEquals("Філодендрон Біркін", generated.nameUk)
        assertEquals("Philodendron Birkin", generated.nameLatin)
        assertEquals("Біркін", generated.popularName)
        assertEquals("Легко", generated.difficulty)
        assertEquals(7, generated.defaultCheckIntervalDays)
        // TASK-025: токсичність із відповіді ШІ ігнорується, у DTO такого поля немає.
        assertTrue(GeneratedPlantResponse::class.java.declaredFields.none { it.name.contains("toxic", ignoreCase = true) })
        assertTrue(generated.careTip.contains("Поливати"))
    }

    @Test
    fun `parseIdentificationResponse correctly parses valid JSON with markdown fences`() {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true }
        )

        val raw = """
            ```json
            {
              "is_plant": true,
              "name_uk": "Монстера делікатесна",
              "name_latin": "Monstera deliciosa",
              "confidence": "high",
              "description": "Популярна ліана з великим різьбленим листям.",
              "light_level": "indirect",
              "light": "Яскраве розсіяне світло",
              "default_check_interval_days": 8,
              "watering_advice": "Поливати після просихання верхнього шару ґрунту.",
              "soil_type": "Пухкий субстрат для ароїдних",
              "toxicity_info": "Листя містить оксалати кальцію, тримати далі від тварин",
              "alternative_species": ["Філодендрон двійчастоперистий"]
            }
            ```
        """.trimIndent()

        val parsed = service.parseIdentificationResponse(raw)
        assertNotNull(parsed)
        assertTrue(parsed!!.isPlant)
        assertEquals("Монстера делікатесна", parsed.nameUk)
        assertEquals("Monstera deliciosa", parsed.nameLatin)
        assertEquals("HIGH", parsed.confidence)
        assertEquals("INDIRECT", parsed.lightLevel)
        assertEquals(8, parsed.defaultCheckIntervalDays)
        assertEquals("Листя містить оксалати кальцію, тримати далі від тварин", parsed.toxicityInfo)
        assertEquals(listOf("Філодендрон двійчастоперистий"), parsed.alternativeSpecies)
    }

    @Test
    fun `identifyPlantFromPhoto returns NotAPlant when is_plant is false`() = runBlocking {
        val json = """
            {
              "is_plant": false,
              "name_uk": "",
              "name_latin": "",
              "confidence": "LOW",
              "description": "На фото зображено кішку",
              "light_level": "INDIRECT",
              "default_check_interval_days": 7
            }
        """.trimIndent()

        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> json }
        )

        val result = service.identifyPlantFromPhoto(null as Bitmap?)
        assertTrue(result is IdentifyPlantResult.NotAPlant)
    }

    @Test
    fun `identifyPlantFromPhoto returns Success with sanitized confidence and interval`() = runBlocking {
        val json = """
            {
              "is_plant": true,
              "name_uk": "Заміокулькас",
              "name_latin": "Zamioculcas zamiifolia",
              "confidence": "unknown_value",
              "description": "Невибаглива вічнозелена рослина.",
              "light_level": "shade",
              "default_check_interval_days": 40
            }
        """.trimIndent()

        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> json }
        )

        val result = service.identifyPlantFromPhoto(null as Bitmap?)
        assertTrue(result is IdentifyPlantResult.Success)
        val success = result as IdentifyPlantResult.Success
        assertEquals("Заміокулькас", success.response.nameUk)
        assertEquals("MEDIUM", success.response.confidence) // sanitized fallback
        assertEquals("SHADE", success.response.lightLevel)
        assertEquals(21, success.response.defaultCheckIntervalDays) // coerced to max 21
        assertEquals("UNKNOWN", success.response.toxicityInfo) // defaulted when empty
    }

    @Test
    fun `identifyPlantFromPhoto returns Error when offline without calling model`() = runBlocking {
        var called = false
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { false },
            generateMultimodal = { _, _ -> called = true; "{}" }
        )

        val result = service.identifyPlantFromPhoto(null as Bitmap?)
        assertFalse(called)
        assertTrue(result is IdentifyPlantResult.Error)
        val error = result as IdentifyPlantResult.Error
        assertTrue(error.isOffline)
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, error.message)
    }

    @Test
    fun `identifyPlantFromPhoto handles QuotaExceeded error`() = runBlocking {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ ->
                throw SdkExceptions.server("RESOURCE_EXHAUSTED: 429 quota exceeded")
            }
        )

        val result = service.identifyPlantFromPhoto(null as Bitmap?)
        assertTrue(result is IdentifyPlantResult.Error)
        val error = result as IdentifyPlantResult.Error
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, error.message)
    }

    @Test
    fun `identifyPlantFromPhoto with File handles missing or empty file`() = runBlocking {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true }
        )

        val nonExistent = File("build/tmp/non_existent_${System.currentTimeMillis()}.jpg")
        val result = service.identifyPlantFromPhoto(nonExistent)
        assertTrue(result is IdentifyPlantResult.Error)
        val error = result as IdentifyPlantResult.Error
        assertTrue(error.message.contains("не знайдено"))
    }

    @Test
    fun `diagnosePlantHealth parses valid HIGH confidence response`() = runBlocking {
        val testPlant = com.flowers.app.data.local.entity.PlantEntity(
            id = 1L,
            customName = "Монстера",
            speciesName = "Монстера делікатесна",
            botanicalName = "Monstera deliciosa"
        )
        val mockJson = """
            {
              "is_plant": true,
              "probable_cause": "Надлишковий полив (перелив)",
              "confidence": "HIGH",
              "visible_symptoms": ["Пожовтіння нижнього листя", "Коричневі вологі плями"],
              "alternative_causes": ["Недостатнє освітлення"],
              "first_aid_steps": [
                "Ізолюйте рослину від інших вазонів для спостереження.",
                "Перевірте вологість ґрунту на глибині 3-4 см перед наступним поливом.",
                "Злийте залишки води з піддона."
              ],
              "follow_up_advice": "Повторно оцініть стан листка через 5 днів.",
              "preliminary_notice": "Попередня оцінка за фотографією. Не замінює лабораторного аналізу."
            }
        """.trimIndent()

        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> mockJson }
        )

        val result = service.diagnosePlantHealth(null as Bitmap?, testPlant)
        assertTrue(result is com.flowers.app.data.remote.DiagnosePlantHealthResult.Success)
        val success = (result as com.flowers.app.data.remote.DiagnosePlantHealthResult.Success).response
        assertTrue(success.isPlant)
        assertEquals("Надлишковий полив (перелив)", success.probableCause)
        assertEquals("HIGH", success.confidence)
        assertEquals(2, success.visibleSymptoms.size)
        assertEquals(3, success.firstAidSteps.size)
        assertTrue(success.firstAidSteps[0].contains("Ізолюйте"))
    }

    @Test
    fun `diagnosePlantHealth with MEDIUM confidence preserves empty alternative causes without hallucination`() = runBlocking {
        val testPlant = com.flowers.app.data.local.entity.PlantEntity(id = 2L, customName = "Фікус")
        val mockJson = """
            {
              "is_plant": true,
              "probable_cause": "Павутинний кліщ",
              "confidence": "MEDIUM",
              "visible_symptoms": ["Дрібні цятки на листі"],
              "alternative_causes": [],
              "first_aid_steps": ["Обережно промийте листя теплою водою"],
              "follow_up_advice": "Огляньте зворотний бік листя через 3 дні",
              "preliminary_notice": "Попередня оцінка за фотографією."
            }
        """.trimIndent()

        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> mockJson }
        )

        val result = service.diagnosePlantHealth(null as Bitmap?, testPlant)
        assertTrue(result is com.flowers.app.data.remote.DiagnosePlantHealthResult.Success)
        val response = (result as com.flowers.app.data.remote.DiagnosePlantHealthResult.Success).response
        assertEquals("MEDIUM", response.confidence)
        // Не вигадуємо альтернативи, якщо модель їх не надала
        assertTrue(response.alternativeCauses.isEmpty())
        assertFalse(response.isSafetySanitized)
    }

    @Test
    fun `parseHealthDiagnosisResponse returns null on empty JSON object or empty array`() {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true }
        )

        assertNull(service.parseHealthDiagnosisResponse("{}"))
        assertNull(service.parseHealthDiagnosisResponse("[]"))
        assertNull(service.parseHealthDiagnosisResponse(""))
        assertNull(service.parseHealthDiagnosisResponse("```json\n{}\n```"))
    }

    @Test
    fun `parseHealthDiagnosisResponse returns null on missing or blank required fields`() {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true }
        )

        // 1. Порожня ймовірна причина
        val jsonNoCause = """
            {
              "is_plant": true,
              "probable_cause": "   ",
              "confidence": "HIGH",
              "first_aid_steps": ["Ізолюйте рослину"],
              "follow_up_advice": "Огляньте через 3 дні"
            }
        """.trimIndent()
        assertNull(service.parseHealthDiagnosisResponse(jsonNoCause))

        // 2. Порожній список дій
        val jsonNoSteps = """
            {
              "is_plant": true,
              "probable_cause": "Хлороз",
              "confidence": "HIGH",
              "first_aid_steps": [],
              "follow_up_advice": "Огляньте через 3 дні"
            }
        """.trimIndent()
        assertNull(service.parseHealthDiagnosisResponse(jsonNoSteps))

        // 3. Список дій з null / некоректними типами
        val jsonInvalidSteps = """
            {
              "is_plant": true,
              "probable_cause": "Хлороз",
              "confidence": "HIGH",
              "first_aid_steps": [null, 123],
              "follow_up_advice": "Огляньте через 3 дні"
            }
        """.trimIndent()
        assertNull(service.parseHealthDiagnosisResponse(jsonInvalidSteps))

        // 4. Відсутня рекомендація повторного огляду
        val jsonNoFollowUp = """
            {
              "is_plant": true,
              "probable_cause": "Хлороз",
              "confidence": "HIGH",
              "first_aid_steps": ["Ізолюйте рослину"],
              "follow_up_advice": "  "
            }
        """.trimIndent()
        assertNull(service.parseHealthDiagnosisResponse(jsonNoFollowUp))
    }

    @Test
    fun `parseHealthDiagnosisResponse maps unknown confidence to LOW`() {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true }
        )

        val jsonUnknownConf = """
            {
              "is_plant": true,
              "probable_cause": "Борошниста роса",
              "confidence": "SUPER_HIGH_CERTAIN",
              "first_aid_steps": ["Ізолюйте рослину"],
              "follow_up_advice": "Огляньте через 5 днів"
            }
        """.trimIndent()

        val parsed = service.parseHealthDiagnosisResponse(jsonUnknownConf)
        assertNotNull(parsed)
        assertEquals("LOW", parsed!!.confidence)
    }

    @Test
    fun `parseHealthDiagnosisResponse activates PlantSafetyGuardrail on toxic chemicals`() {
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true }
        )

        val jsonDangerousChemicals = """
            {
              "is_plant": true,
              "probable_cause": "Попелиця",
              "confidence": "HIGH",
              "first_aid_steps": [
                "Обробіть рослину сильним інсектицидом або дихлофосом",
                "Розведіть мідний купорос та обприскайте листя"
              ],
              "follow_up_advice": "Огляньте через 3 дні"
            }
        """.trimIndent()

        val parsed = service.parseHealthDiagnosisResponse(jsonDangerousChemicals)
        assertNotNull(parsed)
        assertTrue(parsed!!.isSafetySanitized)
        // Замінено на безпечні нейтральні заходи
        assertFalse(parsed.firstAidSteps.any { it.contains("дихлофос") || it.contains("купорос") })
        assertTrue(parsed.firstAidSteps.any { it.contains("Ізолюйте") })
        assertTrue(parsed.preliminaryNotice.contains("Увага: початкові рекомендації містили потенційно небезпечні"))
    }

    @Test
    fun `diagnosePlantHealth with is_plant false returns NotAPlant`() = runBlocking {
        val testPlant = com.flowers.app.data.local.entity.PlantEntity(id = 3L, customName = "Тест")
        val mockJson = """
            {
              "is_plant": false,
              "probable_cause": "На фото не виявлено рослину",
              "confidence": "LOW",
              "visible_symptoms": [],
              "alternative_causes": [],
              "first_aid_steps": [],
              "follow_up_advice": "",
              "preliminary_notice": "Фотографія не містить рослини для аналізу."
            }
        """.trimIndent()

        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ -> mockJson }
        )

        val result = service.diagnosePlantHealth(null as Bitmap?, testPlant)
        assertTrue(result is com.flowers.app.data.remote.DiagnosePlantHealthResult.NotAPlant)
    }

    @Test
    fun `diagnosePlantHealth returns Error when offline without calling model`() = runBlocking {
        var called = false
        val testPlant = com.flowers.app.data.local.entity.PlantEntity(id = 4L, customName = "Тест")
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { false },
            generateMultimodal = { _, _ ->
                called = true
                null
            }
        )

        val result = service.diagnosePlantHealth(null as Bitmap?, testPlant)
        assertFalse(called)
        assertTrue(result is com.flowers.app.data.remote.DiagnosePlantHealthResult.Error)
        val error = result as com.flowers.app.data.remote.DiagnosePlantHealthResult.Error
        assertTrue(error.isOffline)
        assertEquals(AiErrorMapper.MSG_NO_INTERNET, error.message)
    }

    @Test
    fun `diagnosePlantHealth handles 429 quota exceeded`() = runBlocking {
        val testPlant = com.flowers.app.data.local.entity.PlantEntity(id = 5L, customName = "Тест")
        val service = GeminiPlantService(
            catalogDao = failingProxy(),
            carePodDao = failingProxy(),
            networkChecker = NetworkChecker { true },
            generateMultimodal = { _, _ ->
                throw SdkExceptions.server("RESOURCE_EXHAUSTED: 429 quota exceeded")
            }
        )

        val result = service.diagnosePlantHealth(null as Bitmap?, testPlant)
        assertTrue(result is com.flowers.app.data.remote.DiagnosePlantHealthResult.Error)
        val error = result as com.flowers.app.data.remote.DiagnosePlantHealthResult.Error
        assertEquals(AiErrorMapper.MSG_RATE_LIMIT, error.message)
    }
}
