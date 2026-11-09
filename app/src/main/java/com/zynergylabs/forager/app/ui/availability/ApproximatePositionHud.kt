package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ShownPosition
import com.zynergylabs.forager.app.domain.ageMillis
import com.zynergylabs.forager.app.domain.inPlaceOfGps
import com.zynergylabs.forager.app.domain.isApproaching
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatApproximateDistance
import com.zynergylabs.forager.app.domain.relativeBearingDegrees
import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.turnText
import com.zynergylabs.forager.app.domain.shownPosition
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading

/** The HUD's status line with only an approximate position (the owner's words, RECORD -508). */
internal const val APPROXIMATE_HUD_TEXT = "Approximate, finding GPS…"

/**
 * Everything the HUD draws, from the GPS fix, the approximate reading and the last known one
 * (dispatch 2026-09-28-510). The position shown in place of GPS is decided by the one rule the map's
 * label and the strip read ([inPlaceOfGps]); with none, this is [navigationReadout] exactly as before,
 * stale and lost fixes included. "Arrived" is only ever [navigationReadout]'s, from the GPS fix.
 */
internal fun hudReadout(
    heading: TrueHeadingReading,
    liveFix: LocationFix.Update?,
    approximateFix: LocationFix.Update?,
    lastKnownFix: LocationFix.Update?,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    showDecimalDegrees: Boolean = false,
    route: ReturnRoute? = null,
    facing: NavigationFacing = NavigationFacing.FACING_UP,
): NavigationHudReadout = when (val inPlace = shownPosition(liveFix, approximateFix, lastKnownFix, nowEpochMillis).inPlaceOfGps(liveFix)) {
    is ShownPosition.Approximate -> approximateNavigationReadout(heading, inPlace.fix, target, distanceUnit, nowEpochMillis, route, facing)
    is ShownPosition.LastKnown -> lastKnownNavigationReadout(heading, inPlace.fix, target, distanceUnit, nowEpochMillis, facing)
    else -> navigationReadout(heading, liveFix, target, distanceUnit, nowEpochMillis, showDecimalDegrees, route, facing)
}

/**
 * The HUD with only an approximate position (RECORD -508, and the owner's answers in the coder's window):
 * - **Far** (the target beyond twice the reading's accuracy, the HUD's own needle rule, [isApproaching];
 *   the owner chose "Within twice the circle"): "≈ distance" ([formatApproximateDistance]) and the needle.
 * - **Near**: no needle, and a dash where the distance goes; at that range the bearing can be wrong by
 *   more than 30°, and a distance inside the uncertainty says nothing.
 * - **Returning** (a [route] given): no needle at any range. Decision D5 never points the return straight
 *   at the start in place of the route, and the route needs GPS; the owner chose "No needle, ≈ distance".
 * - Always "Approximate, finding GPS…" on the status line; never "Arrived" or "Approaching", which are
 *   claims about where the walker is; no coordinates row, since a 1 m grid reference for a reading known
 *   to 100 m is the false precision the strip withholds too.
 *
 * The heading label and the north arrow are [navigationReadout]'s own, from its no-fix layout, so the
 * two HUDs label the compass identically.
 */
internal fun approximateNavigationReadout(
    heading: TrueHeadingReading,
    reading: LocationFix.Update,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    route: ReturnRoute?,
    facing: NavigationFacing,
): NavigationHudReadout {
    if (target == null) {
        // The GPS HUD's "no origin waypoint" layout, without the reading's coordinates.
        return navigationReadout(heading, reading, null, distanceUnit, nowEpochMillis, facing = facing).copy(elevationText = null, coordinatesText = null)
    }
    val noFix = navigationReadout(heading, null, target, distanceUnit, nowEpochMillis, facing = facing)
    val here = LatLng(reading.lat, reading.lng)
    val there = LatLng(target.lat, target.lng)
    val distanceMeters = GeoDistance.metersBetween(here, there)
    val accuracy = reading.accuracyMeters
    val far = accuracy != null && !isApproaching(distanceMeters, accuracy)
    // Available only: an unreliable compass, or none, has no degrees, so no needle, as on the GPS HUD.
    val headingDegrees = (heading as? TrueHeadingReading.Available)?.degrees
    val needle = if (far && route == null && headingDegrees != null) {
        relativeBearingDegrees(GeoDistance.initialBearingDegrees(here, there), headingDegrees)
    } else {
        null
    }
    return noFix.copy(
        // Dispatch 2026-09-28-677: the turn in words, as the GPS display words it ("Slight left · 10°"); was "Turn N°".
        targetText = needle?.let { turnText(it) } ?: "",
        targetArrowDegrees = needle,
        distanceText = if (far) formatApproximateDistance(distanceMeters, accuracy, distanceUnit) else "—",
        // Dispatch -677: "≈ 3.2 km straight": from an approximate reading the figure is always the straight line, never the route.
        distanceKindText = DistanceKind.STRAIGHT.words.takeIf { far },
        statusText = APPROXIMATE_HUD_TEXT,
    )
}

/**
 * The HUD with only a last known position (dispatch 2026-09-28-510, as stated to the owner): no distance
 * and no needle, since a position hours old points nowhere useful; the GPS HUD's no-fix layout with its
 * age in place of "Location services unavailable": "Last seen 2 h ago, finding GPS…".
 */
internal fun lastKnownNavigationReadout(
    heading: TrueHeadingReading,
    known: LocationFix.Update,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    facing: NavigationFacing,
): NavigationHudReadout =
    navigationReadout(heading, null, target, distanceUnit, nowEpochMillis, facing = facing)
        .copy(statusText = lastSeenText(known.ageMillis(nowEpochMillis)) + FINDING_GPS_SUFFIX)
