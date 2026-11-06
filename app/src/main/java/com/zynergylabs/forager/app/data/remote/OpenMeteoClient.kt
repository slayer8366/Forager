package com.zynergylabs.forager.app.data.remote

/** Builds the [OpenMeteoApi] instance, through [ApiClients], the one builder the app's JSON APIs share. */
object OpenMeteoClient {

    private const val BASE_URL = "https://api.open-meteo.com/"

    /** The built client, separate from [create] so a test can send a request through the real one. */
    internal fun httpClient(debug: Boolean) = ApiClients.httpClient(debug)

    fun create(debug: Boolean): OpenMeteoApi = ApiClients.create(BASE_URL, OpenMeteoApi::class.java, debug)
}
