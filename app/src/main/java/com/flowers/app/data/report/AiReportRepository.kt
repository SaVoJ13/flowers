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
    data object Sent : ReportResult
    data class Failed(val message: String) : ReportResult
}

fun interface ReportSink {
    /** Записує поля нового документа в `reports` (з серверним `createdAt`). Кидає виняток при збої. */
    suspend fun create(fields: Map<String, Any>)
}

/** Запис у Firestore: лише create, автоген ID; читати скарги застосунок не може (правила). */
class FirestoreReportSink(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : ReportSink {
    override suspend fun create(fields: Map<String, Any>) {
        suspendCancellableCoroutine { cont ->
            db.collection(COLLECTION).add(fields + mapOf("createdAt" to FieldValue.serverTimestamp(), "expireAt" to expireAt()))
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    companion object {
        const val COLLECTION = "reports"
        const val RETENTION_DAYS = 365L

        /** Дата видалення TTL-політикою: зберігання 12 місяців (рішення Власника O-Q6). */
        fun expireAt(nowMs: Long = System.currentTimeMillis()) =
            Timestamp(java.util.Date(nowMs + RETENTION_DAYS * 24 * 3600 * 1000))
    }
}

/**
 * Надсилання скарги (TASK-027). Без мережі не пробуємо (Firestore інакше мовчки ставить запис у чергу
 * і `await()` не завершується); таймаут 15 с. Якщо перевірка мережі сама впала, пробуємо (як для ШІ, ADR-018).
 */
class AiReportRepository(
    private val network: NetworkChecker,
    private val sink: ReportSink,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    suspend fun submit(report: AiReport): ReportResult {
        if (!network.isOnline()) return ReportResult.Failed(MSG_OFFLINE)
        return try {
            withTimeout(timeoutMs) { sink.create(report.toFields()) }
            ReportResult.Sent
        } catch (e: TimeoutCancellationException) {
            ReportResult.Failed(MSG_FAILED)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            ReportResult.Failed(MSG_FAILED)
        }
    }

    companion object {
        const val TIMEOUT_MS = 15_000L
        const val MSG_OFFLINE = "Немає підключення до інтернету. Скаргу не надіслано: спробуйте ще раз, коли з'явиться мережа."
        const val MSG_FAILED = "Не вдалося надіслати скаргу. Спробуйте ще раз пізніше."
        const val MSG_THANKS = "Дякуємо! Скаргу надіслано."
    }
}
