package com.forager.app.data.repository

import com.forager.app.data.remote.dto.DailyPrecipitationDto
import com.forager.app.data.remote.dto.PrecipitationResponseDto
import com.forager.app.domain.model.Region
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

    @Test
    fun `region is passed through unchanged`() {
        val summary = toDomain(dto(listOf(0.0)), region)

        assertEquals(region, summary.region)
    }
}
