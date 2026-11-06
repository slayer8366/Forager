package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.isApproaching
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.domain.nextturn.NextTurnCardState
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import com.zynergylabs.forager.app.domain.nextturn.nextTurnSpace
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card's state for the next turn's space (dispatch 2026-11-06-01, Amendment 1, E1 and H4, 2b-1), read from the card's
 * own readout, built by the real [navigationReadout] (and [heldArrivalReadout]) for each state E1 names. E1, the owner:
 * "Blank (Recommended)": "The space keeps its place, empty, so nothing moves. The straight-ahead arrow shows everywhere
 * else there's no turn left." The target is the made-up point P; nothing here is anyone's walk.
 */
class NextTurnCardStateTest {

    private val t = 1_700_000_000_000L
    private val p = LatLng(45.3736, -121.6959)
    private val target = Waypoint("wp", p.lat, p.lng, null, "Made-up summit", "", t)
    private val metersPerDegreeLat = 111_195.08

    /** A fix [metersSouth] south of the target, with [accuracy] reported, taken at [at]. */
    private fun fix(metersSouth: Double, accuracy: Float? = 3.79f, at: Long = t) =
        LocationFix.Update(lat = p.lat - metersSouth / metersPerDegreeLat, lng = p.lng, altitude = null, accuracyMeters = accuracy, timestampEpochMillis = at, provider = FixProvider.GPS)

    private val byTrail = ReturnRoute.Ahead(p, 640.0)

    private fun readout(fix: LocationFix.Update?, route: ReturnRoute?, target: Waypoint? = this.target, now: Long = t + 1_000L) =
        navigationReadout(TrueHeadingReading.Available(0f), fix, target, DistanceUnit.MILES, now, route = route)

    private fun stateOf(fix: LocationFix.Update?, route: ReturnRoute?, target: Waypoint? = this.target, now: Long = t + 1_000L) =
        nextTurnCardState(readout(fix, route, target, now), hasFix = fix != null)

    private val aTurn = NextTurn.Turn(TurnBand.PLAIN, TurnSide.LEFT, 120.0, 0)

    // E1's states: each blanks the space.

    @Test
    fun `E1 - no GPS fix: the top line is the dash, and the space is blank for no fix`() {
        assertEquals("—", readout(null, byTrail).distanceText)
        assertEquals(NextTurnCardState.NO_FIX, stateOf(null, byTrail))
        assertEquals(NextTurn.Blank(NextTurnBlank.NO_FIX), nextTurnSpace(stateOf(null, byTrail), aTurn))
    }

    @Test
    fun `E1 - a lost fix: the top line is the dash, and the space is blank`() {
        val old = fix(400.0, at = t)
        val tenMinutesLater = t + 10 * 60_000L
        assertEquals("—", readout(old, byTrail, now = tenMinutesLater).distanceText)
        assertEquals(NextTurnCardState.NO_DISTANCE, stateOf(old, byTrail, now = tenMinutesLater))
        assertEquals(NextTurn.Blank(NextTurnBlank.NO_DISTANCE), nextTurnSpace(stateOf(old, byTrail, now = tenMinutesLater), aTurn))
    }

    @Test
    fun `E1 - a route still being worked out: the top line is the dash, and the space is blank`() {
        assertEquals("—", readout(fix(400.0), ReturnRoute.Pending).distanceText)
        assertEquals(NextTurnCardState.NO_DISTANCE, stateOf(fix(400.0), ReturnRoute.Pending))
    }

    @Test
    fun `E1 - no target: the top line is the dash, and the space is blank`() {
        assertEquals(NextTurnCardState.NO_DISTANCE, stateOf(fix(400.0), byTrail, target = null))
    }

    @Test
    fun `E1 - Arrived: the space is blank`() {
        assertEquals(ARRIVED_TEXT, readout(fix(10.0), byTrail).distanceText)
        assertEquals(NextTurnCardState.ARRIVED, stateOf(fix(10.0), byTrail))
        assertEquals(NextTurn.Blank(NextTurnBlank.ARRIVED), nextTurnSpace(stateOf(fix(10.0), byTrail), aTurn))
    }

    @Test
    fun `E1 - Arrived held after the return ended on arrival, the fix moving away: the space stays blank`() {
        val held = heldArrivalReadout(readout(fix(400.0), byTrail))
        assertEquals(NextTurnCardState.ARRIVED, nextTurnCardState(held, hasFix = true))
    }

    @Test
    fun `E1 - a held-back route, Unable to calculate route: the space is blank for the withheld route`() {
        val withheld = ReturnRoute.Unavailable(canRetry = true)
        assertEquals(ROUTE_UNAVAILABLE_TEXT, readout(fix(400.0), withheld).distanceText)
        assertEquals(NextTurnCardState.ROUTE_WITHHELD, stateOf(fix(400.0), withheld))
        assertEquals(NextTurn.Blank(NextTurnBlank.ROUTE_WITHHELD), nextTurnSpace(stateOf(fix(400.0), withheld), aTurn))
    }

    @Test
    fun `E1 - within the fix's own circle the top line reads Arrived, so the space is blank`() {
        // A poor fix: 30 m off, with 20 m reported, so the needle is withheld (within twice the accuracy). Arrival's radius
        // is never smaller, so the card reads Arrived here, and the space is blank by that.
        val poor = fix(30.0, accuracy = 20f)
        assertTrue("inside the fix's own circle", isApproaching(30.0, 20f))
        assertEquals(null, readout(poor, byTrail).targetArrowDegrees)
        assertEquals(NextTurnCardState.ARRIVED, stateOf(poor, byTrail))
    }

    // Everywhere else, a figure: the space shows what lies ahead.

    @Test
    fun `a figure by trail: the space shows the next turn`() {
        assertEquals(NextTurnCardState.SHOWS_DISTANCE, stateOf(fix(400.0), byTrail))
        assertEquals(aTurn, nextTurnSpace(stateOf(fix(400.0), byTrail), aTurn))
    }

    @Test
    fun `a stale fix still shows its figure, dimmed, so the space still shows what lies ahead`() {
        val stale = fix(400.0, at = t)
        val aMinuteLater = t + 60_000L
        assertEquals(NextTurnCardState.SHOWS_DISTANCE, stateOf(stale, byTrail, now = aMinuteLater))
    }

    @Test
    fun `a figure by trail with no turn left: the straight-ahead arrow's state passes through`() {
        assertEquals(NextTurn.NoTurnLeft, nextTurnSpace(stateOf(fix(400.0), byTrail), NextTurn.NoTurnLeft))
    }

    @Test
    fun `E3 - the straight line to a waypoint shows its figure, and the space is blank from the way, not the card`() {
        val straight = ReturnRoute.Straight(WayToWaypointNote.NO_TRAIL_ROUTES)
        assertEquals(NextTurnCardState.SHOWS_DISTANCE, stateOf(fix(400.0), straight))
        assertEquals(NextTurn.Blank(NextTurnBlank.NO_TRAIL_ROUTE), nextTurnSpace(stateOf(fix(400.0), straight), NextTurn.Blank(NextTurnBlank.NO_TRAIL_ROUTE)))
    }
}
