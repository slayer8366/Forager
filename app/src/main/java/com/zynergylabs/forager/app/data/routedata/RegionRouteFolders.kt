package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the offline map repository asks of a region's trail route data (dispatch 2026-10-23-03): fetch it when the
 * region downloads, read it when the regions are listed, delete it with the region, and clear what no region owns.
 * An interface so the repository's decisions can be tested without the network or the phone's storage.
 */
interface RegionRouteFolders {
    /** Fetches and stores [region]'s routes under [regionId]. Never throws but for cancellation: every failure is a state. */
    suspend fun fetchFor(regionId: Long, region: Region): RegionRouteData

    /** [regionId]'s routes as they are on the phone now. */
    suspend fun status(regionId: Long): RegionRouteData

    /** Deletes [regionId]'s routes. Throws an [IOException] when they could not be moved out of use; they are then still whole. */
    suspend fun delete(regionId: Long)

    /** Removes every route folder whose region is not in [keepIds] and is not being fetched. Logs; never throws. */
    suspend fun cleanOrphans(keepIds: Set<Long>)
}

/** [RegionRouteFolders] on the phone's own storage, fetched from the Worker with [client]. */
class LocalRegionRouteFolders internal constructor(
    private val store: RegionRouteStore,
    private val client: RouteAreaClient,
    private val appKey: String,
    private val log: (String, Throwable?) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RegionRouteFolders {

    override suspend fun fetchFor(regionId: Long, region: Region): RegionRouteData = withContext(io) {
        // One writer per region folder (Sorrel's review of #383, M1): a second fetch for a region already being
        // fetched answers Fetching and touches nothing, the running fetch's `.partial` included.
        val partial = try {
            store.beginFetch(regionId)
        } catch (e: IOException) {
            store.endFetch(regionId)
            log("Couldn't set up the route folder for region $regionId.", e)
            return@withContext RegionRouteData.Missing(RouteDataMissingReason.STORAGE)
        }
        if (partial == null) {
            log("Routes for region $regionId are already being fetched; not fetched again.", null)
            return@withContext RegionRouteData.Fetching
        }
        try {
            val record = fetchRouteArea(client, appKey, routeAreaFor(region), partial, regionId, log)
            store.commit(regionId, partial, record)
            store.status(regionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The phone could not write or move the folder. Nothing is recorded, so the next listing reads the
            // region as never fetched; this answer says why, for the download that is finishing now.
            log("Couldn't store the routes for region $regionId.", e)
            RegionRouteData.Missing(RouteDataMissingReason.STORAGE)
        } catch (e: Exception) {
            // Whatever it is, it must not undo the map download that already finished (dispatch: "a network or
            // other error leaves the map download intact").
            log("Fetching the routes for region $regionId failed unexpectedly.", e)
            RegionRouteData.Missing(RouteDataMissingReason.INTERNAL_ERROR)
        } finally {
            store.endFetch(regionId)
        }
    }

    override suspend fun status(regionId: Long): RegionRouteData = withContext(io) { store.status(regionId) }

    override suspend fun delete(regionId: Long) = withContext(io) { store.delete(regionId) }

    override suspend fun cleanOrphans(keepIds: Set<Long>) = withContext(io) { store.cleanOrphans(keepIds) }

    companion object {
        /**
         * The phone's one instance: folders under the app's own files directory, `offline_routes/<region id>`, which
         * Android's backup and device transfer already exclude whole (`res/xml/data_extraction_rules.xml`).
         */
        fun create(filesDir: File, http: okhttp3.OkHttpClient, appKey: String, log: (String, Throwable?) -> Unit): LocalRegionRouteFolders =
            LocalRegionRouteFolders(
                store = RegionRouteStore(File(filesDir, "offline_routes"), log),
                client = RouteAreaClient(http, RouteAreaClient.ROUTING_BASE_URL, appKey),
                appKey = appKey,
                log = log,
            )
    }
}
