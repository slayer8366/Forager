package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SundownWatch] through the calls the recording service makes ([SundownWatch.begin],
 * [SundownWatch.onFix], [SundownWatch.tick], [SundownWatch.end]), on a fake clock, with the real
 * countdown, the real decision and the real [returnWalkingTime] behind it. The reading is what
 * reaches [AlertDelivery]: which alert, when, and what it says about sunset and the walk back.
 *
 * Dispatch 2026-09-28-516 (RECORD -515). No Activity, no ViewModel: the watch is the whole of it.
 */
class SundownWatchTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    /** A mid-latitude place with an ordinary sunset; the day is 2026-10-03. */
    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val morning = 1_791_010_800_000L // 2026-10-03T07:00:00Z, the small hours in Oregon

    /** Sunset as the real countdown computes it for [origin] on that day. */
    private val sunset = (ComputeSundownCountdownUseCase()(morning, LatLng(origin.lat, origin.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private class Clock(var now: Long) : CurrentTimeProvider {
        override fun nowEpochMillis() = now
    }

    private class Preferences(var margin: Int = 60, var enabled: Boolean = true) : SundownPreferencesRepository {
        override suspend fun getDarknessMarginMinutes() = Result.success(margin)
        override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit).also { margin = minutes }
        override suspend fun getAlertsEnabled() = Result.success(enabled)
        override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit).also { this.enabled = enabled }
    }

    private val delivered = mutableListOf<Pair<Long, Alert>>()
    private val clock = Clock(sunset - 6 * hour)
    private val preferences = Preferences()
    private var track: Track? = null
    private var returning = false
    private val logged = mutableListOf<String>()

    private val watch = SundownWatch(
        alertDelivery = { delivered += clock.now to it },
        clock = clock,
        preferences = preferences,
        readTrack = { id -> Result.success(track?.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = { returning },
        errorLog = { _, message, _ -> logged += message },
    )

    /**
     * A walk north from [origin] at 1.2 m/s, one stored point every 5 s with its Doppler speed, for
     * [minutes]: past the fifteen-minute bar, so the pace is measured and settled (not "at least").
     */
    private fun walkOut(minutes: Int, startAt: Long = sunset - 6 * hour, speed: Float = 1.2f): List<TrackPoint> =
        (0..minutes * 12).map { i ->
            val metres = i * 5 * speed
            TrackPoint(origin.lat + metres / 111_195.0, origin.lng, null, 4f, startAt + i * 5_000L, speed, 0.5f)
        }

    private fun trackOf(points: List<TrackPoint>) = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)

    /** A GPS fix (whole-second stamp) at [point]'s position, stamped [at]. */
    private fun gpsFix(point: TrackPoint, at: Long = clock.now) = point.copy(timestampEpochMillis = at / 1_000L * 1_000L)

    /** A network fix: the same position, a stamp with milliseconds ([isNetworkProviderFix]). */
    private fun networkFix(point: TrackPoint, at: Long = clock.now) = point.copy(timestampEpochMillis = at / 1_000L * 1_000L + 437L, accuracyMeters = 40f)

    private fun tick() = runBlocking { watch.tick() }

    /** Sunset where [point] is: the watch computes it at the newest fix, which is up to 1.4 km north of [origin]. */
    private fun sunsetAt(point: TrackPoint) =
        (ComputeSundownCountdownUseCase()(morning, LatLng(point.lat, point.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    /**
     * The crossing search is exact to a fraction of a second, and its answer moves by that much
     * with the time it searches from (0.3 s seen), so a sunset is checked to the second.
     */
    private fun assertSameSunset(expected: Long, actual: Long) =
        assertTrue("sunset $actual within a second of $expected", kotlin.math.abs(actual - expected) < 1_000L)

    private fun kinds() = delivered.map { it.second.kind }

    /** Ticks once a minute from [from] to [to], feeding a fresh GPS fix at [at] before each. */
    private fun walkTheClock(from: Long, to: Long, at: TrackPoint) {
        clock.now = from
        while (clock.now <= to) {
            watch.onFix(gpsFix(at), FixProvider.GPS)
            tick()
            clock.now += minute
        }
    }

    private fun measuredWalkBack(points: List<TrackPoint>, at: TrackPoint): Long {
        val estimate = returnWalkingTime(trackOf(points), origin, LatLng(at.lat, at.lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertTrue("precondition: the walk is long enough for a measured pace", !estimate.isAtLeast)
        return estimate.walkingMillis
    }

    @Test
    fun `heads-up 30 minutes before the leave-by time, then leave-by, then sunset, each once`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val walkBack = measuredWalkBack(points, here)
        val sunset = sunsetAt(here)
        val leaveBy = sunset - hour - walkBack

        watch.begin("t1")
        walkTheClock(leaveBy - hour, sunset + hour, here)

        assertEquals(listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), kinds())
        val (headsUpAt, headsUp) = delivered[0]
        val (leaveByAt, _) = delivered[1]
        val (sunsetAt, _) = delivered[2]
        assertTrue("heads-up within the minute of leave-by minus 30 min", headsUpAt in (leaveBy - 30 * minute)..(leaveBy - 29 * minute))
        assertTrue("leave-by within the minute of leave-by", leaveByAt in leaveBy..(leaveBy + minute))
        assertTrue("sunset within the minute of sunset", sunsetAt in sunset..(sunset + minute))
        assertEquals(WalkBack.About(walkBack), headsUp.sundown?.walkBack)
        assertSameSunset(sunset, headsUp.sundown!!.sunsetAtEpochMillis)
        assertSameSunset(leaveBy, headsUp.sundown!!.leaveByAtEpochMillis) // the start-by clock time the text shows
        assertTrue("every sundown alert overrides a silenced phone", delivered.all { it.second.overridesSilence })
    }

    @Test
    fun `the leave-by time moves earlier as the track grows, and fires at once when it passes now`() {
        val short = walkOut(16)
        track = trackOf(short)
        val shortLeaveBy = sunset - hour - measuredWalkBack(short, short.last())

        watch.begin("t1")
        clock.now = shortLeaveBy - 31 * minute
        watch.onFix(gpsFix(short.last()), FixProvider.GPS)
        tick()
        assertEquals("before the heads-up, with the short track", emptyList<AlertKind>(), kinds())

        // Much further out: the walk back is longer, so the leave-by time is now behind us.
        val long = walkOut(120)
        track = trackOf(long)
        val longLeaveBy = sunset - hour - measuredWalkBack(long, long.last())
        assertTrue("precondition: the longer walk puts the leave-by time in the past", longLeaveBy < clock.now)
        watch.onFix(gpsFix(long.last()), FixProvider.GPS)
        tick()

        assertEquals("leave-by at once, the heads-up spent without sounding", listOf(AlertKind.LEAVE_BY), kinds())
        tick()
        assertEquals("once", listOf(AlertKind.LEAVE_BY), kinds())
    }

    @Test
    fun `a recording started past the leave-by time fires leave-by at once, with the heads-up spent`() {
        val points = walkOut(20)
        track = trackOf(points)
        val leaveBy = sunset - hour - measuredWalkBack(points, points.last())

        watch.begin("t1")
        walkTheClock(leaveBy + 5 * minute, sunset - minute, points.last())

        assertEquals(listOf(AlertKind.LEAVE_BY), kinds())
        assertTrue(delivered[0].first == leaveBy + 5 * minute)
    }

    @Test
    fun `with no GPS fix the walk back is unknown and the leave-by time falls back to sunset minus the margin`() {
        val points = walkOut(20)
        track = trackOf(points)

        watch.begin("t1")
        clock.now = sunset - 2 * hour
        while (clock.now <= sunset + minute) {
            watch.onFix(networkFix(points.last()), FixProvider.NETWORK) // network only: a sunset position, no walk-back position
            tick()
            clock.now += minute
        }

        assertEquals(listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), kinds())
        val leaveByAt = delivered[1].first
        val sunsetHere = sunsetAt(points.last())
        assertTrue("leave-by at sunset minus the margin", leaveByAt in (sunsetHere - hour)..(sunsetHere - hour + minute))
        assertEquals(WalkBack.Unknown, delivered[1].second.sundown?.walkBack)
        assertSameSunset(sunsetHere, delivered[1].second.sundown!!.sunsetAtEpochMillis)
    }

    // ── Dispatch 2026-09-28-527: the walk back reads the provider, not the clock ──
    //
    // The two disagreements the S22 gave on its walks (the report, item 5): a GPS fix stamped with
    // milliseconds, and a network fix on the whole second. Before this dispatch the timestamp rule
    // decided both the wrong way round.

    /** Ticks a minute at a time to just past sunset, [fix] at the walker's position before each, from [provider]. */
    private fun walkTheClockWith(points: List<TrackPoint>, provider: FixProvider, fix: (TrackPoint) -> TrackPoint) {
        clock.now = sunset - 2 * hour
        while (clock.now <= sunset + minute) {
            watch.onFix(fix(points.last()), provider)
            tick()
            clock.now += minute
        }
    }

    @Test
    fun `a GPS fix stamped with milliseconds gives the walk back`() {
        val points = walkOut(20)
        track = trackOf(points)
        val walkBack = measuredWalkBack(points, points.last())

        watch.begin("t1")
        walkTheClockWith(points, FixProvider.GPS) { gpsFix(it).copy(timestampEpochMillis = clock.now / 1_000L * 1_000L + 479L) }

        assertEquals(WalkBack.About(walkBack), delivered.first().second.sundown?.walkBack)
    }

    @Test
    fun `a network fix on the whole second gives no walk back, and the leave-by time falls back`() {
        val points = walkOut(20)
        track = trackOf(points)

        watch.begin("t1")
        walkTheClockWith(points, FixProvider.NETWORK) { gpsFix(it).copy(accuracyMeters = 15f) }

        assertEquals(listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), kinds())
        assertEquals(WalkBack.Unknown, delivered[1].second.sundown?.walkBack)
    }

    @Test
    fun `a fix from an unknown provider gives no walk back`() {
        val points = walkOut(20)
        track = trackOf(points)

        watch.begin("t1")
        walkTheClockWith(points, FixProvider.UNKNOWN) { gpsFix(it).copy(accuracyMeters = 15f) }

        assertEquals(WalkBack.Unknown, delivered[1].second.sundown?.walkBack)
    }

    @Test
    fun `a GPS fix gone stale beyond five minutes withholds the walk back too`() {
        val points = walkOut(20)
        track = trackOf(points)
        watch.begin("t1")
        watch.onFix(gpsFix(points.last(), at = sunset - 2 * hour), FixProvider.GPS)
        clock.now = sunset - hour
        watch.onFix(networkFix(points.last()), FixProvider.NETWORK)
        tick()
        assertEquals(listOf(AlertKind.LEAVE_BY), kinds())
        assertEquals(WalkBack.Unknown, delivered[0].second.sundown?.walkBack)
    }

    @Test
    fun `a short walk out reads at least, from the default pace`() {
        val points = walkOut(3)
        track = trackOf(points)
        val estimate = returnWalkingTime(trackOf(points), origin, LatLng(points.last().lat, points.last().lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertTrue("precondition", estimate.isAtLeast)

        watch.begin("t1")
        walkTheClock(sunset - hour - estimate.walkingMillis - 31 * minute, sunset - hour - estimate.walkingMillis - 29 * minute, points.last())

        assertEquals(listOf(AlertKind.HEADS_UP), kinds())
        assertEquals(WalkBack.AtLeast(estimate.walkingMillis), delivered[0].second.sundown?.walkBack)
    }

    @Test
    fun `with the alerts turned off nothing fires`() {
        preferences.enabled = false
        val points = walkOut(20)
        track = trackOf(points)
        watch.begin("t1")
        walkTheClock(sunset - 3 * hour, sunset + hour, points.last())
        assertEquals(emptyList<AlertKind>(), kinds())
    }

    @Test
    fun `nothing fires when not recording, before the start or after the end`() {
        val points = walkOut(20)
        track = trackOf(points)
        walkTheClock(sunset - 3 * hour, sunset + hour, points.last())
        assertEquals("never begun", emptyList<AlertKind>(), kinds())

        watch.begin("t1")
        watch.end("t1")
        walkTheClock(sunset - 3 * hour, sunset + hour, points.last())
        assertEquals("ended", emptyList<AlertKind>(), kinds())

        watch.begin("t1")
        watch.end(null) // the service destroyed, with no id left to name
        walkTheClock(sunset - 3 * hour, sunset + hour, points.last())
        assertEquals("ended by the service going", emptyList<AlertKind>(), kinds())
    }

    @Test
    fun `a stop for another track does not end this recording`() {
        val points = walkOut(20)
        track = trackOf(points)
        watch.begin("t1")
        watch.end("t0")
        clock.now = sunset - 30 * minute // past the leave-by time
        watch.onFix(gpsFix(points.last()), FixProvider.GPS)
        tick()
        assertEquals(listOf(AlertKind.LEAVE_BY), kinds())
    }

    @Test
    fun `heading back and arrived at the start, nothing more fires`() {
        val points = walkOut(20)
        track = trackOf(points)
        val atStart = TrackPoint(origin.lat, origin.lng, null, 4f, 0L)
        watch.begin("t1")
        returning = true
        clock.now = sunset - 3 * hour
        watch.onFix(gpsFix(atStart), FixProvider.GPS)
        tick()

        walkTheClock(sunset - 3 * hour, sunset + hour, points.last()) // even walking out again
        assertEquals(emptyList<AlertKind>(), kinds())
    }

    @Test
    fun `at the start without having tapped Return, the alerts still fire`() {
        val points = walkOut(20)
        track = trackOf(points)
        val atStart = TrackPoint(origin.lat, origin.lng, null, 4f, 0L)
        watch.begin("t1")
        returning = false
        // Arrival is what silences them, and arrival needs Return: standing at the start alone does not.
        walkTheClock(sunset - 4 * hour, sunset + minute, atStart)
        assertEquals(listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), kinds())
    }

    @Test
    fun `a new recording starts with nothing fired`() {
        val points = walkOut(20)
        track = trackOf(points)
        watch.begin("t1")
        clock.now = sunset - 30 * minute // past the leave-by time
        watch.onFix(gpsFix(points.last()), FixProvider.GPS)
        tick()
        watch.end("t1")
        watch.begin("t1")
        watch.onFix(gpsFix(points.last()), FixProvider.GPS)
        tick()
        assertEquals(listOf(AlertKind.LEAVE_BY, AlertKind.LEAVE_BY), kinds())
    }

    /**
     * The countdown always reports the *next* sunset, so a recording that begins after sunset
     * counts toward tomorrow's: nothing tonight. The night foray the owner described.
     */
    @Test
    fun `a recording started after sunset alerts on nothing that night`() {
        val points = walkOut(20)
        track = trackOf(points)
        watch.begin("t1")
        walkTheClock(sunset + minute, sunset + 4 * hour, points.last())
        assertEquals(emptyList<AlertKind>(), kinds())
    }

    /**
     * The owner's merge condition for dispatch -516: with no live reading at all, the platform's
     * last known position (-510's [LastKnownLocationSource]) still gives a sunset time and its
     * alerts. It never gives a walk back: that stays unknown, so the leave-by time is sunset minus
     * the margin.
     */
    @Test
    fun `a recording with no live reading at all still gets a sunset time and its alerts, from the last known position`() {
        val lastKnownHere = LocationFix.Update(origin.lat, origin.lng, null, 900f, sunset - 5 * hour + 123L, provider = FixProvider.NETWORK)
        val lastKnownOnly = SundownWatch(
            alertDelivery = { delivered += clock.now to it },
            clock = clock,
            preferences = preferences,
            readTrack = { Result.success(null) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, message, _ -> logged += message },
            lastKnownLocation = { lastKnownHere },
        )
        lastKnownOnly.begin("t1")
        clock.now = sunset - 2 * hour
        while (clock.now <= sunset + minute) {
            runBlocking { lastKnownOnly.tick() } // no onFix: nothing live, ever
            clock.now += minute
        }

        assertEquals(listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), kinds())
        assertTrue("leave-by at sunset minus the margin", delivered[1].first in (sunset - hour)..(sunset - hour + minute))
        assertEquals("never a walk back from a last known position", WalkBack.Unknown, delivered[1].second.sundown?.walkBack)
        assertSameSunset(sunset, delivered[1].second.sundown!!.sunsetAtEpochMillis)
    }

    @Test
    fun `a live fix, once there is one, wins over the last known position`() {
        val farAway = LocationFix.Update(70.0, 20.0, null, 900f, 0L, provider = FixProvider.GPS) // Arctic Norway: another sunset entirely
        val watchWithStale = SundownWatch(
            alertDelivery = { delivered += clock.now to it },
            clock = clock,
            preferences = preferences,
            readTrack = { Result.success(null) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, _, _ -> },
            lastKnownLocation = { farAway },
        )
        watchWithStale.begin("t1")
        clock.now = sunset - hour + minute
        watchWithStale.onFix(networkFix(TrackPoint(origin.lat, origin.lng, null, 40f, 0L)), FixProvider.NETWORK)
        runBlocking { watchWithStale.tick() }
        assertEquals(listOf(AlertKind.LEAVE_BY), kinds())
        assertSameSunset(sunset, delivered[0].second.sundown!!.sunsetAtEpochMillis)
    }

    @Test
    fun `no position at all, nothing fires and nothing is read`() {
        var reads = 0
        val counting = SundownWatch(
            alertDelivery = { delivered += clock.now to it },
            clock = clock,
            preferences = preferences,
            readTrack = { reads++; Result.success(null) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, _, _ -> },
        )
        counting.begin("t1")
        clock.now = sunset + minute
        runBlocking { counting.tick() }
        assertEquals(emptyList<AlertKind>(), kinds())
        assertEquals(0, reads)
    }

    @Test
    fun `a track that cannot be read is logged and the walk back is unknown, not a guess`() {
        val failing = SundownWatch(
            alertDelivery = { delivered += clock.now to it },
            clock = clock,
            preferences = preferences,
            readTrack = { Result.failure(IllegalStateException("disk")) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, message, _ -> logged += message },
        )
        failing.begin("t1")
        clock.now = sunset - hour + minute
        failing.onFix(gpsFix(walkOut(1).last()), FixProvider.GPS)
        runBlocking { failing.tick() }
        assertEquals(listOf(AlertKind.LEAVE_BY), kinds())
        assertEquals(WalkBack.Unknown, delivered[0].second.sundown?.walkBack)
        assertTrue(logged.any { "track" in it })
    }
}
