package com.zynergylabs.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** [formatTimeSpan]: the entry report's and editor's times (dispatch 2026-09-28-667), "1 h 12 min", never "1h 12m". */
class TimeSpanFormatTest {

    @Test
    fun `hours and minutes are written out, rounded down to the minute`() {
        assertEquals("Under 1 min", formatTimeSpan(0L))
        assertEquals("Under 1 min", formatTimeSpan(59_999L))
        assertEquals("1 min", formatTimeSpan(60_000L))
        assertEquals("48 min", formatTimeSpan(48 * 60_000L + 59_000L))
        assertEquals("2 h", formatTimeSpan(120 * 60_000L))
        assertEquals("1 h 12 min", formatTimeSpan(72 * 60_000L))
        assertEquals("a negative span is never written as one", "Under 1 min", formatTimeSpan(-5_000L))
    }
}
