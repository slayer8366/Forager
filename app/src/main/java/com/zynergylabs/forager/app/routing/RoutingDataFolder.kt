package com.zynergylabs.forager.app.routing

import btools.codec.DataBuffers
import btools.mapaccess.PhysicalFile
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import java.io.File
import java.io.IOException

/**
 * Checks a folder of BRouter `.rd5` files before the engine runs, for the failures BRouter v1.7.10 reports badly
 * or not at all (dispatch 2026-10-23-02):
 *
 * - **A file cut short below its 200-byte header reads as success with an empty route.** `PhysicalFile`'s
 *   `readFully` throws an `EOFException` with no message, `NodesCache.getSegmentFor` rethrows it as a
 *   `RuntimeException(null)`, and `RoutingEngine` stores that null as its error message, which is how a caller
 *   tells success. Seen on the fixture cut to 150 bytes. Opening every header here catches it.
 * - **A missing folder** is reported as "segment directory … does not exist", and **an end in a square with no
 *   file** as "datafile … not found": both are file-system facts this can check without the engine's wording.
 *
 * Returns the failure, or null when the engine may run.
 */
internal object RoutingDataFolder {

    fun check(folder: File, start: LatLng, end: LatLng): TrailRouteFailure? {
        if (!folder.isDirectory) return TrailRouteFailure.DataMissing("no routing data folder at ${folder.name}")
        val files = folder.listFiles { f -> f.isFile && f.name.endsWith(RD5_SUFFIX) }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return TrailRouteFailure.DataMissing("no $RD5_SUFFIX file in ${folder.name}")

        // A square with no file holds no data for this area, so an end in it lies outside the stored data. Marked as a
        // square with no file, which the way to a waypoint reads as "No trail routes here" (RECORD -1667).
        val names = files.map { it.name }.toSet()
        if (squareFileName(start) !in names) return TrailRouteFailure.EndOutsideData(RouteEnd.START, squareHasNoFile = true)
        if (squareFileName(end) !in names) return TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true)

        for (file in files) {
            headerProblem(file)?.let { return TrailRouteFailure.DataCorrupt("${file.name}: $it") }
        }
        return null
    }

    /**
     * Opens [file]'s header and index as BRouter does (`PhysicalFile`'s constructor: 200 bytes, then the footer
     * and its checksum), without the lookup-version check, which the engine makes itself. Returns what is wrong,
     * or null.
     */
    private fun headerProblem(file: File): String? = try {
        PhysicalFile(file, DataBuffers(), -1, -1).close()
        null
    } catch (e: IOException) {
        e.message ?: e.javaClass.simpleName
    }

    /**
     * The `.rd5` file BRouter reads for [point]: named for the five-degree square it lies in, by the arithmetic of
     * `NodesCache.fileForSegment` at v1.7.10 on the integer position `RoutingParamCollector.readPosition` makes.
     */
    fun squareFileName(point: LatLng): String {
        val lonDegree = microdegrees(point.lng, 180.0) / 1_000_000
        val latDegree = microdegrees(point.lat, 90.0) / 1_000_000
        val lon = lonDegree - 180 - lonDegree % 5
        val lat = latDegree - 90 - latDegree % 5
        val slon = if (lon < 0) "W${-lon}" else "E$lon"
        val slat = if (lat < 0) "S${-lat}" else "N$lat"
        return "${slon}_$slat$RD5_SUFFIX"
    }

    private fun microdegrees(degrees: Double, offset: Double): Int = ((degrees + offset) * 1_000_000.0 + 0.5).toInt()

    private const val RD5_SUFFIX = ".rd5"
}
