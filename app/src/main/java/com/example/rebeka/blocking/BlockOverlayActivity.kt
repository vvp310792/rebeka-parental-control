package com.example.rebeka.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * launchMode="singleInstance" + excludeFromRecents в манифесте — не должна
 * плодиться копиями и не должна появляться в списке недавних приложений сама.
 *
 * Важно: это НЕ замена настоящей блокировки экрана (DevicePolicyManager.lockNow()
 * тоже доступен через Device Admin, если нужен более жёсткий вариант) — это
 * непроходимый экран поверх всего внутри самого приложения. Родитель снимает
 * его вводом PIN (см. PinEntryScreen в ui/).
 */
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BlockScreen(onParentUnlock = { finish() }) }
    }

    // Блокируем аппаратную кнопку "назад" — иначе ребёнок просто закрывает экран
    override fun onBackPressed() { /* no-op специально */ }

    companion object {
        fun show(context: Context) {
            val intent = Intent(context, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun BlockScreen(onParentUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Время на сегодня закончилось", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Больше шагов — больше времени: +1 час за каждые 5000 шагов")
            Spacer(Modifier.height(24.dp))
            // Реальная реализация: PinEntryScreen с проверкой через AdminUtils.verifyPin,
            // здесь — заглушка кнопки для структуры проекта.
            Button(onClick = onParentUnlock) { Text("PIN родителя") }
        }
    }
}
