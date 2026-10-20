package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from NavigationHud.kt (dispatch 2026-10-19-03, UI split Phase 1, PR 1).

import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteWithheldReason
import com.zynergylabs.forager.app.domain.model.LatLng

/** Everything the HUD draws, as plain values — the pure half, so a sign or threshold error is a pinned-literal test failure, not a visual one. */
internal data class NavigationHudReadout(
    val headingText: String,
    /** Rotation for the north arrow, device-relative; `null` when there is no heading to draw from. */
    val northArrowDegrees: Float?,
    val targetText: String,
    /** Rotation for the target arrow, device-relative; `null` when there is no needle to draw — no heading, fix lost, or inside the approach threshold. */
    val targetArrowDegrees: Float?,
    val distanceText: String,
    val distanceDeEmphasised: Boolean,
    val statusText: String,
    /** The fix's altitude, or "Elevation unavailable" for a fix that carries none; `null` with no fix at all (the row is not drawn). */
    val elevationText: String?,
    /** MGRS or the labelled decimal pair, per the shared toggle; `null` with no fix at all (the row is not drawn). */
    val coordinatesText: String?,
    /** True when the large slot says the route could not be calculated and recomputing could change that: "Try again" is offered. */
    val routeRetryOffered: Boolean = false,
    /**
     * Dispatch 2026-09-28-677 (data part B; the owner, RECORD -656: "0.4 mi by trail" or "0.3 mi straight"): what the large
     * figure measures, written after it in smaller type; `null` when the slot holds words or a dash, or "within" a fix's
     * circle, which already says what it is.
     */
    val distanceKindText: String? = null,
    /**
     * Dispatch -677: [headingText] is the compass's reading (a value, or the dash while it waits), so it is drawn after its
     * label, "Facing", until RECORD -728 removed it; nothing reads this field for a label now. False when [headingText] is itself a status ("Compass unavailable", the facing notices), which
     * needs no label.
     */
    val headingIsReading: Boolean = false,
    /**
     * T11 fixes (dispatch 2026-10-09-02, RECORD -766; the owner: "Distance first, short status (Recommended)"): [statusText]'s
     * shorter forms, longest first, for when the whole one does not fit; "" last means the line may be left empty (see
     * [NavigationHudFit]'s order). Read only while [statusFallbacksFor] is still [statusText], so a readout copied with a new
     * status (the approximate and last-known displays) never shows another status's short form. Before T11 there was one
     * short form, Amendment 1 of dispatch -685 (RECORD -694, "Drop 'Approaching ·' if needed"): "Last fix 45 s ago" for
     * "Approaching · last fix 45 s ago", kept as the first here.
     */
    val statusFallbacks: List<String> = emptyList(),
    val statusFallbacksFor: String? = null,
    /** T11 fixes: [statusText] is the straight-line note on the way home, whose "≈ 1250 ft" form is not used while "By trail ·" leads the line. */
    val statusIsStraightNote: Boolean = false,
    /**
     * Dispatch 2026-10-12-08 (RECORD -951, option (a): GPS status messages show on the strip only): [statusText] says what the
     * position is, no fix ([NO_FIX_MESSAGE]), approximate or last seen, which the compass strip, on screen while navigating since
     * RECORD -950, already says. The navigation info's status line leaves it out. The readout still carries it, as data.
     */
    val statusIsPositionStatus: Boolean = false,
) {
    /**
     * The status's forms, longest first (T11 fixes). With "By trail ·" leading the line ([kindInStatus]) the straight-line
     * note is shown whole or not at all, so the line never reads "By trail · ≈ 1250 ft" (a figure the lead would seem to name).
     */
    fun statusForms(kindInStatus: Boolean = false): List<String> {
        if (statusFallbacksFor != statusText) return listOf(statusText)
        val all = (listOf(statusText) + statusFallbacks).distinct()
        return if (statusIsStraightNote && kindInStatus) all.filter { it == statusText || it.isEmpty() } else all
    }

    /**
     * Dispatch 2026-09-28-685, Amendment 1 (RECORD -694): the first shorter status, or `null` when there is none. See
     * [statusTextThatFits] and, since T11, [statusLineFit].
     */
    val statusShortText: String? get() = statusForms().drop(1).firstOrNull { it.isNotEmpty() }

    /**
     * Dispatch -677: whether the second row is drawn. Since the heading moved there (with its label, out of the space under
     * the north arrow, which at 360 dp could not hold a labelled heading beside the turn words and the distance), the row
     * shows whenever there is a fix to give elevation and coordinates, as before, and also, with no fix, when the heading
     * has something to say beside the dash: "Compass unavailable", or a facing notice. With no fix and only the dash it is
     * not drawn, as before: the status line already carries [NO_FIX_MESSAGE], once.
     */
    val secondRowShown: Boolean get() = coordinatesText != null || headingText != NO_HEADING_TEXT
}

/**
 * The route home as the HUD shows it while returning (dispatch 2026-09-28-423, plan task T6). See
 * [returnRouteOf] for how it is read from [RouteHome].
 */
sealed interface ReturnRoute {
    /** No route result yet: the first seconds of a return, before there is a good fix. The large slot is a dash, not a failure. */
    data object Pending : ReturnRoute

    /** The needle aims at [lookahead]; the large figure is [routeMeters]. */
    data class Ahead(val lookahead: LatLng, val routeMeters: Double) : ReturnRoute

    /**
     * Withheld: "Unable to calculate route". [canRetry] is false where recomputing against the
     * same inputs cannot change the answer, a track with no usable points.
     */
    data class Unavailable(val canRetry: Boolean) : ReturnRoute
}

/**
 * [RouteHome] as the HUD shows it. `null`, no result yet, is [ReturnRoute.Pending]. A route
 * withheld because the walker is off it ([RouteWithheldReason.OFF_ROUTE]) offers "Try again", since
 * the walker moving back towards it changes the answer; one withheld for
 * [RouteWithheldReason.NO_USABLE_POINTS] does not, since nothing would (dispatch 2026-09-28-423).
 */
fun returnRouteOf(routeHome: RouteHome?): ReturnRoute = when (routeHome) {
    null -> ReturnRoute.Pending
    is RouteHome.Ahead -> ReturnRoute.Ahead(routeHome.lookahead, routeHome.routeMeters)
    is RouteHome.Withheld -> ReturnRoute.Unavailable(canRetry = routeHome.reason == RouteWithheldReason.OFF_ROUTE)
}
