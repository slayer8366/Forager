package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RECORD -845: Back by's and the sundown alerts' arrival behaviour stays exactly as it was once a return
 * can end by itself. Both used to judge arrival only while [ReturnWatch] said "returning", and a return
 * now stops returning at the start, on the service's next fix, which is up to 15 s before either
 * watch's next tick. They read [ReturnWatch.arrivalJudgedFor] instead, wired here as `AppContainer`
 * wires it (`isReturning = returnWatch::arrivalJudgedFor`), so a return ended on arrival still counts.
 *
 * The other half, "as today", is pinned too: a return the walker ended before arriving still means
 * neither watch judges arrival.
 *
 * Through each watch's service-facing calls (begin, onFix, tick) on a fake clock; the return through
 * [ReturnWatch]'s. The start is [origin]; arrival there is the walker's GPS fix at it.
 */
class ReturnArrivalAlertsTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val morning = 1_791_010_800_000L // 2026-10-03T07:00:00Z
    private val sunset = (ComputeSundownCountdownUseCase()(morning, LatLng(origin.lat, origin.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis

    private class Clock(var now: Long) : CurrentTimeProvider {
        override fun nowEpochMillis() = now
    }

    private class Preferences : SundownPreferencesRepository {
        override suspend fun getDarknessMarginMinutes() = Result.success(60)
        override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit)
        override suspend fun getAlertsEnabled() = Result.success(true)
        override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit)
    }

    private val clock = Clock(sunset - 3 * hour)
    private val returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), { })

    /** A walk north from [origin] at 1.2 m/s, a stored point every 5 s, for 20 minutes: 1.4 km. */
    private val walkOut = (0..20 * 12).map { i -> TrackPoint(origin.lat + i * 6.0 / 111_195.0, origin.lng, null, 4f, sunset - 6 * hour + i * 5_000L, 1.2f, 0.5f) }
    private val track = Track("t1", null, walkOut.first().timestampEpochMillis, null, walkOut, originWaypointId = origin.id)

    private val sundownAlerts = mutableListOf<AlertKind>()
    private val sundown = SundownWatch(
        alertDelivery = { sundownAlerts += it.kind },
        clock = clock,
        preferences = Preferences(),
        readTrack = { id -> Result.success(track.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = returnWatch::arrivalJudgedFor,
        errorLog = { _, _, _ -> },
    )

    private val backByRecord = mutableListOf<BackByRecordEvent>()
    private val backBy = BackByWatch(
        alertDelivery = { },
        clock = clock,
        readTrack = { id -> Result.success(track.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = returnWatch::arrivalJudgedFor,
        errorLog = { _, _, _ -> },
        record = { backByRecord += it },
    )

    private fun gps(point: TrackPoint) = point.copy(timestampEpochMillis = clock.now / 1_000L * 1_000L)
    private val atStart = TrackPoint(origin.lat, origin.lng, null, 4f, 0L)
    private val farOut = walkOut.last()

    /** The recording service's fan-out of one raw GPS fix: the return watch, sundown, Back by. */
    private fun fixToAll(point: TrackPoint) {
        val fix = gps(point)
        returnWatch.onFix(fix, FixProvider.GPS)
        sundown.onFix(fix, FixProvider.GPS)
        backBy.onFix(fix, FixProvider.GPS)
    }

    private fun begin() {
        returnWatch.begin("t1", TrackRecordingMode.HIGH_ACCURACY)
        walkOut.forEach(returnWatch::onKeptPoint)
        returnWatch.setStartPoint("t1", TrackPoint(origin.lat, origin.lng, null, null, walkOut.first().timestampEpochMillis))
        sundown.begin("t1")
        backBy.begin("t1")
    }

    /** Return far out, then the walk back to the start, a fix a minute; the return ends at the last. */
    private fun returnAndArrive() {
        fixToAll(farOut)
        returnWatch.startReturn("t1")
        clock.now += minute
        fixToAll(walkOut[walkOut.size / 2])
        clock.now += minute
        fixToAll(atStart)
    }

    @Test
    fun `Back by still ends on arrival when the return has already ended there`() {
        begin()
        backBy.set("t1", sunset) // hours ahead: due neither before nor at arrival
        returnAndArrive()
        assertFalse("precondition: the return ended at the start", returnWatch.state.value.isReturning)

        runBlocking { backBy.tick() } // the service's next 15 s tick, after the return has ended

        assertNull("arrived: the reminder is over", backBy.shown.value)
        assertEquals(listOf(BackByRecordEvent.Ended("t1", BackByEndReason.ARRIVAL)), backByRecord.filterIsInstance<BackByRecordEvent.Ended>())
    }

    @Test
    fun `Back by does not end at the start after a return the walker ended themselves, as before`() {
        begin()
        backBy.set("t1", sunset)
        fixToAll(farOut)
        returnWatch.startReturn("t1")
        returnWatch.stopReturn("t1")
        clock.now += minute
        fixToAll(atStart)

        runBlocking { backBy.tick() }

        assertNotNull("no return, so no arrival: the reminder stands", backBy.shown.value)
    }

    @Test
    fun `the sundown alerts still stop on arrival when the return has already ended there`() {
        begin()
        returnAndArrive()
        assertFalse("precondition: the return ended at the start", returnWatch.state.value.isReturning)
        runBlocking { sundown.tick() } // the service's next 15 s tick, after the return has ended

        // On through sunset, standing far out again (even walking out again): nothing more fires.
        while (clock.now <= sunset + hour) {
            sundown.onFix(gps(farOut), FixProvider.GPS)
            runBlocking { sundown.tick() }
            clock.now += minute
        }
        assertEquals(emptyList<AlertKind>(), sundownAlerts)
    }

    @Test
    fun `the sundown alerts still fire at the start after a return the walker ended themselves, as before`() {
        begin()
        fixToAll(farOut)
        returnWatch.startReturn("t1")
        returnWatch.stopReturn("t1")

        clock.now = sunset - 3 * hour
        while (clock.now <= sunset + minute) {
            sundown.onFix(gps(atStart), FixProvider.GPS)
            runBlocking { sundown.tick() }
            clock.now += minute
        }
        assertEquals(listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), sundownAlerts)
    }
}
