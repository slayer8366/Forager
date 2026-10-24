package com.zynergylabs.forager.app.domain.routedata

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import java.io.File

/**
 * What an offline region holds of its trail route data (dispatch 2026-10-23-03, trail navigation step 3): the
 * routing files the Worker cuts to the region's rectangle, which BRouter (`domain/routing/TrailRouter`) routes on.
 * The ruling it implements is RECORD -1498 q1, "Cut to the area": a region's download stores map and routes together.
 *
 * Read from the region's own route folder on every listing, never from a second record that could drift from the
 * files (the same rule the tile counts follow, `OfflineMapRepository.listRegions`). How each state shows to the user
 * is `ui/availability/OfflineRegionRouteLine.kt`, the owner's step path.
 */
sealed interface RegionRouteData {
    /** Bytes of route data on the phone for this region; counted into the region's shown size. */
    val sizeBytes: Long get() = 0L

    /**
     * Nothing recorded: a region downloaded before route data existed, or one whose route fetch never finished (the
     * app was closed during it). Never presented as "no routes here", which is a fact this state does not know.
     */
    data object NotFetched : RegionRouteData

    /**
     * A fetch for this region is running now in this process: the download's own, the automatic one, or Try again.
     * Nothing else may fetch it until that one ends (Sorrel's review of #383, M1: one writer per region folder).
     */
    data object Fetching : RegionRouteData

    /**
     * Every file the Worker named is in [folder], each checked against its sha256 when it was stored. [missingSquares]
     * names the five-degree squares the area needs that the Worker's build does not have (the owner's ruling on
     * partial coverage, relayed by the planner for PR #381): the stored part is kept, and a point in a missing square
     * has no routes here.
     */
    data class Stored(
        /** The route-data build the files were cut from (`run_id` in the Worker's answer). */
        val runId: String,
        /** The OpenStreetMap data's own timestamp, as the Worker gave it. */
        val osmTimestamp: String,
        override val sizeBytes: Long,
        /** A folder of BRouter `.rd5` files under BRouter's own names, what [com.zynergylabs.forager.app.domain.routing.TrailRouter.route] takes. */
        val folder: File,
        /** BRouter square names without `.rd5` (`W130_N45`), as the Worker's `missing` list gives them; empty when whole. */
        val missingSquares: List<String> = emptyList(),
    ) : RegionRouteData

    /** The Worker answered 404 `outside_coverage`: the route data has no square for this area. A fact, not a failure. */
    data object NoneHere : RegionRouteData

    /** The fetch failed; the map download is intact. [reason] says which way, for the log and for the step path. */
    data class Missing(val reason: RouteDataMissingReason) : RegionRouteData

    /**
     * Something was stored and is no longer whole: the record can't be read, or a file it lists is gone or a
     * different size. Never routed on. [detail] is for the log.
     */
    data class Damaged(val detail: String) : RegionRouteData
}

/**
 * Why a region's routes are missing. Each is a separate kind so the log names it and the step path can treat them
 * differently (whether a retry could help, for one). [code] is what the region's route record stores.
 */
enum class RouteDataMissingReason(val code: String) {
    /** This build carries no app key (`BuildConfig.ROUTING_APP_KEY` empty), so no request was sent. */
    NO_APP_KEY("no_app_key"),

    /** The request did not complete: no connection, a timeout, a dropped connection. */
    NETWORK("network"),

    /** 503 `manifest_missing`: the Worker has no route-data build to cut from. */
    MANIFEST_MISSING("manifest_missing"),

    /** 503 `run_missing_square`: the area is in coverage and the build lacks a square it needs. */
    RUN_MISSING_SQUARE("run_missing_square"),

    /** 503 `manifest_invalid`: the Worker's build list can't be read. */
    MANIFEST_INVALID("manifest_invalid"),

    /** 503 `not_configured`: the Worker is deployed without its key or rate limits, and refuses every request. */
    NOT_CONFIGURED("not_configured"),

    /** 502 `unsupported_square`: the Worker cannot cut a square in the form it has it. */
    UNSUPPORTED_SQUARE("unsupported_square"),

    /** 409 `run_superseded`, twice: the build changed between the list and its files, and again on the one retry. */
    RUN_SUPERSEDED("run_superseded"),

    /** 404 `not_found`: a file url the Worker does not know. */
    NOT_FOUND("not_found"),

    /** 502 `checksum`: the Worker found its own copy of a square damaged. */
    SERVER_CHECKSUM("checksum"),

    /** The bytes received do not match the sha256 or the size the Worker's list gave for them. */
    CHECKSUM_MISMATCH("checksum_mismatch"),

    /** 413 `too_large`: the area is over the Worker's limit (`MAX_ROUTE_AREA_SIDE_KM`). */
    TOO_LARGE("too_large"),

