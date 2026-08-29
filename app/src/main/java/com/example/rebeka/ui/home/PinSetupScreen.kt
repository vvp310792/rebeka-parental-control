package com.example.rebeka.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rebeka.data.StatsRepository
import kotlinx.coroutines.launch

/**
 * Первый экран при установке. Без заданного PIN блокировку невозможно снять,
 * поэтому PIN задаётся до всего остального — раньше этого экрана не существовало
 * вовсе, и кнопка "PIN родителя" на экране блокировки просто закрывала окно.
 */
@Composable
fun PinSetupScreen(repository: StatsRepository, onPinSet: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Задайте PIN родителя", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Этим PIN снимается блокировка, когда экранное время закончилось. " +
                "Ребёнок не должен его знать. Если PIN забыт — сбросить его можно " +
                "только переустановкой приложения (что потребует снятия прав администратора).",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
            label = { Text("PIN (минимум 4 цифры)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )

        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { confirm = it; error = null } },
            label = { Text("Повторите PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                when {
                    pin.length < 4 -> error = "PIN должен быть не короче 4 цифр"
                    pin != confirm -> error = "PIN не совпадает"
                    else -> {
                        saving = true
                        scope.launch {
                            repository.setPin(pin)
                            saving = false
                            onPinSet()
                        }
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Сохранение…" else "Сохранить PIN")
        }
    }
}
