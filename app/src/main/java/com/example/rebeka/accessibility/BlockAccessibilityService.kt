package com.example.rebeka.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.rebeka.notifications.ParentAlertNotifier

/**
 * Слушает смену окон внутри com.android.settings (см. accessibility_service_config.xml,
 * packageNames ограничен именно этим пакетом — не читаем ничего в других приложениях).
 *
 * Задача не "заблокировать" системный экран (это невозможно для стороннего
 * приложения без Device Owner), а поймать сам факт, что ребёнок туда зашёл,
 * и сразу предупредить родителя, пока он ещё не успел довести дело до конца.
 */
class BlockAccessibilityService : AccessibilityService() {

    private val watchedScreenClassNames = listOf(
        // экран "Администраторы устройства"
        "com.android.settings.DeviceAdminSettings",
        "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminSettings",
        // экран информации о приложении, откуда идёт Force Stop / Uninstall
        "com.android.settings.applications.InstalledAppDetails"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val className = event.className?.toString() ?: return

        if (watchedScreenClassNames.any { className.contains(it) }) {
            // Для экрана "информация о приложении" стоит убедиться, что открыт
            // именно наш пакет, а не случайное другое приложение —
            // event.packageName в реальной реализации сверяется с rootInActiveWindow.
            ParentAlertNotifier(this).notifySettingsScreenOpened(className)
        }
    }

    override fun onInterrupt() {}
}
