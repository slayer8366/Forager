package com.zynergylabs.forager.app.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference values are Open-Meteo's published sunsets, fetched 2026-09-11 while building this
 * feature, for the dates and coordinates named in each row. Open-Meteo is the oracle because the
 * app already calls it, so this compares against a source the project already relies on rather
 * than against a number someone remembered.
 *
 * An earlier pass of this check used sunset times recalled from memory and produced apparent
 * errors of 5 and 11 minutes at two sites. Both were the harness, not the arithmetic: one was a
 * search window that began before local midnight and therefore found the *previous* day's sunset,
 * and the recalled values themselves had no provenance. The rule that came out of it is the one
 * this file follows: a reference value is fetched and cited, or it is not a reference value.
 */
class SunCrossingTest {

    private data class Ref(
        val place: String,
        val latitude: Double,
        val longitude: Double,
        val sunsetEpochMillis: Long,
        val date: String,
    )

    private val references = listOf(
        Ref("London", 51.5074, -0.1278, 1789237318000L, "2026-09-12"),
        Ref("London", 51.5074, -0.1278, 1789927421000L, "2026-09-20"),
        Ref("New York", 40.7128, -74.006, 1789254588000L, "2026-09-12"),
        Ref("New York", 40.7128, -74.006, 1789944986000L, "2026-09-20"),
        Ref("Seattle", 47.6062, -122.3321, 1789266422000L, "2026-09-12"),
        Ref("Seattle", 47.6062, -122.3321, 1789956647000L, "2026-09-20"),
        Ref("Quito", -0.1807, -78.4678, 1789254798000L, "2026-09-12"),
        Ref("Quito", -0.1807, -78.4678, 1789945834000L, "2026-09-20"),
        Ref("Sydney", -33.8688, 151.2093, 1789199069000L, "2026-09-12"),
        Ref("Sydney", -33.8688, 151.2093, 1789890596000L, "2026-09-20"),
        Ref("Reykjavik", 64.1466, -21.9426, 1789243455000L, "2026-09-12"),
        Ref("Reykjavik", 64.1466, -21.9426, 1789932958000L, "2026-09-20"),
        Ref("Singapore", 1.3521, 103.8198, 1789211086000L, "2026-09-12"),
        Ref("Singapore", 1.3521, 103.8198, 1789902101000L, "2026-09-20"),
        Ref("Ushuaia", -54.8019, -68.303, 1789251170000L, "2026-09-12"),
        Ref("Ushuaia", -54.8019, -68.303, 1789943250000L, "2026-09-20"),
    )

    private val toleranceMillis = 60_000L

    @Test
    fun `sunset matches published times at every latitude checked`() {
        val failures = mutableListOf<String>()
        for (ref in references) {
            // Search the UTC day the reference falls in, so a day-alignment error cannot pass as
            // agreement: the window is derived from the reference, not from a guess at local noon.
            val dayStart = ref.sunsetEpochMillis / DAY_MILLIS * DAY_MILLIS
            val found = SunCrossing.nextDescendingCrossing(
                fromEpochMillis = dayStart,
                withinMillis = DAY_MILLIS,
                latitude = ref.latitude,
                longitude = ref.longitude,
                altitudeDegrees = SunCrossing.SUNSET_ALTITUDE_DEGREES,
            )
            if (found == null) {
                failures += "${ref.place} ${ref.date}: no crossing found"
                continue
            }
            val offBy = abs(found - ref.sunsetEpochMillis)
            if (offBy > toleranceMillis) {
                failures += "${ref.place} ${ref.date}: off by ${offBy / 1000}s"
            }
        }
        assertTrue("sunset disagreed with the published time: $failures", failures.isEmpty())
    }

    @Test
    fun `the returned instant is actually on the threshold`() {
        // Agreement with an oracle and internal consistency are different claims. This one catches
        // a bisection that converges on the wrong side, which a minute of tolerance would hide.
        for (ref in references) {
            val dayStart = ref.sunsetEpochMillis / DAY_MILLIS * DAY_MILLIS
            val found = SunCrossing.nextDescendingCrossing(
                dayStart, DAY_MILLIS, ref.latitude, ref.longitude, SunCrossing.SUNSET_ALTITUDE_DEGREES,
            )
            assertNotNull("${ref.place}: expected a crossing", found)
            val altitude = CivilTwilight.sunAltitudeDegrees(found!!, ref.latitude, ref.longitude)
            assertEquals(
                "${ref.place} ${ref.date}: altitude at the returned instant",
                SunCrossing.SUNSET_ALTITUDE_DEGREES, altitude, 0.01,
            )
        }
    }

