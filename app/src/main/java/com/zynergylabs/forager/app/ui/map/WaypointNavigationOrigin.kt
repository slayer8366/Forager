package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset

/**
 * Where "Navigate" was tapped for a waypoint (dispatch 2026-09-28-502, Amendment 1, the owner's
 * "Back to where you tapped Navigate"): one Back ends the navigation and puts the walker back at this
 * step. Each kind names the waypoint.
 */
sealed interface WaypointNavigationOrigin {
    val waypointId: String

    /** The waypoint's bubble on the Maps tab, where it was anchored when Navigate was tapped. */
    data class MapBubble(override val waypointId: String, val anchorPx: Offset, val bearingDeg: Float) : WaypointNavigationOrigin

    /** The waypoint's details sheet, opened from its bubble on the Maps tab. */
    data class MapDetails(override val waypointId: String) : WaypointNavigationOrigin

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
 * loudly on restore, as [com.zynergylabs.forager.app.ui.log.RecordDetailsTargetSaver] does.
 */
internal val WaypointNavigationOriginSaver: Saver<WaypointNavigationOrigin?, String> = Saver(
    save = { origin ->
        when (origin) {
            null -> ""
            is WaypointNavigationOrigin.MapBubble -> "$BUBBLE_KEY${origin.anchorPx.x}:${origin.anchorPx.y}:${origin.bearingDeg}:${origin.waypointId}"
            is WaypointNavigationOrigin.MapDetails -> "$MAP_DETAILS_KEY${origin.waypointId}"
            is WaypointNavigationOrigin.RecordsRow -> "$RECORDS_ROW_KEY${origin.waypointId}"
            is WaypointNavigationOrigin.RecordsDetails -> "$RECORDS_DETAILS_KEY${origin.waypointId}"
            is WaypointNavigationOrigin.JournalEntryMap -> "$JOURNAL_ENTRY_KEY${origin.waypointId}"
        }
    },
    restore = { saved ->
        when {
            saved.isEmpty() -> null
            saved.startsWith(BUBBLE_KEY) -> {
                val (x, y, bearing, id) = saved.removePrefix(BUBBLE_KEY).split(":", limit = 4)
                WaypointNavigationOrigin.MapBubble(id, Offset(x.toFloat(), y.toFloat()), bearing.toFloat())
            }
            saved.startsWith(MAP_DETAILS_KEY) -> WaypointNavigationOrigin.MapDetails(saved.removePrefix(MAP_DETAILS_KEY))
            saved.startsWith(RECORDS_ROW_KEY) -> WaypointNavigationOrigin.RecordsRow(saved.removePrefix(RECORDS_ROW_KEY))
            saved.startsWith(RECORDS_DETAILS_KEY) -> WaypointNavigationOrigin.RecordsDetails(saved.removePrefix(RECORDS_DETAILS_KEY))
            saved.startsWith(JOURNAL_ENTRY_KEY) -> WaypointNavigationOrigin.JournalEntryMap(saved.removePrefix(JOURNAL_ENTRY_KEY))
            else -> error("Not a saved waypoint navigation origin: '$saved'")
        }
    },
)

private const val BUBBLE_KEY = "bubble:"
private const val MAP_DETAILS_KEY = "map-details:"
private const val RECORDS_ROW_KEY = "records-row:"
private const val RECORDS_DETAILS_KEY = "records-details:"
private const val JOURNAL_ENTRY_KEY = "journal-entry:"
