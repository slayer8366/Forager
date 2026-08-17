package com.forager.app.map

import android.content.Context
import com.forager.app.data.repository.runCatchingCancellable
import com.forager.app.domain.GeoDistance
import com.forager.app.domain.OfflineMapInfo
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import java.io.File
import java.util.Properties
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox

/**
 * [OfflineMapRepository] backed by osmdroid's `CacheManager`, storing tiles under
 * `context.filesDir` rather than `context.cacheDir` — the one requirement CLAUDE.md would call
 * silent otherwise: [SightingsMap][com.forager.app.ui.map.SightingsMap]'s ordinary browsing cache
 * already lives under `cacheDir/osmdroid/tiles`, which the OS is free to clear under storage
 * pressure and which osmdroid itself may trim (see [PersistentTileWriter]'s doc comment for the
 * measured detail on how the two default writers resolve that path). A region the user explicitly
 * asked to keep offline has to survive both of those; `filesDir` does, `cacheDir` does not.
 *
 * ## Verified against `osmdroid-android-6.1.20`'s own sources, not assumed
 *
 * Every non-obvious call below was checked against the pinned artifact's decompiled/attached
 * sources rather than guessed from the (sparse) `CacheManager` Javadoc:
 *
 * - **`CacheManager(ITileSource, IFilesystemCache, minZoom, maxZoom)` needs no `MapView`.** Two of
 *   the four constructors do (`CacheManager(MapView)` and `CacheManager(MapView, IFilesystemCache)`
 *   both call `mapView.getTileProvider()`), but the `since 6.0` constructor used here takes the tile
 *   source directly and touches nothing Android-lifecycle-shaped. So the "off-screen `MapView`"
 *   fallback the originating task flagged as a possible necessity is not needed — the sources show
 *   it plainly isn't.
 * - **`downloadAreaAsyncNoUI(Context, BoundingBox, zoomMin, zoomMax, CacheManagerCallback)` runs on
 *   `AsyncTask`.** `CacheManager.execute` calls `pTask.execute()` with no executor argument, and
 *   `AsyncTask` requires that call (and the task's construction) happen on the main thread. Hence
 *   [withContext] `Dispatchers.Main` around the call below — a suspend function has no other way to
 *   promise that to a caller running on `Dispatchers.IO`.
 * - **The progress callback shape** (`setPossibleTilesInArea(total)` once, `updateProgress(count,
 *   zoom, zoomMin, zoomMax)` repeatedly, then exactly one of `onTaskComplete()` /
 *   `onTaskFailed(errors)`) came from `CacheManagerTask`'s `onPreExecute`/`onProgressUpdate`/
 *   `onPostExecute`, not the interface's own Javadoc, which says nothing about call order or which
 *   callback is terminal.
 * - **osmdroid's own bulk-download gate does not, on its own, block OpenTopoMap or Mapnik.**
 *   `getDownloadingAction(ctx).preCheck()` throws `TileSourcePolicyException` only when
 *   `TileSourcePolicy.acceptsBulkDownload()` is false. In the pinned artifact that is true for
 *   `TileSourceFactory.MAPNIK` (explicit `FLAG_NO_BULK`, citing the OSM tile usage policy) but *not*
 *   for `TileSourceFactory.OpenTopo`, which carries no explicit policy at all and so defaults to
 *   accepting bulk downloads. See "Why USGS Topo only" below for what that means for this class.
 *
 * ## Why USGS Topo only, and why that's hardcoded rather than a parameter
 *
 * An earlier revision of this class accepted a style parameter (Topo or Imagery) resolved from
 * whichever mode the map's own quick-fire icon happened to be showing. The project owner's own
 * framing, after seeing it built: "since offline maps only loads USGS, then there's no need to
 * have it react to the toggle. have it assume USGS usage and have it ready to function." [download]
 * now always fetches [USGS_TOPO_SOURCE] — a parameter with exactly one value it's ever called with
 * is dead configurability, so it was removed rather than kept and always passed the same constant.
 *
 * That also makes this class the entire enforcement of decision #7 (offline downloading is
 * USGS-only): it doesn't accept a tile source of any kind, so there is nothing to gate one level up
 * either — Settings' "Offline Maps" section is unconditionally reachable regardless of which
 * `MapService` is selected for live browsing, since that selection was never wired to this class at
 * all. *Why* USGS-only in the first place, though, still needs the citation trail: both
 * OpenStreetMap's and OpenTopoMap's tile-usage policies prohibit bulk/prefetch downloading against
 * their servers. osmdroid's own pinned artifact encodes the OpenStreetMap half of that directly —
 * `TileSourceFactory.MAPNIK`'s `TileSourcePolicy` sets `FLAG_NO_BULK`, citing
 * `https://operations.osmfoundation.org/policies/tiles/` — but, per the bullet above, carries no
 * such flag for `TileSourceFactory.OpenTopo` at all. The OpenTopoMap half of the claim is therefore
 * one step removed: a web search of `opentopomap.org`'s and the OSM Foundation's own policy text,
 * gathered because this environment's egress proxy blocked both domains on a direct fetch. Worth a
 * primary-source spot-check in an environment that can reach them before relying on it further.
 *
 * ## Progress modulo
 *
 * A custom [CacheManager.CacheManagerCallback] rather than accepting `CacheManagerTask`'s default
 * "every 10 tiles" cadence indirectly: since [onProgress] is called straight through from
 * `updateProgress`, the granularity the caller sees is exactly whatever `CacheManagerTask` chooses —
 * fixed at every 10th tile by `getDownloadingAction`'s `getProgressModulo()`. Labeled here rather
 * than re-derived: a typical offline region (a handful of zoom levels around 15, tens to a few
 * hundred tiles for the radii this app's region picker allows) makes 10-tile steps a reasonably
 * fine-grained bar; a much larger region would see coarser updates. Adjustable, same treatment as
 * [ZOOM_LEVELS_BELOW_MAX] below.
 */
