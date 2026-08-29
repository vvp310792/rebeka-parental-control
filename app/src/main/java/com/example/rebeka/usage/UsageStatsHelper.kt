package com.example.rebeka.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

class UsageStatsHelper(private val context: Context) {

    /**
     * PACKAGE_USAGE_STATS не выдаётся системным диалогом — пользователь должен
     * вручную включить в Настройки → Особый доступ → Доступ к использованию.
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

    /**
     * Реальное экранное время с полуночи.
     *
     * Раньше здесь суммировался totalTimeInForeground по всем пакетам — это
     * завышало результат в разы: складывались перекрывающиеся интервалы, лаунчер,
     * системный UI и фоновые процессы. Из-за этого лимит "исчерпывался" мгновенно.
     *
     * Правильный способ — идти по событиям ACTIVITY_RESUMED/ACTIVITY_PAUSED и
     * складывать только фактические интервалы, когда приложение было на экране,
     * исключая лаунчер, системный UI и само это приложение.
     */
    fun usedMillisToday(): Long {
        if (!hasUsageAccess()) return 0

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val excluded = excludedPackages()
        val events = usm.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        var total = 0L
        var currentPackage: String? = null
        var currentStart = 0L

        fun closeInterval(endTime: Long) {
            val pkg = currentPackage
            if (pkg != null && currentStart > 0 && pkg !in excluded) {
                total += (endTime - currentStart).coerceAtLeast(0)
            }
            currentPackage = null
            currentStart = 0
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Переключение между приложениями: PAUSED предыдущего может не прийти
                    // вовсе (частое поведение на MIUI), поэтому закрываем интервал здесь.
                    closeInterval(event.timeStamp)
                    currentPackage = event.packageName
                    currentStart = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    // Раньше здесь сбрасывался текущий интервал даже для ЧУЖОГО пакета —
                    // из-за этого терялось почти всё время, и счётчик показывал минуты
                    // вместо часов. Реагируем только на события того пакета, что сейчас
                    // на экране.
                    if (currentPackage == event.packageName) closeInterval(event.timeStamp)
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> closeInterval(event.timeStamp)
            }
        }

        // Приложение открыто прямо сейчас — досчитываем незакрытый интервал.
        closeInterval(now)

        return total
    }

    /**
     * Лаунчер, системный UI и само приложение не считаются экранным временем —
     * иначе каждое возвращение на рабочий стол капало бы в лимит.
     */
    private fun excludedPackages(): Set<String> {
        val excluded = mutableSetOf(context.packageName, "android", "com.android.systemui")

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(launcherIntent, 0)
            ?.activityInfo?.packageName?.let { excluded.add(it) }

        return excluded
    }
}
