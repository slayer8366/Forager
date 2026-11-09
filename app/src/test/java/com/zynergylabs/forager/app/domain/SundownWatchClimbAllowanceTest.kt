package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.ClimbWalks.Heights
import com.zynergylabs.forager.app.domain.ClimbWalks.Stop
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.FakeRouter
import com.zynergylabs.forager.app.domain.wayhome.FakeSnapper
import com.zynergylabs.forager.app.domain.wayhome.FindWayHomeUseCase
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.placeOnWayHome
import com.zynergylabs.forager.app.domain.wayhome.toPathHome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The climbing allowance in the walk back's time, through `SundownWatch.tick` and its "start back by" (dispatch
 * 2026-10-26-19 Amendment 1; RECORD -1687, -1689): 2.2 s per metre climbed and per metre descended on the way back,
 * from barometric heights only.
 *
 * Each case compares a walk over a hill with the same walk on flat ground: the same points, the same pace, the same
 * distance, so the only difference in "start back by" is the allowance. The climb is banked through the journal's 4 m
 * hysteresis, which only ever counts less than the true change: at the end of the way back up to one threshold stays
 * unbanked, and at each turn from climbing to descending (or back) up to one threshold is lost from the climb before
 * it and again from the descent after it, since the descent is measured from the last banked height. So a way back
 * with no turn is checked to within one threshold's worth below the true figure (4 m × 2.2 s, 8.8 s) and one with a
 * turn to within three (26.4 s), and never above it. Both still tell counting climb and descent from counting either
 * alone. (The first version of this test allowed two thresholds either side and failed at 110.0 s against 132 s on
 * the hill: the hysteresis had lost 3.6 m of climb at the top and 6.4 m of descent, its rule, not a fault. A port of
 * the heights on the same walk read 16.4 m up and 33.6 m down.)
 */
class SundownWatchClimbAllowanceTest {

