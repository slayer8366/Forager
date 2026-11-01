package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAME_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
import com.zynergylabs.forager.app.ui.route.routeCardLegDistanceTag
import com.zynergylabs.forager.app.ui.route.routeCardLegNameTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The names a real walk can hold: no length limit is applied to a waypoint's name. */
private val LONG_NAMES = listOf(
    "Upper Falls of the North Fork Trailhead Overlook",
    "Old Logging Road Junction Below the Second Ridge",
    "South Meadow Campsite Beside the Beaver Pond",
)

/** Short stops, then one long enough to widen the card, then short again. */
private val WIDENING_NAMES = listOf("Creek", "Ridge", "Upper Falls Overlook Trailhead", "Pond", "Bluff", "Spring")

private const val CARD_SIDE_PADDING_DP = 12f

/** The card's width is measured against its real text: every class here runs under native graphics (Amendment 16). */
private fun width(card: DpRect): Dp = card.right - card.left

/**
 * Dispatch 2026-10-30-08, Amendment 16 (RECORD -1932), the owner: "The card is supposed to slide from the opposite side, only give
 * enough room to display the info". The real Maps tab at the S22's size.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardContentWidthTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val shortRig = RouteCardBarRig(composeRule)

    /** The width the card's own lines take: the widest of them, read from what is drawn, plus the card's padding and the mark's room. */
    private fun assertTakesOnlyWhatItNeeds(rig: RouteCardBarRig, where: String, legs: Int) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val buttons = rig.bounds(ROUTE_CARD_SAVE_TAG).right - rig.bounds(ROUTE_CARD_UNDO_TAG).left
        val total = rig.ink(ROUTE_CARD_ROUTED_COUNT_TAG) + 8.dp + rig.ink(ROUTE_CARD_ROUTED_TOTAL_TAG) + 24.dp
        val rows = (0 until legs).maxOf { rig.ink(routeCardLegNameTag(it)) + 8.dp + rig.ink(routeCardLegDistanceTag(it)) }
        println("A16 $where: card ${width(card).value}, buttons ${buttons.value}, total line ${total.value}, widest row ${rows.value}")
        val needed = maxOf(maxOf(buttons, total), rows) + (CARD_SIDE_PADDING_DP * 2).dp
        assertTrue("$where: the card ${width(card)} is no wider than its widest line needs, $needed", width(card) <= needed + 4.dp)
        assertTrue("$where: the card ${width(card)} fits its widest line, $needed", width(card) >= needed - 1.dp)
    }

    private fun walk(barOnLeft: Boolean) {
        shortRig.setScreen()
        if (barOnLeft) shortRig.dragBarAcross(rightward = false)
        val bar = shortRig.bounds(MAP_ICON_CLUSTER_TAG)
        shortRig.plotTo(3)
        val where = "bar on left $barOnLeft"
        val peek = shortRig.bounds(ROUTE_CARD_TAG)
        assertTrue("$where: narrower than the room's 312 dp: ${width(peek)}", width(peek) < 300.dp)
        if (barOnLeft) {
            assertEquals("$where: anchored to the side away from the bar", (shortRig.screen().right - 8.dp).value, peek.right.value, 0.5f)
        } else {
            assertEquals("$where: anchored to the side away from the bar", 8f, peek.left.value, 0.5f)
        }
        shortRig.setExpanded(true)
        assertTakesOnlyWhatItNeeds(shortRig, where, legs = 2)
        shortRig.assertBarUntouched(where, bar)
        shortRig.assertWhole(where, routeCardLegNameTag(1), "Ridge to Falls")
        shortRig.touchesReachStopAndReturn(where, ACROSS_ROUND)
        shortRig.touchesAcrossSave(where, ACROSS)
    }

    @Test
    fun `the bar on the right - the card is as wide as its lines, at the left edge's margin`() = walk(barOnLeft = false)

    @Test
    fun `the bar on the left - the card is as wide as its lines, at the right edge's margin`() = walk(barOnLeft = true)

    @Test
    fun `the width is the same at peek and expanded, and all through the handle's slide`() {
        shortRig.setScreen()
        shortRig.plotTo(3)
        val peek = shortRig.bounds(ROUTE_CARD_TAG)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { shortRig.plotting.onSetExpanded(true) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(80)
        val part = shortRig.bounds(ROUTE_CARD_TAG)
        assertTrue("precondition: the rows are part way: ${part.top} above ${peek.top}", part.top < peek.top)
        assertEquals("part way, the left", peek.left.value, part.left.value, 0.5f)
        assertEquals("part way, the right", peek.right.value, part.right.value, 0.5f)
        composeRule.mainClock.autoAdvance = true
        shortRig.settle()
        shortRig.assertSame("expanded, the sides", peek, shortRig.bounds(ROUTE_CARD_TAG).copy(top = peek.top, bottom = peek.bottom))
    }

    @Test
    fun `a leg that widens the card eases it wider, the lines do not rewrap on the way, and a leg that needs no more leaves it alone`() {
        val rig = RouteCardBarRig(composeRule, WIDENING_NAMES)
        rig.setScreen()
        rig.plotTo(2)
        rig.setExpanded(true)
        val narrow = rig.bounds(ROUTE_CARD_TAG)
        composeRule.mainClock.autoAdvance = false
        rig.tapNext()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        val part = rig.bounds(ROUTE_CARD_TAG)
        val partLines = rig.lineCount(routeCardLegNameTag(1))
        composeRule.mainClock.autoAdvance = true
        rig.settle()
        val wide = rig.bounds(ROUTE_CARD_TAG)
        println("A16 widening: ${width(narrow).value} > ${width(part).value} > ${width(wide).value}, lines part way $partLines")
        assertTrue("precondition: this leg widens the card: ${width(narrow)} to ${width(wide)}", width(wide) >= width(narrow) + 16.dp)
        assertTrue("part way it is on its way: ${width(narrow)}, ${width(part)}, ${width(wide)}", width(part) > width(narrow) + 1.dp && width(part) < width(wide) - 1.dp)
        assertEquals("part way, the new row is on one line", 1, partLines)
        rig.plotTo(4)
        val later = rig.bounds(ROUTE_CARD_TAG)
        composeRule.mainClock.autoAdvance = false
        rig.tapNext()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals("a shorter leg, part way", width(later).value, width(rig.bounds(ROUTE_CARD_TAG)).value, 0.5f)
        composeRule.mainClock.autoAdvance = true
        rig.settle()
        assertEquals("a shorter leg, settled", width(later).value, width(rig.bounds(ROUTE_CARD_TAG)).value, 0.5f)
    }

    @Test
    fun `under reduced motion a wider leg sets the width at once`() {
        val rig = RouteCardBarRig(composeRule, WIDENING_NAMES)
        rig.setScreen(reduceMotion = true)
        rig.plotTo(2)
        val narrow = rig.bounds(ROUTE_CARD_TAG)
        composeRule.mainClock.autoAdvance = false
        rig.tapNext()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val after = rig.bounds(ROUTE_CARD_TAG)
        composeRule.mainClock.autoAdvance = true
        rig.settle()
        val wide = rig.bounds(ROUTE_CARD_TAG)
        assertTrue("precondition: this leg widens the card: ${width(narrow)} to ${width(wide)}", width(wide) >= width(narrow) + 16.dp)
        assertEquals("the width is there on the first frames", width(wide).value, width(after).value, 0.5f)
    }

    @Test
    fun `long names take the room beside the bar and no more, and wrap whole`() {
        val rig = RouteCardBarRig(composeRule, LONG_NAMES)
        rig.setScreen()
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        rig.plotTo(3)
        rig.setExpanded(true)
        val card = rig.bounds(ROUTE_CARD_TAG)
        assertEquals("the card reaches the room's end, 8 dp from the bar", (bar.left - 8.dp).value, card.right.value, 0.5f)
        rig.assertBarUntouched("long names", bar)
        val first = rig.assertWhole("long names", routeCardLegNameTag(0), "${LONG_NAMES[0]} to ${LONG_NAMES[1]}")
        val second = rig.assertWhole("long names", routeCardLegNameTag(1), "${LONG_NAMES[1]} to ${LONG_NAMES[2]}")
        println("A16 long names: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, rows on $first and $second lines")
        assertTrue("the rows wrap", first > 1 && second > 1)
        val distance = rig.bounds(routeCardLegDistanceTag(0))
        assertEquals("the distance stays at the right", (card.right - CARD_SIDE_PADDING_DP.dp).value, distance.right.value, 0.5f)
        rig.touchesAcrossSave("long names", ACROSS)
    }

    @Test
    fun `a long saved name wraps whole, Directions stays on the card`() {
        val rig = RouteCardBarRig(composeRule)
        rig.setScreen()
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val name = "Upper Falls of the North Fork Trailhead Overlook loop by way of the South Meadow"
        rig.openSaved(name, 3)
        val card = rig.bounds(ROUTE_CARD_TAG)
        assertTrue("the name wraps", rig.assertWhole("saved", ROUTE_CARD_NAME_TAG, name) > 1)
        assertTrue("the card stays clear of the bar", card.right <= bar.left - 7.5.dp)
        assertTrue("Directions is on the card", rig.bounds(ROUTE_CARD_DIRECTIONS_TAG).right <= card.right)
        rig.assertWhole("saved", ROUTE_CARD_ROUTED_COUNT_TAG, "3 Waypoints routed")
    }
}

