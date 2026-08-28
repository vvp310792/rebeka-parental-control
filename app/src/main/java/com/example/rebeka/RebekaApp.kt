package com.example.rebeka

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.rebeka.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RebekaApp : Application() {

    // Персистентный scope на уровне приложения — операции блокировки/записи
    // не должны обрываться при смене экрана. См. паттерн из исходной архитектуры.
    val appScope = CoroutineScope(Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    fun launchPersistent(block: suspend () -> Unit) {
        appScope.launch { block() }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING, "Слежение за экранным временем",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PARENT_ALERT, "Уведомления родителю",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_PARENT_ALERT = "parent_alert"
    }
}
