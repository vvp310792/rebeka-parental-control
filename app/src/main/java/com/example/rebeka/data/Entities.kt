package com.example.rebeka.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Один агрегированный день. epochDay — LocalDate.toEpochDay(), чтобы
 * не путаться с часовыми поясами внутри дня.
 *
 * usedMillis — сколько экранного времени уже потрачено сегодня (из UsageStatsManager)
 * steps — шаги за сегодня (из TYPE_STEP_COUNTER, считается разницей от значения на начало дня)
 * blocked — стоит ли сейчас блокировка (пересчитывается сервисом, не хранится как источник правды,
 *           но кэшируется, чтобы UI не мигал при холодном старте)
 */
@Entity(tableName = "day_stats")
data class DayStats(
    @PrimaryKey val epochDay: Long,
    val usedMillis: Long = 0,
    val steps: Long = 0,
    val stepsBaselineAtMidnight: Long = 0, // сырое значение сенсора на начало дня
    val blocked: Boolean = false
)

/**
 * Настройки, которые родитель может менять только под PIN.
 * Единственная строка (id = 0).
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 0,
    val baseLimitMinutes: Int = 120,       // 2 часа
    val stepsPerBonusHour: Int = 5000,     // 5000 шагов = +1 час
    val pinHash: String = "",              // SHA-256(pin + salt)
    val pinSalt: String = "",
    val parentNotifyEndpoint: String = ""  // куда слать пуш при попытке отключения, см. notifications/
)
