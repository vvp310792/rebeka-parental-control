package com.example.rebeka.data

import com.example.rebeka.admin.AdminUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Единственная точка, откуда UI и сервисы читают/пишут состояние.
 * Ни UI, ни BlockService не трогают Dao напрямую.
 */
class StatsRepository(
    private val dayStatsDao: DayStatsDao,
    private val settingsDao: AppSettingsDao
) {
    private fun today() = LocalDate.now().toEpochDay()

    fun observeToday(): Flow<DayStats> =
        dayStatsDao.observe(today()).map { it ?: DayStats(epochDay = today()) }

    suspend fun getToday(): DayStats =
        dayStatsDao.get(today()) ?: DayStats(epochDay = today())

    suspend fun updateUsedMillis(usedMillis: Long) {
        val current = getToday()
        dayStatsDao.upsert(current.copy(usedMillis = usedMillis))
    }

    suspend fun updateSteps(steps: Long, sensorBaseline: Long) {
        val current = getToday()
        dayStatsDao.upsert(
            current.copy(steps = steps, stepsBaselineAtMidnight = sensorBaseline)
        )
    }

    suspend fun setBlocked(blocked: Boolean) {
        val current = getToday()
        if (current.blocked != blocked) dayStatsDao.upsert(current.copy(blocked = blocked))
    }

    /** Сброс шагов за сегодня: и счётчик, и точка синхронизации с датчиком. */
    suspend fun resetTodaySteps() {
        val current = getToday()
        dayStatsDao.upsert(current.copy(steps = 0, stepsBaselineAtMidnight = 0))
    }

    fun observeSettings(): Flow<AppSettings> =
        settingsDao.observe().map { it ?: AppSettings() }

    suspend fun getSettings(): AppSettings = settingsDao.get() ?: AppSettings()

    suspend fun saveSettings(settings: AppSettings) = settingsDao.upsert(settings)

    suspend fun isPinSet(): Boolean = getSettings().pinHash.isNotEmpty()

    suspend fun setPin(pin: String) {
        val salt = AdminUtils.newSalt()
        saveSettings(getSettings().copy(pinHash = AdminUtils.hashPin(pin, salt), pinSalt = salt))
    }

    suspend fun verifyPin(pin: String): Boolean {
        val s = getSettings()
        if (s.pinHash.isEmpty()) return false
        return AdminUtils.verifyPin(pin, s.pinSalt, s.pinHash)
    }

    /** Родитель снял блокировку на N минут — оверлей не показывается до этого момента. */
    suspend fun grantTemporaryUnlock(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        saveSettings(getSettings().copy(unlockedUntilEpochMillis = until))
    }

    suspend fun isTemporarilyUnlocked(): Boolean =
        getSettings().unlockedUntilEpochMillis > System.currentTimeMillis()

    /** Тестовая блокировка: фиксируем текущие шаги как точку отсчёта для 5000. */
    suspend fun startForcedBlock() {
        val steps = getToday().steps
        saveSettings(
            getSettings().copy(
                forcedBlockActive = true,
                forcedBlockStepsBaseline = steps,
                // Снимаем возможный «оплаченный» ранее анлок, иначе тест не сработает.
                unlockedUntilEpochMillis = 0
            )
        )
    }

    suspend fun clearForcedBlock() {
        saveSettings(getSettings().copy(forcedBlockActive = false, forcedBlockStepsBaseline = 0))
    }
}
