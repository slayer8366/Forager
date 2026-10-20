package com.zynergylabs.forager.app.ui.log

// Moved unchanged from RecordDetailsSheet.kt (dispatch 2026-10-19-07, UI split Phase 1, PR 4).

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier

/**
 * Which Records row's details sheet is open (journal redesign J5c; the owner: "have them display info
 * upon tapping", form "Bottom sheet of details (Recommended)"). A waypoint, a recorded track or an
 * offline region, by id. Finds are not here: a find tile already opens the find's report.
 *
 * Held by [RecordsTab] in `rememberSaveable` state through [RecordDetailsTargetSaver], so an open
 * sheet survives a rotation (which is not a recreation here, J5 L7) and an Activity recreation.
 * Leaving the Journal tab with the sheet open is not possible: the sheet is modal, so its scrim
 * covers the bottom bar and the rail. Its Navigate (dispatch 2026-09-28-502) closes it before the
 * Maps tab comes up, and Back from that navigation opens it again by request.
 */
internal sealed interface RecordDetailsTarget {
    /**
     * [fromTrackId] (dispatch -616 as amended by -618): the walk whose details this waypoint was opened
     * from, through the "Waypoints on this track" list; Back returns there ([returnsTo]). `null` for a
     * waypoint opened any other way, which Back closes as before.
     */
    data class WaypointDetails(val id: String, val fromTrackId: String? = null) : RecordDetailsTarget
    data class TrackDetails(val id: String) : RecordDetailsTarget
    data class OfflineRegionDetails(val id: Long) : RecordDetailsTarget
}

/**
 * Where Back on this target's sheet goes (dispatch -616 as amended by -618; the owner's "Back retraces the
 * way in"): a waypoint opened from its walk's list returns to that walk's details; every other target has
 * nowhere to return to, and Back closes the sheet.
 */
internal fun RecordDetailsTarget.returnsTo(): RecordDetailsTarget? =
    (this as? RecordDetailsTarget.WaypointDetails)?.fromTrackId?.let { RecordDetailsTarget.TrackDetails(it) }

/**
 * Saves a [RecordDetailsTarget] as one string, `waypoint:<id>`, `track:<id>` or `region:<id>` (and, since dispatch -616,
 * `walk-waypoint:<track id><U+001F><id>` for a waypoint opened from its walk). A
 * string that is none of these fails loudly on restore rather than quietly opening nothing, the
 * same choice [JournalScreenState]'s saver makes with `valueOf` and `toBooleanStrict`.
 */
internal val RecordDetailsTargetSaver: Saver<RecordDetailsTarget?, String> = Saver(
    save = { target ->
        when (target) {
            null -> ""
            // Dispatch -616: a waypoint opened from its walk keeps the walk, so Back still returns there after a recreation.
            is RecordDetailsTarget.WaypointDetails -> target.fromTrackId?.let { "$WALK_WAYPOINT_KEY$it$WALK_WAYPOINT_SEPARATOR${target.id}" }
                ?: "$WAYPOINT_KEY${target.id}"
            is RecordDetailsTarget.TrackDetails -> "$TRACK_KEY${target.id}"
            is RecordDetailsTarget.OfflineRegionDetails -> "$REGION_KEY${target.id}"
        }
    },
    restore = { saved ->
        when {
            saved.isEmpty() -> null
            saved.startsWith(WALK_WAYPOINT_KEY) -> saved.removePrefix(WALK_WAYPOINT_KEY).let { rest ->
                val at = rest.indexOf(WALK_WAYPOINT_SEPARATOR)
                if (at < 0) error("Not a saved record-details target: '$saved'")
                RecordDetailsTarget.WaypointDetails(id = rest.substring(at + 1), fromTrackId = rest.substring(0, at))
            }
            saved.startsWith(WAYPOINT_KEY) -> RecordDetailsTarget.WaypointDetails(saved.removePrefix(WAYPOINT_KEY))
            saved.startsWith(TRACK_KEY) -> RecordDetailsTarget.TrackDetails(saved.removePrefix(TRACK_KEY))
            saved.startsWith(REGION_KEY) -> RecordDetailsTarget.OfflineRegionDetails(saved.removePrefix(REGION_KEY).toLong())
            else -> error("Not a saved record-details target: '$saved'")
        }
    },
)

private const val WAYPOINT_KEY = "waypoint:"
private const val TRACK_KEY = "track:"
private const val REGION_KEY = "region:"
private const val WALK_WAYPOINT_KEY = "walk-waypoint:"

/** Between the walk's id and the waypoint's: a control character, so neither id (a UUID, or anything with colons) can contain it. */
private const val WALK_WAYPOINT_SEPARATOR = '\u001F'

/**
 * What a Records row does on a tap once J5c has given it one: open [onClick], announced to TalkBack
 * as "Details for <name>" ([recordDetailsClickLabel]). One modifier for every row type so the three
 * rows cannot drift apart in how they take the tap.
 *
 * A row inside a J4b [TwoStageSwipeRow] needs nothing more for J4b's rule ("a tap on an open row
 * closes it and does not open it"): while the row is open, that component lays an overlay over its
 * content which takes the tap and closes the row, so this click never sees it. The row's own
 * buttons (Directions, Share) are children of the clickable node and take their own taps first.
 */
@Composable
internal fun Modifier.opensRecordDetails(name: String, onClick: () -> Unit): Modifier =
    // Amendment 1 to motion Part 1 (RECORD -657): the press is drawn in a rounded shape; the tap is where it always was.
    clickableWithShapedPress(onClickLabel = recordDetailsClickLabel(name), onClick = onClick)

/** The click label a Records row carries for TalkBack: "Details for Creek pin". */
internal fun recordDetailsClickLabel(name: String): String = "Details for $name"
