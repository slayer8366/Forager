package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer10
import androidx.compose.material.icons.outlined.Timer3
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import java.io.File
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
 * Dispatch 2026-10-13-03, item 6 (RECORD -970, the owner on the strip icons: "C both", thin-line glyphs and a thinner
 * outline): Robolectric screenshots of the camera strip over a near-white and a dark scene, and a sheet of outline widths to
 * choose from. **Not a guard**: each test writes PNGs under the build's test-results directory, which CI uploads with the
 * JUnit XML, and the coder copies them to the owner's evidence folder outside any repository. The one assertion is that each
 * file was written and is not empty. The guard on the outline itself is [CameraStripOutlinePixelsTest].
 *
 * The strip shots are the real camera screen, cropped to the strip, at native size and four times enlarged with no
 * smoothing so the outline's pixels can be counted. Run once before the change and once after; the file names are the same,
 * and the before and after sets are told apart by the commit the CI run built.
 *
 * The width sheet draws each strip symbol in the filled style at today's outline offset, then the outlined style at four
 * offsets, by the same eight-copy method `OverlayIcon` uses, written out here so the sheet does not depend on the code it
 * is choosing for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraStripIconsScreenshotTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private val dir = File("build/test-results/testDebugUnitTest/camera-strip-icons").apply { mkdirs() }

    private fun write(name: String, bitmap: Bitmap) {
        for ((suffix, image) in listOf("" to bitmap, "-x4" to Bitmap.createScaledBitmap(bitmap, bitmap.width * 4, bitmap.height * 4, false))) {
            val file = File(dir, "$name$suffix.png")
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("SCREENSHOT|${file.absolutePath}|${image.width}x${image.height}")
            assertTrue("the screenshot $name$suffix was written", file.length() > 0)
        }
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun shootStrip(name: String, scene: Color, gridMode: GridMode, saveLocation: Boolean, timerTaps: Int, flashTaps: Int) {
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
        repeat(timerTaps) { composeRule.onNodeWithTag(CAMERA_TIMER_CHIP_TAG).performClick() }
        repeat(flashTaps) { composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).performClick() }
        settle()
        val root = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val b = composeRule.onNodeWithTag(CAMERA_STRIP_TAG).getBoundsInRoot()
        val d = composeRule.density.density
        val left = (b.left.value * d).toInt().coerceIn(0, root.width - 1)
        val top = (b.top.value * d).toInt().coerceIn(0, root.height - 1)
        val width = ((b.right - b.left).value * d).toInt().coerceAtMost(root.width - left)
        val height = ((b.bottom - b.top).value * d).toInt().coerceAtMost(root.height - top)
        write(name, Bitmap.createBitmap(root, left, top, width, height))
    }

    private val nearWhite = Color(0xFFF2F1EC)
    private val dark = Color(0xFF1A1C1A)

    @Test
    fun `strip, defaults, over a near-white scene`() = shootStrip("strip-defaults-near-white", nearWhite, GridMode.Off, true, 0, 0)

    @Test
    fun `strip, defaults, over a dark scene`() = shootStrip("strip-defaults-dark", dark, GridMode.Off, true, 0, 0)

    @Test
    fun `strip, timer 3, grid and level, location off, flash auto, over a near-white scene`() =
        shootStrip("strip-timer3-gridlevel-locationoff-near-white", nearWhite, GridMode.GridLevel, false, 1, 1)

    @Test
    fun `strip, timer 3, grid and level, location off, flash auto, over a dark scene`() =
        shootStrip("strip-timer3-gridlevel-locationoff-dark", dark, GridMode.GridLevel, false, 1, 1)

    @Test
    fun `strip, timer 10, grid on, flash on, over a near-white scene`() =
        shootStrip("strip-timer10-gridon-flashon-near-white", nearWhite, GridMode.Grid, true, 2, 2)

    @Test
    fun `strip, timer 10, grid on, flash on, over a dark scene`() =
        shootStrip("strip-timer10-gridon-flashon-dark", dark, GridMode.Grid, true, 2, 2)

    private val symbols: List<Pair<ImageVector, ImageVector>> = listOf(
        Icons.Filled.FlashOff to Icons.Outlined.FlashOff,
        Icons.Filled.FlashAuto to Icons.Outlined.FlashAuto,
        Icons.Filled.FlashOn to Icons.Outlined.FlashOn,
        Icons.Filled.FlashlightOn to Icons.Outlined.FlashlightOn,
        Icons.Filled.TimerOff to Icons.Outlined.TimerOff,
        Icons.Filled.Timer3 to Icons.Outlined.Timer3,
        Icons.Filled.Timer10 to Icons.Outlined.Timer10,
        Icons.Filled.GridOff to Icons.Outlined.GridOff,
        Icons.Filled.GridOn to Icons.Outlined.GridOn,
        Icons.Filled.Straighten to Icons.Outlined.Straighten,
        Icons.Filled.LocationOn to Icons.Outlined.LocationOn,
        Icons.Filled.LocationOff to Icons.Outlined.LocationOff,
        Icons.Filled.Settings to Icons.Outlined.Settings,
    )

    /** Offsets for the eight outline copies; 1.5 dp is today's (`OVERLAY_OUTLINE_WIDTH / 2`). */
    private val offsets: List<Dp> = listOf(1.5.dp, 1.0.dp, 0.75.dp, 0.5.dp)

    @androidx.compose.runtime.Composable
    private fun OutlinedCell(icon: ImageVector, w: Dp) {
        Box(Modifier.size(40.dp).padding(8.dp)) {
            for ((dx, dy) in listOf(-w to -w, 0.dp to -w, w to -w, -w to 0.dp, w to 0.dp, -w to w, 0.dp to w, w to w)) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.offset(dx, dy))
            }
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }

    private fun shootSheet(name: String, scene: Color) {
        composeRule.setContent {
            // Columns: filled at 1.5 dp (today), then outlined at 1.5, 1.0, 0.75 and 0.5 dp. One row per strip symbol.
            Column(Modifier.testTag(SHEET_TAG).background(scene).padding(8.dp)) {
                for ((filled, outlined) in symbols) {
                    Row {
                        OutlinedCell(filled, 1.5.dp)
                        Box(Modifier.size(16.dp, 40.dp))
                        for (w in offsets) OutlinedCell(outlined, w)
                    }
                }
            }
        }
        settle()
        write(name, composeRule.onNodeWithTag(SHEET_TAG).captureToImage().asAndroidBitmap())
    }

    @Test
    fun `outline width sheet over a near-white scene`() = shootSheet("width-sheet-near-white", nearWhite)

    @Test
    fun `outline width sheet over a dark scene`() = shootSheet("width-sheet-dark", dark)
}

private const val SHEET_TAG = "camera-strip-icons-width-sheet"
