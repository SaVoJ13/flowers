package com.flowers.app.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

/**
 * Відповідь моделі на мультимодальне розпізнавання рослини за фото (TASK-053).
 */
data class PlantIdentificationResponse(
    @SerializedName("is_plant")
    val isPlant: Boolean = true,

    @SerializedName("name_uk")
    val nameUk: String = "",

    @SerializedName("name_latin")
    val nameLatin: String = "",

    @SerializedName("confidence")
    val confidence: String = "MEDIUM",

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("light_level")
    val lightLevel: String = "INDIRECT",

    @SerializedName("light")
    val light: String? = null,

    @SerializedName("default_check_interval_days")
    val defaultCheckIntervalDays: Int = 7,

    @SerializedName("watering_advice")
    val wateringAdvice: String? = null,

    @SerializedName("soil_type")
    val soilType: String? = null,

    @SerializedName("toxicity_info")
    val toxicityInfo: String? = null,

    @SerializedName("alternative_species")
    val alternativeSpecies: List<String> = emptyList()
)

sealed class IdentifyPlantResult {
    data class Success(val response: PlantIdentificationResponse) : IdentifyPlantResult()
    data class NotAPlant(
        val message: String = "На фото не виявлено рослину. Спробуйте сфотографувати рослину ближче при кращому освітленні."
    ) : IdentifyPlantResult()
    data class Error(
        val message: String,
        val isOffline: Boolean = false
    ) : IdentifyPlantResult()
}

/**
 * Відповідь моделі на мультимодальну діагностику здоров'я та шкідників рослини (TASK-055).
 */
data class PlantHealthDiagnosisResponse(
    @SerializedName("is_plant")
    val isPlant: Boolean = true,

    @SerializedName("probable_cause")
    val probableCause: String = "",

    @SerializedName("confidence")
    val confidence: String = "MEDIUM",

    @SerializedName("visible_symptoms")
    val visibleSymptoms: List<String> = emptyList(),

    @SerializedName("alternative_causes")
    val alternativeCauses: List<String> = emptyList(),

    @SerializedName("first_aid_steps")
    val firstAidSteps: List<String> = emptyList(),

    @SerializedName("follow_up_advice")
    val followUpAdvice: String = "",

    @SerializedName("preliminary_notice")
    val preliminaryNotice: String = "Попередня оцінка за фотографією. Не замінює очного огляду фахівцем.",

    val isSafetySanitized: Boolean = false
)

sealed class DiagnosePlantHealthResult {
    data class Success(val response: PlantHealthDiagnosisResponse) : DiagnosePlantHealthResult()
    data class NotAPlant(
        val message: String = "На фото не виявлено рослину. Будь ласка, сфотографуйте уражену частину рослини при кращому освітленні."
    ) : DiagnosePlantHealthResult()
    data class Error(
        val message: String,
        val isOffline: Boolean = false
    ) : DiagnosePlantHealthResult()
}

