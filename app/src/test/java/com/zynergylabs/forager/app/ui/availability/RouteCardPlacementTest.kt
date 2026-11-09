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

    /**
     * D32 (RECORD -1987): the S22's figures from s8 b3-3p-rc.xml at 3.75 px per dp: Re-center would start at 514 px (the L's foot at
     * 484 px and the 8 dp gap, 30 px), its icon 180 px, the gap before the cards 30 px, and the cards at the strip's width from 576 px.
     * Both cards narrow by 514 + 180 + 30 - 576 = 148 px (39.5 dp).
     */
    @Test
    fun `D32 with the S22's figures both cards narrow by the icon and its gaps less the room there`() {
        assertEquals(148f, routeCardLeaveRoomPx(besideLeftPx = 514f, iconPx = 180f, gapPx = 30f, naturalCardLeftPx = 576f), 0.01f)
    }

    @Test
    fun `D32 where the icon and its gaps fit exactly, nothing narrows`() {
        assertEquals(0f, routeCardLeaveRoomPx(besideLeftPx = 514f, iconPx = 180f, gapPx = 30f, naturalCardLeftPx = 724f), 0.01f)
    }

    @Test
    fun `D32 where there is room to spare, nothing narrows and nothing widens`() {
        assertEquals(0f, routeCardLeaveRoomPx(besideLeftPx = 336f, iconPx = 144f, gapPx = 24f, naturalCardLeftPx = 615f), 0.01f)
    }

    /** Amendment 6, A1 (RECORD -2187): with no tuck, a card's top 10 dp over the box's bottom puts the card there. Before A1: 0 dp. */
    @Test
    fun `a navigation card's top 10 dp over the box's bottom puts the card's bottom there`() {
        assertEquals(10f, bottomWith(10.dp).value, 0.01f)
    }

    @Test
    fun `a navigation card's top at the box's bottom leaves the padding at 0 dp`() {
        assertEquals(0f, bottomWith(0.dp).value, 0.01f)
    }

    /** Amendment 6, A1 (RECORD -2187): the card's bottom on the navigation card's top. Before A1: that top less the 16 dp tuck (64 dp). */
    @Test
    fun `a taller navigation card lifts the card's bottom to its top`() {
        assertEquals(80f, bottomWith(80.dp).value, 0.01f)
    }

    /** Sorrel's N-4: a bar minimised before it was first measured reports 0 dp; the column still takes its touch target. */
    @Test
    fun `a bar not yet measured still takes its touch target as the column, and a measured bar its own width`() {
        assertEquals("unmeasured", (MAP_ICON_BAR_EDGE_INSET + MIN_TOUCH_TARGET + ROUTE_CARD_SIDE_MARGIN).value, routeCardBarColumn(0.dp).value, 0.01f)
        assertEquals("measured", (MAP_ICON_BAR_EDGE_INSET + 60.dp + ROUTE_CARD_SIDE_MARGIN).value, routeCardBarColumn(60.dp).value, 0.01f)
    }

    private fun sides(barOnLeft: Boolean): Pair<Float, Float> {
        val p = routeCardSides(barOnLeft, barColumn = 64.dp)
        return p.calculateStartPadding(LayoutDirection.Ltr).value to p.calculateEndPadding(LayoutDirection.Ltr).value
    }

    /**
     * C17: nothing on the card's own edge, the bar's column on the bar's side. C18's third case, the navigation card's width on the
     * card's side while touching it, went with C21 (RECORD -1955), which puts the card on the navigation card at its width instead.
     */
    @Test
    fun `the card's own edge takes nothing, and the bar its column`() {
        assertEquals("bar on the right, card at the left edge", 0f to 64f, sides(barOnLeft = false))
        assertEquals("bar on the left, card at the right edge", 64f to 0f, sides(barOnLeft = true))
    }
}
