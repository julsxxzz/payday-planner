package com.paydayplanner.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paydayplanner.app.MainActivity
import com.paydayplanner.app.PaydayApp
import com.paydayplanner.app.R
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import com.paydayplanner.app.domain.OVERDUE_LOOKBACK_DAYS
import com.paydayplanner.app.domain.buildBillDues
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Posts one notification listing unpaid bills that are overdue or due within the next few days. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val container = (ctx as PaydayApp).container
        val settings = container.settings.settings.first()
        if (!settings.remindersEnabled) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val today = LocalDate.now()
        val from = today.minusDays(OVERDUE_LOOKBACK_DAYS)
        val to = today.plusDays(settings.remindDaysBefore.toLong())
        val bills = container.db.billDao().activeOnce()
        val payments = container.db.expenseDao().billPaymentsDueBetweenOnce(from.toEpochDay(), to.toEpochDay())
        val unpaid = buildBillDues(bills, from, to, payments).filter { !it.paid }
        if (unpaid.isEmpty()) return Result.success()

        val lines = unpaid.map { due ->
            val whenText = when {
                due.dueDate.isBefore(today) -> "overdue since ${Dates.short(due.dueDate)}"
                due.dueDate == today -> "due today"
                else -> "due ${Dates.short(due.dueDate)}"
            }
            "${due.bill.name} · ${Money.format(due.bill.amountCents, settings.currency)} · $whenText"
        }
        val total = Money.format(unpaid.sumOf { it.bill.amountCents }, settings.currency)
        val title = if (unpaid.size == 1) "1 bill to pay" else "${unpaid.size} bills to pay"

        val openApp = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().setSummaryText("Total $total")
        lines.take(6).forEach { style.addLine(it) }

        val notification = NotificationCompat.Builder(ctx, PaydayApp.REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
        return Result.success()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
