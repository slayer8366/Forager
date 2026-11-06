package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import com.zynergylabs.forager.app.ui.format.displayDateTime

/**
 * The track sheet's figures (dispatch 2026-09-28-677), headless: every tile's label and value, the cases that have no figure,
 * and the sheet's clock. The walk: three points 0.01° of latitude apart (2223.9 m), 35 minutes apart (70 min), climbing 30 m
 * then dropping 12 m (both past the 4 m hysteresis).
 */
class TrackSheetTilesTest {

    private val t0 = 1_790_000_000_000L
    private val minute = 60_000L

    private fun track(importedWithoutTimes: Boolean = false, altitudes: List<Double?> = listOf(100.0, 130.0, 118.0), spacingMillis: Long = 35 * minute) = Track(
        id = "t",
        name = "Ridge",
        startedAtEpochMillis = t0,
        endedAtEpochMillis = t0 + 2 * spacingMillis,
        points = altitudes.mapIndexed { i, altitude ->
            TrackPoint(lat = 45.0 + i * 0.01, lng = -122.0, altitude = altitude, accuracyMeters = 5f, timestampEpochMillis = t0 + i * spacingMillis)
        },
        importedAtEpochMillis = if (importedWithoutTimes) t0 else null,
        importedWithoutTimes = importedWithoutTimes,
    )

    private fun tiles(track: Track, unit: DistanceUnit = DistanceUnit.MILES) =
        trackSheetTiles(track, ComputeTrackStatisticsUseCase()(track.points), unit)

    @Test
    fun `the five tiles in order, each labelled, in miles and feet`() {
        // 2223.9 m = 1.38 mi; 30 m = 98 ft; 12 m = 39 ft. Both intervals move (1111.95 m in 2100 s, 0.5295 m/s, past the
        // 0.5 m/s floor), 70 min of moving time: 0.5295 m/s = 1.18 mph.
        assertEquals(
            listOf("Distance" to "1.4 mi", "Time" to "1 h 10 min", "Climb" to "98 ft", "Descent" to "39 ft", "Moving speed" to "1.2 mph"),
            tiles(track()),
        )
    }

    @Test
    fun `in kilometres and metres`() {
        // 0.5295 m/s = 1.906 km/h.
        assertEquals(
            listOf("Distance" to "2.2 km", "Time" to "1 h 10 min", "Climb" to "30 m", "Descent" to "12 m", "Moving speed" to "1.9 km/h"),
            tiles(track(), DistanceUnit.KILOMETERS),
        )
    }

    @Test
    fun `no heights read Not recorded, not zero`() {
        val values = tiles(track(altitudes = listOf(null, null, null))).toMap()
        assertEquals("Not recorded", values["Climb"])
        assertEquals("Not recorded", values["Descent"])
    }

    @Test
    fun `a file with no times has no Time and no Moving speed, and says so`() {
        val values = tiles(track(importedWithoutTimes = true)).toMap()
        assertEquals("No times in file", values["Time"])
        assertEquals("No times in file", values["Moving speed"])
        assertEquals("the distance does not need times", "1.4 mi", values["Distance"])
    }

    @Test
    fun `points that share one time have no speed, a dash`() {
        val values = tiles(track(spacingMillis = 0L)).toMap()
        assertEquals("—", values["Moving speed"])
        assertEquals("Under 1 min", values["Time"])
    }

    /**
     * Amendment 1 (RECORD -680; the owner: "Moving speed (Recommended)"): a long stop does not slow the figure. 1111.95 m in
     * 10 min, an hour stopped, 1111.95 m in 10 min: moving, 2223.9 m in 20 min, 1.853 m/s = 4.15 mph. Distance over the
     * whole 80 min would read 1.0 mph; that is the figure this replaced.
     */
    @Test
    fun `a walk with a long stop reads its moving speed, not distance over the whole time`() {
        val points = listOf(0L to 45.00, 10L to 45.01, 70L to 45.01, 80L to 45.02).map { (atMinute, lat) ->
            TrackPoint(lat = lat, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t0 + atMinute * minute)
        }
        val stopped = Track(id = "s", name = "Stop", startedAtEpochMillis = t0, endedAtEpochMillis = t0 + 80 * minute, points = points)
        val values = tiles(stopped).toMap()
        assertEquals("4.1 mph", values["Moving speed"])
        assertEquals("the time is still the whole walk", "1 h 20 min", values["Time"])
    }

    @Test
    fun `under five minutes of moving there is no figure, not the walk-back estimate's assumed pace`() {
        val values = tiles(track(spacingMillis = 2 * minute)).toMap()
        assertEquals("—", values["Moving speed"])
    }

    @Test
    fun `the heights line counts the points that carry one`() {
        assertEquals("2 of 3 points", heightsRecordedLabel(2, 3))
    }

    @Test
    fun `the sheet's clock follows the 12- or 24-hour setting, with the date as Oct 7, 2026`() {
        val at = Instant.parse("2026-10-07T14:05:00Z").toEpochMilli()
        assertEquals("Oct 7, 2026, 2:05 PM", displayDateTime(at, is24HourClock = false, zone = ZoneOffset.UTC))
        assertEquals("Oct 7, 2026, 14:05", displayDateTime(at, is24HourClock = true, zone = ZoneOffset.UTC))
    }
}
