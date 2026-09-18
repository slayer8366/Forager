package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.content.res.Configuration
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/**
 * The camera dialog in a **landscape window** (this class's qualifiers): which arrangement it
 * chooses for each state of the setting, where the shutter, count and Done land, that nothing
 * turns in the landscape arrangement when the device does, and that the arrangement is held when
 * the configuration changes underneath it. Measured on the real dialog over the fake session,
 * with bounds read back from the composition, not from a description of the layout.
 *
 * What a Robolectric window cannot show: whether `LOCKED` actually pins the device's window, and
 * whether the frame stays still on a real rotation. Those are on the device check by name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xhdpi")
class InAppCameraDialogLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private val session = FakeCameraCaptureSession()
    private var setOrientation: (Int) -> Unit = {}

    /** The dialog under a configuration this test controls, so "the window turned underneath it" can be simulated. */
    private fun setDialog(lockToPortrait: Boolean) {
        composeRule.setContent {
            val base = LocalConfiguration.current
            var orientation by remember { mutableStateOf(base.orientation) }
            setOrientation = { orientation = it }
            val configuration = Configuration(base).apply { this.orientation = orientation }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    lockToPortrait = lockToPortrait,
                    onPhotoCaptured = {},
                    onDismiss = {},
                    viewfinder = { modifier -> Box(modifier) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getBoundsInRoot()
    private fun DpRect.centreX() = ((left + right) / 2).value
    private fun DpRect.centreY() = ((top + bottom) / 2).value

    @Test
    fun `the window really is landscape here, before anything else is claimed about it`() {
        setDialog(lockToPortrait = false)
        val frame = bounds(IN_APP_CAMERA_TAG)
        assertTrue("${frame.width} x ${frame.height}", frame.width > frame.height)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, composeRule.activity.resources.configuration.orientation)
    }

    /**
     * Pins the window's rotation before the dialog opens, and **proves it took**. The arrangement
     * reads the display's rotation at open, so a harness that silently ignored this would leave
     * every rotation-specific assertion below running at `ROTATION_0` — passing on the one sample
     * that cannot fail them (CLAUDE.md: confirm the sample includes the cases that could have
     * failed the check). The precondition assertion is what makes that impossible.
     */
    private fun setDisplayRotation(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        @Suppress("DEPRECATION") val reported = composeRule.activity.windowManager.defaultDisplay.rotation
        assertEquals("the harness must actually report the rotation this test is about", rotation, reported)
    }

    @Test
    fun `at ROTATION_90 the port edge is the screen's right, so the shutter is there with the count inboard`() {
        setDisplayRotation(Surface.ROTATION_90)
        setDialog(lockToPortrait = false)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        val count = bounds(CAMERA_COUNT_TAG)
        val done = bounds(CAMERA_DONE_TAG)

        assertEquals("port edge (right at ROTATION_90), inside the frame's padding", (frame.right - Spacing.lg).value, shutter.right.value, 0.51f)
        assertEquals("vertically centred", frame.centreY(), shutter.centreY(), 0.51f)
        assertTrue("never along the bottom: ${shutter.bottom} of ${frame.bottom}", shutter.bottom < frame.bottom - Spacing.lg * 2)
        assertTrue("the count sits beside the shutter, to its left: ${count.right} vs ${shutter.left}", count.right <= shutter.left)
        assertEquals("the count is level with the shutter", shutter.centreY(), count.centreY(), 1f)
        // Done's node is the TextButton's own bounds, which include Material's minimum touch
        // target, so its corner is asserted as a region rather than an exact offset.
        assertTrue("Done top-left: $done in $frame", done.left < frame.left + Spacing.lg && done.top < frame.top + Spacing.lg)
        assertTrue("Done is left of the count and above it", done.right < count.left && done.bottom < count.top)
        assertTrue("right of centre, the mirror claim: ${shutter.centreX()} vs ${frame.centreX()}", shutter.centreX() > frame.centreX())
    }

    /**
     * The mirror of the test above, and the one the 2026-09-17 device-check run was reported
     * against: at `ROTATION_270` the charger port is on the user's left, so the shutter belongs on
     * the screen's left. Before the fix this drew on the right — the punch-hole end of an S26
     * Ultra — because the arrangement had no rotation input and both landscapes got `CenterEnd`.
     */
    @Test
    fun `at ROTATION_270 the port edge is the screen's left, so the shutter is there with the count inboard`() {
        setDisplayRotation(Surface.ROTATION_270)
        setDialog(lockToPortrait = false)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        val count = bounds(CAMERA_COUNT_TAG)
        val done = bounds(CAMERA_DONE_TAG)

        assertEquals("port edge (left at ROTATION_270), inside the frame's padding", (frame.left + Spacing.lg).value, shutter.left.value, 0.51f)
        assertEquals("vertically centred", frame.centreY(), shutter.centreY(), 0.51f)
        assertTrue("never along the bottom: ${shutter.bottom} of ${frame.bottom}", shutter.bottom < frame.bottom - Spacing.lg * 2)
        assertTrue("the count sits beside the shutter, to its right: ${count.left} vs ${shutter.right}", count.left >= shutter.right)
        assertEquals("the count is level with the shutter", shutter.centreY(), count.centreY(), 1f)
        assertTrue("Done top-left: $done in $frame", done.left < frame.left + Spacing.lg && done.top < frame.top + Spacing.lg)
        assertTrue("left of centre, the mirror claim: ${shutter.centreX()} vs ${frame.centreX()}", shutter.centreX() < frame.centreX())
    }

    // The two landscapes being mirror images is asserted by the side-of-centre line in each of the
    // two tests above, not by a third test comparing them: a compose rule takes `setContent` once,
    // so one test cannot open the dialog at both rotations. The pure-function form of the same
    // claim — that the two rotations never resolve to one arrangement — is in CameraArrangementTest.

    @Test
    fun `setting off in a landscape window, nothing turns when the device does`() {
        setDialog(lockToPortrait = false)
        val doneBefore = bounds(CAMERA_DONE_TAG)
        val countBefore = bounds(CAMERA_COUNT_TAG)
        assertTrue("precondition: Done is wider than tall, so a quarter turn would be visible", doneBefore.width > doneBefore.height)

        // A reading a quarter turn from the harness's display rotation, not equal to it: equal
        // would cancel under sensor-minus-display even with rotateWithDevice applied, and this
        // test would then pass with the modifier wrongly present (a revert showed exactly that).
        @Suppress("DEPRECATION") val displayRotation = composeRule.activity.windowManager.defaultDisplay.rotation
        session.deviceRotation = (displayRotation + 1) % 4
        composeRule.waitForIdle()

        assertEquals("upright, whatever the sensor says: the window already matches the grip", doneBefore, bounds(CAMERA_DONE_TAG))
        assertEquals(countBefore, bounds(CAMERA_COUNT_TAG))
    }

    @Test
    fun `setting on in a landscape window, the portrait arrangement is used and its controls turn`() {
        setDialog(lockToPortrait = true)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        assertEquals("shutter along the bottom, centred: the window is about to be forced portrait", frame.centreX(), shutter.centreX(), 0.51f)
        assertEquals((frame.bottom - Spacing.lg).value, shutter.bottom.value, 0.51f)

        // The portrait arrangement's angle is sensor minus display. Robolectric's landscape window
        // reports a turned display (the real one would be forced portrait by the setting, which
        // this harness cannot do), so a device reading equal to the display's would cancel to no
        // turn — correctly. A reading a quarter turn from the display's is what must turn here.
        @Suppress("DEPRECATION") val displayRotation = composeRule.activity.windowManager.defaultDisplay.rotation
        val doneBefore = bounds(CAMERA_DONE_TAG)
        session.deviceRotation = (displayRotation + 1) % 4
        composeRule.waitForIdle()
        val doneAfter = bounds(CAMERA_DONE_TAG)
        assertEquals("a quarter turn from a display at $displayRotation swaps the extents", doneBefore.width.value, doneAfter.height.value, 0.51f)
        assertEquals(doneBefore.height.value, doneAfter.width.value, 0.51f)
    }

    @Test
    fun `the arrangement chosen at open is held when the configuration changes underneath the dialog`() {
        setDialog(lockToPortrait = false)
        val before = bounds(CAMERA_SHUTTER_TAG)

        setOrientation(Configuration.ORIENTATION_PORTRAIT)
        composeRule.waitForIdle()

        assertEquals("no reflow: the shutter is where it was", before, bounds(CAMERA_SHUTTER_TAG))
    }
}
