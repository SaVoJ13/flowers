package com.flowers.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flowers.app.data.local.FlowersDatabase
import com.flowers.app.data.remote.AndroidNetworkChecker
import com.flowers.app.data.report.AiReportRepository
import com.flowers.app.data.report.FirestoreReportSink
import com.flowers.app.data.repository.FlowersRepositoryImpl
import com.flowers.app.data.security.AppCheckHelper
import com.flowers.app.data.worker.DailyReminderWorker
import com.flowers.app.data.worker.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.initialize
import java.util.Calendar
import java.util.concurrent.TimeUnit

class FlowersApplication : Application() {

    val database by lazy { FlowersDatabase.getInstance(this) }
    val repository by lazy {
        FlowersRepositoryImpl(
            plantDao = database.plantDao(),
            careEventDao = database.careEventDao(),
            catalogDao = database.catalogDao(),
            carePodDao = database.carePodDao(),
            context = this
        )
    }

    /** Скарги на відповіді ШІ (TASK-027); Firestore ініціалізується ліниво, лише при надсиланні. */
    val aiReportRepository by lazy { AiReportRepository(AndroidNetworkChecker(this), FirestoreReportSink()) }

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        AppCheckHelper.initAppCheck()
        NotificationHelper.createNotificationChannel(this)
        scheduleDailyReminder()
    }

    private fun scheduleDailyReminder() {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelay = target.timeInMillis - now.timeInMillis

        val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
