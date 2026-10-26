package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.LIVE_FIX_MAX_ACCURACY_METERS
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walker's place on a way home along trails, and the distance home from it (dispatch 2026-10-24-10).
 *
 * The place, which the needle and the greyed walked part read (RECORD -1613, Amendment 1): the earliest node near the
 * fix, not before the last place. The figure (RECORD -1618 and -1620, Amendments 3 and 4): counted from the earliest
 * node within [WAY_HOME_FIGURE_REACH_METERS] (the live-fix gate's 50 m plus the line's 10 m node step, 60 m) of the fix,
 * anywhere on the line, plus 25 m when that node is on the trail part, so the distance home is never short for any fix
 * the gate admits (`PathHome.kt`'s ruling). Pinned on a switchback, a loop, a double-back, and Sorrel's B-395-2 cases:
 * a fix up to 50 m from the walker but more than 50 m from every node at or behind them.
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
        // Amendments 3 and 4: the figure counts from the earliest node within the reach, so it reads up to the reach long,
        // plus the 25 m allowance, plus a node's spacing.
        assertTrue(
            "long by at most the reach, the allowance and a node: ${place.remainingMeters}",
            place.remainingMeters <= 420.0 + WAY_HOME_FIGURE_REACH_METERS + 25.0 + 10.0,
        )
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
        // counts from the earliest node within the reach anywhere, which here is on leg 1, so it reads long, never short.
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

        // At (195, 0), not (200, 0): from (200, 0) the node at (140, 0) is the reach exactly, a test of rounding.
        val onTrail = placed(placeOnWayHome(line, at(195.0, 0.0), previous = null))
        assertEquals(true, onTrail.onTrail)
        // The figure's node is the earliest within the 60 m reach of (195, 0) anywhere (Amendment 4): (140, 0), 55 m
        // away, 50 + 140 along, so 380 m of line, plus the 25 m.
        assertEquals(380.0 + WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS, onTrail.remainingMeters, 0.5)
    }

    /**
     * The way to a waypoint (dispatch 2026-10-26-11 Amendment 3, item 5; RECORD -1671, the owner: "Drop it (Recommended)",
     * Juniper's O2): a line built with no trail allowance reads the 60 m reach alone, so the same fix as above reads 380 m,
     * not 405. The way home's line, built without saying, keeps its 25 m (the case above).
     */
    @Test
    fun `a line with no trail allowance, the waypoint's, reads the reach alone on the trail`() {
        val waypointLine = WayHomeLine(hairpin, trailAllowanceMeters = 0.0)
        val onTrail = placed(placeOnWayHome(waypointLine, at(195.0, 0.0), previous = null))
        assertEquals(true, onTrail.onTrail)
        assertEquals("no 25 m on the waypoint's line", 380.0, onTrail.remainingMeters, 0.5)
        assertEquals("the way home's line keeps it", 405.0, placed(placeOnWayHome(line, at(195.0, 0.0), previous = null)).remainingMeters, 0.5)
    }

    @Test
    fun `between 25 and 50 m off the line the hop is counted, beside the start the distance to it is added (-1634), and over 50 m the walker is off the line`() {
        val off35 = placed(placeOnWayHome(line, at(-35.0, -50.0), previous = null))
        assertEquals(HopBand.COUNTED, off35.hopBand)
        // RECORD -1634 (the owner: "Add it, never short (Recommended)"; dispatch 2026-10-25-06 Amendment 1) supersedes
        // -1618's "no hop is added" for a fix beside or behind the line's first point: this fix is 35 m square beside it,
        // so the figure adds the 35 m back to it. GPS wobble at the start reads up to about 50 m long, never short.
        assertEquals(570.0 + 35.0, off35.remainingMeters, 0.5)

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
    fun `a fix just inside the 50 m gate beside the start is placed and adds its distance to the start (-1634), one just outside is off the line`() {
        val inside = placed(placeOnWayHome(line, at(-49.9, -50.0), previous = null))
        // RECORD -1634 supersedes -1618's "no hop is added" for a fix beside or behind the line's first point
        // (dispatch 2026-10-25-06 Amendment 1): 49.9 m beside it, so 570 + 49.9.
        assertEquals(570.0 + 49.9, inside.remainingMeters, 0.5)
        assertTrue(placeOnWayHome(line, at(-50.1, -50.0), previous = null) is WayHomeProgress.OffLine)
    }

    /** The walker is truly at [walker]; [fix] is a position the gate admits for them, within its 50 m of where they are. */
    private fun assertNeverShort(line: WayHomeLine, walker: LatLng, fix: LatLng, truth: Double) {
        assertTrue(
            "the fix is within the gate's 50 m of the walker",
            GeoDistance.metersBetween(walker, fix) <= LIVE_FIX_MAX_ACCURACY_METERS,
        )
        val place = placed(placeOnWayHome(line, fix, previous = null))
        assertTrue("never short: ${place.remainingMeters} against $truth", place.remainingMeters >= truth)
    }

    @Test
    fun `B-395-2 shape 1 - a fix 50 m ahead of the walker on the track back does not read short`() {
        // Sorrel's case (a): the walker 1.1 m past the track back's first node, (0, -50); the fix 49.9 m ahead along the
        // line. With the reach at 50 m, the earliest node within it was the one ahead of the walker, (0, -41.67): 561.7 m
        // against 568.9 m.
        assertNeverShort(line, walker = at(0.0, -48.9), fix = at(0.0, 1.0), truth = line.totalMeters - 1.1)
    }

    @Test
    fun `B-395-2 shape 1 - a fix 50 m ahead of the walker on the track to the start does not read short`() {
        // Sorrel's case (b): the track to the start is filled to 9.09 m nodes; the walker at (0, 61), 4.6 m past the node
        // at (0, 56.36); the fix 49.9 m ahead. With the reach at 50 m: 54.5 m against 59 m.
        assertNeverShort(line, walker = at(0.0, 61.0), fix = at(0.0, 110.9), truth = 120.0 - 61.0)
    }

    @Test
    fun `B-395-2 shape 2 - a fix at the gate's edge beside the hairpin's later leg does not read short`() {
        // Sorrel's case (e), on this PR's own hairpin (legs 20 m apart): the walker at (101, 0) on leg 1; the fix at
        // (105.4, 49.8), 49.99 m from them, 50.09 and 50.01 m from the nodes either side of them, and 45.7 m from leg 2's
        // (140, 20). With the reach at 50 m the figure counted from leg 2: 265 m against 419 m.
        assertNeverShort(line, walker = at(101.0, 0.0), fix = at(105.4, 49.8), truth = 100.0 - 1.0 + 20.0 + 200.0 + 100.0)
    }

    @Test
    fun `B-395-2 shape 2 - a fix at the gate's edge with the later leg 70 m away does not read short`() {
        // Sorrel's case (d): the same hairpin with legs 70 m apart. The walker midway between leg 1's nodes at (100, 0) and
        // (110, 0); the fix 49.9 m north of them, 50.15 m from both nodes. With the reach at 50 m the figure counted from
        // leg 2's (150, 70): 275 m against 465 m.
        val wide = WayHomeLine(
            WayHomeLines.wayHome(
                part(WayHomePartKind.TRACK_BACK, 0.0 to -50.0, 0.0 to 0.0),
                part(WayHomePartKind.TRAIL, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 70.0, 0.0 to 70.0),
                part(WayHomePartKind.TRACK_TO_START, 0.0 to 70.0, 0.0 to 170.0),
            ),
        )
        assertNeverShort(wide, walker = at(105.0, 0.0), fix = at(105.0, 49.9), truth = 95.0 + 70.0 + 200.0 + 100.0)
    }

    // Dispatch 2026-10-25-06 (RECORD -1630, the owner: "Add it, fix first"): a walker past the line's first point reads the
    // distance back to it on top of the line's total, so the figure is never short of the line drawn. Hazel's S22 check
    // (prompts/preserved/2026-10-25-hazel-s22-figure-check.md, section 3): after a re-route the figure held at the line's
    // total while the walker walked 27 m on, out past the line's start.

    @Test
    fun `a walker past the line's first point reads the distance back to it on top of the line's total`() {
        // The hairpin starts at (0, -50) heading north; (0, -80) is 30 m past its start, the way the walker was going.
        for (beyond in listOf(5.0, 10.0, 20.0, 30.0, 45.0)) {
            val place = placed(placeOnWayHome(line, at(0.0, -50.0 - beyond), previous = null))
            assertEquals("$beyond m past the start", 570.0 + beyond, place.remainingMeters, 0.5)
        }
    }

    @Test
    fun `beside the line's first point, the walker reads the distance across to it as well`() {
        // 20 m east of the start, square to the first stretch: to the line is 20 m, then the whole line.
        assertEquals(590.0, placed(placeOnWayHome(line, at(20.0, -50.0), previous = null)).remainingMeters, 0.5)
    }

    @Test
    fun `a walker on the line ahead of its first point reads today's figure, with nothing added`() {
        // Pins that the gap is added only past the start: along the first stretch the figure stays the line's metres from
        // the earliest node within the reach, here node 0, as before this dispatch.
        for (north in listOf(-50.0, -45.0, -30.0, -10.0)) {
            assertEquals("at (0, $north)", 570.0, placed(placeOnWayHome(line, at(0.0, north), previous = null)).remainingMeters, 0.5)
        }
    }
}
