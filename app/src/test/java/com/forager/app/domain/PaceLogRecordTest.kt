package com.forager.app.domain

import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [toLogRecord] on hand-built tracks — Pass 2 instrumentation. The point geometry is
 * [MovingPaceTest]'s (due north, 0.000135° every 15 s: 15.011336 m per step, 1.0007557 m/s implied;
 * [northPoints] is a copy of that class's private builder, not a widening of it), so every expected
 * figure below is the one that class already works by hand; what this class adds
 * is the *line*: which keys, in which order, which fields carry the literal `null` and when, and the
 * three identities a parser checks. Numeric doubles are compared by parsing the printed token, never
 * by re-formatting the pace — the claim is that the token round-trips to the value, which is the
 * replay contract.
 */
class PaceLogRecordTest {

    @Test
    fun `a pre-migration track prints the differencing side, the literal null on the Doppler trio, and every key in order`() {
        val track = track(northPoints(count = 21), excludedPointCount = 3)

        val line = movingPace(track.points).toLogRecord(track, call = 4, atEpochMillis = 1_789_000_000_000L, mode = TrackRecordingMode.HIGH_ACCURACY)
        val fields = parse(line)

        assertEquals(EXPECTED_KEYS, fields.keys.toList())
        assertEquals("1", fields["v"])
        assertEquals("track-a", fields["track"])
        assertEquals("4", fields["call"])
        assertEquals("1789000000000", fields["at"])
        assertEquals("HIGH_ACCURACY", fields["mode"])
        assertEquals("21", fields["points"])
        assertEquals("24", fields["stored"])
        assertEquals("3", fields["excluded"])
        assertEquals("1700000000000", fields["first"])
        assertEquals("1700000300000", fields["last"])
        assertEquals("300000", fields["wallMs"])
        assertEquals("20", fields["examined"])
        assertEquals("20", fields["moving"])
        assertEquals("300000", fields["movingMs"])
        assertEquals(300.22672, fields.getValue("movingM").toDouble(), 0.001)
        assertEquals(1.0007557, fields.getValue("diffAll").toDouble(), 1e-6)
        assertEquals("0", fields["dopplerMs"])
        assertEquals("0.0", fields["dopplerM"])
        // The trio is null together: no counted sample, no comparison.
        assertEquals("null", fields["doppler"])
        assertEquals("null", fields["diffSame"])
        assertEquals("null", fields["ratio"])
        assertEquals("0", fields["counted"])
        assertEquals("20", fields["nullSpeed"])
        assertEquals("0", fields["belowFloor"])
        assertEquals("false", fields["dopplerBar"])
        assertEquals("true", fields["diffBar"])
        assertEquals("DIFFERENCING", fields["source"])
        assertEquals("0.5", fields["floor"])
        assertEquals("300000", fields["barMs"])
        assertIdentitiesHold(fields)
    }

    @Test
    fun `with Doppler on every point the trio is printed, the ratio is Doppler over differencing, and both bars clear`() {
        val track = track(northPoints(count = 21, speed = 0.9f))

        val fields = parse(movingPace(track.points).toLogRecord(track, call = 1, atEpochMillis = 0L, mode = TrackRecordingMode.BALANCED))

        assertEquals("BALANCED", fields["mode"])
        assertEquals("300000", fields["dopplerMs"])
        assertEquals(300.22672, fields.getValue("dopplerM").toDouble(), 0.001)
        assertEquals(0.9, fields.getValue("doppler").toDouble(), 1e-6)
        assertEquals(1.0007557, fields.getValue("diffSame").toDouble(), 1e-6)
        assertEquals(0.89932, fields.getValue("ratio").toDouble(), 1e-4) // 0.9 ÷ 1.0007557
        assertEquals("20", fields["counted"])
        assertEquals("0", fields["nullSpeed"])
        assertEquals("true", fields["dopplerBar"])
        assertEquals("true", fields["diffBar"])
        assertEquals("DOPPLER", fields["source"])
        assertIdentitiesHold(fields)
    }

    /**
     * The disagreement the comparison exists to surface, and the reason both bars are printed
     * rather than `source` alone: 40 intervals of differencing (600 s, past the bar), Doppler on the
     * last ten (150 s, short of it). `source` says `DIFFERENCING`; only the two booleans say why.
     */
    @Test
    fun `partial Doppler coverage prints differencing past the bar and Doppler short of it, with the counts that explain it`() {
        val noSpeed = northPoints(count = 31)
        val withSpeed = northPoints(count = 10, speed = 0.9f, startLat = 45.0 + 31 * 0.000135, startMillis = 1_700_000_000_000L + 31 * 15_000L)
        val track = track(noSpeed + withSpeed)

        val fields = parse(movingPace(track.points).toLogRecord(track, call = 9, atEpochMillis = 0L, mode = TrackRecordingMode.HIGH_ACCURACY))

        assertEquals("41", fields["points"])
        assertEquals("40", fields["examined"])
        assertEquals("40", fields["moving"])
        assertEquals("600000", fields["movingMs"])
        assertEquals("150000", fields["dopplerMs"])
        assertEquals("10", fields["counted"])
        assertEquals("30", fields["nullSpeed"])
        assertEquals("0", fields["belowFloor"])
        assertEquals("true", fields["diffBar"])
        assertEquals("false", fields["dopplerBar"])
        assertEquals("DIFFERENCING", fields["source"])
        assertEquals(0.9, fields.getValue("doppler").toDouble(), 1e-6)
        assertIdentitiesHold(fields)
    }

