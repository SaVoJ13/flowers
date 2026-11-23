package com.flowers.app.data.report

import com.flowers.app.data.remote.NetworkChecker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ReportResult {
    /** Сервер підтвердив запис. */
    data object Sent : ReportResult

    /** Запис поставлено в локальну чергу Firestore і буде доставлено, щойно з'явиться зв'язок (не помилка). */
    data object Queued : ReportResult

    /** Сервер відхилив запис або сталася помилка до постановки в чергу. */
    data class Failed(val message: String) : ReportResult
}

/** Запис, що вже стартував (локально поставлений у чергу); дозволяє дочекатися підтвердження сервера. */
fun interface ReportWrite {
    /** Завершується, коли сервер підтвердив запис; кидає виняток, якщо сервер відхилив. */
    suspend fun awaitServer()
}

interface ReportSink {
    /** Згенерувати ID нового документа на пристрої (без мережі). Один ID на діалог захищає від дублів при повторі. */
    fun newId(): String

    /**
     * Стартує запис документа `reports/{docId}` (з серверним `createdAt`) і одразу повертає [ReportWrite].
     * Firestore ставить запис у локальну чергу, тож він переживе офлайн і перезапуск застосунку.
     */
    fun start(docId: String, fields: Map<String, Any>): ReportWrite
}

/** Запис у Firestore: лише create, ID з пристрою; читати скарги застосунок не може (правила). */
class FirestoreReportSink(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : ReportSink {
    override fun newId(): String = db.collection(COLLECTION).document().id

    override fun start(docId: String, fields: Map<String, Any>): ReportWrite {
        val task = db.collection(COLLECTION).document(docId)
            .set(fields + mapOf("createdAt" to FieldValue.serverTimestamp(), "expireAt" to expireAt()))
        return ReportWrite {
            suspendCancellableCoroutine { cont ->
                task.addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            }
        }
    }

    companion object {
        const val COLLECTION = "reports"
        const val RETENTION_DAYS = 365L

        /** Службова дата кінця зберігання: 12 місяців (рішення Власника O-Q6; TTL недоступний на Spark, ADR-019). */
        fun expireAt(nowMs: Long = System.currentTimeMillis()) =
            Timestamp(java.util.Date(nowMs + RETENTION_DAYS * 24 * 3600 * 1000))
    }
}

/**
 * Надсилання скарги (TASK-027, TASK-048). Запис завжди одразу ставиться в локальну чергу Firestore.
 * Без мережі або якщо сервер не відповів за 15 с, повертаємо [ReportResult.Queued] (запис доставиться пізніше,
 * зокрема після перезапуску), а не помилку: інакше користувач повторював би скаргу й створював дублі.
 * Дублі також блокує один `docId` на діалог: повторне надсилання з тим самим ID не створює другого документа (правила забороняють update, тож сервер відхилить повтор, якщо перший запис уже на сервері).
 * Якщо перевірка мережі сама впала, вважаємо, що мережа є (ADR-018).
 */
class AiReportRepository(
    private val network: NetworkChecker,
    private val sink: ReportSink,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    fun newReportId(): String = sink.newId()

    suspend fun submit(docId: String, report: AiReport): ReportResult {
        val write = try {
            sink.start(docId, report.toFields())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReportResult.Failed(MSG_FAILED)
        }
        if (!network.isOnline()) return ReportResult.Queued
        return try {
            withTimeout(timeoutMs) { write.awaitServer() }
            ReportResult.Sent
        } catch (e: TimeoutCancellationException) {
            ReportResult.Queued
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            ReportResult.Failed(MSG_FAILED)
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val MSG_QUEUED = "Скаргу буде надіслано, щойно з'явиться зв'язок."
        const val MSG_FAILED = "Не вдалося надіслати скаргу. Спробуйте ще раз пізніше."
        const val MSG_THANKS = "Дякуємо! Скаргу надіслано."
    }
}
