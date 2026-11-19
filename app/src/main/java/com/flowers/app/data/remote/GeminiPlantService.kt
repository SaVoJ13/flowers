package com.flowers.app.data.remote

import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private val generativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = GEMINI_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.2f
            }
        )
    }

    suspend fun generatePlantCareCard(
        plantQuery: String
    ): GeneratePlantResult = withContext(Dispatchers.IO) {
        val trimmedQuery = plantQuery.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext GeneratePlantResult.Error("Вкажіть назву рослини для пошуку.")
        }

        val prompt = AiPrompt.plantCareCard(trimmedQuery)

        try {
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: ""
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
                    toxicToPets = false, // не використовується: для ШІ-карток токсичність невідома (TASK-025)
                    isAiGenerated = true
                )

                val podEntity = CarePodEntity(
                    id = "pod_$plantId",
                    plantSpeciesId = plantId,
                    category = "general_rule",
                    titleUk = "Порада догляду: ${generated.popularName}",
                    contentUk = generated.careTip,
                    icon = "psychology"
                )

                catalogDao.insertAll(listOf(catalogEntity))
                carePodDao.insertAll(listOf(podEntity))

                return@withContext GeneratePlantResult.Success(catalogEntity, podEntity)
            } else {
                return@withContext GeneratePlantResult.Error("Не вдалося розпізнати відповідь ШІ для цієї рослини. Спробуйте уточнити назву.")
            }
        } catch (e: Exception) {
            val errorMsg = AiErrorMapper.map(e)
            return@withContext GeneratePlantResult.Error(errorMsg)
        }
    }

    fun parseGeminiResponse(rawText: String): GeneratedPlantResponse? {
        return try {
            val cleaned = rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // If rawText is wrapped in candidates JSON (legacy/REST format or test mock)
            if (cleaned.startsWith("{") && cleaned.contains("\"candidates\"")) {
                val root = gson.fromJson(cleaned, Map::class.java)
                val candidates = root["candidates"] as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String
                if (text != null) {
                    val candidateCleaned = text
                        .trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    return gson.fromJson(candidateCleaned, GeneratedPlantResponse::class.java)
                }
            }

            gson.fromJson(cleaned, GeneratedPlantResponse::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
