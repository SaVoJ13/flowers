package com.flowers.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.local.entity.ChatAttachmentEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import com.flowers.app.data.local.entity.PlantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-056 / PR №43: Тестування ChatAttachmentDao, зв'язки ChatMessageWithAttachments
 * та цілісності зовнішніх ключів (CASCADE, plant isolation) на ізольованій in-memory базі.
 */
@RunWith(AndroidJUnit4::class)
class ChatAttachmentDaoTest {

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

    private suspend fun createPlant(name: String = "Фікус"): Long =
        db.plantDao().insertPlant(PlantEntity(customName = name, nextCheckDate = 1000L))

    private suspend fun createMessage(
        plantId: Long,
        role: MessageRole = MessageRole.USER,
        content: String = "Питання",
        status: MessageStatus = MessageStatus.SENT,
        timestamp: Long = 1000L
    ): Long = db.chatMessageDao().insertMessage(
        ChatMessageEntity(
            plantId = plantId,
            role = role,
            content = content,
            status = status,
            timestamp = timestamp
        )
    )

    private fun attachment(
        messageId: Long,
        path: String = "/data/user/0/com.savoj.flowers/files/chat_photos/p1.jpg",
        createdAt: Long = 1000L
    ) = ChatAttachmentEntity(
        messageId = messageId,
        filePath = path,
        mimeType = "image/jpeg",
        fileSizeBytes = 204800L,
        width = 1536,
        height = 1152,
        createdAt = createdAt
    )

    @Test
    fun insertAndRetrieveAttachmentByMessageId() = runBlocking {
        val plantId = createPlant()
        val msgId = createMessage(plantId)
        val dao = db.chatAttachmentDao()

        val attId = dao.insertAttachment(attachment(msgId, "/photos/1.jpg"))
        assertTrue(attId > 0)

        val list = dao.getAttachmentsForMessage(msgId)
        assertEquals(1, list.size)
        val loaded = list[0]
        assertEquals(attId, loaded.id)
        assertEquals(msgId, loaded.messageId)
        assertEquals("/photos/1.jpg", loaded.filePath)
        assertEquals("image/jpeg", loaded.mimeType)
        assertEquals(204800L, loaded.fileSizeBytes)
        assertEquals(1536, loaded.width)
        assertEquals(1152, loaded.height)
    }

    @Test
    fun foreignKeyRejectsAttachmentForNonExistentMessage() = runBlocking {
        val dao = db.chatAttachmentDao()
        try {
            dao.insertAttachment(attachment(messageId = 999999L))
            fail("Очікувався SQLiteConstraintException при вставці вкладення без батьківського повідомлення")
        } catch (e: SQLiteConstraintException) {
            // Очікуваний виняток
        }
    }

    @Test
    fun getAttachmentsForMessages_batchRetrieval() = runBlocking {
        val plantId = createPlant()
        val m1 = createMessage(plantId, content = "M1")
        val m2 = createMessage(plantId, content = "M2")
        val m3 = createMessage(plantId, content = "M3")
        val dao = db.chatAttachmentDao()

        dao.insertAttachment(attachment(m1, "/photos/m1.jpg"))
        dao.insertAttachment(attachment(m2, "/photos/m2.jpg"))

        val batch = dao.getAttachmentsForMessages(listOf(m1, m2, m3))
        assertEquals(2, batch.size)
        assertEquals(setOf("/photos/m1.jpg", "/photos/m2.jpg"), batch.map { it.filePath }.toSet())
    }

