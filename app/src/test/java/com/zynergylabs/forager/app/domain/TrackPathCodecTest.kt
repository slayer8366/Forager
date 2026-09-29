package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * F3 (dispatch 2026-09-28-195, item 1): the codec that turns a track's path into the `path` column of
 * `cartography_entry_track_paths` and back. Pure Kotlin, no Android, no database. Every assertion is on the
 * decoded values or the exact byte count, not on "something was encoded".
 */
class TrackPathCodecTest {

    private val ridge = listOf(
        LatLng(45.2000000001, -122.5000000002),
        LatLng(45.2001, -122.4999),
        LatLng(-33.868820, 151.209290),
        LatLng(90.0, 180.0),
        LatLng(-90.0, -180.0),
        LatLng(0.0, 0.0),
        LatLng(1e-9, -1e-9),
    )

    @Test
    fun `a path round-trips exactly, in order, every coordinate identical`() {
        assertEquals(ridge, TrackPathCodec.decode(TrackPathCodec.encode(ridge)))
    }

    @Test
    fun `an empty path round-trips as an empty path and encodes to no bytes`() {
        val bytes = TrackPathCodec.encode(emptyList())

        assertEquals(0, bytes.size)
        assertEquals(emptyList<LatLng>(), TrackPathCodec.decode(bytes))
    }

    @Test
    fun `one point round-trips and takes sixteen bytes, two doubles`() {
        val one = listOf(LatLng(45.52, -122.68))

        val bytes = TrackPathCodec.encode(one)

        assertEquals(16, bytes.size)
        assertEquals(one, TrackPathCodec.decode(bytes))
    }

    @Test
    fun `sixteen bytes a point, so the size is the point count times sixteen`() {
        assertEquals(ridge.size * 16, TrackPathCodec.encode(ridge).size)
    }

    @Test
    fun `negative zero survives, so the round trip is bit for bit and not merely numerically equal`() {
        val path = listOf(LatLng(-0.0, -0.0))

        val decoded = TrackPathCodec.decode(TrackPathCodec.encode(path))

        assertEquals(java.lang.Double.doubleToRawLongBits(-0.0), java.lang.Double.doubleToRawLongBits(decoded.single().lat))
        assertEquals(java.lang.Double.doubleToRawLongBits(-0.0), java.lang.Double.doubleToRawLongBits(decoded.single().lng))
    }

    @Test
    fun `bytes that are not a whole number of points are refused, never read as a shorter path`() {
        val whole = TrackPathCodec.encode(ridge)

        assertThrows(IllegalArgumentException::class.java) { TrackPathCodec.decode(whole.copyOf(whole.size - 1)) }
        assertThrows(IllegalArgumentException::class.java) { TrackPathCodec.decode(ByteArray(8)) }
    }
}
