package com.forager.app.map

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.maplibre.android.storage.FileSource

/**
 * MapLibre's `FileSource` defaults its offline-region and tile-resource database into the app's
 * *cache* directory — confirmed via `javap` against the pinned `org.maplibre.gl:android-sdk:13.5.0`
 * artifact (the API is literally named `getResourcesCachePath`/`setResourcesCachePath`), and
 * confirmed on hardware: a device with three downloaded offline regions showed them counted under
 * Android Settings' "Cache" bucket, not "Data". That default silently undoes the design doc's own
 * "no automatic deletion" rule for offline regions (`docs/plans/journal-trips-and-offline-regions.md`,
 * "Freshness") two different ways — Android itself can clear app cache under storage pressure with no
 * warning, and this app's own in-app "Clear cache" control (Settings → Storage) would wipe every
 * downloaded region along with it.
 *
 * Redirected to a subdirectory of [Context.getFilesDir] instead — not cleared by the OS under storage
 * pressure, and untouched by "Clear cache".
 *
 * Called from both real `MapLibre.getInstance()` call sites in this app
 * ([MapLibreOfflineMapRepository] and `com.forager.app.ui.map.MapLibreBasemapPreviewActivity`),
 * *before* that call, rather than once centrally in `Application.onCreate()`: the native `FileSource`
 * locks onto whatever cache path is active the first time anything touches it, so whichever of those
 * two entry points a user reaches first has to win the race, not just one of them. [AtomicBoolean]
 * makes the actual redirect run exactly once regardless of which call site gets there first.
 *
 * (An `Application.onCreate()`-level version of this was tried first and reverted — Robolectric boots
 * the real [com.forager.app.ForagerApplication] for every unit test, and no test exercises either real
 * MapLibre call site, so an unconditional call there tried to initialize MapLibre's native library
 * during ordinary ViewModel/Compose tests that have nothing to do with maps, and every one of them
 * failed with `MapLibreConfigurationException`.)
 *
 * This only changes where *new* writes go — it does not migrate any region already downloaded under
 * the old cache path on a device from before this change. Not a concern yet: no build predating this
 * fix has shipped to a real user.
 */
internal fun ensureMapLibreStorageOutsideCache(context: Context) {
    if (!hasRedirected.compareAndSet(false, true)) return

    val appContext = context.applicationContext
    val offlineStorageDir = File(appContext.filesDir, "maplibre-offline").apply { mkdirs() }
    FileSource.setResourcesCachePath(
        appContext,
        offlineStorageDir.absolutePath,
        object : FileSource.ResourcesCachePathChangeCallback {
            override fun onSuccess(path: String) = Unit

            // Not fatal: FileSource simply keeps using whatever path it already had (the cache-dir
            // default, or a previous run's redirected path). Logged rather than silently swallowed,
            // per CLAUDE.md, since this is exactly the failure this function exists to prevent.
            override fun onError(message: String) {
                Log.w("MapLibreStorage", "Couldn't redirect MapLibre's resource storage out of the cache dir: $message")
            }
        },
    )
}

private val hasRedirected = AtomicBoolean(false)
