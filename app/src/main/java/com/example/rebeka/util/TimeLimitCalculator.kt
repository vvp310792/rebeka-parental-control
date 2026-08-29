package com.example.rebeka.util

import kotlin.math.ceil

/**
 * Чистые функции без побочных эффектов — легко покрыть unit-тестом.
 *
 * Начисление ПРОПОРЦИОНАЛЬНОЕ: каждый шаг сразу добавляет свою долю времени.
 * Раньше здесь было целочисленное деление (шаги / 5000), из-за чего награда шла
 * скачками: 5000 шагов давали час, а следующие 4999 — ровно ничего. Теперь
 * "чем больше прошёл, тем больше заработал" выполняется на каждом шаге.
 *
 * Настройка stepsPerBonusHour задаёт курс обмена: сколько шагов стоит один час.
 */
object TimeLimitCalculator {

    private const val MILLIS_PER_HOUR = 3_600_000L

    /** Сколько миллисекунд экранного времени стоит один шаг. */
    fun millisPerStep(stepsPerBonusHour: Int): Double =
        if (stepsPerBonusHour <= 0) 0.0 else MILLIS_PER_HOUR.toDouble() / stepsPerBonusHour

    /** Заработано шагами, без базового лимита. */
    fun bonusMillis(steps: Long, stepsPerBonusHour: Int): Long =
        if (stepsPerBonusHour <= 0) 0 else steps * MILLIS_PER_HOUR / stepsPerBonusHour

    fun limitMillis(baseLimitMinutes: Int, steps: Long, stepsPerBonusHour: Int): Long =
        baseLimitMinutes * 60_000L + bonusMillis(steps, stepsPerBonusHour)

    fun remainingMillis(
        baseLimitMinutes: Int,
        steps: Long,
        stepsPerBonusHour: Int,
        usedMillis: Long
    ): Long = (limitMillis(baseLimitMinutes, steps, stepsPerBonusHour) - usedMillis)
        .coerceAtLeast(0)

    fun isOverLimit(
        baseLimitMinutes: Int,
        steps: Long,
        stepsPerBonusHour: Int,
        usedMillis: Long
    ): Boolean = usedMillis >= limitMillis(baseLimitMinutes, steps, stepsPerBonusHour)

    /**
     * Сколько шагов нужно прямо сейчас, чтобы блокировка снялась.
     *
     * Раньше на экране блокировки показывалось "до следующего бонусного часа" —
     * при пропорциональном начислении это уже не то число: чтобы разблокироваться,
     * достаточно покрыть шагами именно перерасход, а не набрать целый час.
     */
    fun stepsNeededToUnlock(
        baseLimitMinutes: Int,
        steps: Long,
        stepsPerBonusHour: Int,
        usedMillis: Long
    ): Long {
        val deficit = usedMillis - limitMillis(baseLimitMinutes, steps, stepsPerBonusHour)
        if (deficit <= 0) return 0
        val perStep = millisPerStep(stepsPerBonusHour)
        if (perStep <= 0.0) return 0
        return ceil(deficit / perStep).toLong().coerceAtLeast(1)
    }
}
