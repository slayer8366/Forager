package com.forager.app.data.remote

import com.forager.app.data.remote.dto.PrecipitationResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Raw Retrofit surface for the Open-Meteo forecast API (https://api.open-meteo.com/).
 *
 * This interface is the only place in the app that speaks Retrofit/Open-Meteo's wire format
 * directly. Domain and UI code depend on [com.forager.app.domain.WeatherProvider] instead, per
 * the isolated-integration-layer rule in CLAUDE.md.
 */
interface OpenMeteoApi {

    /** Daily precipitation totals for the trailing [pastDays] days, no forecast days. */
    @GET("v1/forecast")
    suspend fun getPrecipitation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "precipitation_sum",
        @Query("past_days") pastDays: Int,
        @Query("forecast_days") forecastDays: Int = 0,
        @Query("timezone") timezone: String = "auto",
    ): PrecipitationResponseDto
}
