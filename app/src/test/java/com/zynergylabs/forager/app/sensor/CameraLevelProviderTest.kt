package com.zynergylabs.forager.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.first
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
 * The level's roll source: the fake the screen tests drive it with, [rollDegrees] on its own, and
 * [AndroidLevelProvider] against the framework's own rotation-matrix code under Robolectric, the
 * way `AndroidCompassProviderTest` drives the compass. The rotation vectors here are built from a
 * physical description (stand the phone up, then turn it about its screen), so a sign or axis
 * error in the provider shows as a wrong angle, not as agreement with the same arithmetic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraLevelProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val shadow: ShadowSensorManager = Shadows.shadowOf(sensorManager)

    // ── The fake ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the fake's roll is observable, and a tilt reaches a collector`() = runTest {
        val fake = FakeLevelProvider(initial = 0f)
        val seen = mutableListOf<Float?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fake.roll.collect { seen += it } }

        fake.tilt(12.5f)
        fake.tilt(-3f)

        assertEquals(listOf(0f, 12.5f, -3f), seen)
    }

    // ── rollDegrees ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `upright is zero, a clockwise turn is positive, a quarter turn either way is ninety`() {
        assertEquals("upright: world-up along the device's y", 0f, rollDegrees(0f, 1f), 1e-3f)
        // Turned clockwise by θ, world-up in device coordinates is (-sin θ, cos θ).
        assertEquals(30f, rollDegrees(-sin(Math.toRadians(30.0)).toFloat(), cos(Math.toRadians(30.0)).toFloat()), 1e-3f)
        assertEquals(-30f, rollDegrees(sin(Math.toRadians(30.0)).toFloat(), cos(Math.toRadians(30.0)).toFloat()), 1e-3f)
        assertEquals("a quarter turn clockwise", 90f, rollDegrees(-1f, 0f), 1e-3f)
        assertEquals("a quarter turn anticlockwise", -90f, rollDegrees(1f, 0f), 1e-3f)
        assertEquals("upside down", 180f, rollDegrees(0f, -1f), 1e-3f)
    }

    // ── AndroidLevelProvider ────────────────────────────────────────────────────────────────

    /**
     * A rotation vector for the phone stood upright (a quarter turn about its x axis from flat),
     * then turned [clockwiseDegrees] about the axis through its screen as the user sees it: the
     * quaternion product q_x(90°) ⊗ q_z(-θ), since a clockwise turn seen from the front is negative
     * about a z axis pointing at the viewer.
     */
    private fun uprightThenTurned(clockwiseDegrees: Double): SensorEvent {
        val s1 = sin(Math.toRadians(45.0)); val c1 = cos(Math.toRadians(45.0))
        val half = Math.toRadians(-clockwiseDegrees) / 2
        val s2 = sin(half); val c2 = cos(half)
        val event = ShadowSensorManager.createSensorEvent(5, Sensor.TYPE_ROTATION_VECTOR)
        event.values[0] = (s1 * c2).toFloat()
        event.values[1] = (-s1 * s2).toFloat()
        event.values[2] = (c1 * s2).toFloat()
        event.values[3] = (c1 * c2).toFloat()
        event.values[4] = -1f
        return event
    }

    @Test
    fun `the provider reads the framework's rotation matrix, upright is zero and a turn is its angle`() = runTest {
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadow.addSensor(Sensor.TYPE_ROTATION_VECTOR, sensor)
        val seen = mutableListOf<Float?>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { AndroidLevelProvider(context).roll.collect { seen += it } }
        assertTrue("registered while collected", shadow.hasListener(shadow.listeners.single(), sensor))

        shadow.sendSensorEventToListeners(uprightThenTurned(0.0))
        shadow.sendSensorEventToListeners(uprightThenTurned(20.0))
        shadow.sendSensorEventToListeners(uprightThenTurned(-20.0))

        assertEquals("upright", 0f, seen[0]!!, 0.01f)
        assertEquals("turned 20° clockwise", 20f, seen[1]!!, 0.01f)
        assertEquals("turned 20° anticlockwise", -20f, seen[2]!!, 0.01f)

        collector.cancel()
        assertTrue("unregistered once no one collects", shadow.listeners.isEmpty())
    }

    @Test
    fun `no rotation-vector sensor emits null once, not a silent flow`() = runTest {
        assertNull(AndroidLevelProvider(context).roll.first())
    }
}
