package com.flowers.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** TASK-056, крок 1: поведінка ChatMessageDao та взаємодія з plants на ізольованій in-memory базі. */
@RunWith(AndroidJUnit4::class)
class ChatMessageDaoTest {

    private lateinit var db: FlowersDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlowersDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun plant(name: String = "Рослина"): Long =
        db.plantDao().insertPlant(PlantEntity(customName = name, nextCheckDate = 1000L))

    private fun msg(
        plantId: Long,
        content: String,
        ts: Long = 100,
        role: MessageRole = MessageRole.USER,
        status: MessageStatus = MessageStatus.SENT
    ) = ChatMessageEntity(plantId = plantId, role = role, content = content, status = status, timestamp = ts)

    @Test
    fun rolesAndStatuses_roundTripAndAreStoredAsNameStrings() = runBlocking {
        val p = plant()
        val dao = db.chatMessageDao()
        dao.insertMessage(msg(p, "q", 1, MessageRole.USER, MessageStatus.SENDING))
        dao.insertMessage(msg(p, "a", 2, MessageRole.MODEL, MessageStatus.SENT))
        dao.insertMessage(msg(p, "e", 3, MessageRole.USER, MessageStatus.ERROR).copy(errorMessage = "Немає інтернету"))
        val all = dao.getMessages(p)
        assertEquals(listOf(MessageRole.USER, MessageRole.MODEL, MessageRole.USER), all.map { it.role })
        assertEquals(listOf(MessageStatus.SENDING, MessageStatus.SENT, MessageStatus.ERROR), all.map { it.status })
        assertEquals("Немає інтернету", all[2].errorMessage)
        db.openHelper.readableDatabase.query("SELECT role, status FROM chat_messages ORDER BY id").use {
            assertTrue(it.moveToFirst())
            assertEquals("USER", it.getString(0))
            assertEquals("SENDING", it.getString(1))
            it.moveToNext()
            assertEquals("MODEL", it.getString(0))
            assertEquals("SENT", it.getString(1))
        }
    }

    @Test
    fun chatsOfTwoPlantsAreIsolated() = runBlocking {
        val a = plant("A")
        val b = plant("B")
        val dao = db.chatMessageDao()
        dao.insertMessage(msg(a, "a1", 1))
        dao.insertMessage(msg(b, "b1", 2))
        dao.insertMessage(msg(a, "a2", 3))
        assertEquals(listOf("a1", "a2"), dao.getMessages(a).map { it.content })
        assertEquals(listOf("b1"), dao.getMessages(b).map { it.content })
        assertEquals(listOf("a1", "a2"), dao.observeMessages(a).first().map { it.content })
        assertEquals(listOf("a2"), dao.getLastMessages(a, 1).map { it.content })
        assertEquals(listOf("b1"), dao.getLastMessages(b, 6).map { it.content })
    }

    @Test
    fun sameTimestamp_orderIsDeterministicByIdAndLastNWindowIsChronological() = runBlocking {
        val p = plant()
        val dao = db.chatMessageDao()
        val ids = (1..8).map { dao.insertMessage(msg(p, "m$it", ts = 500)) }
        assertEquals(ids.sorted(), dao.getMessages(p).map { it.id })
        assertEquals((1..8).map { "m$it" }, dao.getMessages(p).map { it.content })
        // Останні 6 з однаковими timestamp: m3..m8 у хронологічному порядку.
        assertEquals((3..8).map { "m$it" }, dao.getLastMessages(p, 6).map { it.content })
        assertEquals((3..8).map { "m$it" }, dao.getLastMessages(p, 6).map { it.content })
    }

    @Test
    fun lastMessages_withMixedTimestamps_returnsNewestWindowInOrder() = runBlocking {
        val p = plant()
        val dao = db.chatMessageDao()
        dao.insertMessage(msg(p, "late", ts = 300))
        dao.insertMessage(msg(p, "early", ts = 100))
        dao.insertMessage(msg(p, "mid", ts = 200))
        assertEquals(listOf("early", "mid", "late"), dao.getMessages(p).map { it.content })
        assertEquals(listOf("mid", "late"), dao.getLastMessages(p, 2).map { it.content })
    }

