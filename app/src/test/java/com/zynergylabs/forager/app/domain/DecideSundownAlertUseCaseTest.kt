package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.SundownCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The three moments of dispatch 2026-09-28-516 (RECORD -515): the heads-up 30 minutes before the
 * leave-by time, the leave-by time (sunset, minus the darkness margin, minus the walk back), and
 * sunset. Each once; a moment already behind when a later one fires is spent without sounding.
 */
class DecideSundownAlertUseCaseTest {

    private val decide = DecideSundownAlertUseCase()

    private val sunset = 1789237318000L
    private val minute = 60_000L
    private val hour = 60 * minute

    /** Sunset fixed; the darkness margin one hour unless given. */
    private fun countdownAt(nowEpochMillis: Long, marginMillis: Long = hour) = SundownCountdown.Known(
        nowEpochMillis = nowEpochMillis,
        sunsetAtEpochMillis = sunset,
        civilDuskAtEpochMillis = sunset + 35 * minute,
        turnaroundAtEpochMillis = sunset - marginMillis,
        fixAgeMillis = 0L,
    )

    // With a 90-minute walk back and the one-hour margin, the leave-by time is sunset minus 2 h 30.
    private val walkBack = 90 * minute
    private val leaveBy = sunset - hour - walkBack

    @Test
    fun `the leave-by time is sunset minus the margin minus the walk back`() {
        val decision = decide(countdownAt(leaveBy - 2 * hour), walkBack, alreadyFired = emptySet())
        assertEquals(leaveBy, decision.leaveByAtEpochMillis)
        assertNull(decision.fire)
        assertEquals(emptySet<SundownAlert>(), decision.spent)
    }

    @Test
    fun `with the walk back unknown, the leave-by time falls back to sunset minus the margin`() {
        val decision = decide(countdownAt(sunset - 3 * hour), walkBackMillis = null, alreadyFired = emptySet())
        assertEquals(sunset - hour, decision.leaveByAtEpochMillis)
    }

    @Test
    fun `nothing fires until 30 minutes before the leave-by time`() {
        assertNull(decide(countdownAt(leaveBy - 30 * minute - 1), walkBack, emptySet()).fire)
    }

    @Test
    fun `the heads-up fires 30 minutes before the leave-by time, once`() {
        val first = decide(countdownAt(leaveBy - 30 * minute), walkBack, emptySet())
        assertEquals(SundownAlert.HEADS_UP, first.fire)
        assertEquals(setOf(SundownAlert.HEADS_UP), first.spent)

        val second = decide(countdownAt(leaveBy - 10 * minute), walkBack, first.spent)
        assertNull("a moment must not re-fire on every evaluation", second.fire)
        assertEquals(first.spent, second.spent)
    }

    @Test
    fun `heads-up, leave-by and sunset each fire once, in order`() {
        val fired = mutableListOf<SundownAlert>()
        var spent = emptySet<SundownAlert>()
        var now = leaveBy - 45 * minute
        while (now <= sunset + hour) {
            val decision = decide(countdownAt(now), walkBack, spent)
            decision.fire?.let(fired::add)
            spent = decision.spent
            now += minute
        }
        assertEquals(listOf(SundownAlert.HEADS_UP, SundownAlert.LEAVE_BY, SundownAlert.SUNSET), fired)
    }

    @Test
    fun `a start already past the leave-by time fires the leave-by at once and spends the heads-up`() {
        val decision = decide(countdownAt(leaveBy + 5 * minute), walkBack, alreadyFired = emptySet())
        assertEquals(SundownAlert.LEAVE_BY, decision.fire)
        assertEquals(setOf(SundownAlert.HEADS_UP, SundownAlert.LEAVE_BY), decision.spent)
        assertNull("the heads-up cannot fire late", decide(countdownAt(leaveBy + 6 * minute), walkBack, decision.spent).fire)
    }

    @Test
    fun `a walk back that grows moves the leave-by time earlier, and can move it into the past`() {
        val now = sunset - 2 * hour - 5 * minute // before the short walk's heads-up, at sunset minus 2 h
        val short = decide(countdownAt(now), 30 * minute, emptySet())
        assertEquals(sunset - hour - 30 * minute, short.leaveByAtEpochMillis)
        assertNull(short.fire)

        val long = decide(countdownAt(now), 70 * minute, short.spent)
        assertEquals("the further out, the earlier", sunset - hour - 70 * minute, long.leaveByAtEpochMillis)
        assertEquals("now past it: the leave-by fires at once", SundownAlert.LEAVE_BY, long.fire)
        assertEquals(setOf(SundownAlert.HEADS_UP, SundownAlert.LEAVE_BY), long.spent)
    }

    @Test
    fun `all three already past fires only sunset, and burns the other two undelivered`() {
        val decision = decide(countdownAt(sunset + 10 * minute), walkBack, alreadyFired = emptySet())
        assertEquals(SundownAlert.SUNSET, decision.fire)
        assertEquals(setOf(SundownAlert.HEADS_UP, SundownAlert.LEAVE_BY, SundownAlert.SUNSET), decision.spent)
        assertNull("and then nothing more", decide(countdownAt(sunset + 2 * hour), walkBack, decision.spent).fire)
    }

    @Test
    fun `the states with nothing to say do not alert`() {
        for (state in listOf(
            SundownCountdown.NoPositionYet,
            SundownCountdown.SunDoesNotSet,
            SundownCountdown.SunStaysDown,
        )) {
            val decision = decide(state, walkBack, alreadyFired = emptySet())
            assertNull("$state must not alert", decision.fire)
            assertEquals("$state must not mark anything spent", emptySet<SundownAlert>(), decision.spent)
            assertNull(decision.leaveByAtEpochMillis)
        }
    }
}
