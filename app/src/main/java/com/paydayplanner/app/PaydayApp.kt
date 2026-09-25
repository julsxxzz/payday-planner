package com.paydayplanner.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.paydayplanner.app.data.AppDatabase
import com.paydayplanner.app.data.SettingsRepository
import com.paydayplanner.app.reminders.BillReminderWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Simple manual dependency container shared by the whole app. */
class AppContainer(app: Application) {
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "payday.db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()
    val settings = SettingsRepository(app)
}

class PaydayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        scheduleDailyReminder()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            REMINDER_CHANNEL,
            "Bill reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Reminds you about bills that are due soon or overdue" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Checks for upcoming bills once a day, around 8:00 AM. */
    private fun scheduleDailyReminder() {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(8, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("bill-reminders", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val REMINDER_CHANNEL = "bill_reminders"
    }
}
