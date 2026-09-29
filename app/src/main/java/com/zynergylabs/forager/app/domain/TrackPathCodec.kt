package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import java.nio.ByteBuffer

/**
 * The bytes of a saved track path: for each point, its latitude then its longitude, each as a big-endian
 * IEEE 754 double. Sixteen bytes a point, no header, no count (the length is the count), so an empty path is
 * zero bytes. F3, dispatch 2026-09-28-195, item 1.
 *
 * **Why raw doubles and not an encoded polyline.** The dispatch allows either. An encoded polyline rounds each
 * coordinate to 5 or 6 decimal places, so the line an entry draws after its track is deleted would differ,
 * slightly, from the line it drew the day before: a saved path that does not equal the path it saved. Raw
 * doubles round-trip bit for bit, and sixteen bytes a point is the figure the kept-track-path pulse priced
 * for this design (`docs/audits/2026-09-29-kept-track-path-pulse.md`, option O2). The cost is size: a polyline
 * is roughly a third of it, which matters only if paths grow far beyond a walk's worth of points.
 *
 * Pure Kotlin: no Android, no Room. The database column is a BLOB holding exactly these bytes.
 */
object TrackPathCodec {
    private const val BYTES_PER_POINT = 16

    fun encode(path: List<LatLng>): ByteArray {
        val buffer = ByteBuffer.allocate(path.size * BYTES_PER_POINT)
        for (point in path) {
            buffer.putDouble(point.lat)
            buffer.putDouble(point.lng)
        }
        return buffer.array()
    }

    /**
     * Throws [IllegalArgumentException] when [bytes] is not a whole number of points. A truncated blob is
     * refused rather than read as a shorter path: a line that silently loses its tail would look like a
     * complete track that stopped early.
     */
    fun decode(bytes: ByteArray): List<LatLng> {
        require(bytes.size % BYTES_PER_POINT == 0) {
            "a saved track path is a whole number of $BYTES_PER_POINT-byte points; got ${bytes.size} bytes"
        }
        val buffer = ByteBuffer.wrap(bytes)
        return List(bytes.size / BYTES_PER_POINT) { LatLng(buffer.getDouble(), buffer.getDouble()) }
    }
}
