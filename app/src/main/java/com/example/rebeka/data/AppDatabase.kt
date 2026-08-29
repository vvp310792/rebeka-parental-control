package com.example.rebeka.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DayStats::class, AppSettings::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayStatsDao(): DayStatsDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        /**
         * Добавлена временная разблокировка родителем по PIN.
         * Никогда не удалять старые миграции и не использовать деструктивный
         * fallback — иначе апдейт сотрёт статистику пользователю.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN unlockedUntilEpochMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rebeka.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
