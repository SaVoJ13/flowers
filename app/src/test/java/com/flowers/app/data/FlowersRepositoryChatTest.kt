package com.flowers.app.data

import android.content.Context
import android.content.ContextWrapper
import com.flowers.app.data.local.dao.CareEventDao
import com.flowers.app.data.local.dao.CarePodDao
import com.flowers.app.data.local.dao.CatalogDao
import com.flowers.app.data.local.dao.ChatMessageDao
import com.flowers.app.data.local.dao.PlantDao
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.PlantEntity
import com.flowers.app.data.remote.GeminiPlantService
import com.flowers.app.data.remote.PlantChatResult
import com.flowers.app.data.repository.FlowersRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class FlowersRepositoryChatTest {

    private open class FakePlantDao : PlantDao() {
        var plantToReturn: PlantEntity? = null
        var getPlantByIdCalled = false

        override fun getAllActivePlants(): Flow<List<PlantEntity>> = emptyFlow()
        override fun getPlantsDueForCheck(endOfDayTimestamp: Long): Flow<List<PlantEntity>> = emptyFlow()
        override suspend fun getPlantById(plantId: Long): PlantEntity? {
            getPlantByIdCalled = true
            return plantToReturn
        }
        override fun getPlantFlowById(plantId: Long): Flow<PlantEntity?> = emptyFlow()
        override suspend fun insertPlant(plant: PlantEntity): Long = 1L
        override suspend fun updatePlant(plant: PlantEntity) {}
        override suspend fun deletePlant(plant: PlantEntity) {}
        override suspend fun deletePlantById(plantId: Long) {}
        override suspend fun insertCareEvent(event: CareEventEntity): Long = 1L
        override suspend fun updateWateringDates(plantId: Long, lastWatered: Long, nextCheck: Long): Int = 1
        override suspend fun updateNextCheckDate(plantId: Long, nextCheck: Long): Int = 1
    }

    private lateinit var plantDao: FakePlantDao
    private lateinit var careEventDao: CareEventDao
    private lateinit var chatMessageDao: ChatMessageDao
    private lateinit var catalogDao: CatalogDao
    private lateinit var carePodDao: CarePodDao
    private lateinit var context: Context

    private inline fun <reified T : Any> stubProxy(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            if (m.name == "getPlantsCount") 1 else null
        } as T

    @Before
    fun setUp() {
        plantDao = FakePlantDao()
        careEventDao = stubProxy()
        chatMessageDao = stubProxy()
        catalogDao = stubProxy()
        carePodDao = stubProxy()
        context = object : ContextWrapper(null) {}
    }

    private fun testPlant(id: Long = 1L) = PlantEntity(
        id = id,
        customName = "Фікус",
        roomLocation = "Зал",
        lightLevel = "BRIGHT",
        checkIntervalDays = 5
    )

    @Test
    fun `blankQuestion returnsServiceErrorWithoutQueryingDb`() = runBlocking {
        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(catalogDao, carePodDao, object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            })
        )

        val result = repo.sendPlantChatMessage(1L, "   ", "uk")
        assertTrue(result is PlantChatResult.ServiceError)
        assertEquals("Запитання не може бути порожнім.", (result as PlantChatResult.ServiceError).message)
        assertFalse("plantDao не мав викликатися", plantDao.getPlantByIdCalled)
    }

    @Test
    fun `plantNotFound returnsServiceError`() = runBlocking {
        plantDao.plantToReturn = null

        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = GeminiPlantService(catalogDao, carePodDao, object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            })
        )

        val result = repo.sendPlantChatMessage(999L, "Чи потрібен полив?", "uk")
        assertTrue(result is PlantChatResult.ServiceError)
        assertTrue((result as PlantChatResult.ServiceError).message.contains("не знайдено"))
        assertTrue("plantDao мав бути опитаний", plantDao.getPlantByIdCalled)
    }

    @Test
    fun `validQuestion delegatesToGeminiPlantService`() = runBlocking {
        plantDao.plantToReturn = testPlant(1L)

        careEventDao = Proxy.newProxyInstance(CareEventDao::class.java.classLoader, arrayOf(CareEventDao::class.java)) { _, m, _ ->
            if (m.name == "getRecentEventsForPlant") emptyList<CareEventEntity>() else null
        } as CareEventDao

        chatMessageDao = Proxy.newProxyInstance(ChatMessageDao::class.java.classLoader, arrayOf(ChatMessageDao::class.java)) { _, m, _ ->
            if (m.name == "getLastSentMessages") emptyList<ChatMessageEntity>() else null
        } as ChatMessageDao

        val geminiService = GeminiPlantService(
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            networkChecker = object : com.flowers.app.data.remote.NetworkChecker {
                override fun isOnline(): Boolean = true
            },
            generateChatText = { _, _ -> "Фікус почувається чудово." }
        )

        val repo = FlowersRepositoryImpl(
            plantDao = plantDao,
            careEventDao = careEventDao,
            catalogDao = catalogDao,
            carePodDao = carePodDao,
            context = context,
            chatMessageDao = chatMessageDao,
            geminiPlantService = geminiService
        )

        val result = repo.sendPlantChatMessage(1L, "Як справи?", "uk")
        assertTrue(result is PlantChatResult.Success)
        assertEquals("Фікус почувається чудово.", (result as PlantChatResult.Success).reply)
    }
}
