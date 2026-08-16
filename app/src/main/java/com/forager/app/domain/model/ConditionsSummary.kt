package com.forager.app.domain.model

/**
 * Recent observed rainfall for a region — raw current-conditions data, not a prediction.
 * Deliberately kept separate from [AvailabilityForecast]: see [com.forager.app.domain.GetConditionsUseCase].
 */
data class ConditionsSummary(
    val region: Region,
    val totalPrecipitationMm: Double,
    /** Null when no day in the lookback window cleared the significant-rain threshold. */
    val daysSinceSignificantRain: Int?,
)
