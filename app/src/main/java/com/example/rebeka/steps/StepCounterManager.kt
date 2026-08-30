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

/**
 * TYPE_STEP_COUNTER отдаёт число шагов, накопленное с момента последней ЗАГРУЗКИ
 * телефона, а не за сегодня.
 *
 * Первая версия вычитала фиксированную точку отсчёта: шаги = текущее - точка.
 * После перезагрузки датчик начинает с нуля, разница уходит в минус, а
 * coerceAtLeast(0) превращал это в ноль — счётчик залипал до конца суток.
 *
 * Теперь считаем накопительно по дельтам: храним последнее показание датчика и
 * прибавляем прирост. Если новое показание меньше предыдущего — это перезагрузка,
 * и прирост равен самому показанию.
 *
 * Поле stepsBaselineAtMidnight переиспользуется под «последнее показание датчика»,
 * поэтому схема БД не меняется и миграция не нужна.
 */
class StepCounterManager(
    private val appContext: Context,
    private val repository: StatsRepository
) : SensorEventListener {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            0 // maxReportLatency = 0: без батчинга, события приходят сразу
        )
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rawTotal = event.values.firstOrNull()?.toLong() ?: return

        // runBlocking в колбэке датчика блокировал бы поток сенсоров — пишем асинхронно.
        scope.launch {
            val today = repository.getToday()
            val lastRaw = today.stepsBaselineAtMidnight

            val delta = when {
                // Первое показание за сегодня: прироста ещё нет, только фиксируем точку.
                lastRaw == 0L -> 0L
                // Обычный случай.
                rawTotal >= lastRaw -> rawTotal - lastRaw
                // Показание упало — телефон перезагружался, датчик начал заново с нуля.
                else -> rawTotal
            }

            repository.updateSteps(today.steps + delta, rawTotal)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
