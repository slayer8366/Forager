package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
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
 * The large-screen hold (RECORD -981, the owner: "The camera is to never, ever, rotate like this. This is a cardinal sin";
 * RECORD -985). With `targetSdk` 37, Android 17 ignores the camera's portrait request on displays wider than 600 dp, so the
 * window turns with the tablet; the camera's content must still be laid out in the device's natural frame and turned back,
 * so to the user it never moves.
 *
 * The window here is a 1000 x 700 dp landscape one with the display pinned to a quarter turn, and the pin asserted, which
 * is what a portrait-natural tablet held sideways reports. Robolectric does not simulate the platform ignoring the request:
 * it simply never applies one, so the turned window is set up directly. Every expected position is a sentence in the
 * device's anatomy: at `ROTATION_90` the device's natural top is the window's left, its natural bottom (the shutter's edge)
 * the window's right, and its natural left the window's bottom; `ROTATION_270` is the mirror.
 *
 * Positions are read with `getBoundsInRoot`, which carries the layer's turn, and every touch is a real touch at window
 * coordinates (CLAUDE.md: a semantic click asserts wiring, not routing), sampled across each target.
 *
 * Not shown here, and device-only: the platform actually ignoring the request on an API 37 tablet, CameraX's picture in
 * the turned viewfinder, the system's own rotation animation, and real insets (Robolectric reports zero).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1000dp-h700dp-land-mdpi")
class InAppCameraLargeScreenHoldTest {

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
    private val captured = mutableListOf<PhotoSource>()
    private val gridRequests = mutableListOf<GridMode>()
    private val locationRequests = mutableListOf<Boolean>()
    private var viewfinderSawHold: Boolean? = null

