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
 * When each sundown alert fires, pinned to the tick, for the four cases dispatch 2026-09-28-592's
 * Amendment 1 (RECORD -593) names: alerts off, arrival, alerts turned on mid-walk, and the margin
 * changed mid-walk. The owner allowed the watch to compute and publish the line's value on every
 * tick, with the alerts-off and arrived checks moved to just before deciding and delivering, on the
 * condition that every alert still fires at exactly the moment it did. These tests were written
 * against `main` at `ef269025` and run there first, using only the calls the service makes
 * ([SundownWatch.begin], [SundownWatch.onFix], [SundownWatch.tick]) and what reaches
 * [AlertDelivery]: which alert, at which tick, saying what.
 *
 * Every expected moment is "the first one-minute tick at or after" an instant worked out from the
 * real countdown and the real [returnWalkingTime], so a moment that moved by even one tick fails.
 */
class SundownWatchAlertMomentsTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val morning = 1_791_010_800_000L // 2026-10-03T07:00:00Z

    private val baseSunset = (ComputeSundownCountdownUseCase()(morning, LatLng(origin.lat, origin.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

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
    private val clock = Clock(baseSunset - 6 * hour)
    private val preferences = Preferences()
    private var track: Track? = null
    private var returning = false

    private val watch = SundownWatch(
        alertDelivery = { delivered += clock.now to it },
        clock = clock,
        preferences = preferences,
        readTrack = { id -> Result.success(track?.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = { returning },
        errorLog = { _, _, _ -> },
    )

    private fun walkOut(minutes: Int, startAt: Long = baseSunset - 6 * hour, speed: Float = 1.2f): List<TrackPoint> =
        (0..minutes * 12).map { i ->
            val metres = i * 5 * speed
            TrackPoint(origin.lat + metres / 111_195.0, origin.lng, null, 4f, startAt + i * 5_000L, speed, 0.5f)
        }

    private fun trackOf(points: List<TrackPoint>) = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)

    private fun gpsFix(point: TrackPoint, at: Long = clock.now) = point.copy(timestampEpochMillis = at / 1_000L * 1_000L)

    private fun sunsetAt(point: TrackPoint) =
        (ComputeSundownCountdownUseCase()(morning, LatLng(point.lat, point.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private fun walkBack(points: List<TrackPoint>, at: TrackPoint): Long {
        val estimate = returnWalkingTime(trackOf(points), origin, LatLng(at.lat, at.lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertTrue("precondition: a measured pace", !estimate.isAtLeast)
        return estimate.walkingMillis
    }

    /** One tick at [clock]'s time, a fresh GPS fix at [at] first. */
    private fun tickAt(at: TrackPoint) {
        watch.onFix(gpsFix(at), FixProvider.GPS)
        runBlocking { watch.tick() }
    }

    /** One tick a minute from the clock's time while it is before [until], each with a fresh fix at [at]. */
    private fun ticksUntil(until: Long, at: TrackPoint) {
        while (clock.now < until) {
            tickAt(at)
            clock.now += minute
        }
    }

    /** The first tick at or after [instant] on the one-minute grid that starts at [gridStart]. */
    private fun tickAtOrAfter(gridStart: Long, instant: Long): Long {
        if (instant <= gridStart) return gridStart
        val steps = (instant - gridStart + minute - 1) / minute
        return gridStart + steps * minute
    }

    private fun moments() = delivered.map { it.second.kind to it.first }

    // ── Alerts turned on mid-walk ──

    @Test
    fun `alerts turned on mid-walk before the heads-up time fire each alert at its own tick`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        val leaveBy = sunset - hour - walkBack(points, here)
        val gridStart = leaveBy - 2 * hour + 30_000L // off the grid by half a tick: the search's sub-second wobble cannot move a moment across one
        preferences.enabled = false

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(leaveBy - 50 * minute, here)
        assertEquals("nothing while off", emptyList<Pair<AlertKind, Long>>(), moments())
        preferences.enabled = true
        ticksUntil(sunset + hour, here)

        assertEquals(
            listOf(
                AlertKind.HEADS_UP to tickAtOrAfter(gridStart, leaveBy - 30 * minute),
                AlertKind.LEAVE_BY to tickAtOrAfter(gridStart, leaveBy),
                AlertKind.SUNSET to tickAtOrAfter(gridStart, sunset),
            ),
            moments(),
        )
    }

    @Test
    fun `alerts turned on between the heads-up and the leave-by time sound the heads-up at the turn-on tick`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        val leaveBy = sunset - hour - walkBack(points, here)
        val gridStart = leaveBy - 2 * hour + 30_000L // off the grid by half a tick: the search's sub-second wobble cannot move a moment across one
        preferences.enabled = false

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(leaveBy - 10 * minute, here)
        val turnedOnAt = clock.now
        preferences.enabled = true
        ticksUntil(sunset + hour, here)

        assertEquals(
            listOf(
                AlertKind.HEADS_UP to turnedOnAt,
                AlertKind.LEAVE_BY to tickAtOrAfter(gridStart, leaveBy),
                AlertKind.SUNSET to tickAtOrAfter(gridStart, sunset),
            ),
            moments(),
        )
    }

    @Test
    fun `alerts turned off over the leave-by time and back on sound the leave-by late, once, at the turn-on tick`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        val leaveBy = sunset - hour - walkBack(points, here)
        val gridStart = leaveBy - 2 * hour + 30_000L // off the grid by half a tick: the search's sub-second wobble cannot move a moment across one

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(leaveBy - 5 * minute, here)
        preferences.enabled = false
        ticksUntil(leaveBy + 20 * minute, here)
        val turnedOnAt = clock.now
        preferences.enabled = true
        ticksUntil(sunset + hour, here)

        assertEquals(
            listOf(
                AlertKind.HEADS_UP to tickAtOrAfter(gridStart, leaveBy - 30 * minute),
                AlertKind.LEAVE_BY to turnedOnAt,
                AlertKind.SUNSET to tickAtOrAfter(gridStart, sunset),
            ),
            moments(),
        )
    }

    // ── Alerts off ──

    /**
     * With the alerts off nothing is held, so the sunset this recording counts toward is never
     * fixed: turned on after sunset, the countdown has moved on to tomorrow's and nothing sounds
     * tonight. A watch that held the sunset while off would sound the sunset alert at the turn-on.
     */
    @Test
    fun `alerts off through sunset and turned on after it sound nothing that night`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        preferences.enabled = false

        watch.begin("t1")
        clock.now = sunset - 3 * hour
        ticksUntil(sunset + 10 * minute, here)
        preferences.enabled = true
        ticksUntil(sunset + 4 * hour, here)

        assertEquals(emptyList<Pair<AlertKind, Long>>(), moments())
    }

    @Test
    fun `alerts off the whole walk sound nothing, and the walk back is not carried while off`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        preferences.enabled = false

        watch.begin("t1")
        clock.now = sunset - 4 * hour
        ticksUntil(sunset + 2 * hour, here)

        assertEquals(emptyList<Pair<AlertKind, Long>>(), moments())
    }

    // ── Arrival ──

    /**
     * Arrival is noticed only while the alerts are on: with them off the watch never looks. So a
     * walker who reached the start with the alerts off, then set out again and turned them on,
     * still hears them.
     */
    @Test
    fun `reaching the start with the alerts off does not silence them once they are turned on away from it`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val atStart = TrackPoint(origin.lat, origin.lng, null, 4f, 0L)
        val sunset = sunsetAt(here)
        val leaveBy = sunset - hour - walkBack(points, here)
        val gridStart = leaveBy - 2 * hour + 30_000L // off the grid by half a tick: the search's sub-second wobble cannot move a moment across one
        preferences.enabled = false
        returning = true

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(gridStart + 10 * minute, atStart)
        preferences.enabled = true
        ticksUntil(sunset + hour, here)

        assertEquals(
            listOf(
                AlertKind.HEADS_UP to tickAtOrAfter(gridStart, leaveBy - 30 * minute),
                AlertKind.LEAVE_BY to tickAtOrAfter(gridStart, leaveBy),
                AlertKind.SUNSET to tickAtOrAfter(gridStart, sunset),
            ),
            moments(),
        )
    }

    @Test
    fun `arrived with the alerts on, after the heads-up, nothing more fires, through alerts off and on again`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val atStart = TrackPoint(origin.lat, origin.lng, null, 4f, 0L)
        val sunset = sunsetAt(here)
        val leaveBy = sunset - hour - walkBack(points, here)
        val gridStart = leaveBy - 2 * hour + 30_000L // off the grid by half a tick: the search's sub-second wobble cannot move a moment across one

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(leaveBy - 20 * minute, here)
        returning = true
        tickAt(atStart)
        clock.now += minute
        preferences.enabled = false
        ticksUntil(leaveBy + 5 * minute, here)
        preferences.enabled = true
        ticksUntil(sunset + hour, here)

        assertEquals(listOf(AlertKind.HEADS_UP to tickAtOrAfter(gridStart, leaveBy - 30 * minute)), moments())
    }

    // ── The margin changed mid-walk ──

    @Test
    fun `a longer margin set after the heads-up sounds the leave-by at the next tick, and the sunset stays put`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        val walk = walkBack(points, here)
        val leaveBy = sunset - hour - walk
        val gridStart = leaveBy - 2 * hour + 30_000L // off the grid by half a tick: the search's sub-second wobble cannot move a moment across one

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(leaveBy - 20 * minute, here)
        preferences.margin = 90 // the leave-by time moves 30 minutes earlier, behind now
        val changedAt = clock.now
        ticksUntil(sunset + hour, here)

        assertEquals(
            listOf(
                AlertKind.HEADS_UP to tickAtOrAfter(gridStart, leaveBy - 30 * minute),
                AlertKind.LEAVE_BY to changedAt,
                AlertKind.SUNSET to tickAtOrAfter(gridStart, sunset),
            ),
            moments(),
        )
        val said = delivered[1].second.sundown!!
        assertTrue("the leave-by alert states the new start-back time", kotlin.math.abs(said.leaveByAtEpochMillis - (sunset - 90 * minute - walk)) < 1_000L)
    }

    @Test
    fun `a shorter margin set before the heads-up moves every leave-by moment later by the difference`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        val walk = walkBack(points, here)
        val leaveBy60 = sunset - hour - walk
        val gridStart = leaveBy60 - 2 * hour + 30_000L

        watch.begin("t1")
        clock.now = gridStart
        ticksUntil(leaveBy60 - 45 * minute, here)
        preferences.margin = 30
        ticksUntil(sunset + hour, here)

        val leaveBy30 = sunset - 30 * minute - walk
        assertEquals(
            listOf(
                AlertKind.HEADS_UP to tickAtOrAfter(gridStart, leaveBy30 - 30 * minute),
                AlertKind.LEAVE_BY to tickAtOrAfter(gridStart, leaveBy30),
                AlertKind.SUNSET to tickAtOrAfter(gridStart, sunset),
            ),
            moments(),
        )
    }
}