    @Test
    fun failedSend_retryUsesSameId_noDuplicates_andIsIdempotent() = runBlocking {
        val p = plant()
        val dao = db.chatMessageDao()
        val id = dao.insertMessage(msg(p, "питання", 1, status = MessageStatus.SENDING))
        assertEquals(1, dao.updateStatus(id, MessageStatus.ERROR, "Таймаут"))
        assertEquals(MessageStatus.ERROR, dao.getById(id)!!.status)
        assertEquals("Таймаут", dao.getById(id)!!.errorMessage)

        assertEquals(1, dao.beginRetry(id))
        assertEquals(0, dao.beginRetry(id)) // повторний тап: нічого не змінено
        val retrying = dao.getById(id)!!
        assertEquals(MessageStatus.SENDING, retrying.status)
        assertNull(retrying.errorMessage)

        assertEquals(1, dao.updateStatus(id, MessageStatus.SENT, null))
        val all = dao.getMessages(p)
        assertEquals(1, all.size)
        assertEquals("питання", all[0].content)
        assertEquals(MessageStatus.SENT, all[0].status)
        assertEquals(0, dao.updateStatus(9999L, MessageStatus.SENT, null))
        assertEquals(0, dao.beginRetry(9999L))
    }

    @Test
    fun interruptedSending_isRecoveredAsErrorOnRestart_withoutTouchingOthers() = runBlocking {
        val a = plant("A")
        val b = plant("B")
        val dao = db.chatMessageDao()
        val s1 = dao.insertMessage(msg(a, "pending-a", 1, status = MessageStatus.SENDING))
        val s2 = dao.insertMessage(msg(b, "pending-b", 2, status = MessageStatus.SENDING))
        val ok = dao.insertMessage(msg(a, "done", 3, status = MessageStatus.SENT))
        assertEquals(2, dao.markInterruptedSendingAsError("Відправку перервано"))
        assertEquals(MessageStatus.ERROR, dao.getById(s1)!!.status)
        assertEquals("Відправку перервано", dao.getById(s1)!!.errorMessage)
        assertEquals(MessageStatus.ERROR, dao.getById(s2)!!.status)
        assertEquals(MessageStatus.SENT, dao.getById(ok)!!.status)
        assertEquals(0, dao.markInterruptedSendingAsError("x"))
        // Після відновлення повтор за тим самим id працює.
        assertEquals(1, dao.beginRetry(s1))
        assertEquals(2, dao.getMessages(a).size)
    }

