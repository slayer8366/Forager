package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.ClimbWalks.Heights
import com.zynergylabs.forager.app.domain.ClimbWalks.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The climbing allowance's parts (dispatch 2026-10-26-19 Amendment 1; RECORD -1687, -1689): its arithmetic, the climb's
 * banking, which must be the journal's, and the barometer rule. The allowance through the sundown line is
 * `SundownWatchClimbAllowanceTest`.
 */
class ClimbAllowanceTest {

    @Test
    fun `the allowance is 2_2 s per metre climbed and per metre descended`() {
        assertEquals(132_000L, climbAllowanceMillis(WayBackClimb.Measured(climbMeters = 20.0, descentMeters = 40.0)))
        assertEquals(44_000L, climbAllowanceMillis(WayBackClimb.Measured(climbMeters = 20.0, descentMeters = 0.0)))
        assertEquals(88_000L, climbAllowanceMillis(WayBackClimb.Measured(climbMeters = 0.0, descentMeters = 40.0)))
    }

    @Test
    fun `no barometer, or nothing measured, is no allowance`() {
        assertEquals(0L, climbAllowanceMillis(WayBackClimb.NoBarometer))
        assertEquals(0L, climbAllowanceMillis(null))
    }

    @Test
    fun `the climb is banked as the journal banks Climb and Descent`() {
        // A walk over two hills, with jitter under the threshold, read through the journal's own use case.
        val stops = listOf(Stop(0.0, 0.0, 0.0), Stop(0.0, 300.0, 30.0), Stop(0.0, 450.0, 12.0), Stop(0.0, 700.0, 41.0), Stop(0.0, 800.0, 5.0))
        val points = ClimbWalks.walk(stops, Heights.BAROMETER, endAt = 10_000_000L).mapIndexed { i, p ->
            p.copy(pressureHpa = p.pressureHpa!! + (if (i % 2 == 0) 0.15f else -0.15f))
        }
        val journal = ComputeTrackStatisticsUseCase()(points)
        val banked = bankedClimb(trackHeights(points))

        assertEquals(journal.elevationGainMeters!!, banked.climbMeters, 1e-9)
        assertEquals(journal.elevationLossMeters!!, banked.descentMeters, 1e-9)
        assertTrue("the walk has climbs to bank: ${banked.climbMeters}", banked.climbMeters > 50.0)
    }

    @Test
    fun `a missing height resets the climb's reference, as it does the journal's`() {
        // 3 m, a gap, then 3 m more: neither side reaches the threshold on its own, so nothing banks.
        assertEquals(WayBackClimb.Measured(0.0, 0.0), bankedClimb(listOf(0.0, 3.0, null, 3.0, 6.0)))
        assertEquals(WayBackClimb.Measured(6.0, 0.0), bankedClimb(listOf(0.0, 3.0, 6.0)))
    }

    @Test
    fun `the heights are barometric when at least half the points carry a pressure, as trackHeights reads them`() {
        val points = ClimbWalks.walk(ClimbWalks.overAHill, Heights.BAROMETER, endAt = 10_000_000L)
        val half = points.mapIndexed { i, p -> if (i % 2 == 0) p else p.copy(pressureHpa = null) }
        val underHalf = points.mapIndexed { i, p -> if (i % 2 == 0 && i > 0) p else p.copy(pressureHpa = null) }

        assertTrue(isBarometricTrack(points))
        assertTrue(isBarometricTrack(half))
        assertFalse(isBarometricTrack(underHalf))
        assertFalse(isBarometricTrack(ClimbWalks.walk(ClimbWalks.overAHill, Heights.GPS_ONLY, endAt = 10_000_000L)))
        assertEquals(WayBackClimb.NoBarometer, climbAlongRoute(underHalf, underHalf.indices.toList().reversed()))
    }
}
