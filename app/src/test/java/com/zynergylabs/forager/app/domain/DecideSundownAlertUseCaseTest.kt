package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.SundownCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecideSundownAlertUseCaseTest {

    private val decide = DecideSundownAlertUseCase()

    private val sunset = 1789237318000L
    private val hour = 60 * 60 * 1000L

    private fun countdownAt(nowEpochMillis: Long, marginMillis: Long = hour) = SundownCountdown.Known(
        nowEpochMillis = nowEpochMillis,
        sunsetAtEpochMillis = sunset,
        civilDuskAtEpochMillis = sunset + 35 * 60_000L,
        turnaroundAtEpochMillis = sunset - marginMillis,
        fixAgeMillis = 0L,
    )

    @Test
    fun `nothing fires before the turnaround`() {
        val decision = decide(countdownAt(sunset - 2 * hour), alreadyFired = emptySet())
        assertNull(decision.fire)
        assertEquals(emptySet<SundownAlert>(), decision.spent)
    }

    @Test
    fun `the turnaround fires once and then stays quiet`() {
        val past = countdownAt(sunset - 30 * 60_000L)

        val first = decide(past, alreadyFired = emptySet())
        assertEquals(SundownAlert.TURNAROUND, first.fire)
        assertEquals(setOf(SundownAlert.TURNAROUND), first.spent)

        val second = decide(past, alreadyFired = first.spent)
        assertNull("a moment must not re-fire on every evaluation", second.fire)
        assertEquals(first.spent, second.spent)
    }

    @Test
    fun `sunset fires after the turnaround has already gone`() {
        val afterTurnaround = decide(countdownAt(sunset - 30 * 60_000L), emptySet())
        val afterSunset = decide(countdownAt(sunset + 60_000L), alreadyFired = afterTurnaround.spent)

        assertEquals(SundownAlert.SUNSET, afterSunset.fire)
        assertEquals(setOf(SundownAlert.TURNAROUND, SundownAlert.SUNSET), afterSunset.spent)
        assertNull("and then nothing more", decide(countdownAt(sunset + 2 * hour), afterSunset.spent).fire)
    }

    @Test
    fun `both already past fires only sunset, and burns the turnaround undelivered`() {
        // A recording started after dark, or a margin wider than the day that was left. Two alarms
        // in one second is noise, and "turn back now" has stopped being true.
        val decision = decide(countdownAt(sunset + 10 * 60_000L), alreadyFired = emptySet())

        assertEquals(SundownAlert.SUNSET, decision.fire)
        assertEquals(
            "the turnaround is spent without ever being delivered, so it cannot fire late",
            setOf(SundownAlert.TURNAROUND, SundownAlert.SUNSET),
            decision.spent,
        )
        assertNull(decide(countdownAt(sunset + 20 * 60_000L), decision.spent).fire)
    }

    @Test
    fun `a zero margin still fires only one alert`() {
        val decision = decide(countdownAt(sunset + 1, marginMillis = 0L), alreadyFired = emptySet())
        assertEquals(SundownAlert.SUNSET, decision.fire)
    }

    @Test
    fun `the states with nothing to say do not alert`() {
        for (state in listOf(
            SundownCountdown.NoPositionYet,
            SundownCountdown.SunDoesNotSet,
            SundownCountdown.SunStaysDown,
        )) {
            val decision = decide(state, alreadyFired = emptySet())
            assertNull("$state must not alert", decision.fire)
            assertEquals("$state must not mark anything spent", emptySet<SundownAlert>(), decision.spent)
        }
    }
}
