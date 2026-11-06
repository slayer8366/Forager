package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-09-28-502, Amendment 1: where Navigate was tapped is saved with the screen, so Back
 * still goes back there after a recreation, or after the phone closed the app and restored it. Each
 * kind round-trips, including an id with the separator in it and the bubble's anchor and bearing.
 */
class WaypointNavigationOriginSaverTest {

    private val scope = SaverScope { true }

    private fun roundTrip(origin: WaypointNavigationOrigin?): WaypointNavigationOrigin? =
        WaypointNavigationOriginSaver.restore(with(WaypointNavigationOriginSaver) { scope.save(origin) }!!)

    @Test
    fun `every kind comes back as it was saved`() {
        listOf(
            WaypointNavigationOrigin.MapBubble("wp-1", Offset(120.5f, 340.25f), 37.5f),
            WaypointNavigationOrigin.MapDetails("wp-1"),
            WaypointNavigationOrigin.RecordsRow("wp-1"),
            WaypointNavigationOrigin.RecordsDetails("wp-1"),
            WaypointNavigationOrigin.JournalEntryMap("wp-1"),
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `an id with colons in it comes back whole`() {
        val origin = WaypointNavigationOrigin.MapBubble("a:b:c", Offset(1f, 2f), 3f)
        assertEquals(origin, roundTrip(origin))
        assertEquals(WaypointNavigationOrigin.RecordsDetails("x:y"), roundTrip(WaypointNavigationOrigin.RecordsDetails("x:y")))
    }

    // Dispatch 2026-11-05-05, Amendment 1, A8: "Back returns to the trip", after a recreation too.
    @Test
    fun `a trip's bubble and a trip's card come back as a trip's, colons and all`() {
        listOf(
            WaypointNavigationOrigin.MapBubble("trip-1", Offset(120.5f, 340.25f), 37.5f, NavigationTargetKind.TRIP),
            WaypointNavigationOrigin.MapDetails("trip-1", NavigationTargetKind.TRIP),
            WaypointNavigationOrigin.MapBubble("t:r:i:p", Offset(1f, 2f), 3f, NavigationTargetKind.TRIP),
            WaypointNavigationOrigin.MapDetails("t:r:i:p", NavigationTargetKind.TRIP),
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `a waypoint's origins are saved in the same strings as before trips could be navigated to`() {
        fun saved(origin: WaypointNavigationOrigin) = with(WaypointNavigationOriginSaver) { scope.save(origin) }
        assertEquals("bubble:1.0:2.0:3.0:wp-1", saved(WaypointNavigationOrigin.MapBubble("wp-1", Offset(1f, 2f), 3f)))
        assertEquals("map-details:wp-1", saved(WaypointNavigationOrigin.MapDetails("wp-1")))
        assertEquals(WaypointNavigationOrigin.MapBubble("wp-1", Offset(1f, 2f), 3f, NavigationTargetKind.WAYPOINT), WaypointNavigationOriginSaver.restore("bubble:1.0:2.0:3.0:wp-1"))
    }

    @Test
    fun `none comes back as none`() {
        assertEquals(null, roundTrip(null))
    }
}
