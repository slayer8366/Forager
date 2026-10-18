package com.zynergylabs.forager.app.map

import android.content.Context
import android.util.Log
import org.maplibre.android.MapLibre

/**
 * MapLibre's offline-region and tile-resource database is left where the SDK puts it. Observed on
 * hardware (the S22 and the tablet, `device-evidence/2026-09-28-*`): `files/mbgl-offline.db` under
 * the app's files directory, which Android's "Clear cache" does not touch.
 *
 * **Rejected: redirecting it.** This file used to call `FileSource.setResourcesCachePath` to a
 * `files/maplibre-offline` directory before `MapLibre.getInstance`. That ordering made
 * `getInstance`'s configuration check throw `MapLibreConfigurationException` on the first call of
 * every process, so the redirect never took effect, and the comments here that called the default
 * "the cache dir" were wrong about where the live store is. Making the redirect succeed would have
 * pointed MapLibre at an empty directory while every saved region's tiles sat in
 * `files/mbgl-offline.db`. Dropped, not fixed (dispatch 2026-09-28-106, F1).
 *
 * [initializeMapLibre] is the one function every entry point calls instead of
 * `MapLibre.getInstance()`; `ForagerApplication.onCreate` calls it at process start so nothing
 * reads or composes a map before MapLibre is initialised (F2). It also installs the map's HTTP
 * client, right after the SDK call succeeds ([MapLibreInitializer]'s doc comment).
 */
private const val TAG = "MapLibreStorage"

private val mapLibreInitializer = MapLibreInitializer(
    getInstance = { context -> MapLibre.getInstance(context) },
    installHttpClient = { installMapHttpClient() },
    info = { message -> Log.i(TAG, message) },
    error = { message, cause -> Log.e(TAG, message, cause) },
)

internal fun initializeMapLibre(context: Context) = mapLibreInitializer.initialize(context)
