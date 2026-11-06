package com.zynergylabs.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dispatch 2026-10-12-09 (RECORD -960; the owner: "When holding the phone in landscape while navigating, it does turn the map
 * sideways as if it's still measuring portrait orientation"). The heading is the direction the top of the *screen* faces, so in
 * landscape it is the phone's natural-frame azimuth turned by the display's rotation.
 *
 * One fixed physical pose throughout: the phone flat, its natural (portrait) top edge at azimuth 30°. Only the display rotation
 * changes, set on Robolectric's display shadow, which is what `DisplayManager.getDisplay(DEFAULT_DISPLAY)` returns. The
 * framework's own `getRotationMatrix*`, `remapCoordinateSystem` and `getOrientation` run for real; the expected values are
 * worked by hand: at ROTATION_90 the screen's top is the phone's right edge, 30 + 90 = 120; at ROTATION_270 its left edge,
 * 30 − 90 = 300.
 *
 * The ROTATION_0 cases are a control (portrait unchanged), not evidence: they pass identically before and after the fix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidCompassProviderDisplayRotationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val shadow: ShadowSensorManager = Shadows.shadowOf(sensorManager)

    @After
    fun restoreRotation() {
        setDisplayRotation(Surface.ROTATION_0)
    }

    private fun setDisplayRotation(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
    }

    /** The phone flat, its natural top at azimuth 30°: a rotation of −30° about the z axis, as a unit quaternion. */
    private fun rotationVectorAt30Degrees(): SensorEvent {
        val halfAngle = Math.toRadians(-30.0) / 2
        return ShadowSensorManager.createSensorEvent(5, Sensor.TYPE_ROTATION_VECTOR).apply {
            values[2] = sin(halfAngle).toFloat()
            values[3] = cos(halfAngle).toFloat()
            values[4] = -1f
            accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        }
    }

    private fun headingFromRotationVector(rotation: Int): Float {
        setDisplayRotation(rotation)
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR))
        return collectOne { shadow.sendSensorEventToListeners(rotationVectorAt30Degrees()) }
    }

    /** The same pose on the fallback path: gravity straight down the z axis, and north 30° counter-clockwise of the phone's top. */
    private fun headingFromFallback(rotation: Int): Float {
        setDisplayRotation(rotation)
        shadow.addSensor(Sensor.TYPE_ACCELEROMETER, ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        shadow.addSensor(Sensor.TYPE_MAGNETIC_FIELD, ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD))
        val thirty = Math.toRadians(30.0)
        return collectOne {
            shadow.sendSensorEventToListeners(
                ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER).apply {
                    values[2] = 9.81f; accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                },
            )
            shadow.sendSensorEventToListeners(
                ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_MAGNETIC_FIELD).apply {
                    values[0] = (-20 * sin(thirty)).toFloat()
                    values[1] = (20 * cos(thirty)).toFloat()
                    values[2] = -40f
                    accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                },
            )
        }
    }

    private fun collectOne(send: () -> Unit): Float {
        var heading = Float.NaN
        runTest {
            val readings = mutableListOf<CompassReading?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                AndroidCompassProvider(context).heading.collect { readings += it }
            }
            send()
            heading = readings.last()!!.magneticHeadingDegrees
        }
        return heading
    }

    @Test
    fun `rotation vector - landscape at ROTATION_90 reads the heading of the screen's top`() {
        assertEquals(120f, headingFromRotationVector(Surface.ROTATION_90), 0.01f)
    }

    @Test
    fun `rotation vector - reverse landscape at ROTATION_270 reads the heading of the screen's top`() {
        assertEquals(300f, headingFromRotationVector(Surface.ROTATION_270), 0.01f)
    }

    @Test
    fun `rotation vector - portrait at ROTATION_0 is unchanged (control)`() {
        assertEquals(30f, headingFromRotationVector(Surface.ROTATION_0), 0.01f)
    }

    @Test
    fun `fallback - landscape at ROTATION_90 reads the heading of the screen's top`() {
        assertEquals(120f, headingFromFallback(Surface.ROTATION_90), 0.01f)
    }

    @Test
    fun `fallback - reverse landscape at ROTATION_270 reads the heading of the screen's top`() {
        assertEquals(300f, headingFromFallback(Surface.ROTATION_270), 0.01f)
    }

    @Test
    fun `fallback - portrait at ROTATION_0 is unchanged (control)`() {
        assertEquals(30f, headingFromFallback(Surface.ROTATION_0), 0.01f)
    }

    @Test
    fun `displayRemapAxes - the axis pair for each of the four rotations, written out`() {
        assertNull("portrait: no remap", displayRemapAxes(Surface.ROTATION_0))
        assertEquals(SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X, displayRemapAxes(Surface.ROTATION_90))
        assertEquals(SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y, displayRemapAxes(Surface.ROTATION_180))
        assertEquals(SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X, displayRemapAxes(Surface.ROTATION_270))
    }
}
