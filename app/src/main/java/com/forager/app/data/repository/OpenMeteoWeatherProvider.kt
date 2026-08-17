package com.forager.app.data.repository

import com.forager.app.data.remote.OpenMeteoApi
import com.forager.app.data.remote.dto.DailyPrecipitationDto
import com.forager.app.data.remote.dto.HourlySoilDto
import com.forager.app.data.remote.dto.PrecipitationResponseDto
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SoilAvailability
import com.forager.app.domain.model.SoilDepthBand
import com.forager.app.domain.model.WeatherSeries
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class OpenMeteoWeatherProvider(
    private val api: OpenMeteoApi,
    /**
     * Injected so the location-local reference day is testable without waiting for midnight. The
     * clock is only ever read in UTC and then shifted by the response's own `utc_offset_seconds`;
     * the device's timezone is never consulted, because it is routinely not the searched
     * location's.
     */
    private val clock: Clock = Clock.systemUTC(),
) : WeatherProvider, TripPlanningWeatherProvider {

    override suspend fun getRecentPrecipitation(region: Region): Result<ConditionsSummary> =
        fetch(region).mapCatching { (response, referenceDay) ->
            summariseObservedConditions(response, region, referenceDay)
        }

    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        fetch(region).mapCatching { (response, referenceDay) ->
            toWeatherSeries(response, region, referenceDay)
        }

    /**
     * One request serving both entry points, and the reference day resolved from its response.
     *
     * Deliberately a single call: two calls made moments apart could straddle local midnight at
     * the searched location and disagree about which day is "today", which is exactly the class of
     * bug the past/future split exists to prevent.
     */
    private suspend fun fetch(region: Region): Result<Pair<PrecipitationResponseDto, LocalDate>> =
        runCatching {
            val response = api.getPrecipitation(
                latitude = region.lat,
                longitude = region.lng,
                pastDays = LOOKBACK_DAYS,
                forecastDays = FruitingPatternAssumptions.FORECAST_HORIZON_DAYS,
            )
            response to resolveReferenceDay(response)
        }

    /**
     * The local day at the searched location, from the response's own UTC offset.
     *
     * Throws rather than falling back to the device's day when the offset is absent: a wrong
     * reference day silently shifts every past/future decision by one, which is unrecoverable
     * downstream and indistinguishable from correct output (CLAUDE.md: no unlogged default
     * fallback; report unsupported rather than fabricate).
     */
    private fun resolveReferenceDay(response: PrecipitationResponseDto): LocalDate {
        val offsetSeconds = response.utcOffsetSeconds
            ?: error(
                "Open-Meteo returned no utc_offset_seconds, so the local day at the searched " +
                    "location is unknown and observed days cannot be told apart from forecast days.",
            )
        return LocalDate.now(clock.withZone(ZoneOffset.ofTotalSeconds(offsetSeconds)))
    }

    companion object {
        /**
         * Labeled, adjustable assumption (not data-derived): the daily rain total, in mm,
         * treated as "enough to matter" for fungi fruiting. Per CLAUDE.md, recorded here
         * rather than left implicit.
         *
         * Now defined as [FruitingPatternAssumptions.RAIN_DAY_MIN_MM] so this threshold and the
         * one the trip-window search uses are the same number by construction rather than by
         * coincidence. The value is unchanged.
         */
        const val SIGNIFICANT_RAIN_MM = FruitingPatternAssumptions.RAIN_DAY_MIN_MM

        /**
         * Labeled, adjustable assumption: how many trailing days of precipitation history to
         * consider. Not derived from any measured rain-to-fruiting lag.
         */
        const val LOOKBACK_DAYS = FruitingPatternAssumptions.OBSERVED_HISTORY_DAYS
    }
}

/**
 * Splits [dto]'s daily arrays at [referenceDay] and summarises only the observed part.
 *
 * This is the whole point of the file. `past_days=14&forecast_days=0` used to guarantee that every
 * entry was in the past, so summing the array and scanning back from its last entry were both
 * correct by accident of the request shape. Now that the same request also carries seven forecast
 * days, "past" has to be decided from the dates in `daily.time` against the location's own local
 * day — never from an entry's position in the array.
 *
 * A day is observed when its date is strictly before [referenceDay]. The reference day itself is
 * forecast: Open-Meteo returns today inside the `forecast_days` portion (verified against the live
 * API — `past_days=14&forecast_days=0` ends at *yesterday*), and today's daily aggregates are
 * still partly modelled.
 *
 * That boundary is also what keeps the shipped conditions card reading the same as before. Both
 * numbers on it are defined against the newest *observed* day, i.e. yesterday: "0 days since
 * significant rain" has always meant "it rained on the last day of the observed window", and
 * measuring the gap from today instead would shift every shipped figure by one.
 */
internal fun summariseObservedConditions(
    dto: PrecipitationResponseDto,
    region: Region,
    referenceDay: LocalDate,
): ConditionsSummary = toDomain(observedSlice(dto, referenceDay), region)

