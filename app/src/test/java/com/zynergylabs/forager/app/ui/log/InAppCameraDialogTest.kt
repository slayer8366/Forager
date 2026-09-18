package com.zynergylabs.forager.app.ui.log

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import android.view.Surface
import android.content.Context
import android.view.Window
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import org.robolectric.shadows.ShadowDialog
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.robolectric.shadows.ShadowDisplay

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

    private val composeRule = createComposeRule()

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

    @Composable
    private fun Subject(
        session: CameraCaptureSession,
        viewfinder: @Composable (Modifier) -> Unit = { modifier -> Box(modifier.fillMaxSize()) },
        stripContent: (@Composable (ScreenEdge) -> Unit)? = null,
        useDefaultStrip: Boolean = false,
    ) {
        if (useDefaultStrip) {
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = CameraCaptureFiles(context),
                lockToPortrait = false,
                onPhotoCaptured = { captured += it },
                onDismiss = { dismissals += 1 },
                viewfinder = viewfinder,
            )
            return
        }
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = CameraCaptureFiles(context),
            lockToPortrait = false,
            onPhotoCaptured = { captured += it },
            onDismiss = { dismissals += 1 },
            stripContent = stripContent,
            viewfinder = viewfinder,
        )
    }

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

    /**
     * The owner's call, 2026-09-18: there is no Done control. The navigation bar's back is the way
     * out, which frees the punch-hole edge for the camera's top strip. Asserted by the label, in the
     * unmerged tree so a label merged into a parent cannot hide from it.
     */
    @Test
    fun `there is no Done control, and system back is how the camera closes`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession()) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Done", useUnmergedTree = true).assertCountEquals(0)
        composeRule.pressBackOnCameraDialog()
        assertEquals(1, dismissals)
    }

    @Test
    fun `back dismisses, and the photos already taken are not withdrawn`() {
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session) }
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.pressBackOnCameraDialog()

        assertEquals(1, dismissals)
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

        composeRule.pressBackOnCameraDialog()

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

    // ── The status bar (hide-status-bar dispatch, 2026-09-18) ───────────────────────────────────

    /**
     * The status bar is requested hidden on the camera dialog's own window and on no other. What
     * is asserted is the request itself, read from the platform's own `InsetsController` on each
     * window, not a proxy like "a controller was obtained": Robolectric runs the real controller,
     * and its requested-visible types are what the system acts on. Read by reflection because the
     * getter is not public API; the test-only cost of that is named in [statusBarRequestedVisible].
     *
     * What this cannot show: whether the bar actually disappears, and whether the controls take
     * the space back. Robolectric reports zero insets and draws no status bar, so both are
     * device-only (v4 step 6 and the dispatch's device items).
     */
    @Test
    fun `the status bar is requested hidden on the camera's own window, and the Activity's is left alone`() {
        lateinit var activityWindow: Window
        composeRule.setContent {
            activityWindow = (LocalView.current.context as Activity).window
            Subject(FakeCameraCaptureSession())
        }
        composeRule.waitForIdle()
        val dialogWindow = checkNotNull(ShadowDialog.getLatestDialog()?.window) { "precondition: the camera dialog has a window" }
        assertTrue("precondition: the dialog's window is not the Activity's", dialogWindow !== activityWindow)

        assertFalse("status bar still requested visible on the camera's window", statusBarRequestedVisible(dialogWindow))
        assertTrue("status bar requested hidden on the Activity's window", statusBarRequestedVisible(activityWindow))
    }

    /**
     * Leaving is the restore: every exit (Done, back, the absence timeout) ends in the host no
     * longer composing the dialog, which is what this does.
     *
     * **What this does not cover, found by revert:** it still passes with `HideStatusBarForThisDialog`'s
     * `onDispose { show(...) }` removed. Under Robolectric the dialog's window going away is enough
     * on its own for the request to read visible again, so this pins the removal-restores property,
     * not the explicit `show()`. That call stays as belt and braces for platforms that might hold a
     * departing window's request; nothing here proves it is needed.
     */
    @Test
    fun `the status bar is requested visible again when the dialog leaves`() {
        val shown = mutableStateOf(true)
        composeRule.setContent { if (shown.value) Subject(FakeCameraCaptureSession()) }
        composeRule.waitForIdle()
        val dialogWindow = checkNotNull(ShadowDialog.getLatestDialog()?.window)
        assertFalse("precondition: hidden while open", statusBarRequestedVisible(dialogWindow))

        shown.value = false
        composeRule.waitForIdle()

        assertTrue("status bar not requested visible again after the dialog left", statusBarRequestedVisible(dialogWindow))
    }

    /**
     * Whether [window]'s own insets controller currently requests the status bar visible. Reads
     * `InsetsController.getRequestedVisibleTypes`, which is platform code Robolectric runs for
     * real but which is not in the public SDK, hence reflection. If a platform update renames it,
     * this throws rather than returning a default, so the test fails loudly instead of passing.
     */
    private fun statusBarRequestedVisible(window: Window): Boolean {
        val controller = checkNotNull(window.insetsController) { "no insets controller on $window" }
        val types = controller.javaClass.getMethod("getRequestedVisibleTypes").invoke(controller) as Int
        return types and android.view.WindowInsets.Type.statusBars() != 0
    }

    // ── The top strip (camera-top-strip dispatch, 2026-09-18) ────────────────────────────────

    /**
     * Pins the display's rotation and proves the harness reports it, the `139727a` convention: a
     * harness that ignored the pin would run every edge assertion at one rotation and could not fail.
     */
    private fun pinDisplayRotation(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        @Suppress("DEPRECATION") val reported = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        assertEquals("the harness must actually report the rotation this test is about", rotation, reported)
    }

    /**
     * A portrait window at rotation 0: the device's punch-hole edge is the screen's top
     * ([deviceTopEdgeOnScreen]), so the strip is there, full width and one row deep, with the shutter
     * on the opposite edge. Then the phone is turned upside down, the hold where punch-hole is at the
     * user's bottom: the window is locked, so the strip must not move, because the device's top edge
     * has not moved on the screen.
     */
    @Test
    fun `in a portrait window the strip is on the device's punch-hole edge, and stays there held upside down`() {
        pinDisplayRotation(Surface.ROTATION_0)
        val session = FakeCameraCaptureSession()
        composeRule.setContent { Subject(session, useDefaultStrip = true) }
        composeRule.waitForIdle()
        val frame = composeRule.onNodeWithTag(IN_APP_CAMERA_TAG).getBoundsInRoot()
        val strip = composeRule.onNodeWithTag(CAMERA_STRIP_TAG).getBoundsInRoot()
        val shutter = composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).getBoundsInRoot()

        assertEquals(ScreenEdge.Top, deviceTopEdgeOnScreen(Surface.ROTATION_0))
        assertEquals("flush with the device's punch-hole edge (screen top here; zero insets under Robolectric)", frame.top.value, strip.top.value, 0.51f)
        assertEquals("one row deep", CAMERA_STRIP_THICKNESS.value, strip.height.value, 0.51f)
        assertEquals("full length of that edge", frame.width.value, strip.width.value, 0.51f)
        assertTrue("the shutter is on the other edge: ${shutter.bottom} vs strip ${strip.bottom}", shutter.top > strip.bottom)
        composeRule.onNodeWithTag(CAMERA_STRIP_PLACEHOLDER_TAG, useUnmergedTree = true).assertExists()

        session.deviceRotation = Surface.ROTATION_180
        composeRule.waitForIdle()
        assertEquals("held upside down, the strip has not moved", strip, composeRule.onNodeWithTag(CAMERA_STRIP_TAG).getBoundsInRoot())
    }

    /** Empty is nothing: no strip node, so no band reserved across the viewfinder. */
    @Test
    fun `an empty strip composes nothing and takes no space`() {
        composeRule.setContent { Subject(FakeCameraCaptureSession(), stripContent = null) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(CAMERA_STRIP_TAG, useUnmergedTree = true).assertCountEquals(0)
    }
}

private const val VIEWFINDER_TAG = "test-viewfinder"
