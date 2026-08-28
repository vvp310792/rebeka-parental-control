package com.example.rebeka.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.rebeka.notifications.ParentAlertNotifier

/**
 * onDisableRequested() вызывается ПЕРЕД тем как система покажет диалог
 * "Деактивировать администратора устройства?" — но он не может ЗАБЛОКИРОВАТЬ
 * это действие, только предупредить пользователя текстом в самом диалоге
 * и среагировать в приложении (см. README про честные границы этого подхода).
 *
 * Основная защита — не здесь, а в BlockAccessibilityService, которая ловит
 * сам факт открытия экрана "Администраторы устройства" ещё до нажатия кнопки.
 */
class RebekaDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        ParentAlertNotifier(context).notifyAdminDisableAttempt()
        return "Отключение контроля потребует PIN родителя в самом приложении Rebeka."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Права сняты — последний шанс зафиксировать факт и уведомить ещё раз.
        ParentAlertNotifier(context).notifyAdminDisabled()
    }
}
