package com.flowers.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowers.app.data.local.entity.CareEventEntity
import com.flowers.app.data.local.entity.PlantEntity
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
import java.io.File

/**
 * Android Instrumented Migration Test (TASK-053).
 * Перевіряє міграцію реальної бази SQLite на пристрої/емуляторі з версії 2 до версії 3.
 * Підтверджує 100% збереження рослин, подій догляду та коректну роботу нових колонок Room v3.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationV2ToV3Test {

    private val dbName = "test_migration_v2_to_v3.db"
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

    @Test
    fun testMigrationV2ToV3PreservesAllDataAndOpensInRoomV3() = runBlocking {
        // 1. Створюємо базу SQLite на версії схеми 2
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Схема v2 з schemas/.../2.json
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `plants` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`customName` TEXT NOT NULL, " +
                            "`speciesId` TEXT, " +
                            "`roomLocation` TEXT NOT NULL, " +
                            "`lightLevel` TEXT NOT NULL, " +
                            "`checkIntervalDays` INTEGER NOT NULL, " +
                            "`lastWateredDate` INTEGER, " +
                            "`nextCheckDate` INTEGER NOT NULL, " +
                            "`photoUri` TEXT, " +
                            "`isArchived` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `care_events` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`plantId` INTEGER NOT NULL, " +
                            "`eventType` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`notes` TEXT, " +
                            "`photoUri` TEXT, " +
                            "`daysDelayed` INTEGER, " +
                            "FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_events_plantId` ON `care_events` (`plantId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_events_timestamp` ON `care_events` (`timestamp`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `catalog_plants` (" +
                            "`id` TEXT NOT NULL, " +
                            "`nameUk` TEXT NOT NULL, " +
                            "`nameLatin` TEXT NOT NULL, " +
                            "`popularName` TEXT NOT NULL, " +
                            "`difficulty` TEXT NOT NULL, " +
                            "`light` TEXT NOT NULL, " +
                            "`defaultCheckIntervalDays` INTEGER NOT NULL, " +
                            "`soilType` TEXT NOT NULL, " +
                            "`toxicToPets` INTEGER NOT NULL, " +
                            "`isAiGenerated` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `care_pods` (" +
                            "`id` TEXT NOT NULL, " +
                            "`plantSpeciesId` TEXT NOT NULL, " +
                            "`category` TEXT NOT NULL, " +
                            "`titleUk` TEXT NOT NULL, " +
                            "`contentUk` TEXT NOT NULL, " +
                            "`icon` TEXT NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val v2Db = helper.writableDatabase

        // 2. Наповнюємо даними v2: рослини, події догляду
        v2Db.execSQL(
            "INSERT INTO plants (id, customName, speciesId, roomLocation, lightLevel, checkIntervalDays, lastWateredDate, nextCheckDate, photoUri, isArchived, createdAt) " +
                "VALUES (1, 'Моя улюблена монстера', 'monstera_deliciosa', 'Вітальня', 'INDIRECT', 7, 1700000000000, 1700600000000, 'file://plants/monstera.jpg', 0, 1690000000000)"
        )
        v2Db.execSQL(
            "INSERT INTO plants (id, customName, speciesId, roomLocation, lightLevel, checkIntervalDays, lastWateredDate, nextCheckDate, photoUri, isArchived, createdAt) " +
                "VALUES (2, 'Фікус Бенджаміна', 'ficus_benjamina', 'Спальня', 'DIRECT', 5, NULL, 1700500000000, NULL, 0, 1690000000001)"
        )
        v2Db.execSQL(
            "INSERT INTO care_events (id, plantId, eventType, timestamp, notes, daysDelayed) " +
                "VALUES (1, 1, 'WATER', 1700000000000, 'Перший полив', 0)"
        )
        v2Db.execSQL(
            "INSERT INTO care_events (id, plantId, eventType, timestamp, notes, daysDelayed) " +
                "VALUES (2, 1, 'DELAY', 1700400000000, 'Ґрунт ще вологий', 2)"
        )
        helper.close()

        // 3. Відкриваємо базу через Room v3 з реєстрацією міграції MIGRATION_2_3
        val roomDb = Room.databaseBuilder(context, FlowersDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
            .build()

        // 4. Перевіряємо збереження наявних даних
        val activePlants = roomDb.plantDao().getAllActivePlants().first()
        assertEquals(2, activePlants.size)

        val plant1 = activePlants.find { it.id == 1L }
        assertNotNull("Рослина 1 має існувати", plant1)
        assertEquals("Моя улюблена монстера", plant1!!.customName)
        assertEquals("monstera_deliciosa", plant1.speciesId)
        assertEquals("Вітальня", plant1.roomLocation)
        assertEquals("INDIRECT", plant1.lightLevel)
        assertEquals(7, plant1.checkIntervalDays)
        assertEquals(1700000000000L, plant1.lastWateredDate)
        assertEquals("file://plants/monstera.jpg", plant1.photoUri)
        // Нові поля Flowers 2.0 мають значення за замовчуванням
        assertNull("speciesName має бути null після міграції", plant1.speciesName)
        assertNull("botanicalName має бути null після міграції", plant1.botanicalName)
        assertNull("description має бути null після міграції", plant1.description)
        assertNull("wateringAdvice має бути null після міграції", plant1.wateringAdvice)
        assertNull("soilType має бути null після міграції", plant1.soilType)
        assertNull("toxicityInfo має бути null після міграції", plant1.toxicityInfo)
        assertNull("aiConfidence має бути null після міграції", plant1.aiConfidence)
        assertEquals(false, plant1.isAiGenerated)

        val plant2 = activePlants.find { it.id == 2L }
        assertNotNull("Рослина 2 має існувати", plant2)
        assertEquals("Фікус Бенджаміна", plant2!!.customName)
        assertNull(plant2.lastWateredDate)

        // Перевіряємо збереження подій догляду (впорядковані за timestamp DESC)
        val eventsPlant1 = roomDb.careEventDao().getEventsForPlant(1L).first()
        assertEquals(2, eventsPlant1.size)
        assertEquals("DELAY", eventsPlant1[0].eventType)
        assertEquals("WATER", eventsPlant1[1].eventType)

        // 5. Перевіряємо вставку та зчитування нової картки Flowers 2.0 у Room v3
        val newPlantV3 = PlantEntity(
            customName = "Заміокулькас смарагдовий",
            speciesName = "Заміокулькас замієлистий",
            botanicalName = "Zamioculcas zamiifolia",
            roomLocation = "Кабінет",
            lightLevel = "SHADE",
            checkIntervalDays = 14,
            lastWateredDate = null,
            nextCheckDate = 1701000000000L,
            photoUri = "file://plants/zz_plant.jpg",
            description = "Вічнозелена сукулентна рослина",
            wateringAdvice = "Повне просихання субстрату",
            soilType = "Ґрунт для кактусів і сукулентів",
            toxicityInfo = "Сік містить оксалати",
            aiConfidence = "HIGH",
            isAiGenerated = true
        )
        val newId = roomDb.plantDao().insertPlant(newPlantV3)
        assertTrue(newId > 0)

        val retrievedV3 = roomDb.plantDao().getPlantById(newId)
        assertNotNull(retrievedV3)
        assertEquals("Заміокулькас смарагдовий", retrievedV3!!.customName)
        assertEquals("Заміокулькас замієлистий", retrievedV3.speciesName)
        assertEquals("Zamioculcas zamiifolia", retrievedV3.botanicalName)
        assertEquals("Вічнозелена сукулентна рослина", retrievedV3.description)
        assertEquals("Повне просихання субстрату", retrievedV3.wateringAdvice)
        assertEquals("Ґрунт для кактусів і сукулентів", retrievedV3.soilType)
        assertEquals("Сік містить оксалати", retrievedV3.toxicityInfo)
        assertEquals("HIGH", retrievedV3.aiConfidence)
        assertEquals(true, retrievedV3.isAiGenerated)

        roomDb.close()
    }
}
