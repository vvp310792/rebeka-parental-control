package com.example.rebeka.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.rebeka.blocking.BlockState
import com.example.rebeka.notifications.ParentAlertNotifier

/**
 * Три задачи:
 *
 * 1. Пока висит блокировка — не давать открыть шторку и Настройки.
 * 2. ВСЕГДА (не только во время блокировки) перехватывать диалог удаления
 *    ЭТОГО приложения и экраны, откуда снимают права администратора. Именно
 *    здесь была дыра: на телефоне без выданных прав администратора ребёнок
 *    просто удалял приложение с рабочего стола, и ничего этому не мешало.
 * 3. Уведомлять родителя о каждой такой попытке.
 *
 * БАГ, который здесь был исправлен: пункт 2 проверял только имя ПАКЕТА
 * системного установщика (com.android.packageinstaller и т.п.) и общие имена
 * классов экранов Настроек. Но тот же пакет-установщик показывает и диалог
 * УСТАНОВКИ, не только удаления — а "InstalledAppDetails"/"AppInfoDashboard"
 * это класс экрана «Инфо о приложении» для ЛЮБОГО приложения, не только
 * этого. В итоге срабатывало на установку/удаление ЛЮБОГО чужого приложения,
 * не только на попытку удалить именно Rebeka. Теперь для диалога удаления
 * дополнительно проверяется className (диалог именно "Uninstall...", а не
 * "Install..."), а для экрана "Инфо о приложении"/оверлея — что на экране
 * реально фигурирует название ЭТОГО приложения (см. isForThisApp).
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

    /**
     * Экраны Настроек, которые не привязаны к конкретному приложению — блокируются
     * всегда, независимо от того, о каком приложении речь (это списки/общие экраны,
     * "своего" приложения там просто нет, сравнивать не с чем).
     */
    private val globalProtectedScreenClassNames = listOf(
        "DeviceAdminSettings",
        "DeviceAdminAdd",
        "AppDrawOverlaySettings"
    )

    /**
     * Экраны, которые Android показывает ОДНИМ И ТЕМ ЖЕ классом для любого
     * приложения (страница "Инфо о приложении", детали разрешения на оверлей).
     * Блокировать их можно только когда на экране реально это приложение —
     * иначе блокируется управление ЛЮБЫМ чужим приложением через Настройки.
     */
    private val perAppProtectedScreenClassNames = listOf(
        "InstalledAppDetails",
        "AppInfoDashboard",
        "DrawOverlayDetails",
        "AlertWindow"
    )

    /** Название этого приложения ("ChildStep") — по нему отличаем "про нас" от "про кого-то ещё". */
    private val ownAppLabel: String by lazy {
        runCatching { applicationInfo.loadLabel(packageManager).toString() }.getOrDefault("")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        // Шторка живёт в systemui. Пока блокировка активна — закрываем сразу.
        if (BlockState.blocked && packageName == SYSTEM_UI_PACKAGE) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Диалог удаления — перехватывается всегда, независимо от блокировки, но
        // только если это (а) правда диалог УДАЛЕНИЯ, а не установки, и (б) правда
        // про ЭТО приложение, а не про любое другое, которое ребёнок или родитель
        // ставит/удаляет обычным способом.
        if (BlockState.uninstallProtectionActive &&
            packageName in uninstallerPackages &&
            className.contains("uninstall", ignoreCase = true) &&
            isForThisApp()
        ) {
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

            if (!BlockState.uninstallProtectionActive) return

            val isGlobalScreen = globalProtectedScreenClassNames.any { className.contains(it, ignoreCase = true) }
            val isPerAppScreen = perAppProtectedScreenClassNames.any { className.contains(it, ignoreCase = true) }

            // Общие экраны блокируем всегда; экраны конкретного приложения — только
            // когда на экране действительно это приложение, а не любое другое.
            if (isGlobalScreen || (isPerAppScreen && isForThisApp())) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                ParentAlertNotifier(this).notifySettingsScreenOpened(className)
            }
        }
    }

    /**
     * Ищет название этого приложения в тексте текущего окна (заголовок диалога
     * удаления, заголовок страницы "Инфо о приложении" и т.п.) — единственный
     * способ для Accessibility Service понять, о каком именно приложении экран,
     * без доступа к Intent, которым он был открыт. Не идеально (зависит от языка
     * системы), но резко сокращает ложные срабатывания на чужие приложения —
     * тот же принцип "задержка/явное действие", что и у остальной защиты в этом
     * проекте (см. README), а не гарантия без единого исключения.
     */
    private fun isForThisApp(): Boolean {
        if (ownAppLabel.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        return nodeContainsText(root, ownAppLabel, depth = 0)
    }

    private fun nodeContainsText(node: AccessibilityNodeInfo, needle: String, depth: Int): Boolean {
        if (depth > MAX_NODE_DEPTH) return false
        node.text?.toString()?.let { if (it.contains(needle, ignoreCase = true)) return true }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (nodeContainsText(child, needle, depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() {}

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val SETTINGS_PACKAGE = "com.android.settings"

        // Разметка системных экранов не бывает глубже нескольких десятков уровней —
        // предохранитель от зависания на аномальном дереве узлов.
        private const val MAX_NODE_DEPTH = 40
    }
}
