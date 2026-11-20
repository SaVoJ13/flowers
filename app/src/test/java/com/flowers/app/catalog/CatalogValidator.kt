package com.flowers.app.catalog

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Валідатор каталогу `schema: 1` (TASK-038). Чиста функція над текстами JSON, без Android: працює в JVM-тестах
 * на `app/src/main/assets/catalog` і на навмисно зламаних прикладах. Повертає список помилок (порожній = валідно).
 * Правила описані в `docs/content/catalog/FORMAT.md`; змінюючи тут, змінювати й там.
 */
object CatalogValidator {

    /** 20 ID рослин з Room v2 (`PLANT_CATALOG.json`): не змінюються й не видаляються ніколи (INBOX-008, Q-1.1-1). */
    val LEGACY_IDS: List<String> = listOf(
        "zamioculcas", "sansevieria", "epipremnum", "monstera", "spathiphyllum", "chlorophytum", "crassula",
        "ficus_elastica", "ficus_benjamina", "dracaena_marginata", "peperomia", "aglaonema", "philodendron",
        "syngonium", "aloe_vera", "tradescantia", "maranta", "schefflera", "calathea", "begonia"
    )

    val LANGS = listOf("uk", "en", "ru", "pl")
    val DIFFICULTY = listOf("VERY_EASY", "EASY", "MEDIUM", "DEMANDING")
    val LIGHT = listOf("LOW", "MEDIUM", "HIGH", "DIRECT") // порядок = зростання
    val HUMIDITY = listOf("LOW", "MEDIUM", "HIGH")
    val SOIL = listOf("UNIVERSAL", "AROID_MIX", "CACTUS_MIX", "ORCHID_BARK", "MOISTURE_RETAINING", "LIGHT_AIRY")
    val SAFETY = listOf("TOXIC", "NON_TOXIC", "UNKNOWN")
    val SOURCE_KINDS = listOf("extension", "botanical", "society", "other")

    const val ASPCA_PREFIX = "https://www.aspca.org/"
    const val MIN_CARE_SOURCES = 2

    private val ID_RE = Regex("^[a-z0-9_]+$")
    private val BATCH_RE = Regex("^b\\d{2}$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val CYRILLIC = Regex("[\\u0400-\\u04FF]")
    // Керівні, C1, роздільники рядків/абзаців, нульової ширини та двоспрямовані (bidi) символи.
    private val CONTROL = Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u2028-\\u202E\\u2060-\\u2069\\uFEFF]")

    private val CARD_KEYS = setOf("id", "latin", "family", "retired", "replacedBy", "care", "safety", "i18n", "sourceIds", "review", "notes")
    private val CARD_REQUIRED = CARD_KEYS - setOf("notes", "replacedBy")
    private val CARE_KEYS = setOf("difficulty", "lightMin", "lightMax", "checkIntervalDays", "humidity", "soil")
    private val SAFETY_KEYS = setOf("pets", "children", "sourceIds", "aspcaUrl")
    private val TEXT_KEYS = setOf("name", "aliases", "summary", "watering", "light", "soil", "tip")
    private val REVIEW_KEYS = setOf("authoredBy", "factChecked", "ownerSampled", "nativeReviewed")
    private val SOURCE_DEF_KEYS = setOf("title", "publisher", "url", "kind")

    /** Межі довжин (символів) текстових полів. */
    private val LENGTHS = mapOf(
        "name" to 2..60, "summary" to 30..300, "watering" to 15..300, "light" to 15..300, "soil" to 15..300, "tip" to 15..300
    )
    private const val MAX_ALIASES = 8
    private const val MAX_ALIAS_LEN = 40
    private const val MAX_NOTES = 300

