package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.geometry.Rect
import com.zynergylabs.forager.app.ui.map.NavigationSideInsets
import com.zynergylabs.forager.app.ui.map.navigationViewPaddingPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-06-01, Amendment 1, J8 (RECORD -2183), the owner with the route card resting on the navigation card: "(b) Above top
 * card (Recommended)": the dot is kept just above whichever card is on top. The route card's own top comes from its branch (#507),
 * which writes [MapIconClusterState.navigationRestingOnCardTopInRootPx]; nothing on this branch writes it, so the real screen cannot
 * reach it yet and this holds the input as the screen reads it: through the cluster state the cards write, to the padding the camera
 * is sent, to where that puts the dot. The navigation card alone is held on the real Maps tab (NavigationDotAboveCardAt780Test,
 * NavigationDotAboveCardAt823Test).
 *
 * A map 780 x 360 px with its box at the root's origin: the view's top padding 90 px, so with no bottom padding the dot's centre is at
 * 225 px, at the middle across, 390 px.
 */
class NavigationDotAboveCardInputTest {

    private val clearancePx = 12f

    private fun cluster(card: Rect, restingOnTop: Float?) = MapIconClusterState(MapIconClusterPositionState()).apply {
        mapContentBoxLeftInRootPx = 0f
        mapContentBoxTopInRootPx = 0f
        mapContentBoxWidthPx = 780f
        mapContentBoxHeightPx = 360f
        navigationCardRestingBoundsInRoot = card
        navigationRestingOnCardTopInRootPx = restingOnTop
    }

    /** The dot's centre, from the top, for the padding the camera is sent with [bottomPx] added. */
    private fun dotY(bottomPx: Double): Double {
        val (_, top, _, bottom) = navigationViewPaddingPx(360, NavigationSideInsets(0.0, 0.0, bottomPx)).toList()
        return top + (360.0 - top - bottom) / 2.0
    }

    private val card = Rect(200f, 230f, 600f, 360f)

    @Test
    fun `J8 a top resting above the navigation card's moves the dot above that top, not the navigation card's`() {
        val alone = navigationDotAboveCardBottomPx(cluster(card, restingOnTop = null), NavigationSideInsets.NONE, clearancePx)
        assertEquals("the navigation card alone: the dot's centre its clearance above that card's top", 230.0 - 12.0, dotY(alone.bottomPx), 1e-3)
        val stacked = navigationDotAboveCardBottomPx(cluster(card, restingOnTop = 150f), NavigationSideInsets.NONE, clearancePx)
        assertEquals("a card resting on it with its top at 150: the dot's centre its clearance above that top", 150.0 - 12.0, dotY(stacked.bottomPx), 1e-3)
        assertTrue("and so higher than for the navigation card alone", dotY(stacked.bottomPx) < dotY(alone.bottomPx))
        assertTrue("neither held at the top padding", !alone.capped && !stacked.capped)
    }

    @Test
    fun `J8 a card clear of the dot across asks for nothing, whatever rests on it`() {
        val beside = Rect(0f, 230f, 300f, 360f)
        assertEquals(DotAboveCard.NONE, navigationDotAboveCardBottomPx(cluster(beside, restingOnTop = 150f), NavigationSideInsets.NONE, clearancePx))
    }

    @Test
    fun `J8 a card whose top already clears the dot's bottom asks for nothing`() {
        val low = Rect(200f, 237f, 600f, 360f)
        assertEquals(DotAboveCard.NONE, navigationDotAboveCardBottomPx(cluster(low, restingOnTop = null), NavigationSideInsets.NONE, clearancePx))
    }

    @Test
    fun `J8 a card moved up off the bottom edge above the dot asks for nothing`() {
        // J3 step 3: moved up, its bottom at the dot's top.
        val lifted = Rect(200f, 100f, 600f, 213f)
        assertEquals(DotAboveCard.NONE, navigationDotAboveCardBottomPx(cluster(lifted, restingOnTop = null), NavigationSideInsets.NONE, clearancePx))
    }

    @Test
    fun `J8 across, the dot is where D29's side padding puts it`() {
        // D29 moved the dot to x 150 (left 0, right 480): a card at 200..600 no longer reaches it, one at 100..300 does.
        val side = NavigationSideInsets(0.0, 480.0)
        assertEquals(DotAboveCard.NONE, navigationDotAboveCardBottomPx(cluster(card, restingOnTop = null), side, clearancePx))
        val under = navigationDotAboveCardBottomPx(cluster(Rect(100f, 230f, 300f, 360f), restingOnTop = null), side, clearancePx)
        assertEquals(230.0 - 12.0, dotY(under.bottomPx), 1e-3)
    }

    @Test
    fun `J8 a top above the view's top padding holds the dot at its edge and says so`() {
        val result = navigationDotAboveCardBottomPx(cluster(card, restingOnTop = 60f), NavigationSideInsets.NONE, clearancePx)
        assertTrue("held", result.capped)
        assertEquals("the dot's centre its clearance below the top padding's edge", 90.0 + 12.0, dotY(result.bottomPx), 1e-3)
    }
}
