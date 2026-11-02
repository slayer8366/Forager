package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card's room, headless. Sorrel's N-4 (docs/reviews/2026-11-01-sorrel-pr449-2.md on sorrel-pr449-2) for the bar's column; C17 to
 * C20 (dispatch 2026-10-30-08) for the sides. The three tests of the landscape tuck's padding floor (Sorrel's N-5,
 * docs/reviews/2026-11-01-sorrel-pr449-2.md on sorrel-pr449-2) went with C18, which took the card off the navigation card's top, and
 * came back with C21 (RECORD -1955), which puts it back there; restored from 8a6126f9d. The padding's floor was guarded only by HUD
 * classes that happen to lay the card out at the short heights; a navigation card whose top is within the tuck of the box's bottom
 * leaves no room to tuck into.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardPlacementTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /** The card's bottom padding with the navigation card's top [navigationTopAboveBoxBottom] over the box's bottom. */
    private fun bottomWith(navigationTopAboveBoxBottom: Dp): Dp {
        var room: RouteCardLandscapeRoom? = null
        composeRule.setContent {
            val density = LocalDensity.current
            val cluster = remember {
                MapIconClusterState(MapIconClusterPositionState()).apply {
                    railPortEdge = ScreenEdge.Left
                    punchHoleEdge = ScreenEdge.Right
                    mapContentBoxTopInRootPx = 0f
                    mapContentBoxHeightPx = with(density) { 384.dp.toPx() }
                    navigationCardBoundsInRoot = with(density) {
                        val top = (384.dp - navigationTopAboveBoxBottom).toPx()
                        Rect(0f, top, 278.dp.toPx(), top + 100.dp.toPx())
                    }
                }
            }
            room = routeCardLandscapeRoom(cluster, stripWidthPx = with(density) { 278.dp.roundToPx() }, topLimitPx = 0f, isNavigating = true)
        }
        composeRule.waitForIdle()
        assertNotNull("a landscape window with a measured strip has a place", room)
        return room!!.bottom
    }

    @Test
    fun `a navigation card's top within the tuck of the box's bottom leaves the padding at 0 dp`() {
        assertEquals(0f, bottomWith(10.dp).value, 0.01f)
    }

    @Test
    fun `a navigation card's top at the box's bottom leaves the padding at 0 dp`() {
        assertEquals(0f, bottomWith(0.dp).value, 0.01f)
    }

    @Test
    fun `a taller navigation card lifts the card to its top, less the tuck`() {
        assertEquals(64f, bottomWith(80.dp).value, 0.01f)
    }

    /** D12's credit on the S22, in px: [0,1364][1638,1440]. */
    private val d12Credit = Rect(0f, 1364f, 1638f, 1440f)

    /** D13 (RECORD -1955), fail first on D12's figures: the card [520,930][1287,1440] covered the credit's middle, so the credit rises. */
    @Test
    fun `D12's card over the credit on the S22 lifts the credit`() {
        assertTrue(routeCardOverCredit(card = Rect(520f, 930f, 1287f, 1440f), creditAtRest = d12Credit))
    }

    /**
     * D16 (RECORD -1956, the planner's call): "meet" means the two rectangles overlap at the credit's resting place. A card that shares
     * only the credit's horizontal span, above it, or only its rows, beside it, never touches it and leaves it down; so does one whose
     * bottom edge only reaches the credit's top.
     */
    @Test
    fun `a card that shares only a span with the credit, or only touches its edge, leaves it down`() {
        assertFalse("above it, the same span", routeCardOverCredit(card = Rect(520f, 930f, 1287f, 1300f), creditAtRest = d12Credit))
        assertFalse("its bottom on the credit's top", routeCardOverCredit(card = Rect(520f, 930f, 1287f, 1364f), creditAtRest = d12Credit))
        assertFalse("beside it, the same rows", routeCardOverCredit(card = Rect(1700f, 930f, 2000f, 1440f), creditAtRest = d12Credit))
        assertFalse("no card", routeCardOverCredit(card = null, creditAtRest = d12Credit))
        assertFalse("no credit measured", routeCardOverCredit(card = Rect(520f, 930f, 1287f, 1440f), creditAtRest = null))
    }

    /** Sorrel's N-4: a bar minimised before it was first measured reports 0 dp; the column still takes its touch target. */
    @Test
    fun `a bar not yet measured still takes its touch target as the column, and a measured bar its own width`() {
        assertEquals("unmeasured", (MAP_ICON_BAR_EDGE_INSET + MIN_TOUCH_TARGET + ROUTE_CARD_SIDE_MARGIN).value, routeCardBarColumn(0.dp).value, 0.01f)
        assertEquals("measured", (MAP_ICON_BAR_EDGE_INSET + 60.dp + ROUTE_CARD_SIDE_MARGIN).value, routeCardBarColumn(60.dp).value, 0.01f)
    }

    private fun sides(barOnLeft: Boolean, cardOnLeft: Boolean, beside: Float): Pair<Float, Float> {
        val p = routeCardSides(barOnLeft, barColumn = 64.dp, cardOnLeft = cardOnLeft, besideNavigationCard = beside.dp)
        return p.calculateStartPadding(LayoutDirection.Ltr).value to p.calculateEndPadding(LayoutDirection.Ltr).value
    }

    /** C17: nothing on the card's own edge; the bar's column on the bar's side; beside the navigation card, its width on the card's. */
    @Test
    fun `the card's own edge takes nothing, the bar its column, and the navigation card its width`() {
        assertEquals("bar on the right, card at the left edge", 0f to 64f, sides(barOnLeft = false, cardOnLeft = true, beside = 0f))
        assertEquals("bar on the left, card at the right edge", 64f to 0f, sides(barOnLeft = true, cardOnLeft = false, beside = 0f))
        assertEquals("navigating, the L on the left, the navigation card on the right", 64f to 343f, sides(barOnLeft = true, cardOnLeft = false, beside = 343f))
    }
}
