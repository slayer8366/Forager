package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.NavigationTargetKind

/**
 * Where "Navigate" was tapped for a waypoint (dispatch 2026-09-28-502, Amendment 1, the owner's
 * "Back to where you tapped Navigate"): one Back ends the navigation and puts the walker back at this
 * step. Each kind names the waypoint.
 *
 * Since dispatch 2026-11-05-05 (Amendment 1, A8; the owner: "Back returns to the trip") a trip's bubble and its card on
 * the Maps tab are origins too: [MapBubble] and [MapDetails] with [kind] TRIP, whose [waypointId] is then the trip's id
 * (the name is kept from -502). Every other origin is a waypoint's.
 */
sealed interface WaypointNavigationOrigin {
    val waypointId: String

    /** What [waypointId] names. */
    val kind: NavigationTargetKind get() = NavigationTargetKind.WAYPOINT

    /** The waypoint's bubble on the Maps tab, where it was anchored when Navigate was tapped. */
    data class MapBubble(
        override val waypointId: String,
        val anchorPx: Offset,
        val bearingDeg: Float,
        /** A trip's bubble with [NavigationTargetKind.TRIP] (dispatch 2026-11-05-05). */
        override val kind: NavigationTargetKind = NavigationTargetKind.WAYPOINT,
    ) : WaypointNavigationOrigin

    /** The waypoint's details sheet, opened from its bubble on the Maps tab; a trip's card with [kind] TRIP. */
    data class MapDetails(
        override val waypointId: String,
        override val kind: NavigationTargetKind = NavigationTargetKind.WAYPOINT,
    ) : WaypointNavigationOrigin

    /** The waypoint's row in Records. */
    data class RecordsRow(override val waypointId: String) : WaypointNavigationOrigin

    /** The waypoint's details sheet, opened from Records. */
    data class RecordsDetails(override val waypointId: String) : WaypointNavigationOrigin

    /**
     * The waypoint's bubble on a journal entry's map (RECORD -890), or the details sheet opened from it. Back returns to
     * the entry, as Back after a Records row returns to Records.
     */
    data class JournalEntryMap(override val waypointId: String) : WaypointNavigationOrigin
}

/**
 * Saves a [WaypointNavigationOrigin] as one string, its kind first and the waypoint's id last, so an id
 * holding the separator still comes back whole: `bubble:<x>:<y>:<bearing>:<id>`, `map-details:<id>`,
 * `records-row:<id>`, `records-details:<id>`, `journal-entry:<id>`, or empty for none. A string that is none of these fails
 * loudly on restore, as [com.zynergylabs.forager.app.ui.log.RecordDetailsTargetSaver] does. A trip's bubble and card
 * (dispatch 2026-11-05-05) are `trip-bubble:<x>:<y>:<bearing>:<id>` and `trip-map-details:<id>`; a waypoint's keep the
 * strings they had.
 */
internal val WaypointNavigationOriginSaver: Saver<WaypointNavigationOrigin?, String> = Saver(
    save = { origin ->
        when (origin) {
            null -> ""
            is WaypointNavigationOrigin.MapBubble ->
                "${if (origin.kind == NavigationTargetKind.TRIP) TRIP_BUBBLE_KEY else BUBBLE_KEY}${origin.anchorPx.x}:${origin.anchorPx.y}:${origin.bearingDeg}:${origin.waypointId}"
            is WaypointNavigationOrigin.MapDetails -> "${if (origin.kind == NavigationTargetKind.TRIP) TRIP_MAP_DETAILS_KEY else MAP_DETAILS_KEY}${origin.waypointId}"
            is WaypointNavigationOrigin.RecordsRow -> "$RECORDS_ROW_KEY${origin.waypointId}"
            is WaypointNavigationOrigin.RecordsDetails -> "$RECORDS_DETAILS_KEY${origin.waypointId}"
            is WaypointNavigationOrigin.JournalEntryMap -> "$JOURNAL_ENTRY_KEY${origin.waypointId}"
        }
    },
    restore = { saved ->
        when {
            saved.isEmpty() -> null
            saved.startsWith(BUBBLE_KEY) -> restoreBubble(saved.removePrefix(BUBBLE_KEY), NavigationTargetKind.WAYPOINT)
            saved.startsWith(TRIP_BUBBLE_KEY) -> restoreBubble(saved.removePrefix(TRIP_BUBBLE_KEY), NavigationTargetKind.TRIP)
            saved.startsWith(MAP_DETAILS_KEY) -> WaypointNavigationOrigin.MapDetails(saved.removePrefix(MAP_DETAILS_KEY))
            saved.startsWith(TRIP_MAP_DETAILS_KEY) -> WaypointNavigationOrigin.MapDetails(saved.removePrefix(TRIP_MAP_DETAILS_KEY), NavigationTargetKind.TRIP)
            saved.startsWith(RECORDS_ROW_KEY) -> WaypointNavigationOrigin.RecordsRow(saved.removePrefix(RECORDS_ROW_KEY))
            saved.startsWith(RECORDS_DETAILS_KEY) -> WaypointNavigationOrigin.RecordsDetails(saved.removePrefix(RECORDS_DETAILS_KEY))
            saved.startsWith(JOURNAL_ENTRY_KEY) -> WaypointNavigationOrigin.JournalEntryMap(saved.removePrefix(JOURNAL_ENTRY_KEY))
            else -> error("Not a saved waypoint navigation origin: '$saved'")
        }
    },
)

/** A saved bubble's anchor, bearing and id, its kind's key already taken off. */
private fun restoreBubble(rest: String, kind: NavigationTargetKind): WaypointNavigationOrigin.MapBubble {
    val (x, y, bearing, id) = rest.split(":", limit = 4)
    return WaypointNavigationOrigin.MapBubble(id, Offset(x.toFloat(), y.toFloat()), bearing.toFloat(), kind)
}

private const val BUBBLE_KEY = "bubble:"
private const val MAP_DETAILS_KEY = "map-details:"
private const val TRIP_BUBBLE_KEY = "trip-bubble:"
private const val TRIP_MAP_DETAILS_KEY = "trip-map-details:"
private const val RECORDS_ROW_KEY = "records-row:"
private const val RECORDS_DETAILS_KEY = "records-details:"
private const val JOURNAL_ENTRY_KEY = "journal-entry:"
