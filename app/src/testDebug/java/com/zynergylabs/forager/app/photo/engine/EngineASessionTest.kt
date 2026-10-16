package com.zynergylabs.forager.app.photo.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The session the dialog gets with engine A chosen: the platform engine, and CameraX the moment it gives up (dispatch
 * 2026-10-16-02, "Fallback": "Any open or configure failure falls back to the CameraX engine for that session, logged
 * with the reason"; and "Every path hands the camera back or falls back to CameraX").
 *
 * CameraX is [FakeCameraCaptureSession], the screen tests' own fake; one shared order list ([order]) records the platform
 * camera's release and CameraX's open, so "released before CameraX opens" is read from the sequence, not assumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngineASessionTest {

    private inner class Rig(test: TestScope) {
        val order = mutableListOf<String>()
        val camera = FakePlatformCamera(test.backgroundScope)
        val lines = mutableListOf<String>()
        val platform = PlatformCameraEngine(
            camera = camera,
            scope = test.backgroundScope,
            ioDispatcher = StandardTestDispatcher(test.testScheduler),
            log = { lines += it },
        )
        val cameraX = FakeCameraCaptureSession(state = CameraSessionState.Opening)
        var cameraXBuilt = 0
        val composite = EngineASession(
            platform = platform,
            cameraXFactory = {
                cameraXBuilt += 1
                order += "camerax built; platform holding=${camera.holding}"
                object : CameraEngine {
                    override val session: CameraCaptureSession get() = cameraX

                    @Composable
                    override fun Viewfinder(modifier: Modifier, held: Boolean) = Unit
                }
            },
            platformViewfinder = { _, _ -> },
            log = { lines += it },
        )
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(test.testScheduler))
    }

    @Test
    fun `while the platform engine runs, the dialog sees it and CameraX is never built`() = runTest {
        val rig = Rig(this)
        rig.composite.open(rig.owner)
        runCurrent()
        rig.platform.onPreviewTargetAvailable(FakePreviewTarget("a"))
        runCurrent()

        assertEquals(CameraSessionState.Ready, rig.composite.state)
        assertEquals(EnginePhase.Streaming, rig.platform.phase)
        assertEquals(0, rig.cameraXBuilt)
        assertNull(rig.composite.fallbackReason)
        assertTrue(rig.composite.hasFlashUnit)

        rig.composite.close()
        runCurrent()
        assertFalse(rig.camera.holding)
        assertEquals(0, rig.cameraXBuilt)
    }

    @Test
    fun `when the platform engine cannot open, CameraX opens for the rest of the session, after the platform let go`() = runTest {
        val rig = Rig(this)
        rig.camera.openOutcome = OpenOutcome.Failed("onError ERROR_CAMERA_DEVICE")
        rig.composite.requestOutputFormat(CameraFormat.Raw)
        rig.composite.open(rig.owner)
        runCurrent()

        assertEquals(1, rig.cameraXBuilt)
        assertEquals(listOf("camerax built; platform holding=false"), rig.order)
        assertEquals("CameraX opened with the format the chip asked for, before its open", listOf("request Raw", "open"), rig.cameraX.events)
        assertEquals(1, rig.cameraX.openCalls)
        assertEquals("the dialog now reads CameraX's session", CameraSessionState.Ready, rig.composite.state)
        assertEquals(CameraFormat.Raw, rig.composite.outputFormat)
        assertEquals("the camera did not open: onError ERROR_CAMERA_DEVICE", rig.composite.fallbackReason)
        assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=composite event=camerax_fallback reason=the camera did not open") })

        rig.composite.close()
        runCurrent()
        assertEquals(1, rig.cameraX.closeCalls)
        assertFalse(rig.camera.holding)
    }

    @Test
    fun `after a fallback, the dialog's calls reach CameraX`() = runTest {
        val rig = Rig(this)
        rig.camera.configureOutcome = ConfigureOutcome.Failed("onConfigureFailed")
        rig.composite.open(rig.owner)
        runCurrent()
        rig.platform.onPreviewTargetAvailable(FakePreviewTarget("a"))
        runCurrent()
        assertEquals(1, rig.cameraXBuilt)

        rig.composite.requestOutputFormat(CameraFormat.Raw)
        val shot = rig.composite.capture(java.io.File(System.getProperty("java.io.tmpdir"), "engine-a-composite.jpg"))

        assertTrue(shot.isSuccess)
        assertEquals(1, rig.cameraX.captureCalls)
        assertEquals(listOf("request Jpg", "open", "request Raw"), rig.cameraX.events)
        assertEquals("one fallback only", 1, rig.cameraXBuilt)
        rig.composite.close()
        runCurrent()
    }

    @Test
    fun `closing before anything failed closes the platform engine only`() = runTest {
        val rig = Rig(this)
        rig.composite.open(rig.owner)
        runCurrent()
        rig.composite.close()
        runCurrent()

        assertFalse(rig.camera.holding)
        assertEquals(EnginePhase.Closed, rig.platform.phase)
        assertEquals(0, rig.cameraXBuilt)
        assertEquals(0, rig.cameraX.closeCalls)
    }
}
