package com.forager.app.data.repository

import com.forager.app.data.remote.OpenMeteoApi
import com.forager.app.data.remote.dto.DailyPrecipitationDto
import com.forager.app.data.remote.dto.PrecipitationResponseDto
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.model.Region
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The provider's own behaviour: what it asks the API for, and how it decides which day "today" is.
 *
 * Exercised through [OpenMeteoWeatherProvider]'s real entry points with a fake [OpenMeteoApi],
 * rather than by calling the mapping functions directly — those are covered separately, and the
 * request parameters and the clock handling only exist on this path.
 */
class OpenMeteoWeatherProviderRequestTest {

    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    /** Records the query it was called with and replays a canned response. */
    private class FakeApi(
        private val response: PrecipitationResponseDto,
    ) : OpenMeteoApi {
        var pastDays: Int? = null
        var forecastDays: Int? = null
        var daily: String? = null
        var hourly: String? = null
        var timezone: String? = null
        var calls = 0

        override suspend fun getPrecipitation(
            latitude: Double,
            longitude: Double,
            daily: String,
            hourly: String,
            pastDays: Int,
            forecastDays: Int,
            timezone: String,
        ): PrecipitationResponseDto {
            calls++
            this.pastDays = pastDays
            this.forecastDays = forecastDays
            this.daily = daily
            this.hourly = hourly
            this.timezone = timezone
            return response
        }
    }

    private fun response(
        utcOffsetSeconds: Int?,
        dates: List<LocalDate>,
        mmPerDay: Double = 1.0,
    ) = PrecipitationResponseDto(
        utcOffsetSeconds = utcOffsetSeconds,
        daily = DailyPrecipitationDto(
            time = dates.map { it.toString() },
            precipitationSum = List(dates.size) { mmPerDay },
        ),
    )

    private fun dates(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }

    @Test
    fun `the request asks for the observed history and the forecast horizon`() = runTest {
        val api = FakeApi(response(-25200, dates(LocalDate.of(2026, 8, 2), 21)))

        OpenMeteoWeatherProvider(api).getWeatherSeries(region)

        assertEquals(FruitingPatternAssumptions.OBSERVED_HISTORY_DAYS, api.pastDays)
        assertEquals(FruitingPatternAssumptions.FORECAST_HORIZON_DAYS, api.forecastDays)
        assertEquals(7, api.forecastDays)
    }

    @Test
    fun `the request asks for precipitation, evapotranspiration and both soil band families`() = runTest {
        val api = FakeApi(response(-25200, dates(LocalDate.of(2026, 8, 2), 21)))

        OpenMeteoWeatherProvider(api).getWeatherSeries(region)

        assertEquals("precipitation_sum,et0_fao_evapotranspiration", api.daily)
        val hourly = api.hourly!!.split(",")
        assertEquals(
            listOf(
                "soil_moisture_0_to_7cm", "soil_moisture_0_to_10cm",
                "soil_moisture_7_to_28cm", "soil_moisture_10_to_40cm",
                "soil_temperature_0_to_7cm", "soil_temperature_0_to_10cm",
            ),
            hourly,
        )
        // timezone=auto is what makes utc_offset_seconds the location's offset rather than UTC.
        assertEquals("auto", api.timezone)
    }

    @Test
    fun `the same instant resolves to different reference days at different longitudes`() = runTest {
        // 2026-08-16T04:00Z is 2026-08-15 21:00 in Portland and 2026-08-16 06:00 in Berlin. This
        // is the case that makes the device's own day the wrong thing to split on.
        val instant = Clock.fixed(Instant.parse("2026-08-16T04:00:00Z"), ZoneOffset.UTC)
        val window = dates(LocalDate.of(2026, 8, 2), 21)

        val portland = OpenMeteoWeatherProvider(FakeApi(response(-25200, window)), instant)
            .getWeatherSeries(region).getOrThrow()
        val berlin = OpenMeteoWeatherProvider(FakeApi(response(7200, window)), instant)
            .getWeatherSeries(region).getOrThrow()

        assertEquals(LocalDate.of(2026, 8, 15), portland.referenceDay)
        assertEquals(LocalDate.of(2026, 8, 16), berlin.referenceDay)
        // And the split moves with it: Berlin has one more observed day than Portland.
        assertEquals(13, portland.observedDays.size)
        assertEquals(14, berlin.observedDays.size)
    }

    @Test
    fun `a response with no timezone offset fails explicitly instead of using the device's day`() = runTest {
        val api = FakeApi(response(utcOffsetSeconds = null, dates = dates(LocalDate.of(2026, 8, 2), 21)))

        val result = OpenMeteoWeatherProvider(api).getWeatherSeries(region)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message
        assertNotNull(message)
        assertTrue(message!!.contains("utc_offset_seconds"))
        assertTrue(message.contains("observed days cannot be told apart from forecast days"))
    }

    @Test
    fun `the conditions summary path fails the same way rather than reporting numbers`() = runTest {
        val api = FakeApi(response(utcOffsetSeconds = null, dates = dates(LocalDate.of(2026, 8, 2), 21)))

        val result = OpenMeteoWeatherProvider(api).getRecentPrecipitation(region)

        assertTrue(
            "an unsplittable response must not produce a plausible-looking total",
            result.isFailure,
        )
    }

    @Test
    fun `the conditions summary counts only the observed days of the same response`() = runTest {
        // 21 days of 3.0mm each — above the significant-rain threshold, so the days-since figure
        // is exercised too. The observed portion is the 14 days before the reference day, so the
        // shipped "last 14 days" figure is 42.0mm, not the 63.0mm the whole array holds.
        val instant = Clock.fixed(Instant.parse("2026-08-16T12:00:00Z"), ZoneOffset.UTC)
        val api = FakeApi(response(0, dates(LocalDate.of(2026, 8, 2), 21), mmPerDay = 3.0))

        val summary = OpenMeteoWeatherProvider(api, instant).getRecentPrecipitation(region).getOrThrow()

        assertEquals(42.0, summary.totalPrecipitationMm, 0.0001)
        assertEquals(0, summary.daysSinceSignificantRain)
        assertEquals(1, api.calls)
    }
}
