package com.zynergylabs.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.content.getSystemService
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.CompassStatus
import com.zynergylabs.forager.app.domain.HeadingUncertainty
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
 *
 * ## The uncertainty, read from what this listener was always handed (compass-reliability dispatch)
 *
 * Before this dispatch `onAccuracyChanged` was an empty override, `SensorEvent.accuracy` was never
 * read, and the rotation vector's `values[4]` — the sensor's own estimated heading accuracy in
 * radians — was passed to `getRotationMatrixFromVector` (which uses `values[0..3]`) and dropped.
 * The registration is unchanged; the reading now carries what arrives with it:
 *
 * - **Primary, rotation-vector path:** `values[4]` as [HeadingUncertainty.Estimated], in degrees.
 * - **Fallback, both paths:** the accuracy status as [HeadingUncertainty.Status]. On the
 *   rotation-vector path that is the rotation-vector sensor's own status — the only sensor
 *   registered there, so the only status that can arrive. On the accelerometer+magnetometer path it
 *   is the **magnetometer's** (owner decision: the magnetometer is the sensor being distorted; the
 *   accelerometer's status is about gravity, not heading).
 *
 * ## What counts as "not supplied" for `values[4]` — and the zero that cannot be told apart
 *
 * The platform documents `−1` for unavailable, and older implementations deliver fewer than five
 * values; both fall back. **Exactly `0f` also falls back.** A legitimate zero ("perfect") and an
 * implementation that leaves the slot at its default are the same bits, and nothing can tell them
 * apart by value. The design makes the distinction unnecessary (owner decision): a sensor whose
 * heading accuracy is genuinely zero reports `ACCURACY_HIGH` as its status, so the fallback gives
 * the same answer the primary would have; whereas trusting a zero that meant "unpopulated" would
 * silently disable the feature on that device. NaN and anything past π are nonsense and fall back
 * too. **A device that always reports zero is, in effect, on the status-level path** — the first
 * thing the owner's device list checks.
 *
 * ## The heading is the screen's top, not the phone's (dispatch 2026-10-12-09, RECORD -960)
 *
 * The owner, on PR #221's build: "When holding the phone in landscape while navigating, it does turn the map sideways as if
 * it's still measuring portrait orientation." Every reader of this heading draws in the screen's frame (the facing-up map, the
 * puck's cone, the navigation display's heading, north arrow and needle, the compass strip), so the heading is the direction
 * the top of the *screen* faces. Each event's rotation matrix is remapped for the display's current rotation
 * ([displayRemapAxes]) before `getOrientation`, on both paths. The rotation is read per event, not once, because
 * `MainActivity` handles rotation itself (`configChanges`) and is not recreated, and it is read from [DisplayManager]'s default
 * display because this provider holds the application context.
 *
 * Until `f94f29ff` (2026-10-03) the puck ran on the map library's own compass engine, which does this remap (read from its bytecode in
 * the pinned 13.5.0 artifact: `Display.getRotation`, then `remapCoordinateSystem`). Replacing it with the app's true heading
 * dropped the remap, and nothing recorded that; this puts it back here, where every reader gets it. The remap rather than
 * adding the rotation's degrees to the azimuth (the planner's call, (a)): identical for a phone held flat, and the remap is what
 * the replaced engine and the platform both do when the phone is tilted.
 *
 * ## The callback re-emits
 *
 * `onAccuracyChanged` updates the status *and*, when the last emitted reading was itself
 * status-based, re-emits that heading with the new status — so a status change reaches the
 * consumer at once, not on the next sensor event. When the last reading carried an estimate the
 * callback only records the status, since the estimate is the better signal and will arrive again
 * with the next event.
 */
class AndroidCompassProvider(private val context: Context) : CompassProvider {

    override val heading: Flow<CompassReading?> = callbackFlow {
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
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val defaultDisplay: Display? = context.getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)
        var lastAccelerometer: FloatArray? = null
        var lastMagnetometer: FloatArray? = null
        // The status that gates the heading: the rotation-vector sensor's on that path, the
        // magnetometer's on the fallback path. Seeded HIGH — the platform reports a change before
        // the first event on most devices, and an event's own accuracy field overwrites this anyway.
        var gatingStatus = CompassStatus.HIGH
        var lastHeading: Float? = null
        var lastWasStatusBased = false

        fun emit(headingDegrees: Float, uncertainty: HeadingUncertainty, timestampMillis: Long) {
            lastHeading = headingDegrees
            lastWasStatusBased = uncertainty is HeadingUncertainty.Status
            trySend(CompassReading(headingDegrees, uncertainty, timestampMillis))
        }