class OsmdroidOfflineMapRepository(context: Context) : OfflineMapRepository {

    private val appContext = context.applicationContext
    private val storageRoot = File(appContext.filesDir, "offline-maps")
    private val tilesDir = File(storageRoot, "tiles")
    private val statusFile = File(storageRoot, "status.properties")

    override suspend fun download(
        region: Region,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Result<OfflineMapInfo> = runCatchingCancellable {
        Configuration.getInstance().userAgentValue = appContext.packageName

        // A download replaces whatever was there before outright, so start from a clean directory
        // rather than layering a new region's tiles over a previous one's (or a previous *failed*
        // attempt's partial tiles — see the catch block below).
        deleteTilesDirContents()
        statusFile.delete()
        tilesDir.mkdirs()

        val box = region.toOsmdroidBoundingBox()
        val zoomMax = USGS_MAX_ZOOM
        val zoomMin = (USGS_MAX_ZOOM - ZOOM_LEVELS_BELOW_MAX).coerceAtLeast(0)

        val cacheManager = CacheManager(USGS_TOPO_SOURCE, PersistentTileWriter(tilesDir), zoomMin, zoomMax)

        try {
            runDownload(cacheManager, box, zoomMin, zoomMax, onProgress)
        } catch (e: Exception) {
            // Never leave a half-downloaded region looking like a complete one: no status file was
            // written yet (that happens only after this succeeds), but the partial tile files
            // themselves are real disk usage claiming to be part of "the" offline region. Cleaned up
            // so a failed attempt costs nothing and getStatus() has nothing ambiguous to read.
            deleteTilesDirContents()
            throw e
        }

        val tileCount = tilesDir.walkTopDown().count { it.isFile }
        val sizeBytes = tilesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val info = OfflineMapInfo(
            region = region,
            tileCount = tileCount,
            sizeBytes = sizeBytes,
            downloadedAtEpochMillis = downloadedAtEpochMillisProvider(),
        )
        storageRoot.mkdirs()
        statusFile.outputStream().use { out -> info.toProperties().store(out, null) }
        info
    }

    override suspend fun delete(): Result<Unit> = runCatchingCancellable {
        deleteTilesDirContents()
        statusFile.delete()
        Unit
    }

    override suspend fun getStatus(): Result<OfflineMapInfo?> = runCatchingCancellable {
        if (!statusFile.exists()) return@runCatchingCancellable null
        val properties = Properties()
        statusFile.inputStream().use { input -> properties.load(input) }
        properties.toOfflineMapInfo()
    }

    private fun deleteTilesDirContents() {
        if (tilesDir.exists()) tilesDir.deleteRecursively()
    }

    /**
     * Bridges `CacheManagerTask`'s `AsyncTask` callbacks into a suspend call. `Dispatchers.Main`
     * because `AsyncTask.execute()` demands the main thread for both construction and execution —
     * see this class's doc comment for the source citation.
     */
    private suspend fun runDownload(
        cacheManager: CacheManager,
        box: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Unit = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var total = 0
            val callback = object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMinLevel: Int, zoomMaxLevel: Int) {
                    onProgress(progress, total)
                }

                override fun downloadStarted() = Unit

                override fun setPossibleTilesInArea(possibleTiles: Int) {
                    total = possibleTiles
                    onProgress(0, possibleTiles)
                }

                override fun onTaskFailed(errors: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            java.io.IOException("$errors of $total tiles failed to download."),
                        )
                    }
                }
            }
            val task = cacheManager.downloadAreaAsyncNoUI(appContext, box, zoomMin, zoomMax, callback)
            continuation.invokeOnCancellation { task.cancel(true) }
        }
    }

    /**
     * The highest zoom level USGS Topo offers — [com.forager.app.ui.map.Basemap.USGS_TOPO] declares
     * 15, verified against the same pinned artifact in `BasemapTileSourceTest`. Duplicated as a
     * literal rather than imported: this `map/` package deliberately doesn't depend on `ui/map/`
     * (see [USGS_TOPO_SOURCE]'s doc comment), so the two are kept in sync by both being pinned
     * against the same artifact rather than by a shared reference.
     */
    private val USGS_MAX_ZOOM = 15

    /**
     * How many levels below [USGS_MAX_ZOOM] a download reaches down to — an adjustable assumption,
     * the same kind [com.forager.app.ui.map.SightingsMap]'s `zoomForRadiusKm` already is: a wider
     * span means more usable offline zoom range at the cost of more tiles (roughly ×4 tiles per
     * additional level), and this project has no usage data yet on what span foraging trips actually
     * need. 3 keeps a typical region's download in the tens-to-low-hundreds of tiles.
     */
    private val ZOOM_LEVELS_BELOW_MAX = 3

    /** `System.currentTimeMillis()` behind a seam only so a test could fake it; nothing here does yet. */
    private fun downloadedAtEpochMillisProvider(): Long = System.currentTimeMillis()
}

private fun Region.toOsmdroidBoundingBox(): BoundingBox {
    val box = GeoDistance.boundingBox(LatLng(lat, lng), radiusKm)
    return BoundingBox(box.north, box.east, box.south, box.west)
}

/**
 * The same osmdroid source `com.forager.app.ui.map.BasemapTileSources.tileSourceFor` maps
 * `Basemap.USGS_TOPO` to, duplicated as a direct reference rather than shared for the reason
 * [OsmdroidOfflineMapRepository.USGS_MAX_ZOOM]'s doc comment gives: this package does not depend
 * on `ui/map/`. `BasemapTileSourceTest` and this file both pin against the identical
 * `TileSourceFactory.USGS_TOPO` field, so a mismatch would show up as two tests disagreeing about
 * the same artifact rather than silently drifting apart.
 */
private val USGS_TOPO_SOURCE = TileSourceFactory.USGS_TOPO
