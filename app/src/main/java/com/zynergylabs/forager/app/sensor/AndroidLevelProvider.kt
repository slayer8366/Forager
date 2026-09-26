package com.zynergylabs.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.getSystemService
import com.zynergylabs.forager.app.domain.LevelProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * [LevelProvider] on [Sensor.TYPE_ROTATION_VECTOR], the fused sensor the compass also prefers
 * ([AndroidCompassProvider]), at [SensorManager.SENSOR_DELAY_UI], the compass's rate. That rate is
 * the first thing to change if the device check finds the line lagging; it is not raised here
 * without that reading. The compass is not touched: this is a second, independent registration.
 *
 * The listener exists only while [roll] is collected, which the camera does only while the level
 * line is on screen, so it cannot outlive the camera.
 *
 * **Nothing here runs on a device in the test suite.** `CameraLevelProviderTest` drives it under
 * Robolectric with synthetic rotation vectors through the framework's own matrix code; whether a
 * real sensor's frame matches, and the sign of a real tilt, are device checks.
 */
class AndroidLevelProvider(private val context: Context) : LevelProvider {

    override val roll: Flow<Float?> = callbackFlow {
        val sensorManager = context.getSystemService<SensorManager>()
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationSensor == null) {
            Log.i(TAG, "No rotation-vector sensor; the camera's level line cannot be shown on this device.")
            trySend(null)
            close()
            return@callbackFlow
        }
        val rotationMatrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                trySend(rollDegrees(rotationMatrix[6], rotationMatrix[7]))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private companion object {
        const val TAG = "AndroidLevelProvider"
    }
}

/**
 * The phone's tilt about its screen axis from world-up expressed in device coordinates (row three
 * of the rotation matrix: its x and y components). Upright, world-up lies along the device's y
 * axis, `(0, 1)`; turned clockwise by θ it is `(-sin θ, cos θ)`, so θ is `atan2(-upX, upY)`.
 *
 * **Undefined with the phone flat**, where world-up is along the screen's normal and both
 * components go to zero: the angle then follows sensor noise. No threshold hides it, since no
 * data yet says where one belongs; the device check's flat-phone observation is that data, and it
 * feeds the top-down crosshair decision (B7d).
 */
internal fun rollDegrees(upX: Float, upY: Float): Float {
    val degrees = Math.toDegrees(kotlin.math.atan2(-upX.toDouble(), upY.toDouble())).toFloat()
    // atan2 returns -180 for (-0.0, negative): upside down with a negative zero. The interface
    // promises (-180, 180], so the half turn is always +180.
    return if (degrees <= -180f) degrees + 360f else degrees
}
