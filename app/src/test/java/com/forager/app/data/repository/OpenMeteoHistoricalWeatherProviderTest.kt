package com.forager.app.data.repository

import com.forager.app.data.remote.OpenMeteoArchiveApi
import com.forager.app.data.remote.dto.HistoricalDailyPrecipitationDto
import com.forager.app.data.remote.dto.HistoricalPrecipitationResponseDto
import com.forager.app.domain.model.Region
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The provider's own behaviour: the request it sends and how it maps the archive DTO onto
 * [com.forager.app.domain.model.DailyWeather]. Mirrors
 * [OpenMeteoWeatherProviderRequestTest]'s shape for the historical-archive endpoint.
 */
class OpenMeteoHistoricalWeatherProviderTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    private class FakeApi(private val response: HistoricalPrecipitationResponseDto) : OpenMeteoArchiveApi {
        var latitude: Double? = null
        var longitude: Double? = null
        var startDate: String? = null
        var endDate: String? = null
        var daily: String? = null
        var timezone: String? = null
        var calls = 0

        override suspend fun getHistoricalPrecipitation(
            latitude: Double,
            longitude: Double,
            startDate: String,
            endDate: String,
            daily: String,
            timezone: String,
        ): HistoricalPrecipitationResponseDto {
            calls++
            this.latitude = latitude
            this.longitude = longitude
            this.startDate = startDate
            this.endDate = endDate
            this.daily = daily
            this.timezone = timezone
            return response
        }
    }

    private fun response(dates: List<LocalDate>, values: List<Double?>) = HistoricalPrecipitationResponseDto(
        utcOffsetSeconds = -25200,
        daily = HistoricalDailyPrecipitationDto(
            time = dates.map { it.toString() },
            precipitationSum = values,
        ),
    )

    @Test
    fun `the request asks for the requested region and date range, and daily precipitation only`() = runTest {
        val api = FakeApi(response(emptyList(), emptyList()))
        val from = LocalDate.of(2024, 6, 1)
        val through = LocalDate.of(2024, 8, 31)

        OpenMeteoHistoricalWeatherProvider(api).getHistoricalPrecipitation(region, from, through)

        assertEquals(region.lat, api.latitude)
        assertEquals(region.lng, api.longitude)
        assertEquals("2024-06-01", api.startDate)
        assertEquals("2024-08-31", api.endDate)
        assertEquals(OpenMeteoArchiveApi.DAILY_VARIABLES, api.daily)
        assertEquals("precipitation_sum", api.daily)
        assertEquals("auto", api.timezone)
        assertEquals(1, api.calls)
    }

    @Test
    fun `parses the daily block into DailyWeather, all historical and with no soil data`() = runTest {
        val dates = listOf(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 2), LocalDate.of(2024, 6, 3))
        val api = FakeApi(response(dates, listOf(0.0, 12.5, 3.2)))

        val days = OpenMeteoHistoricalWeatherProvider(api)
            .getHistoricalPrecipitation(region, dates.first(), dates.last())
            .getOrThrow()

        assertEquals(3, days.size)
        assertEquals(listOf(0.0, 12.5, 3.2), days.map { it.precipitationMm })
        assertTrue(days.all { !it.isForecast })
        assertTrue(days.all { it.evapotranspirationMm == null })
        assertTrue(days.all { it.shallowSoilMoistureM3M3 == null && it.soilTemperatureMeanC == null })
    }

    @Test
    fun `a day with no precipitation value is dropped rather than defaulted to zero`() = runTest {
        val dates = listOf(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 2), LocalDate.of(2024, 6, 3))
        val api = FakeApi(response(dates, listOf(1.0, null, 4.0)))

        val days = OpenMeteoHistoricalWeatherProvider(api)
            .getHistoricalPrecipitation(region, dates.first(), dates.last())
            .getOrThrow()

        assertEquals(listOf(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 3)), days.map { it.date })
        assertFalse(days.any { it.precipitationMm == 0.0 && it.date == LocalDate.of(2024, 6, 2) })
    }

    @Test
    fun `a from date after the through date fails rather than silently swapping them`() = runTest {
        val api = FakeApi(response(emptyList(), emptyList()))

        val result = OpenMeteoHistoricalWeatherProvider(api)
            .getHistoricalPrecipitation(region, LocalDate.of(2024, 8, 31), LocalDate.of(2024, 6, 1))

        assertTrue(result.isFailure)
        assertEquals(0, api.calls)
    }

    @Test
    fun `an api failure is propagated as a Result failure`() = runTest {
        val throwingApi = object : OpenMeteoArchiveApi {
            override suspend fun getHistoricalPrecipitation(
                latitude: Double,
                longitude: Double,
                startDate: String,
                endDate: String,
                daily: String,
                timezone: String,
            ): HistoricalPrecipitationResponseDto = error("boom")
        }

        val result = OpenMeteoHistoricalWeatherProvider(throwingApi)
            .getHistoricalPrecipitation(region, LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 2))

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
    }
}
