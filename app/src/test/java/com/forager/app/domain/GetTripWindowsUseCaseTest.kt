package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SoilAvailability
import com.forager.app.domain.model.WeatherSeries
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GetTripWindowsUseCaseTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
    private val referenceDay: LocalDate = LocalDate.of(2026, 8, 16)

    private class FakeProvider(
        private val result: Result<WeatherSeries>,
    ) : TripPlanningWeatherProvider {
        var regionAsked: Region? = null
        override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> {
            regionAsked = region
            return result
        }
    }

    private fun series(rain: Map<LocalDate, Double>): WeatherSeries {
        val days = (0L..20L).map { offset ->
            val date = referenceDay.minusDays(14).plusDays(offset)
            DailyWeather(
                date = date,
                isForecast = !date.isBefore(referenceDay),
                precipitationMm = rain[date] ?: 0.0,
                evapotranspirationMm = 3.0,
                shallowSoilMoistureM3M3 = null,
                deeperSoilMoistureM3M3 = null,
                soilTemperatureMeanC = null,
                soilTemperatureMinC = null,
                soilTemperatureMaxC = null,
            )
        }
        return WeatherSeries(
            region = region,
            referenceDay = referenceDay,
            days = days,
            soilAvailability = SoilAvailability(null, null, null),
        )
    }

    @Test
    fun `it fetches the series for the region and reports the windows found in it`() = runTest {
        val provider = FakeProvider(Result.success(series(mapOf(LocalDate.of(2026, 8, 12) to 12.0))))

        val report = GetTripWindowsUseCase(provider, ComputeTripWindowsUseCase())(region).getOrThrow()

        assertEquals(region, provider.regionAsked)
        assertEquals(LocalDate.of(2026, 8, 19), report.windows.single().startDate)
    }

    @Test
    fun `a provider failure is propagated, not turned into an empty report`() = runTest {
        // An empty windows list means "searched, found nothing"; a failed fetch means "did not
        // search". Collapsing the second into the first would report a failure as a result.
        val boom = IllegalStateException("no utc_offset_seconds")
        val provider = FakeProvider(Result.failure(boom))

        val result = GetTripWindowsUseCase(provider, ComputeTripWindowsUseCase())(region)

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }
}
