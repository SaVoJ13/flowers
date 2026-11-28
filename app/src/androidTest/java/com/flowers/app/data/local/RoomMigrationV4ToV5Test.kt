package com.flowers.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.local.entity.ChatAttachmentEntity
import com.flowers.app.data.local.entity.ChatMessageEntity
import com.flowers.app.data.local.entity.MessageRole
import com.flowers.app.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-056 / PR №43. Тест реальної міграції Room v4 -> v5 на базі SQLite (DDL з schemas/4.json).
 * Перевіряє збереження всіх даних, суто адитивний характер міграції та підтримку каскадного видалення.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationV4ToV5Test {

    private val dbName = "test_migration_v4_to_v5.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV4DatabaseWithData() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `plants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`customName` TEXT NOT NULL, `speciesId` TEXT, `roomLocation` TEXT NOT NULL, " +
                            "`lightLevel` TEXT NOT NULL, `checkIntervalDays` INTEGER NOT NULL, `lastWateredDate` INTEGER, " +
                            "`nextCheckDate` INTEGER NOT NULL, `photoUri` TEXT, `isArchived` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, `speciesName` TEXT, `botanicalName` TEXT, `description` TEXT, " +
                            "`wateringAdvice` TEXT, `soilType` TEXT, `toxicityInfo` TEXT, `aiConfidence` TEXT, " +
                            "`isAiGenerated` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `care_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`plantId` INTEGER NOT NULL, `eventType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                            "`notes` TEXT, `photoUri` TEXT, `daysDelayed` INTEGER, " +
                            "FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_events_plantId` ON `care_events` (`plantId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_events_timestamp` ON `care_events` (`timestamp`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `catalog_plants` (`id` TEXT NOT NULL, `nameUk` TEXT NOT NULL, " +
                            "`nameLatin` TEXT NOT NULL, `popularName` TEXT NOT NULL, `difficulty` TEXT NOT NULL, " +
                            "`light` TEXT NOT NULL, `defaultCheckIntervalDays` INTEGER NOT NULL, `soilType` TEXT NOT NULL, " +
                            "`toxicToPets` INTEGER NOT NULL, `isAiGenerated` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `care_pods` (`id` TEXT NOT NULL, `plantSpeciesId` TEXT NOT NULL, " +
                            "`category` TEXT NOT NULL, `titleUk` TEXT NOT NULL, `contentUk` TEXT NOT NULL, " +
                            "`icon` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`plantId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                            "`status` TEXT NOT NULL, `errorMessage` TEXT, `timestamp` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_plantId_timestamp` ON `chat_messages` (`plantId`, `timestamp`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v4 = helper.writableDatabase
        v4.execSQL(
            "INSERT INTO plants (id, customName, speciesId, roomLocation, lightLevel, checkIntervalDays, lastWateredDate, " +
                "nextCheckDate, photoUri, isArchived, createdAt, speciesName, botanicalName, description, wateringAdvice, " +
                "soilType, toxicityInfo, aiConfidence, isAiGenerated) VALUES " +
                "(1, 'Монстера', 'monstera_deliciosa', 'Вітальня', 'INDIRECT', 7, 1700000000000, 1700600000000, " +
                "'/data/user/0/com.savoj.flowers/files/plants/monstera.jpg', 0, 1690000000000, 'Монстера делікатесна', " +
                "'Monstera deliciosa', 'Опис', 'Перевіряйте ґрунт', 'Aroid mix', 'UNKNOWN', 'HIGH', 1)"
        )
        v4.execSQL(
            "INSERT INTO plants (id, customName, speciesId, roomLocation, lightLevel, checkIntervalDays, lastWateredDate, " +
                "nextCheckDate, photoUri, isArchived, createdAt, isAiGenerated) VALUES " +
                "(2, 'Фікус', NULL, 'Спальня', 'DIRECT', 5, NULL, 1700500000000, NULL, 0, 1690000000001, 0)"
        )
        v4.execSQL("INSERT INTO care_events (id, plantId, eventType, timestamp, notes, photoUri, daysDelayed) VALUES (1, 1, 'WATER', 1700000000000, 'Полив', NULL, NULL)")
        v4.execSQL("INSERT INTO chat_messages (id, plantId, role, content, status, errorMessage, timestamp) VALUES (101, 1, 'USER', 'Жовтіє листок', 'SENT', NULL, 1700100000000)")
        v4.execSQL("INSERT INTO chat_messages (id, plantId, role, content, status, errorMessage, timestamp) VALUES (102, 1, 'MODEL', 'Перевірте вологість', 'SENT', NULL, 1700100005000)")
        v4.execSQL("INSERT INTO chat_messages (id, plantId, role, content, status, errorMessage, timestamp) VALUES (201, 2, 'USER', 'Коли поливати?', 'SENT', NULL, 1700200000000)")
        v4.execSQL(
            "INSERT INTO catalog_plants (id, nameUk, nameLatin, popularName, difficulty, light, defaultCheckIntervalDays, soilType, toxicToPets, isAiGenerated) VALUES " +
                "('monstera_deliciosa', 'Монстера делікатесна', 'Monstera deliciosa', 'Монстера', 'MEDIUM', 'INDIRECT', 7, 'Пухкий', 1, 0)"
        )
        v4.execSQL(
            "INSERT INTO care_pods (id, plantSpeciesId, category, titleUk, contentUk, icon) VALUES " +
                "('pod_1', 'monstera_deliciosa', 'WATERING', 'Полив', 'Поливайте помірно', 'water_drop')"
        )
        helper.close()
    }

    private fun openV5(): FlowersDatabase =
        Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(
                Migrations.MIGRATION_1_2,
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5
            )
            .build()

    @Test
    fun migration_4_to_5_preservesAllExistingData_andCreatesEmptyChatAttachmentsTable() = runBlocking {
        createV4DatabaseWithData()
        val db = openV5()
        try {
            // Перевіряємо збереження plants
            val plants = db.plantDao().getAllActivePlants().first()
            assertEquals(2, plants.size)
            val monstera = db.plantDao().getPlantById(1L)!!
            assertEquals("Монстера", monstera.customName)

            // Перевіряємо збереження care_events
            val events1 = db.careEventDao().getEventsForPlant(1L).first()
            assertEquals(1, events1.size)

            // Перевіряємо збереження chat_messages
            val msgs1 = db.chatMessageDao().getMessages(1L)
            assertEquals(2, msgs1.size)
            assertEquals("Жовтіє листок", msgs1[0].content)
            assertEquals("Перевірте вологість", msgs1[1].content)
            val msgs2 = db.chatMessageDao().getMessages(2L)
            assertEquals(1, msgs2.size)
            assertEquals("Коли поливати?", msgs2[0].content)

            // Перевіряємо збереження catalog_plants та care_pods
            val catalog = db.catalogDao().getCatalogPlantById("monstera_deliciosa")
            assertNotNull(catalog)
            assertEquals("Монстера делікатесна", catalog!!.nameUk)
            assertEquals(true, catalog.toxicToPets)

            val pods = db.carePodDao().getPodsForSpecies("monstera_deliciosa").first()
            assertEquals(1, pods.size)
            assertEquals("pod_1", pods[0].id)
            assertEquals("Полив", pods[0].titleUk)

            // Перевіряємо, що chat_attachments створена і спочатку порожня
            val attachmentsDao = db.chatAttachmentDao()
            assertEquals(0, attachmentsDao.getAttachmentsForPlant(1L).size)
            assertEquals(0, attachmentsDao.getAllStoredAttachmentPaths().size)

            // Додаємо вкладення до існуючого повідомлення
            val attachmentId = attachmentsDao.insertAttachment(
                ChatAttachmentEntity(
                    messageId = 101L,
                    filePath = "/data/user/0/com.savoj.flowers/files/chat_photos/plant_1_photo1.jpg",
                    mimeType = "image/jpeg",
                    fileSizeBytes = 204800L,
                    width = 1536,
                    height = 1152,
                    createdAt = 1700100000000L
                )
            )
            assertTrue(attachmentId > 0)

            // Перевіряємо зв'язку через ChatMessageWithAttachments
            val withAttachments = db.chatMessageDao().getMessagesWithAttachments(1L)
            assertEquals(2, withAttachments.size)
            assertEquals(1, withAttachments[0].attachments.size)
            assertEquals("/data/user/0/com.savoj.flowers/files/chat_photos/plant_1_photo1.jpg", withAttachments[0].primaryPhoto?.filePath)
            assertEquals(0, withAttachments[1].attachments.size)
            assertNull(withAttachments[1].primaryPhoto)
        } finally {
            db.close()
        }
    }

    @Test
    fun migratedDatabase_cascadesDeleteOnPlantAndMessage() = runBlocking {
        createV4DatabaseWithData()
        val db = openV5()
        try {
            val attachmentsDao = db.chatAttachmentDao()
            attachmentsDao.insertAttachment(
                ChatAttachmentEntity(
                    messageId = 101L,
                    filePath = "/data/user/0/com.savoj.flowers/files/chat_photos/p1.jpg",
                    mimeType = "image/jpeg",
                    fileSizeBytes = 1000L,
                    width = 1536,
                    height = 1152,
                    createdAt = 100L
                )
            )
            attachmentsDao.insertAttachment(
                ChatAttachmentEntity(
                    messageId = 201L,
                    filePath = "/data/user/0/com.savoj.flowers/files/chat_photos/p2.jpg",
                    mimeType = "image/jpeg",
                    fileSizeBytes = 1000L,
                    width = 1536,
                    height = 1152,
                    createdAt = 200L
                )
            )

            assertEquals(1, attachmentsDao.getAttachmentsForPlant(1L).size)
            assertEquals(1, attachmentsDao.getAttachmentsForPlant(2L).size)

            // 1. Пряме видалення конкретного повідомлення (messageId = 101L) каскадно видаляє його вкладення,
            // але зберігає саму рослину 1 та інше її повідомлення (messageId = 102L)
            db.openHelper.writableDatabase.execSQL("DELETE FROM chat_messages WHERE id = 101")
            assertEquals(1, db.chatMessageDao().getMessages(1L).size)
            assertEquals(102L, db.chatMessageDao().getMessages(1L)[0].id)
            assertTrue(attachmentsDao.getAttachmentsForMessage(101L).isEmpty())
            assertTrue(attachmentsDao.getAttachmentsForPlant(1L).isEmpty())
            assertNotNull(db.plantDao().getPlantById(1L))

            // Повідомлення рослини 2 та його вкладення залишаються недоторканими
            assertEquals(1, db.chatMessageDao().getMessages(2L).size)
            assertEquals(1, attachmentsDao.getAttachmentsForPlant(2L).size)

            // 2. Видалення рослини 2 повинно каскадно видалити її повідомлення (201) та вкладення (p2.jpg)
            db.plantDao().deletePlantById(2L)
            assertTrue(db.chatMessageDao().getMessages(2L).isEmpty())
            assertTrue(attachmentsDao.getAttachmentsForPlant(2L).isEmpty())
            assertTrue(attachmentsDao.getAllStoredAttachmentPaths().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationSql_isPurelyAdditive() {
        val sql = Migrations.MIGRATION_4_5_SQL.joinToString("\n").uppercase()
            .replace("ON DELETE CASCADE", "")
            .replace("ON UPDATE NO ACTION", "")
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `CHAT_ATTACHMENTS`"))
        listOf("DROP ", "DELETE ", "ALTER ", "UPDATE ", "INSERT ").forEach {
            assertTrue("Міграція 4->5 не повинна містити $it", !sql.contains(it))
        }
    }
}
