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
 * distance, so the only difference in "start back by" is the allowance. The barometer's one-minute smoothing and the
 * 4 m hysteresis can each leave up to one threshold unbanked at a turn, so the figures are checked to within two
 * thresholds' worth (2 × 4 m × 2.2 s, about 18 s), which still tells counting both climb and descent from counting
 * either alone.
 */
class SundownWatchClimbAllowanceTest {

    private val hour = 3_600_000L
    private val morning = 1_791_010_800_000L
    private val start = Ground.at(0.0, 0.0)
    private val origin = Waypoint("origin", start.lat, start.lng, null, "Start", "", 0L)
    private val sunset = (ComputeSundownCountdownUseCase()(morning, start, morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis
    private val now = sunset - 3 * hour

    /** Two thresholds' worth of allowance: see the class doc. */
    private val tolerance = 2 * ElevationHysteresis.THRESHOLD_METERS * CLIMB_ALLOWANCE_SECONDS_PER_METER * 1_000.0

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
        assertEquals("start back by moves earlier by the allowance", 132_000.0, (flat - hilly).toDouble(), tolerance)
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
        assertEquals("the allowance along the way home", 110_000.0, (flat - hilly).toDouble(), tolerance)
    }

    @Test
    fun `the allowance does not drop at Return when the way home follows the trail the walker came out on`() {
        val points = walk(Heights.BAROMETER, alongATrail)
        val flatPoints = walk(Heights.FLAT, alongATrail)
        val beforeReturn = startBackAt(flatPoints) - startBackAt(points)
        val afterReturn = startBackAt(flatPoints, wayHome = ::wayHomeAlongTheTrail) - startBackAt(points, wayHome = ::wayHomeAlongTheTrail)

        assertEquals("the same climb on the same ground, before and after Return", beforeReturn.toDouble(), afterReturn.toDouble(), tolerance / 2)
        assertTrue("and it is there: $beforeReturn ms", beforeReturn > 90_000L)
    }
}