/**
 * The entries of [dto]'s daily arrays whose date is strictly before [referenceDay], as a DTO of
 * the same shape.
 *
 * Returning the narrowed DTO rather than raw lists lets [toDomain] stay exactly as it shipped —
 * unchanged code, still covered by its original tests, now with its precondition ("every entry is
 * in the past") actually guaranteed by the caller instead of by the request shape.
 */
internal fun observedSlice(dto: PrecipitationResponseDto, referenceDay: LocalDate): PrecipitationResponseDto {
    val dates = dto.daily.time.map(LocalDate::parse)
    val observed = dates.indices.filter { dates[it].isBefore(referenceDay) }
    return dto.copy(
        daily = DailyPrecipitationDto(
            time = observed.map { dto.daily.time[it] },
            precipitationSum = observed.mapNotNull { dto.daily.precipitationSum.getOrNull(it) },
            et0FaoEvapotranspiration = observed.map { dto.daily.et0FaoEvapotranspiration.getOrNull(it) },
        ),
    )
}

/**
 * Maps the Open-Meteo DTO onto [ConditionsSummary].
 *
 * Precondition: every entry in `dto.daily` is an observed past day. Callers get that by going
 * through [summariseObservedConditions]; handing this function a response that still contains
 * forecast days silently produces wrong numbers, which is the bug
 * `OpenMeteoPastFutureSplitTest` exists to hold shut.
 */
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

/** One resolved soil variable: which depth band actually served data, and its hourly values. */
private class ResolvedSoilSeries(val band: SoilDepthBand, val hourly: List<Double?>)

/**
 * Picks whichever of [candidates] the location's weather model actually served.
 *
 * Open-Meteo's soil depth bands are model-specific and `timezone=auto` chooses a different model
 * per location, so a band the chosen model lacks comes back as a full-length all-null array with
 * `hourly_units` reporting `"undefined"` — HTTP 200, no error (measured across eight locations;
 * see [HourlySoilDto]). Neither band family is universally available: North America served only
 * 0–10 cm and the UK served only 0–7 cm on 2026-08-17.
 *
 * So both are requested and the populated one wins. Choosing a neighbouring depth band is not a
 * substituted proxy — it is the same physical quantity over a slightly different depth — and the
 * band travels with the value all the way into [SoilAvailability] so the reader is told which one
 * they are looking at. Hardcoding one band and shipping a permanently empty soil signal across
 * either continent was the rejected alternative.
 *
 * Returns null when no candidate served anything; the caller reports that as an explicit
 * unavailability rather than substituting a different variable.
 */
private fun resolveSoilSeries(
    hourly: HourlySoilDto?,
    units: Map<String, String>,
    candidates: List<Triple<String, SoilDepthBand, (HourlySoilDto) -> List<Double?>>>,
): ResolvedSoilSeries? {
    if (hourly == null) return null
    for ((name, band, select) in candidates) {
        val values = select(hourly)
        // The unit string is checked as well as the values, because "undefined" is Open-Meteo's
        // own marker for "this variable is not served here" and is the signal that distinguishes
        // an unsupported band from a genuine run of missing readings.
        val servedUnit = units[name]?.takeIf { it != "undefined" }
        if (values.any { it != null } && servedUnit != null) return ResolvedSoilSeries(band, values)
    }
    return null
}

/**
 * Reduces hourly readings to one value per local day, keyed by date.
 *
 * @param reduce how to collapse a day's readings. Called only for days that cleared
 *   [FruitingPatternAssumptions.MIN_HOURLY_READINGS_PER_DAY] non-null readings; a day with fewer
 *   gets no entry at all rather than a mean of whatever arrived.
 */
private fun reduceToDaily(
    times: List<String>,
    values: List<Double?>,
    reduce: (List<Double>) -> Double,
): Map<LocalDate, Double> {
    val byDay = linkedMapOf<LocalDate, MutableList<Double>>()
    times.forEachIndexed { index, timestamp ->
        val value = values.getOrNull(index) ?: return@forEachIndexed
        // Hourly timestamps are "2026-08-17T13:00" in the location's own timezone, so the date
        // prefix is already the local day. No zone conversion is needed or wanted here.
        val day = LocalDate.parse(timestamp.substring(0, 10))
        byDay.getOrPut(day) { mutableListOf() }.add(value)
    }
    return byDay
        .filterValues { it.size >= FruitingPatternAssumptions.MIN_HOURLY_READINGS_PER_DAY }
        .mapValues { (_, dayValues) -> reduce(dayValues) }
}

