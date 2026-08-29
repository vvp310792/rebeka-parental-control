package com.example.rebeka.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.rebeka.blocking.BlockState
import com.example.rebeka.notifications.ParentAlertNotifier

/**
 * Три задачи:
 *
 * 1. Пока висит блокировка — не давать открыть шторку и Настройки.
 * 2. ВСЕГДА (не только во время блокировки) перехватывать диалог удаления
 *    приложения и экраны, откуда снимают права администратора. Именно здесь была
 *    дыра: на телефоне без выданных прав администратора ребёнок просто удалял
 *    приложение с рабочего стола, и ничего этому не мешало.
 * 3. Уведомлять родителя о каждой такой попытке.
 *
 * Родитель может временно снять защиту от удаления в настройках под PIN —
 * см. BlockState.allowUninstall().
 */
class BlockAccessibilityService : AccessibilityService() {

    /** Диалог удаления: у каждого вендора свой установщик пакетов. */
    private val uninstallerPackages = listOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.huawei.packageinstaller",
        "com.oppo.packageinstaller",
        "com.vivo.packageinstaller"
    )

    /** Экраны, откуда снимают администратора или идут к удалению. */
    private val protectedScreenClassNames = listOf(
        "DeviceAdminSettings",
        "DeviceAdminAdd",
        "InstalledAppDetails",
        "AppInfoDashboard",
        "UninstallerActivity",
        "AppDrawOverlaySettings",
        "DrawOverlayDetails",
        "AlertWindow"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        // Шторка живёт в systemui. Пока блокировка активна — закрываем сразу.
        if (BlockState.blocked && packageName == SYSTEM_UI_PACKAGE) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Диалог удаления — перехватывается всегда, независимо от блокировки.
        // Ребёнок удаляет приложение именно тогда, когда телефон не заблокирован.
        if (BlockState.uninstallProtectionActive && packageName in uninstallerPackages) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            ParentAlertNotifier(this).notifyUninstallAttempt()
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (packageName == SETTINGS_PACKAGE) {
            // Во время блокировки Настройки не открыть вообще: идти туда ребёнку
            // незачем, кроме как чтобы обойти блокировку.
            if (BlockState.blocked) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                ParentAlertNotifier(this).notifySettingsScreenOpened(className)
                return
            }

            // Вне блокировки закрываем только опасные экраны: снятие админа,
            // страницу приложения (откуда «Удалить»), отзыв показа поверх окон.
            if (BlockState.uninstallProtectionActive &&
                protectedScreenClassNames.any { className.contains(it, ignoreCase = true) }
            ) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                ParentAlertNotifier(this).notifySettingsScreenOpened(className)
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
