package com.finanzas.inteligentes.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val today = LocalDate.now().toEpochDay()
        val due = AppDb.get(c).dao().subs().first().count { it.nextEpochDay in today..(today + 1) }
        if (due == 0) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("pagos", "Pagos previstos", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(c, "pagos")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Finanzas Inteligentes")
            .setContentText(if (due == 1) "Tiene 1 pago previsto hoy o mañana." else "Tiene $due pagos previstos hoy o mañana.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
        return Result.success()
    }
}

object Reminders {
    private const val NAME = "pagos_previstos"

    fun enable(c: Context) {
        Security.setReminders(c, true)
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun disable(c: Context) {
        Security.setReminders(c, false)
        WorkManager.getInstance(c).cancelUniqueWork(NAME)
    }
}