    /**
     * A ten-minute stop: the point that ends it is examined as an interval and skipped as stopped,
     * so it is in none of the three sample counts — `examined - moving` is where it shows.
     */
    @Test
    fun `a stopped interval counts as examined but not moving, and the sample counts still sum to moving`() {
        val walking = northPoints(count = 21)
        val afterStop = walking.last().let { it.copy(lat = it.lat + 0.000135, timestampEpochMillis = it.timestampEpochMillis + 600_000L) }
        val track = track(walking + afterStop)

        val fields = parse(movingPace(track.points).toLogRecord(track, call = 2, atEpochMillis = 0L, mode = TrackRecordingMode.HIGH_ACCURACY))

        assertEquals("22", fields["points"])
        assertEquals("21", fields["examined"])
        assertEquals("20", fields["moving"])
        assertEquals("300000", fields["movingMs"]) // the stop's 600 s is not moving time
        assertEquals("900000", fields["wallMs"]) // but it is wall clock
        assertIdentitiesHold(fields)
    }

    @Test
    fun `an empty track prints null for the timestamps and every average, zero counts, and the default source`() {
        val track = track(emptyList())

        val fields = parse(movingPace(track.points).toLogRecord(track, call = 1, atEpochMillis = 5L, mode = TrackRecordingMode.BATTERY_SAVER))

        assertEquals(EXPECTED_KEYS, fields.keys.toList())
        assertEquals("0", fields["points"])
        assertEquals("null", fields["first"])
        assertEquals("null", fields["last"])
        assertEquals("null", fields["wallMs"])
        assertEquals("0", fields["examined"])
        assertEquals("0", fields["moving"])
        assertEquals("0.0", fields["movingM"])
        assertEquals("null", fields["diffAll"])
        assertEquals("null", fields["doppler"])
        assertEquals("null", fields["diffSame"])
        assertEquals("null", fields["ratio"])
        assertEquals("false", fields["dopplerBar"])
        assertEquals("false", fields["diffBar"])
        assertEquals("DEFAULT", fields["source"])
        assertIdentitiesHold(fields)
    }

    @Test
    fun `the line is one line with no free text, and null appears only where a field is declared nullable`() {
        val track = track(northPoints(count = 21))
        val line = movingPace(track.points).toLogRecord(track, call = 1, atEpochMillis = 0L, mode = TrackRecordingMode.HIGH_ACCURACY)

        assertTrue(line, '\n' !in line)
        assertTrue(line, line.split(' ').all { token -> token.count { it == '=' } == 1 })
        val nullFields = parse(line).filterValues { it == "null" }.keys
        assertTrue(nullFields.toString(), nullFields.all { it in NULLABLE_KEYS })
    }

    private fun track(points: List<TrackPoint>, excludedPointCount: Int = 0) = Track(
        id = "track-a",
        name = null,
        startedAtEpochMillis = 1_699_999_990_000L,
        endedAtEpochMillis = null,
        points = points,
        excludedPointCount = excludedPointCount,
    )

    private fun parse(line: String): Map<String, String> =
        line.split(' ').associate { token -> token.substringBefore('=') to token.substringAfter('=') }
            .also { assertEquals("every key exactly once", line.split(' ').size, it.size) }

    private fun assertIdentitiesHold(fields: Map<String, String>) {
        val points = fields.getValue("points").toInt()
        val examined = fields.getValue("examined").toInt()
        val moving = fields.getValue("moving").toInt()
        assertEquals("points - 1 = examined", (points - 1).coerceAtLeast(0), examined)
        assertEquals(
            "moving = counted + nullSpeed + belowFloor",
            moving,
            fields.getValue("counted").toInt() + fields.getValue("nullSpeed").toInt() + fields.getValue("belowFloor").toInt(),
        )
        assertTrue("moving <= examined", moving <= examined)
    }

    private companion object {
        const val LNG = -122.0

        fun northPoints(count: Int, speed: Float? = null, startLat: Double = 45.0, startMillis: Long = 1_700_000_000_000L): List<TrackPoint> =
            (0 until count).map { i ->
                TrackPoint(
                    lat = startLat + i * 0.000135,
                    lng = LNG,
                    altitude = null,
                    accuracyMeters = null,
                    timestampEpochMillis = startMillis + i * 15_000L,
                    speedMetersPerSecond = speed,
                )
            }

        val EXPECTED_KEYS = listOf(
            "v", "track", "call", "at", "mode",
            "points", "stored", "excluded", "first", "last", "wallMs",
            "examined", "moving", "movingMs", "movingM", "diffAll",
            "dopplerMs", "dopplerM", "doppler", "diffSame", "ratio",
            "counted", "nullSpeed", "belowFloor", "dopplerBar", "diffBar", "source",
            "floor", "barMs",
        )
        val NULLABLE_KEYS = setOf("first", "last", "wallMs", "diffAll", "doppler", "diffSame", "ratio")
    }
}
