package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
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
 * The camera's gear chip and panel (dispatch 2026-09-28-707; the owner: "Move the two camera options
 * at the bottom to a settings menu inside the camera itself", "Gear chip with the others
 * (Recommended)"), driven through the real [InAppCameraDialog] with real touches at screen
 * coordinates (CLAUDE.md: a semantic click asserts wiring, not routing). The values and handlers are
 * handed in the way `AvailabilityScreen` hands them down, and the handlers store what they are asked,
 * as `AvailabilityViewModel` does; the path from the screen is `AvailabilityScreenInAppCameraTest`'s.
 *
 * Held what Settings' own tests held for its two rows until dispatch 2026-10-13-03 emptied the panel
 * (RECORD -968, -972): the defaults, the explanations, and a touch anywhere across a row writing the
 * toggled value. Those two tests, `the panel shows both settings with their explanations, location
 * on and lock off by default` and `a real touch anywhere across each row writes the toggled value`,
 * were removed with the rows and are recorded in `docs/archive/camera-portrait-lock.md`; the empty
 * panel's own test replaces them, and the Location chip's tests hold the value. Window size is a phone's (the S22 Ultra's, 384 x 823 dp);
 * Robolectric reports zero insets (CLAUDE.md), so nothing here is evidence about the cut-out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class InAppCameraSettingsPanelTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val defaults = AvailabilityUiState()
    private var saveLocation by mutableStateOf(defaults.autoSaveLocationToPhotos)
    private val calls = mutableListOf<String>()
    private val captured = mutableListOf<PhotoSource>()
    private var dismissals = 0
    private val session = FakeCameraCaptureSession()

    private fun setDialog() {
        composeRule.setContent {
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onPhotoCaptured = { captured += it },
                onDismiss = { dismissals += 1 },
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = saveLocation,
                onAutoSaveLocationToPhotosChanged = { requested ->
                    calls += "photo location $requested"
                    saveLocation = requested
                },
                levelProvider = FakeLevelProvider(),
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getBoundsInRoot()
    private fun panelCount() = composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).fetchSemanticsNodes().size

    /** A real touch at [x], [y] in dp, hit-tested from the root as a finger is. */
    private fun touchAt(x: Float, y: Float) {
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) {
        val b = bounds(tag)
        touchAt(((b.left + b.right) / 2).value, ((b.top + b.bottom) / 2).value)
    }

    private fun openPanel() {
        touchCentreOf(CAMERA_SETTINGS_CHIP_TAG)
        assertEquals("the gear opened the panel", 1, panelCount())
    }

    @Test
    fun `the gear is in the strip, and the panel is closed until it is touched`() {
        setDialog()
        val strip = bounds(CAMERA_STRIP_TAG)
        val gear = bounds(CAMERA_SETTINGS_CHIP_TAG)
        assertTrue("the gear is in the strip: $gear in $strip", gear.left >= strip.left && gear.right <= strip.right && gear.top >= strip.top && gear.bottom <= strip.bottom)
        composeRule.onNodeWithTag(CAMERA_SETTINGS_CHIP_TAG).assertContentDescriptionEquals(CAMERA_SETTINGS_LABEL)
        assertEquals(0, panelCount())
    }

    /** A finger is not a point (CLAUDE.md): five touches across the gear's own bounds, each opening the panel, each closed by Back. */
    @Test
    fun `a real touch anywhere on the gear opens the panel`() {
        setDialog()
        val b = bounds(CAMERA_SETTINGS_CHIP_TAG)
        val inset = 0.2f
        val l = b.left.value; val t = b.top.value; val w = b.width.value; val h = b.height.value
        val points = listOf(
            (l + w / 2) to (t + h / 2),
            (l + w * inset) to (t + h * inset),
            (l + w * (1 - inset)) to (t + h * inset),
            (l + w * inset) to (t + h * (1 - inset)),
            (l + w * (1 - inset)) to (t + h * (1 - inset)),
        )
        points.forEachIndexed { i, (x, y) ->
            touchAt(x, y)
            assertEquals("touch ${i + 1} at ($x, $y) opened the panel", 1, panelCount())
            composeRule.pressBackOnCamera()
            assertEquals("and Back closed it", 0, panelCount())
        }
        assertEquals("Back closed only the panel, five times", 0, dismissals)
        assertEquals("nothing was written by opening and closing", emptyList<String>(), calls)
    }

    /**
     * Dispatch 2026-10-13-03 (RECORD -968, -971, -972; the owner: "Remove save location from the settings, empty it out
     * completely"): the gear stays and opens the panel, and the panel holds no rows. It says so in one plain line (the
     * planner's call, -972) rather than opening blank. The two old rows' labels and explanation are written out here as
     * literals, not through the constants that drew them, so this test does not depend on code the removal deletes.
     */
    @Test
    fun `the panel holds no settings and says so`() {
        setDialog()
        openPanel()

        assertEquals("the empty line shows once", 1, composeRule.onAllNodesWithText("No camera settings yet").fetchSemanticsNodes().size)
        for (text in listOf(
            "Lock camera to portrait",
            "Keeps the camera and its controls still when you turn the phone. Photos are always saved in portrait, even when you hold the phone sideways.",
            "Automatically Save Location to Photos",
        )) {
            assertEquals("'$text' is not in the panel", 0, composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size)
        }
        assertEquals(
            "no checkbox in the panel",
            0,
            composeRule.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag(CAMERA_SETTINGS_PANEL_TAG)), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        assertEquals("nothing is written by opening it", emptyList<String>(), calls)
    }

    /** Back closes the panel and nothing else; the next Back closes the camera, as it always has. */
    @Test
    fun `Back closes the panel first, and the camera on the next Back`() {
        setDialog()
        openPanel()
        composeRule.pressBackOnCamera()
        assertEquals(0, panelCount())
        assertEquals("the camera is still open", 1, composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).fetchSemanticsNodes().size)
        assertEquals(0, dismissals)

        composeRule.pressBackOnCamera()
        assertEquals("the second Back reached the camera's own close", 1, dismissals)
    }

    /**
     * A tap outside closes the panel and does nothing else, even on the shutter: the convention for a
     * popup dismissed by a tap outside. The positive control is the same point once the panel is
     * closed, which takes a photo, so "no photo" the first time is the panel taking the touch and not
     * a touch that missed.
     */
    @Test
    fun `a tap outside closes the panel and does nothing else, even on the shutter`() {
        setDialog()
        openPanel()
        touchCentreOf(CAMERA_SHUTTER_TAG)
        assertEquals("the panel closed", 0, panelCount())
        assertEquals("no photo was taken", 0, session.captureCalls)
        assertEquals("and the camera did not close", 0, dismissals)

        touchCentreOf(CAMERA_SHUTTER_TAG)
        composeRule.waitUntil(timeoutMillis = 5_000) { captured.size == 1 }
        assertEquals("the control: the same point, panel closed, is the shutter", 1, session.captureCalls)
    }

    /** In portrait the panel is under the strip and clear of the shutter band, so it never covers the shutter, the count or an error. */
    @Test
    fun `in portrait the panel sits under the strip and clear of the shutter band`() {
        setDialog()
        openPanel()
        val strip = bounds(CAMERA_STRIP_TAG)
        val panel = bounds(CAMERA_SETTINGS_PANEL_TAG)
        val band = bounds(CAMERA_SHUTTER_BAND_TAG)
        assertTrue("below the strip: $panel under $strip", panel.top >= strip.bottom)
        assertTrue("above the shutter band: $panel over $band", panel.bottom <= band.top)
    }

    /**
     * In landscape the strip is on one side and the shutter band on the other, and the panel sits
     * beside the strip, clear of the band. Run at both landscape rotations, since the two are mirror
     * images (`CameraArrangement`); the rotation is pinned and proved before the dialog opens, as
     * `InAppCameraDialogLandscapeTest.setDisplayRotation` does, because the arrangement reads it then.
     */
    @Test
    @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `in landscape at ROTATION_90 the panel sits beside the strip and clear of the shutter band`() {
        assertPanelBesideStripInLandscape(Surface.ROTATION_90)
    }

    @Test
    @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `in landscape at ROTATION_270 the panel sits beside the strip and clear of the shutter band`() {
        assertPanelBesideStripInLandscape(Surface.ROTATION_270)
    }

    private fun assertPanelBesideStripInLandscape(rotation: Int) {
        // RECORD -985: full screen, a turned display is held in the natural frame (InAppCameraLargeScreenHoldTest); the
        // landscape arrangement this measures is reached in a multi-window window. Setup only, assertions unchanged.
        Shadows.shadowOf(composeRule.activity).setInMultiWindowMode(true)
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        @Suppress("DEPRECATION") val reported = composeRule.activity.windowManager.defaultDisplay.rotation
        assertEquals("the harness reports the rotation this test is about", rotation, reported)
        setDialog()
        openPanel()

        val strip = bounds(CAMERA_STRIP_TAG)
        val panel = bounds(CAMERA_SETTINGS_PANEL_TAG)
        val band = bounds(CAMERA_SHUTTER_BAND_TAG)
        val frame = bounds(IN_APP_CAMERA_TAG)
        assertTrue("the window is landscape: $frame", frame.width > frame.height)
        assertTrue("the strip runs down a side: $strip", strip.height > strip.width)
        if (strip.left < band.left) {
            assertTrue("beside the strip on the left: $panel, $strip", panel.left >= strip.right)
            assertTrue("clear of the band on the right: $panel, $band", panel.right <= band.left)
        } else {
            assertTrue("beside the strip on the right: $panel, $strip", panel.right <= strip.left)
            assertTrue("clear of the band on the left: $panel, $band", panel.left >= band.right)
        }
        assertTrue("within the window's height: $panel in $frame", panel.top >= frame.top && panel.bottom <= frame.bottom)
    }

    /**
     * The panel turns with the device, by the one rotation rule, like the chips (owner, 2026-09-17).
     * Asserted on the angle ([RotateWithDeviceTarget]), as the flash chip's is in
     * `InAppCameraDialogLandscapeTest`: in a portrait window, a device reading of ROTATION_90 turns it
     * a quarter clockwise.
     */
    @Test
    fun `the panel turns with the device`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        setDialog()
        openPanel()
        assertEquals("no reading yet, no turn", 0f, panelAngle(), 0.01f)

        session.deviceRotation = Surface.ROTATION_90
        composeRule.waitForIdle()

        assertEquals("a quarter turn clockwise", 90f, panelAngle(), 0.01f)
        assertEquals("and the gear turns with it", 90f, composeRule.onNodeWithTag(CAMERA_SETTINGS_CHIP_TAG).fetchSemanticsNode().config[RotateWithDeviceTarget], 0.01f)
    }

    private fun panelAngle(): Float = composeRule.onNodeWithTag(CAMERA_SETTINGS_PANEL_TAG).fetchSemanticsNode().config[RotateWithDeviceTarget]
}
