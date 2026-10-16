package com.zynergylabs.forager.app.photo.engine

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.raw.DngDefaultCrop
import com.zynergylabs.forager.app.photo.RawShot
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lens chooser in camera engine A (dispatch 2026-10-16-04), over [FakePlatformCamera], through the engine's own entry
 * points: `open`, the viewfinder's surface, [PlatformCameraEngine.onLensTapped] (the row's buttons),
 * [PlatformCameraEngine.onPinch] (the picture's pinch), `capture`, `captureRaw` and `close`. What the camera does with
 * a plan or a crop on a phone is the device check's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LensEngineTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun destination(): File = File(app.cacheDir, "lens/${UUID.randomUUID()}.jpg")

    @Before
    fun freshProcess() {
        LensMemory.lastLensId = null
    }

    @After
    fun forget() {
        LensMemory.lastLensId = null
    }

    private inner class Rig(val test: TestScope, lenses: LensSet = LensFixtures.s26Set()) {
        val camera = FakePlatformCamera(test.backgroundScope, description = CameraDescription.Described(FakePlatformCamera.lensFacts(lenses)))
        val lines = mutableListOf<String>()
        val engine = PlatformCameraEngine(
            camera = camera,
            scope = test.backgroundScope,
            ioDispatcher = StandardTestDispatcher(test.testScheduler),
            log = { lines += it },
        )
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(test.testScheduler))

        fun stream(format: CameraFormat = CameraFormat.Jpg) {
            engine.requestOutputFormat(format)
            engine.open(owner)
            test.runCurrent()
            engine.onPreviewTargetAvailable(FakePreviewTarget("view"))
            test.runCurrent()
            assertEquals("precondition: streaming", EnginePhase.Streaming, engine.phase)
        }

        fun frames(n: Int) = repeat(n) {
            camera.deliverFrame()
            test.runCurrent()
        }

        fun lastCrop(): LensCrop? = camera.repeating.last().lensCrop

        fun closeAndSettle() {
            engine.close()
            test.runCurrent()
        }
    }

    @Test
    fun `with no lens remembered the camera opens on the main wide, 1, all its streams on that lens`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            assertEquals("5", rig.engine.litLens?.physicalId)
            assertEquals(1.0, rig.engine.zoom, 1e-9)
            assertEquals("5", rig.camera.plans.single().lens?.physicalId)
            assertEquals("configure:raw=true:lens=5", rig.camera.calls.first { it.startsWith("configure") })
            assertEquals(1.0, rig.lastCrop()!!.crop, 1e-9)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a tap lights that lens at once, then the camera moves to it at its own zoom`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onLensTapped("6")
            assertEquals("lit before the switch has run", "6", rig.engine.litLens?.physicalId)
            runCurrent()
            assertEquals(listOf("5", "6"), rig.camera.plans.map { it.lens?.physicalId })
            assertEquals(rig.engine.lensSet!!.byId("6")!!.zoom, rig.engine.zoom, 1e-9)
            assertEquals(1.0, rig.lastCrop()!!.crop, 1e-9)
            assertTrue(rig.lines.toString(), rig.lines.any { it.startsWith("engine=platform event=lens_switch from=5 to=6 reason=tap") })
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a pinch inside one lens crops it, with no new session`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onPinch(2f)
            runCurrent()
            assertEquals("still one session", 1, rig.camera.plans.size)
            val crop = rig.lastCrop()!!
            assertEquals("5", crop.lensId)
            assertEquals(2.0, crop.crop, 1e-9)
            assertEquals(cropRegion(PixelSize(4080, 3060), 2.0), crop.region)
            assertEquals("the main wide stays lit", "5", rig.engine.litLens?.physicalId)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a pinch past the next lens's zoom moves to that lens, lights it, and crops the rest`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onPinch(4f)
            assertEquals("6", rig.engine.litLens?.physicalId)
            runCurrent()
            val plan = rig.camera.plans.last()
            assertEquals("6", plan.lens?.physicalId)
            assertEquals("the JPG stream is the 3x lens's own full size", PixelSize(3648, 2736), plan.lens?.jpegSize)
            val crop = rig.lastCrop()!!
            assertEquals(4.0 / plan.lens!!.zoom, crop.crop, 1e-9)
            assertEquals(cropRegion(PixelSize(3648, 2736), crop.crop), crop.region)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a JPG at an in-between zoom asks the lens's own full-size stream for the crop`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onPinch(4f)
            runCurrent()
            assertTrue(rig.engine.capture(destination()).isSuccess)
            val still = rig.camera.stills.last().lensCrop!!
            assertEquals("6", still.lensId)
            assertEquals(cropRegion(PixelSize(3648, 2736), 4.0 / rig.engine.lensSet!!.byId("6")!!.zoom), still.region)
            assertEquals(PixelSize(3648, 2736), rig.camera.configuredLens?.jpegSize)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a shot during a lens switch waits, then is taken on the new lens`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            val gate = CompletableDeferred<Unit>()
            rig.camera.configureGate = gate
            rig.engine.onLensTapped("7")
            runCurrent()
            val shot = async { rig.engine.capture(destination()) }
            runCurrent()
            assertTrue("no still while the switch runs", rig.camera.stills.isEmpty())
            assertFalse(shot.isCompleted)
            rig.camera.configureGate = null
            gate.complete(Unit)
            runCurrent()
            assertTrue(shot.await().isSuccess)
            assertEquals("taken on the new lens", "7", rig.camera.stills.single().lensCrop?.lensId)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `the lens is remembered across a close and reopen at its own zoom, and a new process opens on 1`() = runTest {
        val first = Rig(this)
        try {
            first.stream()
            first.engine.onLensTapped("7")
            runCurrent()
            first.engine.onPinch(1.5f)
            runCurrent()
            assertTrue("zoomed past the 5x lens's own", first.engine.zoom > first.engine.lensSet!!.byId("7")!!.zoom + 1)
        } finally {
            first.closeAndSettle()
        }

        val reopened = Rig(this)
        try {
            reopened.stream()
            assertEquals("the remembered lens", "7", reopened.engine.litLens?.physicalId)
            assertEquals("at that lens's own zoom, not the pinched one", reopened.engine.lensSet!!.byId("7")!!.zoom, reopened.engine.zoom, 1e-9)
            assertEquals(1.0, reopened.lastCrop()!!.crop, 1e-9)
            assertEquals("7", reopened.camera.plans.single().lens?.physicalId)
        } finally {
            reopened.closeAndSettle()
        }

        LensMemory.lastLensId = null // what a new process starts with: nothing is stored
        val restarted = Rig(this)
        try {
            restarted.stream()
            assertEquals("5", restarted.engine.litLens?.physicalId)
            assertEquals(1.0, restarted.engine.zoom, 1e-9)
        } finally {
            restarted.closeAndSettle()
        }
    }

    @Test
    fun `a RAW at an in-between zoom carries the hard crop, and one at a lens's own zoom carries none`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream(CameraFormat.Raw)
            rig.engine.onPinch(2f)
            runCurrent()
            val zoomed = rig.engine.captureRaw(destination()).getOrThrow()
            // The fake frame is 8x6 with its ActiveArea the whole frame; half of it, centred, on even rows and columns.
            assertEquals(DngDefaultCrop(originH = 2, originV = 2, sizeH = 4, sizeV = 2), zoomed.dng!!.defaultCrop)
            assertEquals("the mosaic itself is whole", 48, zoomed.dng!!.samples.size)

            rig.engine.onLensTapped("5")
            runCurrent()
            val own = rig.engine.captureRaw(destination()).getOrThrow()
            assertNull(own.dng!!.defaultCrop)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `with RAW chosen, a lens without RAW keeps its button and its shot is a JPG marked for the lens's line`() = runTest {
        val rig = Rig(this, LensFixtures.s26SetWithoutRawOn6())
        try {
            rig.stream(CameraFormat.Raw)
            rig.engine.onLensTapped("6")
            runCurrent()
            assertFalse("no RAW stream asked of that lens", rig.camera.plans.last().raw)
            assertEquals("the chip still says RAW", CameraFormat.Raw, rig.engine.outputFormat)
            val shot: RawShot = rig.engine.captureRaw(destination()).getOrThrow()
            assertTrue(shot.lensWithoutRaw)
            assertNull(shot.dng)
            assertEquals("a JPG was taken on that lens", "6", rig.camera.stills.last().lensCrop?.lensId)
            assertTrue(rig.camera.calls.last { it.startsWith("jpeg") || it.startsWith("rawjpeg") }.startsWith("jpeg"))
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `too close for the telephoto, the main lens stands in at the same zoom, lit, and the telephoto comes back`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onLensTapped("6")
            runCurrent()
            val teleZoom = rig.engine.zoom
            rig.camera.frameFocusDiopters = 2.5f
            rig.camera.frameAfState = CloseFocusRule.AF_PASSIVE_UNFOCUSED
            rig.frames(CloseFocusRule.FRAMES_TO_DECIDE)
            assertEquals("the main lens's button lights", "5", rig.engine.litLens?.physicalId)
            assertEquals("5", rig.camera.plans.last().lens?.physicalId)
            assertEquals("the zoom is kept", teleZoom, rig.engine.zoom, 1e-9)
            assertEquals("cropped to the zoom", teleZoom, rig.lastCrop()!!.crop, 1e-9)

            rig.camera.frameFocusDiopters = 1.0f
            rig.camera.frameAfState = CloseFocusRule.AF_PASSIVE_FOCUSED
            rig.frames(CloseFocusRule.FRAMES_TO_DECIDE)
            assertEquals("6", rig.engine.litLens?.physicalId)
            assertEquals("6", rig.camera.plans.last().lens?.physicalId)
            assertTrue(rig.lines.toString(), rig.lines.count { it.startsWith("engine=platform event=close_focus too_close") } == 2)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `the viewfinder makes up only the part of the zoom the camera did not crop`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onPinch(2f)
            runCurrent()
            rig.camera.frameCropAchieved = 1.0
            rig.frames(1)
            assertEquals("the camera did not crop: the view scales 2x", 2f, rig.engine.previewScale, 1e-6f)
            rig.camera.frameCropAchieved = 2.0
            rig.frames(1)
            assertEquals("the camera cropped: the view does not", 1f, rig.engine.previewScale, 1e-6f)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a camera with no lens set has no row and the session it had before`() = runTest {
        val camera = FakePlatformCamera(backgroundScope)
        val engine = PlatformCameraEngine(camera = camera, scope = backgroundScope, ioDispatcher = StandardTestDispatcher(testScheduler), log = {})
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, UnconfinedTestDispatcher(testScheduler))
        try {
            engine.open(owner)
            runCurrent()
            engine.onPreviewTargetAvailable(FakePreviewTarget("view"))
            runCurrent()
            assertNull(engine.lensSet)
            assertNull(engine.litLens)
            assertEquals("configure:raw=true", camera.calls.first { it.startsWith("configure") })
            assertNull(camera.repeating.last().lensCrop)
            engine.onLensTapped("6")
            engine.onPinch(3f)
            runCurrent()
            assertEquals("nothing reconfigures", 1, camera.plans.size)
            assertNotNull(engine.facts)
        } finally {
            engine.close()
            runCurrent()
        }
    }
}
