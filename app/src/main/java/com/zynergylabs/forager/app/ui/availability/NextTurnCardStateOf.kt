package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.nextturn.NextTurnCardState

/**
 * What the card's top line holds, as the next turn's space needs it (dispatch 2026-11-06-01, Amendment 1, E1 and H4, 2b-1),
 * read from the card's own [readout], so the space and the top line cannot disagree. [hasFix] is whether there is a fix
 * at all (the readout's no-fix branch, `liveFix == null`).
 *
 * E1, the owner, to "When the card shows "Arrived", "—", a held-back route, no GPS fix, or you're within the fix's own
 * circle, what goes in the next-turn space?": "Blank (Recommended)". The option read: "The space keeps its place, empty,
 * so nothing moves. The straight-ahead arrow shows everywhere else there's no turn left."
 *
 * - no fix: [NextTurnCardState.NO_FIX];
 * - the top line reads [ARRIVED_TEXT], live or held after an arrival ([heldArrivalReadout]): [NextTurnCardState.ARRIVED];
 * - it reads the withheld route's words, [ROUTE_UNAVAILABLE_TEXT]: [NextTurnCardState.ROUTE_WITHHELD];
 * - it reads the dash (a lost fix, no target, a route still pending): [NextTurnCardState.NO_DISTANCE];
 * - otherwise a figure: [NextTurnCardState.SHOWS_DISTANCE], and the space shows what lies ahead.
 *
 * **Within the fix's own circle** gives no state of its own here: on the live card it is always "Arrived" first. The
 * needle is withheld within twice the fix's accuracy of the target (`isApproaching`), and arrival counts within the larger
 * of that and 15 m (`hasArrived`), so the one circle lies inside the other, and the top line reads "Arrived" there. Its
 * value, [NextTurnCardState.WITHIN_FIX_CIRCLE], is kept for the day those rules part.
 */
internal fun nextTurnCardState(readout: NavigationHudReadout, hasFix: Boolean): NextTurnCardState = when {
    readout.distanceText.isNotEmpty() || readout.distanceText.isEmpty() -> NextTurnCardState.SHOWS_DISTANCE // STUB
    !hasFix -> NextTurnCardState.NO_FIX
    readout.distanceText == ARRIVED_TEXT -> NextTurnCardState.ARRIVED
    readout.distanceText == ROUTE_UNAVAILABLE_TEXT -> NextTurnCardState.ROUTE_WITHHELD
    readout.distanceText == NO_DISTANCE_TEXT -> NextTurnCardState.NO_DISTANCE
    else -> NextTurnCardState.SHOWS_DISTANCE
}

/** The dash the top line reads with no figure ([navigationReadout]'s literal). Its own name here, so the card does not hold a bare string. */
private const val NO_DISTANCE_TEXT = "—"
