package com.example.rebeka.util

/**
 * Чистая функция, без побочных эффектов — легко покрыть unit-тестом.
 *
 * limit = базовый лимит + (шаги / шагов_на_бонусный_час) часов
 * Бонус начисляется по целым порциям — 4999 шагов не дают бонуса,
 * 5000 дают +1 час, 9999 всё ещё +1 час, 10000 — уже +2 часа.
 */
object TimeLimitCalculator {

    fun limitMillis(baseLimitMinutes: Int, steps: Long, stepsPerBonusHour: Int): Long {
        val bonusHours = if (stepsPerBonusHour <= 0) 0 else steps / stepsPerBonusHour
        val totalMinutes = baseLimitMinutes + bonusHours * 60
        return totalMinutes * 60_000L
    }

    fun remainingMillis(baseLimitMinutes: Int, steps: Long, stepsPerBonusHour: Int, usedMillis: Long): Long =
        (limitMillis(baseLimitMinutes, steps, stepsPerBonusHour) - usedMillis).coerceAtLeast(0)

    fun isOverLimit(baseLimitMinutes: Int, steps: Long, stepsPerBonusHour: Int, usedMillis: Long): Boolean =
        usedMillis >= limitMillis(baseLimitMinutes, steps, stepsPerBonusHour)
}
