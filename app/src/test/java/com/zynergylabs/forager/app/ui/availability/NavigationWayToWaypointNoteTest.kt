package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.NavigationTargetKind
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
 *
 * Dispatch 2026-11-05-10 (RECORD -2181), Amendment 1, F2, widening F1 ("the straight line is the explanation"), the owner: "Keep
 * the ones asking something": "Remove the ones that only say why (no routes here, another area, far from any trail, no clear way
 * in…). Keep "Trail routes not downloaded" and "Trail routes need an app update", since you could act on them." So only those two
 * are in the status line, whole where they fit, else without "· straight line"; the other 11 leave it empty, with no short form,
 * in a trip's words as in a waypoint's. Each note still chooses the straight line, its retry and its log line, as before.
 */
class NavigationWayToWaypointNoteTest {

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val creek = Waypoint(id = "wp-1", lat = 45.53, lng = -122.68, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = t)

    private fun readout(route: ReturnRoute?) =
        navigationReadout(TrueHeadingReading.Available(0f), fix, creek, DistanceUnit.MILES, t + 1_000L, route = route)

    /** The two notes that ask something (F2). */
    private val asking = setOf(WayToWaypointNote.ROUTES_NOT_DOWNLOADED, WayToWaypointNote.ROUTES_NEED_APP_UPDATE)

    /**
     * Was "with a note the HUD is today's straight one, the note in its status line", asserting `assertEquals(note.text,
     * r.statusText)` and `assertEquals("$note", listOf(note.text, short), r.statusForms())` for every note. F2 keeps that claim for
     * the two that ask something and changes it to "no status line" for the other 11.
     */
    @Test
    fun `with a note the HUD is today's straight one, and only the two notes that ask something are in its status line`() {
        val today = readout(null)
        assertEquals("positive control: 13 notes, 2 that stay and 11 that go", listOf(13, 2), listOf(WayToWaypointNote.entries.size, asking.size))
        for (note in WayToWaypointNote.entries) {
            val r = readout(ReturnRoute.Straight(note))
            val straight = GeoDistance.metersBetween(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng))
            assertEquals("$note: the straight distance", formatDistanceWithAccuracy(straight, fix.accuracyMeters, DistanceUnit.MILES), r.distanceText)
            assertEquals("$note", today.distanceText, r.distanceText)
            assertEquals("$note: \"straight\"", "straight", r.distanceKindText)
            assertEquals("$note: the needle at the waypoint, as today", today.targetArrowDegrees, r.targetArrowDegrees)
            assertEquals("$note", today.targetText, r.targetText)
            if (note in asking) {
                assertEquals("$note: the note, whole", note.text, r.statusText)
                assertEquals("$note", listOf(note.text, note.text.removeSuffix(" · straight line")), r.statusForms())
            } else {
                assertEquals("$note: no status line (F2)", "", r.statusText)
                assertEquals("$note: no short form either", listOf(""), r.statusForms())
            }
        }
    }

    /**
     * Was "the short forms drop the tail, and the leaves-area one is the owner's", asserting "No trail routes here", "The waypoint is
     * in another area" and "Route leaves your downloaded area" as the short forms of NO_TRAIL_ROUTES, WAYPOINT_IN_ANOTHER_AREA and
     * LEAVES_AREA. Those three go (F2), so the card offers no short form for them: the claim is now its absence. The two that stay
     * still drop the tail. The leaves-area short form stays the way home's (NavigationWayHomeNoteTest).
     */
    @Test
    fun `the short forms - none for the notes that go, and the two that stay drop the tail`() {
        for (note in listOf(WayToWaypointNote.NO_TRAIL_ROUTES, WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA, WayToWaypointNote.LEAVES_AREA)) {
            assertEquals("$note: no short form offered", listOf(""), readout(ReturnRoute.Straight(note)).statusForms())
        }
        assertEquals("Trail routes not downloaded", wayToWaypointNoteShortText(WayToWaypointNote.ROUTES_NOT_DOWNLOADED))
        assertEquals("Trail routes need an app update", wayToWaypointNoteShortText(WayToWaypointNote.ROUTES_NEED_APP_UPDATE))
        assertEquals(
            listOf("Trail routes not downloaded · straight line", "Trail routes not downloaded"),
            readout(ReturnRoute.Straight(WayToWaypointNote.ROUTES_NOT_DOWNLOADED)).statusForms(),
        )
    }

    /**
     * A trip (dispatch 2026-11-05-05, A8, "the trip" for "the waypoint"): the three notes with a trip wording all go (F2), so no trip
     * wording reaches the card; the two that stay name neither, and read as for a waypoint.
     */
    @Test
    fun `for a trip the three trip wordings leave no status line, and the two that stay read as for a waypoint`() {
        for (note in listOf(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL, WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA, WayToWaypointNote.WAY_IN_BLOCKED)) {
            val r = readout(ReturnRoute.Straight(note, NavigationTargetKind.TRIP))
            assertEquals("$note, a trip: no status line", "", r.statusText)
            assertEquals("$note, a trip: no short form", listOf(""), r.statusForms())
        }
        for (note in asking) assertEquals("$note, a trip", note.text, readout(ReturnRoute.Straight(note, NavigationTargetKind.TRIP)).statusText)
    }
}
