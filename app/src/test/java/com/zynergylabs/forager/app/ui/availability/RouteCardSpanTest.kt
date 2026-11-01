package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Test

/** The card's span in the landscape L (dispatch 2026-10-30-08, Amendment 13, RECORD -1919), in plain numbers. */
class RouteCardSpanTest {

    @Test
    fun `the S22 with the rail, the L on the left - the card narrows to start past the pill`() {
        assertEquals(RouteCardSpan(112, 632), routeCardSpan(availablePx = 744, maxCardPx = 640, clearancePx = 112, clusterOnLeft = true))
    }

    @Test
    fun `the S22 with the rail, the L on the right - the card narrows to end before the pill`() {
        assertEquals(RouteCardSpan(0, 632), routeCardSpan(availablePx = 744, maxCardPx = 640, clearancePx = 112, clusterOnLeft = false))
    }

    @Test
    fun `room to spare past the pill - the card keeps its width and moves only as far as it must`() {
        assertEquals(RouteCardSpan(112, 640), routeCardSpan(availablePx = 823, maxCardPx = 640, clearancePx = 112, clusterOnLeft = true))
        assertEquals(RouteCardSpan(71, 640), routeCardSpan(availablePx = 823, maxCardPx = 640, clearancePx = 112, clusterOnLeft = false))
    }

    @Test
    fun `a window wide enough that the centred card already clears - nothing changes`() {
        assertEquals(RouteCardSpan(299, 640), routeCardSpan(availablePx = 1238, maxCardPx = 640, clearancePx = 112, clusterOnLeft = true))
        assertEquals(RouteCardSpan(299, 640), routeCardSpan(availablePx = 1238, maxCardPx = 640, clearancePx = 112, clusterOnLeft = false))
    }
}