    @Test
    fun plantIsolationAndPathQueries() = runBlocking {
        val p1 = createPlant("Монстера")
        val p2 = createPlant("Заміокулькас")
        val m1 = createMessage(p1, content = "Листок 1")
        val m2 = createMessage(p2, content = "Листок 2")
        val dao = db.chatAttachmentDao()

        dao.insertAttachment(attachment(m1, "/photos/p1_leaf.jpg"))
        dao.insertAttachment(attachment(m2, "/photos/p2_leaf.jpg"))

        val p1Atts = dao.getAttachmentsForPlant(p1)
        val p2Atts = dao.getAttachmentsForPlant(p2)
        assertEquals(1, p1Atts.size)
        assertEquals("/photos/p1_leaf.jpg", p1Atts[0].filePath)
        assertEquals(1, p2Atts.size)
        assertEquals("/photos/p2_leaf.jpg", p2Atts[0].filePath)

        val p1Paths = dao.getAllFilePathsForPlant(p1)
        assertEquals(listOf("/photos/p1_leaf.jpg"), p1Paths)

        val allPaths = dao.getAllStoredAttachmentPaths()
        assertEquals(2, allPaths.size)
        assertTrue(allPaths.contains("/photos/p1_leaf.jpg"))
        assertTrue(allPaths.contains("/photos/p2_leaf.jpg"))
    }

    @Test
    fun cascadeDeleteOnMessageDeletion() = runBlocking {
        val plantId = createPlant()
        val msgId = createMessage(plantId)
        val dao = db.chatAttachmentDao()

        dao.insertAttachment(attachment(msgId, "/photos/will_delete.jpg"))
        assertEquals(1, dao.getAttachmentsForMessage(msgId).size)

        // Видалення повідомлення через SQL
        db.openHelper.writableDatabase.execSQL("DELETE FROM chat_messages WHERE id = $msgId")

        assertTrue(dao.getAttachmentsForMessage(msgId).isEmpty())
        assertTrue(dao.getAllStoredAttachmentPaths().isEmpty())
    }

    @Test
    fun cascadeDeleteOnPlantDeletion() = runBlocking {
        val plantId = createPlant()
        val msgId = createMessage(plantId)
        val dao = db.chatAttachmentDao()

        dao.insertAttachment(attachment(msgId, "/photos/plant_delete.jpg"))
        assertEquals(1, dao.getAttachmentsForPlant(plantId).size)

        db.plantDao().deletePlantById(plantId)

        assertTrue(db.chatMessageDao().getMessages(plantId).isEmpty())
        assertTrue(dao.getAttachmentsForPlant(plantId).isEmpty())
    }

    @Test
    fun observeMessagesWithAttachmentsEmitsUpdates() = runBlocking {
        val plantId = createPlant()
        val chatDao = db.chatMessageDao()
        val attDao = db.chatAttachmentDao()

        // Спочатку порожньо
        var initial = chatDao.observeMessagesWithAttachments(plantId).first()
        assertTrue(initial.isEmpty())

        // Додаємо повідомлення без вкладення
        val m1 = createMessage(plantId, content = "Текст без фото", timestamp = 100)
        var list = chatDao.observeMessagesWithAttachments(plantId).first()
        assertEquals(1, list.size)
        assertEquals("Текст без фото", list[0].message.content)
        assertTrue(list[0].attachments.isEmpty())
        assertNull(list[0].primaryPhoto)

        // Додаємо вкладення
        attDao.insertAttachment(attachment(m1, "/photos/attached.jpg", createdAt = 100))
        list = chatDao.observeMessagesWithAttachments(plantId).first()
        assertEquals(1, list.size)
        assertEquals(1, list[0].attachments.size)
        assertEquals("/photos/attached.jpg", list[0].primaryPhoto?.filePath)

        // getMessagesWithAttachments також повертає вкладення
        val nonReactive = chatDao.getMessagesWithAttachments(plantId)
        assertEquals(1, nonReactive.size)
        assertEquals("/photos/attached.jpg", nonReactive[0].primaryPhoto?.filePath)
    }

    @Test
    fun deleteAttachmentsForMessageRemovesOnlyTargetAttachments() = runBlocking {
        val plantId = createPlant()
        val m1 = createMessage(plantId, content = "M1")
        val m2 = createMessage(plantId, content = "M2")
        val dao = db.chatAttachmentDao()

        dao.insertAttachment(attachment(m1, "/photos/m1.jpg"))
        dao.insertAttachment(attachment(m2, "/photos/m2.jpg"))

        val deleted = dao.deleteByMessageId(m1)
        assertEquals(1, deleted)
        assertTrue(dao.getAttachmentsForMessage(m1).isEmpty())
        assertEquals(1, dao.getAttachmentsForMessage(m2).size)
    }
}
