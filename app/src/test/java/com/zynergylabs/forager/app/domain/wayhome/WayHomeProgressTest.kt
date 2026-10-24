package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walker's place on a way home along trails (RECORD -1613, the owner: "A, plus 25 m (Recommended)"; dispatch
 * 2026-10-24-10 Amendment 1, ruling 1): the earliest node near the fix, not before the last place, plus 25 m on the
 * trail part, so the distance home is never short (`PathHome.kt`'s ruling). Pinned on a switchback and a loop.
 */
class WayHomeProgressTest {

    private val line = WayHomeLine(hairpin)

    private fun placed(progress: WayHomeProgress): WayHomePlace {
        assertTrue("placed, not $progress", progress is WayHomeProgress.Placed)
        return (progress as WayHomeProgress.Placed).place
    }

    @Test
    fun `the line's metres are the way home's, its parts' metres summed`() {
        val engineMetres = WayHomeLines.wayHome(
            part(WayHomePartKind.TRACK_BACK, 0.0 to -50.0, 0.0 to 0.0),
            part(WayHomePartKind.TRAIL, 0.0 to 0.0, 300.0 to 0.0, meters = 330.0),
            part(WayHomePartKind.TRACK_TO_START, 300.0 to 0.0, 300.0 to 40.0),
        )
        assertEquals(engineMetres.totalMeters, WayHomeLine(engineMetres).totalMeters, 0.01)
        assertEquals(570.0, line.totalMeters, 0.5)
    }

    @Test
    fun `on a switchback, a walker on the first leg nearer the second is placed on the first, never short`() {
        // At (100, 12): 12 m from leg 1, 8 m from leg 2. The true distance home from leg 1 there is 100 + 20 + 200 + 100.
        val place = placed(placeOnWayHome(line, at(100.0, 12.0), previous = null))
        assertTrue("on leg 1, not the later leg: ${place.remainingMeters}", place.remainingMeters >= 420.0)
        assertTrue("short by nothing, long by at most the 25 m reach and the allowance: ${place.remainingMeters}", place.remainingMeters <= 420.0 + 50.0)
        assertTrue(place.onTrail)
    }

    @Test
    fun `once on the second leg, the walker stays placed on it`() {
        val leg2At160 = line.nodes.indexOfFirst { com.zynergylabs.forager.app.domain.GeoDistance.metersBetween(it, at(160.0, 20.0)) < 0.5 }
        val previous = WayHomePlace(leg2At160, hopMeters = 0.0, hopBand = HopBand.NONE, onTrail = true, remainingMeters = 0.0)
        val next = placed(placeOnWayHome(line, at(100.0, 12.0), previous = previous))
        // From leg 2 at 100 m east the true distance home is 100 + 100.
        assertTrue("never short: ${next.remainingMeters}", next.remainingMeters >= 200.0)
        assertTrue("not put back on leg 1: ${next.remainingMeters}", next.remainingMeters < 300.0)
    }

    @Test
    fun `a walker who turned back is placed back along the line`() {
        val nearStart = placed(placeOnWayHome(line, at(0.0, 100.0), previous = null))
        val back = placed(placeOnWayHome(line, at(100.0, 2.0), previous = nearStart))
        assertTrue("back on leg 1: ${back.remainingMeters}", back.remainingMeters >= 420.0)
    }

    @Test
    fun `on a loop that passes its own beginning, the walker is placed at the beginning`() {
        // The trail runs 300 m east; the walker's own track to the start loops north and west and ends 15 m from where
        // the trail began. At (0, 8) the end of the line is 7 m away, but the walker is at its beginning.
        val loop = WayHomeLine(
            WayHomeLines.wayHome(
                part(WayHomePartKind.TRACK_BACK, 0.0 to -30.0, 0.0 to 0.0),
                part(WayHomePartKind.TRAIL, 0.0 to 0.0, 300.0 to 0.0),
                part(WayHomePartKind.TRACK_TO_START, 300.0 to 0.0, 300.0 to 100.0, 0.0 to 100.0, 0.0 to 15.0),
            ),
        )
        val place = placed(placeOnWayHome(loop, at(0.0, 8.0), previous = null))
        // From the trail's beginning: 300 + 100 + 300 + 85.
        assertTrue("never short: ${place.remainingMeters}", place.remainingMeters >= 785.0)
    }

    @Test
    fun `on the walker's own track there is no allowance, on the trail there is 25 m`() {
        val onTrack = placed(placeOnWayHome(line, at(0.0, -50.0), previous = null))
        assertEquals(false, onTrack.onTrail)
        assertEquals(570.0, onTrack.remainingMeters, 0.5)

        val onTrail = placed(placeOnWayHome(line, at(200.0, 0.0), previous = null))
        assertEquals(true, onTrail.onTrail)
        // Earliest node within 25 m of (200, 0) is at (180, 0): 50 + 180 along, so 340 m of line, plus the 25 m.
        assertEquals(340.0 + WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS, onTrail.remainingMeters, 0.5)
    }

    @Test
    fun `between 25 and 50 m off the line the hop is counted, and over 50 m the walker is off the line`() {
        val off35 = placed(placeOnWayHome(line, at(-35.0, -50.0), previous = null))
        assertEquals(HopBand.COUNTED, off35.hopBand)
        assertEquals(570.0 + 35.0, off35.remainingMeters, 0.5)

        val off60 = placeOnWayHome(line, at(-60.0, -50.0), previous = null)
        assertTrue("off the line: $off60", off60 is WayHomeProgress.OffLine)
    }

    @Test
    fun `every stretch of the line is a track or a trail stretch, in order`() {
        val runs = line.kinds.fold(emptyList<RouteLinePartKind>()) { acc, k -> if (acc.lastOrNull() == k) acc else acc + k }
        assertEquals(listOf(RouteLinePartKind.TRACK, RouteLinePartKind.TRAIL, RouteLinePartKind.TRACK), runs)
    }
}
