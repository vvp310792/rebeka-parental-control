package com.example.rebeka.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DayStats::class, AppSettings::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayStatsDao(): DayStatsDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        // Схема будет расти — держим все миграции по порядку здесь,
        // never fallbackToDestructiveMigration(), см. README исходной архитектуры.
        // private val MIGRATION_1_2 = object : Migration(1, 2) { ... }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rebeka.db")
                // .addMigrations(MIGRATION_1_2, ...)
                .build()
    }
}
