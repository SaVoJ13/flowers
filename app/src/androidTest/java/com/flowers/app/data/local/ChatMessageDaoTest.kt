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
     * Характеризація ризику (рішення Власника потрібне): PlantDao.insertPlant має OnConflictStrategy.REPLACE.
     * SQLite реалізує REPLACE як DELETE + INSERT, тому при увімкнених foreign keys (Room вмикає їх) повторна
     * вставка рослини з уже існуючим id каскадно видаляє дочірні care_events і chat_messages.
     * Тест фіксує фактичну поведінку, а також безпечну альтернативу (updatePlant) без втрат.
     * Поточний production-код викликає insertPlant лише для НОВИХ рослин (id = 0), редагування йде через updatePlant.
     */
    @Test
    fun replaceOnInsertPlant_documentsCascadeDataLoss_andUpdatePlantIsSafe() = runBlocking {
        val p = plant("Оригінал")
        val dao = db.chatMessageDao()
        dao.insertMessage(msg(p, "історія", 1))
        db.careEventDao().insertEvent(CareEventEntity(plantId = p, eventType = "WATER", timestamp = 1))

        // Безпечний шлях: @Update не чіпає дочірні записи.
        val original = db.plantDao().getPlantById(p)!!
        db.plantDao().updatePlant(original.copy(customName = "Оновлено"))
        assertEquals(1, dao.getMessages(p).size)
        assertEquals(1, db.careEventDao().getEventsForPlant(p).first().size)

        // Небезпечний шлях: REPLACE з наявним id.
        db.plantDao().insertPlant(original.copy(customName = "Через REPLACE"))
        val afterReplace = db.plantDao().getPlantById(p)
        assertNotNull(afterReplace)
        assertEquals("Через REPLACE", afterReplace!!.customName)
        val chatLeft = dao.getMessages(p).size
        val eventsLeft = db.careEventDao().getEventsForPlant(p).first().size
        // РИЗИК ПІДТВЕРДЖЕНО: дочірні записи зникли разом із «замінюваним» рядком.
        assertEquals("REPLACE каскадно видаляє chat_messages", 0, chatLeft)
        assertEquals("REPLACE каскадно видаляє care_events", 0, eventsLeft)
        assertFalse(chatLeft > 0 && eventsLeft > 0)
    }
}
