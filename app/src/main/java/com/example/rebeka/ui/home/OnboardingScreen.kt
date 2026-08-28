package com.example.rebeka.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rebeka.accessibility.AccessibilityUtils
import com.example.rebeka.admin.AdminUtils
import com.example.rebeka.usage.UsageStatsHelper

/**
 * Ни одна из этих проверок не подписана на системные события — Android просто
 * не уведомляет приложение, когда пользователь меняет эти настройки. Поэтому
 * состояние пересчитывается при каждом возврате в приложение (ON_RESUME),
 * а не один раз при первом открытии экрана — иначе галочки не появлялись бы,
 * даже когда родитель реально всё включил в системных настройках.
 */
@Composable
fun OnboardingScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageHelper = remember { UsageStatsHelper(context) }

    var accessibilityDone by remember { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }
    var usageDone by remember { mutableStateOf(usageHelper.hasUsageAccess()) }
    var adminDone by remember { mutableStateOf(AdminUtils.isAdminActive(context)) }
    var overlayDone by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var stepsPermissionDone by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityDone = AccessibilityUtils.isServiceEnabled(context)
                usageDone = usageHelper.hasUsageAccess()
                adminDone = AdminUtils.isAdminActive(context)
                overlayDone = Settings.canDrawOverlays(context)
                stepsPermissionDone = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allDone = accessibilityDone && usageDone && adminDone && overlayDone && stepsPermissionDone

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройка (выполняет родитель)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Без этих разрешений приложение не увидит ни шаги, ни экранное время — " +
                "они не выдаются системным диалогом, только вручную.",
            style = MaterialTheme.typography.bodySmall
        )

        PermissionStep(
            title = "Учёт шагов",
            done = stepsPermissionDone,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                )
            }
        )

        PermissionStep(
            title = "Специальные возможности",
            done = accessibilityDone,
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        PermissionStep(
            title = "Доступ к статистике использования",
            done = usageDone,
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )

        PermissionStep(
            title = "Администратор устройства",
            done = adminDone,
            onClick = { context.startActivity(AdminUtils.requestAdminIntent(context)) }
        )

        PermissionStep(
            title = "Показ поверх других приложений",
            done = overlayDone,
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        Button(onClick = onAllGranted, enabled = allDone, modifier = Modifier.fillMaxWidth()) {
            Text(if (allDone) "Готово" else "Осталось включить пункты выше")
        }
    }
}

@Composable
private fun PermissionStep(title: String, done: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f).padding(end = 8.dp))
            if (done) {
                Text("✓", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onClick) { Text("Открыть", maxLines = 1) }
            }
        }
    }
}
