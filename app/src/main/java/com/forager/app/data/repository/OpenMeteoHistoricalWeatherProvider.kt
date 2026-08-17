package com.forager.app.data.repository

import com.forager.app.data.remote.OpenMeteoArchiveApi
import com.forager.app.data.remote.dto.HistoricalPrecipitationResponseDto
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * [HistoricalWeatherProvider] over Open-Meteo's historical archive API
 * (`archive-api.open-meteo.com/v1/archive`).
 *
 * **Location simplification.** Fetched at [Region.lat]/[Region.lng] — the search's centre point —
 * not per-sighting coordinates each sighting the fruiting-lag distribution considers actually sits
 * at. This is the same simplification [GetConditionsUseCase][com.forager.app.domain.GetConditionsUseCase]
 * and [OpenMeteoWeatherProvider] already make for the shipped conditions card and trip-window
 * search, applied here for the same reason and now written down rather than left implicit: a
 * region can span up to [Region.MAX_RADIUS_KM] km, and per-sighting weather would mean one
 * archive request per sighting rather than one per search. The tradeoff this makes is real —
 * rainfall at the centre can differ from rainfall 40km away at a sighting's actual position — and
 * is the same tradeoff the rest of this codebase already accepts for the same reason.
 *
 * **One request per search, unchunked.** Verified against the live API 2026-08-17
 * (`scripts/verify-open-meteo-historical-fields.sh`): a single request spanning 2016-01-01 to
 * 2024-12-31 (3288 days) returned every day fully populated, so a multi-year window — the
 * realistic size for [from]..[through] when a species has sightings spread across many years —
 * does not need to be split into multiple requests.
 */
class OpenMeteoHistoricalWeatherProvider(
    private val api: OpenMeteoArchiveApi,
) : HistoricalWeatherProvider {

    override suspend fun getHistoricalPrecipitation(
        region: Region,
        from: LocalDate,
        through: LocalDate,
    ): Result<List<DailyWeather>> = runCatchingCancellable {
        require(!from.isAfter(through)) { "from ($from) must not be after through ($through)" }
        val response = api.getHistoricalPrecipitation(
            latitude = region.lat,
            longitude = region.lng,
            startDate = from.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDate = through.format(DateTimeFormatter.ISO_LOCAL_DATE),
        )
        toDailyWeather(response)
    }
}

/**
 * Maps the archive DTO onto [DailyWeather]. Every entry is historical (`isForecast = false`) and
 * carries no soil or evapotranspiration data — this provider only ever requests
 * `daily=precipitation_sum`.
 *
 * A date with no precipitation value is dropped rather than defaulted to zero, mirroring
 * [toWeatherSeries]'s same rule for the forecast endpoint: a missing day must not read as a
 * confirmed-dry one, and [ComputeTripWindowsUseCase.findSoakingEvents][com.forager.app.domain.ComputeTripWindowsUseCase]'s
 * consecutive-run detection already breaks a run on a date gap, so a dropped day cannot silently
 * bridge two rain events into one.
 */
internal fun toDailyWeather(dto: HistoricalPrecipitationResponseDto): List<DailyWeather> =
    dto.daily.time.mapIndexedNotNull { index, timestamp ->
        val precipitation = dto.daily.precipitationSum.getOrNull(index) ?: return@mapIndexedNotNull null
        DailyWeather(
            date = LocalDate.parse(timestamp),
            isForecast = false,
            precipitationMm = precipitation,
            evapotranspirationMm = null,
            shallowSoilMoistureM3M3 = null,
            deeperSoilMoistureM3M3 = null,
            soilTemperatureMeanC = null,
            soilTemperatureMinC = null,
            soilTemperatureMaxC = null,
        )
    }
