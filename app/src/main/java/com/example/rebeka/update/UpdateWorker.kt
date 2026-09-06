package com.example.rebeka.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.rebeka.RebekaApp
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка обновлений — WorkManager, а не что-то привязанное к
 * открытому экрану, поэтому работает в любом режиме приложения: и когда
 * ребёнок просто пользуется телефоном, и когда висит блокирующий оверлей,
 * и когда приложение вообще свёрнуто. Планируется один раз при старте
 * приложения (см. RebekaApp.onCreate) и переживает перезагрузку — это
 * штатное поведение WorkManager, отдельный BootReceiver не нужен.
 *
 * Проверяет и скачивает новый APK молча; поставить его без тапа пользователя
 * по уведомлению нельзя — то же системное ограничение, что описано в
 * UpdateManager.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = (UpdateManager.check() as? UpdateManager.CheckResult.Available)?.info
            ?: return Result.success()

        return try {
            val apk = UpdateManager.download(applicationContext, info)
            notifyReady(apk)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyReady(apk: File) {
        val context = applicationContext
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            UpdateManager.installIntent(context, apk),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, RebekaApp.CHANNEL_UPDATE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Доступно обновление ChildStep")
            .setContentText("Нажмите, чтобы установить")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val UNIQUE_WORK_NAME = "update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            // KEEP: если работа уже запланирована с прошлого запуска приложения,
            // не сбрасываем её расписание на каждый onCreate.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
