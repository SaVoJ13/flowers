package com.flowers.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flowers.app.data.local.FlowersDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

class DailyReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "DailyPlantReminderWork"

        fun formatReminderMessage(plantNames: List<String>): String {
            return when {
                plantNames.isEmpty() -> ""
                plantNames.size == 1 -> "Час перевірити ґрунт у ${plantNames.first()}"
                plantNames.size == 2 -> "Час перевірити ґрунт у ${plantNames[0]} та ${plantNames[1]}"
                else -> {
                    val firstTwo = "${plantNames[0]}, ${plantNames[1]}"
                    val remaining = plantNames.size - 2
                    val remainderWord = when {
                        remaining % 10 == 1 && remaining % 100 != 11 -> "рослини"
                        else -> "рослин"
                    }
                    "Час перевірити ґрунт у $firstTwo та ще $remaining $remainderWord"
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val database = FlowersDatabase.getInstance(context)
            val now = System.currentTimeMillis()
            val endOfDay = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val duePlants = database.plantDao().getPlantsDueForCheck(endOfDay).first()

            if (duePlants.isNotEmpty()) {
                val title = "Час перевірити ґрунт"
                val message = formatReminderMessage(duePlants.map { it.customName })
                NotificationHelper.showReminderNotification(context, title, message)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
