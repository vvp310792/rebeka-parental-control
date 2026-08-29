package com.example.rebeka.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rebeka.RebekaApp
import com.example.rebeka.data.StatsRepository
import com.example.rebeka.notifications.ParentAlertNotifier
import kotlinx.coroutines.launch

/**
 * Экран блокировки. Закрывается ТОЛЬКО по верному PIN родителя — раньше здесь
 * стояла заглушка, которая просто вызывала finish() по нажатию кнопки, из-за чего
 * блокировка не работала вообще.
 *
 * Кнопка "назад" заблокирована. Кнопку "домой" перехватить нельзя (это системный
 * жест, недоступный обычному приложению), но BlockService проверяет состояние
 * каждые 30 секунд и показывает экран заново — пока не выдан временный анлок.
 */
class BlockOverlayActivity : ComponentActivity() {

    private lateinit var repository: StatsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RebekaApp
        repository = StatsRepository(app.database.dayStatsDao(), app.database.appSettingsDao())

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    BlockScreen(
                        repository = repository,
                        onUnlocked = { finish() },
                        onWrongPinAttempts = { ParentAlertNotifier(this).notifyAdminDisableAttempt() }
                    )
                }
            }
        }
    }

    @Deprecated("Специально блокируем системную кнопку «назад»")
    override fun onBackPressed() {
        // no-op: выйти можно только по верному PIN
    }

    companion object {
        fun show(context: Context) {
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun BlockScreen(
    repository: StatsRepository,
    onUnlocked: () -> Unit,
    onWrongPinAttempts: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Время на сегодня закончилось", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Больше шагов — больше времени: +1 час за каждые 5000 шагов",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
                label = { Text("PIN родителя") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null,
                enabled = !checking
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    checking = true
                    scope.launch {
                        val ok = repository.verifyPin(pin)
                        if (ok) {
                            // Без временного анлока сервис через 30 секунд показал бы
                            // этот экран снова, и разблокировка выглядела бы как баг.
                            repository.grantTemporaryUnlock(minutes = 15)
                            onUnlocked()
                        } else {
                            attempts++
                            pin = ""
                            error = "Неверный PIN"
                            if (attempts >= 3) onWrongPinAttempts()
                        }
                        checking = false
                    }
                },
                enabled = pin.length >= 4 && !checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "Проверка…" else "Разблокировать")
            }
        }
    }
}
