package com.zynergylabs.forager.app.photo.engine

import android.app.Application
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FlashMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Camera engine A's state machine (dispatch 2026-10-16-02, "Tests"): open, stream, capture, burst, disconnect and recover,
 * close, and fallback on failure, over [FakePlatformCamera], through the engine's own entry points: `open` with a lifecycle
 * owner, the viewfinder's surface callbacks, `capture`, `captureRaw`, `setFlashMode`, `burst` and `close`, the calls the
 * camera dialog, the viewfinder and the adb trigger make.
 *
 * "Dead", the dispatch's abort condition, is [FakePlatformCamera.holding] still true after a path has ended with no
 * camera in use, or a fallback reported while the device is still held (CameraX would then open a camera the prototype
 * still has). Every path below ends by reading it.
 *
 * Nothing here runs a camera: whether a real phone opens, streams and saves as the fake does is the device check's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlatformCameraEngineTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun destination(): File = File(app.cacheDir, "engine-a/${UUID.randomUUID()}.jpg")

    private fun tagOf(file: File): Int = ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)

    private inner class Rig(val test: TestScope, facts: CameraFacts = FakePlatformCamera.facts()) {
        val camera = FakePlatformCamera(test.backgroundScope, description = CameraDescription.Described(facts))
        val lines = mutableListOf<String>()
        val fallbacks = mutableListOf<String>()
        var forced = false
        val engine = PlatformCameraEngine(
            camera = camera,
            scope = test.backgroundScope,
            ioDispatcher = StandardTestDispatcher(test.testScheduler),
            log = { lines += it },
            forceFallback = { forced },
        ).also { engine -> engine.onFallback = { reason -> fallbacks += "$reason; holding=${camera.holding}" } }
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(test.testScheduler))
        val target = FakePreviewTarget("first")

        /** Opens and hands over the viewfinder's surface: the dialog's open, then the viewfinder composing once Ready. */
        fun stream() {
            engine.open(owner)
            test.runCurrent()
            engine.onPreviewTargetAvailable(target)
            test.runCurrent()
            assertEquals("precondition: streaming", EnginePhase.Streaming, engine.phase)
        }

        fun closeAndSettle() {
            engine.close()
            test.runCurrent()
        }
    }

    // ── Open and stream ───────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `open reads Opening, then Ready once the device opens, and streams only once the viewfinder gives it a surface`() = runTest {
        val rig = Rig(this)
        rig.camera.openTakesNanos = 120_000_000L
        rig.camera.firstFrameAfterNanos = 180_000_000L
        try {
            rig.engine.open(rig.owner)
            assertEquals(CameraSessionState.Opening, rig.engine.state)
            runCurrent()

            assertEquals("Ready once the device is open, so the dialog composes the viewfinder", CameraSessionState.Ready, rig.engine.state)
            assertTrue(rig.camera.holding)
            assertEquals(EnginePhase.AwaitingViewfinder, rig.engine.phase)
            assertEquals("no surface yet, so nothing is configured", listOf("describe", "open:0"), rig.camera.calls)

            rig.engine.onPreviewTargetAvailable(rig.target)
            runCurrent()

            assertEquals(EnginePhase.Streaming, rig.engine.phase)
            assertEquals(
                listOf("describe", "open:0", "configure:raw=true", "repeat:${PreviewControls(FlashMode.Off)}"),
                rig.camera.calls,
            )
            assertTrue(rig.lines.toString(), "engine=platform event=open open_ms=120.0" in rig.lines)
            assertTrue(rig.lines.toString(), "engine=platform event=first_frame first_frame_ms=300.0" in rig.lines)
            assertTrue("the flash chip's unit is the camera's", rig.engine.hasFlashUnit)
            assertNotNull("the chip's capabilities are the camera's", rig.engine.capabilities)
        } finally {
            rig.closeAndSettle()
        }
        assertFalse(rig.camera.holding)
    }

    @Test
    fun `the pinned main wide and where the RAW stream comes from are logged when the session configures`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=configured") && "raw_source=physical:5" in it })
        } finally {
            rig.closeAndSettle()
        }
    }

    // ── JPG capture and the orientation tag ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a JPG shot in each of the four holds asks for CameraX's rotation and the saved file carries its tag`() = runTest {
        // Listener degrees, then what today's camera puts on the file for a back camera mounted at 90 degrees:
        // CameraX snaps the degrees to a surface rotation and asks for (sensor - target) mod 360.
        val holds = listOf(
            0 to (90 to ExifInterface.ORIENTATION_ROTATE_90), // portrait
            90 to (180 to ExifInterface.ORIENTATION_ROTATE_180), // turned clockwise
            180 to (270 to ExifInterface.ORIENTATION_ROTATE_270), // upside down, the gill shot
            270 to (0 to ExifInterface.ORIENTATION_NORMAL), // turned anticlockwise
        )
        val rig = Rig(this)
        try {
            rig.stream()
            for ((listenerDegrees, expected) in holds) {
                val (requestDegrees, tag) = expected
                rig.engine.onDeviceOrientation(listenerDegrees)
                val file = destination()

                val result = rig.engine.capture(file)

                assertTrue("held at $listenerDegrees: $result", result.isSuccess)
                assertEquals("held at $listenerDegrees: the request's JPEG_ORIENTATION", requestDegrees, rig.camera.stills.last().jpegOrientationDegrees)
                assertEquals("held at $listenerDegrees: the file's tag", tag, tagOf(file))
            }
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `with no orientation reading the shot follows the window's rotation, as today's display fallback does, and says so`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.displayRotation = Surface.ROTATION_90
            val file = destination()

            rig.engine.capture(file)

            assertEquals(0, rig.camera.stills.last().jpegOrientationDegrees)
            assertEquals(ExifInterface.ORIENTATION_NORMAL, tagOf(file))
            assertTrue(rig.lines.toString(), rig.lines.any { "no orientation reading" in it })
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a shot logs shutter to saved, and a failed one is a failure with nothing reported as saved`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.camera.failNextCapture = "the camera said no"

            val failed = rig.engine.capture(destination())

            assertTrue(failed.isFailure)
            assertEquals("the camera said no", failed.exceptionOrNull()?.message)
            assertFalse(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=shot kind=jpg") })
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=shot_failed kind=jpg") })

            assertTrue(rig.engine.capture(destination()).isSuccess)
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=shot kind=jpg shutter_to_saved_ms=") })
        } finally {
            rig.closeAndSettle()
        }
    }

    // ── RAW ───────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a RAW shot is one request for both files, noise reduction off, and its DNG input takes the JPG's final tag`() = runTest {
        val rig = Rig(this)
        try {
            rig.engine.requestOutputFormat(CameraFormat.Raw)
            rig.stream()
            assertEquals(CameraFormat.Raw, rig.engine.outputFormat)
            rig.engine.onDeviceOrientation(0)
            val file = destination()

            val shot = rig.engine.captureRaw(file).getOrThrow()

            assertEquals("one request, both streams", 1, rig.camera.calls.count { it.startsWith("rawjpeg") })
            assertTrue(rig.camera.calls.toString(), "rawjpeg:90:nrOff=true" in rig.camera.calls)
            assertEquals(ExifInterface.ORIENTATION_ROTATE_90, tagOf(file))
            val dng = assertNotNullAnd(shot.dng, shot.dngUnavailable)
            assertEquals("the DNG is written the way the JPG displays", ExifInterface.ORIENTATION_ROTATE_90, dng.orientation)
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=raw_shot") && "active_physical=5" in it && "noise_reduction=0" in it })
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=shot kind=raw shutter_to_saved_ms=") })
        } finally {
            rig.closeAndSettle()
        }
    }

    private fun <T : Any> assertNotNullAnd(value: T?, why: String?): T {
        assertNotNull("expected a value; the reason given was: $why", value)
        return value!!
    }

    @Test
    fun `switching the format while open needs no new session, so the viewfinder never blinks`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            assertEquals(CameraFormat.Jpg, rig.engine.outputFormat)

            rig.engine.requestOutputFormat(CameraFormat.Raw)
            runCurrent()

            assertEquals(CameraFormat.Raw, rig.engine.outputFormat)
            assertEquals("the RAW stream was configured from the start", 1, rig.camera.calls.count { it.startsWith("configure") })
            assertFalse(rig.camera.calls.contains("closeSession"))
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `on a camera with no RAW the format stays JPG and a RAW shot is refused`() = runTest {
        val rig = Rig(this, FakePlatformCamera.facts(raw = false))
        try {
            rig.engine.requestOutputFormat(CameraFormat.Raw)
            rig.stream()

            assertEquals(CameraFormat.Jpg, rig.engine.outputFormat)
            assertTrue(rig.camera.calls.toString(), "configure:raw=false" in rig.camera.calls)
            assertTrue(rig.engine.captureRaw(destination()).isFailure)
        } finally {
            rig.closeAndSettle()
        }
    }

    // ── Flash ─────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `with no flash unit a flash mode is ignored and logged, never reported`() = runTest {
        val rig = Rig(this, FakePlatformCamera.facts(hasFlashUnit = false))
        try {
            rig.stream()
            rig.engine.setFlashMode(FlashMode.On)

            assertFalse(rig.engine.hasFlashUnit)
            assertEquals(FlashMode.Off, rig.engine.flashMode)
            assertTrue(rig.lines.toString(), rig.lines.any { "ignored" in it && "no flash unit" in it })
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `torch lights on the viewfinder request, and closing the camera puts the flash back to off`() = runTest {
        val rig = Rig(this)
        rig.stream()
        rig.engine.setFlashMode(FlashMode.Torch)

        assertEquals(FlashMode.Torch, rig.engine.flashMode)
        assertEquals(PreviewControls(FlashMode.Torch), rig.camera.repeating.last())

        rig.closeAndSettle()
        assertEquals(FlashMode.Off, rig.engine.flashMode)
        assertFalse(rig.camera.holding)
    }

    @Test
    fun `flash on runs the precapture sequence before the still and ends it after`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.setFlashMode(FlashMode.On)
            rig.camera.calls.clear()

            rig.engine.capture(destination())

            assertEquals(listOf("precapture:On", "jpeg:90", "endPrecapture"), rig.camera.calls.filterNot { it.startsWith("repeat") })
            assertEquals(FlashMode.On, rig.camera.stills.last().flash)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `flash auto runs the precapture sequence only when the live preview says the flash is needed`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.setFlashMode(FlashMode.Auto)
            rig.camera.deliverFrame(aeState = PlatformStates.AE_CONVERGED)
            rig.camera.calls.clear()

            rig.engine.capture(destination())
            assertEquals("bright scene: no precapture", listOf("jpeg:90"), rig.camera.calls.filterNot { it.startsWith("repeat") })

            rig.camera.deliverFrame(aeState = PlatformStates.AE_FLASH_REQUIRED)
            rig.camera.calls.clear()
            rig.engine.capture(destination())
            assertEquals("dim scene: precapture", listOf("precapture:Auto", "jpeg:90", "endPrecapture"), rig.camera.calls.filterNot { it.startsWith("repeat") })
        } finally {
            rig.closeAndSettle()
        }
    }

    // ── The burst ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the burst runs in the running session, locks exposure and white balance from the preview, and unlocks after`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.camera.calls.clear()

            val report = rig.engine.burst()

            assertTrue("$report", report is BurstReport.Done)
            report as BurstReport.Done
            assertEquals(8, report.frames)
            assertEquals(setOf<String?>("5"), report.activePhysicalIds)
            assertEquals(
                "lock, burst, unlock; no open, no configure, no release: no hand-over",
                listOf(
                    "lock:ae=true,awb=true",
                    "burst:8:locked=true:nrOff=true",
                    "repeat:${PreviewControls(FlashMode.Off)}",
                ),
                rig.camera.calls,
            )
            assertTrue(report.line, "frames=8/8" in report.line)
            assertTrue(report.line, "fps=30.0" in report.line)
            assertTrue(report.line, "first_to_last_ms=233.3" in report.line)
            assertTrue(report.line, "shutter_to_last_ms=273.3" in report.line)
            assertEquals(EnginePhase.Streaming, rig.engine.phase)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a burst on a camera with no RAW stream, or before the viewfinder streams, is unsupported and touches nothing`() = runTest {
        val noRaw = Rig(this, FakePlatformCamera.facts(raw = false))
        try {
            noRaw.stream()
            noRaw.camera.calls.clear()
            assertTrue(noRaw.engine.burst() is BurstReport.Unsupported)
            assertEquals(emptyList<String>(), noRaw.camera.calls)
        } finally {
            noRaw.closeAndSettle()
        }
        val notStreaming = Rig(this)
        assertTrue(notStreaming.engine.burst() is BurstReport.Unsupported)
        assertEquals(emptyList<String>(), notStreaming.camera.calls)
    }

    // ── Lifecycle, disconnect and recovery ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `backgrounding releases the camera and keeps the viewfinder, and coming back streams again`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()

            rig.owner.currentState = Lifecycle.State.CREATED // ON_STOP: background, or the screen off
            runCurrent()
            assertFalse("another app may use the camera while Forager is away", rig.camera.holding)
            assertEquals(EnginePhase.Paused, rig.engine.phase)
            assertEquals("the dialog keeps its viewfinder, as with CameraX", CameraSessionState.Ready, rig.engine.state)

            rig.owner.currentState = Lifecycle.State.RESUMED
            runCurrent()
            assertEquals(EnginePhase.Streaming, rig.engine.phase)
            assertTrue(rig.camera.holding)
            assertEquals(2, rig.camera.calls.count { it == "open:0" })
            assertTrue("the same viewfinder surface is reused", !rig.target.released)
        } finally {
            rig.closeAndSettle()
        }
        assertFalse(rig.camera.holding)
    }

    @Test
    fun `another app taking the camera reads unavailable, and the camera comes back when it is free`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()

            rig.camera.disconnect()
            runCurrent()
            assertEquals(CameraSessionState.Unavailable(PlatformCameraEngine.IN_USE_MESSAGE), rig.engine.state)
            assertEquals(EnginePhase.Disconnected, rig.engine.phase)
            assertTrue(rig.camera.calls.toString(), "watchAvailability:0" in rig.camera.calls)
            assertTrue("a disconnect is recovered, not a fallback", rig.fallbacks.isEmpty())
            // The dialog drops the viewfinder while unavailable.
            rig.engine.onPreviewTargetGone(rig.target)
            runCurrent()
            assertTrue("the old surface is released once nothing draws into it", rig.target.released)

            rig.camera.becomeAvailable()
            runCurrent()
            assertEquals(CameraSessionState.Ready, rig.engine.state)
            assertTrue(rig.camera.holding)
            rig.engine.onPreviewTargetAvailable(FakePreviewTarget("second"))
            runCurrent()
            assertEquals(EnginePhase.Streaming, rig.engine.phase)
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=recovered") })
        } finally {
            rig.closeAndSettle()
        }
        assertFalse(rig.camera.holding)
    }

    // ── Fallback ──────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an open that fails falls back with its reason, after the camera is released`() = runTest {
        val rig = Rig(this)
        rig.camera.openOutcome = OpenOutcome.Failed("onError ERROR_CAMERA_DEVICE")
        rig.engine.open(rig.owner)
        runCurrent()

        assertEquals(listOf("the camera did not open: onError ERROR_CAMERA_DEVICE; holding=false"), rig.fallbacks)
        assertEquals(EnginePhase.FellBack, rig.engine.phase)
        assertTrue(rig.camera.calls.contains("release"))
        assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=fallback reason=") })
        rig.closeAndSettle()
    }

    @Test
    fun `a session that will not configure falls back`() = runTest {
        val rig = Rig(this)
        rig.camera.configureOutcome = ConfigureOutcome.Failed("onConfigureFailed")
        rig.engine.open(rig.owner)
        runCurrent()
        rig.engine.onPreviewTargetAvailable(rig.target)
        runCurrent()

        assertEquals(listOf("the session did not configure: onConfigureFailed; holding=false"), rig.fallbacks)
        assertTrue("the surface is not left to a session that will never draw into it", rig.target.released)
        rig.closeAndSettle()
    }

    @Test
    fun `a session that never delivers a frame falls back when the first-frame wait runs out`() = runTest {
        val rig = Rig(this)
        rig.camera.streams = false
        rig.engine.open(rig.owner)
        runCurrent()
        rig.engine.onPreviewTargetAvailable(rig.target)
        runCurrent()
        assertTrue("not yet", rig.fallbacks.isEmpty())

        advanceTimeBy(EngineTimeouts().firstFrameMillis + 1)
        runCurrent()

        assertEquals(listOf("no viewfinder frame within 3000 ms; holding=false"), rig.fallbacks)
        rig.closeAndSettle()
    }

    @Test
    fun `before the first stream, a camera in use is a fallback too, and a phone with no camera is one`() = runTest {
        val inUse = Rig(this)
        inUse.camera.openOutcome = OpenOutcome.InUse("ERROR_CAMERA_IN_USE")
        inUse.engine.open(inUse.owner)
        runCurrent()
        assertEquals(listOf("the camera did not open: ERROR_CAMERA_IN_USE; holding=false"), inUse.fallbacks)
        inUse.closeAndSettle()

        val none = Rig(this)
        none.camera.description = CameraDescription.NoCamera("no back or front camera is listed")
        none.engine.open(none.owner)
        runCurrent()
        assertEquals(listOf("no camera to open: no back or front camera is listed; holding=false"), none.fallbacks)
        assertFalse(none.camera.calls.any { it.startsWith("open") })
        none.closeAndSettle()
    }

    @Test
    fun `the debug flag forces the fallback before the camera is touched`() = runTest {
        val rig = Rig(this)
        rig.forced = true
        rig.engine.open(rig.owner)
        runCurrent()

        assertEquals(listOf("forced by the debug flag; holding=false"), rig.fallbacks)
        assertFalse(rig.camera.calls.any { it.startsWith("open") })
        rig.closeAndSettle()
    }

    // ── Close and hand-back ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `close releases the camera once, a second close is a no-op, and the session reads Opening again`() = runTest {
        val rig = Rig(this)
        rig.stream()

        rig.closeAndSettle()
        rig.closeAndSettle()

        assertFalse(rig.camera.holding)
        assertEquals(1, rig.camera.calls.count { it == "release" })
        assertEquals(EnginePhase.Closed, rig.engine.phase)
        assertEquals(CameraSessionState.Opening, rig.engine.state)
        assertNull(rig.engine.capabilities)
        assertFalse(rig.engine.hasFlashUnit)
        assertTrue(rig.target.released)
        assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=summary") })
    }

    @Test
    fun `close while the open is still pending releases the camera, and nothing streams afterwards`() = runTest {
        val rig = Rig(this)
        rig.camera.openGate = CompletableDeferred()
        rig.engine.open(rig.owner)
        runCurrent()

        rig.closeAndSettle()
        rig.camera.openGate?.complete(Unit)
        runCurrent()

        assertFalse(rig.camera.holding)
        assertTrue(rig.camera.calls.contains("release"))
        assertEquals(EnginePhase.Closed, rig.engine.phase)
        assertTrue(rig.fallbacks.isEmpty())
    }

    @Test
    fun `every path ends with the camera handed back`() = runTest {
        fun path(name: String, block: suspend (Rig) -> Unit): Pair<String, suspend (Rig) -> Unit> = name to block
        val paths = listOf(
            path("closed while streaming") { r -> r.stream(); r.closeAndSettle() },
            path("closed before the viewfinder") { r -> r.engine.open(r.owner); runCurrent(); r.closeAndSettle() },
            path("backgrounded") { r -> r.stream(); r.owner.currentState = Lifecycle.State.CREATED; runCurrent() },
            path("disconnected") { r -> r.stream(); r.camera.disconnect(); runCurrent() },
            path("fell back on open") { r -> r.camera.openOutcome = OpenOutcome.Failed("x"); r.engine.open(r.owner); runCurrent() },
            path("fell back on configure") { r ->
                r.camera.configureOutcome = ConfigureOutcome.Failed("x")
                r.engine.open(r.owner)
                runCurrent()
                r.engine.onPreviewTargetAvailable(r.target)
                runCurrent()
            },
            path("burst then closed") { r -> r.stream(); r.engine.burst(); r.closeAndSettle() },
        )
        for ((name, path) in paths) {
            val rig = Rig(this)
            path(rig)
            assertFalse("$name: the camera is still held", rig.camera.holding)
            rig.closeAndSettle()
        }
    }
}
