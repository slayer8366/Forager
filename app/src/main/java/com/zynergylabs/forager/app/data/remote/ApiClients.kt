package com.zynergylabs.forager.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.zynergylabs.forager.app.net.UserAgentInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * The one builder for the app's JSON APIs (dispatch 2026-09-28-658, D4): [INaturalistClient],
 * [OpenMeteoClient] and [OpenMeteoArchiveClient] each had a hand-copied OkHttp and Retrofit
 * builder that differed only in the base URL and the API type, and each said it was "the only
 * place that constructs Retrofit/OkHttp". This is the one place for these three; a change to the
 * User-Agent, the timeouts or the debug logging now lands in all of them. The map's tile client
 * (`map/MapHttpClient`) is built separately, for MapLibre, and is not one of these.
 */
internal object ApiClients {

    /** The OkHttp client every JSON API uses: the app's User-Agent, 15 s timeouts, and request logging in debug builds. */
    fun httpClient(debug: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (debug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()

    /** A Retrofit [api] at [baseUrl] over [httpClient], reading JSON that may carry keys it does not know. */
    fun <T> create(baseUrl: String, api: Class<T>, debug: Boolean): T {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient(debug))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(api)
    }
}
