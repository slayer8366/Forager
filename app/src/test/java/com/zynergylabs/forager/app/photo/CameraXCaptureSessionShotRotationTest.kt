package com.zynergylabs.forager.app.photo

import android.app.Application
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What [CameraXCaptureSession.capture] assigns to `ImageCapture.targetRotation`, read back from a
 * real [ImageCapture] — CameraX's own object, not a fake — through the session's real entry point.
 *
 * ## Why this exists
 *
 * The "Lock camera to portrait" setting shipped (`ecd449e`) with the assignment established by
 * reading the class and no test on it: the gate test's own doc said a revert pointing `capture()`
 * back at the raw field would pass the suite. The device result on `b586195` (a locked shot saved
 * landscape) then had to be traced with no test to lean on. This class is that test. It needs no
 * camera: an unbound `ImageCapture` accepts `setTargetRotation` and reports the value back, and
 * `takePicture` on it fails with `ImageCaptureException` rather than throwing, which the session
 * surfaces as `Result.failure` — asserted here too, since a capture that cannot happen must not
 * read as one that did.
 *
 * ## Since dispatch 2026-10-13-03
 *
 * "Lock camera to portrait" was removed (RECORD -968), and with it the two tests that held its shot
 * to `ROTATION_0` (`locked, the shot assigns ROTATION_0 whatever the sensor said` and `locked before
 * any reading, the shot still assigns ROTATION_0`); they are recorded in
 * `docs/archive/camera-portrait-lock.md`. The unlocked tests below were the setting-off case and are
 * now the only case; since RECORD -980 and -981 they are named for it (the window held portrait, the photo following
 * the phone), and "unlocked" is gone from their names.
 *
 * ## What a coincident default would hide, and how it is kept out
 *
 * `ImageCapture`'s default target rotation is `ROTATION_0`, which is also what the lock assigns.
 * A locked-case test on a fresh `ImageCapture` would pass with the assignment deleted. So every
 * `ImageCapture` here is built at a rotation the case must *not* end on, and the precondition is
 * asserted before the shot.
 *
 * ## What this does not cover
 *
 * Everything after the setter: CameraX's request to the HAL, the HAL's own tag, and the file. The
 * trace of those hops is in the window-lock report's addendum on the trace, and the file-level
 * reapply that follows from it is [IntendedOrientationTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXCaptureSessionShotRotationTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun destination(): File = File(app.cacheDir, "shot-${UUID.randomUUID()}.jpg")

    private fun imageCaptureAt(rotation: Int): ImageCapture = ImageCapture.Builder().setTargetRotation(rotation).build()

    /**
     * **The sensor reads reverse portrait even though the window cannot occupy it.** Since
     * 2026-09-19 the camera's window asks for `SCREEN_ORIENTATION_SENSOR`, which the platform will
     * not resolve to `ROTATION_180` on a phone, so a phone held end-over-end leaves the window
     * alone — and this is the half that must keep working anyway, because it is the gill shot:
     * phone flipped over to get the lens near the ground, and the photo has to save upright.
     *
     * Nothing in this path reads the window (`CameraXCaptureSession.onDeviceOrientation` snaps the
     * `OrientationEventListener`'s degrees and `capture` assigns them), so the two are independent
     * by construction rather than by arrangement. This pins that: 180 degrees in, `ROTATION_180`
     * onto the shot.
     */
    @Test
    fun `the sensor still reports reverse portrait, which is the gill shot, whatever the window may occupy`() = runTest {
        val session = CameraXCaptureSession(app)
        val capture = imageCaptureAt(Surface.ROTATION_0)
        session.installImageCapture(capture)
        session.onDeviceOrientation(180) // held end over end
        assertEquals("precondition", Surface.ROTATION_0, capture.targetRotation)

        session.capture(destination())

        assertEquals("the shot is tagged reverse portrait, so the saved photo is upright", Surface.ROTATION_180, capture.targetRotation)
    }

    @Test
    fun `held sideways while the window stays portrait, the shot assigns the sensor's rotation, so the photo saves upright`() = runTest {
        // RECORD -980, -981: the window is held portrait and the photo still follows the phone, because nothing in this path
        // reads the window. Was `unlocked, the shot assigns the sensor's rotation`, same assertions. Not the old lock's
        // ROTATION_0, which saved a sideways photo portrait (docs/archive/camera-portrait-lock.md).
        val session = CameraXCaptureSession(app)
        val capture = imageCaptureAt(Surface.ROTATION_0)
        session.installImageCapture(capture)
        session.onDeviceOrientation(270)
        assertEquals("precondition", Surface.ROTATION_0, capture.targetRotation)

        val result = session.capture(destination())

        assertEquals(Surface.ROTATION_90, capture.targetRotation)
        // Moved here from the removed locked test (dispatch 2026-10-13-03), which was the only one asserting it.
        assertTrue("an unbound ImageCapture cannot shoot, and that is reported, not hidden", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageCaptureException)
    }

    @Test
    fun `with no reading and no viewfinder, nothing is assigned`() = runTest {
        val session = CameraXCaptureSession(app)
        val capture = imageCaptureAt(Surface.ROTATION_270)
        session.installImageCapture(capture)

        session.capture(destination())

        assertEquals("no reading and no display to fall back to: the use case keeps its own value", Surface.ROTATION_270, capture.targetRotation)
    }

    @Test
    fun `an unknown orientation from the listener is not a reading`() {
        val session = CameraXCaptureSession(app)
        session.onDeviceOrientation(OrientationEventListener.ORIENTATION_UNKNOWN)
        assertNull(session.deviceRotation)
        session.onDeviceOrientation(0)
        assertEquals(Surface.ROTATION_0, session.deviceRotation)
    }
}