    private val hour = 3_600_000L
    private val morning = 1_791_010_800_000L
    private val start = Ground.at(0.0, 0.0)
    private val origin = Waypoint("origin", start.lat, start.lng, null, "Start", "", 0L)
    private val sunset = (ComputeSundownCountdownUseCase()(morning, start, morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis
    private val now = sunset - 3 * hour

    /** One hysteresis threshold's worth of allowance, in ms: see the class doc. */
    private val threshold = ElevationHysteresis.THRESHOLD_METERS * CLIMB_ALLOWANCE_SECONDS_PER_METER * 1_000.0

    /** [actual] is [trueMillis] less at most [thresholds] thresholds' worth, and never more. */
    private fun assertAllowance(message: String, trueMillis: Double, thresholds: Int, actual: Long) =
        assertTrue("$message: $actual ms, for $trueMillis ms true, at most $thresholds thresholds under", actual <= trueMillis + 1_000.0 && actual >= trueMillis - thresholds * threshold - 1_000.0)

    private val logged = mutableListOf<String>()

    /** "Start back by" after [ticks] ticks with the walker at [points]' last point, the way home given by [wayHome]. */
    private fun startBackAt(points: List<TrackPoint>, ticks: Int = 1, wayHome: (Track) -> PathHome? = { null }): Long {
        val track = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)
        val path = wayHome(track)
        val watch = SundownWatch(
            alertDelivery = { },
            clock = CurrentTimeProvider { now },
            preferences = object : SundownPreferencesRepository {
                override suspend fun getDarknessMarginMinutes() = Result.success(60)
                override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit)
                override suspend fun getAlertsEnabled() = Result.success(true)
                override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit)
            },
            readTrack = { id -> Result.success(track.takeIf { it.id == id }) },
            readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
            isReturning = { path != null },
            errorLog = { _, message, _ -> logged += message },
            wayHomeFor = { path },
        )
        watch.begin("t1")
        repeat(ticks) {
            watch.onFix(points.last().copy(timestampEpochMillis = now / 1_000L * 1_000L), FixProvider.GPS)
            runBlocking { watch.tick() }
        }
        return (watch.shown.value!!.line as SundownLine.BeforeSunset).startBackAtEpochMillis!!
    }

    private fun walk(heights: Heights, stops: List<Stop> = ClimbWalks.overAHill) = ClimbWalks.walk(stops, heights, endAt = now)

    private fun noAllowanceLogs() = logged.filter { "climbing allowance" in it }

    // ---- Before Return: the way back along the walker's own track ----

    @Test
    fun `at the far point of a climbing out-and-back, start back by is earlier by 2_2 s per metre climbed and descended on the way back`() {
        val flat = startBackAt(walk(Heights.FLAT))
        val hilly = startBackAt(walk(Heights.BAROMETER))

        // The way back climbs 20 m and descends 40 m: 60 m at 2.2 s, 132 s. Climb alone would be 44 s, descent alone 88 s.
        assertAllowance("start back by moves earlier by the allowance", 132_000.0, thresholds = 3, actual = flat - hilly)
        assertEquals("a barometric walk logs nothing about the allowance", emptyList<String>(), noAllowanceLogs())
    }

    @Test
    fun `a walk with no barometer gets no allowance, and says so once`() {
        // The same hill in GPS altitude only (RECORD -1689, C3, the owner: "No allowance there (Recommended)").
        val flat = startBackAt(walk(Heights.FLAT))
        logged.clear()
        val gpsOnly = startBackAt(walk(Heights.GPS_ONLY), ticks = 3)

        assertEquals("no allowance from GPS heights", flat.toDouble(), gpsOnly.toDouble(), 1_000.0)
        assertEquals("logged once over three ticks: $logged", 1, noAllowanceLogs().size)
    }

    // ---- After Return: the way home along trails (RECORD -1689, C1: the walker's track stands in for the trail) ----

    /**
     * Out along a trail running east from the start, climbing 40 m over 500 m, then 200 m north into the forest,
     * climbing 10 m more. The way home is the forest back to the trail, the trail back west, and nothing to the start.
     */
    private val alongATrail = listOf(Stop(0.0, 0.0, 0.0), Stop(500.0, 0.0, 40.0), Stop(500.0, 200.0, 50.0))

    private fun wayHomeAlongTheTrail(track: Track): PathHome {
        val found = runBlocking {
            FindWayHomeUseCase(
                FakeRegions(Ground.region()),
                FakeRouter(FakeRouter.found(500, at(500.0, 0.0), at(250.0, 0.0), at(0.0, 0.0))),
                FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0))),
                { _, _, _ -> },
            )(track, origin)
        }
        assertTrue("precondition: the way home is along the trail, not $found", found is WayHomeResult.AlongTrails)
        val line = WayHomeLine((found as WayHomeResult.AlongTrails).wayHome)
        val last = track.points.last()
        val placed = placeOnWayHome(line, LatLng(last.lat, last.lng), previous = null)
        assertTrue("precondition: the walker is placed on it, not $placed", placed is WayHomeProgress.Placed)
        // As AppContainer wires SundownWatch's wayHomeFor.
        return (placed as WayHomeProgress.Placed).place.toPathHome(pointCount = 0)
    }

    @Test
    fun `after Return on a way home along trails, the figure keeps the trail's share of the climb`() {
        val flat = startBackAt(walk(Heights.FLAT, alongATrail), wayHome = ::wayHomeAlongTheTrail)
        val hilly = startBackAt(walk(Heights.BAROMETER, alongATrail), wayHome = ::wayHomeAlongTheTrail)

        // 10 m down out of the forest and 40 m down the trail: 50 m, 110 s. Without the trail's share it would be 22 s.
        assertAllowance("the allowance along the way home", 110_000.0, thresholds = 1, actual = flat - hilly)
    }

    @Test
    fun `the allowance does not drop at Return when the way home follows the trail the walker came out on`() {
        val points = walk(Heights.BAROMETER, alongATrail)
        val flatPoints = walk(Heights.FLAT, alongATrail)
        val beforeReturn = startBackAt(flatPoints) - startBackAt(points)
        val afterReturn = startBackAt(flatPoints, wayHome = ::wayHomeAlongTheTrail) - startBackAt(points, wayHome = ::wayHomeAlongTheTrail)

        assertEquals("the same climb on the same ground, before and after Return", beforeReturn.toDouble(), afterReturn.toDouble(), threshold)
        assertTrue("and it is there: $beforeReturn ms", beforeReturn > 90_000L)
    }
}
