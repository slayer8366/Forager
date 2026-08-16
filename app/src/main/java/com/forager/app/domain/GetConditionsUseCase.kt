package com.forager.app.domain

import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.Region

/**
 * Fetches recent observed precipitation for a region. Deliberately no fusion with
 * [PredictAvailabilityUseCase]'s ranking — see that class's doc comment. Pure and
 * Android-framework-free so it's unit-testable headless.
 */
class GetConditionsUseCase(
    private val weatherProvider: WeatherProvider,
) {
    suspend operator fun invoke(region: Region): Result<ConditionsSummary> {
        return weatherProvider.getRecentPrecipitation(region)
    }
}
