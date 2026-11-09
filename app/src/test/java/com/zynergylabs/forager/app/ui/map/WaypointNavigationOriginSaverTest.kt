package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
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

    @Test
    fun `none comes back as none`() {
        assertEquals(null, roundTrip(null))
    }
}
