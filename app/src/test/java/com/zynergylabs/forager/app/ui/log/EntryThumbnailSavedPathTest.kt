package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F3 (dispatch 2026-09-28-195, item 5; owner, 2026-09-29, "C: list screen loads lazily"): which entries
 * need a saved path for their card's thumbnail, and how the thumbnail's tracks are chosen once they have
 * it. Pure functions, no Compose.
 */
class EntryThumbnailSavedPathTest {

    private fun live(id: String) = Track(
        id = id, name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = 2_000L,
        points = listOf(
            TrackPoint(lat = 1.0, lng = 1.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L),
            TrackPoint(lat = 1.1, lng = 1.1, altitude = null, accuracyMeters = null, timestampEpochMillis = 2_000L),
        ),
    )

    private fun decision(id: String, kept: Boolean = true) = TrackDecision(trackId = id, name = "Walk $id", distanceMeters = 1.0, durationMillis = 1L, pointCount = 2, kept = kept)

    private fun entry(id: String, vararg decisions: TrackDecision) =
        CartographyEntry.draft(id = id, date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 0L).copy(isDraft = false, trackDecisions = decisions.toList())

    private val savedA = listOf(LatLng(45.0, -122.0), LatLng(45.001, -122.002))
    private val savedB = listOf(LatLng(46.0, -123.0), LatLng(46.001, -123.002), LatLng(46.002, -123.004))

    @Test
    fun `an entry needs a saved path only when a kept track is missing from the loaded tracks`() {
        val tracksById = mapOf("live-1" to live("live-1"))
        val entries = listOf(
            entry("all-live", decision("live-1")),
            entry("one-gone", decision("live-1"), decision("gone-1")),
            entry("withheld-gone", decision("gone-2", kept = false)),
            entry("no-tracks"),
        )

        assertEquals(listOf("one-gone"), entriesNeedingSavedPaths(entries, tracksById))
    }

    @Test
    fun `a kept track that is loaded draws live, and its saved path is ignored`() {
        val tracksById = mapOf("tr-a" to live("tr-a"))

        val tracks = entryThumbnailTracksOrSaved(entry("e", decision("tr-a")), tracksById, savedPaths = mapOf("tr-a" to savedA))

        assertEquals(listOf(live("tr-a")), tracks)
    }

    @Test
    fun `a kept track that is gone draws its saved path as a track with its id, in decision order among live ones`() {
        val tracksById = mapOf("tr-b" to live("tr-b"))

        val tracks = entryThumbnailTracksOrSaved(entry("e", decision("tr-a"), decision("tr-b"), decision("tr-c")), tracksById, savedPaths = mapOf("tr-a" to savedA, "tr-c" to savedB))

        assertEquals(listOf("tr-a", "tr-b", "tr-c"), tracks.map { it.id })
        assertEquals(savedA, tracks[0].points.map { LatLng(it.lat, it.lng) })
        assertEquals(live("tr-b"), tracks[1])
        assertEquals(savedB, tracks[2].points.map { LatLng(it.lat, it.lng) })
    }

    @Test
    fun `a withheld track's saved path is not drawn`() {
        val tracks = entryThumbnailTracksOrSaved(entry("e", decision("tr-a", kept = false)), tracksById = emptyMap(), savedPaths = mapOf("tr-a" to savedA))

        assertEquals(emptyList<Track>(), tracks)
    }

    @Test
    fun `a kept track that is gone and has no saved path is left out, and the rest still draw`() {
        val tracksById = mapOf("tr-b" to live("tr-b"))

        val tracks = entryThumbnailTracksOrSaved(entry("e", decision("tr-a"), decision("tr-b")), tracksById, savedPaths = emptyMap())

        assertEquals(listOf(live("tr-b")), tracks)
    }
}
