package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.ClimbWalks.Heights
import com.zynergylabs.forager.app.domain.ClimbWalks.Stop
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the climbing allowance costs on the 15 s sundown tick (dispatch 2026-10-26-19 Amendment 1: "Measure the cost of
 * computing heights on the 15 s sundown tick in a test, and state it"). The tick computes [returnWalkingTime], whose
 * [pathHome] now reads the whole track's barometric heights ([climbAlongRoute]) on every call.
 *
 * Measured, not asserted: a time bound on a shared CI machine would be a flaky test, and this is here to give a figure.
 * The figures are printed to the test's standard output (in its JUnit XML), for two track sizes: the 10-24 outing's
 * 1,135 stored points, and a four-hour walk at the high-accuracy sampler's 5 s, 2,880 points (the size `TrackSelfJoin`'s
 * cost note uses). Each figure is the median of nine runs after three warm-up runs, on the JVM, not a phone.
 */
class ClimbAllowanceCostTest {

    /** A wandering walk of about [pointCount] points: a slow loop with a hill, so the heights and joins are not trivial. */
    private fun walkOf(pointCount: Int): Track {
        val stops = (0..pointCount / 20).map { k ->
            val angle = k * 0.21
            Stop(400.0 * cos(angle) + k * 3.0, 400.0 * sin(angle), 30.0 * sin(angle * 2.0))
        }
        val points = ClimbWalks.walk(stops, Heights.BAROMETER, endAt = 100_000_000_000L).take(pointCount)
        return Track("t", null, points.first().timestampEpochMillis, null, points)
    }

    private fun medianMillis(runs: Int, block: () -> Unit): Double {
        repeat(3) { block() }
        val times = (1..runs).map {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1e6
        }.sorted()
        return times[times.size / 2]
    }

    @Test
    fun `the tick's walk back with the climb, measured at two track sizes`() {
        for (size in listOf(1_135, 2_880)) {
            val track = walkOf(size)
            assertTrue("the walk has $size points, not ${track.points.size}", track.points.size == size)
            val last = track.points.last()
            val current = LatLng(last.lat, last.lng)

            val heightsOnly = medianMillis(9) { barometricTrackHeightsOrNull(track.points) }
            var estimate: ReturnWalkingTime? = null
            val wholeEstimate = medianMillis(9) { estimate = returnWalkingTime(track, null, current, FixFreshness.FRESH) }
            val path = (estimate as ReturnWalkingTime.Estimate).path
            assertTrue("the climb was measured: ${path.climb}", path.climb is WayBackClimb.Measured)
            println("ClimbAllowanceCost: $size points: barometric heights ${"%.1f".format(heightsOnly)} ms; whole walk-back estimate ${"%.1f".format(wholeEstimate)} ms (median of 9)")
        }
    }
}
