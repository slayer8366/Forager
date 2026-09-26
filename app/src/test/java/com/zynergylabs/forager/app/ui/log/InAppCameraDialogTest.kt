package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.LevelProvider
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.robolectric.shadows.ShadowDisplay
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import android.view.Surface
import android.content.res.Configuration
import androidx.compose.ui.platform.testTag
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.photo.FlashMode

/**
 * [InAppCameraDialog] driven through its real controls, against a fake [CameraCaptureSession].
 *
 * **What this class can and cannot establish, stated before the tests rather than after.** The
 * multi-shot behaviour the owner asked for is *here*: one photo per shutter tap, the count, each
 * photo handed over as it lands, dismissal, and a failed capture surfacing rather than silently not
 * appearing. None of it involves CameraX, which cannot run under Robolectric at all — there is no
 * camera provider, no HAL and no surface. So a green run here says the screen is right and says
 * **nothing** about whether a real camera opens, whether the viewfinder draws, or whether a
 * captured JPEG is right-way-up. Those are device checks, listed as such in the completion report.
 *
 * The fake is [FakeCameraCaptureSession], shared and observable; its doc carries what it scripts.
 *
 * ## The deadlock this suite missed, and why (2026-09-15)
 *
 * On a device the camera never opened: the Opening spinner, forever. The dialog composes the
 * viewfinder slot only in the `Ready` branch, and on the head that shipped, the code that fetches
 * the provider, binds, and sets `Ready` lived inside the real `Viewfinder`'s `DisposableEffect` —
 * the composable the slot calls. The dialog waited for `Ready` before composing the only thing
 * that could produce it. This suite passed throughout because the fake defaulted to `Ready`, the
 * slot was a plain `Box`, and the fake's state was a plain `var` with no notion of "produced by
 * opening": nothing here could model the circularity. The two tests under "Opening" are the ones
 * that would have caught it, and did once written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppCameraDialogTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * `ui-test-manifest` is deliberately not a dependency of this project (see the reasoning in
     * `app/build.gradle.kts`), so nothing registers `ComponentActivity` and the compose rule cannot
     * launch its host. Registered here, exactly as [PhotoViewerDialogTest] does.
     */
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    /**
     * The `FileProvider` cache reset every capture-creating test needs is [FileProviderCacheReset],
     * shared since the store's own tests started creating captures too; its doc carries the
     * mechanism. What stays here is the part that is about this class:
     *
     * ## Correction, same day, and the reason it is written here rather than quietly dropped
     *
     * The first full-suite run after this class was added also failed
     * `AvailabilityScreenSettingsPanelTest`'s GPX share test, with the same
     * `IllegalArgumentException at FileProvider.java:911`. This class was recorded — in a commit
     * message, a report and an index row — as having caused it by leaving a poisoned cache behind,
     * and clearing the cache on exit as the fix. **Neither was established, and both look wrong.**
     * Class order is stable across runs, with `AvailabilityScreenSettingsPanelTest` at position
     * 122 and this class at 149, so this class runs *after* it and cannot poison it; removing the
     * exit clear left the full suite green (1442 / 0); restoring this file to its exact state in
     * the failing commit left it green too. That one failure is **unexplained and reported, not
     * fixed**. What *is* reproducible is the within-class problem: disable the reset and 7 of these
     * 13 fail, each naming its own stale temp root.
     */

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    /** Records what the screen handed up, which is the actual output — not "the count changed". */
    private val captured = mutableListOf<PhotoSource>()
    private var dismissals = 0

    /** Every window the camera asked to have its status bar hidden, and every one it asked to restore — the seam CameraWindowChrome.kt describes. */
    private val hiddenOn = mutableListOf<Pair<Window, View>>()
    private val shownOn = mutableListOf<Pair<Window, View>>()
    private val recordingStatusBarHider = object : StatusBarHider {
        override fun hide(window: Window, view: View) { hiddenOn += window to view }
        override fun show(window: Window, view: View) { shownOn += window to view }
    }

    @Composable
    private fun Subject(
        session: CameraCaptureSession,
        viewfinder: @Composable (Modifier) -> Unit = { modifier -> Box(modifier.fillMaxSize()) },
        gridMode: GridMode = GridMode.Off,
        onGridModeChanged: (GridMode) -> Unit = {},
        levelProvider: LevelProvider = FakeLevelProvider(),
    ) {
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = CameraCaptureFiles(context),
            lockToPortrait = false,
            onPhotoCaptured = { captured += it },
            onDismiss = { dismissals += 1 },
            gridMode = gridMode,
            onGridModeChanged = onGridModeChanged,
            levelProvider = levelProvider,
            statusBarHider = recordingStatusBarHider,
            viewfinder = viewfinder,
        )
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getBoundsInRoot()
    private fun DpRect.centreX() = ((left + right) / 2).value
    private fun DpRect.centreY() = ((top + bottom) / 2).value

    // ── Opening ──────────────────────────────────────────────────────────────────────────────

    /**
     * The device-check failure, as a test. The session starts `Opening` and becomes `Ready` only
     * when it is opened; the assertion is that the viewfinder slot is eventually composed. On the
     * head that shipped this fails with a timeout waiting for the viewfinder: the dialog never
     * opens the session, because opening lived inside the viewfinder it was withholding.
     */
    @Test
    fun `the viewfinder is composed once the session opens`() {
        val session = FakeCameraCaptureSession(state = CameraSessionState.Opening)
        composeRule.setContent {
            Subject(session, viewfinder = { modifier -> Box(modifier.fillMaxSize().testTag(VIEWFINDER_TAG)) })
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(VIEWFINDER_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(CAMERA_OPENING_TAG).assertDoesNotExist()
        assertEquals("opened by the dialog, exactly once", 1, session.openCalls)
    }

    /** The lifecycle the fix hangs on: open once on enter, close once on leave, never the other way. */
    @Test
    fun `the session is opened once on enter and closed once when the dialog leaves`() {
        val session = FakeCameraCaptureSession()
        val shown = mutableStateOf(true)
        composeRule.setContent { if (shown.value) Subject(session) }
        composeRule.waitForIdle()
        assertEquals(1, session.openCalls)
        assertEquals(0, session.closeCalls)

        shown.value = false
        composeRule.waitForIdle()

        assertEquals("closed exactly once", 1, session.closeCalls)
        assertEquals("and not reopened", 1, session.openCalls)
    }

    // ── The request itself ───────────────────────────────────────────────────────────────────

    /** The headline: the camera does not close after one photo. This is what the owner asked for. */
    @Test
    fun `the camera stays open across several shots and hands up one photo per tap`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }

        repeat(4) {
            composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
            composeRule.waitForIdle()
        }

        assertEquals("four taps, four photos", 4, captured.size)
        assertEquals("and four actual captures, not a count incremented on its own", 4, session.captureCalls)
        assertEquals("the camera never dismissed itself", 0, dismissals)
        composeRule.onNodeWithTag(IN_APP_CAMERA_TAG).assertIsDisplayed()
    }

    /**
     * Each photo is handed over as it lands, not batched at dismissal — *"people can preview after
     * they're done"*. Asserted **before** any dismissal, which is the only way to tell the two
     * designs apart: a batch-at-the-end implementation passes the test above and fails this one.
     */
    @Test
    fun `a photo is handed over as it lands, before the camera is dismissed`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals("handed over already, with the camera still open", 1, captured.size)
        assertEquals(0, dismissals)
        assertTrue("and it is a camera capture, carrying the URI the store will read", captured.single() is CameraCapturePhotoSource)
    }

    /** Every photo gets its own destination. A reused one would silently overwrite the last shot. */
    @Test
    fun `each shot goes to its own file`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }

        repeat(3) {
            composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
            composeRule.waitForIdle()
        }

        val uris = captured.map { (it as CameraCapturePhotoSource).uri.toString() }
        assertEquals("three distinct destinations", 3, uris.toSet().size)
    }

    @Test
    fun `the running count is the only feedback and it reads as a sentence`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithText("No photos yet").assertIsDisplayed()

        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 photo taken").assertIsDisplayed()

        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 photos taken").assertIsDisplayed()
    }

    @Test
    fun `Back closes the camera and the photos already taken are not withdrawn, and there is no Done control`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Done").assertCountEquals(0)
        composeRule.pressBackOnCamera()
        composeRule.waitForIdle()

        assertEquals("Back reaches the same close Done used to", 1, dismissals)
        assertEquals("the photo stands; dismissing is not a cancel", 1, captured.size)
    }

    // ── Failure is reported, never swallowed ─────────────────────────────────────────────────

    /**
     * CLAUDE.md: a partial or failed result is never presented as success. A camera that silently
     * takes no photo while the user watches the count not move is the worst version of this,
     * because they will keep tapping.
     */
    @Test
    fun `a failed capture is shown, the count does not move, and nothing is handed up`() {
        val session = FakeCameraCaptureSession().apply { failEveryCapture() }
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CAMERA_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(CAPTURE_FAILED_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithText("No photos yet").assertIsDisplayed()
        assertTrue("a failed capture must not reach the store", captured.isEmpty())
    }

    /** The empty destination is cleaned up, so the persist path never meets a zero-byte file. */
    @Test
    fun `a failed capture leaves no file behind`() {
        val session = FakeCameraCaptureSession().apply { failEveryCapture() }
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        val captures = File(context.filesDir, "captures").listFiles().orEmpty()
        assertTrue("left behind: ${captures.map { it.name }}", captures.isEmpty())
    }

    /**
     * A recovered camera clears the message rather than leaving a stale failure on screen. One
     * `setContent` and one session throughout: re-composing a fresh screen would clear the message
     * whatever the production code did, which is the version of this test that cannot fail.
     */
    @Test
    fun `the failure message clears on the next successful shot`() {
        val session = FakeCameraCaptureSession().apply { failEveryCapture() }
        composeRule.setContent { Subject(session) }
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CAMERA_ERROR_TAG).assertIsDisplayed()

        session.recover()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CAMERA_ERROR_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("1 photo taken").assertIsDisplayed()
        assertEquals(1, captured.size)
    }

    // ── The states that are not "ready" ──────────────────────────────────────────────────────

    @Test
    fun `an unavailable camera says why, and the shutter cannot be used`() {
        val session = FakeCameraCaptureSession(state = CameraSessionState.Unavailable("This device has no camera available."))
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_UNAVAILABLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("This device has no camera available.").assertIsDisplayed()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a camera still opening shows progress rather than a dead viewfinder`() {
        val session = FakeCameraCaptureSession(state = CameraSessionState.Opening, readyOnOpen = false)
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_OPENING_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).assertIsNotEnabled()
    }

    /** Dismissal has to work from a camera that never opened, or the user is stuck on a black screen. */
    @Test
    fun `an unavailable camera can still be dismissed`() {
        val session = FakeCameraCaptureSession(state = CameraSessionState.Unavailable("The camera is in use by another app."))
        composeRule.setContent { Subject(session) }

        composeRule.pressBackOnCamera()
        composeRule.waitForIdle()

        assertEquals(1, dismissals)
    }

    // ── The label, on its own ────────────────────────────────────────────────────────────────

    @Test
    fun `the count label is singular at one and plural either side`() {
        assertEquals("No photos yet", photoCountLabel(0))
        assertEquals("1 photo taken", photoCountLabel(1))
        assertEquals("2 photos taken", photoCountLabel(2))
        assertEquals("11 photos taken", photoCountLabel(11))
    }

    /**
     * The instrument, checked against itself — and the check that was wrong first time.
     *
     * `a failed capture leaves no file behind` asserts an empty directory. An empty directory is
     * also exactly what a fake that writes nothing on failure produces, so with the original fake
     * that test passed whether or not the screen cleaned anything up. A revert check found it:
     * deleting the `deleteCapture` call produced **zero** failures. CLAUDE.md names this shape
     * directly — a check that passes identically before and after a change is not covering what it
     * claims to — and the revert is what surfaced it, not review.
     *
     * The fake now writes a partial file and *then* fails, which is the real case: a camera that
     * errors mid-write leaves a stub the persist path would later try to read. This test is what
     * keeps that property of the fake from being quietly lost again.
     */
    @Test
    fun `the fake leaves a partial file on failure, which is what makes the cleanup test able to fail`() {
        val written = File(context.cacheDir, "probe/written.jpg")
        val partial = File(context.cacheDir, "probe/partial.jpg")

        val success = runBlocking { FakeCameraCaptureSession().capture(written) }
        val failure = runBlocking { FakeCameraCaptureSession().apply { failEveryCapture() }.capture(partial) }

        assertTrue(success.isSuccess)
        assertTrue("a successful capture leaves a file", written.exists())
        assertTrue(failure.isFailure)
        assertTrue("and a failed one leaves a stub for the screen to clean up", partial.exists())
        assertFalse("which is not a whole photo", partial.readBytes().size > 1)
    }

    // ── The strip, the bands and the window chrome (camera overlay spec, 2026-09-18) ────────

    /**
     * Portrait, in device anatomy: the port is at the window's bottom, so the punch-hole edge is
     * the top and the strip is flush with it, full width, one control row deep; the shutter band is
     * at the bottom with the shutter in it. Robolectric's zero insets make "flush" the edge itself;
     * the cut-out inset that moves the strip inboard on a device is invisible here (CameraBands.kt).
     */
    @Test
    fun `in portrait the strip is flush with the top edge and the shutter band with the bottom`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession()) }
        composeRule.waitForIdle()
        val frame = bounds(IN_APP_CAMERA_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)

        assertEquals("flush with the punch-hole edge", frame.top.value, strip.top.value, 0.51f)
        assertEquals("full width", frame.left.value, strip.left.value, 0.51f)
        assertEquals(frame.right.value, strip.right.value, 0.51f)
        assertEquals("one control row deep", STRIP_ROW_HEIGHT.value, strip.height.value, 0.51f)
        assertEquals("the shutter on the port edge, inside the frame's padding", (frame.bottom - Spacing.lg).value, shutter.bottom.value, 0.51f)
        assertEquals("centred along it", frame.centreX(), shutter.centreX(), 0.51f)
        val chip = bounds(CAMERA_FLASH_CHIP_TAG)
        assertTrue("the flash chip is in the strip: $chip in $strip", chip.left >= strip.left && chip.right <= strip.right && chip.top >= strip.top && chip.bottom <= strip.bottom)
    }

    /**
     * The status bar is hidden on **the Activity's** window, once, and shown again when the camera
     * leaves composition. That restore is the cost this design accepted: while the camera was a
     * `Dialog` the bar came back by the dialog's window being destroyed, with no restore path to
     * miss — now every exit routes through the camera leaving composition, and `onDispose` is what
     * covers all of them (CameraWindowChrome.kt). Whether the bar actually goes and returns is the
     * emulator's and the device's.
     */
    @Test
    fun `the status bar is hidden on the Activity's window and shown again when the camera leaves`() {
        var shown by mutableStateOf(true)
        composeRule.setContent { if (shown) Subject(FakeCameraCaptureSession()) }
        composeRule.waitForIdle()

        assertEquals("asked once", 1, hiddenOn.size)
        val (window, _) = hiddenOn.single()
        assertTrue("the Activity's window, because the camera draws in it", window === composeRule.activity.window)
        assertEquals("nothing restored while the camera is up", 0, shownOn.size)

        shown = false
        composeRule.waitForIdle()

        assertEquals("restored exactly once on the way out", 1, shownOn.size)
        assertTrue("and on the same window it was hidden on", shownOn.single().first === window)
    }

    /**
     * The other claim on the Activity's window (CameraWindowChrome.kt): seamless rotation, so the
     * turn the window makes with the device (`RequestWindowOrientation`, setting off) carries no
     * animation. The platform reads this window's own layout params, so the assertion is on the
     * attribute, and on it being put back when the camera leaves — the request is the camera's, not
     * the app's. Whether the platform then rotates seamlessly is the emulator's and the device's.
     */
    @Test
    fun `the Activity's window asks for seamless rotation while the camera is open, and stops on the way out`() {
        var shown by mutableStateOf(true)
        val before = composeRule.activity.window.attributes.rotationAnimation
        composeRule.setContent { if (shown) Subject(FakeCameraCaptureSession()) }
        composeRule.waitForIdle()

        assertEquals(
            "ROTATION_ANIMATION_SEAMLESS on the Activity's window, so a turn is a re-layout and not an animation",
            WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS,
            composeRule.activity.window.attributes.rotationAnimation,
        )

        shown = false
        composeRule.waitForIdle()

        assertEquals("put back, so only the camera rotates this way", before, composeRule.activity.window.attributes.rotationAnimation)
    }

    /**
     * A camera with no flash unit: no flash chip, and the grid chip takes the strip's first place;
     * nothing else moves. Until the grid chip this test also held "no chip, no strip at all"; the
     * grid chip is always present, so the camera's strip is never empty now, and the empty case is
     * held at the container, in `CameraStripTest`.
     */
    @Test
    fun `with no flash unit there is no flash chip, the grid chip comes first, and the shutter is where it was`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession(flashUnitOnOpen = false)) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_FLASH_CHIP_TAG).assertCountEquals(0)
        val strip = bounds(CAMERA_STRIP_TAG)
        val grid = bounds(CAMERA_GRID_CHIP_TAG)
        // The first slot, not the second: a chip's node sits inside its 48 dp touch target, so its
        // left edge is a few dp in, and a second slot would start a whole row plus the spacing along.
        assertTrue("the grid chip in the strip's first slot: $grid in $strip", grid.left - strip.left < STRIP_ROW_HEIGHT)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        assertEquals((frame.bottom - Spacing.lg).value, shutter.bottom.value, 0.51f)
        assertEquals(frame.centreX(), shutter.centreX(), 0.51f)
    }

    /**
     * **A finger on the flash chip reaches it**, with the viewfinder underneath: real touches at
     * screen coordinates, not a semantic click, which would bypass hit-testing (CLAUDE.md). A
     * finger is not a point, so five touches across the chip's own bounds, centre and four points
     * inset from its corners, each of which must reach the session.
     */
    @Test
    fun `a real touch anywhere on the flash chip reaches it, over the viewfinder`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }
        composeRule.waitForIdle()
        val chip = composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).fetchSemanticsNode().boundsInRoot
        val inset = 0.2f
        val points = listOf(
            chip.center,
            Offset(chip.left + chip.width * inset, chip.top + chip.height * inset),
            Offset(chip.right - chip.width * inset, chip.top + chip.height * inset),
            Offset(chip.left + chip.width * inset, chip.bottom - chip.height * inset),
            Offset(chip.right - chip.width * inset, chip.bottom - chip.height * inset),
        )

        points.forEachIndexed { i, point ->
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("touch ${i + 1} at $point reached the chip", i + 1, session.setFlashModeCalls)
        }
        // Changed 2026-09-26 (decision B8): the cycle is Off, Auto, On, Torch, so five taps from Off end on Auto.
        assertEquals("five taps from Off end on Auto", FlashMode.Auto, session.flashMode)
    }

    /**
     * The grid is over the preview and under the bands: at Grid it fills the camera's frame, and it
     * is placed before the strip and the shutter band, which is the order they draw and hit-test in.
     * Placement order is read from the frame's semantics children; that it is also the order the
     * pixels land in is the device check's first step.
     */
    @Test
    fun `at Grid the grid fills the preview and sits under both bands`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession(), gridMode = GridMode.Grid) }
        composeRule.waitForIdle()

        val frame = bounds(IN_APP_CAMERA_TAG)
        assertEquals("the grid spans the preview", frame, bounds(CAMERA_GRID_TAG))
        val order = composeRule.onNodeWithTag(IN_APP_CAMERA_TAG).fetchSemanticsNode().children.map { it.config.getOrNull(SemanticsProperties.TestTag) }
        val grid = order.indexOf(CAMERA_GRID_TAG)
        assertTrue("the grid is a child of the frame: $order", grid >= 0)
        assertTrue("placed before the strip: $order", grid < order.indexOf(CAMERA_STRIP_TAG))
        assertTrue("and before the shutter band: $order", grid < order.indexOf(CAMERA_SHUTTER_BAND_TAG))
    }

    @Test
    fun `at Off there is no grid and no level`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession(), gridMode = GridMode.Off) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(CAMERA_GRID_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(0)
    }

    /**
     * **The level's sensor does not outlive the camera** (the dispatch's rule: register on open,
     * unregister on close). With Grid + Level on, the camera is listening while it is open, and
     * not once it has closed.
     */
    @Test
    fun `at Grid and Level the level is shown and its sensor is released when the camera closes`() {
        val level = FakeLevelProvider(initial = 3f)
        var open by mutableStateOf(true)
        composeRule.setContent { if (open) Subject(FakeCameraCaptureSession(), gridMode = GridMode.GridLevel, levelProvider = level) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(CAMERA_GRID_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CAMERA_LEVEL_TAG).assertCountEquals(1)
        assertEquals("listening while open", 1, level.collectors)

        open = false
        composeRule.waitForIdle()

        assertEquals("released once closed", 0, level.collectors)
    }

    /** The outline is a second, stroked pass of the same text — it must not become a second text node. */
    @Test
    fun `outlined overlay text is one text node, not two`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession()) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(photoCountLabel(0)).assertCountEquals(1)
    }


    /**
     * The mirror of the landscape class's follow test: opened in portrait, the window turns to
     * landscape at `ROTATION_90` (port on the right) — a return from background on a phone, or an
     * ignored lock on a large screen — and the arrangement follows: shutter to the right edge,
     * strip down the left. The configuration is provided so the turn can be driven without
     * recreating the Activity, and the display pin is asserted.
     */
    @Test
    fun `opened in portrait, the arrangement follows a window turned to landscape, shutter to the port edge and strip to the punch-hole edge`() {
        var orientation by mutableStateOf(Configuration.ORIENTATION_PORTRAIT)
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { this.orientation = orientation }
            CompositionLocalProvider(LocalConfiguration provides configuration) { Subject(FakeCameraCaptureSession()) }
        }
        composeRule.waitForIdle()
        val frame = bounds(IN_APP_CAMERA_TAG)
        assertEquals("precondition: portrait arrangement, shutter at the bottom", (frame.bottom - Spacing.lg).value, bounds(CAMERA_SHUTTER_TAG).bottom.value, 0.51f)

        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        assertEquals("the harness must report the pinned rotation", Surface.ROTATION_90, ShadowDisplay.getDefaultDisplay().rotation)
        orientation = Configuration.ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()

        val shutter = bounds(CAMERA_SHUTTER_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)
        assertEquals("landscape arrangement at ROTATION_90: shutter on the right, the port edge", (frame.right - Spacing.lg).value, shutter.right.value, 0.51f)
        assertEquals("vertically centred", frame.centreY(), shutter.centreY(), 0.51f)
        assertEquals("strip down the left, the punch-hole edge", frame.left.value, strip.left.value, 0.51f)
        // 1.5 dp, not 0.51: this class's window stays portrait-sized (470.6 dp tall) while only the
        // configuration says landscape, and the frame's and the strip's bottom edges round that
        // half-pixel differently (471 vs 470). Full height to within a pixel is the claim.
        assertEquals("full height", frame.bottom.value, strip.bottom.value, 1.5f)
    }

}

private const val VIEWFINDER_TAG = "test-viewfinder"
