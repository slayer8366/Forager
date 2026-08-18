package com.forager.app.domain

import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
import org.junit.Assert.assertEquals
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
}
