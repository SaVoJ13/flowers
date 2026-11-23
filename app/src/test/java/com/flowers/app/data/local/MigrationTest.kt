package com.flowers.app.data.local

import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Тест міграції Room 1 → 2 (TASK-025) на справжній SQLite (sqlite-jdbc).
 * Схема v1 береться дослівно з коду, який Room згенерував для версії 1 (FlowersDatabase_Impl).
 * Схема v2 звіряється з експортованим `schemas/.../2.json`, тож тест впаде, якщо міграція розійдеться з Entity.
 */
class MigrationTest {

    private lateinit var conn: Connection

    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `plants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customName` TEXT NOT NULL, `speciesId` TEXT, `roomLocation` TEXT NOT NULL, `lightLevel` TEXT NOT NULL, `checkIntervalDays` INTEGER NOT NULL, `lastWateredDate` INTEGER, `nextCheckDate` INTEGER NOT NULL, `photoUri` TEXT, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `care_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `eventType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `notes` TEXT, `photoUri` TEXT, `daysDelayed` INTEGER, FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `catalog_plants` (`id` TEXT NOT NULL, `nameUk` TEXT NOT NULL, `nameLatin` TEXT NOT NULL, `popularName` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `light` TEXT NOT NULL, `defaultCheckIntervalDays` INTEGER NOT NULL, `soilType` TEXT NOT NULL, `toxicToPets` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `care_pods` (`id` TEXT NOT NULL, `plantSpeciesId` TEXT NOT NULL, `category` TEXT NOT NULL, `titleUk` TEXT NOT NULL, `contentUk` TEXT NOT NULL, `icon` TEXT NOT NULL, PRIMARY KEY(`id`))"
    )

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON") // як у Room: каскадне видалення було б помітне
            v1Schema.forEach { st.execute(it) }
            seedV1Data(st)
        }
    }

    @After
    fun tearDown() = conn.close()

    private fun seedV1Data(st: java.sql.Statement) {
        st.execute("INSERT INTO plants VALUES (1,'Моя Монстера','monstera','Вітальня','INDIRECT',7,1700000000000,1700600000000,NULL,0,1690000000000)")
        st.execute("INSERT INTO plants VALUES (2,'Фікус Ліра','ai_1700000000_ficus_lyrata','Спальня','DIRECT',9,NULL,1700600000000,'content://photo/2',0,1690000000001)")
        st.execute("INSERT INTO plants VALUES (3,'Архівна','zamioculcas','Кухня','SHADE',14,NULL,1700600000000,NULL,1,1690000000002)")
        st.execute("INSERT INTO care_events VALUES (1,1,'WATERED',1700000000000,'нотатка',NULL,0)")
        st.execute("INSERT INTO care_events VALUES (2,1,'WATERED',1700100000000,NULL,NULL,2)")
        st.execute("INSERT INTO care_events VALUES (3,2,'WATERED',1700200000000,NULL,NULL,NULL)")
        fun cat(id: String, toxic: Int) =
            st.execute("INSERT INTO catalog_plants VALUES ('$id','н','n','п','Легко','л',7,'г',$toxic)")
        cat("monstera", 1)
        cat("chlorophytum", 0)
        cat("ai_1700000000_ficus_lyrata", 1) // припущення ШІ «токсична» має зникнути
        cat("ai_1700000001_calathea", 0)
        cat("aiX_not_ai", 1) // «_» у LIKE без ESCAPE збіглося б з будь-яким символом
        cat("airplant", 1) // починається з «ai», але не з «ai_»
        st.execute("INSERT INTO care_pods VALUES ('pod_ai_1700000000_ficus_lyrata','ai_1700000000_ficus_lyrata','general_rule','Т','К','psychology')")
    }

    private fun migrate() {
        conn.createStatement().use { st -> Migrations.MIGRATION_1_2_SQL.forEach { st.execute(it) } }
    }

    private fun count(sql: String): Int =
        conn.createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getInt(1) } }

    @Test
    fun `user plants and care events are untouched after migration`() {
        migrate()
        assertEquals(3, count("SELECT COUNT(*) FROM plants"))
        assertEquals(3, count("SELECT COUNT(*) FROM care_events"))
        assertEquals(1, count("SELECT COUNT(*) FROM plants WHERE id=1 AND customName='Моя Монстера' AND speciesId='monstera' AND lastWateredDate=1700000000000 AND createdAt=1690000000000"))
        assertEquals(1, count("SELECT COUNT(*) FROM plants WHERE id=2 AND customName='Фікус Ліра' AND speciesId='ai_1700000000_ficus_lyrata' AND photoUri='content://photo/2'"))
        assertEquals(1, count("SELECT COUNT(*) FROM plants WHERE id=3 AND isArchived=1"))
        assertEquals(1, count("SELECT COUNT(*) FROM care_events WHERE id=1 AND notes='нотатка' AND daysDelayed=0"))
        assertEquals(1, count("SELECT COUNT(*) FROM care_pods"))
    }

    @Test
    fun `all catalog rows survive and verified toxicity flags are unchanged`() {
        migrate()
        assertEquals(6, count("SELECT COUNT(*) FROM catalog_plants"))
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_plants WHERE id='monstera' AND toxicToPets=1 AND isAiGenerated=0"))
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_plants WHERE id='chlorophytum' AND toxicToPets=0 AND isAiGenerated=0"))
    }

    @Test
    fun `previously generated ai cards are flagged and lose ai toxicity guess`() {
        migrate()
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_plants WHERE id='ai_1700000000_ficus_lyrata' AND isAiGenerated=1 AND toxicToPets=0"))
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_plants WHERE id='ai_1700000001_calathea' AND isAiGenerated=1"))
        assertEquals(2, count("SELECT COUNT(*) FROM catalog_plants WHERE isAiGenerated=1"))
    }

    @Test
    fun `ids that merely resemble the ai prefix are not flagged`() {
        migrate()
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_plants WHERE id='aiX_not_ai' AND isAiGenerated=0 AND toxicToPets=1"))
        assertEquals(1, count("SELECT COUNT(*) FROM catalog_plants WHERE id='airplant' AND isAiGenerated=0 AND toxicToPets=1"))
    }

    @Test
    fun `migration contains no destructive statements`() {
        Migrations.MIGRATION_1_2_SQL.forEach {
            val up = it.uppercase()
            assertTrue("no DROP: $it", !up.contains("DROP"))
            assertTrue("no DELETE: $it", !up.contains("DELETE"))
            assertTrue("no unfiltered UPDATE: $it", !up.startsWith("UPDATE") || up.contains(" WHERE "))
        }
    }

    @Test
    fun `migrated catalog table matches exported v2 schema`() {
        migrate()
        val schemaFile = File("schemas/com.flowers.app.data.local.FlowersDatabase/2.json")
        assertTrue("exported schema 2.json must exist (exportSchema=true)", schemaFile.exists())
        val entities = JsonParser.parseString(schemaFile.readText()).asJsonObject
            .getAsJsonObject("database").getAsJsonArray("entities")
        val catalog = entities.map { it.asJsonObject }.first { it.get("tableName").asString == "catalog_plants" }
        val expected = catalog.getAsJsonArray("fields").map { it.asJsonObject }.map {
            listOf(
                it.get("columnName").asString,
                it.get("affinity").asString,
                it.get("notNull").asBoolean,
                if (it.has("defaultValue")) it.get("defaultValue").asString else null
            )
        }
        val actual = mutableListOf<List<Any?>>()
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`catalog_plants`)").use { rs ->
                while (rs.next()) {
                    actual += listOf(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value"))
                }
            }
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `migration is registered for 1 to 2`() {
        assertEquals(1, Migrations.MIGRATION_1_2.startVersion)
        assertEquals(2, Migrations.MIGRATION_1_2.endVersion)
    }

    private fun migrateV2ToV3() {
        conn.createStatement().use { st -> Migrations.MIGRATION_2_3_SQL.forEach { st.execute(it) } }
    }

    @Test
    fun `migration 2 to 3 preserves all existing user plants and adds flowers 2_0 columns with default values`() {
        migrate() // 1 -> 2
        migrateV2ToV3() // 2 -> 3

        assertEquals(3, count("SELECT COUNT(*) FROM plants"))
        assertEquals(3, count("SELECT COUNT(*) FROM care_events"))

        // Existing plants must have new columns with default NULL and isAiGenerated=0
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, customName, speciesName, botanicalName, description, wateringAdvice, soilType, toxicityInfo, aiConfidence, isAiGenerated FROM plants WHERE id=1").use { rs ->
                assertTrue(rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals("Моя Монстера", rs.getString("customName"))
                assertNull(rs.getString("speciesName"))
                assertNull(rs.getString("botanicalName"))
                assertNull(rs.getString("description"))
                assertNull(rs.getString("wateringAdvice"))
                assertNull(rs.getString("soilType"))
                assertNull(rs.getString("toxicityInfo"))
                assertNull(rs.getString("aiConfidence"))
                assertEquals(0, rs.getInt("isAiGenerated"))
            }
        }

        // Inserting a new Flowers 2.0 plant with new fields works seamlessly
        conn.createStatement().use { st ->
            st.execute("INSERT INTO plants VALUES (4,'Спатифілум','spathi_1','Спальня','INDIRECT',5,NULL,1700900000000,'path/to/img.jpg',0,1700800000000,'Спатифілум Шопен','Spathiphyllum','Красива квітка','Поливати помірно','Пухкий ґрунт','Безпечно для людей','HIGH',1)")
        }

        assertEquals(4, count("SELECT COUNT(*) FROM plants"))
        conn.createStatement().use { st ->
            st.executeQuery("SELECT speciesName, botanicalName, aiConfidence, isAiGenerated FROM plants WHERE id=4").use { rs ->
                assertTrue(rs.next())
                assertEquals("Спатифілум Шопен", rs.getString("speciesName"))
                assertEquals("Spathiphyllum", rs.getString("botanicalName"))
                assertEquals("HIGH", rs.getString("aiConfidence"))
                assertEquals(1, rs.getInt("isAiGenerated"))
            }
        }
    }

    @Test
    fun `migration 2 to 3 contains no destructive statements`() {
        Migrations.MIGRATION_2_3_SQL.forEach {
            val up = it.uppercase()
            assertTrue("no DROP: $it", !up.contains("DROP"))
            assertTrue("no DELETE: $it", !up.contains("DELETE"))
            assertTrue("must only be ALTER TABLE ADD COLUMN: $it", up.startsWith("ALTER TABLE") && up.contains("ADD COLUMN"))
        }
    }

    @Test
    fun `migration 2 to 3 is registered for 2 to 3`() {
        assertEquals(2, Migrations.MIGRATION_2_3.startVersion)
        assertEquals(3, Migrations.MIGRATION_2_3.endVersion)
    }
}
