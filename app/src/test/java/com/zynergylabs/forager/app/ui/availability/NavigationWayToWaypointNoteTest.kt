package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step 6 PR 3 (dispatch 2026-10-26-11), the step path's step 8: with no trail route to a waypoint the HUD is today's
 * straight-line one (the straight distance, "straight", the needle at the waypoint), with the approved note in its
 * status line (RECORD -1652 Q4, -1653, -1665, -1667): whole where it fits, else its short form, the note without
 * "· straight line", or "Route leaves your downloaded area" for the leaves-area note; never an empty form.
 */
class NavigationWayToWaypointNoteTest {

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val creek = Waypoint(id = "wp-1", lat = 45.53, lng = -122.68, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = t)

    private fun readout(route: ReturnRoute?) =
        navigationReadout(TrueHeadingReading.Available(0f), fix, creek, DistanceUnit.MILES, t + 1_000L, route = route)

    @Test
    fun `with a note the HUD is today's straight one, the note in its status line`() {
        val today = readout(null)
        for (note in WayToWaypointNote.entries) {
            val r = readout(ReturnRoute.Straight(note))
            val straight = GeoDistance.metersBetween(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng))
            assertEquals("$note: the straight distance", formatDistanceWithAccuracy(straight, fix.accuracyMeters, DistanceUnit.MILES), r.distanceText)
            assertEquals("$note", today.distanceText, r.distanceText)
            assertEquals("$note: \"straight\"", "straight", r.distanceKindText)
            assertEquals("$note: the needle at the waypoint, as today", today.targetArrowDegrees, r.targetArrowDegrees)
            assertEquals("$note", today.targetText, r.targetText)
            assertEquals(note.text, r.statusText)
            val short = if (note == WayToWaypointNote.LEAVES_AREA) "Route leaves your downloaded area" else note.text.removeSuffix(" · straight line")
            assertEquals("$note", listOf(note.text, short), r.statusForms())
        }
    }

    @Test
    fun `the short forms drop the tail, and the leaves-area one is the owner's`() {
        assertEquals("No trail routes here", wayToWaypointNoteShortText(WayToWaypointNote.NO_TRAIL_ROUTES))
        assertEquals("The waypoint is in another area", wayToWaypointNoteShortText(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA))
        assertEquals("Route leaves your downloaded area", wayToWaypointNoteShortText(WayToWaypointNote.LEAVES_AREA))
    }
}
