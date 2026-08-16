package com.forager.app.domain

import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.Region
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeWeatherProvider(
    private val result: Result<ConditionsSummary>,
) : WeatherProvider {
    var lastRegion: Region? = null

    override suspend fun getRecentPrecipitation(region: Region): Result<ConditionsSummary> {
        lastRegion = region
        return result
    }
}

class GetConditionsUseCaseTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    @Test
    fun `returns the provider's result unchanged`() = runTest {
        val summary = ConditionsSummary(region = region, totalPrecipitationMm = 12.5, daysSinceSignificantRain = 3)
        val provider = FakeWeatherProvider(Result.success(summary))
        val useCase = GetConditionsUseCase(provider)

        val result = useCase(region)

        assertEquals(summary, result.getOrThrow())
    }

    @Test
    fun `passes the requested region through unchanged`() = runTest {
        val summary = ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null)
        val provider = FakeWeatherProvider(Result.success(summary))
        val useCase = GetConditionsUseCase(provider)

        useCase(region)

        assertEquals(region, provider.lastRegion)
    }

    @Test
    fun `provider failure propagates as failure`() = runTest {
        val failure = IllegalStateException("network down")
        val provider = FakeWeatherProvider(Result.failure(failure))
        val useCase = GetConditionsUseCase(provider)

        val result = useCase(region)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
