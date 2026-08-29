package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region

/**
 * The reference day's own forecast for [region] — what the Seasonal tab's weather panel shows
 * next to [GetConditionsUseCase]'s observed rainfall, per PANEL-CONTENTS-DISPATCH.md item 2
 * ("current conditions plus the day's forecast").
 *
 * Wraps [TripPlanningWeatherProvider.getWeatherSeries] with its own fetch rather than reusing
 * [GetTripWindowsUseCase]'s call: that class discards the raw [com.forager.app.domain.model.WeatherSeries]
 * once [ComputeTripWindowsUseCase] has reduced it to a [com.forager.app.domain.model.TripWindowReport],
 * and that report's own doc comment restricts its fields to trip-window measurements and date
 * arithmetic for a different question (which of the next several days is worth a trip) — not
 * today's own forecast. A second identical-parameter Open-Meteo request is the same tradeoff
 * [GetConditionsUseCase] and [GetTripWindowsUseCase] already accept for the same reason: see
 * [com.forager.app.data.repository.OpenMeteoWeatherProvider.fetch]'s own doc comment.
 */
class GetTodaysForecastUseCase(
    private val weatherProvider: TripPlanningWeatherProvider,
) {
    /** The first forecast day in the series — the reference day itself; see [com.forager.app.domain.model.DailyWeather.isForecast]. */
    suspend operator fun invoke(region: Region): Result<DailyWeather?> =
        weatherProvider.getWeatherSeries(region).map { series -> series.forecastDays.firstOrNull() }
}