    @Test
    fun `civil dusk follows sunset and reuses the night threshold`() {
        for (ref in references) {
            val dayStart = ref.sunsetEpochMillis / DAY_MILLIS * DAY_MILLIS
            val sunset = SunCrossing.nextDescendingCrossing(
                dayStart, DAY_MILLIS, ref.latitude, ref.longitude, SunCrossing.SUNSET_ALTITUDE_DEGREES,
            )!!
            val dusk = SunCrossing.nextDescendingCrossing(
                dayStart, DAY_MILLIS, ref.latitude, ref.longitude, CivilTwilight.NIGHT_ALTITUDE_DEGREES,
            )
            assertNotNull("${ref.place}: expected a civil dusk", dusk)
            assertTrue("${ref.place}: civil dusk must be after sunset", dusk!! > sunset)
        }
    }

    @Test
    fun `polar day has no sunset and polar night has no crossing`() {
        val tromsoLat = 69.6492
        val tromsoLon = 18.9553
        // Midsummer: the sun never reaches the threshold going down.
        val midsummer = utcMidnight(2026, 6, 21)
        assertNull(
            "Tromso in midsummer must report no sunset rather than inventing one",
            SunCrossing.nextDescendingCrossing(
                midsummer, DAY_MILLIS, tromsoLat, tromsoLon, SunCrossing.SUNSET_ALTITUDE_DEGREES,
            ),
        )
        // Midwinter: the sun is already below it and never comes back up to cross downward.
        val midwinter = utcMidnight(2026, 12, 21)
        assertNull(
            "Tromso in polar night must report no crossing rather than inventing one",
            SunCrossing.nextDescendingCrossing(
                midwinter, DAY_MILLIS, tromsoLat, tromsoLon, SunCrossing.SUNSET_ALTITUDE_DEGREES,
            ),
        )
    }

    @Test
    fun `a window that ends before the crossing reports none`() {
        val ref = references.first { it.place == "London" }
        val dayStart = ref.sunsetEpochMillis / DAY_MILLIS * DAY_MILLIS
        // One hour of window starting at UTC midnight cannot contain a London September sunset.
        assertNull(
            SunCrossing.nextDescendingCrossing(
                dayStart, 60 * 60 * 1000L, ref.latitude, ref.longitude, SunCrossing.SUNSET_ALTITUDE_DEGREES,
            ),
        )
    }

    @Test
    fun `a crossing in the window's final partial step is still found`() {
        // The scan strides in ten-minute steps. A window whose length is not a whole number of
        // steps must still see a crossing in its tail, which is what the closing check exists for.
        val ref = references.first { it.place == "London" && it.date == "2026-09-12" }
        val startsJustBefore = ref.sunsetEpochMillis - (23 * 60 * 1000L)
        val found = SunCrossing.nextDescendingCrossing(
            fromEpochMillis = startsJustBefore,
            withinMillis = 25 * 60 * 1000L,
            latitude = ref.latitude,
            longitude = ref.longitude,
            altitudeDegrees = SunCrossing.SUNSET_ALTITUDE_DEGREES,
        )
        assertNotNull("a crossing 23 minutes into a 25-minute window must be found", found)
        assertTrue(abs(found!! - ref.sunsetEpochMillis) <= toleranceMillis)
    }

    @Test
    fun `sunset at zero degrees would be measurably earlier, which is why it is not zero`() {
        // Guards the -0.833 constant against being "simplified" to 0.0 by someone who reads it as
        // an arbitrary fudge. At London's latitude the gap is several minutes.
        val ref = references.first { it.place == "London" && it.date == "2026-09-12" }
        val dayStart = ref.sunsetEpochMillis / DAY_MILLIS * DAY_MILLIS
        val atZero = SunCrossing.nextDescendingCrossing(
            dayStart, DAY_MILLIS, ref.latitude, ref.longitude, 0.0,
        )!!
        val atSunset = SunCrossing.nextDescendingCrossing(
            dayStart, DAY_MILLIS, ref.latitude, ref.longitude, SunCrossing.SUNSET_ALTITUDE_DEGREES,
        )!!
        assertTrue("0 degrees must come first", atZero < atSunset)
        val gapMinutes = (atSunset - atZero) / 60_000.0
        assertTrue("expected a gap of minutes, got $gapMinutes", gapMinutes > 2.0)
    }

    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        java.time.LocalDate.of(year, month, day)
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