    /**
     * @param batches ім'я партії (`b00`) -> текст JSON файлу.
     * @param presentBatchFiles імена `bNN` усіх файлів партій у каталозі (щоб ловити неперелічені в manifest).
     */
    fun validate(
        manifest: String,
        sources: String,
        batches: Map<String, String>,
        presentBatchFiles: Set<String> = batches.keys
    ): List<String> {
        val errors = ArrayList<String>()
        val man = parseObject(manifest, "manifest.json", errors)
        val src = parseObject(sources, "sources.json", errors)
        var contentVersion = 0
        val batchNames = ArrayList<String>()
        if (man != null) {
            val keys = setOf("contentVersion", "schema", "batches")
            checkKeys(man, keys, keys, "manifest", errors)
            contentVersion = man.int("contentVersion", "manifest.contentVersion", errors, 0..100000) ?: 0
            man.int("schema", "manifest.schema", errors, 1..1)
            val arr = man.get("batches")
            if (arr != null) {
                if (arr !is JsonArray) errors += "manifest.batches має бути масивом" else arr.forEach { e ->
                    val n = if (e.isJsonPrimitive && e.asJsonPrimitive.isString) e.asString else null
                    if (n == null || !BATCH_RE.matches(n)) errors += "manifest.batches: недопустима назва партії '$e'"
                    else if (!batchNames.add(n)) errors += "manifest.batches: дубль '$n'"
                }
            }
        }
        if (man != null && man.has("batches") && contentVersion == 0 && batchNames.isNotEmpty()) errors += "manifest: contentVersion 0 означає порожній каталог, але batches непорожній"
        if (man != null && contentVersion >= 1 && batchNames.isEmpty()) errors += "manifest: contentVersion ≥ 1 потребує хоча б однієї партії"
        val sourceIds = HashSet<String>()
        if (src != null) validateSources(src, sourceIds, errors)

        batchNames.filter { it !in batches }.forEach { errors += "партія $it.json вказана в manifest, але файлу немає" }
        presentBatchFiles.filter { it !in batchNames }.forEach { errors += "файл $it.json лежить у каталозі, але не вказаний у manifest.batches" }

        val seenIds = HashMap<String, String>()
        val replacements = ArrayList<Triple<String, String, String>>()
        for (b in batchNames) {
            val text = batches[b] ?: continue
            val arr = try {
                StrictJson.parse(text)
            } catch (e: Exception) {
                errors += "$b.json: не JSON (${e.message})"
                continue
            }
            if (arr !is JsonArray) {
                errors += "$b.json: має бути масивом карток"
                continue
            }
            arr.forEachIndexed { i, el ->
                val where = "$b[$i]"
                if (el !is JsonObject) {
                    errors += "$where: картка має бути об'єктом"
                    return@forEachIndexed
                }
                val id = validateCard(el, where, sourceIds, errors)
                if (id != null) {
                    seenIds.put(id, where)?.let { errors += "$where: id '$id' дублює $it" }
                    val rb = el.get("replacedBy")
                    if (rb != null && rb.isJsonPrimitive && rb.asJsonPrimitive.isString) replacements += Triple(where, id, rb.asString)
                }
            }
        }
        replacements.forEach { (where, id, target) ->
            if (target == id) errors += "$where: replacedBy вказує на себе"
            else if (target !in seenIds) errors += "$where: replacedBy '$target' не існує в каталозі"
        }
        val repl = replacements.associate { it.second to it.third }
        replacements.forEach { (where, id, _) ->
            val seen = HashSet<String>()
            var cur: String? = id
            while (cur != null && seen.add(cur)) cur = repl[cur]
            if (cur != null && seen.size > 1) errors += "$where: цикл replacedBy"
        }
        if (contentVersion >= 1) {
            LEGACY_IDS.filter { it !in seenIds }.forEach {
                errors += "відсутній наявний ID '$it' (ID з Room v2 не видаляються й не змінюються)"
            }
        }
        return errors
    }

    private fun validateSources(src: JsonObject, ids: MutableSet<String>, errors: MutableList<String>) {
        for ((id, v) in src.entrySet()) {
            if (!ID_RE.matches(id)) errors += "sources: недопустимий sourceId '$id'"
            ids += id
            if (v !is JsonObject) {
                errors += "sources.$id: має бути об'єктом"
                continue
            }
            checkKeys(v, SOURCE_DEF_KEYS, SOURCE_DEF_KEYS, "sources.$id", errors)
            v.str("title", "sources.$id.title", errors, 2..200)
            v.str("publisher", "sources.$id.publisher", errors, 2..200)
            val url = v.str("url", "sources.$id.url", errors, 10..500)
            if (url != null) {
                if (!url.startsWith("https://")) errors += "sources.$id.url: лише https"
                if (url.contains("aspca.org", ignoreCase = true)) {
                    errors += "sources.$id.url: ASPCA не може бути джерелом даних (лише посилання aspcaUrl), ADR-014"
                }
            }
            v.enum("kind", SOURCE_KINDS, "sources.$id.kind", errors)
        }
    }

