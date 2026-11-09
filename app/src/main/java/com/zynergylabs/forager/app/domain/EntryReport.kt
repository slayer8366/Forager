package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint

/**
 * The figures behind a journal entry's report (dispatch 2026-09-28-667, data part A; the owner in RECORD
 * -656: "Summary tiles + height profile (Recommended)": labelled tiles for Distance, Time out, Climb and
 * Finds, a small height profile from stored altitudes, then the waypoints as a short table of name, time
 * and distance from start, with coordinates behind a tap).
 *
 * Only included items count, as everywhere in the report. [distanceMeters] and [durationMillis] are the
 * sums of the included tracks' snapshots, the same figures the report always showed per track, so a
 * track deleted from Records still counts in them. [climbMeters] and [heightProfile] need each included
 * track's live points, which a snapshot does not hold (the data scout, section A1: `TrackDecision` has no
 * gain column); when any included track's points are missing, both say so rather than show a figure for
 * part of the walk as if it were all of it.
 */
data class EntryReport(
    val trackCount: Int,
    val distanceMeters: Double,
    /** The included tracks' times added together, each first point to last. Time between two walks is not counted. */
    val durationMillis: Long,
    /** Height gained over the included tracks, `null` when it is not known for every one of them. */
    val climbMeters: Double?,
    val findCount: Int,
    val heightProfile: HeightProfile,
    val waypoints: List<EntryReportWaypoint>,
)

sealed interface HeightProfile {
    /** No track is included, so the report draws no profile and says nothing about one. */
    data object NoTrack : HeightProfile

    /** An included track's points could not be read (deleted from Records, or the day's records did not load). */
    data object PointsUnavailable : HeightProfile

    /** Too few of the included tracks' points carry a height to draw a profile that is not misleading; see [PROVISIONAL_PROFILE_HEIGHT_LIMIT]. */
    data class TooFewHeights(val pointsWithHeight: Int, val totalPoints: Int) : HeightProfile

    /**
     * One [segments] entry per included track, in the order walked, each its points that carry a height,
     * against distance along the tracks from the first one's start. The distance runs on from one track
     * to the next without counting the gap between them.
     */
    data class Drawn(
        val segments: List<List<HeightSample>>,
        val lowestMeters: Double,
        val highestMeters: Double,
        val lengthMeters: Double,
    ) : HeightProfile
}

data class HeightSample(val distanceMeters: Double, val altitudeMeters: Double)

/**
 * One included waypoint's table row. [timeEpochMillis] is when its live record was made, `null` when that
 * record is gone. [distanceFromStartMeters] is the distance walked along its own track up to that time,
 * so it is known only for a waypoint whose live record names an included track with real point times
 * (a recording, or a GPX import that carried times); `null` otherwise, never a straight-line stand-in.
 */
data class EntryReportWaypoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val timeEpochMillis: Long?,
    val distanceFromStartMeters: Double?,
)

/**
 * When a walk has enough heights for a profile: at least [minPointsWithHeight] of the included tracks'
 * points carry one, and they are at least [minShareWithHeight] of all those points. Below either, the
 * report shows no profile and one plain line saying why (the owner: "Don't draw a misleading profile").
 */
data class ProfileHeightLimit(val minPointsWithHeight: Int, val minShareWithHeight: Double)

/**
 * **Provisional.** The owner, RECORD -671: "Keep it, check real walks (Recommended)". These figures are
 * a judgement, not a measurement: real altitude coverage on the owner's walks has not been read (the
 * data scout, section H). The device check in `docs/ui/2026-10-07-data-a-entry-report.md` reads it off
 * real walks; change it here, in one place, from what that finds.
 */
val PROVISIONAL_PROFILE_HEIGHT_LIMIT = ProfileHeightLimit(minPointsWithHeight = 10, minShareWithHeight = 0.5)

/**
 * Builds [EntryReport] for [entry] from the day's [liveTracks] and [liveWaypoints] (the editor's
 * candidates; empty when they did not load). Pure.
 */
