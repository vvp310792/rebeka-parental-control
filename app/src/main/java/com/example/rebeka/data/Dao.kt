package com.example.rebeka.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DayStatsDao {
    @Query("SELECT * FROM day_stats WHERE epochDay = :epochDay")
    suspend fun get(epochDay: Long): DayStats?

    @Query("SELECT * FROM day_stats WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DayStats?>

    @Upsert
    suspend fun upsert(stats: DayStats)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun observe(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun get(): AppSettings?

    @Upsert
    suspend fun upsert(settings: AppSettings)
}