        val gatingType = if (rotationSensor != null) Sensor.TYPE_ROTATION_VECTOR else Sensor.TYPE_MAGNETIC_FIELD

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val timestampMillis = event.timestamp / NANOS_PER_MILLI
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        gatingStatus = compassStatus(event.accuracy)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val uncertainty = estimatedHeadingUncertainty(event.values) ?: HeadingUncertainty.Status(gatingStatus)
                        emit(screenHeading(rotationMatrix, remappedMatrix, orientation, defaultDisplay), uncertainty, timestampMillis)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lastAccelerometer = event.values.clone()
                        combinedHeading(lastAccelerometer, lastMagnetometer, rotationMatrix, remappedMatrix, orientation, defaultDisplay)
                            ?.let { emit(it, HeadingUncertainty.Status(gatingStatus), timestampMillis) }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        gatingStatus = compassStatus(event.accuracy)
                        lastMagnetometer = event.values.clone()
                        combinedHeading(lastAccelerometer, lastMagnetometer, rotationMatrix, remappedMatrix, orientation, defaultDisplay)
                            ?.let { emit(it, HeadingUncertainty.Status(gatingStatus), timestampMillis) }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type != gatingType) return
                gatingStatus = compassStatus(accuracy)
                val heading = lastHeading
                if (heading != null && lastWasStatusBased) {
                    emit(heading, HeadingUncertainty.Status(gatingStatus), SystemClock.elapsedRealtime())
                }
            }
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
        remappedMatrix: FloatArray,
        orientation: FloatArray,
        display: Display?,
    ): Float? {
        if (accelerometer == null || magnetometer == null) return null
        if (!SensorManager.getRotationMatrix(rotationMatrix, null, accelerometer, magnetometer)) return null
        return screenHeading(rotationMatrix, remappedMatrix, orientation, display)
    }

    /**
     * The heading of the screen's top: [rotationMatrix] remapped for the display's rotation now, then its azimuth. With no
     * display (none has been seen to be missing), the natural frame stands, as before this dispatch, and that is logged.
     */
    private fun screenHeading(rotationMatrix: FloatArray, remappedMatrix: FloatArray, orientation: FloatArray, display: Display?): Float {
        val axes = if (display != null) {
            displayRemapAxes(display.rotation)
        } else {
            if (!loggedNoDisplay) {
                loggedNoDisplay = true
                Log.w(LOG_TAG, "No default display: the heading stays in the phone's natural frame")
            }
            null
        }
        val matrix = if (axes != null && SensorManager.remapCoordinateSystem(rotationMatrix, axes.first, axes.second, remappedMatrix)) {
            remappedMatrix
        } else {
            rotationMatrix
        }
        SensorManager.getOrientation(matrix, orientation)
        return headingDegrees(orientation[0])
    }

    private var loggedNoDisplay = false

    private fun headingDegrees(azimuthRadians: Float): Float =
        (Math.toDegrees(azimuthRadians.toDouble()).toFloat() + 360f) % 360f

    private companion object {
        const val LOG_TAG = "ForagerCompass"
        const val NANOS_PER_MILLI = 1_000_000L
        const val HEADING_ACCURACY_INDEX = 4
    }
}

/**
 * The `remapCoordinateSystem` axis pair that turns the phone's natural frame into the screen's for a display at [rotation]
 * (a `Surface.ROTATION_*`), or `null` at `ROTATION_0`, where the two frames are the same and no remap is done. The pairs are
 * the platform's documented ones and the ones the map library's own compass engine uses: at `ROTATION_90` the screen's top is the
 * phone's right edge (x), so the new y is x and the new x is −y; at `ROTATION_270` its left edge (−x). An unknown value is
 * treated as `ROTATION_0`. `internal` so the four cases are a pinned-literal test.
 */
internal fun displayRemapAxes(rotation: Int): Pair<Int, Int>? = when (rotation) {
    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
    else -> null
}

/**
 * `values[4]` of a rotation-vector event as an [HeadingUncertainty.Estimated], or `null` when it is
 * not supplied — see [AndroidCompassProvider]'s doc comment for the four cases, including why
 * exactly zero is one of them. `internal` so the boundary is a pinned-literal test, not a device
 * observation.
 */
internal fun estimatedHeadingUncertainty(values: FloatArray): HeadingUncertainty.Estimated? {
    if (values.size < 5) return null
    val radians = values[4]
    if (radians.isNaN() || radians <= 0f || radians > Math.PI.toFloat()) return null
    return HeadingUncertainty.Estimated(Math.toDegrees(radians.toDouble()).toFloat())
}

/** `SensorManager.SENSOR_STATUS_*` → the owned enum; anything unrecognised (including `NO_CONTACT`) reads as [CompassStatus.UNRELIABLE], never as trusted. */
internal fun compassStatus(platformStatus: Int): CompassStatus = when (platformStatus) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassStatus.HIGH
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassStatus.MEDIUM
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassStatus.LOW
    else -> CompassStatus.UNRELIABLE
}