    /** Повертає id картки (якщо рядок), помилки додає в [errors]. */
    private fun validateCard(c: JsonObject, w: String, sourceIds: Set<String>, errors: MutableList<String>): String? {
        checkKeys(c, CARD_KEYS, CARD_REQUIRED, w, errors)
        val id = c.str("id", "$w.id", errors, 2..60)
        if (id != null && !ID_RE.matches(id)) errors += "$w.id: '$id' має відповідати [a-z0-9_]+"
        c.str("latin", "$w.latin", errors, 3..80)
        c.str("family", "$w.family", errors, 3..60)
        val retiredEl = c.get("retired")
        val retired = if (retiredEl != null && retiredEl.isJsonPrimitive && retiredEl.asJsonPrimitive.isBoolean) retiredEl.asBoolean else null
        if (c.has("retired") && retired == null) errors += "$w.retired має бути boolean"
        val rb = c.get("replacedBy")
        val hasReplacement = rb != null && !rb.isJsonNull
        if (hasReplacement && !(rb!!.isJsonPrimitive && rb.asJsonPrimitive.isString && ID_RE.matches(rb.asString))) {
            errors += "$w.replacedBy: null або id"
        }
        if (retired == true && !hasReplacement) errors += "$w: retired=true потребує replacedBy"
        c.get("notes")?.let { n ->
            val ok = n.isJsonPrimitive && n.asJsonPrimitive.isString && n.asString.isNotBlank() && n.asString.length <= MAX_NOTES && !CONTROL.containsMatchIn(n.asString)
            if (!ok) errors += "$w.notes: непорожній рядок ≤ $MAX_NOTES без керівних символів (або відсутнє поле)"
        }

        c.obj("care", w, errors)?.let { care ->
            checkKeys(care, CARE_KEYS, CARE_KEYS, "$w.care", errors)
            care.enum("difficulty", DIFFICULTY, "$w.care.difficulty", errors)
            val lo = care.enum("lightMin", LIGHT, "$w.care.lightMin", errors)
            val hi = care.enum("lightMax", LIGHT, "$w.care.lightMax", errors)
            if (lo != null && hi != null && LIGHT.indexOf(lo) > LIGHT.indexOf(hi)) errors += "$w.care: lightMin вищий за lightMax"
            care.int("checkIntervalDays", "$w.care.checkIntervalDays", errors, 3..28)
            care.enum("humidity", HUMIDITY, "$w.care.humidity", errors)
            care.enum("soil", SOIL, "$w.care.soil", errors)
        }

        c.obj("safety", w, errors)?.let { s ->
            checkKeys(s, SAFETY_KEYS, setOf("pets", "children", "sourceIds"), "$w.safety", errors)
            val pets = s.enum("pets", SAFETY, "$w.safety.pets", errors)
            val kids = s.enum("children", SAFETY, "$w.safety.children", errors)
            val sids = s.strList("sourceIds", "$w.safety.sourceIds", errors)
            if (sids != null && sids.toSet().size != sids.size) errors += "$w.safety.sourceIds: дублі"
            sids?.filter { it !in sourceIds }?.forEach { errors += "$w.safety.sourceIds: невідоме джерело '$it'" }
            val claims = (pets != null && pets != "UNKNOWN") || (kids != null && kids != "UNKNOWN")
            if (claims && sids.isNullOrEmpty()) errors += "$w.safety: прапорець ≠ UNKNOWN потребує safety.sourceIds (безпека лише з джерелом)"
            s.get("aspcaUrl")?.let { u ->
                val ok = u.isJsonPrimitive && u.asJsonPrimitive.isString && u.asString.startsWith(ASPCA_PREFIX) &&
                    u.asString.length <= 300 && !CONTROL.containsMatchIn(u.asString) && u.asString.none { it.isWhitespace() }
                if (!ok) errors += "$w.safety.aspcaUrl: має починатися з $ASPCA_PREFIX"
            }
        }

        c.strList("sourceIds", "$w.sourceIds", errors)?.let { care ->
            if (care.toSet().size < MIN_CARE_SOURCES) errors += "$w.sourceIds: потрібно ≥ $MIN_CARE_SOURCES різних джерел догляду"
            care.filter { it !in sourceIds }.forEach { errors += "$w.sourceIds: невідоме джерело '$it'" }
        }

        c.obj("i18n", w, errors)?.let { i18n ->
            val keys = i18n.keySet()
            (LANGS - keys).forEach { errors += "$w.i18n: немає мови $it" }
            (keys - LANGS.toSet()).forEach { errors += "$w.i18n: зайва мова $it" }
            for (lang in LANGS) {
                val tEl = i18n.get(lang) ?: continue
                if (tEl !is JsonObject) {
                    errors += "$w.i18n.$lang має бути об'єктом"
                    continue
                }
                val t = tEl
                val tw = "$w.i18n.$lang"
                checkKeys(t, TEXT_KEYS, TEXT_KEYS, tw, errors)
                LENGTHS.forEach { (k, range) -> t.str(k, "$tw.$k", errors, range) }
                val aliases = t.get("aliases")
                if (aliases != null) {
                    if (aliases !is JsonArray) errors += "$tw.aliases має бути масивом" else {
                        if (aliases.size() > MAX_ALIASES) errors += "$tw.aliases: більше $MAX_ALIASES"
                        val norm = aliases.mapNotNull { x -> if (x.isJsonPrimitive && x.asJsonPrimitive.isString) x.asString.trim().lowercase() else null }
                        if (norm.toSet().size != norm.size) errors += "$tw.aliases: дублі"
                        aliases.forEach { x ->
                            val ok = x.isJsonPrimitive && x.asJsonPrimitive.isString && x.asString.isNotBlank() &&
                                x.asString.length <= MAX_ALIAS_LEN && !CONTROL.containsMatchIn(x.asString)
                            if (!ok) errors += "$tw.aliases: недопустимий елемент $x"
                        }
                    }
                }
                val nameEl = t.get("name")
                if (nameEl != null && nameEl.isJsonPrimitive && nameEl.asJsonPrimitive.isString) {
                    val nm = nameEl.asString
                    val cyr = CYRILLIC.containsMatchIn(nm)
                    val letters = nm.count { it.isLetter() }
                    val cyrLetters = nm.count { CYRILLIC.matches(it.toString()) }
                    if ((lang == "uk" || lang == "ru") && (!cyr || cyrLetters * 2 <= letters)) errors += "$tw.name: очікується переважно кирилиця"
                    if ((lang == "en" || lang == "pl") && cyr) errors += "$tw.name: очікується латиниця"
                }
            }
        }

        c.obj("review", w, errors)?.let { r ->
            checkKeys(r, REVIEW_KEYS, REVIEW_KEYS, "$w.review", errors)
            r.str("authoredBy", "$w.review.authoredBy", errors, 2..60)
            r.str("factChecked", "$w.review.factChecked", errors, 10..10)?.let {
                if (!DATE_RE.matches(it) || runCatching { LocalDate.parse(it) }.isFailure) errors += "$w.review.factChecked: формат YYYY-MM-DD і існуюча дата"
            }
            val os = r.get("ownerSampled")
            if (os != null && !(os.isJsonPrimitive && os.asJsonPrimitive.isBoolean)) errors += "$w.review.ownerSampled має бути boolean"
            r.obj("nativeReviewed", "$w.review", errors)?.let { nr ->
                checkKeys(nr, LANGS.toSet(), LANGS.toSet(), "$w.review.nativeReviewed", errors)
                for (l in LANGS) {
                    val v = nr.get(l)
                    if (v != null && !(v.isJsonPrimitive && v.asJsonPrimitive.isBoolean)) errors += "$w.review.nativeReviewed.$l має бути boolean"
                }
            }
        }
        return id
    }