/**
 * Maps the response onto a [WeatherSeries], splitting observed days from forecast days by date.
 *
 * **How hourly soil data is reduced to a daily number, and why.** Soil moisture and soil
 * temperature are hourly-only on Open-Meteo — asking for them as daily variables is rejected
 * outright ("Cannot initialize ForecastVariableDaily from invalid String value
 * soil_moisture_0_to_7cm"), verified against the live API. They have to be collapsed to something
 * daily to sit alongside `precipitation_sum`, and the two are collapsed differently on purpose:
 *
 * - **Soil moisture: daily mean.** Volumetric water content is a slowly-varying state, not an
 *   event; its diurnal swing at 7–10 cm depth is small, and "how wet was the soil that day" is
 *   exactly what the mean answers. A daily max was rejected as biased by a single post-rain spike,
 *   and a fixed hour-of-day reading as arbitrary.
 * - **Soil temperature: mean, min and max, all three.** Temperature in the top centimetres has a
 *   real diurnal cycle — measured swings of several degrees within one day in the probe data — so
 *   a lone mean would present "soil is 14 °C" while hiding a 9–20 °C range. The mean is the
 *   comparable headline; the min and max are carried so a stated temperature band can be shown
 *   against the actual spread rather than against a number that flattens it.
 *
 * Either way the reduction only runs on a day with at least
 * [FruitingPatternAssumptions.MIN_HOURLY_READINGS_PER_DAY] readings; a partial day reports no
 * value rather than a mean of a fragment.
 */
internal fun toWeatherSeries(
    dto: PrecipitationResponseDto,
    region: Region,
    referenceDay: LocalDate,
): WeatherSeries {
    val hourly = dto.hourly
    val units = dto.hourlyUnits

    val shallowMoisture = resolveSoilSeries(
        hourly, units,
        listOf(
            Triple("soil_moisture_0_to_7cm", SoilDepthBand(0, 7)) { it.soilMoisture0To7Cm },
            Triple("soil_moisture_0_to_10cm", SoilDepthBand(0, 10)) { it.soilMoisture0To10Cm },
        ),
    )
    val deeperMoisture = resolveSoilSeries(
        hourly, units,
        listOf(
            Triple("soil_moisture_7_to_28cm", SoilDepthBand(7, 28)) { it.soilMoisture7To28Cm },
            Triple("soil_moisture_10_to_40cm", SoilDepthBand(10, 40)) { it.soilMoisture10To40Cm },
        ),
    )
    val temperature = resolveSoilSeries(
        hourly, units,
        listOf(
            Triple("soil_temperature_0_to_7cm", SoilDepthBand(0, 7)) { it.soilTemperature0To7Cm },
            Triple("soil_temperature_0_to_10cm", SoilDepthBand(0, 10)) { it.soilTemperature0To10Cm },
        ),
    )

    val times = hourly?.time.orEmpty()
    val shallowByDay = shallowMoisture?.let { reduceToDaily(times, it.hourly) { v -> v.average() } }.orEmpty()
    val deeperByDay = deeperMoisture?.let { reduceToDaily(times, it.hourly) { v -> v.average() } }.orEmpty()
    val tempMeanByDay = temperature?.let { reduceToDaily(times, it.hourly) { v -> v.average() } }.orEmpty()
    val tempMinByDay = temperature?.let { reduceToDaily(times, it.hourly) { v -> v.min() } }.orEmpty()
    val tempMaxByDay = temperature?.let { reduceToDaily(times, it.hourly) { v -> v.max() } }.orEmpty()

    val days = dto.daily.time.mapIndexedNotNull { index, timestamp ->
        val date = LocalDate.parse(timestamp)
        val precipitation = dto.daily.precipitationSum.getOrNull(index) ?: return@mapIndexedNotNull null
        DailyWeather(
            date = date,
            // The split, and the only place it is decided: by date, never by array position.
            isForecast = !date.isBefore(referenceDay),
            precipitationMm = precipitation,
            evapotranspirationMm = dto.daily.et0FaoEvapotranspiration.getOrNull(index),
            shallowSoilMoistureM3M3 = shallowByDay[date],
            deeperSoilMoistureM3M3 = deeperByDay[date],
            soilTemperatureMeanC = tempMeanByDay[date],
            soilTemperatureMinC = tempMinByDay[date],
            soilTemperatureMaxC = tempMaxByDay[date],
        )
    }

    return WeatherSeries(
        region = region,
        referenceDay = referenceDay,
        days = days,
        soilAvailability = SoilAvailability(
            shallowMoistureBand = shallowMoisture?.band,
            deeperMoistureBand = deeperMoisture?.band,
            temperatureBand = temperature?.band,
            unavailable = listOfNotNull(
                unavailableNote(shallowMoisture, "Shallow soil moisture", "0–7 cm or 0–10 cm"),
                unavailableNote(deeperMoisture, "Deeper soil moisture", "7–28 cm or 10–40 cm"),
                unavailableNote(temperature, "Soil temperature", "0–7 cm or 0–10 cm"),
            ),
        ),
    )
}

/** An explicit "unsupported here", naming what is missing and what was asked for. */
private fun unavailableNote(resolved: ResolvedSoilSeries?, what: String, bands: String): String? =
    if (resolved != null) {
        null
    } else {
        "$what is unavailable at this location: the weather model Open-Meteo selected here " +
            "served no data for the $bands band."
    }
