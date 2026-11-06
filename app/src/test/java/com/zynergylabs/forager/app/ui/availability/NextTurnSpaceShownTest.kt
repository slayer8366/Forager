package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.nextturn.FakeRouteWays
import com.zynergylabs.forager.app.domain.nextturn.JunctionClass
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.domain.nextturn.NextTurnWay
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnGround
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import com.zynergylabs.forager.app.domain.nextturn.nextTurn
import com.zynergylabs.forager.app.domain.nextturn.routeTurnsFor
import com.zynergylabs.forager.app.domain.wayhome.WayHomePlace
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.placeOnWayHome
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The next turn's space, from the card's own readout (dispatch 2026-11-06-01, Amendment 1, J, 2b-2 step 1): its arrow, figure and
 * words through [nextTurnSpaceShown], with the readout built by the real [navigationReadout] and, where it is a walk, the next turn
 * by the real [nextTurn] on a walker placed fix by fix by the app's own [placeOnWayHome]. The ground is made-up ways round the
 * made-up point P; nothing here is anyone's walk. Robolectric, for the logged fallback's [Log].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NextTurnSpaceShownTest {

    private val t = 1_700_000_000_000L
    private val p = LatLng(45.3736, -121.6959)
    private val target = Waypoint("wp", p.lat, p.lng, null, "Made-up summit", "", t)
    private val metersPerDegreeLat = 111_195.08

    private fun fix(metersSouth: Double, accuracy: Float? = 3.79f, at: Long = t) =
        LocationFix.Update(lat = p.lat - metersSouth / metersPerDegreeLat, lng = p.lng, altitude = null, accuracyMeters = accuracy, timestampEpochMillis = at, provider = FixProvider.GPS)

    private val byTrail = ReturnRoute.Ahead(p, 640.0)

    private fun readout(fix: LocationFix.Update?, route: ReturnRoute?, unit: DistanceUnit = DistanceUnit.MILES, now: Long = t + 1_000L) =
        navigationReadout(TrueHeadingReading.Available(0f), fix, target, unit, now, route = route)

    private fun shown(fix: LocationFix.Update?, route: ReturnRoute?, ahead: NextTurn, unit: DistanceUnit = DistanceUnit.MILES, now: Long = t + 1_000L) =
        nextTurnSpaceShown(readout(fix, route, unit, now), hasFix = fix != null, ahead = ahead, unit = unit)

    private val aTurn = NextTurn.Turn(TurnBand.PLAIN, TurnSide.LEFT, 120.0, 0)

    // E1, the owner: "Blank (Recommended)": "The space keeps its place, empty, so nothing moves."

    @Test
    fun `E1 - no GPS fix - the space is blank, with a turn ahead`() {
        assertEquals(NextTurnSpaceShown.BLANK, shown(null, byTrail, aTurn))
    }

    @Test
    fun `E1 - Arrived - the space is blank`() {
        assertEquals(ARRIVED_TEXT, readout(fix(10.0), byTrail).distanceText)
        assertEquals(NextTurnSpaceShown.BLANK, shown(fix(10.0), byTrail, aTurn))
        assertEquals(NextTurnSpaceShown.BLANK, shown(fix(10.0), byTrail, NextTurn.NoTurnLeft))
    }

    @Test
    fun `E1 - the dash, a lost fix - the space is blank`() {
        val tenMinutesLater = t + 10 * 60_000L
        assertEquals("—", readout(fix(400.0), byTrail, now = tenMinutesLater).distanceText)
        assertEquals(NextTurnSpaceShown.BLANK, shown(fix(400.0), byTrail, aTurn, now = tenMinutesLater))
    }

    @Test
    fun `E1 - a held-back route - the space is blank`() {
        assertEquals(NextTurnSpaceShown.BLANK, shown(fix(400.0), ReturnRoute.Unavailable(canRetry = true), aTurn))
    }

    @Test
    fun `E2 - the way home's own track, its turns unknown - the space is blank`() {
        assertEquals(NextTurnSpaceShown.BLANK, shown(fix(400.0), byTrail, NextTurn.Blank(NextTurnBlank.TRACK_TURNS_UNKNOWN)))
    }

    @Test
    fun `E3 - the straight line to a waypoint - the card shows a figure, and the space is blank, never the straight-ahead arrow`() {
        val straight = ReturnRoute.Straight(WayToWaypointNote.NO_TRAIL_ROUTES)
        val ahead = nextTurn(NextTurnWay.NoTrailRoute, progress = null, current = null)
        assertEquals("≈ 1300 ft", readout(fix(400.0), straight).distanceText)
        assertEquals(NextTurnSpaceShown.BLANK, shown(fix(400.0), straight, ahead))
    }

    // A3, narrowed by E3: "The straight-ahead arrow shows only on a trail route with no turn left before the target."

    @Test
    fun `A3 - a trail route with no turn left - the straight-ahead arrow, with the top line's own figure`() {
        val r = readout(fix(400.0), byTrail)
        val s = shown(fix(400.0), byTrail, NextTurn.NoTurnLeft)
        assertEquals(NextTurnArrow.STRAIGHT, s.arrow)
        assertEquals(r.distanceText, s.figure)
        assertEquals("0.4 mi", s.figure)
        assertEquals("keep straight in 0.4 miles", s.spoken)
    }

    @Test
    fun `A3 - in kilometres the straight-ahead figure is still the top line's own`() {
        val s = shown(fix(400.0), byTrail, NextTurn.NoTurnLeft, unit = DistanceUnit.KILOMETERS)
        assertEquals("640 m", s.figure)
        assertEquals("keep straight in 640 meters", s.spoken)
    }

    // D1: "the figure counts from the needle's place, never below 0 ... Units follow the card's."

    @Test
    fun `D1 - a turn's figure is its distance from the needle's place, in the card's units`() {
        assertEquals(NextTurnSpaceShown(NextTurnArrow.LEFT, "394 ft", "turn left in 394 feet"), shown(fix(400.0), byTrail, aTurn))
        assertEquals(NextTurnSpaceShown(NextTurnArrow.LEFT, "120 m", "turn left in 120 meters"), shown(fix(400.0), byTrail, aTurn, unit = DistanceUnit.KILOMETERS))
    }

    @Test
    fun `D1 - a fork's figure the same way - keep right, its arrow and words`() {
        val fork = NextTurn.Fork(TurnSide.RIGHT, 500.0)
        assertEquals(NextTurnSpaceShown(NextTurnArrow.FORK_RIGHT, "0.3 mi", "keep right in 0.3 miles"), shown(fix(400.0), byTrail, fork))
        assertEquals(NextTurnSpaceShown(NextTurnArrow.FORK_RIGHT, "500 m", "keep right in 500 meters"), shown(fix(400.0), byTrail, fork, unit = DistanceUnit.KILOMETERS))
    }

    @Test
    fun `G3 - counting on a plain turn reaches the words - take the second left`() {
        assertEquals("take the second left in 394 feet", shown(fix(400.0), byTrail, aTurn.copy(sameSidePathsBefore = 1)).spoken)
    }

    @Test
    fun `G3 - a sharp turn with paths before it is not counted`() {
        val sharp = NextTurn.Turn(TurnBand.SHARP, TurnSide.RIGHT, 120.0, sameSidePathsBefore = 2)
        assertEquals(NextTurnSpaceShown(NextTurnArrow.SHARP_RIGHT, "394 ft", "make a sharp right in 394 feet"), shown(fix(400.0), byTrail, sharp))
    }

    // A walk: the next turn by the real nextTurn, the walker placed fix by fix.

    @Test
    fun `a walk up the trail to a left at a crossroads - the left arrow, its distance from the needle's place, in feet and metres`() {
        val way = TurnGround.wayTo(
            leadIn = listOf(0.0 to -60.0, 0.0 to -50.0),
            trail = listOf(0.0 to -50.0, 0.0 to 200.0, -300.0 to 200.0),
            finalLeg = listOf(-300.0 to 200.0, -300.0 to 250.0),
        )
        val ways = listOf(
            TurnGround.way(0.0 to -50.0, 0.0 to 200.0),
            TurnGround.way(0.0 to 200.0, -300.0 to 200.0),
            TurnGround.way(0.0 to 200.0, 0.0 to 400.0),
            TurnGround.way(0.0 to 200.0, 300.0 to 200.0),
        )
        val line = TurnGround.lineOf(way)
        val turns = runBlocking { routeTurnsFor(way, line, FakeRouteWays(ways), TurnGround.FOLDER) }
        var last: WayHomePlace? = null
        var progress: WayHomeProgress? = null
        for (north in listOf(-55.0, 0.0, 50.0, 100.0)) {
            progress = placeOnWayHome(line, TurnGround.at(0.0, north), last)
            (progress as? WayHomeProgress.Placed)?.let { last = it.place }
        }
        val here = TurnGround.at(0.0, 100.0)
        val ahead = nextTurn(NextTurnWay.AlongTrails(line, turns), progress, here)
        val walkFix = LocationFix.Update(lat = here.lat, lng = here.lng, altitude = null, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)
        val turnNode = (turns as RouteTurns.Read).junctions.first { it.judgement.junctionClass == JunctionClass.TURN }.node
        val nearest = line.nodes.indices.minBy { GeoDistance.metersBetween(line.nodes[it], here) }
        val expectedMeters = line.alongMeters[turnNode] - line.alongMeters[nearest]
        for (unit in DistanceUnit.entries) {
            val r = navigationReadout(TrueHeadingReading.Available(0f), walkFix, target, unit, t + 1_000L, route = ReturnRoute.Ahead(TurnGround.at(0.0, 120.0), 460.0))
            val s = nextTurnSpaceShown(r, hasFix = true, ahead = ahead, unit = unit)
            assertEquals(NextTurnArrow.LEFT, s.arrow)
            assertEquals(formatDistanceMeters(expectedMeters, unit), s.figure)
            assertTrue("${s.spoken}", s.spoken!!.startsWith("turn left in "))
        }
    }

    // The fallback: a figure with no unit to spell out is spoken as written, and logged (CLAUDE.md: no fallback that isn't logged).

    @Test
    fun `a straight-ahead figure in a form with no unit to spell out is spoken as written, and the fallback is logged`() {
        ShadowLog.clear()
        // The straight line's "≈" form (no route): never paired with the straight-ahead arrow by the domain (E3), so
        // this pairing is made up, to reach the fallback.
        val s = shown(fix(400.0), route = null, ahead = NextTurn.NoTurnLeft)
        assertEquals("≈ 1300 ft", s.figure)
        assertEquals("keep straight in ≈ 1300 ft", s.spoken)
        assertTrue("a warning logged", ShadowLog.getLogsForTag("NextTurn").any { it.type == Log.WARN })
    }
}
