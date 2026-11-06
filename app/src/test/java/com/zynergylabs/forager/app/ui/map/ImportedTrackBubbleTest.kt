package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan T16, "Import, show "No times"" (RECORD -636): an imported track whose file had no times shows
 * "No times in file" in its map bubble where the duration would be; one with times, and a recorded walk,
 * show their duration as before. Through [mapBubbleContentFor], the function the Maps tab's bubble calls.
 */
class ImportedTrackBubbleTest {

    private val points = listOf(TrackPoint(45.5, -122.6, null, null, 0L), TrackPoint(45.6, -122.6, null, null, 3_600_000L))

    private fun durationOf(track: Track): String {
        val content = mapBubbleContentFor(
            MapBubbleTarget.FeatureTarget(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, track.id, LatLng(45.5, -122.6)),
            MapRecordSources(tracks = listOf(track)),
            is24HourClock = false,
        ) as MapBubbleContent.TrackContent
        return content.duration
    }

    @Test
    fun `an imported track with no times says so where the duration would be`() {
        assertEquals("No times in file", durationOf(Track("imp", "Plan", 0L, 3_600_000L, points, importedAtEpochMillis = 1L, importedWithoutTimes = true)))
    }

    @Test
    fun `an imported track with times, and a recorded walk, show their duration`() {
        assertEquals("1h 0m", durationOf(Track("imp", "Imported", 0L, 3_600_000L, points, importedAtEpochMillis = 1L)))
        assertEquals("1h 0m", durationOf(Track("rec", null, 0L, 3_600_000L, points)))
    }
}
