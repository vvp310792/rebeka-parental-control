package com.example.rebeka.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rebeka.admin.AdminUtils
import com.example.rebeka.data.AppSettings
import com.example.rebeka.data.DayStats
import com.example.rebeka.data.StatsRepository
import com.example.rebeka.usage.UsageStatsHelper
import com.example.rebeka.util.TimeLimitCalculator
import kotlinx.coroutines.delay

/**
 * Раньше экран показывал только "осталось" и молча зависел от того, что сервис
 * успел записать в Room. Теперь: показываем и потраченное, и лимит, и из чего он
 * состоит, а данные обновляются сразу при открытии экрана и далее раз в 10 секунд,
 * пока экран открыт — не ждём 30-секундного тика сервиса.
 */
@Composable
fun HomeScreen(repository: StatsRepository, onOpenParentSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageHelper = remember { UsageStatsHelper(context) }

    val today by repository.observeToday().collectAsState(initial = DayStats(epochDay = 0))
    val settings by repository.observeSettings().collectAsState(initial = AppSettings())

    // Немедленный пересчёт при открытии + периодически, пока экран виден.
    LaunchedEffect(Unit) {
        while (true) {
            if (usageHelper.hasUsageAccess()) {
                repository.updateUsedMillis(usageHelper.usedMillisToday())
            }
            delay(10_000)
        }
    }

    // И ещё раз при каждом возврате в приложение, чтобы цифра не была устаревшей.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && usageHelper.hasUsageAccess()) {
                // suspend-вызов из обсервера — короткая запись, безопасно в IO-scope приложения
                val app = context.applicationContext as com.example.rebeka.RebekaApp
                app.launchPersistent { repository.updateUsedMillis(usageHelper.usedMillisToday()) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val limitMillis = TimeLimitCalculator.limitMillis(
        settings.baseLimitMinutes, today.steps, settings.stepsPerBonusHour
    )
    val remainingMillis = TimeLimitCalculator.remainingMillis(
        settings.baseLimitMinutes, today.steps, settings.stepsPerBonusHour, today.usedMillis
    )
    val bonusMillis = TimeLimitCalculator.bonusMillis(today.steps, settings.stepsPerBonusHour)
    val progress = if (limitMillis > 0) (today.usedMillis.toFloat() / limitMillis).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Сегодня", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Экранное время", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatDuration(today.usedMillis), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "  из ${formatDuration(limitMillis)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Text(
                    "Осталось: ${formatDuration(remainingMillis)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        StatCard(label = "Шаги", value = "${today.steps}")

        StatCard(
            label = "Из чего складывается лимит",
            value = "${settings.baseLimitMinutes / 60} ч базовых + ${formatDuration(bonusMillis)} за шаги"
        )

        StatCard(
            label = "Курс обмена",
            value = "${settings.stepsPerBonusHour} шагов = 1 час"
        )

        StatCard(
            label = "Заработано последними 1000 шагами",
            value = formatDuration(
                TimeLimitCalculator.bonusMillis(1000, settings.stepsPerBonusHour)
            )
        )

        if (today.blocked) {
            Text(
                "Лимит исчерпан — телефон заблокирован",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!usageHelper.hasUsageAccess()) {
            Text(
                "Нет доступа к статистике использования — экранное время не считается. " +
                    "Проверьте разрешения в настройках.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                val app = context.applicationContext as com.example.rebeka.RebekaApp
                app.launchPersistent { repository.startForcedBlock() }
            },
            enabled = settings.pinHash.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Заблокировать экран (тест)")
        }

        Text(
            if (settings.pinHash.isEmpty())
                "Сначала задайте PIN родителя — иначе блокировку будет нечем снять."
            else
                "Снять блокировку можно только PIN родителя или ${settings.stepsPerBonusHour} шагами. " +
                    "Окно появится в течение 5 секунд.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!AdminUtils.isAdminActive(context)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Приложение можно удалить!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Права администратора устройства не выданы. Без них система " +
                            "разрешает удалить ChildStep обычным способом с рабочего стола.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { context.startActivity(AdminUtils.requestAdminIntent(context)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Выдать права администратора") }
                }
            }
        }

        OutlinedButton(onClick = onOpenParentSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Настройки родителя")
        }

        Text(
            "Версия ${com.example.rebeka.BuildConfig.VERSION_NAME} (${com.example.rebeka.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
