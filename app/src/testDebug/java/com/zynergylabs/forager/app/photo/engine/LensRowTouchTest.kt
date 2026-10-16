package com.zynergylabs.forager.app.photo.engine

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.photo.RAW_LENS_UNSUPPORTED_MESSAGE
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import com.zynergylabs.forager.app.ui.log.CAMERA_NOTICE_TAG
import com.zynergylabs.forager.app.ui.log.CAMERA_SHUTTER_TAG
import com.zynergylabs.forager.app.ui.log.InAppCameraDialog
import com.zynergylabs.forager.app.ui.log.StatusBarHider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The lens row and the pinch in the real camera screen (dispatch 2026-10-16-04, "Real touches"): `InAppCameraDialog`
 * with engine A's session over [FakePlatformCamera], the row from the session's own `lensControls`, and a stand-in
 * viewfinder carrying the same [lensPinch] gesture `PlatformViewfinder` carries (a `TextureView` never gets a surface under
 * Robolectric). Every touch here is a coordinate touch through hit-testing, never a semantic click (CLAUDE.md): a
 * semantic click would pass with another composable covering the button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LensRowTouchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val captured = mutableListOf<PhotoSource>()
    private var viewfinderDowns = 0

    private val noStatusBar = object : StatusBarHider {
        override fun hide(window: android.view.Window, view: android.view.View) = Unit
        override fun show(window: android.view.Window, view: android.view.View) = Unit
    }

    @Before
    fun freshProcess() {
        LensMemory.lastLensId = null
    }

    @After
    fun tearDown() {
        scope.cancel()
        LensMemory.lastLensId = null
    }

    private class Subject(val engine: PlatformCameraEngine, val session: EngineASession)

    private fun subject(lenses: LensSet): Subject {
        val camera = FakePlatformCamera(scope, description = CameraDescription.Described(FakePlatformCamera.lensFacts(lenses)))
        val engine = PlatformCameraEngine(camera = camera, scope = scope, ioDispatcher = Dispatchers.Unconfined, log = {})
        val session = EngineASession(
            platform = engine,
            cameraXFactory = { error("CameraX is not built while the platform engine runs") },
            platformViewfinder = { modifier, _ -> StandInViewfinder(engine, modifier) },
            log = {},
        )
        return Subject(engine, session)
    }

    @Composable
    private fun StandInViewfinder(engine: PlatformCameraEngine, modifier: Modifier) {
        LaunchedEffect(engine) { engine.onPreviewTargetAvailable(FakePreviewTarget("view")) }
        Box(
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (event.changes.any { it.pressed && !it.previousPressed }) viewfinderDowns += 1
                        }
                    }
                }
                .lensPinch(engine)
                .testTag(VIEWFINDER_TAG),
        )
    }

    private fun show(subject: Subject, format: CameraFormat = CameraFormat.Jpg) {
        composeRule.setContent {
            InAppCameraDialog(
                session = subject.session,
                cameraCaptureFiles = CameraCaptureFiles(context),
                onPhotoCaptured = { captured += it },
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = false,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                statusBarHider = noStatusBar,
                cameraFormat = format,
                viewfinder = { modifier -> subject.session.Viewfinder(modifier, held = false) },
                lensControls = subject.session.lensControls,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            subject.engine.phase == EnginePhase.Streaming && composeRule.onAllNodesWithTag(LENS_ROW_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Five points across a target's own bounds: the centre and four inset from its corners. A finger is not a point. */
    private fun pointsAcross(r: Rect): List<Offset> {
        val inset = 0.25f
        return listOf(
            r.center,
            Offset(r.left + r.width * inset, r.top + r.height * inset),
            Offset(r.right - r.width * inset, r.top + r.height * inset),
            Offset(r.left + r.width * inset, r.bottom - r.height * inset),
            Offset(r.right - r.width * inset, r.bottom - r.height * inset),
        )
    }

    private fun touch(point: Offset) {
        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()
    }

    @Test
    fun `a real touch anywhere on each lens button lights that lens`() {
        val s = subject(LensFixtures.s26Set())
        show(s)
        val ids = s.engine.lensSet!!.lenses.map { it.physicalId }
        for (id in ids) {
            val other = ids.first { it != id }
            for ((i, point) in pointsAcross(bounds(lensButtonTag(id))).withIndex()) {
                // Light another lens first, so each touch has to change something to pass.
                composeRule.runOnIdle { s.engine.onLensTapped(other) }
                composeRule.waitForIdle()
                touch(point)
                assertEquals("touch ${i + 1} at $point on lens $id", id, s.engine.litLens?.physicalId)
            }
        }
    }

    @Test
    fun `a two-finger pinch on the picture zooms in, and far enough lights the next lens`() {
        val s = subject(LensFixtures.s26Set())
        show(s)
        assertEquals(1.0, s.engine.zoom, 1e-9)
        val vf = bounds(VIEWFINDER_TAG)
        // Upright fingers 80 px apart opening to 300 px: about 3.7x, less the gesture's touch slop. Vertical, because
        // Robolectric's default screen is only 320 px wide; the fingers start on the picture, clear of the strip and the row.
        val c = Offset(vf.center.x, vf.top + vf.height * 0.4f)
        composeRule.onRoot().performTouchInput {
            pinch(
                start0 = c + Offset(0f, -40f),
                end0 = c + Offset(0f, -150f),
                start1 = c + Offset(0f, 40f),
                end1 = c + Offset(0f, 150f),
                durationMillis = 400,
            )
        }
        composeRule.waitForIdle()
        assertTrue("zoomed past the 3x lens's own zoom: ${s.engine.zoom}", s.engine.zoom > 2.95)
        assertEquals("the 3x lens lights", "6", s.engine.litLens?.physicalId)
    }

    @Test
    fun `a touch beside the row reaches the viewfinder, not a lens`() {
        val s = subject(LensFixtures.s26Set())
        show(s)
        val row = bounds(LENS_ROW_TAG)
        val beside = listOf(Offset(row.left - 30f, row.center.y), Offset(row.right + 30f, row.center.y))
        for (point in beside) {
            val before = viewfinderDowns
            touch(point)
            assertEquals("the viewfinder got the touch at $point", before + 1, viewfinderDowns)
            assertEquals("no lens changed", "5", s.engine.litLens?.physicalId)
        }
    }

    @Test
    fun `the row sits directly above the shutter and does not move when the lens's no-RAW line appears`() {
        val s = subject(LensFixtures.s26SetWithoutRawOn6())
        show(s, CameraFormat.Raw)
        touch(bounds(lensButtonTag("6")).center)
        composeRule.waitUntil(timeoutMillis = 5_000) { s.engine.phase == EnginePhase.Streaming && s.engine.litLens?.physicalId == "6" }
        val rowBefore = bounds(LENS_ROW_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        assertTrue("the row is above the shutter: $rowBefore, $shutter", rowBefore.bottom <= shutter.top)
        assertTrue("and centred over it", kotlin.math.abs(rowBefore.center.x - shutter.center.x) < 2f)

        touch(shutter.center)
        composeRule.waitUntil(timeoutMillis = 5_000) { captured.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals(RAW_LENS_UNSUPPORTED_MESSAGE, "This lens can't save RAW, saved as JPG.")
        val notice = composeRule.onNodeWithTag(CAMERA_NOTICE_TAG).fetchSemanticsNode()
        val text = notice.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals("This lens can't save RAW, saved as JPG.", text)
        assertNull("saved as a JPG, with no RAW file", (captured.single() as CameraCapturePhotoSource).rawFile)
        assertEquals("the row did not move", rowBefore, bounds(LENS_ROW_TAG))
    }

    @Test
    fun `today's camera has no lens row`() {
        composeRule.setContent {
            InAppCameraDialog(
                session = FakeCameraCaptureSession(state = CameraSessionState.Ready),
                cameraCaptureFiles = CameraCaptureFiles(context),
                onPhotoCaptured = {},
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = false,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                statusBarHider = noStatusBar,
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).fetchSemanticsNode()
        assertTrue(composeRule.onAllNodesWithTag(LENS_ROW_TAG).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val VIEWFINDER_TAG = "lens-test-viewfinder"
    }
}
