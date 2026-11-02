package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The card's room, headless. Sorrel's N-4 (docs/reviews/2026-11-01-sorrel-pr449-2.md on sorrel-pr449-2) for the bar's column; C17 to
 * C20 (dispatch 2026-10-30-08) for the sides. The three tests of the landscape tuck's padding floor (Sorrel's N-5) went with C18,
 * which took the card off the navigation card's top: there is no tuck.
 */
class RouteCardPlacementTest {

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
