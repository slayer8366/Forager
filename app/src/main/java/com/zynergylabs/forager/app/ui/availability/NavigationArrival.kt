package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from NavigationHud.kt (dispatch 2026-10-19-03, UI split Phase 1, PR 1).

import com.zynergylabs.forager.app.domain.hasArrived
import com.zynergylabs.forager.app.domain.FixFreshness
import com.zynergylabs.forager.app.domain.LOST_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.STALE_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ageMillis
import com.zynergylabs.forager.app.domain.fixFreshness
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint

/**
 * Whether the walker has arrived at [start] (dispatch 2026-09-28-497, plan task T7): a fix that is
 * not lost, within [hasArrived]'s radius, straight line. The one rule the HUD's "Arrived" and the
 * map's ring both read, so the two cannot disagree. Since dispatch 2026-09-28-502 [start] is any
 * navigation target, a chosen waypoint as well as the start (the owner: "Same as the start"); the name
 * is kept from T7.
 */
internal fun arrivedAtStart(liveFix: LocationFix.Update?, start: Waypoint, nowEpochMillis: Long): Boolean {
    if (liveFix == null || fixFreshness(liveFix.ageMillis(nowEpochMillis)) == FixFreshness.LOST) return false
    return hasArrived(GeoDistance.metersBetween(LatLng(liveFix.lat, liveFix.lng), LatLng(start.lat, start.lng)), liveFix.accuracyMeters)
}

/**
 * The display while it holds "Arrived" after a return ended on arrival (RECORD -844, -845): [live]'s heading,
 * elevation and coordinates, with the large slot reading [ARRIVED_TEXT] whatever the fix says, and no needle,
 * no turn, no status and no distance label, as an arrival read live has none (see [navigationReadout]'s
 * `arrived` branches). Held, not live, so a fix moving away inside the hold changes nothing here.
 */
internal fun heldArrivalReadout(live: NavigationHudReadout): NavigationHudReadout =
    live.copy(
        targetText = "",
        targetArrowDegrees = null,
        distanceText = ARRIVED_TEXT,
        distanceDeEmphasised = false,
        statusText = "",
        routeRetryOffered = false,
        distanceKindText = null,
        statusFallbacks = emptyList(),
        statusFallbacksFor = null,
        statusIsStraightNote = false,
    )

/**
 * Dispatch 2026-09-28-502, step 3: the dashed line navigating to a waypoint draws, from the walker's fix to
 * [target]. None with no fix or a lost one (the HUD withholds its distance then too: there is no walker to
 * draw from), and none once arrived ([arrivedAtStart]), where the line ends as the return's does.
 */
internal fun straightLineToTarget(liveFix: LocationFix.Update?, target: Waypoint, nowEpochMillis: Long): List<LatLng>? {
    if (liveFix == null || fixFreshness(liveFix.ageMillis(nowEpochMillis)) == FixFreshness.LOST) return null
    if (arrivedAtStart(liveFix, target, nowEpochMillis)) return null
    return listOf(LatLng(liveFix.lat, liveFix.lng), LatLng(target.lat, target.lng))
}

/**
 * The dashed line navigating to a waypoint draws (dispatch -502), and whether it is from a current fix
 * ([isCurrent]) or faded: from a stale fix, or kept from the last one after the fix was lost.
 */
internal data class StraightLine(val points: List<LatLng>, val isCurrent: Boolean, val isOffline: Boolean = false)

/**
 * The line to draw next, given [previous], the one drawn last (dispatch -502). Two choices of the owner's:
 * - At the S22 desk check, 2026-10-04, in the coder's window, "Keep the last line, faded", whose option read
 *   "If the location is lost after it was found, keep the last dashed line, faded, as the way back does when
 *   its route can't be calculated. Right after the app opens there's no last line, so it still waits for the
 *   first fix."
 * - Relayed by the planner (RECORD -506), "Fade with the HUD": from the moment the HUD dims its distance, the
 *   line fades too, and a fresh fix brings both back.
 *
 * So:
 * - A fresh fix (under 30 s): the line from it ([straightLineToTarget]), current.
 * - A stale fix (30 s to 5 min, the HUD's dimmed distance): the line from it, faded.
 * - Arrived (on a fix that is not lost): none; the line ends there, as before.
 * - No fix, or a lost one (the HUD's "No fix for N min"): [previous] kept, grey ([StraightLine.isOffline];
 *   the owner, at the desk check: "Grey once location is lost"), if it was drawn to this [target]; none if
 *   nothing was drawn yet, as when the app has just opened.
 */
internal fun nextStraightLine(previous: StraightLine?, liveFix: LocationFix.Update?, target: Waypoint, nowEpochMillis: Long): StraightLine? {
    straightLineToTarget(liveFix, target, nowEpochMillis)?.let { points ->
        val fresh = liveFix != null && fixFreshness(liveFix.ageMillis(nowEpochMillis)) == FixFreshness.FRESH
        return StraightLine(points, isCurrent = fresh)
    }
    val lost = liveFix == null || fixFreshness(liveFix.ageMillis(nowEpochMillis)) == FixFreshness.LOST
    if (!lost) return null
    // The owner, after seeing the kept line at the desk check: "Grey once location is lost": kept, it is the offline line.
    return previous?.takeIf { it.points.lastOrNull() == LatLng(target.lat, target.lng) }?.copy(isCurrent = false, isOffline = true)
}

/**
 * How long until [fix] next changes how the line is drawn: to stale ([STALE_AFTER_MILLIS]) while fresh, to lost
 * ([LOST_AFTER_MILLIS]) while stale; `null` once lost, or with no fix, since nothing more changes until a new
 * fix comes. What lets the line fade at those moments even when nothing else changes on screen, since no new
 * fix is coming to prompt it. Always more than 0 when not `null`.
 */
internal fun millisUntilFreshnessChanges(fix: LocationFix.Update?, nowEpochMillis: Long): Long? {
    val age = fix?.ageMillis(nowEpochMillis) ?: return null
    return when (fixFreshness(age)) {
        FixFreshness.FRESH -> STALE_AFTER_MILLIS - age
        FixFreshness.STALE -> LOST_AFTER_MILLIS - age
        FixFreshness.LOST -> null
    }
}
