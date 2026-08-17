package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/forecast.
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
    /**
     * Present only when the request asked for hourly variables. Soil moisture and soil temperature
     * are hourly-only on Open-Meteo — the daily endpoint rejects them outright ("Cannot initialize
     * ForecastVariableDaily from invalid String value soil_moisture_0_to_7cm", verified against
     * the live API 2026-08-17).
     */
    @SerialName("hourly") val hourly: HourlySoilDto? = null,
    /**
     * Per-variable units, e.g. `{"soil_moisture_0_to_7cm": "m³/m³"}`. Load-bearing, not
     * decoration: Open-Meteo reports a requested-but-unavailable variable as `"undefined"` here
     * with an all-null value array and an HTTP 200 (see [HourlySoilDto]).
     */
    @SerialName("hourly_units") val hourlyUnits: Map<String, String> = emptyMap(),
)

@Serializable
data class DailyPrecipitationDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double> = emptyList(),
    /**
     * FAO-56 reference evapotranspiration, mm/day — how much water the atmosphere pulled out of a
     * reference surface that day. Used as the drying-side counterweight to rainfall. Verified
     * available as a *daily* variable at every location probed (Portland, London, Berlin), with
     * full coverage across both the past and forecast portions of the window.
     */
    @SerialName("et0_fao_evapotranspiration") val et0FaoEvapotranspiration: List<Double?> = emptyList(),
)

/**
 * The hourly block, when requested.
 *
 * **Every list here is `List<Double?>` and every field is defaulted, on purpose.** Open-Meteo's
 * soil depth bands are model-specific and `timezone=auto` picks a different weather model per
 * location. A band the chosen model does not carry comes back as a full-length array of `null`s
 * with `hourly_units` reporting `"undefined"` — HTTP 200, no error, no warning. Measured against
 * the live API on 2026-08-17 with `past_days=14&forecast_days=7`:
 *
 * | location  | 0–7cm / 7–28cm  | 0–10cm / 10–40cm |
 * |-----------|-----------------|------------------|
 * | Portland  | 0/504 non-null  | 504/504 non-null |
 * | NYC       | 0/504           | 504/504          |
 * | Denver    | 0/504           | 504/504          |
 * | Vancouver | 0/504           | 504/504          |
 * | London    | 504/504         | 0/504            |
 * | Berlin    | 504/504         | 504/504          |
 * | Sydney    | 504/504         | 504/504          |
 * | Tokyo     | 504/504         | 504/504          |
 *
 * So *neither* band family is universally available: hardcoding 0–7cm produces a permanently
 * empty soil signal across North America, and hardcoding 0–10cm produces one in the UK. Both
 * families are therefore requested and the one that actually returned data is resolved at parse
 * time — see `resolveSoilSeries` in OpenMeteoWeatherProvider.
 *
 * Unknown variable *names* are a different case and do fail loudly: `hourly=totally_made_up`
 * returns HTTP 400. Only real-but-unavailable-here variables null out silently.
 */
@Serializable
data class HourlySoilDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("soil_moisture_0_to_7cm") val soilMoisture0To7Cm: List<Double?> = emptyList(),
    @SerialName("soil_moisture_0_to_10cm") val soilMoisture0To10Cm: List<Double?> = emptyList(),
    @SerialName("soil_moisture_7_to_28cm") val soilMoisture7To28Cm: List<Double?> = emptyList(),
    @SerialName("soil_moisture_10_to_40cm") val soilMoisture10To40Cm: List<Double?> = emptyList(),
    @SerialName("soil_temperature_0_to_7cm") val soilTemperature0To7Cm: List<Double?> = emptyList(),
    @SerialName("soil_temperature_0_to_10cm") val soilTemperature0To10Cm: List<Double?> = emptyList(),
)
