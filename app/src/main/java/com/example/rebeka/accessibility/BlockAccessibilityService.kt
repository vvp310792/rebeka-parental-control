package com.example.rebeka.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.rebeka.blocking.BlockState
import com.example.rebeka.notifications.ParentAlertNotifier

/**
 * Две задачи:
 *
 * 1. Пока висит блокировка — не давать открыть шторку. Полностью запретить её
 *    может только Device Owner (setStatusBarDisabled); стороннее приложение
 *    может лишь закрыть её сразу после открытия. Практически это выглядит так,
 *    что шторка «не открывается»: она схлопывается за доли секунды, и добраться
 *    до переключателя «поверх других окон» ребёнок не успевает.
 *
 * 2. Ловить открытие экранов «Администраторы устройства» и «О приложении»,
 *    откуда идут снятие прав и удаление, и уведомлять родителя.
 */
class BlockAccessibilityService : AccessibilityService() {

    private val watchedScreenClassNames = listOf(
        "com.android.settings.DeviceAdminSettings",
        "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminSettings",
        "com.android.settings.applications.InstalledAppDetails"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()

        // Шторка и панель быстрых настроек живут в systemui. Пока блокировка
        // активна — немедленно закрываем всё, что оттуда открылось.
        if (BlockState.blocked && packageName == SYSTEM_UI_PACKAGE) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val className = event.className?.toString() ?: return

        if (watchedScreenClassNames.any { className.contains(it) }) {
            ParentAlertNotifier(this).notifySettingsScreenOpened(className)
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
