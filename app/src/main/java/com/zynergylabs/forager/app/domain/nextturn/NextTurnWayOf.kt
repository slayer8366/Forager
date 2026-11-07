package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.wayhome.WayHomeShown

/**
 * The way home as the next turn needs it ([NextTurnWay]), from what `WayHomeWatch` shows (dispatch 2026-11-06-01, Amendment 1,
 * J2: "Call nextTurn(...) where the line and progress live (TrackRecordingRouteTicks, AvailabilityWaypointRoute)"):
 * - a line along trails: [NextTurnWay.AlongTrails], with its junctions once read ([WayHomeShown.turns], `null` while read);
 * - nothing shown yet, or the first way home of a return still being worked out: [NextTurnWay.Pending];
 * - a way home with no trail route (the track fallback, no routing data): [NextTurnWay.NoTrailRoute], so the space is blank
 *   (E2, the owner: "For instances without trails, when there is no routing data, then keep it blank.").
 */
fun nextTurnWayOf(shown: WayHomeShown?): NextTurnWay {
    val line = shown?.line
    return when {
        line != null -> NextTurnWay.AlongTrails(line, shown.turns)
        shown?.result == null -> NextTurnWay.Pending
        else -> NextTurnWay.NoTrailRoute
    }
}
