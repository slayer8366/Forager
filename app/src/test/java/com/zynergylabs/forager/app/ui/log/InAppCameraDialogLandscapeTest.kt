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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import com.zynergylabs.forager.app.ui.theme.Spacing
import org.junit.Assert.assertEquals
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
 *
 * **Since dispatch 2026-10-13-03 there is one state of the setting**: "Lock camera to portrait" was
 * removed (RECORD -968), and with it three tests here: `wiring only, setting on, the portrait
 * arrangement is used and a control turns for whatever value the dialog is handed`, `setting on,
 * through the gate, nothing turns for any sensor reading, before or after the window flips`, and
 * `setting off, through the gate, the count still turns when the sensor does`, the last a duplicate
 * of `setting off in a landscape window, the count turns in place when the device does, as in
 * portrait` through a fake (`GatedFakeCameraCaptureSession`, also removed) whose gate no longer
 * exists. They are recorded in `docs/archive/camera-portrait-lock.md`.
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

    /**
     * RECORD -985: a full-screen window whose display is turned is now held in the device's natural frame
     * (`NaturalOrientationHold`, tested in `InAppCameraLargeScreenHoldTest`), so the landscape arrangement this class
     * measures, at a turned display, is reached only in split screen or a freeform window. Every test here runs in one.
     * Setup only: no assertion in this class changed. Before RECORD -985 the window was full screen.
     */
    @Before
    fun inAMultiWindowWindow() {
        Shadows.shadowOf(composeRule.activity).setInMultiWindowMode(true)
    }

    /** The dialog under a configuration this test controls, so "the window turned underneath it" can be simulated. */
    private fun setDialog(session: CameraCaptureSession = this.session) {
        composeRule.setContent {
            val base = LocalConfiguration.current
            var orientation by remember { mutableStateOf(base.orientation) }
            setOrientation = { orientation = it }
            val configuration = Configuration(base).apply { this.orientation = orientation }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    onPhotoCaptured = {},
                    onDismiss = {},
                    gridMode = GridMode.Off,
                    onGridModeChanged = {},
                    autoSaveLocationToPhotos = true,
                    onAutoSaveLocationToPhotosChanged = {},
                    levelProvider = FakeLevelProvider(),
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
        setDialog()
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

    /**
     * RECORD -958 (asked whether the in-app camera should look the same both ways, as the map does since RECORD -956, the owner:
     * "Yes"): in a landscape window the camera's layout is ROTATION_90's at both rotations: the shutter on the screen's right,
     * vertically centred, the count inboard to its left, and the strip flush with the screen's left, full height. Each rotation is
     * held to the same absolute bounds in this same window, which is the equality: a compose rule takes `setContent` once, so one
     * test cannot open the dialog at both. Was (2026-09-17 device-check run): ROTATION_270 the mirror, the shutter on the screen's
     * left (the charger port's edge there), `assertEquals((frame.left + Spacing.lg), shutter.left)`, the count to its right, "left of
     * centre, the mirror claim".
     */
    private fun assertLandscapeLayout(rotation: Int) {
        setDisplayRotation(rotation)
        setDialog()
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        val count = bounds(CAMERA_COUNT_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)
        println("MEASURED camera at rotation $rotation: frame $frame, shutter $shutter, count $count, strip $strip")

        assertEquals("the shutter on the screen's right, inside the frame's padding", (frame.right - Spacing.lg).value, shutter.right.value, 0.51f)
        assertEquals("vertically centred", frame.centreY(), shutter.centreY(), 0.51f)
        assertTrue("never along the bottom: ${shutter.bottom} of ${frame.bottom}", shutter.bottom < frame.bottom - Spacing.lg * 2)
        assertTrue("the count sits beside the shutter, to its left: ${count.right} vs ${shutter.left}", count.right <= shutter.left)
        assertEquals("the count is level with the shutter", shutter.centreY(), count.centreY(), 1f)
        assertEquals("the strip flush with the screen's left", frame.left.value, strip.left.value, 0.51f)
        assertEquals("full height", frame.top.value, strip.top.value, 0.51f)
        assertEquals(frame.bottom.value, strip.bottom.value, 0.51f)
    }

    @Test
    fun `at ROTATION_90 the shutter is on the screen's right with the count inboard, and the strip on its left`() = assertLandscapeLayout(Surface.ROTATION_90)

    @Test
    fun `at ROTATION_270 the layout is the same as at ROTATION_90, shutter right and strip left`() = assertLandscapeLayout(Surface.ROTATION_270)

    /**
     * **One rotation rule, every arrangement** (owner, 2026-09-17). This test used to be the
     * opposite, `nothing turns when the device does`, asserting "upright, whatever the sensor says:
     * the window already matches the grip". That clause had no source — the owner stated one rule
     * and the landscape exception was written into the spec by the planner — and on an S26 Ultra it
     * left Done and the count sideways as soon as the phone turned.
     *
     * The same reading the old test used, a quarter turn from the display's, because a reading
     * *equal* to the display's cancels under sensor-minus-display whether or not the modifier is
     * present (the 2026-09-15 session found exactly that with a revert), and would make this test
     * unable to fail.
     */
    @Test
    fun `setting off in a landscape window, the count turns in place when the device does, as in portrait`() {
        setDialog()
        val countBefore = bounds(CAMERA_COUNT_TAG)
        // Done is gone (Back is the way out), so "turned" and "in place" are both asserted on the count.
        // No such aspect check on the count, deliberately. This harness measures "No photos yet" at
        // about 7x20dp, under the old code as well as this one (measured both ways with a probe on
        // 2026-09-18), so width-greater-than-height is not what upright looks like here and would
        // fail on correct code. The extent swap and the fixed centre below do not depend on it.

        @Suppress("DEPRECATION") val displayRotation = composeRule.activity.windowManager.defaultDisplay.rotation
        session.deviceRotation = (displayRotation + 1) % 4
        composeRule.waitForIdle()
        val countAfter = bounds(CAMERA_COUNT_TAG)

        assertEquals("the count turned a quarter too", countBefore.width.value, countAfter.height.value, 0.51f)
        assertEquals("in place", countBefore.centreX(), countAfter.centreX(), 0.51f)
        assertEquals(countBefore.centreY(), countAfter.centreY(), 0.51f)
    }

    /** Turning the controls must not move the shutter: it was put on the port edge at `139727a` and confirmed on a device. */
    @Test
    fun `turning the controls leaves the shutter exactly where it was`() {
        setDisplayRotation(Surface.ROTATION_270)
        setDialog()
        val shutterBefore = bounds(CAMERA_SHUTTER_TAG)

        session.deviceRotation = Surface.ROTATION_0
        composeRule.waitForIdle()

        assertEquals("the shutter does not move when the controls beside it turn", shutterBefore, bounds(CAMERA_SHUTTER_TAG))
    }

    /**
     * **The arrangement follows the window** (owner, 2026-09-18). This test used to assert the
     * opposite — "held when the configuration changes underneath the dialog", the shutter staying on
     * the right edge — for the large-screen case where the platform ignores the lock. That ruling
     * was reversed once its consequence was visible: holding leaves a landscape layout in a
     * portrait window, the exact mismatch a background-and-return produced on the phone. Same case,
     * opposite expectation; the old assertion's failure under the new key
     * (`expected right edge 552–624 dp, was 284–356 dp`) is this test's before-state.
     *
     * The window is turned two ways at once, as a real turn does: the provided configuration goes
     * portrait and the display's rotation goes to `ROTATION_0`, with the pin asserted so the test
     * cannot pass on a display that stayed at the landscape rotation.
     */
    @Test
    fun `the arrangement follows when the window turns underneath the dialog, the shutter moving to the new window's port edge`() {
        setDisplayRotation(Surface.ROTATION_90)
        setDialog()
        val frame = bounds(IN_APP_CAMERA_TAG)
        val before = bounds(CAMERA_SHUTTER_TAG)
        assertEquals("precondition: landscape arrangement, shutter on the right (port) edge", (frame.right - Spacing.lg).value, before.right.value, 0.51f)

        setDisplayRotation(Surface.ROTATION_0)
        setOrientation(Configuration.ORIENTATION_PORTRAIT)
        composeRule.waitForIdle()

        val after = bounds(CAMERA_SHUTTER_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)
        assertEquals("portrait arrangement: the shutter on the window's bottom, the port edge in a portrait hold", (frame.bottom - Spacing.lg).value, after.bottom.value, 0.51f)
        assertEquals("centred along it", frame.centreX(), after.centreX(), 0.51f)
        assertEquals("and the strip on the window's top, the punch-hole edge", frame.top.value, strip.top.value, 0.51f)
        assertEquals("full width", frame.right.value, strip.right.value, 0.51f)
    }

    /** The control for the test above: the device turning while the window does not is not a window turn, and nothing reflows. */
    @Test
    fun `while the window does not turn, a device turn moves neither the shutter nor the strip`() {
        setDisplayRotation(Surface.ROTATION_90)
        setDialog()
        val shutterBefore = bounds(CAMERA_SHUTTER_TAG)
        val stripBefore = bounds(CAMERA_STRIP_TAG)

        session.deviceRotation = Surface.ROTATION_0
        composeRule.waitForIdle()

        assertEquals("no reflow: the shutter is where it was", shutterBefore, bounds(CAMERA_SHUTTER_TAG))
        assertEquals("nor the strip", stripBefore, bounds(CAMERA_STRIP_TAG))
    }

    /**
     * The strip on the punch-hole edge, in device anatomy: at `ROTATION_90` the port is on the
     * user's right, so the punch-hole is on the left and the strip is flush with the window's left
     * edge, full height. Robolectric reports zero insets, so "flush" here is the edge itself; on a
     * device the cut-out inset moves the strip inboard, which no test can see (CameraBands.kt).
     */
    @Test
    fun `at ROTATION_90 the strip is flush with the left edge, the punch-hole side, full height`() {
        setDisplayRotation(Surface.ROTATION_90)
        setDialog()
        val frame = bounds(IN_APP_CAMERA_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)

        assertEquals("flush with the punch-hole edge", frame.left.value, strip.left.value, 0.51f)
        assertEquals("full height", frame.top.value, strip.top.value, 0.51f)
        assertEquals(frame.bottom.value, strip.bottom.value, 0.51f)
        assertEquals("one control row deep", STRIP_ROW_HEIGHT.value, strip.width.value, 0.51f)
        assertTrue("on the opposite side from the shutter: ${strip.right} vs ${shutter.left}", strip.right < shutter.left)
        val chip = bounds(CAMERA_FLASH_CHIP_TAG)
        assertTrue("the flash chip is in the strip: $chip in $strip", chip.left >= strip.left && chip.right <= strip.right && chip.top >= strip.top && chip.bottom <= strip.bottom)
    }

    /**
     * All five chips fit down the landscape strip (decision B8, Closed decision A; the gear since
     * dispatch 2026-09-28-707), in both arrangements: each inside the strip, in order Flash, Timer,
     * Grid, Location, gear from its top, none overlapping the next. This class's window is 360 dp
     * tall; five chips and four gaps are 272 dp. Robolectric reports zero insets, so this is not
     * evidence about the cut-out.
     */
    private fun assertFiveChipsDownTheStrip() {
        val strip = bounds(CAMERA_STRIP_TAG)
        val tags = listOf(CAMERA_FLASH_CHIP_TAG, CAMERA_TIMER_CHIP_TAG, CAMERA_GRID_CHIP_TAG, CAMERA_LOCATION_CHIP_TAG, CAMERA_SETTINGS_CHIP_TAG)
        val chips = tags.map { bounds(it) }
        tags.zip(chips).forEach { (tag, chip) ->
            assertTrue("$tag inside the strip: $chip in $strip", chip.left >= strip.left && chip.right <= strip.right && chip.top >= strip.top && chip.bottom <= strip.bottom)
        }
        tags.zip(chips).zipWithNext().forEach { (a, b) ->
            assertTrue("${a.first} ends before ${b.first} starts: ${a.second} then ${b.second}", a.second.bottom <= b.second.top)
        }
    }

    @Test
    fun `at ROTATION_90 all five chips sit inside the strip, Flash, Timer, Grid, Location, gear, none overlapping`() {
        setDisplayRotation(Surface.ROTATION_90)
        setDialog()
        assertFiveChipsDownTheStrip()
    }

    @Test
    fun `at ROTATION_270 all five chips sit inside the strip, Flash, Timer, Grid, Location, gear, none overlapping`() {
        setDisplayRotation(Surface.ROTATION_270)
        setDialog()
        assertFiveChipsDownTheStrip()
    }

    /**
     * RECORD -958 (the owner: "Yes", the camera the same at both rotations). Was `at ROTATION_270 the strip is flush with the right
     * edge, the punch-hole side, full height`: `assertEquals(frame.right, strip.right)` and the strip right of the shutter,
     * `strip.left > shutter.right`. Now the left edge, as at ROTATION_90. On a device the cut-out inset still moves the strip (or the
     * shutter band) inboard on whichever side the camera falls (CameraBands pads each band by the cut-out on its own edge).
     */
    @Test
    fun `at ROTATION_270 the strip is flush with the left edge, as at ROTATION_90, full height`() {
        setDisplayRotation(Surface.ROTATION_270)
        setDialog()
        val frame = bounds(IN_APP_CAMERA_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)

        assertEquals("flush with the screen's left", frame.left.value, strip.left.value, 0.51f)
        assertEquals(frame.top.value, strip.top.value, 0.51f)
        assertEquals(frame.bottom.value, strip.bottom.value, 0.51f)
        assertEquals(STRIP_ROW_HEIGHT.value, strip.width.value, 0.51f)
        assertTrue("on the opposite side from the shutter: ${strip.right} vs ${shutter.left}", strip.right < shutter.left)
    }

    /**
     * RECORD -958: the camera controls' bounds at ROTATION_90 and ROTATION_270, compared directly. The dialog is opened at one
     * rotation, closed, and opened afresh at the other in the same composition (a new key, so it reads the rotation at open as a real
     * opening does), and every control's bounds must match: the shutter, its band, the count, the strip and its five chips.
     */
    @Test
    fun `the camera's controls have the same bounds at ROTATION_90 and ROTATION_270`() {
        var opening by mutableStateOf(0)
        setDisplayRotation(Surface.ROTATION_90)
        composeRule.setContent {
            androidx.compose.runtime.key(opening) {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    onPhotoCaptured = {},
                    onDismiss = {},
                    gridMode = GridMode.Off,
                    onGridModeChanged = {},
                    autoSaveLocationToPhotos = true,
                    onAutoSaveLocationToPhotosChanged = {},
                    levelProvider = FakeLevelProvider(),
                    viewfinder = { modifier -> Box(modifier) },
                )
            }
        }
        composeRule.waitForIdle()
        val tags = listOf(
            CAMERA_SHUTTER_TAG, CAMERA_SHUTTER_BAND_TAG, CAMERA_COUNT_TAG, CAMERA_STRIP_TAG,
            CAMERA_FLASH_CHIP_TAG, CAMERA_TIMER_CHIP_TAG, CAMERA_GRID_CHIP_TAG, CAMERA_LOCATION_CHIP_TAG, CAMERA_SETTINGS_CHIP_TAG,
        )
        val at90 = tags.associateWith { bounds(it) }

        setDisplayRotation(Surface.ROTATION_270)
        opening = 1
        composeRule.waitForIdle()
        val at270 = tags.associateWith { bounds(it) }
        println("MEASURED camera controls: ROTATION_90 $at90; ROTATION_270 $at270")

        val differ = tags.filter { tag ->
            val a = at90.getValue(tag)
            val b = at270.getValue(tag)
            listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).any { kotlin.math.abs(it.value) > 0.51f }
        }
        assertTrue("the same at both rotations; differ: ${differ.map { "$it: ${at90[it]} vs ${at270[it]}" }}", differ.isEmpty())
    }

    /**
     * The flash chip turns with the device, by the one rotation rule. Asserted on the angle
     * ([RotateWithDeviceTarget]), not on the bounds: the chip is square, so its extents are the same
     * turned or not, and the extents check the placeholder label's version of this test made would
     * pass here on a chip that never turned. At `ROTATION_90` the window has carried the chip a
     * quarter clockwise, so a device reading of `ROTATION_0` turns it a quarter back.
     */
    @Test
    fun `the flash chip turns in place when the device does`() {
        setDisplayRotation(Surface.ROTATION_90)
        setDialog()
        val before = bounds(CAMERA_FLASH_CHIP_TAG)
        assertEquals("no reading yet, no turn", 0f, chipAngle(), 0.01f)

        session.deviceRotation = Surface.ROTATION_0
        composeRule.waitForIdle()

        assertEquals("a quarter turn back", -90f, chipAngle(), 0.01f)
        assertEquals("in place", before.centreX(), bounds(CAMERA_FLASH_CHIP_TAG).centreX(), 0.51f)
        assertEquals(before.centreY(), bounds(CAMERA_FLASH_CHIP_TAG).centreY(), 0.51f)
    }

    private fun chipAngle(): Float = composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).fetchSemanticsNode().config[RotateWithDeviceTarget]

}
