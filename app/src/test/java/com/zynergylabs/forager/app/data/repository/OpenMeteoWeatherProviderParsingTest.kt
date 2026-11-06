package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.remote.dto.DailyPrecipitationDto
import com.zynergylabs.forager.app.data.remote.dto.PrecipitationResponseDto
import com.zynergylabs.forager.app.domain.model.DailyRain
import com.zynergylabs.forager.app.domain.model.Region
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun dto(precipitationSum: List<Double>) = PrecipitationResponseDto(
    daily = DailyPrecipitationDto(
        time = precipitationSum.indices.map { "2026-08-%02d".format(it + 1) },
        precipitationSum = precipitationSum,
    ),
)

class OpenMeteoWeatherProviderParsingTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    @Test
    fun `sums precipitation across the window`() {
        val summary = toDomain(dto(listOf(0.0, 3.2, 1.5, 0.0)), region)

        assertEquals(4.7, summary.totalPrecipitationMm, 0.0001)
    }

    @Test
    fun `days since significant rain is 0 when the most recent day cleared the threshold`() {
        val summary = toDomain(dto(listOf(0.0, 0.0, 5.0)), region)

        assertEquals(0, summary.daysSinceSignificantRain)
    }

    @Test
    fun `days since significant rain counts back to an older day that cleared the threshold`() {
        val summary = toDomain(dto(listOf(0.0, 4.0, 0.5, 0.0)), region)

        assertEquals(2, summary.daysSinceSignificantRain)
    }

    @Test
    fun `days since significant rain is null when no day in the window cleared the threshold`() {
        val summary = toDomain(dto(listOf(0.0, 1.0, 1.9, 0.0)), region)

        assertNull(summary.daysSinceSignificantRain)
    }

    @Test
    fun `a day exactly at the threshold counts as significant`() {
        val summary = toDomain(dto(listOf(0.0, 2.0)), region)

        assertEquals(0, summary.daysSinceSignificantRain)
    }

    /**
     * Data part C (dispatch -668): the daily values the total is summed from are kept, each with its
     * own date, oldest first, instead of being discarded after the sum.
     */
    @Test
    fun `the daily rain values are kept with their dates, oldest first`() {
        val summary = toDomain(dto(listOf(0.0, 3.2, 1.5)), region)

        assertEquals(
            listOf(
                DailyRain(LocalDate.of(2026, 8, 1), 0.0),
                DailyRain(LocalDate.of(2026, 8, 2), 3.2),
                DailyRain(LocalDate.of(2026, 8, 3), 1.5),
            ),
            summary.dailyRain,
        )
    }

    /**
     * Through the entry point production uses, `summariseObservedConditions`: today and the forecast
     * days are not observed, so they are not in the daily rain either.
     */
    @Test
    fun `the daily rain holds only the observed days, never today or the forecast`() {
        val summary = summariseObservedConditions(dto(listOf(1.0, 2.0, 30.0, 40.0)), region, referenceDay = LocalDate.of(2026, 8, 3))

        assertEquals(listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)), summary.dailyRain.map { it.date })
        assertEquals(3.0, summary.totalPrecipitationMm, 0.0001)
    }

    @Test
    fun `region is passed through unchanged`() {
        val summary = toDomain(dto(listOf(0.0)), region)

        assertEquals(region, summary.region)
    }
}
