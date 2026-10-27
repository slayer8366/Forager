package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The quiet off-trail state (dispatch 2026-10-26-13, plan step 7 PR 3; RECORD -1631, -1676), on the hairpin
 * ([WayHomeLines.hairpin]): its trail runs from (0, 0) east and back to (0, 20), and its track from the trailhead runs
 * north from (0, 20) to the start at (0, 120). One GPS reading a second, at 5 m accuracy, so "off" is over 45 m.
 */
class WayHomeOffTrailTest {

    private val trail = hairpin.trail.points
    private val toStart = hairpin.trackToStart.points
    private val wayHomeLines = listOf(trail, toStart)
    private val judge = WayHomeOffTrail()

    private fun reading(east: Double, north: Double, second: Int, accuracy: Float = 5f): TrackPoint {
        val p = at(east, north)
        return TrackPoint(p.lat, p.lng, null, accuracy, T + second * 1_000L)
    }

    /** Readings at (east, north) for each second in [seconds], against [lines]; the state after each, by second. */
    private fun walk(east: Double, north: Double, seconds: IntRange, provider: FixProvider = FixProvider.GPS, lines: List<List<com.zynergylabs.forager.app.domain.model.LatLng>> = wayHomeLines) =
        seconds.associateWith { judge.next(reading(east, north, it), provider, lines) }

    @Test
    fun `on the trail line it stays on`() {
        val states = walk(100.0, 5.0, 0..40)
        assertEquals(setOf(OffTrailState.ON), states.values.toSet())
    }

    @Test
    fun `off the trail it turns off at 15 s, not before`() {
        // 50 m south of the trail's start, on the dotted track back: off the trail line, over the 45 m limit.
        val states = walk(0.0, -50.0, 0..16)
        assertEquals("still on at 14 s", OffTrailState.ON, states[14])
        assertEquals("off at 15 s", OffTrailState.OFF, states[15])
        assertEquals(OffTrailState.OFF, states[16])
    }

    @Test
    fun `back on the trail for 10 s reads Back on trail, for 5 s, then on`() {
        walk(0.0, -50.0, 0..15)
        assertEquals(OffTrailState.OFF, judge.state)
        val back = walk(100.0, 5.0, 20..40)
        assertEquals("still off at 9 s back on", OffTrailState.OFF, back[29])
        assertEquals("back on at 10 s", OffTrailState.BACK_ON, back[30])
        assertEquals("still showing at 4 s", OffTrailState.BACK_ON, back[34])
        assertEquals("gone at 5 s", OffTrailState.ON, back[35])
    }

    @Test
    fun `a reading back on restarts the hold`() {
        walk(0.0, -50.0, 0..10)
        walk(100.0, 5.0, 11..11)
        val states = walk(0.0, -50.0, 12..27)
        assertEquals("15 s from the new start, not the old", OffTrailState.ON, states[26])
        assertEquals(OffTrailState.OFF, states[27])
    }

    @Test
    fun `network readings are skipped, both going off and coming back`() {
        val off = walk(0.0, -50.0, 0..30, provider = FixProvider.NETWORK)
        assertEquals("network readings off the trail never turn it off", setOf(OffTrailState.ON), off.values.toSet())
        walk(0.0, -50.0, 31..46)
        assertEquals(OffTrailState.OFF, judge.state)
        val back = walk(100.0, 5.0, 47..70, provider = FixProvider.NETWORK)
        assertEquals("network readings on the trail never bring it back", setOf(OffTrailState.OFF), back.values.toSet())
    }

    @Test
    fun `the track from the trailhead to the start counts as on`() {
        // 80 m north of the trail's end, on the walker's own track to the car.
        val states = walk(0.0, 100.0, 0..30)
        assertEquals(setOf(OffTrailState.ON), states.values.toSet())
    }

    @Test
    fun `the same place against the trail alone is off, so the track to the start is what keeps it on`() {
        val states = walk(0.0, 100.0, 0..30, lines = listOf(trail))
        assertEquals(OffTrailState.OFF, states[30])
    }

    @Test
    fun `no lines decide nothing, and reset clears the state`() {
        assertEquals(setOf(OffTrailState.ON), walk(0.0, -50.0, 0..30, lines = emptyList()).values.toSet())
        walk(0.0, -50.0, 31..46)
        assertEquals(OffTrailState.OFF, judge.state)
        judge.reset()
        assertEquals(OffTrailState.ON, judge.state)
        assertEquals("the hold starts again after a reset", OffTrailState.ON, judge.next(reading(0.0, -50.0, 47), FixProvider.GPS, wayHomeLines))
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
