package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackByWatch] through the calls the recording service makes ([BackByWatch.begin],
 * [BackByWatch.onFix], [BackByWatch.tick], [BackByWatch.end], [BackByWatch.imBack],
 * [BackByWatch.later]) and the screen's ([BackByWatch.set], [BackByWatch.clear]), on a fake clock. The
 * reading is what reaches [AlertDelivery] and what [BackByWatch.shown] publishes.
 *
 * Dispatch 2026-09-28-645, plan task T15; Amendment 1 (RECORD -646): it ends only by the walker's
 * choice, arrival counted only after Return, by the sundown alerts' rule.
 */
class BackByWatchTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val start = 1_791_050_400_000L

    private class Clock(var now: Long) : CurrentTimeProvider {
        override fun nowEpochMillis() = now
    }

    private val origin = Waypoint("origin", 45.0, -122.0, null, "Start", "", 0L)
    private val delivered = mutableListOf<Pair<Long, Alert>>()
    private val clock = Clock(start)
    private var returning = false
    private val logged = mutableListOf<String>()
    private val track = Track("t1", null, start, null, listOf(TrackPoint(45.0, -122.0, null, 4f, start)), originWaypointId = origin.id)

    private val watch = BackByWatch(
        alertDelivery = { delivered += clock.now to it },
        clock = clock,
        readTrack = { id -> Result.success(track.takeIf { it.id == id }) },
        readWaypoint = { id -> Result.success(origin.takeIf { it.id == id }) },
        isReturning = { returning },
        errorLog = { _, message, _ -> logged += message },
        record = { recorded += it },
        alarms = object : WakeUpAlarms {
            override fun schedule(alarm: WakeUpAlarm, atEpochMillis: Long): Boolean = true.also { alarmCalls += "schedule $alarm $atEpochMillis" }
            override fun cancel(alarm: WakeUpAlarm) {
                alarmCalls += "cancel $alarm"
            }
        },
    )

    private val alarmCalls = mutableListOf<String>()

    private val recorded = mutableListOf<BackByRecordEvent>()

    private fun tick() = runBlocking { watch.tick() }

    /** Ticks every 15 s, as the service does, from now to [to]. */
    private fun tickUntil(to: Long) {
        while (clock.now <= to) {
            tick()
            clock.now += 15_000L
        }
    }

    /** A GPS fix [metresNorth] of the start, stamped now on the whole second. */
    private fun fixAt(metresNorth: Double, accuracy: Float = 4f) =
        TrackPoint(origin.lat + metresNorth / 111_195.0, origin.lng, null, accuracy, clock.now / 1_000L * 1_000L)

    private fun times() = delivered.map { it.first }

    // ── The alert ──

    @Test
    fun `nothing before the time, one alert at it, overriding silence, saying the time and the recording`() {
        watch.begin("t1")
        val backBy = start + hour
        assertTrue(watch.set("t1", backBy))
        tickUntil(backBy - 15_000L)
        assertTrue("nothing before the time", delivered.isEmpty())
        tickUntil(backBy + 30 * minute - 15_000L)
        assertEquals("one alert, within one tick of the time", 1, delivered.size)
        val (firedAt, alert) = delivered.single()
        assertTrue("at most 15 s late: ${firedAt - backBy}", firedAt - backBy in 0..15_000L)
        assertEquals(AlertKind.BACK_BY, alert.kind)
        assertTrue("breaks through silent mode, as the sunset alert", alert.overridesSilence)
        assertEquals(BackByAlertDetail(trackId = "t1", backByAtEpochMillis = backBy), alert.backBy)
    }

    @Test
    fun `off unless set`() {
        watch.begin("t1")
        tickUntil(start + 5 * hour)
        assertTrue(delivered.isEmpty())
        assertNull(watch.shown.value)
    }

    // ── The alert's two buttons ──

    @Test
    fun `I'm back ends the reminder and nothing more fires`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        tickUntil(start + hour)
        assertEquals(1, delivered.size)
        watch.imBack("t1")
        assertNull("the reminder is over", watch.shown.value)
        tickUntil(start + 4 * hour)
        assertEquals("nothing more", 1, delivered.size)
    }

    /** The owner: "+30 min" alerts again in 30 minutes; Amendment 1: the strip then shows the new time. */
    @Test
    fun `+30 min moves the time to 30 minutes from now, which is shown, and alerts again then`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        tickUntil(start + hour)
        val tappedAt = start + hour + 2 * minute
        clock.now = tappedAt
        watch.later("t1")
        assertEquals("the new time is shown", tappedAt + 30 * minute, watch.shown.value?.backByAtEpochMillis)
        tickUntil(tappedAt + 30 * minute + 15_000L)
        assertEquals("alerted again", 2, delivered.size)
        assertTrue("at the new time: ${times()}", times()[1] - (tappedAt + 30 * minute) in 0..15_000L)
        assertEquals(tappedAt + 30 * minute, delivered[1].second.backBy?.backByAtEpochMillis)
    }

    @Test
    fun `a button for another recording changes nothing`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        watch.imBack("t0")
        watch.later("t0")
        assertEquals(start + hour, watch.shown.value?.backByAtEpochMillis)
    }

    // ── Set, change, clear ──

    @Test
    fun `set, change and clear`() {
        watch.begin("t1")
        watch.set("t1", start + 2 * hour)
        assertEquals(BackByShown("t1", start + 2 * hour, start), watch.shown.value)
        watch.set("t1", start + hour)
        assertEquals("changed", start + hour, watch.shown.value?.backByAtEpochMillis)
        watch.clear("t1")
        assertNull("cleared", watch.shown.value)
        tickUntil(start + 3 * hour)
        assertTrue("nothing fires once cleared", delivered.isEmpty())
    }

    @Test
    fun `changing the time after it fired arms it again for the new time`() {
        watch.begin("t1")
        watch.set("t1", start + minute)
        tickUntil(start + 2 * minute)
        assertEquals(1, delivered.size)
        watch.set("t1", clock.now + hour)
        tickUntil(clock.now + hour + 15_000L)
        assertEquals(2, delivered.size)
    }

    @Test
    fun `every tick republishes with the clock, so the last-hour window opens on time`() {
        watch.begin("t1")
        watch.set("t1", start + 2 * hour)
        assertFalse(watch.shown.value!!.lineShown)
        clock.now = start + hour + 15_000L
        tick()
        assertEquals(clock.now, watch.shown.value?.nowEpochMillis)
        assertTrue(watch.shown.value!!.lineShown)
    }

    // ── It ends with the recording ──

    @Test
    fun `stopping the recording ends it, and nothing fires`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        watch.end("t1")
        assertNull(watch.shown.value)
        tickUntil(start + 2 * hour)
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `a late stop for an old recording does not end a new one`() {
        watch.begin("t2")
        watch.set("t2", start + hour)
        watch.end("t1")
        assertEquals(start + hour, watch.shown.value?.backByAtEpochMillis)
    }

    /** The service's onDestroy, which a process death also ends in: nothing survives it (the verify report, item 6). */
    @Test
    fun `the service destroyed ends it, and a new recording starts with none`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        watch.end(null)
        assertNull(watch.shown.value)
        watch.begin("t2")
        assertNull("a new recording has no Back by", watch.shown.value)
        tickUntil(start + 2 * hour)
        assertTrue(delivered.isEmpty())
    }

    // ── Set a moment before the service begins ──

    @Test
    fun `a time set just before the service begins is kept for the same track, dropped for another`() {
        assertTrue(watch.set("t1", start + hour))
        watch.begin("t1")
        assertEquals("kept", start + hour, watch.shown.value?.backByAtEpochMillis)

        val other = BackByWatch({ }, clock, { Result.success(null) }, { Result.success(null) }, { false }, { _, _, _ -> })
        other.set("t1", start + hour)
        other.begin("t2")
        assertNull("dropped for another track", other.shown.value)
    }

    @Test
    fun `once begun for one recording, a set for another is refused`() {
        watch.begin("t1")
        assertFalse(watch.set("t2", start + hour))
        assertNull(watch.shown.value)
    }

    // ── Arrival: the sundown alerts' rule, Return required (Amendment 1) ──

    @Test
    fun `after Return, a fresh GPS fix at the start ends it`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        returning = true
        watch.onFix(fixAt(5.0), FixProvider.GPS)
        tick()
        assertNull("arrived: the reminder is over", watch.shown.value)
        tickUntil(start + 2 * hour)
        assertTrue(delivered.isEmpty())
    }

    /** No "back at the car" is inferred: at the start, without Return, it stays set and fires. */
    @Test
    fun `without Return, being at the start does not end it`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        returning = false
        while (clock.now <= start + hour + 15_000L) {
            watch.onFix(fixAt(5.0), FixProvider.GPS)
            tick()
            clock.now += 15_000L
        }
        assertEquals("still fires", 1, delivered.size)
    }

    @Test
    fun `after Return, a network fix at the start does not count`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        returning = true
        watch.onFix(fixAt(5.0, accuracy = 40f), FixProvider.NETWORK)
        tick()
        assertEquals(start + hour, watch.shown.value?.backByAtEpochMillis)
    }

    @Test
    fun `after Return, a GPS fix more than five minutes old does not count`() {
        watch.begin("t1")
        watch.set("t1", start + 2 * hour)
        returning = true
        watch.onFix(fixAt(5.0), FixProvider.GPS)
        clock.now += 5 * minute
        tick()
        assertEquals(start + 2 * hour, watch.shown.value?.backByAtEpochMillis)
    }

    @Test
    fun `after Return, a fix outside arrival's reach does not count`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        returning = true
        watch.onFix(fixAt(40.0), FixProvider.GPS)
        tick()
        assertEquals(start + hour, watch.shown.value?.backByAtEpochMillis)
    }

    @Test
    fun `a partly delivered alert is logged`() {
        val partial = BackByWatch(
            alertDelivery = object : AlertDelivery {
                override fun deliver(alert: Alert) = Unit
                override fun deliverReporting(alert: Alert) = AlertDeliveryOutcome(false, "POST_NOTIFICATIONS denied", true, null)
            },
            clock = clock,
            readTrack = { Result.success(null) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, message, _ -> logged += message },
        )
        partial.begin("t1")
        partial.set("t1", start)
        runBlocking { partial.tick() }
        assertTrue(logged.toString(), logged.any { it.contains("only partly delivered") && it.contains("POST_NOTIFICATIONS denied") })
    }

    /**
     * Merged with dispatch 2026-09-28-685 (RECORD -687): a buzz Android drops is logged as skipped with its reason, as the
     * sundown alerts log theirs, and never as issued.
     */
    @Test
    fun `a skipped buzz is logged as skipped, with its reason, not as issued`() {
        val skipped = BackByWatch(
            alertDelivery = object : AlertDelivery {
                override fun deliver(alert: Alert) = Unit
                override fun deliverReporting(alert: Alert) = AlertDeliveryOutcome(true, null, false, null, VIBRATION_SKIPPED_DO_NOT_DISTURB)
            },
            clock = clock,
            readTrack = { Result.success(null) },
            readWaypoint = { Result.success(null) },
            isReturning = { false },
            errorLog = { _, message, _ -> logged += message },
        )
        skipped.begin("t1")
        skipped.set("t1", start)
        runBlocking { skipped.tick() }
        assertEquals(
            listOf("The back-by alert was only partly delivered: notification posted, vibration skipped: Do Not Disturb."),
            logged,
        )
    }

    // ── The record (dispatch 2026-09-28-796): what happened, in order, never where ──

    @Test
    fun `the record says started, set, each evaluation, fired with its delivery, and ended by stop`() {
        watch.begin("t1")
        val backBy = start + minute
        watch.set("t1", backBy)
        tickUntil(backBy)
        watch.end("t1")
        val kinds = recorded.map { it::class.simpleName }
        assertEquals(BackByRecordEvent.Started("t1", null), recorded.first())
        assertEquals(BackByRecordEvent.Set("t1", backBy, accepted = true, watchedTrackId = null), recorded[1])
        val evaluations = recorded.filterIsInstance<BackByRecordEvent.Evaluated>()
        assertEquals("one evaluation per tick, 15 s apart, from start to the time: $kinds", 5, evaluations.size)
        assertEquals(listOf(false, false, false, false, true), evaluations.map { it.due })
        assertTrue(evaluations.all { it.trigger == EvaluationTrigger.TIMER })
        assertEquals(BackByRecordEvent.Fired("t1", backBy, null), recorded.filterIsInstance<BackByRecordEvent.Fired>().single())
        assertEquals(BackByRecordEvent.Ended("t1", BackByEndReason.STOP), recorded.filterIsInstance<BackByRecordEvent.Ended>().single())
        assertEquals(BackByRecordEvent.AlarmCancelled("t1"), recorded.last())
    }

    @Test
    fun `the record names each way it ends, and a refused set`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        watch.clear("t1")
        watch.set("t1", start + hour)
        watch.imBack("t1")
        watch.set("t1", start + hour)
        watch.later("t1")
        returning = true
        watch.onFix(fixAt(5.0), FixProvider.GPS)
        tick()
        assertFalse("refused for another recording", watch.set("t2", start + hour))
        watch.set("t1", start + hour)
        watch.end(null)
        val ends = recorded.filterIsInstance<BackByRecordEvent.Ended>().map { it.reason }
        assertEquals(listOf(BackByEndReason.CLEAR, BackByEndReason.IM_BACK, BackByEndReason.ARRIVAL, BackByEndReason.SERVICE_DESTROYED), ends)
        assertEquals(BackByRecordEvent.Later("t1", start + BACK_BY_LATER_MILLIS), recorded.filterIsInstance<BackByRecordEvent.Later>().single())
        assertTrue(BackByRecordEvent.Set("t2", start + hour, accepted = false, watchedTrackId = "t1") in recorded)
    }

    @Test
    fun `a stop with no time set records no end`() {
        watch.begin("t1")
        watch.end("t1")
        assertEquals(listOf<BackByRecordEvent>(BackByRecordEvent.Started("t1", null)), recorded.toList())
    }

    // ── A fix, and the wake-up alarm (dispatch 2026-09-28-796; the owner, RECORD -797) ──

    @Test
    fun `a fix asks for an evaluation only once the time has come and not yet alerted`() {
        watch.begin("t1")
        val backBy = start + hour
        watch.set("t1", backBy)
        clock.now = backBy - 1_000L
        assertFalse("before the time", watch.onFix(fixAt(2_000.0), FixProvider.GPS))
        clock.now = backBy
        assertTrue("at the time", watch.onFix(fixAt(2_000.0), FixProvider.GPS))
        runBlocking { watch.tick(EvaluationTrigger.FIX) }
        assertEquals("fired, at the fix", listOf(backBy), times())
        assertFalse("alerted: no more asking", watch.onFix(fixAt(2_000.0), FixProvider.GPS))
        assertTrue(BackByRecordEvent.Evaluated("t1", backBy, due = true, trigger = EvaluationTrigger.FIX) in recorded)
    }

    @Test
    fun `nothing set, a fix asks for nothing`() {
        watch.begin("t1")
        clock.now += 2 * hour
        assertFalse(watch.onFix(fixAt(0.0), FixProvider.GPS))
    }

    @Test
    fun `the wake-up follows the time through set, +30 min, Clear, arrival, stop and the service destroyed`() {
        watch.begin("t1")
        watch.set("t1", start + hour)
        watch.later("t1")
        watch.clear("t1")
        watch.set("t1", start + hour)
        returning = true
        watch.onFix(fixAt(5.0), FixProvider.GPS)
        tick()
        returning = false
        watch.set("t1", start + 2 * hour)
        watch.end("t1")
        watch.begin("t2")
        watch.set("t2", start + hour)
        watch.end(null)
        assertEquals(
            listOf(
                "schedule BACK_BY ${start + hour}",
                "schedule BACK_BY ${start + BACK_BY_LATER_MILLIS}",
                "cancel BACK_BY",
                "schedule BACK_BY ${start + hour}",
                "cancel BACK_BY",
                "schedule BACK_BY ${start + 2 * hour}",
                "cancel BACK_BY",
                "schedule BACK_BY ${start + hour}",
                "cancel BACK_BY",
            ),
            alarmCalls,
        )
        assertEquals(5, recorded.filterIsInstance<BackByRecordEvent.AlarmScheduled>().count { it.accepted })
        assertEquals(4, recorded.filterIsInstance<BackByRecordEvent.AlarmCancelled>().size)
    }

    @Test
    fun `a refused set asks for no wake-up`() {
        watch.begin("t1")
        assertFalse(watch.set("t2", start + hour))
        assertTrue(alarmCalls.isEmpty())
    }

    @Test
    fun `the wake-up arriving at the time fires the alert, recorded as woken by the alarm`() {
        watch.begin("t1")
        val backBy = start + hour
        watch.set("t1", backBy)
        clock.now = backBy + 3 * minute
        runBlocking { watch.onAlarm() }
        assertEquals("fired by the alarm, minutes late as an inexact alarm may be", listOf(backBy + 3 * minute), times())
        assertTrue(BackByRecordEvent.AlarmDelivered("t1") in recorded)
        assertTrue(BackByRecordEvent.Evaluated("t1", backBy, due = true, trigger = EvaluationTrigger.ALARM) in recorded)
    }

    @Test
    fun `a wake-up with nothing set is recorded and does nothing`() {
        runBlocking { watch.onAlarm() }
        assertEquals(listOf<BackByRecordEvent>(BackByRecordEvent.AlarmDelivered(null)), recorded.toList())
        assertTrue(delivered.isEmpty())
    }
}
