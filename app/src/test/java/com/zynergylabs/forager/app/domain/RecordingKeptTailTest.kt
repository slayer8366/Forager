package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-10-26-05 (RECORD -1651): the recording line reaches the walker's dot. [RecordingKeptTail] holds the
 * recording's newest kept points; [recordingLineOf] draws the stored track with the held points newer than its last one,
 * network fixes left out as the store's read leaves them out (`excludeNetworkProviderFixes`).
 */
class RecordingKeptTailTest {

    private fun point(lat: Double, t: Long) = TrackPoint(lat = lat, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    @Test
    fun `the held points are the recording's kept points, newest last`() {
        val holder = RecordingKeptTail()
        holder.add("t1", point(45.001, 1_000L))
        holder.add("t1", point(45.002, 2_000L))

        assertEquals(KeptTail("t1", listOf(point(45.001, 1_000L), point(45.002, 2_000L))), holder.tail.value)
    }

    @Test
    fun `a point on another recording replaces the held points`() {
        val holder = RecordingKeptTail()
        holder.add("t1", point(45.001, 1_000L))
        holder.add("t2", point(46.001, 5_000L))

        assertEquals(KeptTail("t2", listOf(point(46.001, 5_000L))), holder.tail.value)
    }

    @Test
    fun `the end of the recording forgets them, and an end for another recording does not`() {
        val holder = RecordingKeptTail()
        holder.add("t1", point(45.001, 1_000L))
        holder.end("t2")
        assertEquals("an end for another recording changes nothing", "t1", holder.tail.value.trackId)

        holder.end("t1")
        assertEquals(KeptTail.NONE, holder.tail.value)

        holder.add("t1", point(45.001, 1_000L))
        holder.end(null)
        assertEquals("the service's onDestroy names no track and ends any", KeptTail.NONE, holder.tail.value)
    }

    @Test
    fun `at most MAX are held, the newest`() {
        val holder = RecordingKeptTail()
        repeat(RecordingKeptTail.MAX + 5) { holder.add("t1", point(45.0, (it + 1) * 1_000L)) }

        val held = holder.tail.value.points
        assertEquals(RecordingKeptTail.MAX, held.size)
        assertEquals("the oldest five went", 6_000L, held.first().timestampEpochMillis)
        assertEquals((RecordingKeptTail.MAX + 5) * 1_000L, held.last().timestampEpochMillis)
    }

    @Test
    fun `the line is the stored track with the held points newer than its last, once each`() {
        val stored = listOf(point(45.001, 1_000L), point(45.002, 2_000L))
        val tail = KeptTail("t1", listOf(point(45.001, 1_000L), point(45.002, 2_000L), point(45.003, 3_000L), point(45.004, 4_000L)))

        assertEquals(listOf(45.001, 45.002, 45.003, 45.004), recordingLineOf(stored, tail, "t1").map { it.lat })
    }

    @Test
    fun `a network fix held and not written is left out, as the store's read leaves it out`() {
        val stored = listOf(point(45.001, 1_000L))
        // Sub-second time: a network fix by the timestamp rule (isNetworkProviderTimestamp).
        val tail = KeptTail("t1", listOf(point(45.001, 1_000L), point(49.0, 2_500L), point(45.003, 3_000L)))

        assertEquals(listOf(45.001, 45.003), recordingLineOf(stored, tail, "t1").map { it.lat })
    }

    @Test
    fun `before the first write the line is the held points`() {
        val tail = KeptTail("t1", listOf(point(45.001, 1_000L), point(45.002, 2_000L)))

        assertEquals(listOf(45.001, 45.002), recordingLineOf(emptyList(), tail, "t1").map { it.lat })
    }

    @Test
    fun `points held for another recording, or with no recording, are not drawn`() {
        val stored = listOf(point(45.001, 1_000L))
        val tail = KeptTail("t2", listOf(point(46.0, 9_000L)))

        assertEquals(stored, recordingLineOf(stored, tail, "t1"))
        assertEquals(stored, recordingLineOf(stored, KeptTail("t1", listOf(point(46.0, 9_000L))), null))
    }
}
