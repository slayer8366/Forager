package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from NavigationHud.kt (dispatch 2026-10-19-03, UI split Phase 1, PR 1).

import com.zynergylabs.forager.app.ui.map.TrueHeadingReading

internal const val NAVIGATION_HUD_TAG = "navigation-hud"
internal const val NAVIGATION_HUD_EXIT_TAG = "navigation-hud-exit"
internal const val NAVIGATION_HUD_HEADING_TAG = "navigation-hud-heading"
internal const val NAVIGATION_HUD_DISTANCE_TAG = "navigation-hud-distance"
internal const val NAVIGATION_HUD_STATUS_TAG = "navigation-hud-status"
internal const val NAVIGATION_HUD_TARGET_TAG = "navigation-hud-target"
internal const val NAVIGATION_HUD_RETRY_TAG = "navigation-hud-retry"
internal const val NAVIGATION_HUD_DISTANCE_KIND_TAG = "navigation-hud-distance-kind"
/** The needle: the target compass's arrow (T11 fixes, so a test can say nothing covers it). */
internal const val NAVIGATION_HUD_NEEDLE_TAG = "navigation-hud-needle"
/** Every [LabelledReadout]'s label, on the display and the strip alike: a test finds them by text within a parent. */
internal const val NAVIGATION_LABEL_TAG = "navigation-label"
/** Dispatch 2026-10-12-06: the compass arrow inline before the heading (or the crossed-out compass in its place), so a test can say it sits beside the needle. */
internal const val NAVIGATION_HUD_NORTH_ARROW_TAG = "navigation-hud-north-arrow"
/** RECORD -941: each piece of the evening stack in the card's right column. */
internal const val NAVIGATION_HUD_EVENING_PIECE_TAG = "navigation-hud-evening-piece"
/** Dispatch 2026-10-12-08: the navigation info's one divider, between its left and right parts, their full height (RECORD -945). */
internal const val NAVIGATION_HUD_DIVIDER_TAG = "navigation-hud-divider"

/**
 * The one message the strip and the HUD both show when there is no fix. One cause, one statement —
 * not "compass needs a fix · elevation unavailable · coordinates unavailable", three fragments the
 * owner read on device as three separate breakages (navigation-chrome dispatch, item 4). Keyed on
 * the *missing fix*, never on [TrueHeadingReading.NeedsFix]: a phone with no magnetometer and no
 * fix reads [TrueHeadingReading.NoSensor] (`rememberTrueHeading` checks the sensor first), and is
 * still, first, a location problem.
 */
internal const val NO_FIX_MESSAGE = "Location services unavailable"

/** The refresh control's label, the planner's wording (dispatch 2026-09-28-423), shown to the owner with its placement. */
internal const val ROUTE_RETRY_TEXT = "Try again"

/**
 * The labels for the heading and the altitude on the navigation display and the compass strip (dispatch 2026-09-28-677, data
 * part B; the owner, RECORD -656: "heading and altitude labelled"). Short, because at 360 dp the strip's one line holds
 * "Facing 123° SE · Alt 1352 ft · 10T ER 25118 40235" with a few dp to spare. **New words, a stop for the owner.**
 *
 * RECORD -728, the owner: "Keep the the 330° NW metric, just remove the word "facing" and nothing else." The heading's label,
 * "Facing", is gone from the strip and the navigation display's second row; the heading reads "330° NW" alone. "Alt" stays.
 */
internal const val ALTITUDE_LABEL = "Alt"

/** The heading's dash while the compass waits for a fix: the status line carries [NO_FIX_MESSAGE] (owner's call). */
internal const val NO_HEADING_TEXT = "—"

/** A fix that carries no altitude. A status that names itself, so it wears no [ALTITUDE_LABEL]. */
internal const val ELEVATION_UNAVAILABLE_TEXT = "Elevation unavailable"

/** The large slot's words once the walker has arrived at the start (dispatch 2026-09-28-497). */
internal const val ARRIVED_TEXT = "Arrived"

/** The large slot's words when the route is withheld: the owner's, 2026-09-12. */
internal const val ROUTE_UNAVAILABLE_TEXT = "Unable to calculate route"

/** The navigation display's back-by line (dispatch 2026-09-28-645). */
internal const val NAVIGATION_HUD_BACK_BY_LINE_TAG = "navigation-hud-back-by-line"
