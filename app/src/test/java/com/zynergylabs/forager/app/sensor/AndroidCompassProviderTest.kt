package com.zynergylabs.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.CompassStatus
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/**
 * The first tests of the real [AndroidCompassProvider] — compass-reliability dispatch. Driven
 * through Robolectric's sensor shadow: sensors added, events built and delivered to the app's own
 * listener, the accuracy callback invoked on it directly. The framework's own
 * `getRotationMatrixFromVector`/`getOrientation`/`getRotationMatrix` run for real, so the headings
 * asserted are computed, not stubbed. Deliberately **not** covered (dispatch: not this dispatch's
 * debt): the fallback path's heading arithmetic beyond one sanity value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidCompassProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val shadow: ShadowSensorManager = Shadows.shadowOf(sensorManager)

    /** Identity rotation (x, y, z = 0, w = 1): the framework resolves it to azimuth 0 → heading 0°. */
    private fun rotationVectorEvent(headingAccuracyRadians: Float?, accuracy: Int, timestampNanos: Long, size: Int = 5): SensorEvent {
        val event = ShadowSensorManager.createSensorEvent(size, Sensor.TYPE_ROTATION_VECTOR)
        if (size >= 4) event.values[3] = 1f
        if (size >= 5 && headingAccuracyRadians != null) event.values[4] = headingAccuracyRadians
        event.accuracy = accuracy
        event.timestamp = timestampNanos
        return event
    }

    private fun collectWith(block: suspend (readings: MutableList<CompassReading?>) -> Unit) = runTest {
        val readings = mutableListOf<CompassReading?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AndroidCompassProvider(context).heading.collect { readings += it }
        }
        block(readings)
    }

    @Test
    fun `values4 present - the estimate in degrees, on the reading, with the heading the framework computed`() {
        val rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, rotation)
        collectWith { readings ->
            assertTrue("the rotation-vector sensor alone is registered", shadow.hasListener(shadow.listeners.single(), rotation))

            shadow.sendSensorEventToListeners(rotationVectorEvent(0.35f, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 7_000_000L))

            val reading = readings.single()!!
            assertEquals(0f, reading.magneticHeadingDegrees, 1e-3f)
            // 0.35 rad = 20.05° — worked by hand (0.35 × 180 / π), not from the provider.
            assertEquals(20.05f, (reading.uncertainty as HeadingUncertainty.Estimated).degrees, 0.01f)
            assertEquals(7L, reading.timestampMillis)
        }
    }

    @Test
    fun `values4 exactly zero - the status decides, and the reading says so`() {
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR))
        collectWith { readings ->
            shadow.sendSensorEventToListeners(rotationVectorEvent(0f, SensorManager.SENSOR_STATUS_ACCURACY_LOW, 0L))
            assertEquals(HeadingUncertainty.Status(CompassStatus.LOW), readings.single()!!.uncertainty)

            shadow.sendSensorEventToListeners(rotationVectorEvent(0f, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 1_000_000L))
            assertEquals(HeadingUncertainty.Status(CompassStatus.HIGH), readings.last()!!.uncertainty)
        }
    }

    @Test
    fun `values4 absent, minus one, NaN or past pi - the status decides`() {
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR))
        collectWith { readings ->
            shadow.sendSensorEventToListeners(rotationVectorEvent(null, SensorManager.SENSOR_STATUS_UNRELIABLE, 0L, size = 4))
            assertEquals(HeadingUncertainty.Status(CompassStatus.UNRELIABLE), readings.last()!!.uncertainty)
            shadow.sendSensorEventToListeners(rotationVectorEvent(-1f, SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, 0L))
            assertEquals(HeadingUncertainty.Status(CompassStatus.MEDIUM), readings.last()!!.uncertainty)
            shadow.sendSensorEventToListeners(rotationVectorEvent(Float.NaN, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 0L))
            assertEquals(HeadingUncertainty.Status(CompassStatus.HIGH), readings.last()!!.uncertainty)
            shadow.sendSensorEventToListeners(rotationVectorEvent(3.2f, SensorManager.SENSOR_STATUS_ACCURACY_LOW, 0L))
            assertEquals(HeadingUncertainty.Status(CompassStatus.LOW), readings.last()!!.uncertainty)
        }
    }

    @Test
    fun `the accuracy callback alone re-emits a status-based reading - and only records when the last reading carried an estimate`() {
        val rotation = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, rotation)
        collectWith { readings ->
            shadow.sendSensorEventToListeners(rotationVectorEvent(0f, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 0L))
            assertEquals(1, readings.size)

            shadow.listeners.single().onAccuracyChanged(rotation, SensorManager.SENSOR_STATUS_UNRELIABLE)

            assertEquals("the callback produced an emission on its own", 2, readings.size)
            assertEquals(HeadingUncertainty.Status(CompassStatus.UNRELIABLE), readings.last()!!.uncertainty)
            assertEquals(0f, readings.last()!!.magneticHeadingDegrees, 1e-3f)

            shadow.sendSensorEventToListeners(rotationVectorEvent(0.1f, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 0L))
            assertEquals(3, readings.size)
            shadow.listeners.single().onAccuracyChanged(rotation, SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            assertEquals("an estimate-carrying reading is not re-emitted by a status change", 3, readings.size)
        }
    }

    @Test
    fun `fallback path - the magnetometer's status gates, the accelerometer's is ignored`() {
        val accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD)
        shadow.addSensor(Sensor.TYPE_ACCELEROMETER, accelerometer)
        shadow.addSensor(Sensor.TYPE_MAGNETIC_FIELD, magnetometer)
        collectWith { readings ->
            val accel = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_ACCELEROMETER).apply {
                values[2] = 9.81f; accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
            }
            val mag = ShadowSensorManager.createSensorEvent(3, Sensor.TYPE_MAGNETIC_FIELD).apply {
                values[1] = 20f; values[2] = -40f; accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            }
            shadow.sendSensorEventToListeners(accel)
            shadow.sendSensorEventToListeners(mag)

            assertEquals(HeadingUncertainty.Status(CompassStatus.HIGH), readings.last()!!.uncertainty)

            mag.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW
            shadow.sendSensorEventToListeners(mag)
            assertEquals(HeadingUncertainty.Status(CompassStatus.LOW), readings.last()!!.uncertainty)
        }
    }

    @Test
    fun `no sensor at all - one null, then the flow completes`() {
        collectWith { readings ->
            assertEquals(listOf<CompassReading?>(null), readings)
        }
    }

    @Test
    fun `estimatedHeadingUncertainty - the four not-supplied cases and the 15 degree literal`() {
        assertNull(estimatedHeadingUncertainty(floatArrayOf(0f, 0f, 0f, 1f)))
        assertNull(estimatedHeadingUncertainty(floatArrayOf(0f, 0f, 0f, 1f, -1f)))
        assertNull(estimatedHeadingUncertainty(floatArrayOf(0f, 0f, 0f, 1f, 0f)))
        assertNull(estimatedHeadingUncertainty(floatArrayOf(0f, 0f, 0f, 1f, Float.NaN)))
        assertNull(estimatedHeadingUncertainty(floatArrayOf(0f, 0f, 0f, 1f, 3.2f)))
        // 0.2618 rad is 15.000° (0.2618 × 180 / π = 15.0002), written out, not derived.
        assertEquals(15.0f, estimatedHeadingUncertainty(floatArrayOf(0f, 0f, 0f, 1f, 0.2618f))!!.degrees, 0.001f)
    }

    @Test
    fun `platform status maps to the owned enum, and anything unrecognised reads as UNRELIABLE`() {
        assertEquals(CompassStatus.HIGH, compassStatus(SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
        assertEquals(CompassStatus.MEDIUM, compassStatus(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
        assertEquals(CompassStatus.LOW, compassStatus(SensorManager.SENSOR_STATUS_ACCURACY_LOW))
        assertEquals(CompassStatus.UNRELIABLE, compassStatus(SensorManager.SENSOR_STATUS_UNRELIABLE))
        assertEquals(CompassStatus.UNRELIABLE, compassStatus(SensorManager.SENSOR_STATUS_NO_CONTACT))
    }
}