    // ---- допоміжні ----

    private fun parseObject(text: String, name: String, errors: MutableList<String>): JsonObject? = try {
        val e = StrictJson.parse(text)
        if (e is JsonObject) e else { errors += "$name: має бути об'єктом"; null }
    } catch (e: Exception) {
        errors += "$name: не JSON (${e.message})"
        null
    }

    private fun checkKeys(o: JsonObject, allowed: Set<String>, required: Set<String>, where: String, errors: MutableList<String>) {
        (required - o.keySet()).forEach { errors += "$where: немає поля '$it'" }
        (o.keySet() - allowed).forEach { errors += "$where: зайве поле '$it'" }
    }

    /** Об'єкт за ключем; відсутність уже повідомляє [checkKeys] батька, тут лише помилка типу. */
    private fun JsonObject.obj(key: String, where: String, errors: MutableList<String>): JsonObject? {
        val v = get(key) ?: return null
        if (v is JsonObject) return v
        errors += "$where.$key має бути об'єктом"
        return null
    }

    private fun JsonObject.str(key: String, where: String, errors: MutableList<String>, len: IntRange): String? {
        val v = get(key) ?: return null // відсутність повідомляє checkKeys
        if (!(v.isJsonPrimitive && v.asJsonPrimitive.isString)) {
            errors += "$where має бути рядком"
            return null
        }
        val s = v.asString
        if (s.isBlank()) errors += "$where: порожнє значення"
        else if (s.length !in len) errors += "$where: довжина ${s.length} поза межами $len"
        if (CONTROL.containsMatchIn(s)) errors += "$where: керівні символи заборонені"
        return s
    }

