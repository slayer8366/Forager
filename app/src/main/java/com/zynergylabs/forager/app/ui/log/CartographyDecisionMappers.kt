package com.zynergylabs.forager.app.ui.log

// Moved from CartographyViewModel.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision

internal fun MushroomLogEntry.toDecision(kept: Boolean) = FindDecision(
    findId = id,
    foundOn = foundOn,
    ownIdentification = ownIdentification,
    hasPhotos = photos.isNotEmpty(),
    kept = kept,
)

/**
 * Every track decision's snapshot recomputed from [tracks] as the read seam returns them today —
 * see [CartographyViewModel.onOpenEntry]. A decision whose track is not in [tracks] (deleted since)
 * is returned unchanged. Pure; the caller decides whether to persist.
 */
internal fun CartographyEntry.withRecomputedTrackSnapshots(tracks: List<Track>, computeTrackStatistics: ComputeTrackStatisticsUseCase): CartographyEntry {
    val byId = tracks.associateBy { it.id }
    val recomputed = trackDecisions.map { decision ->
        val track = byId[decision.trackId] ?: return@map decision
        val stats = computeTrackStatistics(track.points)
        decision.copy(distanceMeters = stats.distanceMeters, durationMillis = stats.durationMillis, pointCount = stats.totalPoints)
    }
    return if (recomputed == trackDecisions) this else copy(trackDecisions = recomputed)
}

internal fun Track.toDecision(kept: Boolean, computeTrackStatistics: ComputeTrackStatisticsUseCase): TrackDecision {
    val stats = computeTrackStatistics(points)
    return TrackDecision(
        trackId = id,
        name = name,
        distanceMeters = stats.distanceMeters,
        durationMillis = stats.durationMillis,
        pointCount = stats.totalPoints,
        kept = kept,
    )
}

internal fun Waypoint.toDecision(kept: Boolean) = WaypointDecision(waypointId = id, name = name, lat = lat, lng = lng, kept = kept)

internal fun OfflineRegionSummary.toDecision(kept: Boolean) = OfflineRegionDecision(
    offlineRegionId = id,
    name = name,
    lat = region.lat,
    lng = region.lng,
    radiusKm = region.radiusKm,
    kept = kept,
)

/**
 * This list with the element matching [matches] replaced by [change] where it stands, or, when none
 * matches, [create]'s element appended. `null` when nothing matches and [create] has nothing to add, so
 * the caller can say so.
 */
internal inline fun <T> List<T>.setOrAdd(matches: (T) -> Boolean, change: (T) -> T, create: () -> T?): List<T>? =
    if (any(matches)) map { if (matches(it)) change(it) else it } else create()?.let { this + it }
