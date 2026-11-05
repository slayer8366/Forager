package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.mapdata.DropOffTileCopy
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * What the offline map repository asks of an area's drop-off map copy (dispatch 2026-11-04-09), as it asks of its routes
 * (`data/routedata/RegionRouteFolders`): run a pass, read what is held when the areas are listed, delete it with the
 * area, and clear what no area owns. An interface so the repository's decisions can be tested without the network or
 * the phone's storage.
 */
interface RegionDropOffMaps {
    /** One pass for [region], stored under [regionId]. Never throws but for cancellation: every outcome is a [DropOffMapPass]. */
    suspend fun fetchFor(regionId: Long, region: Region): DropOffMapPass

    /** What [regionId]'s copy holds now. */
    suspend fun status(regionId: Long): RegionDropOffMap

    /** Deletes [regionId]'s copy. Throws an [IOException] when it could not be moved out of use; it is then still whole. */
    suspend fun delete(regionId: Long)

    /** Removes every area's copy that is not in [keepIds] and is not being fetched. Logs; never throws. */
    suspend fun cleanOrphans(keepIds: Set<Long>)
}

/** A build or a test that keeps no copy: nothing is held, nothing is fetched, and saying so is the pass's answer. */
object NoRegionDropOffMaps : RegionDropOffMaps {
    override suspend fun fetchFor(regionId: Long, region: Region): DropOffMapPass =
        DropOffMapPass.NotRun("this repository keeps no drop-off map copy")

    override suspend fun status(regionId: Long): RegionDropOffMap = RegionDropOffMap.NONE

    override suspend fun delete(regionId: Long) = Unit

    override suspend fun cleanOrphans(keepIds: Set<Long>) = Unit
}

/** [RegionDropOffMaps] on the phone's own storage, fetched from the basemap's own tile address. */
class LocalRegionDropOffMaps internal constructor(
    private val store: DropOffMapStore,
    private val fetch: DropOffMapFetch,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RegionDropOffMaps {

    /** The copy as the way-to reads it (`LayeredOfflineBasemap`). */
    val tiles: DropOffTileCopy get() = store

    /** The same rectangle MapLibre downloaded and the routes are cut to (`routeAreaFor`), so the copy covers one area. */
    override suspend fun fetchFor(regionId: Long, region: Region): DropOffMapPass = fetch.pass(regionId, routeAreaFor(region))

    override suspend fun status(regionId: Long): RegionDropOffMap = withContext(io) { store.status(regionId) }

    override suspend fun delete(regionId: Long) = withContext(io) { store.delete(regionId) }

    override suspend fun cleanOrphans(keepIds: Set<Long>) = withContext(io) { store.cleanOrphans(keepIds) }

    companion object {
        /**
         * The phone's one instance: folders under the app's own files directory, `offline_basemap/<area id>`, beside
         * `offline_routes`, which Android's backup and device transfer already exclude whole
         * (`res/xml/data_extraction_rules.xml`). Not the cache directory: Android may clear that by itself, and in the
         * field there may be no signal to fetch it back (the reason `MapLibreStorage.kt` gives for MapLibre's store).
         */
        fun create(filesDir: File, http: OkHttpClient, mapLibre: OfflineBasemap, log: (String, Throwable?) -> Unit): LocalRegionDropOffMaps {
            val store = DropOffMapStore(File(filesDir, "offline_basemap"), log)
            return LocalRegionDropOffMaps(store, DropOffMapFetch(http, store, mapLibre, log))
        }
    }
}
