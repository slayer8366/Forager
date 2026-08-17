package com.forager.app.data.repository

import com.forager.app.data.remote.dto.DailyPrecipitationDto
import com.forager.app.data.remote.dto.PrecipitationResponseDto
import com.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The hazard this file exists for.
 *
 * `daily=precipitation_sum&past_days=14&forecast_days=0` returns 14 entries that are all in the
 * past, so summing the whole array and scanning back from its last entry both happen to be
 * correct. Adding `forecast_days=7` silently changes that: the array becomes 21 entries, 7 of
 * them not yet fallen, and both calculations start reading the future as though it were observed
 * history. Nothing errors and nothing else in the suite notices.
 *
 * Verified against the live API on 2026-08-17 (see PR notes): `past_days=14&forecast_days=0`
 * returns `[today-14 .. today-1]` and `past_days=14&forecast_days=7` returns `[today-14 ..
 * today+6]`, where "today" is the *location's* local day, not the device's. So the past portion
 * is exactly the entries whose date is strictly before the reference day, and the shipped
 * `ConditionsSummary` numbers are defined relative to the newest of those (yesterday), not to
 * today.
 */
class OpenMeteoPastFutureSplitTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    /** Local day the fixtures below are built around: 14 days before it, then it, then 6 after. */
    private val referenceDay: LocalDate = LocalDate.of(2026, 8, 16)

    /**
     * 21 days of precipitation laid out the way Open-Meteo returns them for
     * `past_days=14&forecast_days=7`: index 0 is referenceDay-14, index 13 is referenceDay-1,
     * index 14 is referenceDay itself, index 20 is referenceDay+6.
     */
    private fun twentyOneDayResponse(precipitation: List<Double>): PrecipitationResponseDto {
        require(precipitation.size == 21) { "fixture must be 14 past + 7 forecast days" }
        val start = referenceDay.minusDays(14)
        return PrecipitationResponseDto(
            utcOffsetSeconds = -25200,
            daily = DailyPrecipitationDto(
                time = precipitation.indices.map { start.plusDays(it.toLong()).toString() },
                precipitationSum = precipitation,
            ),
        )
    }

    @Test
    fun `the 14-day total excludes forecast days`() {
        // 14 past days summing to 20.0mm, then 7 forecast days carrying 70.0mm of rain that has
        // not fallen. A naive sum reports 90.0.
        val past = List(14) { if (it == 3) 20.0 else 0.0 }
        val forecast = List(7) { 10.0 }

        val summary = summariseObservedConditions(
            twentyOneDayResponse(past + forecast),
            region,
            referenceDay,
        )

        assertEquals(20.0, summary.totalPrecipitationMm, 0.0001)
    }

    @Test
    fun `days since significant rain ignores rain that has not fallen yet`() {
        // Nothing significant in the observed past; a downpour forecast for the reference day.
        // A naive backward scan from the last array entry finds the forecast rain and reports it
        // as recent history.
        val past = List(14) { 0.0 }
        val forecast = listOf(30.0) + List(6) { 0.0 }

        val summary = summariseObservedConditions(
            twentyOneDayResponse(past + forecast),
            region,
            referenceDay,
        )

        assertNull(summary.daysSinceSignificantRain)
    }

    @Test
    fun `days since significant rain is counted from the most recent observed day`() {
        // 8.0mm on referenceDay-1, which is index 13 — the newest *past* day. The shipped
        // meaning of "0 days since" is "the newest day in the observed window", so this is 0.
        //
        // The forecast tail is deliberately dry. With rain in it this assertion passed against
        // the naive implementation too — it scanned back from index 20, hit the forecast rain and
        // returned 20 - 20 = 0, the right answer for the wrong reason (CLAUDE.md: a check that
        // passes identically before and after the change is suspect). Dry forecast days make the
        // naive answer 20 - 13 = 7, so the assertion now discriminates.
        val past = List(14) { if (it == 13) 8.0 else 0.0 }
        val forecast = List(7) { 0.0 }

        val summary = summariseObservedConditions(
            twentyOneDayResponse(past + forecast),
            region,
            referenceDay,
        )

        assertEquals(0, summary.daysSinceSignificantRain)
    }

    @Test
    fun `days since significant rain counts back within the observed window only`() {
        // 8.0mm on referenceDay-4 (index 10), three days before the newest observed day.
        val past = List(14) { if (it == 10) 8.0 else 0.0 }
        val forecast = List(7) { 25.0 }

        val summary = summariseObservedConditions(
            twentyOneDayResponse(past + forecast),
            region,
            referenceDay,
        )

        assertEquals(3, summary.daysSinceSignificantRain)
    }

    @Test
    fun `the reference day itself is treated as forecast, not observed`() {
        // Open-Meteo puts the current local day in the forecast_days portion: with
        // past_days=14&forecast_days=0 the newest entry returned is yesterday. Today's daily
        // aggregate is still partly modelled, so it is not observed history.
        val past = List(14) { 0.0 }
        val forecast = listOf(50.0) + List(6) { 0.0 }

        val summary = summariseObservedConditions(
            twentyOneDayResponse(past + forecast),
            region,
            referenceDay,
        )

        assertEquals(0.0, summary.totalPrecipitationMm, 0.0001)
    }
}
