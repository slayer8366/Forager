package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SoilAvailability
import com.forager.app.domain.model.WeatherSeries
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTripPlanningWeatherProvider(
    private val result: Result<WeatherSeries>,
) : TripPlanningWeatherProvider {
    var lastRegion: Region? = null

    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> {
        lastRegion = region
        return result
    }
}

private val NO_SOIL_DATA = SoilAvailability(
    shallowMoistureBand = null,
    deeperMoistureBand = null,
    temperatureBand = null,
)

class GetTodaysForecastUseCaseTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
    private val referenceDay = LocalDate.of(2026, 8, 17)

    private fun day(date: LocalDate, isForecast: Boolean, precipitationMm: Double) = DailyWeather(
        date = date,
        isForecast = isForecast,
        precipitationMm = precipitationMm,
        evapotranspirationMm = null,
        shallowSoilMoistureM3M3 = null,
        deeperSoilMoistureM3M3 = null,
        soilTemperatureMeanC = null,
        soilTemperatureMinC = null,
        soilTemperatureMaxC = null,
    )

    @Test
    fun `returns the reference day's own forecast entry, not an observed day or a later forecast day`() = runTest {
        val series = WeatherSeries(
            region = region,
            referenceDay = referenceDay,
            days = listOf(
                day(referenceDay.minusDays(1), isForecast = false, precipitationMm = 1.0),
                day(referenceDay, isForecast = true, precipitationMm = 3.2),
                day(referenceDay.plusDays(1), isForecast = true, precipitationMm = 9.9),
            ),
            soilAvailability = NO_SOIL_DATA,
        )
        val provider = FakeTripPlanningWeatherProvider(Result.success(series))
        val useCase = GetTodaysForecastUseCase(provider)

        val result = useCase(region)

        assertEquals(referenceDay, result.getOrThrow()?.date)
        assertEquals(3.2, result.getOrThrow()?.precipitationMm)
    }

    @Test
    fun `returns null when the series has no forecast days`() = runTest {
        val series = WeatherSeries(
            region = region,
            referenceDay = referenceDay,
            days = listOf(day(referenceDay.minusDays(1), isForecast = false, precipitationMm = 1.0)),
            soilAvailability = NO_SOIL_DATA,
        )
        val provider = FakeTripPlanningWeatherProvider(Result.success(series))
        val useCase = GetTodaysForecastUseCase(provider)

        val result = useCase(region)

        assertNull(result.getOrThrow())
    }

    @Test
    fun `passes the requested region through unchanged`() = runTest {
        val series = WeatherSeries(region = region, referenceDay = referenceDay, days = emptyList(), soilAvailability = NO_SOIL_DATA)
        val provider = FakeTripPlanningWeatherProvider(Result.success(series))
        val useCase = GetTodaysForecastUseCase(provider)

        useCase(region)

        assertEquals(region, provider.lastRegion)
    }

    @Test
    fun `provider failure propagates as failure`() = runTest {
        val failure = IllegalStateException("network down")
        val provider = FakeTripPlanningWeatherProvider(Result.failure(failure))
        val useCase = GetTodaysForecastUseCase(provider)

        val result = useCase(region)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