    @Test
    fun insertWithExistingId_abortsInsteadOfSilentlyReplacing() = runBlocking {
        val p = plant()
        val dao = db.chatMessageDao()
        val id = dao.insertMessage(msg(p, "оригінал", 1))
        try {
            dao.insertMessage(msg(p, "підміна", 2).copy(id = id))
            fail("Очікувалось SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
        }
        assertEquals("оригінал", dao.getById(id)!!.content)
        assertEquals(1, dao.getMessages(p).size)
    }

    @Test
    fun insertForMissingPlant_isRejectedByForeignKey() = runBlocking {
        try {
            db.chatMessageDao().insertMessage(msg(424242L, "сирота"))
            fail("Очікувалось SQLiteConstraintException (FOREIGN KEY)")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun deletingPlant_cascadesOnlyItsChat_andDeletingOneChatLeavesOthers() = runBlocking {
        val a = plant("A")
        val b = plant("B")
        val c = plant("C")
        val dao = db.chatMessageDao()
        listOf(a, b, c).forEach { dao.insertMessage(msg(it, "hi-$it", 1)) }
        db.careEventDao().insertEvent(CareEventEntity(plantId = b, eventType = "WATER", timestamp = 1))

        // Видалення історії лише однієї рослини.
        assertEquals(1, dao.deleteAllForPlant(a))
        assertTrue(dao.getMessages(a).isEmpty())
        assertEquals(1, dao.getMessages(b).size)
        assertEquals(1, dao.getMessages(c).size)
        assertNotNull(db.plantDao().getPlantById(a)) // рослина лишилась

        // Каскад при видаленні рослини B: лише її чат (і її події, як і раніше).
        db.plantDao().deletePlantById(b)
        assertTrue(dao.getMessages(b).isEmpty())
        assertEquals(1, dao.getMessages(c).size)
        assertNotNull(db.plantDao().getPlantById(c))
        assertEquals(0, db.careEventDao().getEventsForPlant(b).first().size)
    }

    /**
     * TASK-056-SAFETY-01: PlantDao.insertPlant має OnConflictStrategy.ABORT.
     * Запобігає каскадному видаленню (DELETE + INSERT) дочірніх care_events та chat_messages
     * при випадковій повторній вставці рослини з уже наявним ID.
     *
     * Перевіряє:
     * 1. Створення нової рослини успішне (plantId > 0).
     * 2. Повторна вставка з наявним ID викликає SQLiteConstraintException (ABORT).
     * 3. Назва та інші поля існуючої рослини залишаються незмінними.
     * 4. Усі події догляду (WATER, DELAY, HEALTH_CHECK) залишаються незмінними.
     * 5. Усі повідомлення чату (USER, MODEL) залишаються незмінними.
     * 6. updatePlant() дозволяє штатно редагувати рослину без втрати дочірніх записів.
     * 7. Історія та стан інших рослин залишаються повністю недоторканими.
     */
    @Test
    fun abortOnDuplicateInsertPlant_preservesCascadeData_andUpdatePlantIsSafe() = runBlocking {
        // 1. Створення двох окремих рослин
        val p1 = plant("Оригінал 1")
        val p2 = plant("Оригінал 2")
        assertTrue(p1 > 0)
        assertTrue(p2 > 0)

        val chatDao = db.chatMessageDao()
        val careDao = db.careEventDao()
        val plantDao = db.plantDao()

        // Додаємо повний набір подій та повідомлень для p1
        careDao.insertEvent(CareEventEntity(plantId = p1, eventType = "WATER", timestamp = 100, notes = "Полив 1"))
        careDao.insertEvent(CareEventEntity(plantId = p1, eventType = "DELAY", timestamp = 200, daysDelayed = 2))
        careDao.insertEvent(CareEventEntity(plantId = p1, eventType = "HEALTH_CHECK", timestamp = 300, notes = "Діагностика", photoUri = "file:///tmp/leaf.jpg"))
        chatDao.insertMessage(msg(p1, "Питання користувача", 150, MessageRole.USER, MessageStatus.SENT))
        chatDao.insertMessage(msg(p1, "Відповідь моделі", 250, MessageRole.MODEL, MessageStatus.SENT))

        // Додаємо дані для p2 (контрольна рослина)
        careDao.insertEvent(CareEventEntity(plantId = p2, eventType = "WATER", timestamp = 110, notes = "Полив p2"))
        chatDao.insertMessage(msg(p2, "Чат рослини 2", 160, MessageRole.USER, MessageStatus.SENT))

        // 6. Штатне редагування через updatePlant() не чіпає дочірні записи
        val originalP1 = plantDao.getPlantById(p1)!!
        plantDao.updatePlant(originalP1.copy(customName = "Оновлена назва 1", roomLocation = "Спальня"))

        val p1AfterUpdate = plantDao.getPlantById(p1)!!
        assertEquals("Оновлена назва 1", p1AfterUpdate.customName)
        assertEquals("Спальня", p1AfterUpdate.roomLocation)
        assertEquals(3, careDao.getEventsForPlant(p1).first().size)
        assertEquals(2, chatDao.getMessages(p1).size)

        // 2. Спроба повторної вставки з наявним ID повинна викликати SQLiteConstraintException (ABORT)
        try {
            plantDao.insertPlant(originalP1.copy(customName = "Спроба перезапису"))
            fail("Очікувалось SQLiteConstraintException через OnConflictStrategy.ABORT")
        } catch (_: SQLiteConstraintException) {
            // Очікуваний конфлікт первинного ключа
        }

        // 3. Назва та інші поля існуючої рослини залишилися незмінними
        val p1AfterFailedInsert = plantDao.getPlantById(p1)!!
        assertEquals("Оновлена назва 1", p1AfterFailedInsert.customName)
        assertEquals("Спальня", p1AfterFailedInsert.roomLocation)
        assertEquals(originalP1.nextCheckDate, p1AfterFailedInsert.nextCheckDate)

        // 4. Події WATER, DELAY, HEALTH_CHECK залишилися збереженими з усіма полями
        val eventsP1 = careDao.getEventsForPlant(p1).first()
        assertEquals(3, eventsP1.size)
        val waterEvent = eventsP1.first { it.eventType == "WATER" }
        assertEquals(100L, waterEvent.timestamp)
        assertEquals("Полив 1", waterEvent.notes)

        val delayEvent = eventsP1.first { it.eventType == "DELAY" }
        assertEquals(200L, delayEvent.timestamp)
        assertEquals(2, delayEvent.daysDelayed)

        val healthEvent = eventsP1.first { it.eventType == "HEALTH_CHECK" }
        assertEquals(300L, healthEvent.timestamp)
        assertEquals("Діагностика", healthEvent.notes)
        assertEquals("file:///tmp/leaf.jpg", healthEvent.photoUri)

        // 5. Повідомлення USER та MODEL залишилися збереженими
        val messagesP1 = chatDao.getMessages(p1)
        assertEquals(2, messagesP1.size)
        assertEquals(MessageRole.USER, messagesP1[0].role)
        assertEquals("Питання користувача", messagesP1[0].content)
        assertEquals(MessageRole.MODEL, messagesP1[1].role)
        assertEquals("Відповідь моделі", messagesP1[1].content)

        // 7. Контрольна рослина p2 та її історія повністю недоторкані
        val plantP2 = plantDao.getPlantById(p2)!!
        assertEquals("Оригінал 2", plantP2.customName)
        val eventsP2 = careDao.getEventsForPlant(p2).first()
        assertEquals(1, eventsP2.size)
        assertEquals("Полив p2", eventsP2[0].notes)
        val messagesP2 = chatDao.getMessages(p2)
        assertEquals(1, messagesP2.size)
        assertEquals("Чат рослини 2", messagesP2[0].content)
    }

    @Test
    fun saveAssistantResponse_atomicallyUpdatesUserToSentAndInsertsModel() = runBlocking {
        val p = plant("Фікус")
        val dao = db.chatMessageDao()

        val userId = dao.insertMessage(msg(p, "Як доглядати?", 100, MessageRole.USER, MessageStatus.SENDING))
        val modelMsg = msg(p, "Потрібен помірний полив.", 200, MessageRole.MODEL, MessageStatus.SENT)

        val modelId = dao.saveAssistantResponse(userId, modelMsg)
        assertTrue(modelId > 0)

        val userAfter = dao.getById(userId)
        assertNotNull(userAfter)
        assertEquals(MessageStatus.SENT, userAfter!!.status)
        assertNull(userAfter.errorMessage)

        val modelAfter = dao.getById(modelId)
        assertNotNull(modelAfter)
        assertEquals(MessageRole.MODEL, modelAfter!!.role)
        assertEquals(MessageStatus.SENT, modelAfter.status)
        assertEquals("Потрібен помірний полив.", modelAfter.content)

        // Повторний виклик не повинен створювати другу відповідь
        val duplicateModelId = dao.saveAssistantResponse(userId, modelMsg)
        assertEquals(-1L, duplicateModelId)
        assertEquals(1, dao.getMessages(p).count { it.role == MessageRole.MODEL })

        // Виклик для неіснуючого id повертає -1
        val invalidId = dao.saveAssistantResponse(99999L, modelMsg)
        assertEquals(-1L, invalidId)

        // Виклик для повідомлення зі статусом ERROR повертає -1L і залишає статус ERROR
        val errorMsgId = dao.insertMessage(msg(p, "Помилка", 300, MessageRole.USER, MessageStatus.ERROR))
        val errorSaveResult = dao.saveAssistantResponse(errorMsgId, modelMsg)
        assertEquals(-1L, errorSaveResult)
        assertEquals(MessageStatus.ERROR, dao.getById(errorMsgId)!!.status)

        // Виклик для повідомлення ролі MODEL повертає -1L
        val modelSourceId = dao.insertMessage(msg(p, "Відповідь асистента", 400, MessageRole.MODEL, MessageStatus.SENDING))
        val modelSaveResult = dao.saveAssistantResponse(modelSourceId, modelMsg)
        assertEquals(-1L, modelSaveResult)
    }

    @Test
    fun beginRetryForPlant_onlyAllowsRetryForSamePlantAndStatusError() = runBlocking {
        val p1 = plant("Рослина 1")
        val p2 = plant("Рослина 2")
        val dao = db.chatMessageDao()

        val m1 = dao.insertMessage(msg(p1, "Помилка 1", 100, MessageRole.USER, MessageStatus.ERROR))
        val m2 = dao.insertMessage(msg(p2, "Помилка 2", 200, MessageRole.USER, MessageStatus.ERROR))

        // Спроба повторити повідомлення рослини 2 з рослини 1 повертає 0
        val crossPlantRows = dao.beginRetryForPlant(m2, p1)
        assertEquals(0, crossPlantRows)
        assertEquals(MessageStatus.ERROR, dao.getById(m2)!!.status)

        // Повтор власного повідомлення повертає 1 та переводить у SENDING
        val ownRows = dao.beginRetryForPlant(m1, p1)
        assertEquals(1, ownRows)
        assertEquals(MessageStatus.SENDING, dao.getById(m1)!!.status)

        // Повторний виклик повертає 0 (бо вже SENDING)
        val secondCall = dao.beginRetryForPlant(m1, p1)
        assertEquals(0, secondCall)
    }

    @Test
    fun markSendingAsErrorForPlant_onlyMarksSendingOfTargetPlant() = runBlocking {
        val p1 = plant("Рослина 1")
        val p2 = plant("Рослина 2")
        val dao = db.chatMessageDao()

        val p1Sending = dao.insertMessage(msg(p1, "Запит 1", 100, MessageRole.USER, MessageStatus.SENDING))
        val p1Sent = dao.insertMessage(msg(p1, "Успіх 1", 150, MessageRole.USER, MessageStatus.SENT))
        val p2Sending = dao.insertMessage(msg(p2, "Запит 2", 200, MessageRole.USER, MessageStatus.SENDING))

        val updatedCount = dao.markSendingAsErrorForPlant(p1, "Перезапуск")
        assertEquals(1, updatedCount)

        // p1Sending переведено в ERROR
        val p1SendingAfter = dao.getById(p1Sending)!!
        assertEquals(MessageStatus.ERROR, p1SendingAfter.status)
        assertEquals("Перезапуск", p1SendingAfter.errorMessage)

        // p1Sent залишився SENT
        assertEquals(MessageStatus.SENT, dao.getById(p1Sent)!!.status)

        // p2Sending залишився недоторканим у SENDING
        val p2SendingAfter = dao.getById(p2Sending)!!
        assertEquals(MessageStatus.SENDING, p2SendingAfter.status)
        assertNull(p2SendingAfter.errorMessage)
    }
}
