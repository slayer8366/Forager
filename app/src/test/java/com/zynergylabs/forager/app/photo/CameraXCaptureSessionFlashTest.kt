package com.zynergylabs.forager.app.photo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CameraXCaptureSession]'s flash logic, driven through [CameraXCaptureSession.installFlash]: the
 * one seam the bind in `open` goes through, handed a torch that records what it was asked instead
 * of CameraX's `cameraControl.enableTorch`.
 *
 * **What this does not cover, said plainly:** Robolectric has no camera provider, so no camera is
 * ever bound here. Whether `cameraInfo.hasFlashUnit()` is read right, whether `enableTorch` lights
 * the LED, and whether the torch is off before `unbindAll` on a real device are device checks. This
 * class holds the session's own decisions: what it asks the torch for, and what it reports.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXCaptureSessionFlashTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `closing with the torch on turns it off and leaves flashMode at Off`() {
        val session = CameraXCaptureSession(app)
        val asked = mutableListOf<Boolean>()
        session.installFlash(hasFlashUnit = true) { on -> asked += on }

        session.setFlashMode(FlashMode.Torch)
        assertEquals("Torch lights the torch", listOf(true), asked)
        assertEquals(FlashMode.Torch, session.flashMode)

        session.close()

        assertEquals("close turns the torch off", listOf(true, false), asked)
        assertEquals("torch does not persist past close", FlashMode.Off, session.flashMode)
        assertEquals("and the unit goes with the camera", false, session.hasFlashUnit)
    }

    @Test
    fun `setting Off turns the torch off`() {
        val session = CameraXCaptureSession(app)
        val asked = mutableListOf<Boolean>()
        session.installFlash(hasFlashUnit = true) { on -> asked += on }

        session.setFlashMode(FlashMode.Torch)
        session.setFlashMode(FlashMode.Off)

        assertEquals(listOf(true, false), asked)
        assertEquals(FlashMode.Off, session.flashMode)
    }

    @Test
    fun `a camera with no flash unit is never asked, and the mode stays Off`() {
        val session = CameraXCaptureSession(app)
        val asked = mutableListOf<Boolean>()
        session.installFlash(hasFlashUnit = false) { on -> asked += on }

        session.setFlashMode(FlashMode.Torch)

        assertEquals("the torch was not asked", emptyList<Boolean>(), asked)
        assertEquals(FlashMode.Off, session.flashMode)
        assertEquals(false, session.hasFlashUnit)
    }

    @Test
    fun `before a camera is bound there is no unit, and a request changes nothing`() {
        val session = CameraXCaptureSession(app)

        assertEquals(false, session.hasFlashUnit)
        session.setFlashMode(FlashMode.Torch)
        assertEquals(FlashMode.Off, session.flashMode)
    }
}
