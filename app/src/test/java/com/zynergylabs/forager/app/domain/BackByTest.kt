package com.zynergylabs.forager.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Back by's rules with no state (dispatch 2026-09-28-645, plan task T15; Amendment 1, RECORD -646):
 * the moment each menu choice names, and the strip's last-hour window.
 */
class BackByTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val zone = ZoneId.of("America/Los_Angeles")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(2026, 10, 7, 14, 10) + 25_000L // 2:10:25 PM, Pacific

    @Test
    fun `+1 h, +2 h and +3 h count from now`() {
        assertEquals(now + hour, backByAtFor(BackByChoice.HoursFromNow(1), now, zone))
        assertEquals(now + 2 * hour, backByAtFor(BackByChoice.HoursFromNow(2), now, zone))
        assertEquals(now + 3 * hour, backByAtFor(BackByChoice.HoursFromNow(3), now, zone))
        assertEquals("the menu's three, in order", listOf(1, 2, 3), BACK_BY_QUICK_HOURS)
    }

    @Test
    fun `a picked time later today is today's`() {
        assertEquals(at(2026, 10, 7, 15, 30), backByAtFor(BackByChoice.AtTime(15, 30), now, zone))
    }

    /** The owner, Amendment 1: "A picked time earlier than now means tomorrow." */
    @Test
    fun `a picked time earlier than now is tomorrow's`() {
        assertEquals(at(2026, 10, 8, 9, 0), backByAtFor(BackByChoice.AtTime(9, 0), now, zone))
    }

    @Test
    fun `a picked time in the current minute, already begun, is tomorrow's`() {
        assertEquals(at(2026, 10, 8, 14, 10), backByAtFor(BackByChoice.AtTime(14, 10), now, zone))
    }

    /** The clock time holds across a daylight-saving change: 1 Nov 2026 is the US fall-back. */
    @Test
    fun `a picked time across a daylight-saving change is that clock time tomorrow`() {
        val lateOnSaturday = at(2026, 10, 31, 23, 0)
        val picked = backByAtFor(BackByChoice.AtTime(9, 0), lateOnSaturday, zone)
        assertEquals(at(2026, 11, 1, 9, 0), picked)
        assertEquals("ten clock hours, eleven elapsed: the hour is repeated", 11 * hour, picked - lateOnSaturday)
    }

    @Test
    fun `the line shows from one hour before the time, and stays once it has passed`() {
        val backBy = now + 2 * hour
        assertFalse(backByLineShown(backBy, backBy - 61 * minute))
        assertTrue("at exactly one hour", backByLineShown(backBy, backBy - hour))
        assertTrue(backByLineShown(backBy, backBy - 59 * minute))
        assertTrue("passed, still set", backByLineShown(backBy, backBy + 10 * minute))
        assertTrue(BackByShown("t1", backBy, backBy - 30 * minute).lineShown)
        assertFalse(BackByShown("t1", backBy, backBy - 90 * minute).lineShown)
    }
}
