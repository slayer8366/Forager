package com.zynergylabs.forager.app.domain.model

import java.time.LocalDate

/**
 * Recent observed rainfall for a region — raw current-conditions data, not a prediction.
 * Deliberately kept separate from [AvailabilityForecast]: see [com.zynergylabs.forager.app.domain.GetConditionsUseCase].
 */
data class ConditionsSummary(
    val region: Region,
    val totalPrecipitationMm: Double,
    /** Null when no day in the lookback window cleared the significant-rain threshold. */
    val daysSinceSignificantRain: Int?,
    /**
     * The observed days behind [totalPrecipitationMm], oldest first, one entry per day the response
     * carried a value for (data part C, RECORD -668: the Seasonal tab draws them as a 14-day bar
     * chart; before this they were summed and discarded). Nothing new is fetched for it: these are
     * the same `precipitation_sum` values the total is summed from.
     *
     * Defaulted to empty only so the many test fixtures that build a summary for other reasons need
     * no change; the one production builder, `OpenMeteoWeatherProvider.toDomain`, always passes it.
     * An empty list draws no chart (the rows above it still show).
     */
    val dailyRain: List<DailyRain> = emptyList(),
)

/** One observed day's rain total, millimetres. */
data class DailyRain(val date: LocalDate, val mm: Double)
