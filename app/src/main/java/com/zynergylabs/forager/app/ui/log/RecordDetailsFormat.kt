package com.zynergylabs.forager.app.ui.log

// Moved unchanged from RecordDetailsSheet.kt (dispatch 2026-10-19-07, UI split Phase 1, PR 4).

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.ui.track.NO_TIMES_IN_FILE
import com.zynergylabs.forager.app.domain.HeightProfile
import com.zynergylabs.forager.app.domain.heightProfileOf
import com.zynergylabs.forager.app.domain.trackMovingSpeedMetersPerSecond
import com.zynergylabs.forager.app.domain.model.TrackStatistics
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatSpeed
import com.zynergylabs.forager.app.domain.model.formatTimeSpan
import com.zynergylabs.forager.app.domain.model.formatWholeLength

/** "2 journal entries", "1 journal entry", "no journal entries": the snackbar's own nouns (J4). */
internal fun journalEntryCountLabel(count: Int): String = when (count) {
    0 -> "no journal entries"
    1 -> "1 journal entry"
    else -> "$count journal entries"
}

/**
 * A track's duration as the entry cards and the entry editor print it ("2h 10m", "48m";
 * `trackSubtitle(distanceMeters, durationMillis, unit)` in `CartographyEntryEditScreen.kt`, whose
 * duration half this repeats because that function returns distance and duration as one string and
 * its file is outside J5c).
 */
/** Plan T16: a track's duration, or "No times in file" for an imported track whose file had none (its sheet and its map bubble). */
internal fun trackDurationLabel(track: Track, durationMillis: Long): String =
    if (track.importedWithoutTimes) NO_TIMES_IN_FILE else formatTrackDuration(durationMillis)

internal fun formatTrackDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * The track sheet's tiles (dispatch 2026-09-28-677; the owner, RECORD -656: "Distance, Time, Climb, Descent, Avg speed"), as
 * label and value, in order. Separate from the layout so a test reads every figure exactly.
 *
 * - **Time** is first point to last, moving or not, as Duration was, in data part A's words ("1 h 10 min", [formatTimeSpan]).
 * - **Climb** and **Descent** are the hysteresis-filtered gain and loss ([ComputeTrackStatisticsUseCase]); a track with no
 *   two heights in a row has neither, and says "Not recorded", as the entry report's Climb does.
 * - **Moving speed** (Amendment 1, RECORD -680; the owner: "Moving speed (Recommended)") is the app's moving pace, the
 *   walk-back estimate's own ([trackMovingSpeedMetersPerSecond]), so stops do not count against it. With too little moving
 *   time to measure, it is a dash ([MISSING_FIGURE]), never the estimate's assumed default.
 * - A file with no times has no Time and no Moving speed: each says [NO_TIMES_IN_FILE], as the times above them do (-636).
 */
internal fun trackSheetTiles(track: Track, stats: TrackStatistics, distanceUnit: DistanceUnit): List<Pair<String, String>> {
    val unitSystem = UnitSystem.forDistanceUnit(distanceUnit)
    val noTimes = track.importedWithoutTimes
    return listOf(
        TILE_DISTANCE to formatDistanceMeters(stats.distanceMeters, distanceUnit),
        TRACK_TILE_TIME to if (noTimes) NO_TIMES_IN_FILE else formatTimeSpan(stats.durationMillis),
        TILE_CLIMB to (stats.elevationGainMeters?.let { formatWholeLength(it, unitSystem) } ?: CLIMB_NOT_RECORDED),
        TRACK_TILE_DESCENT to (stats.elevationLossMeters?.let { formatWholeLength(it, unitSystem) } ?: CLIMB_NOT_RECORDED),
        TRACK_TILE_MOVING_SPEED to when {
            noTimes -> NO_TIMES_IN_FILE
            else -> trackMovingSpeedMetersPerSecond(track.points)?.let { formatSpeed(it, distanceUnit) } ?: MISSING_FIGURE
        },
    )
}