class GeminiPlantService(
    private val catalogDao: CatalogDao,
    private val carePodDao: CarePodDao,
    private val networkChecker: NetworkChecker,
    /** Виклик моделі; за замовчуванням Firebase AI Logic. Підміняється в тестах. */
    private val generateText: suspend (String) -> String? = { prompt -> defaultModel.generateContent(prompt).text },
    /** Мультимодальний виклик моделі з передачею зображення та промпту (TASK-053). */
    private val generateMultimodal: suspend (Bitmap?, String) -> String? = { bitmap, prompt ->
        val inputContent = content {
            if (bitmap != null) {
                image(bitmap)
            }
            text(prompt)
        }
        defaultModel.generateContent(inputContent).text
    }
) {
    private val gson = Gson()

    private companion object {
        val defaultModel by lazy {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = GEMINI_MODEL,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.2f
                }
            )
        }
    }

    suspend fun generatePlantCareCard(
        plantQuery: String
    ): GeneratePlantResult = withContext(Dispatchers.IO) {
        val trimmedQuery = plantQuery.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext GeneratePlantResult.Error("Вкажіть назву рослини для пошуку.")
        }

        // Немає мережі: одразу повідомляємо, без запиту до ШІ.
        if (!networkChecker.isOnline()) {
            return@withContext GeneratePlantResult.Error(AiErrorMapper.MSG_NO_INTERNET)
        }

        val prompt = AiPrompt.plantCareCard(trimmedQuery)

        try {
            val responseText = generateText(prompt) ?: ""
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

    /**
     * Мультимодальне розпізнавання рослини за Bitmap (TASK-053).
     */
    suspend fun identifyPlantFromPhoto(
        bitmap: Bitmap?
    ): IdentifyPlantResult = withContext(Dispatchers.IO) {
        if (!networkChecker.isOnline()) {
            return@withContext IdentifyPlantResult.Error(AiErrorMapper.MSG_NO_INTERNET, isOffline = true)
        }

        val prompt = AiPrompt.identifyPlantFromPhotoPrompt()

        try {
            val responseText = generateMultimodal(bitmap, prompt) ?: ""
            val identified = parseIdentificationResponse(responseText)

            if (identified != null) {
                if (!identified.isPlant) {
                    return@withContext IdentifyPlantResult.NotAPlant()
                }
                return@withContext IdentifyPlantResult.Success(identified)
            } else {
                return@withContext IdentifyPlantResult.Error("Не вдалося розпізнати рослину за фотографією. Спробуйте інший ракурс або введіть дані вручну.")
            }
        } catch (e: Exception) {
            val errorMsg = AiErrorMapper.map(e)
            val isOffline = !networkChecker.isOnline()
            return@withContext IdentifyPlantResult.Error(errorMsg, isOffline = isOffline)
        }
    }

    /**
     * Мультимодальне розпізнавання рослини за файлом фотографії (TASK-053).
     * Декодує файл у Bitmap, виконує запит та негайно звільняє нативну пам'ять (recycle).
     */
    suspend fun identifyPlantFromPhoto(
        imageFile: File,
        decoder: (File) -> Bitmap? = { file -> BitmapFactory.decodeFile(file.absolutePath) }
    ): IdentifyPlantResult = withContext(Dispatchers.IO) {
        if (!imageFile.exists() || imageFile.length() == 0L) {
            return@withContext IdentifyPlantResult.Error("Файл фотографії не знайдено або він порожній.")
        }

        val bitmap = try {
            decoder(imageFile)
        } catch (e: OutOfMemoryError) {
            return@withContext IdentifyPlantResult.Error("Недостатньо оперативної пам'яті для обробки фотографії.")
        } catch (e: Exception) {
            return@withContext IdentifyPlantResult.Error("Не вдалося прочитати файл фотографії: ${e.message}")
        } ?: return@withContext IdentifyPlantResult.Error("Не вдалося декодувати зображення для аналізу.")

        try {
            identifyPlantFromPhoto(bitmap)
        } finally {
            try {
                bitmap.recycle()
            } catch (_: Throwable) {}
        }
    }

    fun parseIdentificationResponse(rawText: String): PlantIdentificationResponse? {
        return try {
            val cleaned = rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val candidateText = if (cleaned.startsWith("{") && cleaned.contains("\"candidates\"")) {
                val root = gson.fromJson(cleaned, Map::class.java)
                val candidates = root["candidates"] as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String
                text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim() ?: cleaned
            } else {
                cleaned
            }

            val parsed = gson.fromJson(candidateText, PlantIdentificationResponse::class.java) ?: return null

            val sanitizedConfidence = when (parsed.confidence.uppercase()) {
                "HIGH" -> "HIGH"
                "LOW" -> "LOW"
                else -> "MEDIUM"
            }

            val sanitizedLightLevel = when (parsed.lightLevel.uppercase()) {
                "SHADE" -> "SHADE"
                "DIRECT" -> "DIRECT"
                else -> "INDIRECT"
            }

            val sanitizedInterval = parsed.defaultCheckIntervalDays.coerceIn(3, 21)

            parsed.copy(
                confidence = sanitizedConfidence,
                lightLevel = sanitizedLightLevel,
                defaultCheckIntervalDays = sanitizedInterval,
                toxicityInfo = parsed.toxicityInfo?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun diagnosePlantHealth(
        bitmap: Bitmap?,
        plant: PlantEntity,
        lastWateredTimestamp: Long? = null
    ): DiagnosePlantHealthResult = withContext(Dispatchers.IO) {
        if (!networkChecker.isOnline()) {
            return@withContext DiagnosePlantHealthResult.Error(
                message = AiErrorMapper.MSG_NO_INTERNET,
                isOffline = true
            )
        }

        val plantSummary = buildString {
            append("Назва: ${plant.customName}")
            if (!plant.speciesName.isNullOrBlank()) append(", Вид: ${plant.speciesName}")
            if (!plant.botanicalName.isNullOrBlank()) append(" (${plant.botanicalName})")
            append(", Освітлення: ${plant.lightLevel}")
            append(", Кімната: ${plant.roomLocation}")
            if (!plant.wateringAdvice.isNullOrBlank()) append(", Рекомендація щодо поливу: ${plant.wateringAdvice}")
            if (lastWateredTimestamp != null) {
                val daysAgo = ((System.currentTimeMillis() - lastWateredTimestamp) / 86400000L).coerceAtLeast(0)
                append(", Останній полив: $daysAgo дн. тому")
            }
        }

        val prompt = AiPrompt.diagnosePlantHealthPrompt(plantSummary)

        try {
            val responseText = generateMultimodal(bitmap, prompt) ?: ""
            if (responseText.isBlank()) {
                return@withContext DiagnosePlantHealthResult.Error("Отримано порожню відповідь від сервісу діагностики.")
            }

            val parsed = parseHealthDiagnosisResponse(responseText)
                ?: return@withContext DiagnosePlantHealthResult.Error(
                    "Не вдалося розпізнати або валідувати результат діагностики. Спробуйте зробити чіткіше фото."
                )

            if (!parsed.isPlant) {
                return@withContext DiagnosePlantHealthResult.NotAPlant(
                    message = parsed.probableCause.ifBlank {
                        "На фото не виявлено рослину. Будь ласка, сфотографуйте уражену частину рослини при кращому освітленні."
                    }
                )
            }

            DiagnosePlantHealthResult.Success(parsed)
        } catch (e: Exception) {
            val mapped = AiErrorMapper.map(e)
            val isOffline = !networkChecker.isOnline()
            DiagnosePlantHealthResult.Error(mapped, isOffline = isOffline)
        }
    }

    suspend fun diagnosePlantHealth(
        imageFile: File,
        plant: PlantEntity,
        lastWateredTimestamp: Long? = null,
        decoder: (File) -> Bitmap? = { BitmapFactory.decodeFile(it.absolutePath) }
    ): DiagnosePlantHealthResult = withContext(Dispatchers.IO) {
        if (!imageFile.exists() || !imageFile.isFile) {
            return@withContext DiagnosePlantHealthResult.Error("Файл фотографії не знайдено.")
        }

        val bitmap = try {
            decoder(imageFile)
        } catch (e: OutOfMemoryError) {
            return@withContext DiagnosePlantHealthResult.Error("Недостатньо оперативної пам'яті для обробки фотографії.")
        } catch (e: Exception) {
            return@withContext DiagnosePlantHealthResult.Error("Не вдалося прочитати файл фотографії: ${e.message}")
        } ?: return@withContext DiagnosePlantHealthResult.Error("Не вдалося декодувати зображення для аналізу.")

        try {
            diagnosePlantHealth(bitmap, plant, lastWateredTimestamp)
        } finally {
            try {
                bitmap.recycle()
            } catch (_: Throwable) {}
        }
    }

    fun parseHealthDiagnosisResponse(rawText: String): PlantHealthDiagnosisResponse? {
        return try {
            val cleaned = rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            if (cleaned.isBlank() || cleaned == "{}" || cleaned == "[]") {
                return null
            }

            val candidateText = if (cleaned.startsWith("{") && cleaned.contains("\"candidates\"")) {
                val root = gson.fromJson(cleaned, Map::class.java)
                val candidates = root["candidates"] as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String
                text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim() ?: cleaned
            } else {
                cleaned
            }

            if (candidateText.isBlank() || candidateText == "{}" || candidateText == "[]") {
                return null
            }

            // Сувора валідація JSON: розбір через JsonParser для перевірки типів і відсутності обов'язкових полів
            val jsonElement = com.google.gson.JsonParser.parseString(candidateText)
            if (!jsonElement.isJsonObject) {
                return null
            }
            val jsonObject = jsonElement.asJsonObject
            if (jsonObject.size() == 0) {
                return null
            }

            val isPlantElement = jsonObject.get("is_plant")
            val isPlant = if (isPlantElement != null && isPlantElement.isJsonPrimitive && isPlantElement.asJsonPrimitive.isBoolean) {
                isPlantElement.asBoolean
            } else {
                true
            }

            // Якщо на фото не виявлено рослину
            if (!isPlant) {
                val cause = jsonObject.get("probable_cause")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                return PlantHealthDiagnosisResponse(
                    isPlant = false,
                    probableCause = cause.ifBlank { "На фотографії не розпізнано рослину." },
                    confidence = "LOW",
                    visibleSymptoms = emptyList(),
                    alternativeCauses = emptyList(),
                    firstAidSteps = emptyList(),
                    followUpAdvice = "",
                    preliminaryNotice = "На фотографії не розпізнано рослину."
                )
            }

            // 1. Обов'язкове поле: ймовірна причина (не порожній рядок)
            val probableCauseElement = jsonObject.get("probable_cause")
            if (probableCauseElement == null || !probableCauseElement.isJsonPrimitive || !probableCauseElement.asJsonPrimitive.isString) {
                return null
            }
            val probableCause = probableCauseElement.asString.trim()
            if (probableCause.isBlank()) {
                return null
            }

            // 2. Обов'язкове поле: рекомендація повторного огляду (не порожній рядок)
            val followUpElement = jsonObject.get("follow_up_advice")
            if (followUpElement == null || !followUpElement.isJsonPrimitive || !followUpElement.asJsonPrimitive.isString) {
                return null
            }
            val followUpAdvice = followUpElement.asString.trim()
            if (followUpAdvice.isBlank()) {
                return null
            }

            // 3. Обов'язкове поле: перша допомога (непустий масив валідних рядків)
            val firstAidElement = jsonObject.get("first_aid_steps")
            if (firstAidElement == null || !firstAidElement.isJsonArray) {
                return null
            }
            val rawFirstAidArray = firstAidElement.asJsonArray
            if (rawFirstAidArray.size() == 0) {
                return null
            }
            val rawFirstAidSteps = mutableListOf<String>()
            for (element in rawFirstAidArray) {
                if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
                    return null // елемент не є рядком (наприклад, null або вкладений об'єкт)
                }
                val step = element.asString.trim()
                if (step.isNotBlank()) {
                    rawFirstAidSteps.add(step)
                }
            }
            if (rawFirstAidSteps.isEmpty()) {
                return null
            }

            // 4. Валідація видимих симптомів
            val visibleSymptoms = mutableListOf<String>()
            val symptomsElement = jsonObject.get("visible_symptoms")
            if (symptomsElement != null && symptomsElement.isJsonArray) {
                for (element in symptomsElement.asJsonArray) {
                    if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        val sym = element.asString.trim()
                        if (sym.isNotBlank()) {
                            visibleSymptoms.add(sym)
                        }
                    }
                }
            }

            // 5. Валідація альтернативних причин (без вигадування штучних причин)
            val alternativeCauses = mutableListOf<String>()
            val altElement = jsonObject.get("alternative_causes")
            if (altElement != null && altElement.isJsonArray) {
                for (element in altElement.asJsonArray) {
                    if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        val alt = element.asString.trim()
                        if (alt.isNotBlank()) {
                            alternativeCauses.add(alt)
                        }
                    }
                }
            }

            // 6. Валідація confidence: невідоме значення не підвищується до MEDIUM
            val rawConfidence = jsonObject.get("confidence")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.uppercase()
            val sanitizedConfidence = when (rawConfidence) {
                "HIGH" -> "HIGH"
                "MEDIUM" -> "MEDIUM"
                "LOW" -> "LOW"
                "UNCERTAIN" -> "LOW"
                else -> "LOW" // Будь-яке невідоме значення чи відсутність поля maps to LOW
            }

            // 7. Попередження
            val preliminaryNotice = jsonObject.get("preliminary_notice")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank {
                "Попередня оцінка за фотографією. Не замінює очного огляду фахівцем."
            } ?: "Попередня оцінка за фотографією. Не замінює очного огляду фахівцем."

            // 8. Захисний шар безпеки (Safety Guardrail)
            val (sanitizedSteps, isSanitized) = PlantSafetyGuardrail.sanitizeFirstAidSteps(rawFirstAidSteps)
            val finalNotice = if (isSanitized) {
                "$preliminaryNotice\n${PlantSafetyGuardrail.SAFETY_NOTICE_MODIFIED}"
            } else {
                preliminaryNotice
            }

            PlantHealthDiagnosisResponse(
                isPlant = true,
                probableCause = probableCause,
                confidence = sanitizedConfidence,
                visibleSymptoms = visibleSymptoms,
                alternativeCauses = alternativeCauses,
                firstAidSteps = sanitizedSteps,
                followUpAdvice = followUpAdvice,
                preliminaryNotice = finalNotice,
                isSafetySanitized = isSanitized
            )
        } catch (_: Exception) {
            null
        }
    }
}