/** Amendment 16, on the tablet: "So on a tablet it does not stretch to the bar". */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-xhdpi")
class RouteCardContentWidthTabletTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private fun walk(barOnLeft: Boolean) {
        val rig = RouteCardBarRig(composeRule)
        rig.setScreen()
        if (barOnLeft) rig.dragBarAcross(rightward = false)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        rig.plotTo(3)
        rig.assertBarUntouched("tablet, bar on left $barOnLeft", bar)
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("A16 tablet, bar on left $barOnLeft: card ${card.left.value}..${card.right.value}")
        assertTrue("the card takes what its lines need, not the room: ${width(card)}", width(card) < 320.dp)
        if (barOnLeft) {
            assertEquals("the card's right, at the far edge's margin", (rig.screen().right - 8.dp).value, card.right.value, 0.5f)
        } else {
            assertEquals("the card's left, at the far edge's margin", 8f, card.left.value, 0.5f)
        }
    }

    @Test
    fun `the bar on the right - the card keeps to the left edge, as wide as its lines`() = walk(barOnLeft = false)

    @Test
    fun `the bar on the left - the card keeps to the right edge, as wide as its lines`() = walk(barOnLeft = true)

    @Test
    fun `lines wider than the old cap take that room, 640 dp no longer decides`() {
        val rig = RouteCardBarRig(composeRule, LONG_NAMES)
        rig.setScreen()
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        rig.plotTo(3)
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("A16 tablet long names: card ${card.left.value}..${card.right.value}, bar's left ${bar.left.value}")
        assertTrue("wider than 640 dp: ${width(card)}", width(card) > 640.dp)
        assertTrue("and clear of the bar", card.right <= bar.left - 7.5.dp)
        rig.setExpanded(true)
        assertEquals("the row stays on one line", 1, rig.lineCount(routeCardLegNameTag(0)))
    }
}

/** Amendment 16: "Landscape stays at the strip's width", at any content. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardContentWidthLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    @Test
    fun `short and long names give one width, the strip's, and a long name is whole`() {
        val rig = RouteCardBarRig(composeRule, listOf("A", "B", LONG_NAMES[0]))
        rig.setScreen()
        rig.plotTo(2)
        val short = rig.bounds(ROUTE_CARD_TAG)
        rig.plotTo(3)
        rig.setExpanded(true)
        rig.assertSame("the sides", short, rig.bounds(ROUTE_CARD_TAG).copy(top = short.top, bottom = short.bottom))
        rig.assertWhole("landscape", routeCardLegNameTag(1), "B to ${LONG_NAMES[0]}")
        assertTrue("Cancel stays on the card", rig.bounds(ROUTE_CARD_CANCEL_TAG).right <= short.right)
    }
}
