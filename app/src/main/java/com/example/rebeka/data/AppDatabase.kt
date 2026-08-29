package com.example.rebeka.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DayStats::class, AppSettings::class],
    version = 3,
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

        /**
         * PIN стал шестизначным. Старый 4-значный хэш нужно обнулить: длину из хэша
         * не восстановить, а если оставить как есть, родитель не сможет разблокировать
         * (экран требует 6 цифр, а сохранён хэш от 4). Приложение попросит задать
         * новый PIN при следующем запуске. Статистика при этом не трогается.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_settings SET pinHash = '', pinSalt = ''")
            }
        }

        /**
         * Кнопка тестовой блокировки: два новых поля. Логика снятия не меняется —
         * PIN или 5000 шагов от отметки, зафиксированной при нажатии.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN forcedBlockActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN forcedBlockStepsBaseline INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rebeka.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
