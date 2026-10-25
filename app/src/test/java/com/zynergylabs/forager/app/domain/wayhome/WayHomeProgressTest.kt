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
        // Amendment 3: the figure counts from the earliest node within the gate's 50 m, so it reads up to 50 m long, plus
        // the 25 m allowance, plus a node's spacing.
        assertTrue("long by at most the 50 m reach, the allowance and a node: ${place.remainingMeters}", place.remainingMeters <= 420.0 + 50.0 + 25.0 + 10.0)
        assertTrue(place.onTrail)
    }

    @Test
    fun `once on the second leg, the walker's place stays on it, and the figure is still never short`() {
        val leg2At160 = line.nodes.indexOfFirst { com.zynergylabs.forager.app.domain.GeoDistance.metersBetween(it, at(160.0, 20.0)) < 0.5 }
        val previous = WayHomePlace(leg2At160, hopMeters = 0.0, hopBand = HopBand.NONE, onTrail = true, remainingMeters = 0.0)
        val next = placed(placeOnWayHome(line, at(100.0, 12.0), previous = previous))
        // From leg 2 at 100 m east the true distance home is 100 + 100.
        assertTrue("never short: ${next.remainingMeters}", next.remainingMeters >= 200.0)
        // "Not before the last place" holds for the place the needle and the greyed part read (Amendment 3); the figure
        // counts from the earliest node within 50 m anywhere, which here is on leg 1, so it reads long, never short.
        assertTrue("the place stays on leg 2: ${next.index}", next.index >= leg2At160)
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
        // The figure's node is the earliest within 50 m of (200, 0) anywhere: (150, 0), 50 + 150 along, so 370 m of line,
        // plus the 25 m.
        assertEquals(370.0 + WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS, onTrail.remainingMeters, 0.5)
    }

    @Test
    fun `between 25 and 50 m off the line the hop is counted, and over 50 m the walker is off the line`() {
        val off35 = placed(placeOnWayHome(line, at(-35.0, -50.0), previous = null))
        assertEquals(HopBand.COUNTED, off35.hopBand)
        // Amendment 3: the 50 m reach covers the fix's distance from the line, so no hop is added to the figure.
        assertEquals(570.0, off35.remainingMeters, 0.5)

        val off60 = placeOnWayHome(line, at(-60.0, -50.0), previous = null)
        assertTrue("off the line: $off60", off60 is WayHomeProgress.OffLine)
    }

    @Test
    fun `every stretch of the line is a track or a trail stretch, in order`() {
        val runs = line.kinds.fold(emptyList<RouteLinePartKind>()) { acc, k -> if (acc.lastOrNull() == k) acc else acc + k }
        assertEquals(listOf(RouteLinePartKind.TRACK, RouteLinePartKind.TRAIL, RouteLinePartKind.TRACK), runs)
    }

    @Test
    fun `Sorrel's case - one fix 30 m off leg 1, nearer leg 2, does not read short`() {
        // B-395-1: placed on leg 1 at (90, 0); one fix at (100, 30), 30 m from leg 1 and 10 m from leg 2. The truth from
        // leg 1 at 100 m east is 100 + 20 + 200 + 100 = 420 m. It read 245 m before Amendment 3.
        val at90 = line.nodes.indexOfFirst { com.zynergylabs.forager.app.domain.GeoDistance.metersBetween(it, at(90.0, 0.0)) < 0.5 }
        val previous = WayHomePlace(at90, hopMeters = 0.0, hopBand = HopBand.NONE, onTrail = true, remainingMeters = 0.0)
        val place = placed(placeOnWayHome(line, at(100.0, 30.0), previous = previous))
        assertTrue("never short: ${place.remainingMeters}", place.remainingMeters >= 420.0)
    }

    @Test
    fun `a double-back through the hairpin does not read short`() {
        // Sorrel's case 2: placed on leg 2, the walker turns back round the riser and west along leg 1, away from home.
        val leg2At190 = line.nodes.indexOfFirst { com.zynergylabs.forager.app.domain.GeoDistance.metersBetween(it, at(190.0, 20.0)) < 0.5 }
        var previous: WayHomePlace? = WayHomePlace(leg2At190, hopMeters = 0.0, hopBand = HopBand.NONE, onTrail = true, remainingMeters = 0.0)
        // True distances home from leg 1: 100 + 20 + 200 + 100 at 100 m east, 200 + 20 + 200 + 100 at 0 m east.
        for ((east, truth) in listOf(200.0 to 320.0, 150.0 to 370.0, 100.0 to 420.0, 50.0 to 470.0, 0.0 to 520.0)) {
            val place = placed(placeOnWayHome(line, at(east, 0.0), previous = previous))
            assertTrue("at $east m east, never short: ${place.remainingMeters} against $truth", place.remainingMeters >= truth)
            previous = place
        }
    }

    @Test
    fun `a fix just inside the 50 m gate is placed and counts from the line, one just outside is off the line`() {
        val inside = placed(placeOnWayHome(line, at(-49.9, -50.0), previous = null))
        assertEquals(570.0, inside.remainingMeters, 0.5)
        assertTrue(placeOnWayHome(line, at(-50.1, -50.0), previous = null) is WayHomeProgress.OffLine)
    }
}