fun entryReportOf(
    entry: CartographyEntry,
    liveTracks: List<Track>,
    liveWaypoints: List<Waypoint>,
    computeTrackStatistics: ComputeTrackStatisticsUseCase,
): EntryReport {
    val includedTracks = entry.trackDecisions.filter { it.kept }
    val liveById = liveTracks.associateBy { it.id }
    val includedLive = includedTracks.mapNotNull { liveById[it.trackId] }
    val allLive = includedLive.size == includedTracks.size

    // RECORD -867 (the planner's call, option a): a track with fewer than two points has no height change to miss, so it
    // is left out of Climb instead of blanking it for the whole entry. A longer track with no heights still blanks it.
    val measurable = includedLive.filter { it.points.size >= MIN_POINTS_FOR_CLIMB }
    val climb = if (includedTracks.isEmpty() || !allLive || measurable.isEmpty()) {
        null
    } else {
        val gains = measurable.map { computeTrackStatistics(it.points).elevationGainMeters }
        if (gains.any { it == null }) null else gains.sumOf { it ?: 0.0 }
    }

    val liveWaypointsById = liveWaypoints.associateBy { it.id }
    val includedLiveById = includedLive.associateBy { it.id }
    val waypoints = entry.waypointDecisions.filter { it.kept }.map { decision ->
        val live = liveWaypointsById[decision.waypointId]
        val time = live?.createdAtEpochMillis
        val track = live?.trackId?.let { includedLiveById[it] }
        EntryReportWaypoint(
            id = decision.waypointId,
            name = decision.name,
            lat = decision.lat,
            lng = decision.lng,
            timeEpochMillis = time,
            distanceFromStartMeters = if (track != null && time != null && !track.importedWithoutTimes) distanceWalkedBy(track, time) else null,
        )
    }.sortedBy { it.timeEpochMillis ?: Long.MAX_VALUE }

    return EntryReport(
        trackCount = includedTracks.size,
        distanceMeters = includedTracks.sumOf { it.distanceMeters },
        durationMillis = includedTracks.sumOf { it.durationMillis },
        climbMeters = climb,
        findCount = entry.findDecisions.count { it.kept },
        heightProfile = when {
            includedTracks.isEmpty() -> HeightProfile.NoTrack
            !allLive -> HeightProfile.PointsUnavailable
            else -> heightProfileOf(includedLive)
        },
        waypoints = waypoints,
    )
}

/**
 * The height profile of [tracks] walked in order, under [PROVISIONAL_PROFILE_HEIGHT_LIMIT]. Not private since dispatch
 * 2026-09-28-677 (data part B), whose track sheet draws one track's profile with it, so the entry report and the track sheet
 * draw a walk's heights by the one rule.
 */
fun heightProfileOf(tracks: List<Track>): HeightProfile {
    val totalPoints = tracks.sumOf { it.points.size }
    val withHeight = tracks.sumOf { track -> track.points.count { it.altitude != null } }
    val limit = PROVISIONAL_PROFILE_HEIGHT_LIMIT
    if (withHeight < limit.minPointsWithHeight || withHeight < totalPoints * limit.minShareWithHeight) {
        return HeightProfile.TooFewHeights(pointsWithHeight = withHeight, totalPoints = totalPoints)
    }
    var walked = 0.0
    val segments = tracks
        .sortedBy { it.points.firstOrNull()?.timestampEpochMillis ?: it.startedAtEpochMillis }
        .map { track ->
            val samples = mutableListOf<HeightSample>()
            // RECORD -867, part 4: the chart draws the heights Climb reads ([trackHeights]), not raw GPS altitude.
            val heights = trackHeights(track.points)
            track.points.forEachIndexed { index, point ->
                if (index > 0) {
                    val previous = track.points[index - 1]
                    walked += GeoDistance.metersBetween(LatLng(previous.lat, previous.lng), LatLng(point.lat, point.lng))
                }
                heights[index]?.let { samples += HeightSample(distanceMeters = walked, altitudeMeters = it) }
            }
            samples.toList()
        }
        .filter { it.isNotEmpty() }
    val heights = segments.flatten().map { it.altitudeMeters }
    return HeightProfile.Drawn(
        segments = segments,
        lowestMeters = heights.min(),
        highestMeters = heights.max(),
        lengthMeters = walked,
    )
}

/** Fewest points a track needs to count toward an entry's Climb: one point has no height change to measure (RECORD -867). */
internal const val MIN_POINTS_FOR_CLIMB = 2

/** Distance along [track]'s points up to the last one recorded at or before [atEpochMillis]; 0 before its first. */
private fun distanceWalkedBy(track: Track, atEpochMillis: Long): Double {
    var walked = 0.0
    for (index in 1 until track.points.size) {
        val point = track.points[index]
        if (point.timestampEpochMillis > atEpochMillis) break
        val previous = track.points[index - 1]
        walked += GeoDistance.metersBetween(LatLng(previous.lat, previous.lng), LatLng(point.lat, point.lng))
    }
    return walked
}
