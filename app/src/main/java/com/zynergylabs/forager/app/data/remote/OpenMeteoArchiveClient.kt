package com.zynergylabs.forager.app.data.remote

/**
 * Builds the [OpenMeteoArchiveApi] instance, through [ApiClients], the one builder the app's JSON
 * APIs share. A separate object from [OpenMeteoClient] because the historical archive API is served
 * from a different host (`archive-api.open-meteo.com`, not `api.open-meteo.com`): a different base
 * URL, not a different builder.
 */
object OpenMeteoArchiveClient {

    private const val BASE_URL = "https://archive-api.open-meteo.com/"

    /** The built client, separate from [create] so a test can send a request through the real one. */
    internal fun httpClient(debug: Boolean) = ApiClients.httpClient(debug)

    fun create(debug: Boolean): OpenMeteoArchiveApi = ApiClients.create(BASE_URL, OpenMeteoArchiveApi::class.java, debug)
}
