package com.forager.app.data.repository

import com.forager.app.data.remote.OpenMeteoApi
import com.forager.app.data.remote.dto.PrecipitationResponseDto
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.Region

class OpenMeteoWeatherProvider(
    private val api: OpenMeteoApi,
) : WeatherProvider {

    override suspend fun getRecentPrecipitation(region: Region): Result<ConditionsSummary> {
        return runCatching {
            api.getPrecipitation(
                latitude = region.lat,
                longitude = region.lng,
                pastDays = LOOKBACK_DAYS,
            )
        }.map { response -> toDomain(response, region) }
    }

    companion object {
        /**
         * Labeled, adjustable assumption (not data-derived): the daily rain total, in mm,
         * treated as "enough to matter" for fungi fruiting. Per CLAUDE.md, recorded here
         * rather than left implicit.
         */
        const val SIGNIFICANT_RAIN_MM = 2.0

        /**
         * Labeled, adjustable assumption: how many trailing days of precipitation history to
         * consider. Not derived from any measured rain-to-fruiting lag.
         */
        const val LOOKBACK_DAYS = 14
    }
}

/**
 * TEMPORARY NAIVE IMPLEMENTATION — deliberately wrong, so the past/future split test can be
 * watched failing before the fix is written (CLAUDE.md: see the failure before writing the fix).
 * Replaced in the next commit.
 */
internal fun summariseObservedConditions(
    dto: PrecipitationResponseDto,
    region: Region,
    referenceDay: java.time.LocalDate,
): ConditionsSummary = toDomain(dto, region)

/** Maps the Open-Meteo DTO onto [ConditionsSummary]. */
internal fun toDomain(dto: PrecipitationResponseDto, region: Region): ConditionsSummary {
    val precipitation = dto.daily.precipitationSum
    val total = precipitation.sum()
    // precipitation_sum is ordered oldest-to-newest, so the most recent day is the last entry.
    val daysSinceSignificantRain = precipitation.indices.reversed()
        .firstOrNull { index -> precipitation[index] >= OpenMeteoWeatherProvider.SIGNIFICANT_RAIN_MM }
        ?.let { index -> precipitation.lastIndex - index }

    return ConditionsSummary(
        region = region,
        totalPrecipitationMm = total,
        daysSinceSignificantRain = daysSinceSignificantRain,
    )
}
