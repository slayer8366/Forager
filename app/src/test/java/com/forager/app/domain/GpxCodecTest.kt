package com.forager.app.domain

import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackPointRecord
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxCodecTest {

    @Test
    fun `a track round-trips through encode and decode with lat, lng, altitude, and timestamp intact`() {
        val track = Track(
            id = "ignored-on-decode",
            name = "Ridge loop",
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 5_000L,
            points = listOf(
                TrackPoint(lat = 45.501, lng = -122.601, altitude = 312.5, accuracyMeters = 5f, timestampEpochMillis = 1_000L),
                TrackPoint(lat = 45.502, lng = -122.602, altitude = null, accuracyMeters = 8f, timestampEpochMillis = 2_000L),
            ),
        )

        val decoded = GpxCodec.decode(GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList())))

        assertEquals("Ridge loop", decoded.track?.name)
        assertEquals(2, decoded.track?.points?.size)
        val firstDecoded = decoded.track!!.points[0]
        assertEquals(45.501, firstDecoded.lat, 1e-6)
        assertEquals(-122.601, firstDecoded.lng, 1e-6)
        assertEquals(312.5, firstDecoded.altitude!!, 1e-6)
        assertEquals(1_000L, firstDecoded.timestampEpochMillis)
        // Accuracy is not part of the GPX schema this app writes — see GpxCodec's doc comment —
        // so it's expected to come back null, not silently fabricated from the original value.
        assertNull(firstDecoded.accuracyMeters)
        assertNull(decoded.track!!.points[1].altitude)
    }

    @Test
    fun `a waypoint round-trips through encode and decode with name and note intact`() {
        val waypoint = Waypoint(id = "ignored", lat = 45.1, lng = -122.1, altitude = 250.0, name = "Trailhead & Parking", note = "Gravel lot, room for 3 cars", createdAtEpochMillis = 4_000L)

        val decoded = GpxCodec.decode(GpxCodec.encode(GpxDocument(track = null, waypoints = listOf(waypoint))))

        assertNull(decoded.track)
        assertEquals(1, decoded.waypoints.size)
        val decodedWaypoint = decoded.waypoints.first()
        assertEquals("Trailhead & Parking", decodedWaypoint.name)
        assertEquals("Gravel lot, room for 3 cars", decodedWaypoint.note)
        assertEquals(45.1, decodedWaypoint.lat, 1e-6)
        assertEquals(250.0, decodedWaypoint.altitude!!, 1e-6)
        assertEquals(4_000L, decodedWaypoint.createdAtEpochMillis)
    }

    @Test
    fun `special characters in names are escaped and survive the round trip`() {
        val track = Track(id = "t", name = "A <tricky> & \"quoted\" name", startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())

        val encoded = GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList()))
        assertTrue("encoded XML must not contain a raw '<' from the name", !encoded.substringAfter("<name>").substringBefore("</name>").contains("<tricky"))

        val decoded = GpxCodec.decode(encoded)
        assertEquals("A <tricky> & \"quoted\" name", decoded.track?.name)
    }

    @Test
    fun `a document with no track decodes with a null track`() {
        val decoded = GpxCodec.decode(GpxCodec.encode(GpxDocument(track = null, waypoints = emptyList())))

        assertNull(decoded.track)
        assertTrue(decoded.waypoints.isEmpty())
    }

    /**
     * GPX full-record export dispatch, format B1 (owner ruling): the raw sequence in
     * `<trk><extensions>`, full millisecond timestamp and every optional field, each point's
     * kept/excluded verdict intact, and the block's own authoritative declaration present.
     */
    @Test
    fun `the full record round-trips through encode and decode with every field and verdict intact`() {
        val track = Track(id = "ignored-on-decode", name = "Loop", startedAtEpochMillis = 1_000L, endedAtEpochMillis = 11_000L, points = listOf(TrackPoint(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L)))
        val fullRecord = listOf(
            TrackPointRecord(
                point = TrackPoint(lat = 45.000, lng = -122.0, altitude = 97.5, accuracyMeters = 8f, timestampEpochMillis = 1_000L, speedMetersPerSecond = 1.2f, speedAccuracyMetersPerSecond = 0.3f),
                kept = true,
            ),
            TrackPointRecord(
                point = TrackPoint(lat = 45.030, lng = -122.0, altitude = 111.6, accuracyMeters = 20f, timestampEpochMillis = 3_500L),
                kept = false,
            ),
        )

        val encoded = GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList(), fullRecord = fullRecord))
        assertTrue("the block must declare itself authoritative in-band, not in a comment", encoded.contains("authoritative=\"true\""))
        val decoded = GpxCodec.decode(encoded)

        assertEquals(fullRecord, decoded.fullRecord)
    }

    @Test
    fun `an empty full record encodes no extensions block and decodes back to empty`() {
        val track = Track(id = "t", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = emptyList())

        val encoded = GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList()))
        assertFalse(encoded.contains("<extensions>"))
        assertTrue(GpxCodec.decode(encoded).fullRecord.isEmpty())
    }

    /** GPX has no element for [Waypoint.trackId]/[Waypoint.designation] — carried in the same `forager:` extension the full record uses, and round-trips the same way. */
    @Test
    fun `a waypoint's track link and designation round-trip through the forager extension`() {
        val waypoint = Waypoint(id = "ignored", lat = 45.1, lng = -122.1, altitude = null, name = "Start", note = "", createdAtEpochMillis = 1_000L, trackId = "t1", designation = WaypointDesignation.ORIGIN)

        val decoded = GpxCodec.decode(GpxCodec.encode(GpxDocument(track = null, waypoints = listOf(waypoint))))

        assertEquals("t1", decoded.waypoints.single().trackId)
        assertEquals(WaypointDesignation.ORIGIN, decoded.waypoints.single().designation)
    }

    /**
     * The `<extensions>` block used to be omitted entirely for a waypoint with no track link. It no
     * longer can be: [Waypoint.id] is never absent, so it is always written (GPX rule-provenance
     * dispatch, 2026-09-09, §3). What this still pins is the part that did not change — `trackId`
     * and `designation` are each omitted rather than written empty, and decode reports `null`.
     */
    @Test
    fun `an ordinary waypoint with no track link carries only its id in the extensions block`() {
        val waypoint = Waypoint(id = "wp-ordinary", lat = 45.1, lng = -122.1, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = 1_000L)

        val encoded = GpxCodec.encode(GpxDocument(track = null, waypoints = listOf(waypoint)))

        assertEquals(" id=\"wp-ordinary\"", encoded.substringAfter("<forager:waypoint").substringBefore("/>"))
        val decoded = GpxCodec.decode(encoded).waypoints.single()
        assertEquals("wp-ordinary", decoded.id)
        assertNull(decoded.trackId)
        assertNull(decoded.designation)
    }

    /**
     * [Waypoint.id] round-trips — GPX rule-provenance dispatch §3. The data was always present and
     * simply was not exported, so a waypoint in an exported file could not be matched back to the
     * row it came from, nor to itself in a second export of the same trip.
     */
    @Test
    fun `a waypoint's id survives the round trip rather than being regenerated`() {
        val waypoint = Waypoint(id = "b3f1 & <odd> \"quoted\"", lat = 45.1, lng = -122.1, altitude = null, name = "Start", note = "", createdAtEpochMillis = 1_000L, trackId = "t1", designation = WaypointDesignation.ORIGIN)

        val decoded = GpxCodec.decode(GpxCodec.encode(GpxDocument(track = null, waypoints = listOf(waypoint)))).waypoints.single()

        assertEquals("b3f1 & <odd> \"quoted\"", decoded.id)
        assertEquals("t1", decoded.trackId)
        assertEquals(WaypointDesignation.ORIGIN, decoded.designation)
    }

    /** A file from outside this app carries no id to restore — a fresh one, never a blank string. */
    @Test
    fun `a waypoint from a file with no forager extension gets a fresh id, not an empty one`() {
        val foreign = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Elsewhere" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="45.1" lon="-122.1"><name>Somewhere</name></wpt>
            </gpx>
        """.trimIndent()

        val decoded = GpxCodec.decode(foreign).waypoints.single()

        assertTrue("an id is fabricated only where the file carries none", decoded.id.isNotBlank())
        assertEquals("Somewhere", decoded.name)
    }

    /**
     * GPX rule-provenance dispatch §2 (owner ruling, 2026-09-09): the rule set in force on the
     * record block, and the rule that caught each excluded point on the point itself. The literal
     * `timestampMillisNonZero` is written out here by hand rather than read from
     * [NETWORK_FIX_EXCLUSION_RULES] — an expectation read from the thing under test passes for
     * whatever value that thing happens to hold.
     */
    @Test
    fun `the full record declares its rule set and names the rule on excluded points only`() {
        val track = Track(id = "t", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = 3_500L, points = emptyList())
        val fullRecord = listOf(
            TrackPointRecord(point = TrackPoint(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L), kept = true),
            TrackPointRecord(
                point = TrackPoint(lat = 45.030, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 3_500L),
                kept = false,
                excludedByRule = "timestampMillisNonZero",
            ),
        )

        val encoded = GpxCodec.encode(
            GpxDocument(track = track, waypoints = emptyList(), fullRecord = fullRecord, exclusionRules = listOf("timestampMillisNonZero")),
        )

        val recordBlockTag = encoded.substringAfter("<forager:fullRecord").substringBefore(">")
        assertTrue("the block must declare the rule set in force, got:$recordBlockTag", recordBlockTag.contains("rule=\"timestampMillisNonZero\""))
        val points = Regex("<forager:point [^>]*/>").findAll(encoded).map { it.value }.toList()
        assertEquals(2, points.size)
        assertTrue(points[0].contains("kept=\"true\""))
        assertFalse("a kept point passed everything and has no rule to name", points[0].contains("excludedByRule"))
        assertTrue(points[1].contains("excludedByRule=\"timestampMillisNonZero\""))

        val decoded = GpxCodec.decode(encoded)
        assertEquals(listOf("timestampMillisNonZero"), decoded.exclusionRules)
        assertEquals(fullRecord, decoded.fullRecord)
    }

    /**
     * The case §1 of that dispatch exists to make legible: a file this app wrote **before** `rule`
     * existed. Which rule excluded its points is genuinely unrecoverable, so decode reports no
     * declared rule set and no per-point rule rather than filling in this build's. The verdict
     * survives; the fabrication does not happen.
     */
    @Test
    fun `a full record written before the rule attribute decodes with no rules, not this build's`() {
        val olderFile = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Forager" xmlns="http://www.topografix.com/GPX/1/1" xmlns:forager="https://forager.app/gpx/1">
              <trk>
                <extensions>
                  <forager:fullRecord authoritative="true" trksegDerivedFromFullRecord="true" pointCount="1">
                    <forager:point lat="45.030" lon="-122.0" timeEpochMillis="3500" kept="false"/>
                  </forager:fullRecord>
                </extensions>
                <trkseg></trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val decoded = GpxCodec.decode(olderFile)

        assertEquals(emptyList<String>(), decoded.exclusionRules)
        val record = decoded.fullRecord.single()
        assertFalse("the verdict itself is still readable", record.kept)
        assertNull("which rule produced it is not, and must not be guessed", record.excludedByRule)
    }

    /** A document declaring no rule set writes no attribute — not an empty one reading as "no rules ran". */
    @Test
    fun `a document with no declared rule set writes no rule attribute at all`() {
        val track = Track(id = "t", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList())
        val fullRecord = listOf(
            TrackPointRecord(point = TrackPoint(lat = 45.000, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L), kept = true),
        )

        val encoded = GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList(), fullRecord = fullRecord))

        assertFalse(encoded.substringAfter("<forager:fullRecord").substringBefore(">").contains("rule="))
        assertTrue(GpxCodec.decode(encoded).exclusionRules.isEmpty())
    }
}
