package com.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wording behind the offline banner and the recent-searches picker, at each boundary where it
 * changes unit or switches between singular and plural — the places a label goes wrong ("1 hours
 * ago", "0 minutes ago") without anybody noticing in a screenshot.
 *
 * A plain JVM test with no Compose in it: [relativeTimeLabel] takes both instants as arguments
 * precisely so this is possible (see its doc comment and
 * [com.forager.app.domain.CurrentTimeProvider]).
 */
class RelativeTimeLabelTest {

    private val now = 1_700_000_000_000L

    private fun labelFor(agoMillis: Long) = relativeTimeLabel(now - agoMillis, now)

    @Test
    fun `under a minute reads as just now rather than zero minutes`() {
        assertEquals("just now", labelFor(0))
        assertEquals("just now", labelFor(59 * SECOND))
    }

    @Test
    fun `minutes are singular at one and plural after`() {
        assertEquals("1 minute ago", labelFor(MINUTE))
        assertEquals("2 minutes ago", labelFor(2 * MINUTE))
        assertEquals("59 minutes ago", labelFor(59 * MINUTE + 59 * SECOND))
    }

    @Test
    fun `an hour is the next unit up, not sixty minutes`() {
        assertEquals("1 hour ago", labelFor(HOUR))
        assertEquals("3 hours ago", labelFor(3 * HOUR))
        assertEquals("23 hours ago", labelFor(23 * HOUR + 59 * MINUTE))
    }

    @Test
    fun `a day is the next unit up, not twenty-four hours`() {
        assertEquals("1 day ago", labelFor(DAY))
        assertEquals("2 days ago", labelFor(2 * DAY))
        assertEquals("30 days ago", labelFor(30 * DAY))
    }

    /** A timestamp from the future reads as "just now" rather than as a negative age. */
    @Test
    fun `a future timestamp reads as just now`() {
        assertEquals("just now", relativeTimeLabel(now + DAY, now))
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
