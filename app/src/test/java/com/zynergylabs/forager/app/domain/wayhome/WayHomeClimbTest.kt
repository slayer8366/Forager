package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ClimbWalks
import com.zynergylabs.forager.app.domain.ClimbWalks.Heights
import com.zynergylabs.forager.app.domain.ClimbWalks.Stop
import com.zynergylabs.forager.app.domain.WayBackClimb
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The climb along the way home shown, from the walker's place (dispatch 2026-10-26-19 Amendment 1; RECORD -1689, C1):
 * the track parts' own barometric heights, kept through [FindWayHomeUseCase], and the trail part's taken from the
 * walker's own track between the same two trail points: the stand-in, since this test's fake route has no heights
 * (the trail data's heights are `WayHomeTrailHeightsTest`'s).
 *
 * The walk: out along a trail running east from the start, climbing 40 m over 500 m, then 200 m north into the forest,
 * climbing 10 m more.
 */
class WayHomeClimbTest {

    private val stops = listOf(Stop(0.0, 0.0, 0.0), Stop(500.0, 0.0, 40.0), Stop(500.0, 200.0, 50.0))

    private fun line(heights: Heights): WayHomeLine {
        val track = Ground.track().copy(points = ClimbWalks.walk(stops, heights, endAt = 10_000_000L))
        val found = runBlocking {
            FindWayHomeUseCase(
                FakeRegions(Ground.region()),
                FakeRouter(FakeRouter.found(500, at(500.0, 0.0), at(250.0, 0.0), at(0.0, 0.0))),
                FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0))),
                { _, _, _ -> },
            )(track, null)
        }
        assertTrue("precondition: along trails, not $found", found is WayHomeResult.AlongTrails)
        return WayHomeLine((found as WayHomeResult.AlongTrails).wayHome)
    }

    private fun climbAt(line: WayHomeLine, current: LatLng, previous: WayHomePlace? = null): WayBackClimb? {
        val placed = placeOnWayHome(line, current, previous)
        assertTrue("placed, not $placed", placed is WayHomeProgress.Placed)
        return (placed as WayHomeProgress.Placed).place.climb
    }

    @Test
    fun `the trail part carries the walker's own track's heights between the two trail points, marked a stand-in`() {
        val wayHome = line(Heights.BAROMETER).wayHome
        val trail = wayHome.trail.heights
        assertTrue("the trail part has barometric heights, not $trail", trail is PartHeights.Barometric)
        trail as PartHeights.Barometric
        assertTrue("marked a stand-in", trail.standIn)
        assertEquals("laid along the trail part's own metres", wayHome.trail.meters, trail.samples.last().alongMeters, 0.01)
        assertEquals("from the leave point's height", 41.0, trail.samples.first().height!! - 100.0, 1.5)
        assertEquals("to the entry point's", 0.0, trail.samples.last().height!! - 100.0, 1.5)
        assertTrue("the track back keeps its heights", wayHome.trackBack.heights is PartHeights.Barometric)
        assertTrue("so does the track to the start", wayHome.trackToStart.heights is PartHeights.Barometric)
    }

    @Test
    fun `at the far point the climb home is the forest's and the trail's`() {
        val climb = climbAt(line(Heights.BAROMETER), at(500.0, 200.0))
        assertTrue("measured, not $climb", climb is WayBackClimb.Measured)
        climb as WayBackClimb.Measured
        assertEquals("all down: 10 m out of the forest and 40 m down the trail", 50.0, climb.descentMeters, 8.0)
        assertEquals(0.0, climb.climbMeters, 4.0)
    }

    @Test
    fun `halfway down the trail only what is left of it is counted`() {
        val climb = climbAt(line(Heights.BAROMETER), at(250.0, 0.0))
        assertTrue("measured, not $climb", climb is WayBackClimb.Measured)
        climb as WayBackClimb.Measured
        // The figure's node is the earliest within 60 m, about 310 m east, where the trail is about 25 m up.
        assertTrue("less than the whole 50 m, and more than nothing: ${climb.descentMeters}", climb.descentMeters in 16.0..32.0)
    }

    @Test
    fun `a way home with no barometer says so, and a line with no heights says nothing`() {
        assertEquals(WayBackClimb.NoBarometer, climbAt(line(Heights.GPS_ONLY), at(500.0, 200.0)))
        assertNull("the hand-built hairpin carries no heights", climbAt(WayHomeLine(WayHomeLines.hairpin), WayHomeLines.hairpin.line.first()))
    }
}
