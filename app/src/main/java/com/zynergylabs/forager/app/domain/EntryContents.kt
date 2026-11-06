package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DerivedTrip
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.time.LocalDate

/**
 * What a journal entry holds, arranged for the editor's "In this entry" panel (dispatch 2026-09-28-667,
 * data part A; the owner in RECORD -656: "Summary first, open to adjust (Recommended)". One panel with a
 * switch per group; tap a group to change single items; a track's Start, End and waypoints sit under the
 * track; items new since the last save are flagged at the top).
 *
 * Pure and free of Android: built from the entry's own decisions and, when they have loaded, the day's
 * live candidates. It changes nothing about what a left-out item does (the data scout's Withhold
 * section); it only arranges the same three states the editor always had, under the owner's words:
 * [Inclusion.INCLUDED] is a kept decision, [Inclusion.LEFT_OUT] a withheld one, [Inclusion.NEW] a live
 * candidate with no decision yet.
 *
 * **Where each item is shown, and which group switch reaches it.** The two are the same rule, so a
 * group's switch only ever changes items the user can find by opening that group (or in the new list
 * at the top):
 * - A decided track is listed under Tracks, with every decided waypoint whose live record names that
 *   track (`Waypoint.trackId`: its Start, its End, and waypoints dropped while it recorded) beneath it.
 * - Every other decided waypoint is listed under Waypoints. That includes a waypoint whose record was
 *   deleted (the decision snapshot does not carry its track) and every waypoint while the day's
 *   candidates have not loaded.
 * - A new item is listed at the top only, not in its group as well. Its group is Tracks for a track,
 *   and for a waypoint whose live record names any track in this entry; otherwise its kind's group.
 *   A group's switch includes or leaves out its new items too, which settles them.
 *
 * Decided items are ordered as the day's live candidates are (by time, from the repositories), with any
 * that are no longer live after them in decision order. The decision lists themselves put a changed
 * decision last (`CartographyViewModel`'s setters), which would otherwise move an item to the bottom of
 * its list as its switch is flipped.
 */
data class EntryContents(
    val newItems: List<EntryItem>,
    val tracks: List<EntryTrackWithWaypoints>,
    val waypoints: List<EntryWaypointItem>,
    val finds: List<EntryFindItem>,
    val offlineMaps: List<EntryOfflineMapItem>,
    private val membership: Map<EntryGroup, List<EntryItem>>,
) {
    /** Every item a group's switch changes: its decided items and its new ones. */
    fun members(group: EntryGroup): List<EntryItem> = membership[group].orEmpty()

    /** Read from the group's decided items only; a new item is not yet in or out. */
    fun groupState(group: EntryGroup): EntryGroupState {
        val members = members(group)
        if (members.isEmpty()) return EntryGroupState.EMPTY
        val decided = members.filter { it.inclusion != Inclusion.NEW }
        return when {
            decided.isEmpty() -> EntryGroupState.NOT_CHOSEN
            decided.all { it.inclusion == Inclusion.INCLUDED } -> EntryGroupState.ALL_INCLUDED
            decided.none { it.inclusion == Inclusion.INCLUDED } -> EntryGroupState.ALL_LEFT_OUT
            else -> EntryGroupState.SOME_INCLUDED
        }
    }

    /**
     * The panel's summary line. Waypoints count without the tracks' own Start and End, matching the
     * owner's example ("1 track, 5 waypoints, 6 finds, 1 offline map" for one walk with five dropped
     * waypoints, which the data scout counts as 2 + 5 waypoint rows).
     */
    val included: EntryIncludedCounts
        get() {
            val all = membership.values.flatten()
            return EntryIncludedCounts(
                tracks = all.count { it is EntryTrackItem && it.inclusion == Inclusion.INCLUDED },
                waypoints = all.count { it is EntryWaypointItem && it.designation == null && it.inclusion == Inclusion.INCLUDED },
                finds = all.count { it is EntryFindItem && it.inclusion == Inclusion.INCLUDED },
                offlineMaps = all.count { it is EntryOfflineMapItem && it.inclusion == Inclusion.INCLUDED },
            )
        }
}

