package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAME_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-30-08, C16 with C19 (RECORD -1946, -1953), and C17 (-1946), in portrait and on the tablet.
 *
 * C16, the owner: "One width, rows wrap (Recommended)". C19: "Fits a long route, per text size (Recommended)", whose option read
 * "Wide enough for '10 Waypoints routed' and a two-digit mile total at the user's text size (about 246 dp at normal size), never wider
 * than the room beside the bar. It doesn't change while plotting, only if the user changes text size, and normal routes never wrap."
 * So the card has one width from the first tap: the hint, a leg in flight, every routed state, peek, expanded and the handle's slide
 * between them, and a saved route.
 *
 * C17, the owner: "Flat on the side it comes from only"; "Flush to the edge (Recommended)". The card sits flush against the edge away
 * from the bar, with its two corners on that side square and the two facing the map round.
 *
 * Under native graphics, because the width is measured from real text. [expectedWidth] is the reference card's width at this font
 * scale, `null` where it is the room beside the bar instead (C19: "never wider than the room beside the bar").
 */
abstract class RouteCardOneWidthTests(private val expectedWidth: Float?) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun width(r: DpRect): Float = (r.right - r.left).value

    /** The width C19 gives: the reference card's, or, where that is wider, the room between the far edge and the bar's gap. */
    private fun ruledWidth(bar: DpRect, barOnLeft: Boolean): Float {
        val room = if (barOnLeft) (rig.screen().right - bar.right - 8.dp).value else (bar.left - 8.dp).value
        return expectedWidth?.let { minOf(it, room) } ?: room
    }

    /** One width, flush to the edge away from the bar, and the bar's gap kept on its side. */
    private fun assertPlaced(where: String, bar: DpRect, barOnLeft: Boolean) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("C16 font ${composeRule.density.fontScale} $where: card ${card.left.value}..${card.right.value}, width ${width(card)}, bar ${bar.left.value}..${bar.right.value}")
        assertEquals("$where: the card's one width", ruledWidth(bar, barOnLeft), width(card), 0.5f)
        if (barOnLeft) {
            assertEquals("$where: flush to the right edge", rig.screen().right.value, card.right.value, 0.5f)
            assertTrue("$where: the bar's gap kept: card ${card.left}, bar ${bar.right}", card.left >= bar.right + 8.dp - 0.5.dp)
        } else {
            assertEquals("$where: flush to the left edge", 0f, card.left.value, 0.5f)
            assertTrue("$where: the bar's gap kept: card ${card.right}, bar ${bar.left}", card.right <= bar.left - 8.dp + 0.5.dp)
        }
    }

    private fun walk(barOnLeft: Boolean) {
        rig.setScreen()
        if (barOnLeft) rig.dragBarAcross(rightward = false)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        assertEquals("precondition: the bar's side", barOnLeft, bar.right < rig.screen().right / 2)
        rig.plotTo(1)
        assertPlaced("the hint", bar, barOnLeft)
        rig.holdLegs()
        rig.tapNext()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        assertPlaced("the first leg in flight", bar, barOnLeft)
        rig.landLeg()
        rig.settle()
        assertPlaced("the first leg landed", bar, barOnLeft)
        listOf(3, 6).forEach { n ->
            rig.plotTo(n)
            listOf(false, true).forEach { expanded ->
                rig.setExpanded(expanded)
                assertPlaced("$n stops, ${if (expanded) "expanded" else "peek"}", bar, barOnLeft)
            }
        }
        rig.setExpanded(false)
        rig.assertBarUntouched("the card at 6 stops", bar)
    }

    @Test
    fun `the bar on the right - one width from the first tap, flush to the left edge`() = walk(barOnLeft = false)

    @Test
    fun `the bar on the left - one width from the first tap, flush to the right edge`() = walk(barOnLeft = true)

    @Test
    fun `the width holds all through the handle's slide`() {
        rig.setScreen()
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        rig.plotTo(3)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(center) }
        repeat(8) { frame ->
            composeRule.mainClock.advanceTimeBy(50)
            assertPlaced("sliding up, frame $frame", bar, barOnLeft = false)
        }
        composeRule.mainClock.autoAdvance = true
        rig.settle()
        assertPlaced("expanded", bar, barOnLeft = false)
    }

    @Test
    fun `a saved route has the same width, and a long name wraps whole with Directions on the card`() {
        val name = "The long way round past the old fire lookout and down the creek"
        val trip = rig.savedTrip(name, 3)
        rig.setScreen(trips = listOf(trip))
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        rig.open(trip)
        assertPlaced("a saved route", bar, barOnLeft = false)
        rig.assertWhole("a saved route", ROUTE_CARD_NAME_TAG, name)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val directions = rig.bounds(ROUTE_CARD_DIRECTIONS_TAG)
        assertTrue("Directions $directions is on the card $card", directions.left >= card.left && directions.right <= card.right)
    }

    /** Whether the card's own pixel at ([x], [y]) is its fill, against a pixel well inside it on the same side. */
    private fun Bitmap.sameAsInside(x: Int, y: Int, insideX: Int): Boolean {
        val a = getPixel(x, y)
        val b = getPixel(insideX, height / 2)
        return listOf(16, 8, 0).all { shift -> kotlin.math.abs(((a shr shift) and 0xff) - ((b shr shift) and 0xff)) <= 6 }
    }

    private fun corners(barOnLeft: Boolean) {
        rig.setScreen()
        if (barOnLeft) rig.dragBarAcross(rightward = false)
        rig.plotTo(3)
        val image = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val w = image.width
        val h = image.height
        val left = listOf(1 to 1, 1 to h - 2)
        val right = listOf(w - 2 to 1, w - 2 to h - 2)
        val (square, round) = if (barOnLeft) right to left else left to right
        square.forEach { (x, y) ->
            assertTrue("bar on left $barOnLeft: the corner at ($x, $y) on the edge side is square, the card's fill", image.sameAsInside(x, y, insideX = x))
        }
        round.forEach { (x, y) ->
            assertTrue("bar on left $barOnLeft: the corner at ($x, $y) facing the map is round, not the card's fill", !image.sameAsInside(x, y, insideX = x))
        }
    }

    @Test
    fun `the bar on the right - square on the left edge, round facing the map`() = corners(barOnLeft = false)

    @Test
    fun `the bar on the left - square on the right edge, round facing the map`() = corners(barOnLeft = true)
}

/** The S22's portrait window at normal text size: the reference card, C19's "about 246 dp" ([REFERENCE_CARD_DP]). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.0f)
class RouteCardOneWidthTest : RouteCardOneWidthTests(expectedWidth = REFERENCE_CARD_DP)

/** At text size 2.0 the reference card needs about 410 dp, more than the room beside the bar: the card takes the room. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class RouteCardOneWidthFontTwoTest : RouteCardOneWidthTests(expectedWidth = null)

/**
 * The tablet: the reference card well inside the room, so it does not stretch to the bar. 248.5 dp at 2 px per dp, against 247.67 at
 * the phone's 3: the text's widths round to whole pixels.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-xhdpi", fontScale = 1.0f)
class RouteCardOneWidthTabletTest : RouteCardOneWidthTests(expectedWidth = 248.5f)
