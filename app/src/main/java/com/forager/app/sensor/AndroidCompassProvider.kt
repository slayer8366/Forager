package com.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.forager.app.domain.CompassProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Prefers [Sensor.TYPE_ROTATION_VECTOR] (fused, more stable) and falls back to
 * [Sensor.TYPE_ACCELEROMETER] + [Sensor.TYPE_MAGNETIC_FIELD] when a device has no rotation-vector
 * sensor — both checked, per the plan doc's own note, since not every device carries the fused
 * sensor. Emits `null` once and stops if neither path is available, rather than a [Flow] that
 * silently never emits: a collector cannot tell "no sensor" apart from "hasn't updated yet" without
 * an explicit value for the first case.
 */
class AndroidCompassProvider(private val context: Context) : CompassProvider {

    override val heading: Flow<Float?> = callbackFlow {
        val sensorManager = context.getSystemService<SensorManager>()
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (sensorManager == null || (rotationSensor == null && (accelerometer == null || magnetometer == null))) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var lastAccelerometer: FloatArray? = null
        var lastMagnetometer: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        trySend(headingDegrees(orientation[0]))
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lastAccelerometer = event.values.clone()
                        combinedHeading(lastAccelerometer, lastMagnetometer, rotationMatrix, orientation)?.let(::trySend)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lastMagnetometer = event.values.clone()
                        combinedHeading(lastAccelerometer, lastMagnetometer, rotationMatrix, orientation)?.let(::trySend)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private fun combinedHeading(
        accelerometer: FloatArray?,
        magnetometer: FloatArray?,
        rotationMatrix: FloatArray,
        orientation: FloatArray,
    ): Float? {
        if (accelerometer == null || magnetometer == null) return null
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, accelerometer, magnetometer)) return null
        SensorManager.getOrientation(rotationMatrix, orientation)
        return headingDegrees(orientation[0])
    }

    private fun headingDegrees(azimuthRadians: Float): Float =
        (Math.toDegrees(azimuthRadians.toDouble()).toFloat() + 360f) % 360f
}
