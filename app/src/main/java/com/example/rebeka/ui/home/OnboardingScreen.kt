package com.example.rebeka.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rebeka.admin.AdminUtils
import com.example.rebeka.usage.UsageStatsHelper

/**
 * Ни одно из этих трёх разрешений не выдаётся стандартным диалогом —
 * все требуют ручного захода в системные настройки (см. README, раздел
 * "Разрешения, которые нужно выдать вручную"). Задача экрана — не пытаться
 * это обойти (нельзя), а провести родителя по каждому пункту.
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val usageHelper = remember { UsageStatsHelper(context) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Настройка (выполняет родитель)", style = MaterialTheme.typography.headlineSmall)

        PermissionStep(
            title = "Специальные возможности",
            done = false, // реальная проверка — через AccessibilityManager.getEnabledAccessibilityServiceList
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        PermissionStep(
            title = "Доступ к статистике использования",
            done = usageHelper.hasUsageAccess(),
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )

        PermissionStep(
            title = "Администратор устройства",
            done = AdminUtils.isAdminActive(context),
            onClick = { context.startActivity(AdminUtils.requestAdminIntent(context)) }
        )

        PermissionStep(
            title = "Показ поверх других приложений",
            done = Settings.canDrawOverlays(context),
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )
    }
}

@Composable
private fun PermissionStep(title: String, done: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            if (done) Text("✓") else TextButton(onClick = onClick) { Text("Открыть") }
        }
    }
}
