package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The owner's display naming for auto-created waypoints — the default name only; nothing reads it back. Since data part D
 * (RECORD -703, "New ones follow the new style") the date carries its year, "Sep 5, 2026", and the time follows the phone's
 * 12/24-hour setting; these expected names read "Sep 5, 9:41 AM" before.
 */
class AutoWaypointNameTest {

    /** 2026-09-05T09:41:00Z. */
    private val start = 1_788_601_260_000L

    @Test
    fun `origin reads Start with the local date and time`() {
        assertEquals("Start · Sep 5, 2026, 9:41 AM", autoWaypointName(WaypointDesignation.ORIGIN, start, ZoneOffset.UTC, is24HourClock = false))
    }

    @Test
    fun `end reads End, eighty-one minutes later`() {
        assertEquals("End · Sep 5, 2026, 11:02 AM", autoWaypointName(WaypointDesignation.END, start + 81L * 60L * 1_000L, ZoneOffset.UTC, is24HourClock = false))
    }

    @Test
    fun `the time is written in the given zone`() {
        // Pacific Daylight Time in September is UTC−7: 09:41Z is 2:41 AM.
        assertEquals("Start · Sep 5, 2026, 2:41 AM", autoWaypointName(WaypointDesignation.ORIGIN, start, ZoneId.of("America/Los_Angeles"), is24HourClock = false))
    }

    @Test
    fun `on a 24-hour phone the time is in 24-hour form`() {
        assertEquals("Start · Sep 5, 2026, 09:41", autoWaypointName(WaypointDesignation.ORIGIN, start, ZoneOffset.UTC, is24HourClock = true))
        assertEquals("End · Sep 5, 2026, 11:02", autoWaypointName(WaypointDesignation.END, start + 81L * 60L * 1_000L, ZoneOffset.UTC, is24HourClock = true))
    }
}
