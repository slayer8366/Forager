package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/forecast?daily=precipitation_sum.
 * https://open-meteo.com/en/docs
 */
@Serializable
data class PrecipitationResponseDto(
    /**
     * The location's offset from UTC, in seconds, as Open-Meteo resolved it for `timezone=auto`.
     * This is how the caller works out which local day "today" is at the *searched location* —
     * which is not always the device's day. Verified 2026-08-17: a single request moment returned
     * a window ending 2026-08-15 for Portland and 2026-08-16 for London.
     *
     * Nullable with no default value substituted: a response without it cannot be split into past
     * and future, and the caller has to say so rather than assume the device's day.
     */
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("daily") val daily: DailyPrecipitationDto,
)

@Serializable
data class DailyPrecipitationDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double> = emptyList(),
)
