package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure

/**
 * Names the failure behind a BRouter v1.7.10 error message (dispatch 2026-10-23-02). BRouter reports a failed
 * route only as `RoutingEngine.getErrorMessage()`, a string, so this reads the strings its source throws. Each
 * one below is quoted from that source at tag v1.7.10, and the router's tests produce the ones the fixture can
 * reach through the real engine, so an upgrade that rewords one fails a test rather than turning it into
 * [TrailRouteFailure.EngineFailed] unnoticed.
 *
 * Anything not recognised is [TrailRouteFailure.EngineFailed] with the message kept: never guessed into one of
 * the named kinds (CLAUDE.md: an unsupported case is reported as such, not given a plausible value).
 */
internal object BRouterFailures {

    fun classify(message: String): TrailRouteFailure = when {
        // RoutingEngine.matchWaypointsToNodes: no way within the catching range of an end, and with dynamic range
        // off no wider retry. The ends are named "from" and "to" by RoutingParamCollector.getWayPointList.
        message.startsWith("from-position not mapped") -> TrailRouteFailure.EndOutsideData(RouteEnd.START)
        message.startsWith("to-position not mapped") -> TrailRouteFailure.EndOutsideData(RouteEnd.END)

        // RoutingEngine: the island checks before routing, and the search itself running out of paths.
        message.startsWith("target island detected") ||
            message.startsWith("start island detected") ||
            message.startsWith("no track found") -> TrailRouteFailure.NoPath(message)

        // NodesCache.matchWaypointsToNodes and preloadPosition: the first file read for an end is missing.
        // RoutingDataFolder checks for this first, so it means a file went between that check and the engine.
        message.startsWith("datafile ") && message.endsWith(" not found") -> TrailRouteFailure.DataMissing(message)
        message.startsWith("segment directory ") && message.endsWith(" does not exist") ->
            TrailRouteFailure.DataMissing(message)

        // PhysicalFile (header and footer) and OsmFile (a tile's index and cells), rethrown by NodesCache.
        CORRUPT_PREFIXES.any { message.startsWith(it) } -> TrailRouteFailure.DataCorrupt(message)

        else -> TrailRouteFailure.EngineFailed(message)
    }

    private val CORRUPT_PREFIXES = listOf(
        "file of size ", // PhysicalFile: "file of size N too short, should be M"
        "top index checksum error", // PhysicalFile
        "lookup version mismatch", // PhysicalFile: built for another lookups.dat
        "sub index checksum error", // OsmFile
        "checkum error", // OsmFile, BRouter's own spelling
        "old, unsupported data-format", // OsmFile
        "error reading datafile ", // NodesCache.getSegmentFor, any other exception reading a file
    )
}