    private fun JsonObject.int(key: String, where: String, errors: MutableList<String>, range: IntRange): Int? {
        val v = get(key) ?: return null
        if (!(v.isJsonPrimitive && v.asJsonPrimitive.isNumber)) {
            errors += "$where має бути числом"
            return null
        }
        val d = v.asDouble
        if (d != Math.floor(d) || d.toInt() !in range) {
            errors += "$where: $d поза межами $range"
            return null
        }
        return d.toInt()
    }

    private fun JsonObject.enum(key: String, allowed: List<String>, where: String, errors: MutableList<String>): String? {
        val v = get(key) ?: return null
        val s = if (v.isJsonPrimitive && v.asJsonPrimitive.isString) v.asString else null
        if (s == null || s !in allowed) {
            errors += "$where: '$v' не з $allowed"
            return null
        }
        return s
    }

    private fun JsonObject.strList(key: String, where: String, errors: MutableList<String>): List<String>? {
        val v = get(key) ?: return null
        if (v !is JsonArray) {
            errors += "$where має бути масивом"
            return null
        }
        val out = ArrayList<String>()
        v.forEach {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank()) out += it.asString
            else errors += "$where: недопустимий елемент $it"
        }
        return out
    }
}

/**
 * Перевірка оригінальності текстів (правило партії): найдовша послідовність слів, спільна з текстом джерела.
 * Поріг у FORMAT.md: ≥ [MAX_SHARED_WORDS] слів поспіль = копіювання.
 */
object OverlapChecker {
    const val MAX_SHARED_WORDS = 8

    private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    fun longestSharedRun(text: String, source: String): Int {
        val a = words(text)
        val b = words(source)
        var best = 0
        // Динамічне програмування за найдовшою спільною підпослідовністю підряд (рядки текстів короткі).
        var prev = IntArray(b.size + 1)
        for (i in 1..a.size) {
            val cur = IntArray(b.size + 1)
            for (j in 1..b.size) {
                if (a[i - 1] == b[j - 1]) {
                    cur[j] = prev[j - 1] + 1
                    if (cur[j] > best) best = cur[j]
                }
            }
            prev = cur
        }
        return best
    }

    fun copies(text: String, source: String) = longestSharedRun(text, source) >= MAX_SHARED_WORDS
}

/** Строгий JSON: без одинарних лапок/ключів без лапок/коментарів і з відхиленням дублів ключів (імпортер має читати так само). */
object StrictJson {
    fun parse(text: String): JsonElement {
        val r = JsonReader(StringReader(text))
        r.strictness = Strictness.STRICT
        val v = read(r)
        if (r.peek() != JsonToken.END_DOCUMENT) throw JsonParseException("зайвий текст після JSON")
        return v
    }

