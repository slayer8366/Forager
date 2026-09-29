package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
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

        DeleteTrackUseCase(tracks, waypoints, InMemoryKeptTrackPaths())("t1").getOrThrow()

        assertNull(tracks.tracks["t1"])
        assertEquals(linked.copy(trackId = null), waypoints.waypoints["w1"])
        assertEquals(unrelated, waypoints.waypoints["w2"])
    }

    @Test
    fun `waypoints are detached before the track is deleted, so a failed detach leaves the track in place`() = runTest {
        val tracks = InMemoryTracks(track)
        val waypoints = InMemoryWaypoints(linked).apply { failDetach = true }

        val result = DeleteTrackUseCase(tracks, waypoints, InMemoryKeptTrackPaths())("t1")

        assertTrue(result.isFailure)
        assertTrue(tracks.deletedIds.isEmpty())
        assertEquals(track, tracks.tracks["t1"])
    }

    @Test
    fun `a failed delete after a successful detach is reported as a failure`() = runTest {
        val tracks = InMemoryTracks(track).apply { failDelete = true }
        val waypoints = InMemoryWaypoints(linked)

        val result = DeleteTrackUseCase(tracks, waypoints, InMemoryKeptTrackPaths())("t1")

        assertTrue(result.isFailure)
        assertEquals(listOf("t1"), waypoints.detachedTrackIds)
        assertEquals(track, tracks.tracks["t1"])
    }

    // F3 (dispatch 2026-09-28-195, item 3): the path goes into the entries' saved-path rows before the track goes.

    private val walked = track.copy(
        points = listOf(
            TrackPoint(lat = 45.0, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L),
            TrackPoint(lat = 45.1, lng = -122.1, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L),
        ),
    )

    @Test
    fun `the track's path is copied before the track is deleted, as the lat and lng of the points the repository returns`() = runTest {
        val tracks = InMemoryTracks(walked)
        val paths = InMemoryKeptTrackPaths()
        var trackStillThereWhenCopied: Boolean? = null
        paths.onCopy = { trackStillThereWhenCopied = tracks.tracks.containsKey("t1") }

        DeleteTrackUseCase(tracks, InMemoryWaypoints(linked), paths)("t1").getOrThrow()

        assertEquals(listOf("t1" to listOf(LatLng(45.0, -122.0), LatLng(45.1, -122.1))), paths.copies)
        assertEquals("the copy ran while the track still existed", true, trackStillThereWhenCopied)
        assertNull("and the track is deleted after it", tracks.tracks["t1"])
    }

    @Test
    fun `a failed copy is reported and neither the waypoints nor the track are touched`() = runTest {
        val tracks = InMemoryTracks(walked)
        val waypoints = InMemoryWaypoints(linked)
        val paths = InMemoryKeptTrackPaths().apply { failCopy = true }

        val result = DeleteTrackUseCase(tracks, waypoints, paths)("t1")

        assertTrue(result.isFailure)
        assertEquals("the track is still there, so an entry that kept it can still draw it", walked, tracks.tracks["t1"])
        assertTrue(tracks.deletedIds.isEmpty())
        assertTrue("the waypoints were not detached either", waypoints.detachedTrackIds.isEmpty())
    }

    @Test
    fun `a track that is already gone copies nothing and the delete still runs`() = runTest {
        val tracks = InMemoryTracks()
        val paths = InMemoryKeptTrackPaths()

        val result = DeleteTrackUseCase(tracks, InMemoryWaypoints(), paths)("t1")

        assertTrue(result.isSuccess)
        assertTrue("nothing to copy from, and a copy of nothing must not overwrite an earlier saved path", paths.copies.isEmpty())
        assertEquals(listOf("t1"), tracks.deletedIds)
    }
}
