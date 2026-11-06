package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sundown line [SundownWatch] publishes ([SundownWatch.shown]; dispatch 2026-09-28-592,
 * Amendment 1, RECORD -593), through the calls the service makes and the one Settings makes
 * ([SundownWatch.onMarginChanged]). The one claim that matters most: the line's start-back time is
 * the time the leave-by alert states, read here off what reaches [AlertDelivery].
 */
class SundownWatchLineTest {

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
    private var lastKnown: LocationFix.Update? = null

    private val watch = SundownWatch(
        alertDelivery = { delivered += clock.now to it },
        clock = clock,
        preferences = preferences,
        readTrack = { id -> Result.success(track?.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = { returning },
        errorLog = { _, _, _ -> },
        lastKnownLocation = { lastKnown },
    )

    private fun walkOut(minutes: Int, startAt: Long = baseSunset - 6 * hour): List<TrackPoint> =
        (0..minutes * 12).map { i ->
            val metres = i * 5 * 1.2
            TrackPoint(origin.lat + metres / 111_195.0, origin.lng, null, 4f, startAt + i * 5_000L, 1.2f, 0.5f)
        }

    private fun trackOf(points: List<TrackPoint>) = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)

    private fun gpsFix(point: TrackPoint) = point.copy(timestampEpochMillis = clock.now / 1_000L * 1_000L)

    private fun networkFix(point: TrackPoint) = point.copy(timestampEpochMillis = clock.now / 1_000L * 1_000L + 437L, accuracyMeters = 40f)

    private fun sunsetAt(point: TrackPoint) =
        (ComputeSundownCountdownUseCase()(morning, LatLng(point.lat, point.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private fun walkBack(points: List<TrackPoint>, at: TrackPoint) =
        (returnWalkingTime(trackOf(points), origin, LatLng(at.lat, at.lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate).walkingMillis

    private fun tickAt(point: TrackPoint) {
        watch.onFix(gpsFix(point), FixProvider.GPS)
        runBlocking { watch.tick() }
    }

    private fun line(): SundownLine = watch.shown.value!!.also { assertEquals("t1", it.trackId) }.line

    private fun assertSameSecond(expected: Long, actual: Long?) =
        assertTrue("$actual within a second of $expected", actual != null && kotlin.math.abs(actual - expected) < 1_000L)

    @Test
    fun `nothing is shown before a recording, and finding your position from its start until a place`() {
        assertNull(watch.shown.value)
        watch.begin("t1")
        assertEquals(SundownLine.FindingPosition, line())
        runBlocking { watch.tick() }
        assertEquals("a tick with no position at all still says so", SundownLine.FindingPosition, line())
    }

    @Test
    fun `the line's start-back time is the time the leave-by alert states`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val leaveBy = sunsetAt(here) - hour - walkBack(points, here)

        watch.begin("t1")
        clock.now = leaveBy + 30_000L
        tickAt(here)

        val alert = delivered.single().second
        assertEquals(AlertKind.LEAVE_BY, alert.kind)
        val shown = line() as SundownLine.BeforeSunset
        assertEquals("exactly the alert's own time", alert.sundown!!.leaveByAtEpochMillis, shown.startBackAtEpochMillis)
        assertEquals(alert.sundown!!.sunsetAtEpochMillis, shown.sunsetAtEpochMillis)
    }

    @Test
    fun `with no GPS fix the walk back is unknown and the line has no start-back time`() {
        val points = walkOut(20)
        track = trackOf(points)
        watch.begin("t1")
        clock.now = baseSunset - 3 * hour
        watch.onFix(networkFix(points.last()), FixProvider.NETWORK)
        runBlocking { watch.tick() }

        val shown = line() as SundownLine.BeforeSunset
        assertNull(shown.startBackAtEpochMillis)
        assertSameSecond(sunsetAt(points.last()), shown.sunsetAtEpochMillis)
    }

    @Test
    fun `an at-least walk back gives the start-back time too`() {
        val points = walkOut(3)
        track = trackOf(points)
        val estimate = returnWalkingTime(trackOf(points), origin, LatLng(points.last().lat, points.last().lng), FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertTrue("precondition", estimate.isAtLeast)
        watch.begin("t1")
        clock.now = baseSunset - 3 * hour
        tickAt(points.last())

        val shown = line() as SundownLine.BeforeSunset
        assertSameSecond(sunsetAt(points.last()) - hour - estimate.walkingMillis, shown.startBackAtEpochMillis)
    }

    @Test
    fun `with the alerts off nothing is delivered and the line stays, start-back time and all`() {
        preferences.enabled = false
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        watch.begin("t1")
        clock.now = sunsetAt(here) - 30 * minute // past the leave-by time
        tickAt(here)

        assertEquals(emptyList<Pair<Long, Alert>>(), delivered)
        val shown = line() as SundownLine.BeforeSunset
        assertSameSecond(sunsetAt(here) - hour - walkBack(points, here), shown.startBackAtEpochMillis)
    }

    @Test
    fun `after arriving at the start the line is still published`() {
        val points = walkOut(20)
        track = trackOf(points)
        val atStart = TrackPoint(origin.lat, origin.lng, null, 4f, 0L)
        watch.begin("t1")
        returning = true
        clock.now = baseSunset - 3 * hour
        tickAt(atStart)
        clock.now = baseSunset + 10 * minute
        tickAt(atStart)

        assertEquals(emptyList<Pair<Long, Alert>>(), delivered)
        assertTrue("after sunset now: ${line()}", line() is SundownLine.AfterSunset)
    }

    @Test
    fun `a margin changed in Settings moves the start-back time at once, and delivers nothing`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val walk = walkBack(points, here)
        watch.begin("t1")
        clock.now = baseSunset - 4 * hour
        tickAt(here)
        val before = (line() as SundownLine.BeforeSunset).startBackAtEpochMillis!!

        watch.onMarginChanged(30)

        val after = line() as SundownLine.BeforeSunset
        assertEquals("thirty minutes later, at once, with no tick", before + 30 * minute, after.startBackAtEpochMillis)
        assertSameSecond(sunsetAt(here) - 30 * minute - walk, after.startBackAtEpochMillis)
        assertEquals(emptyList<Pair<Long, Alert>>(), delivered)
    }

    @Test
    fun `a margin change before any place, or with nothing watched, does nothing`() {
        watch.onMarginChanged(30)
        assertNull(watch.shown.value)
        watch.begin("t1")
        watch.onMarginChanged(30)
        assertEquals(SundownLine.FindingPosition, line())
    }

    /**
     * The defect Amendment 1 names: once the sunset was held, the held countdown kept the computed
     * civil dusk, which is tomorrow's. The line after sunset reads the held countdown, so "dark in"
     * would have counted to tomorrow evening.
     */
    @Test
    fun `after sunset on a recording begun before it, sun set at today's sunset and dark at today's dusk`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val sunset = sunsetAt(here)
        val dusk = civilDuskAfter(sunset, LatLng(here.lat, here.lng))!!
        watch.begin("t1")
        clock.now = sunset - 2 * hour
        while (clock.now < sunset + 5 * minute) {
            tickAt(here)
            clock.now += minute
        }

        val shown = line() as SundownLine.AfterSunset
        assertSameSecond(sunset, shown.sunsetAtEpochMillis)
        assertSameSecond(dusk, shown.civilDuskAtEpochMillis)
        assertTrue("dark is within the hour: ${(shown.civilDuskAtEpochMillis!! - shown.nowEpochMillis) / minute} min", shown.civilDuskAtEpochMillis!! - shown.nowEpochMillis < hour)
    }

    @Test
    fun `a recording started after dark says dark since today's dusk`() {
        val points = walkOut(20)
        track = trackOf(points)
        val here = points.last()
        val dusk = civilDuskAfter(sunsetAt(here), LatLng(here.lat, here.lng))!!
        watch.begin("t1")
        clock.now = dusk + 2 * hour
        tickAt(here)

        assertSameSecond(dusk, (line() as SundownLine.DarkSince).civilDuskAtEpochMillis)
        assertEquals(emptyList<Pair<Long, Alert>>(), delivered)
    }

    @Test
    fun `the last known position gives the line before any live fix`() {
        lastKnown = LocationFix.Update(origin.lat, origin.lng, null, 900f, baseSunset - 5 * hour, provider = FixProvider.NETWORK)
        watch.begin("t1")
        clock.now = baseSunset - 3 * hour
        runBlocking { watch.tick() }
        val shown = line() as SundownLine.BeforeSunset
        assertNull("never a walk back from a last known position", shown.startBackAtEpochMillis)
    }

    @Test
    fun `the end of the recording takes the line away`() {
        watch.begin("t1")
        watch.end("t1")
        assertNull(watch.shown.value)
    }
}