enum class EntryGroup { TRACKS, WAYPOINTS, FINDS, OFFLINE_MAPS }

enum class Inclusion { INCLUDED, LEFT_OUT, NEW }

enum class EntryGroupState { EMPTY, NOT_CHOSEN, ALL_INCLUDED, SOME_INCLUDED, ALL_LEFT_OUT }

data class EntryIncludedCounts(val tracks: Int, val waypoints: Int, val finds: Int, val offlineMaps: Int) {
    val isEmpty: Boolean get() = tracks == 0 && waypoints == 0 && finds == 0 && offlineMaps == 0
}

sealed interface EntryItem {
    val id: String
    val inclusion: Inclusion
}

/** [distanceMeters]/[durationMillis]/[pointCount] are the decision's snapshot, or computed from [liveTrack] for a new one. */
data class EntryTrackItem(
    override val id: String,
    val name: String?,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointCount: Int,
    val liveTrack: Track?,
    override val inclusion: Inclusion,
) : EntryItem

/** [trackId] and [designation] come from the live record, so both are `null` when it is not loaded or is gone. */
data class EntryWaypointItem(
    override val id: String,
    val name: String,
    val trackId: String?,
    val designation: WaypointDesignation?,
    override val inclusion: Inclusion,
) : EntryItem

data class EntryFindItem(
    override val id: String,
    val foundOn: LocalDate,
    val ownIdentification: String?,
    override val inclusion: Inclusion,
) : EntryItem

data class EntryOfflineMapItem(
    val regionId: Long,
    val name: String,
    val radiusKm: Int,
    override val inclusion: Inclusion,
) : EntryItem {
    override val id: String get() = regionId.toString()
}

data class EntryTrackWithWaypoints(val track: EntryTrackItem, val waypoints: List<EntryWaypointItem>)

/**
 * Builds [EntryContents] from [entry]'s decisions and the day's live [candidates] and
 * [candidateOfflineRegions]. [candidates] is `null` while the day's report has not loaded (or failed);
 * the panel then shows the entry's own decisions only, as the editor always did.
 */
