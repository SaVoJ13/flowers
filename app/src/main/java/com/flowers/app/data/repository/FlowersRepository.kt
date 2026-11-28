package com.flowers.app.data.repository

import com.flowers.app.data.image.ChatPhotoAvailability
import com.flowers.app.data.image.ImageSource
import com.flowers.app.data.image.OrphanCleanupReport
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.local.entity.ChatAttachmentEntity
import com.flowers.app.data.local.entity.ChatMessageWithAttachments
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.model.SymptomTreeRoot
import com.flowers.app.data.preferences.UserPreferences
import com.flowers.app.data.remote.DiagnosePlantHealthResult
import com.flowers.app.data.remote.GeneratePlantResult
import com.flowers.app.data.remote.IdentifyPlantResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

interface FlowersRepository {
    val userPreferences: UserPreferences
    fun getAllActivePlants(): Flow<List<PlantEntity>>
    fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>>
    suspend fun getPlantById(plantId: Long): PlantEntity?
    fun getPlantFlowById(plantId: Long): Flow<PlantEntity?>
    suspend fun insertPlant(plant: PlantEntity): Long
    suspend fun updatePlant(plant: PlantEntity)
    suspend fun deletePlant(plant: PlantEntity)

    fun getEventsForPlant(plantId: Long): Flow<List<CareEventEntity>>
    suspend fun getLastWateringEvent(plantId: Long): CareEventEntity?
    suspend fun logCareEvent(event: CareEventEntity): Long

    fun getAllCatalogPlants(): Flow<List<CatalogPlantEntity>>
    fun searchCatalog(query: String): Flow<List<CatalogPlantEntity>>
    suspend fun getCatalogPlantById(speciesId: String): CatalogPlantEntity?

    fun getPodsForSpecies(speciesId: String): Flow<List<CarePodEntity>>
    fun getGeneralPods(): Flow<List<CarePodEntity>>

    suspend fun getSymptomTree(): SymptomTreeRoot

    suspend fun performWatering(plant: PlantEntity, notes: String? = null)
    suspend fun snoozePlant(plant: PlantEntity, days: Int)
    suspend fun skipPlant(plant: PlantEntity)

    suspend fun generatePlantWithAi(plantQuery: String): GeneratePlantResult
    suspend fun identifyPlantFromPhoto(imageFile: File): IdentifyPlantResult
    suspend fun diagnosePlantHealth(imageFile: File, plant: PlantEntity): DiagnosePlantHealthResult
    suspend fun sendPlantChatMessage(
        plantId: Long,
        userQuestion: String,
        language: String = "uk",
        currentMessageId: Long? = null
    ): com.flowers.app.data.remote.PlantChatResult

    fun getChatMessages(plantId: Long): Flow<List<com.flowers.app.data.local.entity.ChatMessageEntity>>
    fun getChatMessagesWithAttachments(plantId: Long): Flow<List<ChatMessageWithAttachments>> = emptyFlow()
    suspend fun getChatMessageById(messageId: Long): com.flowers.app.data.local.entity.ChatMessageEntity?
    suspend fun getChatAttachmentForMessage(messageId: Long): ChatAttachmentEntity? = null
    suspend fun insertChatMessage(message: com.flowers.app.data.local.entity.ChatMessageEntity): Long
    suspend fun saveUserMessageWithPhoto(
        plantId: Long,
        content: String,
        photoSource: ImageSource,
        timestamp: Long = System.currentTimeMillis()
    ): Result<ChatMessageWithAttachments> = Result.failure(UnsupportedOperationException("Not implemented"))
    suspend fun saveUserMessageWithPhoto(
        plantId: Long,
        content: String,
        sourceFile: File,
        timestamp: Long = System.currentTimeMillis()
    ): Result<ChatMessageWithAttachments> = saveUserMessageWithPhoto(plantId, content, ImageSource.FromFile(sourceFile), timestamp)
    fun checkChatPhotoAvailability(filePath: String, expectedPlantId: Long? = null): ChatPhotoAvailability =
        throw UnsupportedOperationException("Not implemented")
    fun getSavedChatPhotoFile(filePath: String, expectedPlantId: Long? = null): Result<File> =
        Result.failure(UnsupportedOperationException("Not implemented"))
    suspend fun <T> withChatPhotoInUse(
        filePath: String,
        expectedPlantId: Long? = null,
        block: suspend (File) -> T
    ): T = throw UnsupportedOperationException("Not implemented")
    suspend fun cleanupOrphanChatPhotos(): OrphanCleanupReport = OrphanCleanupReport()
    suspend fun retryPendingChatPhotoDeletions(): Int = 0
    suspend fun updateChatMessageStatus(
        messageId: Long,
        status: com.flowers.app.data.local.entity.MessageStatus,
        errorMessage: String?
    ): Boolean
    suspend fun saveAssistantResponse(
        userMessageId: Long,
        modelMessage: com.flowers.app.data.local.entity.ChatMessageEntity
    ): Long
    suspend fun beginChatMessageRetry(messageId: Long, plantId: Long): Boolean
    suspend fun clearChatHistory(plantId: Long): Int
    suspend fun recoverStaleSendingMessages(plantId: Long): Int
    fun isPlantChatSending(plantId: Long): Boolean
    suspend fun <T> withChatSending(plantId: Long, block: suspend () -> T): T = block()
}
