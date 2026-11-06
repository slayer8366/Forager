package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The overlay rule, read from rendered pixels: "Every overlay element gets a black outline" (owner, 2026-09-18,
 * `CameraOverlay.kt`). Dispatch 2026-10-13-03 thins the strip's icons and their outline (RECORD -970, "C both") and keeps the
 * rule, so this holds the rule on every glyph the strip can show, whatever the outline's width.
 *
 * **What is checked.** The real camera screen over a viewfinder painted one saturated colour. Inside each chip's bounds, no
 * white fill pixel touches a scene pixel: every neighbour of a white pixel is the outline (black), the fill, or a neutral blend
 * of the two. A glyph whose outline went missing, anywhere along its edge, puts white directly against the scene and fails
 * here with the chip's name and the count of such pixels. The scene colour is saturated on purpose: a white-black blend is
 * neutral grey, so it cannot be mistaken for the scene, which a grey scene would allow.
 *
 * **What the sample has to contain for this to be able to fail**, asserted per chip before the claim: white pixels (a
 * glyph is drawn), black pixels (an outline is there to find), and scene pixels (the scene shows inside the crop, so white
 * could have touched it). A crop with no scene in it would pass with no outline at all.
 *
 * Robolectric's native graphics are not a phone's renderer; whether the thinner outline reads over a real bright scene is a
 * device item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraStripOutlinePixelsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private val scene = Color(0xFF33B24C)

    private fun setStrip(gridMode: GridMode, saveLocation: Boolean) {
        composeRule.setContent {
            InAppCameraDialog(
                session = FakeCameraCaptureSession(),
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onPhotoCaptured = {},
                onDismiss = {},
                gridMode = gridMode,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = saveLocation,
                onAutoSaveLocationToPhotosChanged = {},
                levelProvider = FakeLevelProvider(),
                viewfinder = { modifier -> Box(modifier.fillMaxSize().background(scene)) },
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun Color.isWhite() = min(red, min(green, blue)) > 0.85f
    private fun Color.isBlack() = max(red, max(green, blue)) < 0.2f
    private fun Color.isScene() = abs(red - scene.red) < 0.12f && abs(green - scene.green) < 0.12f && abs(blue - scene.blue) < 0.12f

    private fun assertOutlined(tag: String, name: String, image: PixelMap) {
        val b = composeRule.onNodeWithTag(tag).getBoundsInRoot()
        val d = composeRule.density.density
        val left = (b.left.value * d).toInt().coerceAtLeast(1)
        val top = (b.top.value * d).toInt().coerceAtLeast(1)
        val right = (b.right.value * d).toInt().coerceAtMost(image.width - 2)
        val bottom = (b.bottom.value * d).toInt().coerceAtMost(image.height - 2)
        // The grid's lines (GridOverlay) cross the strip and are white with their own black outline; they are not a glyph, so
        // their pixels, one pixel either side, are left out of the count rather than allowed to pass or fail it.
        val gridColumns = (1..2).flatMap { i ->
            composeRule.onAllNodesWithTag(gridLineTag('v', i)).fetchSemanticsNodes().map { node ->
                val r = node.boundsInRoot
                (r.left.toInt() - 1)..(r.right.toInt() + 1)
            }
        }
        val gridRows = (1..2).flatMap { i ->
            composeRule.onAllNodesWithTag(gridLineTag('h', i)).fetchSemanticsNodes().map { node ->
                val r = node.boundsInRoot
                (r.top.toInt() - 1)..(r.bottom.toInt() + 1)
            }
        }
        var white = 0
        var black = 0
        var sceneCount = 0
        var touching = 0
        for (y in top until bottom) for (x in left until right) {
            if (gridColumns.any { x in it } || gridRows.any { y in it }) continue
            val c = image[x, y]
            if (c.isBlack()) black++
            if (c.isScene()) sceneCount++
            if (!c.isWhite()) continue
            white++
            var bare = false
            for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && image[x + dx, y + dy].isScene()) bare = true
            if (bare) touching++
        }
        println("OUTLINE $name: white=$white black=$black scene=$sceneCount whiteTouchingScene=$touching in [$left,$top]-[$right,$bottom]px")
        assertTrue("$name: a glyph is drawn (white pixels: $white)", white >= 20)
        assertTrue("$name: an outline is there to find (black pixels: $black)", black >= 20)
        assertTrue("$name: the scene shows inside the crop, so white could touch it (scene pixels: $sceneCount)", sceneCount >= 20)
        assertTrue("$name: $touching white fill pixels touch the scene with no outline between", touching == 0)
    }

    private fun assertEveryChipOutlined(state: String) {
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        for ((tag, name) in listOf(
            CAMERA_FLASH_CHIP_TAG to "flash",
            CAMERA_TIMER_CHIP_TAG to "timer",
            CAMERA_GRID_CHIP_TAG to "grid",
            CAMERA_LOCATION_CHIP_TAG to "location",
            CAMERA_SETTINGS_CHIP_TAG to "gear",
        )) {
            assertOutlined(tag, "$name ($state)", image)
        }
    }

    @Test
    fun `every strip glyph at its defaults has a black outline between its fill and the scene`() {
        setStrip(GridMode.Off, saveLocation = true)
        assertEveryChipOutlined("flash off, timer off, grid off, location on")
    }

    @Test
    fun `every strip glyph in its other states has a black outline between its fill and the scene`() {
        setStrip(GridMode.GridLevel, saveLocation = false)
        composeRule.onNodeWithTag(CAMERA_TIMER_CHIP_TAG).performClick()
        composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).performClick()
        settle()
        assertEveryChipOutlined("flash auto, timer 3 s, grid and level, location off")
    }

    @Test
    fun `the remaining strip glyphs have a black outline between their fill and the scene`() {
        setStrip(GridMode.Grid, saveLocation = true)
        repeat(2) { composeRule.onNodeWithTag(CAMERA_TIMER_CHIP_TAG).performClick() }
        repeat(2) { composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).performClick() }
        settle()
        assertEveryChipOutlined("flash on, timer 10 s, grid on, location on")
        composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).performClick()
        settle()
        assertEveryChipOutlined("torch on, timer 10 s, grid on, location on")
    }
}
