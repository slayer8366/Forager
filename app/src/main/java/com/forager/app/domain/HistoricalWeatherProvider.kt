package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import java.time.LocalDate

/**
 * Owned abstraction over past-observed daily rainfall for an arbitrary historical date range —
 * years back, not the fixed lookback [WeatherProvider] and [TripPlanningWeatherProvider] serve.
 *
 * A third weather interface rather than a method added to either of the other two, for the same
 * reason [TripPlanningWeatherProvider] is its own interface and not a method on [WeatherProvider]:
 * a fixed-lookback conditions card, a past-plus-forecast trip-window search, and an
 * arbitrary-historical-range fetch are three different capabilities, and forcing every
 * implementation (including test fakes) to carry a body for a capability it doesn't have is worse
 * than a third seam (CLAUDE.md: new capability is a new path, not a conditional threaded into
 * working code).
 *
 * Exists to test [FruitingPatternAssumptions.FRUITING_LAG_DAYS] against real historical sightings
 * and real historical rainfall — see [ComputeFruitingLagDistributionUseCase] and
 * [GetSeasonalPatternUseCase]. It is not wired into [PredictAvailabilityUseCase] or
 * [ComputeTripWindowsUseCase]: neither the ranked list nor the trip-window search gets any more
 * "data-derived" than they already are from this existing.
 */
interface HistoricalWeatherProvider {
    /**
     * Daily precipitation for [region] across [from]..[through] inclusive, both dates given by the
     * caller rather than a fixed lookback. Every returned [DailyWeather] has `isForecast = false`
     * and null soil/evapotranspiration fields — this interface only ever asks for
     * `daily=precipitation_sum`, since that is the only historical signal
     * [ComputeTripWindowsUseCase.findSoakingEvents] (reused by [ComputeFruitingLagDistributionUseCase]) needs.
     */
    suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>>
}
