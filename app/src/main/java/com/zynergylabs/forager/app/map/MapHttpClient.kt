package com.zynergylabs.forager.app.map

import android.content.Context
import com.zynergylabs.forager.app.net.AppUserAgent
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

/**
 * The HTTP client MapLibre sends every request through: tiles, styles, glyphs and offline region
 * downloads alike (all go via `NativeHttpRequest` -> `ModuleProvider.createHttpRequest` ->
 * `HttpRequestImpl`; verified with `javap` on 13.5.0, offline sharing it is inferred from that
 * single Java entry point and not observed on a device).
 *
 * MapLibre's `HttpRequestImpl.executeRequest` adds its own `User-Agent` header to each request
 * before the client sees it, and the string is a private static final, so the header cannot be
 * configured; an application interceptor replaces it with [userAgent].
 *
 * What is kept from MapLibre's own default client (`getOrCreateDefaultClient`): a plain
 * [OkHttpClient] with a [Dispatcher] allowing 20 requests per host (its `getDispatcher` gives 10
 * below API 21; minSdk here is 26). It sets no timeouts and no cache of its own, and neither does this.
 *
 * [tileRetry] comes after the User-Agent, so every resend it makes carries the app's header: in the app it is
 * [tileRetryInterceptor], which holds a failed tile and sends it again (dispatch 2026-10-23-06).
 */
internal fun mapHttpClient(userAgent: String, tileRetry: Interceptor): OkHttpClient =
    OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 })
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .addInterceptor(tileRetry)
        .build()

/**
 * Hands [mapHttpClient] to MapLibre. Must run after `MapLibre.getInstance`: this call loads
 * `HttpRequestImpl`, whose static initialiser reads `MapLibre.getApplicationContext()`. Safe to
 * call again; the client is read per request, so a call before the first request is enough.
 */
internal fun installMapHttpClient(context: Context, userAgent: String = AppUserAgent.forThisApp()) {
    // Each request MapLibre makes is marked as an offline-area download's or the map's (MapRequestUsage.kt), so the
    // tile retry holds only the map's (dispatch 2026-10-23-06 Amendment 3). MapLibre reads the provider per request.
    val provider = MapLibre.getModuleProvider()
    if (provider !is UsageMarkingModuleProvider) MapLibre.setModuleProvider(UsageMarkingModuleProvider(provider))
    HttpRequestUtil.setOkHttpClient(UsageTaggingCallFactory(mapHttpClient(userAgent, tileRetryInterceptor(context))))
}
