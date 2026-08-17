package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TripWindowReport

/**
 * Fetches the weather series for a region and runs the trip-window search over it.
 *
 * The fetch and the computation are separate types so the computation stays a pure function of a
 * [com.forager.app.domain.model.WeatherSeries] and can be tested against synthetic series without
 * a network or a fake provider in the loop; this class is the thin seam that joins them.
 *
 * Deliberately unfused with [PredictAvailabilityUseCase]'s ranking, for the same reason
 * [GetConditionsUseCase] is: the ranked list is built on real observation counts, and folding an
 * unmeasured weather rule of thumb into it would make the ranking look data-derived when that part
 * of it would not be.
 */
class GetTripWindowsUseCase(
    private val weatherProvider: TripPlanningWeatherProvider,
    private val computeTripWindows: ComputeTripWindowsUseCase,
) {
    suspend operator fun invoke(region: Region): Result<TripWindowReport> =
        weatherProvider.getWeatherSeries(region).map(computeTripWindows::invoke)
}
