package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The way home's notes in the navigation display's status line (dispatch 2026-10-24-10; RECORD -1586, -1589, -1613):
 * the whole note where it fits, its short form where it does not ("Route leaves your downloaded area" for the leaves-area
 * notice, the others without "· following your track"), never an empty form; and the line's other states keep their
 * place over it.
 */
class NavigationWayHomeNoteTest {

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val east = LatLng(45.52, -122.679)

    private fun readout(route: ReturnRoute, fixAt: LocationFix.Update = fix, now: Long = t + 1_000L) =
        navigationReadout(TrueHeadingReading.Available(0f), fixAt, start, DistanceUnit.MILES, now, route = route)

    @Test
    fun `every note shows whole in the status line, with its short form and no empty one`() {
        for (note in WayHomeNote.entries) {
            val r = readout(ReturnRoute.Ahead(east, 1_500.0, note))
            assertEquals(note.text, r.statusText)
            val short = if (note == WayHomeNote.LEAVES_AREA) "Route leaves your downloaded area" else note.text.removeSuffix(" · following your track")
            assertEquals("$note", listOf(note.text, short).distinct(), r.statusForms())
        }
    }

    @Test
    fun `with no note the line is empty, as before, and a stale fix keeps the line`() {
        assertEquals("", readout(ReturnRoute.Ahead(east, 1_500.0)).statusText)
        val stale = readout(ReturnRoute.Ahead(east, 1_500.0, WayHomeNote.NO_TRAIL_ROUTES), now = t + 60_000L)
        assertTrue("a stale fix's line, not the note: ${stale.statusText}", stale.statusText.startsWith("Last fix"))
    }
}
