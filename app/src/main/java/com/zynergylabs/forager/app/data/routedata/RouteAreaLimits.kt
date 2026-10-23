package com.zynergylabs.forager.app.data.routedata

/**
 * This app's own operating limits on what the Worker's file list may ask the phone to store (CLAUDE.md: a reported
 * range describes what's possible, not what's safe). Over them, a region's routes are marked missing with
 * `OVER_PHONE_LIMIT`, never stored in part.
 */
internal object RouteAreaLimits {
    /**
     * Files in one area. BRouter's squares are five degrees on a side and the app's largest area is a 48 km box
     * (24 km radius, `OfflineMapRepository.MAX_RADIUS_KM`), so an area touches at most two squares each way: four.
     */
    const val MAX_FILES = 4

    /**
     * Bytes in one area, all files together: 64 MB. PR #381's verify-first report puts a 24 km cut at 0.5 to 0.85 MB,
     * and its upper bound at 25 times the Seattle square's cell density at about 20 MB; this is three times that bound.
     * Stated, not measured: dense-core cell sizes are unmeasured there too.
     */
    const val MAX_AREA_BYTES = 64L * 1024 * 1024

    /** The file list, or an error body, read at most this far: either is a few hundred bytes. */
    const val MAX_LIST_BYTES = 64L * 1024
}
