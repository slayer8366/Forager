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
 * is the owner's call and is not built yet: the dispatch's stop.
 */
sealed interface RegionRouteData {
    /** Bytes of route data on the phone for this region; counted into the region's shown size. */
    val sizeBytes: Long get() = 0L

    /**
     * Nothing recorded: a region downloaded before route data existed, or one whose route fetch never finished (the
     * app was closed during it). Never presented as "no routes here", which is a fact this state does not know.
     */
    data object NotFetched : RegionRouteData

    /** Every file the Worker named is in [folder], each checked against its sha256 when it was stored. */
    data class Stored(
        /** The route-data build the files were cut from (`run_id` in the Worker's answer). */
        val runId: String,
        /** The OpenStreetMap data's own timestamp, as the Worker gave it. */
        val osmTimestamp: String,
        override val sizeBytes: Long,
        /** A folder of BRouter `.rd5` files under BRouter's own names, what [com.zynergylabs.forager.app.domain.routing.TrailRouter.route] takes. */
        val folder: File,
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
 * The rectangle a region's routes are cut to: the same one its tiles are downloaded for (`MapLibreOfflineMapRepository`
 * builds its download bounds from this same [GeoDistance.boundingBox]), so map and routes cover one area.
 */
fun routeAreaFor(region: Region): GeoBoundingBox = GeoDistance.boundingBox(LatLng(region.lat, region.lng), region.radiusKm)

/** Whether [point] is inside this rectangle, edges included; a box whose [GeoBoundingBox.east] is west of its [GeoBoundingBox.west] crosses the antimeridian. */
fun GeoBoundingBox.contains(point: LatLng): Boolean {
    if (point.lat < south || point.lat > north) return false
    return if (west <= east) point.lng in west..east else point.lng >= west || point.lng <= east
}
