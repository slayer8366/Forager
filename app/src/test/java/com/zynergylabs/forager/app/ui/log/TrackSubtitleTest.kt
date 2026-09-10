package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [trackSubtitle] — the one formatter behind the three track-length row sites (track-distance-label
 * dispatch). Expected strings come from the owner's captured version-14 database and hand
 * arithmetic, never from the formatter: track B stored 91.976 m (26 points), which the old
 * kilometre-rounding label printed as "0 mi" and which is 91.976 × 3.28084 = 301.76 → "302 ft";
 * track A stored 732.99 m, printed "1 mi", and is 732.99 / 1609.344 = 0.4555 → "0.5 mi"; the
 * pulse's unfiltered sum 1957.4 m, also "1 mi" before, is 1.2163 mi → "1.2 mi" and 1.957 km →
 * "2.0 km". Metric below a kilometre is the rounded metre count.
 *
 * The sub-500 m case is the one that matters: a test with only kilometre-scale values passes under
 * both the old and the new formatter for track A and would never have seen the "0 mi" — the family
 * CLAUDE.md names (a check that passes because it never saw the data that could fail it).
 */
class TrackSubtitleTest {

    @Test
    fun `a 92 m track reads as feet, never as zero miles`() {
        assertEquals("302 ft · 4m", trackSubtitle(91.976, durationMillis = 4L * 60_000L, distanceUnit = DistanceUnit.MILES))
        assertEquals("92 m · 4m", trackSubtitle(91.976, durationMillis = 4L * 60_000L, distanceUnit = DistanceUnit.KILOMETERS))
    }

    @Test
    fun `a 733 m track reads in tenths of a mile, not a whole mile`() {
        assertEquals("0.5 mi · 22m", trackSubtitle(732.9925470825974, durationMillis = 22L * 60_000L, distanceUnit = DistanceUnit.MILES))
        assertEquals("733 m · 22m", trackSubtitle(732.9925470825974, durationMillis = 22L * 60_000L, distanceUnit = DistanceUnit.KILOMETERS))
    }

    @Test
    fun `a track in the band that used to collapse to one mile reads its own length`() {
        assertEquals("1.2 mi · 1h 5m", trackSubtitle(1957.4, durationMillis = 65L * 60_000L, distanceUnit = DistanceUnit.MILES))
        assertEquals("2.0 km · 1h 5m", trackSubtitle(1957.4, durationMillis = 65L * 60_000L, distanceUnit = DistanceUnit.KILOMETERS))
    }
}
