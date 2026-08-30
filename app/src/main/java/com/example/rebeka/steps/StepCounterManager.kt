package com.example.rebeka.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.rebeka.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Считает шаги С НАЧАЛА СУТОК, а не с момента запуска приложения.
 *
 * Датчик TYPE_STEP_COUNTER не хранит историю: он отдаёт только суммарное число
 * шагов с момента последней ЗАГРУЗКИ телефона. Поэтому «сколько пройдено сегодня»
 * приходится вычислять самим, и для этого нужно помнить показание датчика между
 * сутками и между запусками приложения.
 *
 * Ровно этого и не хватало раньше: показание хранилось только внутри записи за
 * текущий день, и при смене суток, обновлении приложения или сбросе точка отсчёта
 * терялась — счёт начинался с текущего момента, а пройденное с полуночи пропадало.
 *
 * Теперь показание датчика живёт в SharedPreferences отдельно от дневной
 * статистики и переживает и полночь, и перезапуск процесса.
 */
class StepCounterManager(
    private val appContext: Context,
    private val repository: StatsRepository
) : SensorEventListener {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var registered = false

    val isRegistered: Boolean get() = registered

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Идемпотентно. Сервис вызывает это на каждой проверке: разрешение на шаги
     * часто выдают уже ПОСЛЕ первого запуска сервиса, и без повторной попытки
     * датчик так и остался бы неподписанным до перезапуска приложения.
     */
    fun start() {
        if (registered || !hasPermission()) return
        val sensor = stepSensor ?: return

        // При подписке система сразу присылает текущее показание счётчика —
        // отдельный запрос «сколько сейчас» не нужен.
        registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            0
        )
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rawTotal = event.values.firstOrNull()?.toLong() ?: return

        // Mutex сериализует записи: события приходят пачками, и без него две
        // корутины прочитали бы одно значение и перетёрли друг друга.
        scope.launch {
            writeMutex.withLock { handleReading(rawTotal) }
        }
    }

    private suspend fun handleReading(rawTotal: Long) {
        val today = LocalDate.now().toEpochDay()
        val storedRaw = prefs.getLong(KEY_LAST_RAW, NO_VALUE)
        val storedDay = prefs.getLong(KEY_LAST_DAY, NO_VALUE)
        val dayStats = repository.getToday()

        val stepsToday: Long = when {
            // Нет сохранённого показания: первый запуск, переустановка, очистка данных.
            storedRaw == NO_VALUE -> recoverStepsSinceMidnight(rawTotal)

            // Тот же день — продолжаем накапливать.
            storedDay == today -> {
                val delta = if (rawTotal >= storedRaw) rawTotal - storedRaw else rawTotal
                dayStats.steps + delta
            }

            // Наступили новые сутки. Точкой отсчёта берём последнее показание
            // прошлого дня: всё, что датчик накрутил после него, пройдено уже
            // сегодня. Именно так шаги между полуночью и первым событием дня
            // перестают теряться.
            else -> if (rawTotal >= storedRaw) rawTotal - storedRaw else rawTotal
        }

        val sane = stepsToday.coerceIn(0L, plausibleMaxForNow())

        prefs.edit()
            .putLong(KEY_LAST_RAW, rawTotal)
            .putLong(KEY_LAST_DAY, today)
            .apply()

        repository.updateSteps(sane, rawTotal)
    }

    /**
     * Восстановление после переустановки или очистки данных.
     *
     * Датчик считает с момента загрузки телефона. Если телефон загружался уже
     * после полуночи, то все накопленные им шаги сделаны сегодня — их можно
     * засчитать целиком. Если загрузка была вчера или раньше, узнать долю за
     * сегодня неоткуда, и счёт начинается с нуля.
     */
    private fun recoverStepsSinceMidnight(rawTotal: Long): Long {
        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val startOfDayMillis = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        return if (bootTimeMillis >= startOfDayMillis) rawTotal else 0L
    }

    /**
     * Потолок здравого смысла, растущий в течение дня: быстрый бег даёт около
     * 200 шагов в минуту, устойчиво больше человек не выдаёт. Защищает от мусора
     * в показаниях, но, в отличие от прошлой версии, не обнуляет накопленное.
     */
    private fun plausibleMaxForNow(): Long {
        val minutesSinceMidnight = LocalTime.now().toSecondOfDay() / 60L
        return (minutesSinceMidnight * MAX_STEPS_PER_MINUTE).coerceIn(1_000L, MAX_STEPS_PER_DAY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val PREFS_NAME = "step_counter"
        private const val KEY_LAST_RAW = "last_raw_sensor_value"
        private const val KEY_LAST_DAY = "last_raw_epoch_day"
        private const val NO_VALUE = -1L

        private const val MAX_STEPS_PER_MINUTE = 200L
        private const val MAX_STEPS_PER_DAY = 200_000L
    }
}
