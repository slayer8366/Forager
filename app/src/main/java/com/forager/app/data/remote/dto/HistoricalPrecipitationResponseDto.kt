package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/archive on `archive-api.open-meteo.com`.
 * https://open-meteo.com/en/docs/historical-weather-api
 *
 * A dedicated type rather than reuse of [PrecipitationResponseDto]: the two response shapes
 * overlap (both carry a `daily.time`/`daily.precipitation_sum` block) but come from different
 * hosts with different query parameters (`start_date`/`end_date` here, `past_days`/
 * `forecast_days` there) and no hourly soil block at all. Coupling them would mean a future change
 * to one silently drifting the other — this interface is meant to be the only place that speaks
 * the archive endpoint's shape, per CLAUDE.md's isolated-integration-layer rule.
 *
 * Verified against the live API 2026-08-17 (`scripts/verify-open-meteo-historical-fields.sh`): a
 * request for `start_date=2016-01-01&end_date=2024-12-31` (3288 days) returned every day fully
 * populated in one response, so this app makes one unchunked request per search rather than
 * paging a long historical range.
 */
@Serializable
data class HistoricalPrecipitationResponseDto(
    /** Same role as [PrecipitationResponseDto.utcOffsetSeconds]: resolves which local day each entry is. */
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("daily") val daily: HistoricalDailyPrecipitationDto = HistoricalDailyPrecipitationDto(),
)

@Serializable
data class HistoricalDailyPrecipitationDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
)
