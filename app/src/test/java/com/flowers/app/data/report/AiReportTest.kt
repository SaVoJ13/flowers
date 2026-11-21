package com.flowers.app.data.report

import com.flowers.app.data.local.entity.CarePodEntity
import com.flowers.app.data.local.entity.CatalogPlantEntity
import com.flowers.app.data.remote.NetworkChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReportTest {

    private fun plant(name: String = "Тюльпан") = CatalogPlantEntity(
        id = "ai_1_tulip", nameUk = name, nameLatin = "Tulipa", popularName = "тюльпан", difficulty = "Легко",
        light = "яскраве", defaultCheckIntervalDays = 5, soilType = "легкий", toxicToPets = false, isAiGenerated = true
    )

    private fun pod(i: Int, content: String = "текст $i") =
        CarePodEntity("p$i", "ai_1_tulip", "tip", "Заголовок $i", content, "💧")

    private val allowedKeys = setOf("feature", "plantQuery", "responseText", "reason", "comment", "appVersion")

    @Test
    fun `fields contain only allowed keys and reason code`() {
        val f = AiReport.forPlantCard(plant(), listOf(pod(1)), ReportReason.DANGEROUS, "небезпечно", "1.0.0").toFields()
        assertTrue(allowedKeys.containsAll(f.keys))
        assertEquals("plant_card", f["feature"])
        assertEquals("dangerous", f["reason"])
        assertEquals("Тюльпан", f["plantQuery"])
        assertEquals("1.0.0", f["appVersion"])
        assertTrue((f["responseText"] as String).contains("Заголовок 1: текст 1"))
    }

    @Test
    fun `blank comment is omitted so it passes strict rules`() {
        val f = AiReport.forPlantCard(plant(), emptyList(), ReportReason.OTHER, "   ", "1").toFields()
        assertFalse(f.containsKey("comment"))
    }

    @Test
    fun `fields are truncated to rule limits`() {
        val r = AiReport.forPlantCard(
            plant("я".repeat(300)), listOf(pod(1, "ф".repeat(9000))), ReportReason.WRONG, "к".repeat(900), "в".repeat(80)
        )
        assertEquals(AiReport.MAX_PLANT_QUERY, r.plantQuery.length)
        assertEquals(AiReport.MAX_RESPONSE, r.responseText.length)
        assertEquals(AiReport.MAX_COMMENT, r.comment.length)
        assertEquals(AiReport.MAX_APP_VERSION, r.appVersion.length)
    }

    @Test
    fun `reason codes match rules file`() {
        val rules = java.io.File("../firestore.rules").readText()
        ReportReason.entries.forEach { assertTrue(it.code, rules.contains("'${it.code}'")) }
        assertTrue(rules.contains("'${AiReport.FEATURE_PLANT_CARD}'"))
        // Точні вирази (не підрядок: "<= 400" входить у "<= 4000").
        fun limit(field: String, n: Int) = Regex("""d\.$field\.size\(\) <= $n(?!\d)""").containsMatchIn(rules)
        assertTrue(limit("plantQuery", AiReport.MAX_PLANT_QUERY))
        assertTrue(limit("responseText", AiReport.MAX_RESPONSE))
        assertTrue(limit("comment", AiReport.MAX_COMMENT))
        assertTrue(limit("appVersion", AiReport.MAX_APP_VERSION))
    }

    // ---- репозиторій ----

    private fun report() = AiReport.forPlantCard(plant(), emptyList(), ReportReason.WRONG, "", "1.0.0")

    /** Підроблений Sink: фіксує старти записів; поведінка підтвердження задається. */
    private class FakeSink(private val ack: suspend () -> Unit = {}, private val failOnStart: Boolean = false) : ReportSink {
        val started = mutableListOf<Pair<String, Map<String, Any>>>()
        private var counter = 0
        override fun newId() = "id${++counter}"
        override fun start(docId: String, fields: Map<String, Any>): ReportWrite {
            if (failOnStart) throw IllegalStateException("start failed")
            started += docId to fields
            return ReportWrite { ack() }
        }
    }

    @Test
    fun `offline still queues the write and returns queued, not an error`() = runBlocking {
        val sink = FakeSink()
        val r = AiReportRepository(NetworkChecker { false }, sink).submit("d1", report())
        assertEquals(ReportResult.Queued, r)
        assertEquals(1, sink.started.size)
        assertEquals("d1", sink.started[0].first)
    }

    @Test
    fun `online success writes fields once with given id`() = runBlocking {
        val sink = FakeSink()
        val r = AiReportRepository(NetworkChecker { true }, sink).submit("d1", report())
        assertEquals(ReportResult.Sent, r)
        assertEquals(1, sink.started.size)
        assertEquals("plant_card", sink.started[0].second["feature"])
    }

    @Test
    fun `server rejection returns failed message`() = runBlocking {
        val sink = FakeSink(ack = { throw IllegalStateException("PERMISSION_DENIED") })
        val r = AiReportRepository(NetworkChecker { true }, sink).submit("d1", report())
        assertEquals(ReportResult.Failed(AiReportRepository.MSG_FAILED), r)
    }

    @Test
    fun `hanging server ack times out into queued, not an error`() = runBlocking {
        val sink = FakeSink(ack = { delay(10_000) })
        val r = AiReportRepository(NetworkChecker { true }, sink, timeoutMs = 50).submit("d1", report())
        assertEquals(ReportResult.Queued, r)
        assertEquals(1, sink.started.size)
    }

    @Test
    fun `failure to start the write is a failure`() = runBlocking {
        val r = AiReportRepository(NetworkChecker { true }, FakeSink(failOnStart = true)).submit("d1", report())
        assertEquals(ReportResult.Failed(AiReportRepository.MSG_FAILED), r)
    }

    @Test
    fun `retry in the same dialog reuses the same document id so no duplicate is created`() = runBlocking {
        val sink = FakeSink(ack = { delay(10_000) })
        val repo = AiReportRepository(NetworkChecker { true }, sink, timeoutMs = 20)
        val id = repo.newReportId()
        repo.submit(id, report())
        repo.submit(id, report())
        assertEquals(listOf(id, id), sink.started.map { it.first })
        assertEquals(1, sink.started.map { it.first }.toSet().size)
    }

    @Test
    fun `new dialog gets a new document id`() {
        val repo = AiReportRepository(NetworkChecker { true }, FakeSink())
        assertTrue(repo.newReportId() != repo.newReportId())
    }

    @Test
    fun `caller cancellation is not swallowed`() {
        val sink = FakeSink(ack = { throw CancellationException("cancel") })
        val result = runCatching {
            runBlocking { AiReportRepository(NetworkChecker { true }, sink).submit("d1", report()) }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
