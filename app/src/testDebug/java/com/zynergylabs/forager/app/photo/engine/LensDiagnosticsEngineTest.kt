package com.zynergylabs.forager.app.photo.engine

import android.app.Application
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.PixelRect
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lens chooser's Phase 2a diagnostics in camera engine A (dispatch 2026-10-17-01), over [FakePlatformCamera], through
 * the engine's own entry points (`open`, the viewfinder's surface, [PlatformCameraEngine.onLensTapped],
 * [PlatformCameraEngine.onPinch], `capture`) and the adb receiver's `onReceive`. What the phone reports is the device run's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LensDiagnosticsEngineTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun destination(): File = File(app.cacheDir, "lens-diag/${UUID.randomUUID()}.jpg")

    @Before
    fun freshProcess() {
        LensMemory.lastLensId = null
        ZoomFormSwitch.form = ZoomForm.Auto
    }

    @After
    fun forget() {
        LensMemory.lastLensId = null
        ZoomFormSwitch.form = ZoomForm.Auto
        ActivePlatformEngine.current = null
    }

    private inner class Rig(
        val test: TestScope,
        lenses: LensSet = LensFixtures.s26Set(),
        statics: List<LensStatic> = emptyList(),
        zoomForm: (() -> ZoomForm)? = null,
    ) {
        var form: ZoomForm = ZoomForm.Auto
        val camera = FakePlatformCamera(
            test.backgroundScope,
            description = CameraDescription.Described(FakePlatformCamera.lensFacts(lenses).copy(lensStatics = statics)),
        )
        val lines = mutableListOf<String>()
        val engine = PlatformCameraEngine(
            camera = camera,
            scope = test.backgroundScope,
            ioDispatcher = StandardTestDispatcher(test.testScheduler),
            log = { lines += it },
            zoomForm = zoomForm ?: { form },
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

        fun count(prefix: String) = lines.count { it.startsWith(prefix) }

        fun closeAndSettle() {
            engine.close()
            test.runCurrent()
        }
    }

    private fun diag(plan: ZoomPlan) = FrameZoomDiag(plan, OisAsk(null, null, 0), ZoomResult(lensResult = true, lensCrop = PixelRect(0, 0, 3648, 2736)))

    @Test
    fun `the configured line reports the lens session's own preview, JPG and RAW sizes`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            val main = rig.engine.lensSet!!.main
            val atOpen = rig.lines.first { it.startsWith("engine=platform event=configured") }
            assertTrue(atOpen, " preview=${main.previewSize} jpeg=${main.jpegSize} raw=${main.rawSize} " in atOpen)
            rig.engine.onLensTapped("6")
            runCurrent()
            val three = rig.engine.lensSet!!.byId("6")!!
            val onThree = rig.lines.last { it.startsWith("engine=platform event=configured") }
            assertTrue(onThree, " preview=${three.previewSize} jpeg=3648x2736 raw=3648x2736 " in onThree)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a telephoto's shot logs its crop in its own units under the logical zoom ratio`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onLensTapped("6")
            runCurrent()
            // The S26 under the logical ratio, no crop asked: its 3x lens's result reads its own zoom (RECORD -1147).
            rig.camera.stillCropAchieved = rig.engine.lensSet!!.byId("6")!!.zoom
            rig.camera.stillZoomFormUsed = ZoomForm.LogicalRatio
            assertTrue(rig.engine.capture(destination()).isSuccess)
            val shot = rig.lines.last { it.startsWith("engine=platform event=jpg_shot") }
            assertTrue(shot, shot.endsWith(" lens=6 zoom=2.89 crop_requested=1.00 crop_achieved=1.00"))
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `the viewfinder reads a telephoto's crop in its own units and makes up only what the camera did not crop`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onLensTapped("6")
            runCurrent()
            rig.engine.onPinch(1.1f)
            runCurrent()
            val three = rig.engine.lensSet!!.byId("6")!!
            assertEquals("precondition: still on the 3x lens", "6", rig.camera.repeating.last().lensCrop?.lensId)
            rig.camera.frameZoomFormUsed = ZoomForm.LogicalRatio
            rig.camera.frameCropAchieved = three.zoom // the camera framed the lens but did not crop it further
            rig.frames(1)
            assertEquals(1.1f, rig.engine.previewScale, 1e-4f)
            val line = rig.lines.last { it.startsWith("engine=platform event=viewfinder_crop") }
            assertTrue(line, line.contains(" crop_requested=1.10 crop_achieved=1.00 view_scale=1.10"))
            rig.camera.frameCropAchieved = three.zoom * 1.1 // now it did
            rig.frames(1)
            assertEquals(1f, rig.engine.previewScale, 1e-4f)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `on a telephoto the focus readings are logged once a second, with the rule's counts`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onLensTapped("6")
            runCurrent()
            rig.camera.frameFocusDiopters = 1.0f
            rig.camera.frameAfState = CloseFocusRule.AF_PASSIVE_FOCUSED
            val before = rig.count("engine=platform event=focus_reading")
            rig.frames(5)
            assertEquals("one line for the first frames", before + 1, rig.count("engine=platform event=focus_reading"))
            val line = rig.lines.last { it.startsWith("engine=platform event=focus_reading") }
            assertEquals(
                "engine=platform event=focus_reading streaming=6 wanted=6 focus_diopters=1.0 af_state=2 min_focus_diopters=2.5 " +
                    "near_limit=2.375 near_frames=0 far_frames=0 frames_to_decide=15 too_close=false",
                line,
            )
            rig.frames(31) // a little over a second at 30 frames a second
            assertEquals("one more a second later", before + 2, rig.count("engine=platform event=focus_reading"))
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `the focus line counts the frames towards too close as the rule does`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.engine.onLensTapped("6")
            runCurrent()
            rig.camera.frameFocusDiopters = 2.45f // past 0.95 of the 3x lens's 2.5 diopters
            rig.camera.frameAfState = CloseFocusRule.AF_PASSIVE_UNFOCUSED
            rig.frames(1)
            val line = rig.lines.last { it.startsWith("engine=platform event=focus_reading") }
            assertTrue(line, line.contains(" near_frames=1 far_frames=0 "))
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `on the main lens with the main lens wanted no focus readings are logged`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            rig.camera.frameFocusDiopters = 1.0f
            rig.camera.frameAfState = CloseFocusRule.AF_PASSIVE_FOCUSED
            rig.frames(40)
            assertEquals(0, rig.count("engine=platform event=focus_reading"))
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `each camera's static zoom facts are logged once at open`() = runTest {
        val statics = listOf(
            LensStatic("0", true, PixelRect(0, 0, 4000, 3000), null, 8f, 0.6..10.0, listOf(0, 1), emptyList(), listOf("crop_region", "zoom_ratio", "ois")),
            LensStatic("6", false, PixelRect(0, 0, 3648, 2736), null, 8f, 1.0..8.0, listOf(0, 1), emptyList()),
        )
        val rig = Rig(this, statics = statics)
        try {
            rig.stream()
            rig.frames(3)
            val logged = rig.lines.filter { it.startsWith("engine=platform event=lens_static") }
            assertEquals(statics.map { lensStaticLine(it) }, logged)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `crop_diag is logged once a second for the viewfinder, at once when the request changes, and for every shot`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            val first = ZoomPlan(ZoomForm.Auto, ZoomForm.LogicalRatio, "5", logicalZoomRatio = 1.0)
            rig.camera.frameZoomDiag = diag(first)
            rig.frames(5)
            assertEquals(1, rig.count("engine=platform event=crop_diag kind=preview"))
            rig.frames(31)
            assertEquals(2, rig.count("engine=platform event=crop_diag kind=preview"))
            rig.camera.frameZoomDiag = diag(first.copy(logicalZoomRatio = 2.0))
            rig.frames(1)
            assertEquals("a new request is logged at once", 3, rig.count("engine=platform event=crop_diag kind=preview"))

            rig.camera.stillZoomDiag = diag(first)
            assertTrue(rig.engine.capture(destination()).isSuccess)
            assertTrue(rig.engine.capture(destination()).isSuccess)
            assertEquals("every shot", 2, rig.count("engine=platform event=crop_diag kind=jpg"))
            val shotLine = rig.lines.last { it.startsWith("engine=platform event=crop_diag kind=jpg") }
            assertEquals(cropDiagLine("jpg", diag(first), rig.engine.lensSet!!.main, rig.camera.stills.last().lensCrop), shotLine)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `the switched zoom form reaches the viewfinder request and the shot`() = runTest {
        val rig = Rig(this)
        rig.form = ZoomForm.LensRatio
        try {
            rig.stream()
            assertEquals(ZoomForm.LensRatio, rig.camera.repeating.last().zoomForm)
            assertTrue(rig.engine.capture(destination()).isSuccess)
            assertEquals(ZoomForm.LensRatio, rig.camera.stills.last().zoomForm)
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `a zoom form changed while streaming replaces the viewfinder request at once`() = runTest {
        val rig = Rig(this)
        try {
            rig.stream()
            assertEquals(ZoomForm.Auto, rig.camera.repeating.last().zoomForm)
            rig.form = ZoomForm.LogicalRatio
            rig.engine.onZoomFormChanged()
            runCurrent()
            assertEquals(ZoomForm.LogicalRatio, rig.camera.repeating.last().zoomForm)
            assertTrue(rig.lines.toString(), rig.lines.any { it == "engine=platform event=zoom_form form=logical_ratio" })
        } finally {
            rig.closeAndSettle()
        }
    }

    @Test
    fun `adb's zoom_form sets the switch, the open engine takes it at once, and an unknown value changes nothing`() = runTest {
        val rig = Rig(this, zoomForm = { ZoomFormSwitch.form })
        try {
            rig.stream()
            ActivePlatformEngine.current = rig.engine
            val engineBefore = CameraEngineSwitch.choice
            CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ZOOM_FORM, "lens_ratio"))
            runCurrent()
            assertEquals(ZoomForm.LensRatio, ZoomFormSwitch.form)
            assertEquals(ZoomForm.LensRatio, rig.camera.repeating.last().zoomForm)
            assertEquals("the extra alone leaves the engine choice as it was", engineBefore, CameraEngineSwitch.choice)

            val requests = rig.camera.repeating.size
            CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ZOOM_FORM, "Lens_Ratio"))
            runCurrent()
            assertEquals(ZoomForm.LensRatio, ZoomFormSwitch.form)
            assertEquals("no new request", requests, rig.camera.repeating.size)

            CameraEngineReceiver().onReceive(app, Intent(CameraEngineReceiver.ACTION_ENGINE).putExtra(CameraEngineReceiver.EXTRA_ZOOM_FORM, "auto"))
            runCurrent()
            assertEquals(ZoomForm.Auto, ZoomFormSwitch.form)
            assertEquals(ZoomForm.Auto, rig.camera.repeating.last().zoomForm)
        } finally {
            rig.closeAndSettle()
        }
    }
}
