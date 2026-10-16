package com.zynergylabs.forager.app.photo.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Who offers a lens row (dispatch 2026-10-16-04: "With the CameraX engine, nothing changes: no row, no pinch"): engine A
 * while it runs with a lens set, and nothing once it has fallen back to CameraX, nor today's CameraX engine itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LensSessionTest {

    private fun fallbackCameraX(): CameraEngine = object : CameraEngine {
        override val session: CameraCaptureSession = FakeCameraCaptureSession(state = CameraSessionState.Ready)

        @Composable
        override fun Viewfinder(modifier: Modifier, held: Boolean) = Unit
    }

    @Test
    fun `engine A with a lens set offers the row, and once it falls back to CameraX it offers none`() = runTest {
        val camera = FakePlatformCamera(backgroundScope, description = CameraDescription.Described(FakePlatformCamera.lensFacts()))
        val platform = PlatformCameraEngine(camera = camera, scope = backgroundScope, ioDispatcher = StandardTestDispatcher(testScheduler), log = {})
        val session = EngineASession(platform, cameraXFactory = ::fallbackCameraX, platformViewfinder = { _, _ -> }, log = {})
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(testScheduler))
        try {
            session.open(owner)
            runCurrent()
            platform.onPreviewTargetAvailable(FakePreviewTarget("view"))
            runCurrent()
            assertEquals(EnginePhase.Streaming, platform.phase)
            assertNotNull("engine A shows the row", session.lensControls)
        } finally {
            session.close()
            runCurrent()
        }
    }

    @Test
    fun `a session that falls back to CameraX before streaming offers no row`() = runTest {
        val camera = FakePlatformCamera(
            backgroundScope,
            description = CameraDescription.Described(FakePlatformCamera.lensFacts()),
            configureOutcome = ConfigureOutcome.Failed("fake: no stream combination configured"),
        )
        val platform = PlatformCameraEngine(camera = camera, scope = backgroundScope, ioDispatcher = StandardTestDispatcher(testScheduler), log = {})
        val session = EngineASession(platform, cameraXFactory = ::fallbackCameraX, platformViewfinder = { _, _ -> }, log = {})
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(testScheduler))
        try {
            session.open(owner)
            runCurrent()
            platform.onPreviewTargetAvailable(FakePreviewTarget("view"))
            runCurrent()
            assertEquals(EnginePhase.FellBack, platform.phase)
            assertNotNull("CameraX took over", session.fallbackReason)
            assertNull("and the row went with engine A", session.lensControls)
        } finally {
            session.close()
            runCurrent()
        }
    }
}
