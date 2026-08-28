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
import java.time.LocalDate

/**
 * TYPE_STEP_COUNTER отдаёт монотонно растущее с момента последней перезагрузки
 * телефона число шагов — не "шаги за сегодня". Поэтому храним baseline (значение
 * сенсора на полночь) в DayStats.stepsBaselineAtMidnight и вычитаем.
 *
 * Требует ACTIVITY_RECOGNITION (Android 10+, runtime permission).
 */
class StepCounterManager(
    private val appContext: Context,
    private val repository: StatsRepository
) : SensorEventListener {

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    fun start() {
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return // BlockService перерегистрирует при следующем старте, когда разрешение выдано

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val rawTotal = event.values[0].toLong()

        kotlinx.coroutines.runBlocking {
            val today = repository.getToday()
            val baseline = if (today.stepsBaselineAtMidnight == 0L && today.epochDay == LocalDate.now().toEpochDay()) {
                // Первое показание после установки/перезагрузки — фиксируем как точку отсчёта
                rawTotal
            } else {
                today.stepsBaselineAtMidnight
            }
            val stepsToday = (rawTotal - baseline).coerceAtLeast(0)
            repository.updateSteps(stepsToday, baseline)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
