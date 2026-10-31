package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import com.zynergylabs.forager.app.domain.routing.TrailSnapper
import java.io.File
import kotlin.math.hypot

/** Synthetic ground for the way-home tests: metres east and north of [BASE], in W125_N45 (dispatch 2026-10-23-09 Amendment 1). */
internal object Ground {
    val BASE = LatLng(45.02, -120.01)
    private val perDegree = GeoDistance.metersPerDegree(BASE.lat)

    fun at(east: Double, north: Double) = LatLng(BASE.lat + north / perDegree.lat, BASE.lng + east / perDegree.lng)

    fun east(p: LatLng) = (p.lng - BASE.lng) * perDegree.lng
    fun north(p: LatLng) = (p.lat - BASE.lat) * perDegree.lat

    fun track(vararg points: LatLng) = Track(
        id = "t",
        name = null,
        startedAtEpochMillis = 0L,
        endedAtEpochMillis = null,
        points = points.mapIndexed { i, p -> TrackPoint(p.lat, p.lng, null, 5f, i * 1_000L) },
    )

    val FOLDER = File("/routes/1")

    fun region(
        id: Long = 1,
        routeData: RegionRouteData = RegionRouteData.Stored("run-1", "t", 10L, FOLDER),
        centre: LatLng = BASE,
        radiusKm: Int = 10,
        createdAt: Long = 1_000L,
    ) = OfflineRegionSummary(id, "R$id", Region(centre.lat, centre.lng, radiusKm), 10.0, 15.0, 100, 1_000L, createdAt, routeData = routeData)
}

internal class FakeRegions(private val regions: Result<List<OfflineRegionSummary>>) : OfflineMapRepository {
    constructor(vararg regions: OfflineRegionSummary) : this(Result.success(regions.toList()))

    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
    override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = regions
}

/**
 * Trails as straight lines on [Ground]; a point's nearest trail point is its foot on the nearest one, as an engine's
 * matching does, within 250 m (BRouter's catching range with dynamic range off). [failWith] makes every call fail.
 */
internal class FakeSnapper(private val trails: List<Pair<LatLng, LatLng>>, var failWith: TrailRouteFailure? = null) : TrailSnapper {
    val asked = mutableListOf<LatLng>()

    override suspend fun nearestTrail(point: LatLng, toward: LatLng, routeData: File): TrailSnapResult {
        asked += point
        failWith?.let { return TrailSnapResult.Failed(it) }
        val nearest = trails.map { (a, b) -> foot(point, a, b) }.minByOrNull { GeoDistance.metersBetween(point, it) }
        if (nearest == null || GeoDistance.metersBetween(point, nearest) > 250.0) {
            return TrailSnapResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.START))
        }
        return TrailSnapResult.OnNetwork(nearest)
    }

    private fun foot(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val (ax, ay) = Ground.east(a) to Ground.north(a)
        val (bx, by) = Ground.east(b) to Ground.north(b)
        val (px, py) = Ground.east(p) to Ground.north(p)
        val dx = bx - ax
        val dy = by - ay
        val t = if (hypot(dx, dy) == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return Ground.at(ax + t * dx, ay + t * dy)
    }
}

/** A router that answers [result] and remembers what it was asked. */
internal class FakeRouter(var result: TrailRouteResult) : TrailRouter {
    val asked = mutableListOf<Pair<LatLng, LatLng>>()

    override suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult {
        asked += start to end
        return result
    }

    companion object {
        /** A route along [points], with the engine's length given as [lengthMetres]. */
        fun found(lengthMetres: Int, vararg points: LatLng) = TrailRouteResult.Found(TrailRoute(points.toList(), lengthMetres))

        /** A route along [points] whose trail data gives [heightsMetres], `null` where it has none (dispatch 2026-10-30-06). */
        fun found(lengthMetres: Int, points: List<LatLng>, heightsMetres: List<Double?>) =
            TrailRouteResult.Found(TrailRoute(points, lengthMetres, heightsMetres))
    }
}
