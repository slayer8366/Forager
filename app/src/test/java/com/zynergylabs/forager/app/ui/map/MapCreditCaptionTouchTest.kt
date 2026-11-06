package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
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
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * RECORD -937, as the owner corrected it ("curve only the upper right side since it's the only side exposed"): the map's credit
 * line rounds its top-right corner only; the other three stay square. [MapCreditCaption] is
 * composed as `SightingsMap` composes it, bottom left over a stand-in map that counts long-presses: the real map is MapLibre's
 * native view, which Robolectric cannot draw, so the caption is tested on its own here and its place over the real map is a
 * device item. Read from pixels: the top-right corner shows the map, the other three the caption's tint. Read from real touches:
 * long-presses on the caption and in its corner gaps reach the map (it takes no touch of its own, before or after).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapCreditCaptionTouchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var longPresses = 0
    private val mapColour = Color(0xFF3FA040)

    private fun setScreen() {
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(mapColour).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) }) {
                    MapCreditCaption(text = "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)", modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun longPressAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            down(at)
            advanceEventTime(700)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.04f && abs(a.green - b.green) < 0.04f && abs(a.blue - b.blue) < 0.04f

    /**
     * Dispatch 2026-10-12-08, RECORD -952 (the owner: "can the openstreetmaps tint strip have all corners rounded instead of just
     * the one corner, like a pill?"): all four corner gaps show the map. Was "only the top-right corner is rounded and shows the
     * map, the other three are square": the top-right gap the map's, and `!near(mapColour, ...)` at the bottom-right, top-left and
     * bottom-left corners (RECORD -938).
     */
    @Test
    fun `all four corners are rounded, a pill, and each corner gap shows the map`() {
        setScreen()
        val caption = composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).getUnclippedBoundsInRoot()
        val shot = composeRule.onRoot().captureToImage()
        // The screenshot for the report (dispatch 2026-10-12-06), into the uploaded test results, not the repository.
        java.io.File("build/test-results/testDebugUnitTest/rounded-map-strip-screens").apply { mkdirs() }.let { dir ->
            java.io.File(dir, "map-credit-caption.png").outputStream().use { shot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val image = shot.toPixelMap()
        fun at(x: Dp, y: Dp) = with(composeRule.density) { image[x.toPx().toInt(), y.toPx().toInt()] }
        assertTrue("top-right corner gap shows the map", near(mapColour, at(caption.right - 1.dp, caption.top + 1.dp)))
        assertTrue("bottom-right corner gap shows the map", near(mapColour, at(caption.right - 1.dp, caption.bottom - 1.dp)))
        assertTrue("top-left corner gap shows the map", near(mapColour, at(caption.left + 1.dp, caption.top + 1.dp)))
        assertTrue("bottom-left corner gap shows the map", near(mapColour, at(caption.left + 1.dp, caption.bottom - 1.dp)))
        assertTrue("positive control: the caption's middle is its tint", !near(mapColour, at((caption.left + caption.right) / 2, caption.top + 1.dp)))
    }

    @Test
    fun `long-presses on the caption and in its rounded corners' gaps reach the map`() {
        setScreen()
        val c = composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).getUnclippedBoundsInRoot()
        val points = listOf(
            c.right - 1.dp to c.top + 1.dp,
            // RECORD -952: the three other corners round too.
            c.right - 1.dp to c.bottom - 1.dp,
            c.left + 1.dp to c.top + 1.dp,
            c.left + 1.dp to c.bottom - 1.dp,
            (c.left + c.right) / 2 to (c.top + c.bottom) / 2,
            c.left + 2.dp to (c.top + c.bottom) / 2,
        )
        for ((x, y) in points) {
            val before = longPresses
            longPressAt(x, y)
            assertEquals("a long-press at (${x.value}, ${y.value}) on the credit line $c reached the map", before + 1, longPresses)
        }
    }
}
