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
        assertTrue(rules.contains("<= ${AiReport.MAX_PLANT_QUERY}"))
        assertTrue(rules.contains("<= ${AiReport.MAX_RESPONSE}"))
        assertTrue(rules.contains("<= ${AiReport.MAX_COMMENT}"))
        assertTrue(rules.contains("<= ${AiReport.MAX_APP_VERSION}"))
    }

    // ---- репозиторій ----

    private fun report() = AiReport.forPlantCard(plant(), emptyList(), ReportReason.WRONG, "", "1.0.0")

    @Test
    fun `offline does not call sink and returns offline message`() = runBlocking {
        var called = false
        val r = AiReportRepository(NetworkChecker { false }, ReportSink { called = true }).submit(report())
        assertFalse(called)
        assertEquals(ReportResult.Failed(AiReportRepository.MSG_OFFLINE), r)
    }

    @Test
    fun `online success writes fields once`() = runBlocking {
        val written = mutableListOf<Map<String, Any>>()
        val r = AiReportRepository(NetworkChecker { true }, ReportSink { written.add(it) }).submit(report())
        assertEquals(ReportResult.Sent, r)
        assertEquals(1, written.size)
        assertEquals("plant_card", written[0]["feature"])
    }

    @Test
    fun `sink failure returns failed message`() = runBlocking {
        val r = AiReportRepository(NetworkChecker { true }, ReportSink { throw IllegalStateException("PERMISSION_DENIED") }).submit(report())
        assertEquals(ReportResult.Failed(AiReportRepository.MSG_FAILED), r)
    }

    @Test
    fun `hanging sink times out`() = runBlocking {
        val r = AiReportRepository(NetworkChecker { true }, ReportSink { delay(10_000) }, timeoutMs = 50).submit(report())
        assertEquals(ReportResult.Failed(AiReportRepository.MSG_FAILED), r)
    }

    @Test
    fun `caller cancellation is not swallowed`() {
        val result = runCatching {
            runBlocking {
                AiReportRepository(NetworkChecker { true }, ReportSink { throw CancellationException("cancel") }).submit(report())
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
