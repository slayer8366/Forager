package com.forager.app.data.remote

import com.forager.app.data.remote.dto.PrecipitationResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Raw Retrofit surface for the Open-Meteo forecast API (https://api.open-meteo.com/).
 *
 * This interface is the only place in the app that speaks Retrofit/Open-Meteo's wire format
 * directly. Domain and UI code depend on [com.forager.app.domain.WeatherProvider] and
 * [com.forager.app.domain.TripPlanningWeatherProvider] instead, per the isolated-integration-layer
 * rule in CLAUDE.md.
 */
interface OpenMeteoApi {

    /**
     * One request covering both the observed past and the forecast horizon.
     *
     * Deliberately a single call rather than one request for history and another for the forecast:
     * the two would be split across different response timestamps and could disagree about which
     * local day "today" is. The cost is that the caller now *must* split the returned arrays by
     * date — see `splitByReferenceDay` in OpenMeteoWeatherProvider, and the test file
     * OpenMeteoPastFutureSplitTest for why that is not optional.
     *
     * [daily] and [hourly] are defaulted rather than passed by callers so the requested variable
     * set is stated in one place. Both band families of each soil variable are requested; see
     * [com.forager.app.data.remote.dto.HourlySoilDto] for the measurements showing why.
     */
    @GET("v1/forecast")
    suspend fun getPrecipitation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = DAILY_VARIABLES,
        @Query("hourly") hourly: String = HOURLY_VARIABLES,
        @Query("past_days") pastDays: Int,
        @Query("forecast_days") forecastDays: Int,
        @Query("timezone") timezone: String = "auto",
    ): PrecipitationResponseDto

    companion object {
        const val DAILY_VARIABLES = "precipitation_sum,et0_fao_evapotranspiration"

        const val HOURLY_VARIABLES =
            "soil_moisture_0_to_7cm,soil_moisture_0_to_10cm," +
                "soil_moisture_7_to_28cm,soil_moisture_10_to_40cm," +
                "soil_temperature_0_to_7cm,soil_temperature_0_to_10cm"
    }
}
