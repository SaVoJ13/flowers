package com.flowers.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.domain.ReminderCalculator
import kotlinx.coroutines.flow.first

class DailyReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "DailyPlantReminderWork"
    }

    override suspend fun doWork(): Result {
        return try {
            val database = FlowersDatabase.getInstance(context)
            val now = System.currentTimeMillis()
            val endOfDay = now + 86400000L
            val duePlants = database.plantDao().getPlantsDueForCheck(endOfDay).first()

            if (duePlants.isNotEmpty()) {
                val title = "Час оглянути рослини"
                val message = if (duePlants.size == 1) {
                    val plant = duePlants.first()
                    "Час перевірити ${plant.customName}: перевірте вологість ґрунту та стан листя."
                } else {
                    "Сьогодні на перевірку очікують ${duePlants.size} вазонів. Перевірте ґрунт!"
                }
                NotificationHelper.showReminderNotification(context, title, message)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
