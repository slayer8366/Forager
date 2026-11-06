package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.provider.Settings
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.SundownLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sundown line's words, each one of the owner's step path (RECORD -592; Amendment 1, RECORD -593;
 * Amendment 2, RECORD -595 and -596), on a fixed 12-hour clock in UTC so a literal can be asserted;
 * and the phone's own clock ([androidSundownClock]) in 12- and 24-hour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SundownLineTextTest {

    private val minute = 60_000L

    /** 2026-10-03 18:42 UTC. */
    private val sunset = 1_791_052_920_000L

    private val clock = object : SundownClock {
        private val full = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        private val short = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        override fun full(epochMillis: Long): String = full.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = short.format(Date(epochMillis))
    }

    private fun text(line: SundownLine) = sundownLineText(line, clock)

    @Test
    fun `start back by, while the start-back time is ahead`() {
        val startBack = sunset - 70 * minute // 5:32
        assertEquals("Sunset 6:42 PM · start back by 5:32", text(SundownLine.BeforeSunset(startBack - 20 * minute, sunset, sunset + 31 * minute, startBack)))
    }

    @Test
    fun `start back was, once it has passed, and at the moment itself`() {
        val startBack = sunset - 70 * minute
        assertEquals("Sunset 6:42 PM · start back was 5:32", text(SundownLine.BeforeSunset(startBack + 5 * minute, sunset, sunset + 31 * minute, startBack)))
        assertEquals("Sunset 6:42 PM · start back was 5:32", text(SundownLine.BeforeSunset(startBack, sunset, sunset + 31 * minute, startBack)))
    }

    @Test
    fun `with no walk back known, the countdown and dark`() {
        assertEquals("Sunset 6:42 PM · in 1 h 12 min · dark 7:13", text(SundownLine.BeforeSunset(sunset - 72 * minute, sunset, sunset + 31 * minute, null)))
    }

    @Test
    fun `after sunset, dark in`() {
        assertEquals("Sun set 6:42 PM · dark in 21 min", text(SundownLine.AfterSunset(sunset + 10 * minute, sunset, sunset + 31 * minute)))
    }

    @Test
    fun `after dark, dark since`() {
        assertEquals("Dark since 7:13 PM", text(SundownLine.DarkSince(sunset + 31 * minute)))
    }

    @Test
    fun `where it does not get dark tonight, the sunset alone, and no dark time`() {
        assertEquals("Sun set 6:42 PM", text(SundownLine.AfterSunset(sunset + 10 * minute, sunset, null)))
        assertEquals("Sunset 6:42 PM · in 1 h", text(SundownLine.BeforeSunset(sunset - 60 * minute, sunset, null, null)))
    }

    @Test
    fun `polar night is worded`() {
        assertEquals("Sun stays down today", text(SundownLine.SunStaysDown))
    }

    /** RECORD -596: "Yes hide it before a position is known". */
    @Test
    fun `no position has no line, and the finding-position words are gone`() {
        assertNull(text(SundownLine.FindingPosition))
        assertNull(text(SundownLine.SunDoesNotSet))
    }

    @Test
    fun `hidden outside both windows, written once either opens`() {
        assertNull("2 h 31 min to sunset, no start-back", text(SundownLine.BeforeSunset(sunset - 151 * minute, sunset, null, null)))
        assertEquals("Sunset 6:42 PM · in 2 h 29 min", text(SundownLine.BeforeSunset(sunset - 149 * minute, sunset, null, null)))
        val startBack = sunset - 4 * 60 * minute // 2:42
        assertNull("start-back 61 min away", text(SundownLine.BeforeSunset(startBack - 61 * minute, sunset, null, startBack)))
        assertEquals("Sunset 6:42 PM · start back by 2:42", text(SundownLine.BeforeSunset(startBack - 59 * minute, sunset, null, startBack)))
    }

    @Test
    fun `durations round down to the minute`() {
        assertEquals("21 min", formatSundownDuration(21 * minute + 59_999L))
        assertEquals("1 h 12 min", formatSundownDuration(72 * minute))
        assertEquals("2 h", formatSundownDuration(120 * minute + 30_000L))
        assertEquals("less than 1 min", formatSundownDuration(59_000L))
        assertEquals("less than 1 min", formatSundownDuration(-5_000L))
    }

    @Test
    fun `the phone's clock, 12-hour, gives the full time as the alerts do and the later time without the marker`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        val phone = androidSundownClock(context)
        assertEquals("the alerts' own format", DateFormat.getTimeFormat(context).format(Date(sunset)), phone.full(sunset))
        assertEquals(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(sunset)), phone.short(sunset))
    }

    @Test
    fun `the phone's clock, 24-hour, gives both times in 24-hour`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        val phone = androidSundownClock(context)
        // 6:42 PM in the JVM's own zone, which the formats write in: an afternoon hour, so a 12-hour
        // "h:mm" would write "6:42" where 24-hour writes "18:42". The shared [sunset] fixture is a
        // UTC evening, which is a morning hour in some zones (11:42 in Los Angeles), where the two
        // formats agree and this test could not fail.
        val evening = java.time.ZonedDateTime.of(2026, 10, 3, 18, 42, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val expected = DateFormat.getTimeFormat(context).format(Date(evening))
        assertEquals("the sample is an afternoon hour", true, expected.startsWith("18"))
        assertEquals(expected, phone.full(evening))
        assertEquals(expected, phone.short(evening))
        assertEquals("no AM/PM in 24-hour: $expected", false, expected.contains("M"))
    }
}
