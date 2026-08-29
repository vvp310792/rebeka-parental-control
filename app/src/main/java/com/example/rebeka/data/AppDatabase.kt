package com.example.rebeka.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DayStats::class, AppSettings::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayStatsDao(): DayStatsDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {

        /**
         * Ровно та схема, которую Room выводит из @Entity AppSettings.
         *
         * Важно: НЕ указывать здесь DEFAULT для колонок. Room сверяет схему из
         * аннотаций с реальной таблицей, и если в БД у колонки есть SQLite-дефолт,
         * а в @Entity нет @ColumnInfo(defaultValue = ...), проверка падает с
         * "Migration didn't properly handle: app_settings". Именно это и произошло:
         * прошлые миграции добавляли колонки через ALTER TABLE ... DEFAULT 0.
         */
        private const val CREATE_APP_SETTINGS = """
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER NOT NULL,
                baseLimitMinutes INTEGER NOT NULL,
                stepsPerBonusHour INTEGER NOT NULL,
                pinHash TEXT NOT NULL,
                pinSalt TEXT NOT NULL,
                parentNotifyEndpoint TEXT NOT NULL,
                unlockedUntilEpochMillis INTEGER NOT NULL,
                forcedBlockActive INTEGER NOT NULL,
                forcedBlockStepsBaseline INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
        """

        /**
         * Таблица настроек пересоздаётся начисто с любой прошлой версии: в разных
         * сборках она успела получить разный набор колонок и разные SQLite-дефолты,
         * и привести это к одному виду точечными ALTER-ами уже нельзя.
         *
         * Настройки сбрасываются на дефолтные, PIN нужно задать заново — приложение
         * попросит на старте. Таблица day_stats (шаги и экранное время) НЕ трогается,
         * статистика сохраняется.
         */
        private fun recreateSettings(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS app_settings")
            db.execSQL(CREATE_APP_SETTINGS)
        }

        private fun migrationTo5(from: Int) = object : Migration(from, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = recreateSettings(db)
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rebeka.db")
                .addMigrations(
                    migrationTo5(1),
                    migrationTo5(2),
                    migrationTo5(3),
                    migrationTo5(4)
                )
                .build()
    }
}
