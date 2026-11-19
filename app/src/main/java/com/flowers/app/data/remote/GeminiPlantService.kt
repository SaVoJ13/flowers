package com.flowers.app.data.remote

import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GeneratedPlantResponse(
    @SerializedName("name_uk")
    val nameUk: String,

    @SerializedName("name_latin")
    val nameLatin: String,

    @SerializedName("popular_name")
    val popularName: String,

    @SerializedName("difficulty")
    val difficulty: String,

    @SerializedName("light")
    val light: String,

    @SerializedName("default_check_interval_days")
    val defaultCheckIntervalDays: Int,

    @SerializedName("soil_type")
    val soilType: String,

    @SerializedName("toxic_to_pets")
    val toxicToPets: Boolean,

    @SerializedName("care_tip")
    val careTip: String
)

sealed class GeneratePlantResult {
    data class Success(val plant: CatalogPlantEntity, val pod: CarePodEntity) : GeneratePlantResult()
    data class Error(val message: String) : GeneratePlantResult()
}

class GeminiPlantService(
    private val catalogDao: CatalogDao,
    private val carePodDao: CarePodDao
) {
    private val gson = Gson()

    suspend fun generatePlantCareCard(
        plantQuery: String,
        apiKey: String
    ): GeneratePlantResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext GeneratePlantResult.Error(
                "API-ключ Google Gemini не вказано. Будь ласка, вкажіть його у вкладці «Налаштування»."
            )
        }

        val prompt = """
            Ти досвідчений ботанік та експерт із догляду за кімнатними рослинами.
            Склади точну, структуровану картку догляду для кімнатної рослини: «$plantQuery».
            Поверни виключно валідний JSON-об'єкт із такими полями (без жодного markdown-обрамлення):
            {
              "name_uk": "офіційна українська назва виду",
              "name_latin": "ботанічна латинська назва (напр. Monstera deliciosa)",
              "popular_name": "народна/популярна українська назва",
              "difficulty": "Дуже легко / Легко / Помірно / Складно",
              "light": "рекомендації щодо світла українською мовою",
              "default_check_interval_days": ціле число днів від 3 до 21 для перевірки вологості ґрунту (сукуленти 12-14, помірні 6-8, вологолюбні 4-5),
              "soil_type": "склад субстрату та дренажу українською мовою",
              "toxic_to_pets": true або false (чи токсична для котів/собак при жуванні),
              "care_tip": "головна практична порада новачку для уникнення типових помилок (1-2 речення українською)"
            }
        """.trimIndent()

        var lastErrorMessage = ""

        // Єдина модель з GeminiConfig; ключ передається в заголовку, а не в URL (щоб не потрапляв у логи).
        run {
            try {
                val url = URL(geminiGenerateContentUrl())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("x-goog-api-key", trimmedKey)
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.doOutput = true

                val requestJson = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": ${gson.toJson(prompt)}
                            }
                          ]
                        }
                      ],
                      "generationConfig": {
                        "responseMimeType": "application/json",
                        "temperature": 0.2
                      }
                    }
                """.trimIndent()

                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestJson) }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val generated = parseGeminiResponse(responseText)

                    if (generated != null) {
                        val plantId = "ai_" + System.currentTimeMillis() + "_" + generated.nameLatin.lowercase()
                            .replace(" ", "_")
                            .filter { it.isLetterOrDigit() || it == '_' }

                        val catalogEntity = CatalogPlantEntity(
                            id = plantId,
                            nameUk = generated.nameUk,
                            nameLatin = generated.nameLatin,
                            popularName = generated.popularName,
                            difficulty = generated.difficulty,
                            light = generated.light,
                            defaultCheckIntervalDays = generated.defaultCheckIntervalDays.coerceIn(3, 28),
                            soilType = generated.soilType,
                            toxicToPets = generated.toxicToPets
                        )

                        val podEntity = CarePodEntity(
                            id = "pod_$plantId",
                            plantSpeciesId = plantId,
                            category = "general_rule",
                            titleUk = "Порада догляду: ${generated.popularName}",
                            contentUk = generated.careTip,
                            icon = "psychology"
                        )

                        // Save to Room database so it is permanently cached and available offline
                        catalogDao.insertAll(listOf(catalogEntity))
                        carePodDao.insertAll(listOf(podEntity))

                        return@withContext GeneratePlantResult.Success(catalogEntity, podEntity)
                    }
                } else {
                    val errorStream = conn.errorStream
                    val errorBody = errorStream?.let { BufferedReader(InputStreamReader(it, "UTF-8")).use { r -> r.readText() } } ?: ""
                    lastErrorMessage = "Код помилки $responseCode: $errorBody"
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: e.javaClass.simpleName
            }
        }

        GeneratePlantResult.Error("Не вдалося згенерувати картку догляду: $lastErrorMessage")
    }

    private fun parseGeminiResponse(rawJson: String): GeneratedPlantResponse? {
        return try {
            val root = gson.fromJson(rawJson, Map::class.java)
            val candidates = root["candidates"] as? List<*> ?: return null
            val firstCandidate = candidates.firstOrNull() as? Map<*, *> ?: return null
            val content = firstCandidate["content"] as? Map<*, *> ?: return null
            val parts = content["parts"] as? List<*> ?: return null
            val firstPart = parts.firstOrNull() as? Map<*, *> ?: return null
            val text = firstPart["text"] as? String ?: return null

            val cleanedText = text
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            gson.fromJson(cleanedText, GeneratedPlantResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
