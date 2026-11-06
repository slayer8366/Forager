package com.zynergylabs.forager.app.photo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.toLogLine
import com.zynergylabs.forager.app.domain.toLogLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The real [CameraXCaptureSession]'s side of the capability probe (dispatch 2026-10-13-04):
 * what it exposes and what it logs once a result is installed, and that [CameraXCaptureSession.close]
 * drops it. Driven through [CameraXCaptureSession.installCapabilities] and
 * [CameraXCaptureSession.installEncoders], the functions the bind calls, because Robolectric has no
 * camera to bind; the same seam `CameraXCaptureSessionFlashTest` uses for the flash.
 *
 * What this does not reach: the bind itself, the front camera's unbound read, and the background
 * encoder wait. Those run only on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXCaptureSessionCapabilitiesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clearLog() {
        ShadowLog.clear()
    }

    private fun probeLines() = ShadowLog.getLogsForTag(CAMERA_PROBE_LOG_TAG).map { it.msg }

    @Test
    fun `an installed probe result is exposed and logged, one line per camera, under the probe tag`() {
        val session = CameraXCaptureSession(app)

        session.installCapabilities(probeId = 7, probed = CameraCapabilityFixtures.everythingSupported)

        assertEquals(CameraCapabilityFixtures.everythingSupported, session.capabilities)
        assertEquals(CameraCapabilityFixtures.everythingSupported.toLogLines(probeId = 7, bound = true), probeLines())
        assertEquals("the bound camera's line and its two physical cameras'", 3, probeLines().size)
    }

    @Test
    fun `an installed encoder result is exposed and logged with the probe id and facing`() {
        val session = CameraXCaptureSession(app)

        session.installEncoders(probeId = 7, facing = CameraFacing.Back, probed = CameraCapabilityFixtures.everyEncoder)

        assertEquals(CameraCapabilityFixtures.everyEncoder, session.encoderCapabilities)
        assertEquals(listOf(CameraCapabilityFixtures.everyEncoder.toLogLine(probeId = 7, facing = CameraFacing.Back)), probeLines())
    }

    @Test
    fun `nothing is reported before a probe, and close drops what was`() {
        val session = CameraXCaptureSession(app)
        assertNull("before any bind", session.capabilities)
        assertNull("before any bind", session.encoderCapabilities)

        session.installCapabilities(probeId = 1, probed = CameraCapabilityFixtures.everythingSupported)
        session.installEncoders(probeId = 1, facing = CameraFacing.Back, probed = CameraCapabilityFixtures.everyEncoder)
        session.close()

        assertNull("after close", session.capabilities)
        assertNull("after close", session.encoderCapabilities)
    }
}
