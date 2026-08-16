package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/forecast?daily=precipitation_sum.
 * https://open-meteo.com/en/docs
 */
@Serializable
data class PrecipitationResponseDto(
    @SerialName("daily") val daily: DailyPrecipitationDto,
)

@Serializable
data class DailyPrecipitationDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double> = emptyList(),
)
