package com.example.rebeka.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

class UsageStatsHelper(private val context: Context) {

    /**
     * PACKAGE_USAGE_STATS не выдаётся системным диалогом — пользователь должен
     * вручную включить в Настройки → Особый доступ → Доступ к использованию.
     * Эта проверка нужна для onboarding-экрана, чтобы вести родителя туда.
     */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Суммарное экранное время устройства с полуночи, миллисекунды. */
    fun usedMillisToday(): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        // queryAndAggregateUsageStats даёт суммарное totalTimeInForeground по пакетам —
        // складываем все пользовательские (не системные launcher/settings) пакеты.
        val stats = usm.queryAndAggregateUsageStats(startOfDay, now)
        return stats.values
            .filterNot { it.packageName == context.packageName }
            .sumOf { it.totalTimeInForeground }
    }
}
