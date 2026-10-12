package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sundown alerts' fix check and wake-up alarm (dispatch 2026-09-28-796; the owner, RECORD -797: "Yes,
 * same fix (Recommended)"): after each decision the watch asks for a wake-up at the next alert's due time
 * (heads-up, leave-by, sunset), a fix past that time asks for an evaluation at once, and the alarm arriving
 * evaluates; each alert still fires once. On a fake clock with the real countdown and decision, as
 * `SundownWatchTest`; its fakes are repeated here, not shared, for the reason that class gives.
 */
class SundownWatchWakeUpTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val morning = 1_791_010_800_000L

    private class Clock(var now: Long) : CurrentTimeProvider {
        override fun nowEpochMillis() = now
    }

    private class Preferences(var enabled: Boolean = true) : SundownPreferencesRepository {
        override suspend fun getDarknessMarginMinutes() = Result.success(60)
        override suspend fun setDarknessMarginMinutes(minutes: Int) = Result.success(Unit)
        override suspend fun getAlertsEnabled() = Result.success(enabled)
        override suspend fun setAlertsEnabled(enabled: Boolean) = Result.success(Unit).also { this.enabled = enabled }
    }

    private val sunset0 = (ComputeSundownCountdownUseCase()(morning, LatLng(origin.lat, origin.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis
    private val clock = Clock(sunset0 - 6 * hour)
    private val preferences = Preferences()
    private val delivered = mutableListOf<Pair<Long, AlertKind>>()
    private val recorded = mutableListOf<SundownRecordEvent>()
    private val alarmCalls = mutableListOf<Pair<String, Long?>>()
    private var track: Track? = null

    private val watch = SundownWatch(
        alertDelivery = { delivered += clock.now to it.kind },
        clock = clock,
        preferences = preferences,
        readTrack = { id -> Result.success(track?.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = { false },
        errorLog = { _, _, _ -> },
        record = { recorded += it },
        alarms = object : WakeUpAlarms {
            override fun schedule(alarm: WakeUpAlarm, atEpochMillis: Long) = true.also { assertEquals(WakeUpAlarm.SUNDOWN, alarm); alarmCalls += "schedule" to atEpochMillis }
            override fun cancel(alarm: WakeUpAlarm) {
                assertEquals(WakeUpAlarm.SUNDOWN, alarm)
                alarmCalls += "cancel" to null
            }
        },
    )

    private fun walkOut(minutes: Int): List<TrackPoint> =
        (0..minutes * 12).map { i ->
            val metres = i * 5 * 1.2f
            TrackPoint(origin.lat + metres / 111_195.0, origin.lng, null, 4f, sunset0 - 6 * hour + i * 5_000L, 1.2f, 0.5f)
        }

    private fun gpsFix(point: TrackPoint) = point.copy(timestampEpochMillis = clock.now / 1_000L * 1_000L)

    private fun scheduled() = recorded.filterIsInstance<SundownRecordEvent.AlarmScheduled>()

    /** Sets up a measured walk and ticks once, an hour before the leave-by time. Returns (heads-up, leave-by, sunset) as the watch first scheduled them. */
    private fun walking(): Triple<TrackPoint, Long, Long> {
        val points = walkOut(20)
        track = Track("t1", null, points.first().timestampEpochMillis, null, points, originWaypointId = origin.id)
        val here = points.last()
        watch.begin("t1")
        val sunset = (ComputeSundownCountdownUseCase()(morning, LatLng(here.lat, here.lng), morning, 0L) as SundownCountdown.Known).sunsetAtEpochMillis
        clock.now = sunset - 3 * hour
        assertTrue("the first fix asks for an evaluation", watch.onFix(gpsFix(here), FixProvider.GPS))
        runBlocking { watch.tick(EvaluationTrigger.FIX) }
        return Triple(here, scheduled().single().atMillis, sunset)
    }

    @Test
    fun `after a decision, a wake-up is set for the heads-up's due time, then the leave-by's, then sunset's, then cancelled`() {
        val (here, headsUpAt, sunset) = walking()
        assertEquals(SundownAlert.HEADS_UP, scheduled().single().alert)
        assertTrue("the heads-up is ahead: $headsUpAt", headsUpAt > clock.now)
        val leaveBy = headsUpAt + SUNDOWN_HEADS_UP_LEAD_MILLIS

        // Each due time reached by a fix: the fix asks, the evaluation fires that alert, and the next wake-up is set.
        for ((dueAt, expected) in listOf(headsUpAt to AlertKind.HEADS_UP, leaveBy to AlertKind.LEAVE_BY, sunset to AlertKind.SUNSET)) {
            clock.now = dueAt - 1_000L
            assertFalse("a second early, no ask", watch.onFix(gpsFix(here), FixProvider.GPS))
            clock.now = dueAt + 1_000L
            assertTrue("past the due time, a fix asks", watch.onFix(gpsFix(here), FixProvider.GPS))
            assertFalse("asked once", watch.onFix(gpsFix(here), FixProvider.GPS))
            runBlocking { watch.tick(EvaluationTrigger.FIX) }
            assertEquals(expected, delivered.last().second)
        }
        assertEquals("each once", listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET), delivered.map { it.second })
        assertEquals(listOf(SundownAlert.HEADS_UP, SundownAlert.LEAVE_BY, SundownAlert.SUNSET), scheduled().map { it.alert })
        assertEquals("all given: the wake-up cancelled", "cancel", alarmCalls.last().first)
        assertEquals(listOf(SundownAlert.HEADS_UP, SundownAlert.LEAVE_BY, SundownAlert.SUNSET), recorded.filterIsInstance<SundownRecordEvent.Fired>().map { it.alert })
        assertTrue(recorded.filterIsInstance<SundownRecordEvent.Evaluated>().all { it.trigger == EvaluationTrigger.FIX })
    }

    @Test
    fun `the wake-up arriving at the heads-up's time fires it, recorded as woken by the alarm`() {
        val (here, headsUpAt, _) = walking()
        clock.now = headsUpAt + 2 * minute
        watch.onFix(gpsFix(here), FixProvider.GPS).let { } // keeps the walk back fresh; its answer is not what is tested
        runBlocking { watch.onAlarm() }
        assertEquals(listOf(AlertKind.HEADS_UP), delivered.map { it.second })
        assertTrue(SundownRecordEvent.AlarmDelivered("t1") in recorded)
        assertEquals(EvaluationTrigger.ALARM, recorded.filterIsInstance<SundownRecordEvent.Evaluated>().last().trigger)
        assertEquals("the next wake-up is the leave-by's", SundownAlert.LEAVE_BY, scheduled().last().alert)
    }

    @Test
    fun `alerts turned off cancel the wake-up, and stopping the recording cancels it`() {
        walking()
        preferences.enabled = false
        runBlocking { watch.tick() }
        assertEquals("cancel", alarmCalls.last().first)
        preferences.enabled = true
        runBlocking { watch.tick() }
        assertEquals("schedule", alarmCalls.last().first)
        watch.end("t1")
        assertEquals("cancel", alarmCalls.last().first)
        assertEquals(SundownRecordEvent.AlarmCancelled("t1"), recorded.last())
    }

    @Test
    fun `a due time that moves by under a minute does not ask for the wake-up again`() {
        val (here, _, _) = walking()
        repeat(5) {
            clock.now += 15_000L
            watch.onFix(gpsFix(here), FixProvider.GPS)
            runBlocking { watch.tick() }
        }
        assertEquals("one wake-up asked for: ${alarmCalls}", 1, alarmCalls.size)
    }

    @Test
    fun `a wake-up with nothing watched is recorded and does nothing`() {
        runBlocking { watch.onAlarm() }
        assertEquals(listOf<SundownRecordEvent>(SundownRecordEvent.AlarmDelivered(null)), recorded.toList())
        assertTrue(delivered.isEmpty())
    }
}
