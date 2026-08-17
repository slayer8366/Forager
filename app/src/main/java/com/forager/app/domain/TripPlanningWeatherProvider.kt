package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.WeatherSeries

/**
 * Owned abstraction over the fuller weather picture a trip plan needs: observed history plus a
 * forecast horizon, precipitation plus soil state and evapotranspiration.
 *
 * A sibling of [WeatherProvider] rather than a method added to it, on purpose. [WeatherProvider]
 * has one job — the recent-rainfall figures the conditions card ships today — and it has several
 * implementations, including test fakes. Adding an abstract method would have forced every one of
 * them to grow a body for a capability they do not have, and the honest body for a fake is
 * "unsupported", which is a worse thing to have lying around than a second interface. This is
 * CLAUDE.md's "new capability is a new path, not a conditional threaded into working code",
 * applied to an interface. In production both are the same object
 * ([com.forager.app.data.repository.OpenMeteoWeatherProvider]) served by a single API call.
 */
interface TripPlanningWeatherProvider {
    /**
     * Observed history and forecast for [region], as one series with the past/future boundary
     * made explicit.
     *
     * Fails rather than guesses when the response cannot be split — for example when it carries no
     * timezone offset, so the location's own local day is unknown.
     */
    suspend fun getWeatherSeries(region: Region): Result<WeatherSeries>
}
