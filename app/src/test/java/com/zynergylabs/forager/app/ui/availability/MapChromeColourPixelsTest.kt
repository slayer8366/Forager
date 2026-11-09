package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLowestLight
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerHighestDark
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.domain.model.LatLng
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_TAG
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C1 (dispatch 2026-09-28-210): what the phone's portrait Maps tab actually **draws**, read from rendered pixels, not from the
 * container colours the surfaces declare (`MapChromeColourTest` reads those through semantics, which repeat the colour the
 * surface is given; only pixels show that what is drawn is what is declared). The owner: "Have them be the same color as the
 * bottom app navigation bar." So the search bar, the compass strip and the icon bar are each compared with the navigation bar
 * in the same frame, and each with the navigation bar's colour at the standing 0.8 over the screen's own background (the
 * stand-in map draws nothing, as in `LandscapeLClusterPixelsTest`). Native graphics, as `captureToImage` needs.
 */
abstract class MapChromeColourPixelsTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun rgb(c: Color) = "(%.3f, %.3f, %.3f)".format(c.red, c.green, c.blue)

    private fun assertSame(message: String, expected: Color, actual: Color, tolerance: Float = 2f / 255f) {
        assertTrue(
            "$message: expected ${rgb(expected)}, read ${rgb(actual)}",
            abs(expected.red - actual.red) <= tolerance && abs(expected.green - actual.green) <= tolerance && abs(expected.blue - actual.blue) <= tolerance,
        )
    }

    /**
     * The icon bar has a 2 dp shadow (`shadowElevation`, unchanged by this dispatch) that shows through its translucent fill and
     * darkens what is under it slightly: in light, about 3% (read 0.933, 0.922, 0.875 against 0.961, 0.945, 0.898 declared), and about 7% in dark (0.114 against 0.122),
     * So the bar is held to the same colour **darkened by a neutral factor of at most 10%**: each channel is the
     * expected one times one common factor, not a different hue. A Cream or Bark bar fails this, having a different hue.
     */
    private fun assertSameColourShadowed(message: String, expected: Color, actual: Color) {
        val k = actual.red / expected.red
        val ok = k in 0.90f..1.003f &&
            abs(actual.green - expected.green * k) <= 2f / 255f && abs(actual.blue - expected.blue * k) <= 2f / 255f
        assertTrue("$message: expected ${rgb(expected)} darkened by a factor of 0.90 to 1, read ${rgb(actual)} (factor %.3f)".format(k), ok)
    }

    /**
     * Dispatch 2026-10-12-08 (RECORD -945, -951): the pill is a shade lighter than the strip, card, icon bar and navigation bar,
     * which stay one colour. Was "the search bar, the compass strip, the icon bar and the navigation bar are drawn as one colour",
     * the pill and its lower band held to the token at 0.8 (`assertSameColourShadowed("the search pill is the token at 0.8",
     * expected, ...)`).
     */
    @Test
    fun `the search pill is a shade lighter, and the compass strip, the icon bar and the navigation bar are drawn as one colour`() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles, darkTheme = dark) }
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val density = composeRule.density
        fun at(x: Dp, y: Dp): Color = with(density) { image[x.toPx().toInt(), y.toPx().toInt()] }
        fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val token = if (dark) SurfaceContainerDark else SurfaceContainerLight
        val over = MAP_CHROME_OVER_MAP_ALPHA
        val reference = at((root.left + root.right) / 2, (root.top + root.bottom) / 2)
        fun composite(chrome: Color, alpha: Float) = Color(
            red = chrome.red * alpha + reference.red * (1f - alpha),
            green = chrome.green * alpha + reference.green * (1f - alpha),
            blue = chrome.blue * alpha + reference.blue * (1f - alpha),
        )
        val expected = composite(token, over)
        // RECORD -951: the pill's role, the surface ramp's lightest end in each theme.
        val pillExpected = composite(if (dark) SurfaceContainerHighestDark else SurfaceContainerLowestLight, over)

        val nav = bounds(COMPACT_BOTTOM_NAV_TAG)
        val navPixel = at(nav.left + 4.dp, (nav.top + nav.bottom) / 2)
        assertSame("the navigation bar over the map is the token at 0.8", expected, navPixel)

        // Dispatch 2026-10-12-06: the search bar is the rounded pill and the strip the card tucked under it, each with the 2 dp
        // shadow the icon bar has, which shows through the 0.8 fill (see assertSameColourShadowed): held to the token darkened by a
        // neutral factor, as the icon bar is. Was assertSame, against the square, shadowless bar and strip.
        val search = bounds(SEARCH_ENTRY_BAR_TAG)
        val searchMid = (search.top + search.bottom) / 2
        val pillPixel = at(search.left + 4.dp, searchMid)
        assertSameColourShadowed("the search pill is its lighter role at 0.8", pillExpected, pillPixel)

        val strip = bounds("compass-elevation-strip")
        // 4 dp in, mid-height, clear of the strip's text and of its rounded lower corners (16 dp from its bottom).
        val stripPixel = at(strip.left + 4.dp, (strip.top + strip.bottom) / 2)
        assertSameColourShadowed("the compass strip is the token at 0.8", expected, stripPixel)
        // RECORD -945: the tuck reads by day and at night, the pill measurably lighter than the strip under it.
        val lum = { c: Color -> 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue }
        assertTrue(
            "the pill ${rgb(pillPixel)} is lighter than the strip ${rgb(stripPixel)} by at least 2%",
            lum(pillPixel) >= lum(stripPixel) * 1.02f,
        )

        // The tuck (RoundedMapCard.kt): the card's fill reaches up behind the pill's rounded lower corners, less the pill's own
        // shape, so nowhere is the 0.8 laid twice. The pill's lower band, where the card's tuck lies beneath it, reads as the
        // pill does a line higher; a fill laid under it as well would read 0.96 there.
        val band = at(search.left + 24.dp, search.bottom - 3.dp)
        assertSameColourShadowed("the pill's lower band, over the card's tuck, is the pill's colour at 0.8, not laid twice", pillExpected, band)
        // The wedge under the pill's lower-left corner is the card's fill, not the bare map: its hue is the token's (it now carries
        // the pill's cast shadow, below, so only the hue is held, not the level). Was: the token at 0.8, shadow factor 0.90 to 1.
        val wedge = at(search.left + 2.dp, search.bottom - 2.dp)
        val k = wedge.red / expected.red
        assertTrue(
            "the wedge under the pill's corner is the card's fill, the token's hue: read ${rgb(wedge)} against ${rgb(expected)}",
            k in 0.6f..1.003f && abs(wedge.green - expected.green * k) <= 3f / 255f && abs(wedge.blue - expected.blue * k) <= 3f / 255f,
        )

        // RECORD -943's follow-up (the owner: "don't forget the card tuck. I don't see it on the preview"): the pill's shadow is cast
        // onto the card, so the card just below the pill's lower edge is measurably darker than the card further down. Just below
        // the pill's flat lower edge, at the strip's middle (1 dp down, above its text), against 4 dp in at mid-height (clear of
        // the text and of the rounded corners). Not 4 dp in at the top: there the pill's rounded corner lifts its edge 5 dp.
        val justBelow = at((strip.left + strip.right) / 2, strip.top + 1.dp)
        val further = at(strip.left + 4.dp, (strip.top + strip.bottom) / 2)
        val luminance = { c: Color -> 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue }
        assertTrue(
            "the card just below the pill ${rgb(justBelow)} is darker than further down ${rgb(further)} by at least 4%",
            luminance(justBelow) <= luminance(further) * 0.96f,
        )

        // The icon bar is a 0.5 layer on the cluster's 0.6 container: 0.8 together, on a point clear of an icon (as the L test).
        val bar = bounds(MAP_ICON_BAR_TAG)
        assertSameColourShadowed("the icon bar reads as the token at 0.8 (0.5 over 0.6)", expected, at(bar.left + 6.dp, bar.top + 24.dp))

        assertTrue(
            "sanity: the reference (the map's own background) is not the chrome colour itself",
            abs(reference.red - expected.red) > 2f / 255f || abs(reference.green - expected.green) > 2f / 255f || abs(reference.blue - expected.blue) > 2f / 255f,
        )
    }
    /** [color] over the screen's own background at [alpha]. */
    private fun over(color: Color, alpha: Float, reference: Color) = Color(
        red = color.red * alpha + reference.red * (1f - alpha),
        green = color.green * alpha + reference.green * (1f - alpha),
        blue = color.blue * alpha + reference.blue * (1f - alpha),
    )

    @Test
    fun `the search panel is drawn as the token at 0_8`() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles, darkTheme = dark) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val density = composeRule.density
        fun at(x: Dp, y: Dp): Color = with(density) { image[x.toPx().toInt(), y.toPx().toInt()] }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val token = if (dark) SurfaceContainerDark else SurfaceContainerLight
        val panel = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot()
        // The panel covers the middle of the screen, so the reference is the bare map at the foot's edge, above the bottom bar.
        // Changed by RECORD -713 (the planner): the point was a fixed 200 dp above the bottom, which the panel came to cover
        // when it moved down to follow the compass strip's real height (RECORD -709), so "bare map" read the panel itself. It
        // is now 8 dp below the panel's measured bottom, still bare map and above the bottom bar; the claim is unchanged.
        val reference = at(root.left + 4.dp, panel.bottom + 8.dp)
        val navTop = composeRule.onNodeWithTag(COMPACT_BOTTOM_NAV_TAG).getUnclippedBoundsInRoot().top
        check(panel.bottom + 8.dp < navTop) { "the reference point ${panel.bottom + 8.dp} must be above the bottom bar at $navTop" }
        assertSame("the search panel is the token at 0.8", over(token, MAP_CHROME_OVER_MAP_ALPHA, reference), at(panel.left + 3.dp, panel.top + 3.dp))
    }

    @Test
    fun `a map bubble is drawn as the token at 0_8`() {
        val bubbleMap = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, 60.dp, 380.dp, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng))))
        composeRule.setContent { MapChromeTestScreen(state, bubbleMap, roles, darkTheme = dark) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(glyphTag(BUBBLE_WAYPOINT.id), useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val density = composeRule.density
        fun at(x: Dp, y: Dp): Color = with(density) { image[x.toPx().toInt(), y.toPx().toInt()] }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val token = if (dark) SurfaceContainerDark else SurfaceContainerLight
        val card = composeRule.onNodeWithTag(MAP_BUBBLE_TAG).getUnclippedBoundsInRoot()
        val reference = at(root.left + 4.dp, root.bottom - 200.dp)
        // Inside the card's left edge at mid-height, clear of its text; the card has a 6 dp shadow that darkens a little.
        assertSameColourShadowed("the bubble is the token at 0.8", over(token, MAP_CHROME_OVER_MAP_ALPHA, reference), at(card.left + 4.dp, (card.top + card.bottom) / 2))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeColourPixelsDarkTest : MapChromeColourPixelsTests(dark = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeColourPixelsLightTest : MapChromeColourPixelsTests(dark = false)
