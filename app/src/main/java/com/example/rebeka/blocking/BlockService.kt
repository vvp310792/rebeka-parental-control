package com.example.rebeka.blocking

import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.rebeka.RebekaApp
import com.example.rebeka.steps.StepCounterManager
import com.example.rebeka.usage.UsageStatsHelper
import com.example.rebeka.util.TimeLimitCalculator
import kotlinx.coroutines.*

/**
 * Foreground-сервис — не Activity. Смахивание из recents его не убивает
 * (это весь смысл foreground + постоянное уведомление). START_STICKY на случай
 * если систhim всё же убьёт процесс при нехватке памяти — ОС попробует
 * перезапустить. BootReceiver поднимает сервис заново после перезагрузки.
 */
class BlockService : Service() {

    private lateinit var repository: com.example.rebeka.data.StatsRepository
    private lateinit var usageHelper: UsageStatsHelper
    private lateinit var stepManager: StepCounterManager
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val app = application as RebekaApp
        repository = com.example.rebeka.data.StatsRepository(
            app.database.dayStatsDao(), app.database.appSettingsDao()
        )
        usageHelper = UsageStatsHelper(this)
        stepManager = StepCounterManager(this, repository)
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
                checkAndEnforce()
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

        // Родитель мог снять блокировку по PIN на время — тогда не показываем оверлей,
        // иначе он всплывал бы снова через 30 секунд после верного PIN.
        val unlocked = repository.isTemporarilyUnlocked()

        // Пока PIN не задан, блокировать нельзя: снять её было бы нечем.
        val pinSet = repository.isPinSet()

        if (over && !unlocked && pinSet) {
            BlockOverlayActivity.show(this@BlockService)
        }
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHECK_INTERVAL_MS = 30_000L // раз в 30 секунд достаточно для экранного времени

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BlockService::class.java))
        }
    }
}
