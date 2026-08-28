package com.example.rebeka.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rebeka.data.AppSettings
import com.example.rebeka.data.DayStats
import com.example.rebeka.data.StatsRepository
import com.example.rebeka.util.TimeLimitCalculator
import kotlinx.coroutines.flow.combine

/**
 * UI читает Flow напрямую через collectAsState — без ViewModel,
 * как в исходной архитектуре (осознанное упрощение для проекта такого масштаба).
 */
@Composable
fun HomeScreen(repository: StatsRepository) {
    val today by repository.observeToday().collectAsState(initial = DayStats(epochDay = 0))
    val settings by repository.observeSettings().collectAsState(initial = AppSettings())

    val remainingMillis = TimeLimitCalculator.remainingMillis(
        settings.baseLimitMinutes, today.steps, settings.stepsPerBonusHour, today.usedMillis
    )
    val remainingMinutes = remainingMillis / 60_000

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Сегодня", style = MaterialTheme.typography.headlineMedium)

        StatCard(label = "Шаги", value = "${today.steps}")
        StatCard(
            label = "Осталось экранного времени",
            value = "${remainingMinutes} мин"
        )
        StatCard(
            label = "Лимит на сегодня",
            value = "${settings.baseLimitMinutes / 60}ч + бонус за шаги"
        )

        if (today.blocked) {
            Text(
                "Лимит исчерпан — телефон заблокирован",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
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
