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
 * The real session's `deviceRotation` is the orientation sensor's reading, snapped, and nothing else. Replaces
 * `CameraOrientationGateTest` (dispatch 2026-10-13-03, RECORD -968): with "Lock camera to portrait" removed there is no gate
 * left to test, only the pass-through the gate's off branch already was. The session is constructed but never opened, so
 * this needs no camera; the reading goes in through `onDeviceOrientation`, the one-line delegate the orientation listener
 * calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXCaptureSessionDeviceRotationTest {

    @Test
    fun `deviceRotation is the sensor's reading`() {
        val session = CameraXCaptureSession(ApplicationProvider.getApplicationContext<Application>())

        assertNull("before the first reading there is nothing to report", session.deviceRotation)

        session.onDeviceOrientation(270) // a quarter turn counter-clockwise
        assertEquals("a 270 degree reading snaps to ROTATION_90", Surface.ROTATION_90, session.deviceRotation)
    }
}