fun entryContentsOf(
    entry: CartographyEntry,
    candidates: DerivedTrip?,
    candidateOfflineRegions: List<OfflineRegionSummary>,
    computeTrackStatistics: ComputeTrackStatisticsUseCase,
): EntryContents {
    val liveTracks = candidates?.tracks.orEmpty()
    val liveTracksById = liveTracks.associateBy { it.id }
    val liveWaypoints = candidates?.waypoints.orEmpty()
    val liveWaypointsById = liveWaypoints.associateBy { it.id }

    // Decided items, in the live order where they are live.
    val decidedTracks = entry.trackDecisions
        .sortedByLiveOrder(liveTracks.map { it.id }) { it.trackId }
        .map { decision ->
            EntryTrackItem(
                id = decision.trackId,
                name = decision.name,
                distanceMeters = decision.distanceMeters,
                durationMillis = decision.durationMillis,
                pointCount = decision.pointCount,
                liveTrack = liveTracksById[decision.trackId],
                inclusion = decision.kept.asInclusion,
            )
        }
    val decidedWaypoints = entry.waypointDecisions
        .sortedByLiveOrder(liveWaypoints.map { it.id }) { it.waypointId }
        .map { decision ->
            val live = liveWaypointsById[decision.waypointId]
            EntryWaypointItem(
                id = decision.waypointId,
                name = decision.name,
                trackId = live?.trackId,
                designation = live?.designation,
                inclusion = decision.kept.asInclusion,
            )
        }
    val decidedFinds = entry.findDecisions
        .sortedByLiveOrder(candidates?.finds.orEmpty().map { it.id }) { it.findId }
        .map { EntryFindItem(id = it.findId, foundOn = it.foundOn, ownIdentification = it.ownIdentification, inclusion = it.kept.asInclusion) }
    val decidedOfflineMaps = entry.offlineRegionDecisions
        .sortedByLiveOrder(candidateOfflineRegions.map { it.id }) { it.offlineRegionId }
        .map { EntryOfflineMapItem(regionId = it.offlineRegionId, name = it.name, radiusKm = it.radiusKm, inclusion = it.kept.asInclusion) }

    // New items: live candidates with no decision.
    val decidedTrackIds = entry.trackDecisions.map { it.trackId }.toSet()
    val decidedWaypointIds = entry.waypointDecisions.map { it.waypointId }.toSet()
    val decidedFindIds = entry.findDecisions.map { it.findId }.toSet()
    val decidedRegionIds = entry.offlineRegionDecisions.map { it.offlineRegionId }.toSet()
    val newTracks = liveTracks.filter { it.id !in decidedTrackIds }.map { track ->
        val stats = computeTrackStatistics(track.points)
        EntryTrackItem(
            id = track.id,
            name = track.name,
            distanceMeters = stats.distanceMeters,
            durationMillis = stats.durationMillis,
            pointCount = stats.totalPoints,
            liveTrack = track,
            inclusion = Inclusion.NEW,
        )
    }
    val newWaypoints = liveWaypoints.filter { it.id !in decidedWaypointIds }.map {
        EntryWaypointItem(id = it.id, name = it.name, trackId = it.trackId, designation = it.designation, inclusion = Inclusion.NEW)
    }
    val newFinds = candidates?.finds.orEmpty().filter { it.id !in decidedFindIds }.map {
        EntryFindItem(id = it.id, foundOn = it.foundOn, ownIdentification = it.ownIdentification, inclusion = Inclusion.NEW)
    }
    val newOfflineMaps = candidateOfflineRegions.filter { it.id !in decidedRegionIds }.map {
        EntryOfflineMapItem(regionId = it.id, name = it.name, radiusKm = it.region.radiusKm, inclusion = Inclusion.NEW)
    }

    // A decided waypoint sits under a decided track it names; the rest are listed on their own.
    val shownTrackIds = decidedTracks.map { it.id }.toSet()
    val (waypointsUnderTracks, looseWaypoints) = decidedWaypoints.partition { it.trackId != null && it.trackId in shownTrackIds }
    val tracks = decidedTracks.map { track -> EntryTrackWithWaypoints(track, waypointsUnderTracks.filter { it.trackId == track.id }) }

    val allTrackIds = shownTrackIds + newTracks.map { it.id }
    val (newWaypointsOfTracks, newLooseWaypoints) = newWaypoints.partition { it.trackId != null && it.trackId in allTrackIds }

    val membership = mapOf(
        EntryGroup.TRACKS to decidedTracks + waypointsUnderTracks + newTracks + newWaypointsOfTracks,
        EntryGroup.WAYPOINTS to looseWaypoints + newLooseWaypoints,
        EntryGroup.FINDS to decidedFinds + newFinds,
        EntryGroup.OFFLINE_MAPS to decidedOfflineMaps + newOfflineMaps,
    )
    return EntryContents(
        newItems = newTracks + newWaypoints + newFinds + newOfflineMaps,
        tracks = tracks,
        waypoints = looseWaypoints,
        finds = decidedFinds,
        offlineMaps = decidedOfflineMaps,
        membership = membership,
    )
}

private val Boolean.asInclusion: Inclusion get() = if (this) Inclusion.INCLUDED else Inclusion.LEFT_OUT

/** This list in [liveOrder]'s order, with anything not in it after, in its own order. Stable. */
private inline fun <T, Id> List<T>.sortedByLiveOrder(liveOrder: List<Id>, crossinline idOf: (T) -> Id): List<T> {
    val index = liveOrder.withIndex().associate { it.value to it.index }
    return sortedBy { index[idOf(it)] ?: Int.MAX_VALUE }
}
