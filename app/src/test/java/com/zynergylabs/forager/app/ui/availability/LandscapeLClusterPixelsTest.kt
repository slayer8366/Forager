package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.Cream
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay
import kotlin.math.abs

/**
 * What the landscape L actually draws (dispatch `2026-09-28-160`), read from the rendered pixels rather than from the declared
 * constants: "Nothing is drawn around the L" (the empty corner and the gap show the map, pixel for pixel) and the bar and the pill
 * each render at the standing 0.8 chrome alpha as a single layer, so nothing composes darker. Native graphics, as
 * `captureToImage` needs. The stand-in map draws nothing, so the screen's own background is what the map reads as; the reference
 * pixel is taken far from any chrome.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeLClusterPixelsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayoutFixesMapSlot()
    private var dark = false

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            dark = LocalForagerDarkTheme.current
            LayoutFixesScreen(uiState = LAYOUT_FIXES_FIX_STATE, mapSlot = map.slot, isRecording = true)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun described(description: String): DpRect = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    private fun rgb(c: Color) = "(%.3f, %.3f, %.3f)".format(c.red, c.green, c.blue)

    private fun assertSameColour(message: String, expected: Color, actual: Color, tolerance: Float = 2f / 255f) {
        assertTrue(
            "$message: expected ${rgb(expected)}, read ${rgb(actual)}",
            abs(expected.red - actual.red) <= tolerance && abs(expected.green - actual.green) <= tolerance && abs(expected.blue - actual.blue) <= tolerance,
        )
    }

    private fun check(rotation: Int, clusterOnLeft: Boolean) {
        setScreen(rotation)
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val density = composeRule.density
        fun at(x: Dp, y: Dp): Color = with(density) { image[x.toPx().toInt(), y.toPx().toInt()] }

        val fullscreen = described("Fullscreen")
        val add = described("Plan a trip or log a find here")
        val bar = DpRect(fullscreen.left, fullscreen.top, fullscreen.right, add.bottom)
        val pill = tag("control-pill")
        val m = tag(LAYOUT_FIXES_MAP_TAG)

        // Far from every control: the middle of the map area, and the same height as the L's middle.
        val reference = at((m.left + m.right) / 2, (m.top + m.bottom) / 2)
        val chrome = if (dark) Bark else Cream
        val over = MAP_CHROME_OVER_MAP_ALPHA
        fun composite(background: Color) = Color(
            red = chrome.red * over + background.red * (1f - over),
            green = chrome.green * over + background.green * (1f - over),
            blue = chrome.blue * over + background.blue * (1f - over),
        )

        // Nothing drawn: the empty corner inboard of the bar, and the gap, read as the map does.
        val midY = (bar.top + bar.bottom) / 2
        val cornerX = if (clusterOnLeft) bar.right + 24.dp else bar.left - 24.dp
        assertSameColour("the empty corner inboard of the bar shows the map", reference, at(cornerX, midY))
        val gapY = (bar.bottom + pill.top) / 2
        assertSameColour("the gap under the bar shows the map", reference, at((bar.left + bar.right) / 2, gapY))
        val extensionX = if (clusterOnLeft) pill.right - 12.dp else pill.left + 12.dp
        assertSameColour("the gap above the pill's extension shows the map", reference, at(extensionX, gapY))

        // One layer at 0.8: a point on the bar clear of its icon (icons span the middle 24 of 48), and the same on the pill.
        // Off the minimise handle, which straddles the bar's outer edge at its mid-height: the top row's centre height, 6 dp in.
        assertSameColour("the bar reads as one layer at 0.8 over the map", composite(reference), at(bar.left + 6.dp, bar.top + 24.dp))
        val pillEdgeX = if (clusterOnLeft) pill.left + 5.dp else pill.right - 5.dp
        assertSameColour("the pill reads as one layer at 0.8 over the map", composite(reference), at(pillEdgeX, (pill.top + pill.bottom) / 2))
        // And the inboard end of the pill, past where the bar is: the extension is also one layer, not a layer over a container fill.
        val extensionEndX = if (clusterOnLeft) pill.right - 5.dp else pill.left + 5.dp
        assertSameColour("the pill's inboard end reads as one layer at 0.8 over the map", composite(reference), at(extensionEndX, (pill.top + pill.bottom) / 2))
        assertEquals("sanity: the reference is not the chrome colour itself", true, abs(reference.red - composite(reference).red) > 2f / 255f || abs(reference.green - composite(reference).green) > 2f / 255f || abs(reference.blue - composite(reference).blue) > 2f / 255f)
    }

    @Test fun `L5 at ROTATION_90 the corner and the gap show the map, and the bar and the pill are one layer at 0_8`() = check(Surface.ROTATION_90, clusterOnLeft = true)

    @Test fun `L5 at ROTATION_270 the corner and the gap show the map, and the bar and the pill are one layer at 0_8`() = check(Surface.ROTATION_270, clusterOnLeft = false)
}
