package com.flowers.app.catalog

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CatalogValidatorTest {

    // ---- фікстури: валідна картка (вигадана, не є контентом каталогу) ----

    private val sourcesJson = """
        {
          "src_a": {"title": "Houseplant care guide", "publisher": "Example Botanical Garden", "url": "https://example.org/a", "kind": "botanical"},
          "src_b": {"title": "Indoor plants", "publisher": "Example University Extension", "url": "https://example.edu/b", "kind": "extension"}
        }
    """.trimIndent()

    private fun text(name: String, lang: String) = JsonParser.parseString(
        """{"name": "$name", "aliases": ["alias"], "summary": "Короткий опис рослини для тесту валідатора каталогу.",
           "watering": "Поливайте, коли верхній шар ґрунту підсох.", "light": "Яскраве розсіяне світло без прямого сонця.",
           "soil": "Легкий дренований ґрунт для кімнатних рослин.", "tip": "Повертайте горщик раз на тиждень до світла."}"""
    ).asJsonObject.also { _ -> lang.length }

    private fun card(id: String = "test_plant"): JsonObject = JsonParser.parseString(
        """
        {
          "id": "$id", "latin": "Testus plantus", "family": "Testaceae", "retired": false, "replacedBy": null,
          "care": {"difficulty": "EASY", "lightMin": "MEDIUM", "lightMax": "HIGH", "checkIntervalDays": 7, "humidity": "MEDIUM", "soil": "UNIVERSAL"},
          "safety": {"pets": "UNKNOWN", "children": "UNKNOWN", "sourceIds": []},
          "i18n": {
            "uk": ${text("Тестова рослина", "uk")}, "ru": ${text("Тестовое растение", "ru")},
            "en": ${text("Test plant", "en")}, "pl": ${text("Roslina testowa", "pl")}
          },
          "sourceIds": ["src_a", "src_b"],
          "review": {"authoredBy": "agent", "factChecked": "2026-12-01", "ownerSampled": false,
                     "nativeReviewed": {"uk": false, "ru": false, "pl": false, "en": false}}
        }
        """.trimIndent()
    ).asJsonObject

    private fun manifest(version: Int = 0, batches: List<String> = listOf("b00")) =
        """{"contentVersion": $version, "schema": 1, "batches": [${batches.joinToString(",") { "\"$it\"" }}]}"""

    private fun errorsFor(cards: List<JsonObject>, version: Int = 0, sources: String = sourcesJson) =
        CatalogValidator.validate(manifest(version), sources, mapOf("b00" to JsonArray().also { a -> cards.forEach { a.add(it) } }.toString()))

    private fun errorsFor(mutate: (JsonObject) -> Unit): List<String> = errorsFor(listOf(card().also(mutate)))

    private fun assertHas(errors: List<String>, fragment: String) =
        assertTrue("очікувалась помилка з «$fragment», є: $errors", errors.any { it.contains(fragment) })

    // ---- позитивні ----

    @Test
    fun `valid fixture passes`() {
        assertEquals(emptyList<String>(), errorsFor(listOf(card())))
    }

    @Test
    fun `safe flags with sources pass`() {
        val e = errorsFor {
            it.getAsJsonObject("safety").addProperty("pets", "TOXIC")
            it.getAsJsonObject("safety").add("sourceIds", JsonArray().also { a -> a.add("src_a") })
            it.getAsJsonObject("safety").addProperty("aspcaUrl", "https://www.aspca.org/pet-care/x")
        }
        assertEquals(emptyList<String>(), e)
    }

    // ---- негативні (≥ 8 за критерієм TASK-038) ----

    @Test fun `unknown id format`() = assertHas(errorsFor { it.addProperty("id", "Test-Plant") }, "[a-z0-9_]")

    @Test
    fun `duplicate ids`() = assertHas(errorsFor(listOf(card("same_id"), card("same_id"))), "дублює")

    @Test fun `missing language`() = assertHas(errorsFor { it.getAsJsonObject("i18n").remove("pl") }, "немає мови pl")

    @Test fun `extra language`() = assertHas(errorsFor { it.getAsJsonObject("i18n").add("de", JsonObject()) }, "зайва мова de")

    @Test fun `missing required field`() = assertHas(errorsFor { it.remove("latin") }, "немає поля 'latin'")

    @Test fun `unknown card field is a typo guard`() = assertHas(errorsFor { it.addProperty("famly", "x") }, "зайве поле 'famly'")

    @Test fun `bad enum`() = assertHas(errorsFor { it.getAsJsonObject("care").addProperty("difficulty", "HARD") }, "care.difficulty")

    @Test
    fun `light order`() = assertHas(
        errorsFor {
            it.getAsJsonObject("care").addProperty("lightMin", "HIGH")
            it.getAsJsonObject("care").addProperty("lightMax", "LOW")
        },
        "lightMin вищий"
    )

    @Test fun `interval out of range`() = assertHas(errorsFor { it.getAsJsonObject("care").addProperty("checkIntervalDays", 60) }, "checkIntervalDays")

    @Test
    fun `non unknown safety without source`() = assertHas(
        errorsFor { it.getAsJsonObject("safety").addProperty("children", "NON_TOXIC") },
        "потребує safety.sourceIds"
    )

    @Test
    fun `unknown source id`() = assertHas(
        errorsFor { it.add("sourceIds", JsonArray().also { a -> a.add("src_a"); a.add("nope") }) },
        "невідоме джерело 'nope'"
    )

    @Test
    fun `fewer than two care sources`() = assertHas(
        errorsFor { it.add("sourceIds", JsonArray().also { a -> a.add("src_a"); a.add("src_a") }) },
        "≥ 2 різних"
    )

    @Test
    fun `aspca as a data source`() {
        val src = """{"src_a": {"title": "ASPCA list", "publisher": "ASPCA", "url": "https://www.aspca.org/x", "kind": "other"},
            "src_b": {"title": "Indoor plants", "publisher": "Example", "url": "https://example.edu/b", "kind": "extension"}}"""
        assertHas(errorsFor(listOf(card()), sources = src), "ASPCA не може бути джерелом")
    }

    @Test
    fun `aspca url must be aspca`() = assertHas(
        errorsFor { it.getAsJsonObject("safety").addProperty("aspcaUrl", "https://evil.example/x") },
        "aspcaUrl"
    )

    @Test
    fun `text too short`() = assertHas(errorsFor { it.getAsJsonObject("i18n").getAsJsonObject("en").addProperty("tip", "x") }, "i18n.en.tip")

    @Test
    fun `text too long`() = assertHas(
        errorsFor { it.getAsJsonObject("i18n").getAsJsonObject("uk").addProperty("summary", "я".repeat(301)) },
        "i18n.uk.summary"
    )

    @Test
    fun `control characters`() = assertHas(
        errorsFor { it.getAsJsonObject("i18n").getAsJsonObject("pl").addProperty("tip", "Tekst z\u0007 dzwonkiem w srodku zdania.") },
        "керівні символи"
    )

    @Test
    fun `cyrillic in english name`() = assertHas(
        errorsFor { it.getAsJsonObject("i18n").getAsJsonObject("en").addProperty("name", "Тест") },
        "i18n.en.name: очікується латиниця"
    )

    @Test
    fun `latin in ukrainian name`() = assertHas(
        errorsFor { it.getAsJsonObject("i18n").getAsJsonObject("uk").addProperty("name", "Test plant") },
        "i18n.uk.name: очікується кирилиця"
    )

    @Test
    fun `retired without replacement`() = assertHas(errorsFor { it.addProperty("retired", true) }, "retired=true потребує replacedBy")

    @Test
    fun `replacement target must exist`() = assertHas(errorsFor { it.addProperty("replacedBy", "ghost") }, "не існує")

    @Test
    fun `bad review date`() = assertHas(
        errorsFor { it.getAsJsonObject("review").addProperty("factChecked", "01.12.2026") },
        "YYYY-MM-DD"
    )

    // ---- рівень файлів ----

    @Test
    fun `batch listed but missing`() = assertHas(
        CatalogValidator.validate(manifest(batches = listOf("b00", "b01")), sourcesJson, mapOf("b00" to "[]")),
        "b01.json вказана в manifest, але файлу немає"
    )

    @Test
    fun `batch present but not listed`() = assertHas(
        CatalogValidator.validate(manifest(batches = listOf("b00")), sourcesJson, mapOf("b00" to "[]"), setOf("b00", "b09")),
        "b09.json лежить у каталозі"
    )

    @Test
    fun `malformed batch json`() = assertHas(CatalogValidator.validate(manifest(), sourcesJson, mapOf("b00" to "{oops")), "не JSON")

    @Test
    fun `legacy ids required once content exists`() {
        val e = errorsFor(listOf(card()), version = 1)
        assertHas(e, "відсутній наявний ID 'monstera'")
        assertEquals(20, e.count { it.startsWith("відсутній наявний ID") })
    }

    @Test
    fun `all twenty legacy ids with version 1 pass`() {
        val cards = CatalogValidator.LEGACY_IDS.map { card(it) }
        assertEquals(emptyList<String>(), errorsFor(cards, version = 1))
    }

    @Test
    fun `legacy id list matches room v2 catalog file`() {
        val file = File("../docs/content/PLANT_CATALOG.json")
        val ids = JsonParser.parseString(file.readText()).asJsonArray.map { it.asJsonObject.get("id").asString }
        assertEquals(ids.toSet(), CatalogValidator.LEGACY_IDS.toSet())
        assertEquals(20, CatalogValidator.LEGACY_IDS.size)
    }

    // ---- фактичні файли каталогу в assets (джерело істини) ----

    @Test
    fun `shipped catalog in assets is valid`() {
        val dir = File("src/main/assets/catalog")
        val batchFiles = dir.listFiles { f -> Regex("^b\\d{2}\\.json$").matches(f.name) }.orEmpty()
        val batches = batchFiles.associate { it.name.removeSuffix(".json") to it.readText() }
        val errors = CatalogValidator.validate(File(dir, "manifest.json").readText(), File(dir, "sources.json").readText(), batches)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `docs copy of catalog equals assets`() {
        val assets = File("src/main/assets/catalog")
        val docs = File("../docs/content/catalog")
        assets.listFiles { f -> f.name.endsWith(".json") }.orEmpty().forEach {
            assertEquals("${it.name} у docs/content/catalog має збігатися з assets", it.readText().replace("\r\n", "\n"), File(docs, it.name).readText().replace("\r\n", "\n"))
        }
    }

    // ---- оригінальність ----

    @Test
    fun `overlap checker detects copied run`() {
        val src = "Water thoroughly when the top two inches of soil feel dry to the touch and never let it sit in water"
        assertTrue(OverlapChecker.copies("You should water thoroughly when the top two inches of soil feel dry, always", src))
        assertFalse(OverlapChecker.copies("Give a good soak once the upper soil layer has dried out a bit", src))
        assertEquals(0, OverlapChecker.longestSharedRun("", src))
    }
}
