package com.forager.app.map

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.maplibre.android.MapLibre
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
 * **Call [initializeMapLibre], not this function directly** — this only redirects the storage path;
 * [initializeMapLibre] is what actually guarantees the redirect precedes `MapLibre.getInstance()`.
 * [AtomicBoolean] makes the redirect itself run exactly once regardless of how many times this is
 * called.
 *
 * ## How much "locks on first touch" actually proves
 *
 * `FileSource.getInstance(Context)` is a lazy singleton whose private constructor calls a native
 * `initialize(...)` exactly once, with whatever path is current at that moment — a real, one-time
 * lock, confirmed by decompiling `FileSource.class` from the pinned artifact (`javap -c -p` against
 * the jar already sitting in this project's own Gradle cache, not guessed from documentation). But
 * `FileSource.setResourcesCachePath(...)` (what this function calls) is not only a setter for a
 * not-yet-built instance — it resolves the *existing* singleton and calls a separate, private native
 * `setResourceCachePath(String, callback)`, a real re-point method that exists specifically to move
 * an already-running `FileSource`. Whether that native re-point reliably succeeds while a `MapView`
 * is actively rendering is not something bytecode alone can settle. [initializeMapLibre] closes the
 * ordering gap regardless of how that resolves, rather than leaving this project's single most
 * expensive user asset dependent on it.
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

/**
 * `MapLibre.getInstance(context)`, always preceded by [ensureMapLibreStorageOutsideCache] — the one
 * function every real entry point in this app calls instead of `MapLibre.getInstance()` directly.
 *
 * This exists because one entry point didn't call the redirect first: `com.forager.app.ui.map.SightingsMap`'s
 * own `MapLibre.getInstance(context)` call used to reach the native library directly, with nothing
 * before it establishing where the resource database lives. `com.forager.app.map.MapLibreOfflineMapRepository`'s
 * own `offlineManager()` got the order right by calling both, separately, in the right sequence — but
 * that shape asks every call site to remember *two* functions and *their order*; [SightingsMap] is
 * proof one didn't. Folding both into this one function means a call site only has to get one call
 * right, and any future third call site reaches for this the same way the first two now do.
 *
 * **This file previously named a third real call site here, `MapLibreBasemapPreviewActivity`. That
 * was wrong, not merely stale** — an exhaustive search across `app/src/main` (filed as
 * `docs/qc/dispatches/reports/2026-08-28-raster-capture-path-report.md`) confirmed that class never
 * existed as a file, in git history, or in the manifest; it was never merged into this codebase at
 * all. There have only ever been the two real call sites this function now serves:
 * [com.forager.app.ui.map.SightingsMap] and [com.forager.app.map.MapLibreOfflineMapRepository].
 */
internal fun initializeMapLibre(context: Context) {
    ensureMapLibreStorageOutsideCache(context)
    MapLibre.getInstance(context)
}

private val hasRedirected = AtomicBoolean(false)
