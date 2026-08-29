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

    /**
     * Экраны, с которых отзывают «поверх других окон». Android сам показывает
     * уведомление об активном оверлее и не даёт его скрыть, поэтому единственное,
     * что можно сделать — не пустить на сам экран отзыва, пока блокировка активна.
     */
    private val overlaySettingsClassNames = listOf(
        "AppDrawOverlaySettings",
        "DrawOverlayDetails",
        "ManageApplications",
        "AlertWindow",
        "AppOpsDetails"
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

        // Во время блокировки Настройки вообще не открыть: ребёнок идёт туда только
        // чтобы отозвать разрешение или снять администратора.
        if (BlockState.blocked && packageName == SETTINGS_PACKAGE) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            ParentAlertNotifier(this).notifySettingsScreenOpened(className)
            return
        }

        if (overlaySettingsClassNames.any { className.contains(it, ignoreCase = true) }) {
            ParentAlertNotifier(this).notifySettingsScreenOpened(className)
        }

        if (watchedScreenClassNames.any { className.contains(it) }) {
            ParentAlertNotifier(this).notifySettingsScreenOpened(className)
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