    /** The Worker's list asks for more than this app's own operating limit allows (`RouteAreaLimits`). */
    OVER_PHONE_LIMIT("over_phone_limit"),

    /** 400 `bad_request`: the Worker refused the rectangle. */
    BAD_REQUEST("bad_request"),

    /** 403 `forbidden`: the app key was refused. */
    FORBIDDEN("forbidden"),

    /** 429 `rate_limited`. The Retry-After is logged; nothing retries on its own. */
    RATE_LIMITED("rate_limited"),

    /** A 200 whose body is not the agreed shape: bad JSON, a bad file name, a URL on another host. */
    MALFORMED_RESPONSE("malformed_response"),

    /** A status or error code outside the agreed list, including a 404 that is not `outside_coverage`. */
    UNEXPECTED_RESPONSE("unexpected_response"),

    /** The phone could not write the files or their record. */
    STORAGE("storage"),

    /** The fetch failed in a way this code does not recognise; the exception is logged. Never shown as one of the above. */
    INTERNAL_ERROR("internal_error"),
    ;

    companion object {
        fun fromCode(code: String): RouteDataMissingReason? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Whether asking again could change the answer. Not for a build with no key, a key the server refuses (an app update
 * is needed, [needsAppUpdate]), or an area the server or this app will never take (too large, refused as a bad
 * request): none of them offers a button (the owner's step path F, dispatch 2026-10-23-03).
 */
fun RouteDataMissingReason.retryHelps(): Boolean = this !in NOT_RETRYABLE

/**
 * Whether the routes are missing because of this app, not the area: the server refused its key ([FORBIDDEN][RouteDataMissingReason.FORBIDDEN],
 * a wrong key in a build or a key rotated on the Worker after the build shipped), or the build has none
 * ([NO_APP_KEY][RouteDataMissingReason.NO_APP_KEY], a developer build without the key; a release build cannot be made
 * without one, `verifyReleaseHasRoutingAppKey`). Either way only a different build fixes it, so the row says "Map
 * only. Trail routes need an app update." (dispatch 2026-10-23-07, Elder's item 2, the owner's sentence) instead of
 * C's line, which would state something false about the area.
 */
fun RouteDataMissingReason.needsAppUpdate(): Boolean =
    this == RouteDataMissingReason.FORBIDDEN || this == RouteDataMissingReason.NO_APP_KEY

private val NOT_RETRYABLE = setOf(
    RouteDataMissingReason.NO_APP_KEY,
    RouteDataMissingReason.FORBIDDEN,
    RouteDataMissingReason.TOO_LARGE,
    RouteDataMissingReason.OVER_PHONE_LIMIT,
    RouteDataMissingReason.BAD_REQUEST,
)

/**
 * Whether a region's routes should be fetched (again): never fetched, damaged, or missing for a reason a retry could
 * help. What the automatic fetch on opening Offline maps picks up (the owner: "Automatic, button as backup").
 */
fun RegionRouteData.needsFetch(): Boolean = when (this) {
    RegionRouteData.NotFetched, is RegionRouteData.Damaged -> true
    is RegionRouteData.Missing -> reason.retryHelps()
    is RegionRouteData.Stored, RegionRouteData.NoneHere, RegionRouteData.Fetching -> false
}

/**
 * The rectangle a region's routes are cut to: the same one its tiles are downloaded for (`MapLibreOfflineMapRepository`
 * builds its download bounds from this same [GeoDistance.boundingBox]), so map and routes cover one area.
 */
fun routeAreaFor(region: Region): GeoBoundingBox = GeoDistance.boundingBox(LatLng(region.lat, region.lng), region.radiusKm)

/**
 * The five-degree square [point] lies in, named as BRouter and the Worker name it without `.rd5` (`W125_N45`): the
 * square's south-west corner, floored to a multiple of five (the Worker's `squareName`, PR #381 `routing/area.ts`).
 */
fun routeSquareName(point: LatLng): String {
    val lon = Math.floorDiv(Math.floor(point.lng).toInt(), 5) * 5
    val lat = Math.floorDiv(Math.floor(point.lat).toInt(), 5) * 5
    return "${if (lon < 0) "W${-lon}" else "E$lon"}_${if (lat < 0) "S${-lat}" else "N$lat"}"
}

/** Whether [point] is inside this rectangle, edges included; a box whose [GeoBoundingBox.east] is west of its [GeoBoundingBox.west] crosses the antimeridian. */
fun GeoBoundingBox.contains(point: LatLng): Boolean {
    if (point.lat < south || point.lat > north) return false
    return if (west <= east) point.lng in west..east else point.lng >= west || point.lng <= east
}
