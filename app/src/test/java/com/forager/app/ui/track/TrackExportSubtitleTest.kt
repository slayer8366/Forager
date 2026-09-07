package com.forager.app.ui.track

import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/** Records' row subtitle — ordinary tracks read exactly as before; only a large exclusion says what was left out (timestamp-filter dispatch, Item 3). */
class TrackExportSubtitleTest {
    private fun point(t: Long) = TrackPoint(lat = 45.0, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = t)
    private fun track(points: Int, excluded: Int, ended: Boolean = true) = Track(
        id = "t",
        name = null,
        startedAtEpochMillis = 0L,
        endedAtEpochMillis = if (ended) 60_000L else null,
        points = List(points) { point(1_000L * it) },
        excludedPointCount = excluded,
    )

    @Test
    fun `an ordinary track, including one the rule quietly cleaned, reads as before`() {
        assertEquals("9 points", trackSubtitle(track(points = 9, excluded = 8)))
        assertEquals("1 point", trackSubtitle(track(points = 1, excluded = 0)))
        assertEquals("0 points · recording", trackSubtitle(track(points = 0, excluded = 0, ended = false)))
    }

    @Test
    fun `a large exclusion appends what was not shown`() {
        assertEquals("1 point · 12 more not shown (network fixes)", trackSubtitle(track(points = 1, excluded = 12)))
        assertEquals("6 points · 19 more not shown (network fixes) · recording", trackSubtitle(track(points = 6, excluded = 19, ended = false)))
    }

    @Test
    fun `an emptied track says so instead of counting zero`() {
        assertEquals("No usable points — all 12 fixes were from the network provider", trackSubtitle(track(points = 0, excluded = 12)))
    }
}
