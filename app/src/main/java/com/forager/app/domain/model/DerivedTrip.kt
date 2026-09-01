package com.forager.app.domain.model

import java.time.LocalDate

/**
 * One local day's worth of raw material for Cartography's trip report (Stage 2b, not built here) —
 * everything [com.forager.app.domain.GetDerivedTripUseCase] gathered for [date]. **Stores nothing of
 * its own** — the owner's own framing for this dispatch: "a trip is derived, not stored... the trip
 * exists only as the result of asking." No id, no persisted row anywhere; a second call for the same
 * [date] simply re-derives it, and a later find/track/waypoint/region logged for that date changes
 * what the next call returns without anything needing to be updated.
 *
 * Every list is exactly what its own day-scoped repository read returned, unfiltered and
 * uninterpreted — deciding what to keep, snapshot vs. reference, tags, and drafts are all Stage 2b's
 * job on top of this, not this dispatch's. [finds] in particular includes drafts as well as
 * committed entries — see [com.forager.app.domain.GetDerivedTripUseCase]'s own doc comment for why
 * no filtering happens here.
 */
data class DerivedTrip(
    val date: LocalDate,
    val finds: List<MushroomLogEntry>,
    val tracks: List<Track>,
    val waypoints: List<Waypoint>,
    val offlineRegions: List<com.forager.app.domain.OfflineRegionMetadata>,
)
