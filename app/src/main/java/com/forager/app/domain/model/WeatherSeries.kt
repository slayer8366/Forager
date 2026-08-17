package com.forager.app.domain.model

import java.time.LocalDate

/**
 * Which soil depth band a value actually came from.
 *
 * Carried through to the domain rather than erased at the data layer because the band is not
 * constant across the world: Open-Meteo serves 0–7cm in the UK and Europe and 0–10cm in North
 * America, depending on which weather model `timezone=auto` selected (see
 * [com.forager.app.data.remote.dto.HourlySoilDto] for the per-location measurements). A reading
 * of "0.21 m³/m³" means something slightly different at each, so the band travels with the number
 * and the presentation layer can say which one it is showing.
 */
data class SoilDepthBand(val topCm: Int, val bottomCm: Int) {
    val label: String get() = "$topCm–$bottomCm cm"
}

/**
 * Which soil variables this location's weather model actually served, and an explicit reason for
 * each one it did not.
 *
 * The whole point of this type is that "unavailable" is a first-class, stated outcome rather than
 * a silent zero or a nearby-band substitution (CLAUDE.md: an unsupported capability returns an
 * explicit "unsupported", never a fabricated plausible value).
 */
data class SoilAvailability(
    /** Band used for shallow (root-zone) soil moisture, or null if no band returned data. */
    val shallowMoistureBand: SoilDepthBand?,
    /** Band used for the deeper soil-moisture reading, or null if no band returned data. */
    val deeperMoistureBand: SoilDepthBand?,
    /** Band used for shallow soil temperature, or null if no band returned data. */
    val temperatureBand: SoilDepthBand?,
    /**
     * Human-readable statements of what could not be provided and why, e.g. "Soil temperature is
     * unavailable at this location: the weather model Open-Meteo selected here serves neither the
     * 0–7 cm nor the 0–10 cm band." Empty when everything resolved.
     */
    val unavailable: List<String> = emptyList(),
) {
    val hasAnySoilData: Boolean
        get() = shallowMoistureBand != null || deeperMoistureBand != null || temperatureBand != null
}

/**
 * One local day of weather at a region. Null-valued fields mean "not served for this day", never
 * "zero".
 */
data class DailyWeather(
    val date: LocalDate,
    /**
     * True when this day is the reference day or later — i.e. not yet observed.
     *
     * Derived from [date] against the series' reference day, never from the entry's position in
     * the API's arrays. The reference day itself counts as forecast: Open-Meteo returns today in
     * the `forecast_days` portion and its daily aggregates are still partly modelled.
     */
    val isForecast: Boolean,
    val precipitationMm: Double,
    /** FAO-56 reference evapotranspiration for the day, mm. Null when not served. */
    val evapotranspirationMm: Double?,
    /** Daily mean volumetric water content in [SoilAvailability.shallowMoistureBand], m³/m³. */
    val shallowSoilMoistureM3M3: Double?,
    /** Daily mean volumetric water content in [SoilAvailability.deeperMoistureBand], m³/m³. */
    val deeperSoilMoistureM3M3: Double?,
    /** Daily mean soil temperature in [SoilAvailability.temperatureBand], °C. */
    val soilTemperatureMeanC: Double?,
    /** Coldest hour of the day in that band, °C. */
    val soilTemperatureMinC: Double?,
    /** Warmest hour of the day in that band, °C. */
    val soilTemperatureMaxC: Double?,
)

/**
 * A region's weather over a window spanning observed history and a forecast horizon.
 *
 * [referenceDay] is the local day at the *searched location*, resolved from the API's own
 * `utc_offset_seconds` rather than from the device clock — the two disagree routinely (a single
 * request moment on 2026-08-17 returned a window ending 2026-08-15 for Portland and 2026-08-16
 * for London).
 */
data class WeatherSeries(
    val region: Region,
    val referenceDay: LocalDate,
    /** Ordered oldest-first, one entry per local day, past days then forecast days. */
    val days: List<DailyWeather>,
    val soilAvailability: SoilAvailability,
) {
    /** Days strictly before [referenceDay] — observed history. */
    val observedDays: List<DailyWeather> get() = days.filterNot { it.isForecast }

    /** [referenceDay] and later — not yet observed. */
    val forecastDays: List<DailyWeather> get() = days.filter { it.isForecast }
}
