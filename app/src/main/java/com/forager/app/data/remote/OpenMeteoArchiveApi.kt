package com.forager.app.data.remote

import com.forager.app.data.remote.dto.HistoricalPrecipitationResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Raw Retrofit surface for the Open-Meteo historical archive API
 * (https://archive-api.open-meteo.com/), a different host from [OpenMeteoApi]'s forecast API and
 * a different request shape: an explicit [startDate]/[endDate] range rather than
 * `past_days`/`forecast_days`. This interface is the only place in the app that speaks this
 * endpoint's wire format directly — domain and UI code depend on
 * [com.forager.app.domain.HistoricalWeatherProvider] instead, per CLAUDE.md's
 * isolated-integration-layer rule.
 *
 * `daily=precipitation_sum` only: this endpoint exists solely to test the fruiting-lag
 * hypothesis, which only needs rain events (see
 * [com.forager.app.domain.ComputeTripWindowsUseCase.findSoakingEvents]), not soil or
 * evapotranspiration.
 */
interface OpenMeteoArchiveApi {

    @GET("v1/archive")
    suspend fun getHistoricalPrecipitation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = DAILY_VARIABLES,
        @Query("timezone") timezone: String = "auto",
    ): HistoricalPrecipitationResponseDto

    companion object {
        const val DAILY_VARIABLES = "precipitation_sum"
    }
}
