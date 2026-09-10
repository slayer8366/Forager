package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HUD-foundations dispatch, Item 3 (owner decision): deleting a track nulls the link on the
 * waypoints dropped while it recorded, so they survive as ordinary waypoints and never dangle.
 * Fakes from [GetTrackOriginWaypointUseCaseTest], which record what was called and can be told to
 * fail, so the order of the two steps is asserted rather than assumed.
 */
class DeleteTrackUseCaseTest {

    private val track = Track(id = "t1", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = 5_000L, points = emptyList(), originWaypointId = "w1")
    private val linked = Waypoint(id = "w1", lat = 45.52, lng = -122.68, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = 1_000L, trackId = "t1")
    private val unrelated = Waypoint(id = "w2", lat = 45.6, lng = -122.7, altitude = null, name = "Elsewhere", note = "", createdAtEpochMillis = 2_000L, trackId = "other")

    @Test
    fun `the track goes, its waypoints stay with the link nulled, and other tracks' links are untouched`() = runTest {
        val tracks = InMemoryTracks(track)
        val waypoints = InMemoryWaypoints(linked, unrelated)

        DeleteTrackUseCase(tracks, waypoints)("t1").getOrThrow()

        assertNull(tracks.tracks["t1"])
        assertEquals(linked.copy(trackId = null), waypoints.waypoints["w1"])
        assertEquals(unrelated, waypoints.waypoints["w2"])
    }

    @Test
    fun `waypoints are detached before the track is deleted, so a failed detach leaves the track in place`() = runTest {
        val tracks = InMemoryTracks(track)
        val waypoints = InMemoryWaypoints(linked).apply { failDetach = true }

        val result = DeleteTrackUseCase(tracks, waypoints)("t1")

        assertTrue(result.isFailure)
        assertTrue(tracks.deletedIds.isEmpty())
        assertEquals(track, tracks.tracks["t1"])
    }

    @Test
    fun `a failed delete after a successful detach is reported as a failure`() = runTest {
        val tracks = InMemoryTracks(track).apply { failDelete = true }
        val waypoints = InMemoryWaypoints(linked)

        val result = DeleteTrackUseCase(tracks, waypoints)("t1")

        assertTrue(result.isFailure)
        assertEquals(listOf("t1"), waypoints.detachedTrackIds)
        assertEquals(track, tracks.tracks["t1"])
    }
}
