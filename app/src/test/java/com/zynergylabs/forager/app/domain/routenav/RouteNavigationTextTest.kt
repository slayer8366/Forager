package com.zynergylabs.forager.app.domain.routenav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-11-03-03, A9: the owner's words for route navigation, pinned, so a change to any of them is a change someone
 * chose. As FindWayToWaypointUseCaseTest pins its notes.
 */
class RouteNavigationTextTest {

    @Test
    fun `the words are the owner's`() {
        assertEquals("To Ridge · 2 of 3", routeNextStopText("Ridge", index = 1, stopCount = 3))
        assertEquals("To Creek · 1 of 3", routeNextStopText("Creek", index = 0, stopCount = 3))
        assertEquals("Reached Ridge", routeReachedText("Ridge"))
        assertEquals(5_000L, ROUTE_REACHED_SHOW_MILLIS)
        assertEquals("Stopped navigating: that route was deleted.", ROUTE_NAVIGATION_GONE_TEXT)
        assertEquals("Show route", ROUTE_CARD_SHOW_DESCRIPTION)
        assertEquals("Hide route", ROUTE_CARD_HIDE_DESCRIPTION)
        assertEquals("Stop navigating", ROUTE_CARD_STOP_NAVIGATING_TEXT)
        assertEquals("Route navigation", ROUTE_NAVIGATION_CHANNEL_NAME)
        assertEquals("Navigating Ridge loop", routeNavigationNotificationTitle("Ridge loop"))
        assertEquals("Stop navigating", ROUTE_NAVIGATION_NOTIFICATION_STOP)
        assertEquals("Arrived", ROUTE_NAVIGATION_NOTIFICATION_ARRIVED)
    }
}