    private fun open(rotation: Int, multiWindow: Boolean = false) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        @Suppress("DEPRECATION") val reported = composeRule.activity.windowManager.defaultDisplay.rotation
        assertEquals("the harness reports the rotation this test is about", rotation, reported)
        Shadows.shadowOf(composeRule.activity).setInMultiWindowMode(multiWindow)
        composeRule.setContent {
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onPhotoCaptured = { captured += it },
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = { gridRequests += it },
                autoSaveLocationToPhotos = true,
                onAutoSaveLocationToPhotosChanged = { locationRequests += it },
                levelProvider = FakeLevelProvider(),
                viewfinder = { modifier ->
                    viewfinderSawHold = LocalNaturalOrientationHold.current
                    Box(modifier)
                },
            )
        }
        composeRule.waitForIdle()
        val frame = bounds(IN_APP_CAMERA_TAG)
        assertTrue("the window is landscape: $frame", frame.width > frame.height)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getBoundsInRoot()
    private fun DpRect.cx() = ((left + right) / 2).value
    private fun DpRect.cy() = ((top + bottom) / 2).value
    private fun angle(tag: String): Float = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[RotateWithDeviceTarget]

    private fun touchAt(x: Float, y: Float) {
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
    }

    /** Five points across [b]: its centre and four inset by a fifth from each corner. A finger is not a point (CLAUDE.md). */
    private fun across(b: DpRect): List<Pair<Float, Float>> {
        val l = b.left.value; val t = b.top.value; val w = b.width.value; val h = b.height.value
        return listOf(
            (l + w / 2) to (t + h / 2),
            (l + w * 0.2f) to (t + h * 0.2f),
            (l + w * 0.8f) to (t + h * 0.2f),
            (l + w * 0.2f) to (t + h * 0.8f),
            (l + w * 0.8f) to (t + h * 0.8f),
        )
    }

    @Test
    fun `at ROTATION_90, full screen, the layout stays the device's own, shutter on the natural bottom, strip on the natural top`() {
        open(Surface.ROTATION_90)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)

        assertEquals("shutter on the window's right, the device's natural bottom", (frame.right - Spacing.lg).value, shutter.right.value, 0.51f)
        assertEquals("centred along it", frame.cy(), shutter.cy(), 0.51f)
        assertEquals("strip on the window's left, the device's natural top", frame.left.value, strip.left.value, 0.51f)
        assertTrue("the strip runs the window's full height: $strip in $frame", strip.height.value >= frame.height.value - 1f)
        assertTrue(
            "the chips start at the device's natural left, the window's bottom: flash ${bounds(CAMERA_FLASH_CHIP_TAG)} below gear ${bounds(CAMERA_SETTINGS_CHIP_TAG)}",
            bounds(CAMERA_FLASH_CHIP_TAG).cy() > bounds(CAMERA_SETTINGS_CHIP_TAG).cy(),
        )
        assertEquals("the viewfinder is told it is held", true, viewfinderSawHold)
    }

    @Test
    fun `at ROTATION_270, full screen, the layout stays the device's own, shutter on the window's left, strip on its right`() {
        open(Surface.ROTATION_270)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        val strip = bounds(CAMERA_STRIP_TAG)

        assertEquals("shutter on the window's left, the device's natural bottom", (frame.left + Spacing.lg).value, shutter.left.value, 0.51f)
        assertEquals("centred along it", frame.cy(), shutter.cy(), 0.51f)
        assertEquals("strip on the window's right, the device's natural top", frame.right.value, strip.right.value, 0.51f)
        assertTrue(
            "the chips start at the device's natural left, the window's top: flash ${bounds(CAMERA_FLASH_CHIP_TAG)} above gear ${bounds(CAMERA_SETTINGS_CHIP_TAG)}",
            bounds(CAMERA_FLASH_CHIP_TAG).cy() < bounds(CAMERA_SETTINGS_CHIP_TAG).cy(),
        )
        assertEquals("the viewfinder is told it is held", true, viewfinderSawHold)
    }

    /**
     * The icons stay on the accelerometer (RECORD -982). Inside the hold the display term is the natural frame's, zero, so a
     * control turns by the sensor alone: held at `ROTATION_90` with the sensor reading `ROTATION_90`, the chip turns a quarter
     * clockwise inside content that is itself turned a quarter back, and reads upright. Outside the hold the same readings
     * cancel to no turn, so this fails if the hold is missing.
     */
    @Test
    fun `the icons turn by the sensor alone inside the hold`() {
        open(Surface.ROTATION_90)
        assertEquals("no reading yet, no turn", 0f, angle(CAMERA_FLASH_CHIP_TAG), 0.01f)

        session.deviceRotation = Surface.ROTATION_90
        composeRule.waitForIdle()
        for (tag in listOf(CAMERA_FLASH_CHIP_TAG, CAMERA_TIMER_CHIP_TAG, CAMERA_GRID_CHIP_TAG, CAMERA_LOCATION_CHIP_TAG, CAMERA_SETTINGS_CHIP_TAG, CAMERA_COUNT_TAG)) {
            assertEquals("$tag: a quarter turn clockwise", 90f, angle(tag), 0.01f)
        }

        session.deviceRotation = Surface.ROTATION_270
        composeRule.waitForIdle()
        assertEquals("the other way: a quarter turn back", -90f, angle(CAMERA_FLASH_CHIP_TAG), 0.01f)
    }

    @Test
    fun `real touches across the shutter, through the turned layer, each take a photo`() {
        open(Surface.ROTATION_90)
        val points = across(bounds(CAMERA_SHUTTER_TAG))
        points.forEachIndexed { i, (x, y) ->
            touchAt(x, y)
            composeRule.waitUntil(timeoutMillis = 5_000) { captured.size == i + 1 }
        }
        assertEquals("one photo per touch", points.size, session.captureCalls)
    }

    @Test
    fun `real touches across each chip, through the turned layer, reach that chip`() {
        open(Surface.ROTATION_270)

        for ((x, y) in across(bounds(CAMERA_FLASH_CHIP_TAG))) touchAt(x, y)
        assertEquals("five touches on the flash chip, five requests", 5, session.setFlashModeCalls)

        for ((x, y) in across(bounds(CAMERA_GRID_CHIP_TAG))) touchAt(x, y)
        assertEquals("five touches on the grid chip, five requests", 5, gridRequests.size)

        for ((x, y) in across(bounds(CAMERA_LOCATION_CHIP_TAG))) touchAt(x, y)
        assertEquals("five touches on the location chip, five requests", 5, locationRequests.size)

        val timerLabels = across(bounds(CAMERA_TIMER_CHIP_TAG)).map { (x, y) ->
            touchAt(x, y)
            composeRule.onNodeWithTag(CAMERA_TIMER_CHIP_TAG).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        }
        assertEquals("each touch moved the timer on", listOf(TIMER_3_LABEL, TIMER_10_LABEL, TIMER_OFF_LABEL, TIMER_3_LABEL, TIMER_10_LABEL), timerLabels)

        for ((x, y) in across(bounds(CAMERA_SETTINGS_CHIP_TAG))) {
            touchAt(x, y)
            assertEquals("the gear opened the panel", 1, composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).fetchSemanticsNodes().size)
            composeRule.pressBackOnCamera()
        }
        assertEquals("nothing captured by touching chips", 0, session.captureCalls)
    }

    /** RECORD -985: in split screen or a freeform window the content lays out to the window's own shape, as before the hold. */
    @Test
    fun `in a multi-window window there is no hold, the landscape arrangement, and the turn cancels`() {
        open(Surface.ROTATION_270, multiWindow = true)
        val frame = bounds(IN_APP_CAMERA_TAG)
        assertEquals("the landscape arrangement: shutter on the window's right (RECORD -958)", (frame.right - Spacing.lg).value, bounds(CAMERA_SHUTTER_TAG).right.value, 0.51f)
        assertEquals("strip down the window's left", frame.left.value, bounds(CAMERA_STRIP_TAG).left.value, 0.51f)
        assertEquals("the viewfinder is not held", false, viewfinderSawHold)

        session.deviceRotation = Surface.ROTATION_270
        composeRule.waitForIdle()
        assertEquals("sensor and window agree: no turn", 0f, angle(CAMERA_FLASH_CHIP_TAG), 0.01f)
    }

    /** At ROTATION_0 the hold does nothing: its content is placed exactly where it would be without it, given the window's values. */
    @Test
    fun `at ROTATION_0 the hold is a no-op`() {
        var seen: Pair<Boolean, Int>? = null
        var hold: Boolean? = null
        composeRule.setContent {
            HoldNaturalOrientation(windowIsLandscape = true, displayRotation = Surface.ROTATION_0, fullScreen = true) { landscape, rotation ->
                seen = landscape to rotation
                hold = LocalNaturalOrientationHold.current
                Box(Modifier.offset(10.dp, 20.dp).size(30.dp, 40.dp).testTag(PROBE_TAG))
            }
        }
        composeRule.waitForIdle()
        assertEquals("the content gets the window's own values", true to Surface.ROTATION_0, seen)
        assertEquals("and is not told it is held", false, hold)
        val probe = bounds(PROBE_TAG)
        assertEquals(10f, probe.left.value, 0.01f)
        assertEquals(20f, probe.top.value, 0.01f)
        assertEquals(40f, probe.right.value, 0.01f)
        assertEquals(60f, probe.bottom.value, 0.01f)
    }
}

private const val PROBE_TAG = "hold-probe"
