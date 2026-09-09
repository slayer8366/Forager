package com.forager.app.domain.model

/**
 * One stored point exactly as the database holds it — full millisecond timestamp, whichever
 * optional fields were reported — paired with the read-seam verdict
 * ([com.forager.app.domain.isNetworkProviderFix]) that decides whether an ordinary read
 * ([com.forager.app.domain.TrackRepository.getById]/`getAll`/`getForDay`) would keep or exclude it.
 *
 * GPX full-record export dispatch: the one place in this app a [TrackPoint] an ordinary read would
 * drop is still reachable — by [com.forager.app.domain.TrackRepository.getFullRecord] only, never
 * by a display consumer. [kept] is computed from the same predicate the read seam applies, so it
 * cannot drift from what a track's [Track.excludedPointCount] already reflects in aggregate.
 */
data class TrackPointRecord(
    val point: TrackPoint,
    val kept: Boolean,
)
