package com.flowers.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * TASK-056, крок 1. Реальна SQLite-база схеми v3 (DDL з schemas/3.json) відкривається через Room v4
 * з MIGRATION_3_4. Використовується виключно ізольована тестова база, не дані користувача.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationV3ToV4Test {

    private val dbName = "test_migration_v3_to_v4.db"
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

    private fun createV3DatabaseWithData() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v3 = helper.writableDatabase
        v3.execSQL(
            "INSERT INTO plants (id, customName, speciesId, roomLocation, lightLevel, checkIntervalDays, lastWateredDate, " +
                "nextCheckDate, photoUri, isArchived, createdAt, speciesName, botanicalName, description, wateringAdvice, " +
                "soilType, toxicityInfo, aiConfidence, isAiGenerated) VALUES " +
                "(1, 'Монстера', 'monstera_deliciosa', 'Вітальня', 'INDIRECT', 7, 1700000000000, 1700600000000, " +
                "'/data/user/0/com.savoj.flowers/files/plants/monstera.jpg', 0, 1690000000000, 'Монстера делікатесна', " +
                "'Monstera deliciosa', 'Опис', 'Перевіряйте ґрунт', 'Aroid mix', 'UNKNOWN', 'HIGH', 1)"
        )
        v3.execSQL(
            "INSERT INTO plants (id, customName, speciesId, roomLocation, lightLevel, checkIntervalDays, lastWateredDate, " +
                "nextCheckDate, photoUri, isArchived, createdAt, isAiGenerated) VALUES " +
                "(2, 'Фікус', NULL, 'Спальня', 'DIRECT', 5, NULL, 1700500000000, NULL, 0, 1690000000001, 0)"
        )
        v3.execSQL("INSERT INTO care_events (id, plantId, eventType, timestamp, notes, photoUri, daysDelayed) VALUES (1, 1, 'WATER', 1700000000000, 'Полив', NULL, NULL)")
        v3.execSQL("INSERT INTO care_events (id, plantId, eventType, timestamp, notes, photoUri, daysDelayed) VALUES (2, 1, 'DELAY', 1700100000000, 'Ще вологий', NULL, 2)")
        v3.execSQL(
            "INSERT INTO care_events (id, plantId, eventType, timestamp, notes, photoUri, daysDelayed) VALUES " +
                "(3, 1, 'HEALTH_CHECK', 1700200000000, 'Діагностика', '/data/user/0/com.savoj.flowers/files/plants/health.jpg', NULL)"
        )
        v3.execSQL("INSERT INTO care_events (id, plantId, eventType, timestamp, notes, photoUri, daysDelayed) VALUES (4, 2, 'WATER', 1700300000000, NULL, NULL, NULL)")
        helper.close()
    }

    private fun openV4(): FlowersDatabase =
        Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5)
            .build()

    @Test
    fun migration_3_to_4_preservesAllExistingData_andCreatesEmptyChatTable() = runBlocking {
        createV3DatabaseWithData()
        val db = openV4()
        try {
            // Room виконав міграцію та пройшов валідацію схеми (інакше тут було б виключення).
            val plants = db.plantDao().getAllActivePlants().first()
            assertEquals(2, plants.size)
            val monstera = db.plantDao().getPlantById(1L)!!
            assertEquals("Монстера", monstera.customName)
            assertEquals("/data/user/0/com.savoj.flowers/files/plants/monstera.jpg", monstera.photoUri)
            assertEquals("Monstera deliciosa", monstera.botanicalName)
            assertEquals("HIGH", monstera.aiConfidence)
            assertEquals(true, monstera.isAiGenerated)
            assertEquals(1700000000000L, monstera.lastWateredDate)
            val ficus = db.plantDao().getPlantById(2L)!!
            assertEquals("Фікус", ficus.customName)
            assertNull(ficus.photoUri)

            val events1 = db.careEventDao().getEventsForPlant(1L).first()
            assertEquals(listOf("HEALTH_CHECK", "DELAY", "WATER"), events1.map { it.eventType })
            val health = events1.first { it.eventType == "HEALTH_CHECK" }
            assertEquals("/data/user/0/com.savoj.flowers/files/plants/health.jpg", health.photoUri)
            assertEquals("Діагностика", health.notes)
            assertEquals(2, events1.first { it.eventType == "DELAY" }.daysDelayed)
            assertEquals(1, db.careEventDao().getEventsForPlant(2L).first().size)

            // Нова таблиця спочатку порожня.
            assertTrue(db.chatMessageDao().getMessages(1L).isEmpty())
            assertTrue(db.chatMessageDao().getMessages(2L).isEmpty())
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM chat_messages").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migratedDatabase_isolatesChatsAndCascadesOnlyOwnChat() = runBlocking {
        createV3DatabaseWithData()
        val db = openV4()
        try {
            val dao = db.chatMessageDao()
            dao.insertMessage(ChatMessageEntity(plantId = 1L, role = MessageRole.USER, content = "A1", timestamp = 10))
            dao.insertMessage(ChatMessageEntity(plantId = 2L, role = MessageRole.USER, content = "B1", timestamp = 10))
            assertEquals(listOf("A1"), dao.getMessages(1L).map { it.content })
            assertEquals(listOf("B1"), dao.getMessages(2L).map { it.content })

            db.plantDao().deletePlantById(1L)
            assertTrue(dao.getMessages(1L).isEmpty())
            assertEquals(listOf("B1"), dao.getMessages(2L).map { it.content })
            assertNotNull(db.plantDao().getPlantById(2L))
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationSql_isPurelyAdditive() {
        // Формулювання ON DELETE CASCADE — частина зовнішнього ключа, а не оператор DELETE.
        val sql = Migrations.MIGRATION_3_4_SQL.joinToString("\n").uppercase().replace("ON DELETE CASCADE", "").replace("ON UPDATE NO ACTION", "")
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `CHAT_MESSAGES`"))
        listOf("DROP ", "DELETE ", "ALTER ", "UPDATE ", "INSERT ").forEach {
            assertTrue("Міграція не повинна містити $it", !sql.contains(it))
        }
    }
}
