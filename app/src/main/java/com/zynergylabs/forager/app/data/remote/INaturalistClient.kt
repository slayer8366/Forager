package com.zynergylabs.forager.app.data.remote

/** Builds the [INaturalistApi] instance, through [ApiClients], the one builder the app's JSON APIs share. */
object INaturalistClient {

    private const val BASE_URL = "https://api.inaturalist.org/v1/"

    /** The built client, separate from [create] so a test can send a request through the real one. */
    internal fun httpClient(debug: Boolean) = ApiClients.httpClient(debug)

    fun create(debug: Boolean): INaturalistApi = ApiClients.create(BASE_URL, INaturalistApi::class.java, debug)
}
