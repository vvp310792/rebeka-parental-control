package com.example.rebeka.accessibility

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES — единственный надёжный способ
 * проверить, включена ли именно НАША служба. AccessibilityManager.getEnabledAccessibilityServiceList()
 * тоже работает, но требует довольно новых прав на некоторых прошивках — строковая
 * проверка проще и не имеет побочных требований.
 */
object AccessibilityUtils {

    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${BlockAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        if (TextUtils.isEmpty(enabledServices)) return false

        return enabledServices.split(":").any { it.equals(expectedComponent, ignoreCase = true) }
    }
}
