package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.NavigationTargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Dispatch 2026-11-05-05, Amendment 1, A8 (RECORD -2148): navigating to a trip, "each existing message that names "the
 * waypoint" names "the trip" instead, otherwise identical", and a waypoint's keep their words exactly. Every note is
 * checked, so a note added later that names the waypoint fails here until it has its trip words.
 */
class WayToTripNotesTest {

    private val renamed = mapOf(
        WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL to "The trip is far from any trail · straight line",
        WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA to "The trip is in another area · straight line",
        WayToWaypointNote.WAY_IN_BLOCKED to "No clear way in to the trip · straight line",
    )

    @Test
    fun `for a trip, the three notes that name the waypoint name the trip`() {
        renamed.forEach { (note, words) -> assertEquals(note.name, words, note.textFor(NavigationTargetKind.TRIP)) }
    }

    @Test
    fun `for a trip, every other note is the waypoint's, word for word, and none names a waypoint`() {
        WayToWaypointNote.entries.filterNot { it in renamed }.forEach { note ->
            assertEquals(note.name, note.text, note.textFor(NavigationTargetKind.TRIP))
        }
        WayToWaypointNote.entries.forEach { note ->
            assertFalse("${note.name} for a trip: '${note.textFor(NavigationTargetKind.TRIP)}'", note.textFor(NavigationTargetKind.TRIP).contains("waypoint", ignoreCase = true))
        }
    }

    @Test
    fun `for a waypoint, every note keeps its words exactly`() {
        WayToWaypointNote.entries.forEach { note -> assertEquals(note.name, note.text, note.textFor(NavigationTargetKind.WAYPOINT)) }
        assertEquals("The waypoint is far from any trail · straight line", WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL.textFor(NavigationTargetKind.WAYPOINT))
        assertEquals("The waypoint is in another area · straight line", WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA.textFor(NavigationTargetKind.WAYPOINT))
        assertEquals("No clear way in to the waypoint · straight line", WayToWaypointNote.WAY_IN_BLOCKED.textFor(NavigationTargetKind.WAYPOINT))
    }
}
