package com.zynergylabs.forager.app.domain.model

/**
 * One stored point exactly as the database holds it — full millisecond timestamp, whichever
 * optional fields were reported — paired with the read-seam verdict
 * ([com.zynergylabs.forager.app.domain.isNetworkProviderFix]) that decides whether an ordinary read
 * ([com.zynergylabs.forager.app.domain.TrackRepository.getById]/`getAll`/`getForDay`) would keep or exclude it.
 *
 * GPX full-record export dispatch: the one place in this app a [TrackPoint] an ordinary read would
 * drop is still reachable — by [com.zynergylabs.forager.app.domain.TrackRepository.getFullRecord] only, never
 * by a display consumer. [kept] is computed from the same predicate the read seam applies, so it
 * cannot drift from what a track's [Track.excludedPointCount] already reflects in aggregate.
 */
data class TrackPointRecord(
    val point: TrackPoint,
    val kept: Boolean,
    /**
     * Which rule excluded this point — GPX rule-provenance dispatch (owner ruling, 2026-09-09).
     * `null` in two different situations, and they are not the same claim:
     *
     * - the point was **kept**, so no rule caught it and there is nothing to name; or
     * - the point was excluded by a producer that recorded no provenance — in practice a
     *   [com.zynergylabs.forager.app.domain.GpxCodec.decode] of a file this app wrote **before** this attribute
     *   existed, where which rule fired is genuinely unrecoverable.
     *
     * That second case is why this does not replace [kept] and [kept] is not derived from it: an
     * old file's excluded points are exactly the ones whose provenance cannot be reconstructed, and
     * collapsing the two fields would either fabricate a rule name for them or lose the verdict.
     * Every producer inside this app fills it in — see
     * [com.zynergylabs.forager.app.domain.NETWORK_FIX_EXCLUSION_RULES].
     */
    val excludedByRule: String? = null,
)
