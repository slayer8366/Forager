package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The ground's height at a point, from data on the phone (dispatch 2026-11-03-07, RECORD -2025, -2026): what the waypoint
 * bubble's "Elevation" line shows. The interface Forager owns for it (CLAUDE.md, Architecture), so its source can be
 * swapped (RECORD -2001, -2002). The app's one is [com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource],
 * on the Trail Map's relief tiles stored with downloaded regions (the owner: "Map relief tiles (Recommended)").
 *
 * Never on the caller's thread for its disk reads, never over the network, and never throws: a height it cannot give is
 * [GroundHeight.Unknown], with why.
 */
fun interface GroundHeightSource {
    suspend fun groundHeightAt(point: LatLng): GroundHeight
}

/** What [GroundHeightSource.groundHeightAt] answers. */
sealed interface GroundHeight {
    /** [metres] above sea level, as the height data gives it. */
    data class Known(val metres: Double) : GroundHeight

    /** No height for this point; [reason] says which way. Shown as no line at all (dispatch A2). */
    data class Unknown(val reason: GroundHeightUnknown) : GroundHeight
}

enum class GroundHeightUnknown {
    /** Nothing provides heights here (a screen composed without the app's source: tests, previews). */
    NO_SOURCE,

    /** No downloaded region holds height data for this point: outside every download, or downloaded before it had any. */
    NOT_DOWNLOADED,

    /** A region stored this point's height tile with no content (the relief answered 404 when it was downloaded). */
    NO_DATA,

    /** The stored data is in a form this build cannot read (the map library's storage changed). Logged once. */
    UNSUPPORTED,

    /** The read failed. Logged. */
    READ_FAILED,
}

/** The [GroundHeightSource] for a screen composed without one: every point is [GroundHeightUnknown.NO_SOURCE]. */
object NoGroundHeightSource : GroundHeightSource {
    override suspend fun groundHeightAt(point: LatLng): GroundHeight = GroundHeight.Unknown(GroundHeightUnknown.NO_SOURCE)
}
