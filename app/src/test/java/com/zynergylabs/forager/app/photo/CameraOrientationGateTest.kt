package com.zynergylabs.forager.app.photo

import android.app.Application
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Lock camera to portrait" as the one gate it is. [effectiveDeviceRotation] is pure; the real
 * [CameraXCaptureSession] is also read here, constructed but never opened, because its
 * `deviceRotation` getter is plain code that runs without CameraX. That `capture()` assigns the
 * gated value is not this class's: until 2026-09-15 it was established by reading the class, and
 * this doc said a revert pointing `capture()` back at the raw field would pass the suite. It
 * would no longer — [CameraXCaptureSessionShotRotationTest] drives `capture()` against a real,
 * unbound `ImageCapture` and reads `targetRotation` back from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraOrientationGateTest {

    @Test
    fun `off passes the sensor's reading through, null included`() {
        assertNull(effectiveDeviceRotation(lockToPortrait = false, sensorRotation = null))
        assertEquals(Surface.ROTATION_90, effectiveDeviceRotation(lockToPortrait = false, sensorRotation = Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_270, effectiveDeviceRotation(lockToPortrait = false, sensorRotation = Surface.ROTATION_270))
    }

    @Test
    fun `on pins portrait and ignores the sensor entirely`() {
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = null))
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = Surface.ROTATION_180))
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = Surface.ROTATION_270))
    }

    @Test
    fun `the real session's deviceRotation reads through the gate`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        assertNull("off, before any reading: nothing to report", CameraXCaptureSession(app, lockToPortrait = false).deviceRotation)
        assertEquals("on, before any reading: portrait, already", Surface.ROTATION_0, CameraXCaptureSession(app, lockToPortrait = true).deviceRotation)
    }
}
