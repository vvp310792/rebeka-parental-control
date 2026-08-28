package com.example.rebeka.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.rebeka.RebekaApp

/**
 * Два канала оповещения:
 * 1. Локальное уведомление на самом телефоне ребёнка (работает всегда, без бэкенда)
 * 2. Пуш на телефон родителя — требует серверной части (FCM + хотя бы простой
 *    backend, который знает пару "устройство ребёнка ↔ токен родителя"). Это
 *    отдельная задача, не входит в объём текущей архитектуры — здесь только
 *    точка расширения (sendRemote), чтобы код на клиенте не пришлось переписывать.
 */
class ParentAlertNotifier(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)

    fun notifySettingsScreenOpened(screenClassName: String) {
        showLocal(
            id = 100,
            title = "Открыт экран настроек",
            text = "Ребёнок открыл: $screenClassName"
        )
        sendRemote("settings_screen_opened", mapOf("screen" to screenClassName))
    }

    fun notifyAdminDisableAttempt() {
        showLocal(
            id = 101,
            title = "Попытка отключить контроль",
            text = "Ребёнок пытается снять права администратора устройства"
        )
        sendRemote("admin_disable_attempt", emptyMap())
    }

    fun notifyAdminDisabled() {
        showLocal(
            id = 102,
            title = "Контроль отключён",
            text = "Права администратора устройства сняты"
        )
        sendRemote("admin_disabled", emptyMap())
    }

    private fun showLocal(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, RebekaApp.CHANNEL_PARENT_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }

    /**
     * Точка расширения под реальный backend. Пока — no-op с TODO.
     * Вариант без своего сервера: Firebase Cloud Messaging + Firestore
     * (тот же Firebase-проект, что уже заведён под sync, см. исходную
     * архитектуру) — топик или токен родителя хранится в AppSettings.
     */
    private fun sendRemote(event: String, payload: Map<String, String>) {
        // TODO: реализовать через Firebase Cloud Messaging, когда заведён backend
    }
}
