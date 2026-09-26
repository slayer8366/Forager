package com.zynergylabs.forager.app.photo

import android.app.Application
import androidx.camera.core.ImageCapture
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

    // ── Flash on capture (decision B8 / Closed decision B, 2026-09-26) ─────────────────────────

    /**
     * A session bound the way `open()` binds one: the [ImageCapture] installed first, then the
     * flash (`CameraXCaptureSession.kt`, `installImageCapture` before `installFlash`). Under
     * Robolectric `open()` itself binds nothing — there is no camera provider — so this is the
     * bind's own two calls, in its order, with a real unbound [ImageCapture] that reads back what
     * the session set on it.
     */
    private fun boundSession(
        asked: MutableList<Boolean>,
        capture: ImageCapture = ImageCapture.Builder().build(),
        session: CameraXCaptureSession = CameraXCaptureSession(app),
        hasFlashUnit: Boolean = true,
    ): Pair<CameraXCaptureSession, ImageCapture> {
        session.installImageCapture(capture)
        session.installFlash(hasFlashUnit) { on -> asked += on }
        return session to capture
    }

    @Test
    fun `a tap cycles Off, Auto, On, Torch and back to Off`() {
        assertEquals(FlashMode.Auto, FlashMode.Off.next())
        assertEquals(FlashMode.On, FlashMode.Auto.next())
        assertEquals(FlashMode.Torch, FlashMode.On.next())
        assertEquals(FlashMode.Off, FlashMode.Torch.next())
    }

    /**
     * The table in decision B, each row read back from the installed [ImageCapture] and the torch.
     * The order is chosen so every row's ImageCapture value differs from the row before it — Auto,
     * then Torch, then On, then Off — so no row can pass by the previous one's value standing.
     */
    @Test
    fun `each mode sets the torch and the ImageCapture's flash mode as the table says`() {
        val asked = mutableListOf<Boolean>()
        val (session, capture) = boundSession(asked)
        val rows = listOf(
            Triple(FlashMode.Auto, false, ImageCapture.FLASH_MODE_AUTO),
            Triple(FlashMode.Torch, true, ImageCapture.FLASH_MODE_OFF),
            Triple(FlashMode.On, false, ImageCapture.FLASH_MODE_ON),
            Triple(FlashMode.Off, false, ImageCapture.FLASH_MODE_OFF),
        )
        rows.forEachIndexed { i, (mode, torch, imageCaptureMode) ->
            session.setFlashMode(mode)
            assertEquals("$mode is reported", mode, session.flashMode)
            assertEquals("$mode asks the torch once", i + 1, asked.size)
            assertEquals("$mode: torch ${if (torch) "on" else "off"}", torch, asked.last())
            assertEquals("$mode: the ImageCapture's flash mode", imageCaptureMode, capture.flashMode)
        }
    }

    /**
     * Ruling 3 (a). **Cannot fail on its ImageCapture half**: `FLASH_MODE_OFF` is a fresh
     * ImageCapture's own default, so this reads Off whether or not the session sets anything. It
     * is here because the planner asked for the bound state to be pinned, not as evidence the
     * mapping works; the table test above and the revert check are that evidence.
     */
    @Test
    fun `after the bind, the session is Off and the ImageCapture's flash mode is OFF`() {
        val (session, capture) = boundSession(mutableListOf())

        assertEquals(FlashMode.Off, session.flashMode)
        assertEquals(ImageCapture.FLASH_MODE_OFF, capture.flashMode)
    }

    /**
     * Ruling 3 (b): On, then close, then a new bind. The session half can fail (close must reset
     * the mode); the new ImageCapture's half cannot, for the reason on the test above.
     */
    @Test
    fun `On, then close, then a new bind - the session reports Off and the new ImageCapture is OFF`() {
        val asked = mutableListOf<Boolean>()
        val (session, first) = boundSession(asked)
        session.setFlashMode(FlashMode.On)
        assertEquals("precondition: On reached the first ImageCapture", ImageCapture.FLASH_MODE_ON, first.flashMode)

        session.close()
        assertEquals("flash lasts one session", FlashMode.Off, session.flashMode)

        val (_, second) = boundSession(asked, session = session)
        assertEquals(FlashMode.Off, session.flashMode)
        assertEquals(ImageCapture.FLASH_MODE_OFF, second.flashMode)
    }

    @Test
    fun `with no flash unit, Auto and On are refused like Torch, and nothing reaches the ImageCapture or the torch`() {
        val asked = mutableListOf<Boolean>()
        val (session, capture) = boundSession(asked, hasFlashUnit = false)

        session.setFlashMode(FlashMode.Auto)
        session.setFlashMode(FlashMode.On)

        assertEquals(FlashMode.Off, session.flashMode)
        assertEquals(ImageCapture.FLASH_MODE_OFF, capture.flashMode)
        assertEquals("the torch was not asked", emptyList<Boolean>(), asked)
    }

    /**
     * The torch-failure resync (the `enableTorch` listener delegates to [CameraXCaptureSession.onTorchRequestFailed]):
     * the reported mode matches the hardware, and a successful change to Auto or On is never
     * overwritten by it. An unlit torch agrees with Auto and On, so they stand.
     */
    @Test
    fun `a failed torch request with the torch unlit leaves Auto and On standing`() {
        val (session, capture) = boundSession(mutableListOf())

        session.setFlashMode(FlashMode.Auto)
        session.onTorchRequestFailed(lit = false)
        assertEquals("Auto stands", FlashMode.Auto, session.flashMode)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, capture.flashMode)

        session.setFlashMode(FlashMode.On)
        session.onTorchRequestFailed(lit = false)
        assertEquals("On stands", FlashMode.On, session.flashMode)
        assertEquals(ImageCapture.FLASH_MODE_ON, capture.flashMode)
    }

    @Test
    fun `a failed torch request reports what the torch actually is`() {
        val (session, capture) = boundSession(mutableListOf())

        session.setFlashMode(FlashMode.Torch)
        session.onTorchRequestFailed(lit = false)
        assertEquals("Torch asked for but not lit reads Off", FlashMode.Off, session.flashMode)

        session.setFlashMode(FlashMode.Auto)
        session.onTorchRequestFailed(lit = true)
        assertEquals("a lit torch reads Torch, whatever was asked", FlashMode.Torch, session.flashMode)
        assertEquals("and the ImageCapture follows Torch's row", ImageCapture.FLASH_MODE_OFF, capture.flashMode)
    }
}
