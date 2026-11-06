package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import org.junit.Assert.assertEquals
import org.junit.Test

/** [isShortRecording], RECORD -867 ("Leave out recordings under a minute"). Headless. */
class ShortRecordingTest {

    private fun recording(lengthMillis: Long?, importedAt: Long? = null) = Track(
        id = "t",
        name = null,
        startedAtEpochMillis = 1_000_000L,
        endedAtEpochMillis = lengthMillis?.let { 1_000_000L + it },
        points = emptyList(),
        importedAtEpochMillis = importedAt,
    )

    @Test
    fun `a recording under a minute is short, and one of a minute or more is not`() {
        assertEquals("the owner's 2:44 PM case, a few seconds", true, recording(5_000L).isShortRecording())
        assertEquals("one millisecond under", true, recording(59_999L).isShortRecording())
        assertEquals("exactly a minute is not shorter than one", false, recording(60_000L).isShortRecording())
        assertEquals(false, recording(6_800_000L).isShortRecording())
    }

    /** RECORD -873: only a recording's own Start and End markers count as its markers, never a waypoint saved during it. */
    @Test
    fun `a recording's own markers are its Start and End, not a waypoint saved during it`() {
        fun waypoint(trackId: String?, designation: WaypointDesignation?) = Waypoint("w", 45.0, -122.0, null, "w", "", 0L, trackId = trackId, designation = designation)
        val left = setOf("blip")
        assertEquals(true, waypoint("blip", WaypointDesignation.ORIGIN).isOwnMarkerOf(left))
        assertEquals(true, waypoint("blip", WaypointDesignation.END).isOwnMarkerOf(left))
        assertEquals("saved by the user during it", false, waypoint("blip", null).isOwnMarkerOf(left))
        assertEquals("another walk's Start", false, waypoint("walk", WaypointDesignation.ORIGIN).isOwnMarkerOf(left))
        assertEquals("on no walk", false, waypoint(null, null).isOwnMarkerOf(left))
    }

    @Test
    fun `a track still recording, or an imported one, is never short`() {
        assertEquals("no end yet", false, recording(null).isShortRecording())
        assertEquals("imported, not a recording", false, recording(5_000L, importedAt = 2_000_000L).isShortRecording())
    }
}
