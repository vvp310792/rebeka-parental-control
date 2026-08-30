package com.example.rebeka.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rebeka.admin.AdminUtils
import com.example.rebeka.data.StatsRepository
import kotlinx.coroutines.launch

/**
 * Родительские настройки. Закрыты PIN-ом: без этого ребёнок просто выставил бы
 * себе 10 шагов на бонусный час и обошёл всю систему.
 */
@Composable
fun ParentSettingsScreen(repository: StatsRepository, onDone: () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }

    if (!unlocked) {
        PinGate(repository = repository, onSuccess = { unlocked = true }, onCancel = onDone)
    } else {
        SettingsForm(repository = repository, onDone = onDone)
    }
}

@Composable
private fun PinGate(repository: StatsRepository, onSuccess: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки родителя", style = MaterialTheme.typography.headlineSmall)
        Text("Введите PIN, чтобы изменить правила.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= AdminUtils.PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null }
            },
            label = { Text("PIN (${AdminUtils.PIN_LENGTH} цифр)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                checking = true
                scope.launch {
                    if (repository.verifyPin(pin)) onSuccess()
                    else { error = "Неверный PIN"; pin = "" }
                    checking = false
                }
            },
            enabled = pin.length == AdminUtils.PIN_LENGTH && !checking,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (checking) "Проверка…" else "Войти") }

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }
}

@Composable
private fun SettingsForm(repository: StatsRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()

    var steps by remember { mutableStateOf("") }
    var baseHours by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var allowedUninstall by remember { mutableStateOf(false) }
    var stepsReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = repository.getSettings()
        steps = s.stepsPerBonusHour.toString()
        baseHours = (s.baseLimitMinutes / 60).toString()
        loaded = true
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки родителя", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = steps,
            onValueChange = { if (it.all(Char::isDigit) && it.length <= 6) { steps = it; error = null; saved = false } },
            label = { Text("Шагов за один бонусный час") },
            supportingText = { Text("Столько же шагов снимает блокировку досрочно") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = baseHours,
            onValueChange = { if (it.all(Char::isDigit) && it.length <= 2) { baseHours = it; error = null; saved = false } },
            label = { Text("Базовый лимит, часов в день") },
            supportingText = { Text("Без учёта бонусов за шаги") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (saved) Text("Сохранено", color = MaterialTheme.colorScheme.primary)

        Button(
            onClick = {
                val stepsValue = steps.toIntOrNull()
                val hoursValue = baseHours.toIntOrNull()
                when {
                    stepsValue == null || stepsValue < 100 ->
                        error = "Шагов должно быть не меньше 100 — иначе бонус обесценивается"
                    hoursValue == null || hoursValue !in 0..24 ->
                        error = "Базовый лимит — от 0 до 24 часов"
                    else -> {
                        scope.launch {
                            repository.saveSettings(
                                repository.getSettings().copy(
                                    stepsPerBonusHour = stepsValue,
                                    baseLimitMinutes = hoursValue * 60
                                )
                            )
                            saved = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить") }

        OutlinedButton(onClick = {
            scope.launch {
                repository.resetTodaySteps()
                stepsReset = true
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Сбросить шаги за сегодня")
        }

        if (stepsReset) {
            Text(
                "Шаги за сегодня обнулены. Счёт начнётся заново со следующего " +
                    "показания датчика.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(onClick = {
            com.example.rebeka.blocking.BlockState.allowUninstall(minutes = 5)
            allowedUninstall = true
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Разрешить удаление на 5 минут")
        }

        if (allowedUninstall) {
            Text(
                "Защита от удаления снята на 5 минут. Она включится обратно сама — " +
                    "также после перезагрузки телефона.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
    }
}
