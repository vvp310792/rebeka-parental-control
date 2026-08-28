package com.example.rebeka.data

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

    fun observeSettings(): Flow<AppSettings> =
        settingsDao.observe().map { it ?: AppSettings() }

    suspend fun getSettings(): AppSettings = settingsDao.get() ?: AppSettings()

    suspend fun saveSettings(settings: AppSettings) = settingsDao.upsert(settings)
}
