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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraSessionState
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
 * The fake writes a real file for each successful capture, because the production code creates a
 * destination through [CameraCaptureFiles] and deletes it again on failure; a fake that only
 * returned a `Result` would let a "the failed capture's empty file is cleaned up" assertion pass
 * without there being a file either way.
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

    private class FakeSession(
        override var state: CameraSessionState = CameraSessionState.Ready,
        private var failing: Boolean = false,
    ) : CameraCaptureSession {
        var captureCalls = 0
            private set

        fun failEveryCapture() { failing = true }

        fun recover() { failing = false }

        override suspend fun capture(destination: File): Result<Unit> {
            captureCalls += 1
            destination.parentFile?.mkdirs()
            if (failing) {
                // A partial file, *then* the failure. This is the case the production cleanup
                // exists for and the only one in which it can be observed: a camera that errors
                // before writing anything leaves nothing to clean up, so a fake that simply
                // returned a failure would make `a failed capture leaves no file behind` pass
                // whether or not the screen deleted anything. A revert check caught exactly that
                // — see this class's own note on it.
                destination.writeBytes(byteArrayOf(0xFF.toByte()))
                return Result.failure(IllegalStateException("the camera said no"))
            }
            destination.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
            return Result.success(Unit)
        }
    }

    @Composable
    private fun Subject(session: CameraCaptureSession) {
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = CameraCaptureFiles(context),
            onPhotoCaptured = { captured += it },
            onDismiss = { dismissals += 1 },
            viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
        )
    }

    // ── The request itself ───────────────────────────────────────────────────────────────────

    /** The headline: the camera does not close after one photo. This is what the owner asked for. */
    @Test
    fun `the camera stays open across several shots and hands up one photo per tap`() {
        val session = FakeSession()
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
        val session = FakeSession()
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
        val session = FakeSession()
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
        val session = FakeSession()
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
    fun `Done dismisses, and the photos already taken are not withdrawn`() {
        val session = FakeSession()
        composeRule.setContent { Subject(session) }
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CAMERA_DONE_TAG).performClick()
        composeRule.waitForIdle()

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
        val session = FakeSession().apply { failEveryCapture() }
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
        val session = FakeSession().apply { failEveryCapture() }
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
        val session = FakeSession().apply { failEveryCapture() }
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
        val session = FakeSession(state = CameraSessionState.Unavailable("This device has no camera available."))
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_UNAVAILABLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("This device has no camera available.").assertIsDisplayed()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a camera still opening shows progress rather than a dead viewfinder`() {
        val session = FakeSession(state = CameraSessionState.Opening)
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_OPENING_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).assertIsNotEnabled()
    }

    /** Dismissal has to work from a camera that never opened, or the user is stuck on a black screen. */
    @Test
    fun `an unavailable camera can still be dismissed`() {
        val session = FakeSession(state = CameraSessionState.Unavailable("The camera is in use by another app."))
        composeRule.setContent { Subject(session) }

        composeRule.onNodeWithTag(CAMERA_DONE_TAG).performClick()
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

        val success = runBlocking { FakeSession().capture(written) }
        val failure = runBlocking { FakeSession().apply { failEveryCapture() }.capture(partial) }

        assertTrue(success.isSuccess)
        assertTrue("a successful capture leaves a file", written.exists())
        assertTrue(failure.isFailure)
        assertTrue("and a failed one leaves a stub for the screen to clean up", partial.exists())
        assertFalse("which is not a whole photo", partial.readBytes().size > 1)
    }
}