    private fun read(r: JsonReader): JsonElement = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            r.beginObject()
            val o = JsonObject()
            while (r.hasNext()) {
                val n = r.nextName()
                if (o.has(n)) throw JsonParseException("дубль ключа '$n'")
                o.add(n, read(r))
            }
            r.endObject()
            o
        }
        JsonToken.BEGIN_ARRAY -> {
            r.beginArray()
            val a = JsonArray()
            while (r.hasNext()) a.add(read(r))
            r.endArray()
            a
        }
        JsonToken.STRING -> JsonPrimitive(r.nextString())
        JsonToken.NUMBER -> JsonPrimitive(BigDecimal(r.nextString()))
        JsonToken.BOOLEAN -> JsonPrimitive(r.nextBoolean())
        JsonToken.NULL -> { r.nextNull(); JsonNull.INSTANCE }
        else -> throw JsonParseException("неочікуваний токен ${r.peek()}")
    }
}

/** Файли каталогу на диску: відокремлює партії від сторонніх файлів (щоб `b1.json`, `batch.json` не губилися). */
object CatalogFiles {
    private val BATCH_FILE = Regex("^b\\d{2}\\.json$")
    private val ALLOWED_OTHER = setOf("manifest.json", "sources.json")

    class Scan(val batches: Map<String, String>, val unexpected: List<String>)

    fun scan(dir: java.io.File): Scan {
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val batches = files.filter { BATCH_FILE.matches(it.name) }.associate { it.name.removeSuffix(".json") to it.readText() }
        val unexpected = files.map { it.name }.filter { it !in ALLOWED_OTHER && !BATCH_FILE.matches(it) }
        return Scan(batches, unexpected)
    }
}

/**
 * Оригінальність партії (правило партій, FORMAT.md §4): для кожної картки в `drafts/bNN.excerpts.json`
 * (`{"<plantId>": ["уривок джерела", ...]}`) тексти всіх мов не повинні ділити ≥ [OverlapChecker.MAX_SHARED_WORDS]
 * слів поспіль з жодним уривком. Кожна картка зобов'язана мати ≥ 1 уривок (інакше перевірка порожня).
 */
object OriginalityValidator {
    private const val MIN_EXCERPT_LEN = 40
    private val TEXT_FIELDS = listOf("name", "summary", "watering", "light", "soil", "tip")

    fun validate(batch: String, batchName: String, excerpts: String?): List<String> {
        val errors = ArrayList<String>()
        if (excerpts == null) return listOf("$batchName: немає drafts/$batchName.excerpts.json (уривки джерел для перевірки оригінальності)")
        val cards = try { StrictJson.parse(batch) as? JsonArray } catch (e: Exception) { null } ?: return errors
        val ex = try { StrictJson.parse(excerpts) as? JsonObject } catch (e: Exception) { null }
            ?: return listOf("$batchName.excerpts.json: має бути JSON-об'єктом")
        val ids = HashSet<String>()
        cards.forEach { c ->
            val card = c as? JsonObject ?: return@forEach
            val id = card.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
            ids += id
            val list = (ex.get(id) as? JsonArray)?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }.orEmpty()
            if (list.isEmpty() || list.any { it.length < MIN_EXCERPT_LEN }) {
                errors += "$batchName/$id: потрібні уривки джерел (≥ 1, кожен ≥ $MIN_EXCERPT_LEN символів)"
                return@forEach
            }
            val i18n = card.get("i18n") as? JsonObject ?: return@forEach
            for ((lang, t) in i18n.entrySet()) {
                val tobj = t as? JsonObject ?: continue
                for (f in TEXT_FIELDS) {
                    val txt = tobj.get(f)?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                    list.forEach { src ->
                        if (OverlapChecker.copies(txt, src)) errors += "$batchName/$id.i18n.$lang.$f: збіг ≥ ${OverlapChecker.MAX_SHARED_WORDS} слів поспіль з уривком джерела"
                    }
                }
            }
        }
        (ex.keySet() - ids).forEach { errors += "$batchName.excerpts.json: уривки для невідомого id '$it'" }
        return errors
    }
}
