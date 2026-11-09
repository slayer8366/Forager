package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.LiveCounts.app
import com.zynergylabs.forager.app.LiveCounts.awaitUntil
import com.zynergylabs.forager.app.LiveCounts.committed
import com.zynergylabs.forager.app.LiveCounts.container
import com.zynergylabs.forager.app.LiveCounts.deleteThroughJournal
import com.zynergylabs.forager.app.LiveCounts.draft
import com.zynergylabs.forager.app.LiveCounts.finishThroughJournal
import com.zynergylabs.forager.app.LiveCounts.idleUntil
import com.zynergylabs.forager.app.LiveCounts.save
import com.zynergylabs.forager.app.LiveCounts.silentLog
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-28-08 (RECORD -1780, -1781; the owner: "Yes, update straight away (Recommended)"): a waypoint's and a
 * track's "used in N journal entries" is the true number the moment a journal entry keeping it is deleted or finished.
 *
 * Until this dispatch the Track screen's ViewModel read each count once, when its list loaded, and no journal entry save
 * or delete read it again (docs/audits/2026-10-28-records-follow-ups-look.md, section 1). The S22's round 2 showed
 * "Waypoint deleted · used in 1 journal entry" after the only entry keeping it had been deleted.
 *
 * Each test is the look report's proposed test (a), or its save-direction mirror: the real container over the real
 * database, the Track screen's ViewModel as `MainActivity` builds it ([createTrackRecordingViewModel]), and the entry
 * changed through the journal's own ViewModel calls ([CartographyViewModel.onDeleteEntry], the report's Delete dialog's
 * call; Finish entry). Each reads both what the waypoint's details read and what its delete message carries. No test
 * here records, so none can leave a poll loop running (CLAUDE.md, the poll-loop stall).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveWaypointTrackCountsTest {

    private val waypoint = Waypoint(id = "wp-live", lat = 45.21, lng = -122.51, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = 1_500L)
    private fun waypointDecision(kept: Boolean) = WaypointDecision(waypointId = waypoint.id, name = waypoint.name, lat = waypoint.lat, lng = waypoint.lng, kept = kept)

    private val track = Track(
        id = "track-live", name = "Ridge Loop", startedAtEpochMillis = 1_000L, endedAtEpochMillis = null,
        points = listOf(TrackPoint(lat = 45.20, lng = -122.50, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L)),
    )
    private fun trackDecision(kept: Boolean) = TrackDecision(trackId = track.id, name = "Ridge Loop", distanceMeters = 0.0, durationMillis = 1_000L, pointCount = 1, kept = kept)

    private fun seedWaypoint() = runBlocking { container.waypointRepository.save(waypoint).getOrThrow() }

    private fun seedTrack() = runBlocking {
        container.trackRepository.create(track).getOrThrow()
        container.trackRepository.appendPoints(track.id, track.points).getOrThrow()
        container.trackRepository.end(track.id, 2_000L).getOrThrow()
    }

    private fun trackScreen(): TrackRecordingViewModel = createTrackRecordingViewModel(container, silentLog, app)

    private fun TrackRecordingViewModel.waypointCount() = uiState.value.waypointEntryReferenceCounts[waypoint.id] ?: 0
    private fun TrackRecordingViewModel.trackCount() = uiState.value.trackEntryReferenceCounts[track.id] ?: 0

    @Test
    fun `deleting the one entry that keeps a waypoint takes its count to 0, in its details and its delete message`() {
        seedWaypoint()
        save(committed("entry-wp").copy(waypointDecisions = listOf(waypointDecision(kept = true))))
        val screen = trackScreen()
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the waypoint is listed and counted in 1 entry") {
            screen.uiState.value.waypoints.any { it.id == waypoint.id } && screen.waypointCount() == 1
        }

        deleteThroughJournal(journal, "entry-wp")
        idleUntil { screen.waypointCount() == 0 }

        assertEquals("the waypoint's Used in still counts the deleted entry", 0, screen.waypointCount())
        screen.requestRemoveWaypoint(waypoint.id)
        assertEquals("the delete message still says used in the deleted entry", 0, screen.uiState.value.pendingWaypointDelete?.entryReferenceCount)
    }

    @Test
    fun `finishing an entry that keeps an already-listed waypoint takes its count to 1, in its details and its delete message`() {
        seedWaypoint()
        save(draft("entry-wp").copy(waypointDecisions = listOf(waypointDecision(kept = false))))
        val screen = trackScreen()
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the waypoint is listed") { screen.uiState.value.waypoints.any { it.id == waypoint.id } }
        assertEquals("positive control: a draft keeps nothing yet", 0, screen.waypointCount())

        finishThroughJournal(journal, "entry-wp", decide = { it.onSetWaypointDecision(waypoint.id, kept = true) }, saved = { e -> e.waypointDecisions.single().kept })
        idleUntil { screen.waypointCount() == 1 }

        assertEquals("the waypoint's Used in does not count the entry just finished", 1, screen.waypointCount())
        screen.requestRemoveWaypoint(waypoint.id)
        assertEquals("the delete message does not count the entry just finished", 1, screen.uiState.value.pendingWaypointDelete?.entryReferenceCount)
    }

    @Test
    fun `deleting the one entry that keeps a track takes its delete message's count to 0`() {
        seedTrack()
        save(committed("entry-track").copy(trackDecisions = listOf(trackDecision(kept = true))))
        val screen = trackScreen()
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the track is listed and counted in 1 entry") {
            screen.uiState.value.tracks.any { it.id == track.id } && screen.trackCount() == 1
        }

        deleteThroughJournal(journal, "entry-track")
        idleUntil { screen.trackCount() == 0 }

        assertEquals("the track's count still includes the deleted entry", 0, screen.trackCount())
        screen.requestRemoveTrack(track.id)
        assertEquals("the track's delete message still says used in the deleted entry", 0, screen.uiState.value.pendingTrackDelete?.entryReferenceCount)
    }

    @Test
    fun `finishing an entry that keeps an already-listed track takes its delete message's count to 1`() {
        seedTrack()
        save(draft("entry-track").copy(trackDecisions = listOf(trackDecision(kept = false))))
        val screen = trackScreen()
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the track is listed") { screen.uiState.value.tracks.any { it.id == track.id } }
        assertEquals("positive control: a draft keeps nothing yet", 0, screen.trackCount())

        finishThroughJournal(journal, "entry-track", decide = { it.onSetTrackDecision(track.id, kept = true) }, saved = { e -> e.trackDecisions.single().kept })
        idleUntil { screen.trackCount() == 1 }

        assertEquals("the track's count does not include the entry just finished", 1, screen.trackCount())
        screen.requestRemoveTrack(track.id)
        assertEquals("the track's delete message does not count the entry just finished", 1, screen.uiState.value.pendingTrackDelete?.entryReferenceCount)
    }
}
