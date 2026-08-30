package com.example.rebeka.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.example.rebeka.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

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
        purgeCorruptedDataOnce()

        val today = LocalDate.now().toEpochDay()
        val storedRaw = prefs.getLong(KEY_LAST_RAW, NO_VALUE)
        val storedDay = prefs.getLong(KEY_LAST_DAY, NO_VALUE)
        val dayStats = repository.getToday()

        // Показание датчика НИКОГДА не используется как абсолютное число шагов.
        //
        // Раньше здесь была попытка «восстановить» шаги за сегодня, засчитав всё
        // накопленное датчиком, если телефон загружался после полуночи. Это
        // опиралось на предположение, что счётчик обнуляется при перезагрузке.
        // На части устройств (в частности, Xiaomi) он этого не делает и копит
        // шаги за всё время жизни телефона — и в счётчик за день попадали сотни
        // тысяч шагов за месяцы. Абсолютное значение датчика ничего не говорит
        // о сегодняшнем дне, поэтому работаем только с приростами.
        val stepsToday: Long = when {
            // Нет сохранённого показания: первый запуск, переустановка, сброс.
            // Прирост считать не от чего — просто запоминаем точку отсчёта.
            // Шаги, сделанные до этого момента, восстановить неоткуда: датчик
            // не хранит историю, а его абсолютному значению доверять нельзя.
            storedRaw == NO_VALUE -> dayStats.steps

            // Тот же день — накапливаем.
            storedDay == today -> dayStats.steps + plausibleDelta(rawTotal, storedRaw)

            // Новые сутки: точка отсчёта — последнее показание прошлого дня.
            else -> plausibleDelta(rawTotal, storedRaw)
        }

        prefs.edit()
            .putLong(KEY_LAST_RAW, rawTotal)
            .putLong(KEY_LAST_DAY, today)
            .apply()

        repository.updateSteps(stepsToday.coerceAtLeast(0L), rawTotal)
    }

    /**
     * Прирост между двумя показаниями.
     *
     * Все спорные случаи трактуются в пользу занижения: лучше не досчитать
     * несколько шагов, чем начислить ребёнку лишние часы экранного времени.
     *
     * - показание уменьшилось (сброс счётчика после перезагрузки) — прирост не
     *   засчитываем, просто пересинхронизируемся;
     * - прирост неправдоподобно большой (рассинхрон, смена прошивки, подмена
     *   датчика) — тоже не засчитываем.
     */
    private fun plausibleDelta(rawTotal: Long, storedRaw: Long): Long {
        if (rawTotal < storedRaw) return 0
        val delta = rawTotal - storedRaw
        return if (delta > MAX_PLAUSIBLE_DELTA) 0 else delta
    }

    /**
     * Одноразовая чистка. Версии до этой могли записать в счётчик за день сырое
     * показание датчика — сотни тысяч шагов за всё время жизни телефона. Такие
     * данные надо стереть, иначе они останутся в базе навсегда: новый расчёт
     * работает только с приростами и сам их не исправит.
     */
    private suspend fun purgeCorruptedDataOnce() {
        if (prefs.getBoolean(KEY_PURGED, false)) return

        repository.resetTodaySteps()
        prefs.edit()
            .remove(KEY_LAST_RAW)
            .remove(KEY_LAST_DAY)
            .putBoolean(KEY_PURGED, true)
            .apply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val PREFS_NAME = "step_counter"
        private const val KEY_LAST_RAW = "last_raw_sensor_value"
        private const val KEY_LAST_DAY = "last_raw_epoch_day"
        private const val KEY_PURGED = "corrupted_data_purged_v2"
        private const val NO_VALUE = -1L

        /** Больше этого за одно событие датчика — заведомо не шаги, а рассинхрон. */
        private const val MAX_PLAUSIBLE_DELTA = 2_000L
    }
}