/** One track's height profile, by the entry report's rule ([heightProfileOf]), so a walk with too few heights says so as there. */
internal fun trackHeightProfile(track: Track): HeightProfile = heightProfileOf(listOf(track))

/** "45 of 120 points": how many of the walk's points carry a height, the figure the Climb and the profile rest on. */
internal fun heightsRecordedLabel(pointsWithHeight: Int, totalPoints: Int): String = "$pointsWithHeight of $totalPoints points"

internal val FIELD_LABEL_WIDTH = 112.dp
internal val DETAILS_FOLD_MIN_HEIGHT = 48.dp
internal val TRACK_THUMBNAIL_SIZE = 96.dp

/** The Material minimum touch target, so a row is easy to hit with a thumb. */
internal val WALK_WAYPOINT_ROW_MIN_HEIGHT = 48.dp

internal const val WALK_WAYPOINTS_HEADING = "Waypoints on this track"

/** Dispatch -677: the track sheet's new words. **New strings, a stop for the owner** (Distance and Climb are data part A's). */
internal const val TRACK_TILE_TIME = "Time"
internal const val TRACK_TILE_DESCENT = "Descent"
internal const val TRACK_TILE_MOVING_SPEED = "Moving speed"
internal const val DETAILS_FOLD_TITLE = "Details"
internal const val DETAILS_FOLD_SHOW_LABEL = "Show details"
internal const val DETAILS_FOLD_HIDE_LABEL = "Hide details"
internal const val HEIGHTS_FIELD_LABEL = "With height"
internal fun trackTileTag(label: String) = "track-tile-$label"
internal const val RECORD_DETAILS_FOLD_TAG = "record-details-fold"
internal const val RECORD_DETAILS_WALK_WAYPOINTS_HEADING_TAG = "record-details-walk-waypoints-heading"
internal fun recordDetailsWalkWaypointTag(waypointId: String): String = "record-details-walk-waypoint-$waypointId"

internal const val RECORD_DETAILS_SHEET_TAG = "record-details-sheet"

internal const val RECORD_DETAILS_TITLE_TAG = "record-details-title"
internal const val RECORD_DETAILS_STALE_TAG = "record-details-stale"
internal const val RECORD_DETAILS_LABEL_TAG = "record-details-label"
internal const val RECORD_DETAILS_NOTE_TAG = "record-details-note"
internal const val RECORD_DETAILS_ZOOM_TAG = "record-details-zoom"
internal const val RECORD_DETAILS_THUMBNAIL_TAG = "record-details-thumbnail"
internal const val RECORD_DETAILS_DIRECTIONS_TAG = "record-details-directions"
internal const val RECORD_DETAILS_NAVIGATE_TAG = "record-details-navigate"
internal const val RECORD_DETAILS_SHARE_TAG = "record-details-share"
internal const val RECORD_DETAILS_DELETE_TAG = "record-details-delete"

internal fun recordDetailsFieldTag(key: String): String = "record-details-field-$key"

internal const val FIELD_MGRS = "mgrs"
internal const val FIELD_COORDINATES = "coordinates"
internal const val FIELD_CREATED = "created"
internal const val FIELD_TRACK = "track"
internal const val FIELD_USED_IN = "used-in"
internal const val FIELD_STARTED = "started"
internal const val FIELD_ENDED = "ended"
internal const val FIELD_DISTANCE = "distance"
internal const val FIELD_DURATION = "duration"
internal const val FIELD_IMPORTED = "imported"
internal const val FIELD_POINTS = "points"
internal const val FIELD_HEIGHTS = "heights"
internal const val FIELD_RADIUS = "radius"
internal const val FIELD_CENTRE = "centre"
internal const val FIELD_TILES = "tiles"
internal const val FIELD_SIZE = "size"
internal const val FIELD_DOWNLOADED = "downloaded"
