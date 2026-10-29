package com.zynergylabs.forager.app.routing

import btools.codec.DataBuffers
import btools.expressions.BExpressionContextWay
import btools.expressions.BExpressionMetaData
import btools.mapaccess.GeometryDecoder
import btools.mapaccess.OsmFile
import btools.mapaccess.OsmLink
import btools.mapaccess.OsmNode
import btools.mapaccess.OsmNodesMap
import btools.mapaccess.PhysicalFile
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [RouteWays] read straight from BRouter v1.7.10's `.rd5` squares with its own decoder, every way the square holds
 * (way-to dispatch 2026-10-28, PR 3). The profile is neither edited nor used: the way's tags are read with
 * `lookups.dat` alone, and no access check filters the ways, so the closed ones the engine never matches a waypoint to
 * are here too.
 *
 * How, at v1.7.10, the way `router/AreaReader.getDirectData` reads a square: the square's [PhysicalFile], each
 * one-degree [OsmFile], and each data cell's micro-cache from `createMicroCache` with no way validator (so no way is
 * dropped). Each node's links carry the way's tag bitmap, decoded by [BExpressionContextWay.getKeyValueDescription],
 * and its shape points, decoded by [GeometryDecoder]. A cell is 1/32 degree (`1000000 / divisor` micro-degrees; the
 * squares here have divisor 32).
 *
 * It touches none of the engine's shared state (no `ProfileCache`, no `NodesCache`), so it needs no part in
 * [BRouterTrailRouter]'s one-route lock. It holds one square file open at a time.
 */
class BRouterRouteWays(
    /**
     * The folder holding the `lookups.dat` the squares were built with ([RoutingProfile]). Asked on each read, off the
     * caller's thread, so production can hand in `RoutingProfile.install`, which leaves files that are already there alone.
     */
    private val profileDir: () -> File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RouteWays {

    constructor(profileDir: File, dispatcher: CoroutineDispatcher = Dispatchers.IO) : this({ profileDir }, dispatcher)

    override suspend fun near(center: LatLng, radiusMeters: Double, routeData: File): Result<List<RouteWay>> = withContext(dispatcher) {
        try {
            Result.success(read(center, radiusMeters, routeData))
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            // RoutingProfile.install's check() when the profile could not be moved into place.
            Result.failure(e)
        }
    }

    private fun read(center: LatLng, radiusMeters: Double, routeData: File): List<RouteWay> {
        val meta = BExpressionMetaData()
        val wayContext = BExpressionContextWay(meta)
        meta.readMetaData(File(profileDir(), RoutingProfile.LOOKUPS_FILE))
        val perDegree = GeoDistance.metersPerDegree(center.lat)
        val dLat = micro(radiusMeters / perDegree.lat)
        val dLng = micro(radiusMeters / perDegree.lng)
        val lon0 = micro(center.lng + 180.0)
        val lat0 = micro(center.lat + 90.0)
        val lonCells = (lon0 - dLng) / CELL..(lon0 + dLng) / CELL
        val latCells = (lat0 - dLat) / CELL..(lat0 + dLat) / CELL
        val seen = HashSet<String>()
        val ways = mutableListOf<RouteWay>()
        val geometry = GeometryDecoder()
        val bySquare = lonCells.flatMap { lc -> latCells.map { ac -> lc to ac } }.groupBy { (lc, ac) -> squareFile(lc * CELL, ac * CELL) }
        for ((name, squareCells) in bySquare) {
            val file = File(routeData, name)
            if (!file.isFile) throw IOException("no route square $name in ${routeData.name}")
            val buffers = DataBuffers()
            val square = PhysicalFile(file, buffers, -1, -1)
            try {
                if (square.divisor != DIVISOR) throw IOException("$name has cells of 1/${square.divisor} degree, not 1/$DIVISOR")
                for ((lonCell, latCell) in squareCells) {
                    val degree = OsmFile(square, (lonCell * CELL / 1_000_000).toInt(), (latCell * CELL / 1_000_000).toInt(), buffers)
                    if (!degree.hasData()) continue
                    val cache = degree.createMicroCache(lonCell.toInt(), latCell.toInt(), buffers, null, null, true, null) ?: continue
                    val nodes = OsmNodesMap()
                    for (i in 0 until cache.size) {
                        val id = cache.getIdForIndex(i)
                        if (!cache.getAndClear(id)) continue
                        val node = OsmNode(id)
                        node.parseNodeBody(cache, nodes, wayContext)
                        var link: OsmLink? = node.firstlink
                        while (link != null) {
                            val bitmap = link.descriptionBitmap
                            if (bitmap != null) {
                                val target = link.getTarget(node)
                                val reverse = link.isReverse(node)
                                val points = buildList {
                                    add(positionOf(node.ilat, node.ilon))
                                    var t = link.geometry?.let { geometry.decodeGeometry(it, node, target, reverse) }
                                    while (t != null) {
                                        add(positionOf(t.ilat, t.ilon))
                                        t = t.next
                                    }
                                    add(positionOf(target.ilat, target.ilon))
                                }
                                // A link is listed from each end it is stored at: kept once, whichever end comes first.
                                val ends = listOf(node.ilon.toLong() shl 32 or node.ilat.toLong(), target.ilon.toLong() shl 32 or target.ilat.toLong()).sorted()
                                if (seen.add("${ends[0]}:${ends[1]}:${points.size}")) ways += RouteWay(points, tagsOf(wayContext.getKeyValueDescription(reverse, bitmap)))
                            }
                            link = link.getNext(node)
                        }
                    }
                }
            } finally {
                square.close()
            }
        }
        return ways
    }

    private companion object {
        const val DIVISOR = 32
        const val CELL = 1_000_000L / DIVISOR

        fun micro(degrees: Double): Long = (degrees * 1_000_000.0 + 0.5).toLong()

        /** The square file for a micro-degree position, as [RoutingDataFolder.squareFileName] names it. */
        fun squareFile(ilon: Long, ilat: Long): String =
            RoutingDataFolder.squareFileName(LatLng(lat = ilat / 1e6 - 90.0, lng = ilon / 1e6 - 180.0))

        fun positionOf(iLat: Int, iLon: Int) = LatLng(lat = (iLat - 90_000_000) / 1e6, lng = (iLon - 180_000_000) / 1e6)

        /** "highway=service service=driveway access=private" as a map. Lookup values hold no spaces. */
        fun tagsOf(description: String): Map<String, String> =
            description.split(' ').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    }
}
