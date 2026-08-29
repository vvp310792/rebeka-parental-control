package com.example.rebeka.blocking

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.rebeka.RebekaApp
import com.example.rebeka.data.StatsRepository
import com.example.rebeka.notifications.ParentAlertNotifier
import com.example.rebeka.steps.StepCounterManager
import com.example.rebeka.usage.UsageStatsHelper
import com.example.rebeka.util.TimeLimitCalculator
import kotlinx.coroutines.*

/**
 * Foreground-сервис — не Activity. Смахивание из recents его не убивает.
 * START_STICKY на случай если система убьёт процесс, BootReceiver поднимает
 * после перезагрузки.
 *
 * Блокировка рисуется через OverlayBlocker (системное окно поверх всего),
 * а не через Activity — Activity сворачивалась кнопкой «домой».
 */
class BlockService : Service() {

    private lateinit var repository: StatsRepository
    private lateinit var usageHelper: UsageStatsHelper
    private lateinit var stepManager: StepCounterManager
    private lateinit var overlay: OverlayBlocker

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wrongPinAttempts = 0

    override fun onCreate() {
        super.onCreate()
        val app = application as RebekaApp
        repository = StatsRepository(app.database.dayStatsDao(), app.database.appSettingsDao())
        usageHelper = UsageStatsHelper(this)
        stepManager = StepCounterManager(this, repository)
        overlay = OverlayBlocker(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        stepManager.start()
        startCheckLoop()
        return START_STICKY
    }

    private fun startCheckLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                runCatching { checkAndEnforce() }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkAndEnforce() {
        val usedMillis = usageHelper.usedMillisToday()
        repository.updateUsedMillis(usedMillis)

        val settings = repository.getSettings()
        val today = repository.getToday()

        val over = TimeLimitCalculator.isOverLimit(
            settings.baseLimitMinutes, today.steps, settings.stepsPerBonusHour, usedMillis
        )
        repository.setBlocked(over)

        val unlocked = repository.isTemporarilyUnlocked()
        // Пока PIN не задан, блокировать нельзя: снять её было бы нечем.
        val pinSet = repository.isPinSet()

        val shouldBlock = over && !unlocked && pinSet

        // Два пути снятия блокировки: дошагать до следующего бонусного часа
        // (лимит вырастет, over станет false и окно закроется само) либо ввести PIN.
        val stepsToNextBonus = if (settings.stepsPerBonusHour > 0)
            settings.stepsPerBonusHour - (today.steps % settings.stepsPerBonusHour) else 0

        val statusText = buildString {
            append("Потрачено: ${formatDuration(usedMillis)}")
            append(" из ${formatDuration(TimeLimitCalculator.limitMillis(
                settings.baseLimitMinutes, today.steps, settings.stepsPerBonusHour
            ))}")
            append("\n\nШагов сегодня: ${today.steps}")
            append("\nЕщё $stepsToNextBonus шагов — и откроется +1 час")
            append("\n\nИли введите PIN родителя")
        }

        withContext(Dispatchers.Main) {
            if (shouldBlock && !overlay.isShowing) {
                overlay.show(statusText) { pin, callback -> verifyPinAsync(pin, callback) }
            } else if (shouldBlock && overlay.isShowing) {
                // Окно уже висит — обновляем счётчик шагов, чтобы ребёнок видел прогресс.
                overlay.update(statusText)
            } else if (!shouldBlock && overlay.isShowing) {
                overlay.hide()
            }
        }
    }

    private fun verifyPinAsync(pin: String, callback: (Boolean) -> Unit) {
        scope.launch {
            val ok = repository.verifyPin(pin)
            if (ok) {
                // Без временного анлока проверка через несколько секунд вернула бы
                // блокировку обратно, и верный PIN выглядел бы как не сработавший.
                repository.grantTemporaryUnlock(minutes = 15)
                wrongPinAttempts = 0
                withContext(Dispatchers.Main) {
                    overlay.hide()
                    callback(true)
                }
            } else {
                wrongPinAttempts++
                if (wrongPinAttempts >= 3) {
                    ParentAlertNotifier(this@BlockService).notifyAdminDisableAttempt()
                }
                withContext(Dispatchers.Main) { callback(false) }
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, RebekaApp.CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Rebeka следит за экранным временем")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        stepManager.stop()
        loopJob?.cancel()
        mainHandler.post { overlay.hide() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val NOTIFICATION_ID = 1

        // 5 секунд: при 30 у ребёнка было бы полминуты свободного телефона
        // после исчерпания лимита.
        private const val CHECK_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BlockService::class.java))
        }
    }
}
