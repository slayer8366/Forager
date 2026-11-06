package com.zynergylabs.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.zynergylabs.forager.app.domain.PRESSURE_MAX_REPORT_LATENCY_MICROS
import com.zynergylabs.forager.app.domain.PRESSURE_SAMPLING_PERIOD_MICROS
import com.zynergylabs.forager.app.domain.PressureProvider
import com.zynergylabs.forager.app.domain.PressureReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * The phone's barometer, [Sensor.TYPE_PRESSURE] (RECORD -864, -867). Asked for one sample a second, batched up to 5 s
 * ([PRESSURE_SAMPLING_PERIOD_MICROS], [PRESSURE_MAX_REPORT_LATENCY_MICROS]), whatever faster rate the sensor reports it
 * can do: the reported range is what is possible, not what is used (CLAUDE.md). Registered only while collected, and
 * the recording service collects it only while a recording runs. Emits `null` once and completes when the phone has no
 * barometer, as [AndroidCompassProvider] does for a missing compass.
 */
class AndroidPressureProvider(private val context: Context) : PressureProvider {

    override val readings: Flow<PressureReading?> = callbackFlow {
        val sensorManager = context.getSystemService<SensorManager>()
        val barometer = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (sensorManager == null || barometer == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(PressureReading(hPa = event.values[0], elapsedRealtimeNanos = event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, barometer, PRESSURE_SAMPLING_PERIOD_MICROS, PRESSURE_MAX_REPORT_LATENCY_MICROS)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()
}
