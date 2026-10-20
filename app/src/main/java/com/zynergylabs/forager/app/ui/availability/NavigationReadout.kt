package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from NavigationHud.kt (dispatch 2026-10-19-03, UI split Phase 1, PR 1).

import com.zynergylabs.forager.app.domain.FixFreshness
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ageMillis
import com.zynergylabs.forager.app.domain.fixFreshness
import com.zynergylabs.forager.app.domain.isApproaching
import com.zynergylabs.forager.app.domain.isInApproachingZone
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatWholeLength
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import com.zynergylabs.forager.app.domain.relativeBearingDegrees
import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.turnText
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.COMPASS_NORTH_UP_TEXT
import com.zynergylabs.forager.app.ui.map.COMPASS_CALIBRATING_TEXT
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import kotlin.math.roundToInt

internal fun navigationReadout(
    heading: TrueHeadingReading,
    liveFix: LocationFix.Update?,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    showDecimalDegrees: Boolean = false,
    route: ReturnRoute? = null,
    facing: NavigationFacing = NavigationFacing.FACING_UP,
): NavigationHudReadout {
    val headingDegrees = (heading as? TrueHeadingReading.Available)?.degrees
    val headingText = when (heading) {
        is TrueHeadingReading.Available -> "${heading.degrees.roundToInt() % 360}° ${cardinalDirection(heading.degrees)}"
        TrueHeadingReading.NoSensor -> "Compass unavailable"
        // Compass-reliability dispatch: present, reporting, not to be trusted. No cause named —
        // see the strip's own branch for why. The north arrow is not rotated (headingDegrees is
        // null for this state) and the needle below is withheld.
        TrueHeadingReading.Unreliable -> "Compass unreliable"
        // A dash, not a message: the status line carries NO_FIX_MESSAGE, once (owner's call).
        TrueHeadingReading.NeedsFix -> NO_HEADING_TEXT
    }.let { label ->
        // Dispatch 2026-09-28-430, ruling E (continuation 2026-09-28-432): one place, one word. While
        // the map's navigation view retries a stuck compass, and once it has turned north-up, this
        // label is the notice; no separate one is drawn on the map. Only in those two states, which
        // only an unreliable compass or none at all produces.
        when (facing) {
            NavigationFacing.FACING_UP -> label
            NavigationFacing.CALIBRATING -> COMPASS_CALIBRATING_TEXT
            NavigationFacing.NORTH_UP -> COMPASS_NORTH_UP_TEXT
        }
    }
    // Dispatch -677: a reading (a value, or the dash while it waits for a fix) wears the label; a status does not.
    val headingIsReading = facing == NavigationFacing.FACING_UP &&
        (heading is TrueHeadingReading.Available || heading == TrueHeadingReading.NeedsFix)
    val compassUnreliable = heading is TrueHeadingReading.Unreliable
    val northArrowDegrees = headingDegrees?.let { -it }
    val elevationText = liveFix?.let { fix -> fix.altitude?.let { formatWholeLength(it, UnitSystem.forDistanceUnit(distanceUnit)) } ?: ELEVATION_UNAVAILABLE_TEXT }
    val coordinatesText = liveFix?.let { coordinatesStripText(LatLng(it.lat, it.lng), showDecimalDegrees) }

    if (liveFix == null) {
        return NavigationHudReadout(
            headingText, northArrowDegrees, "Target", null, "—", false, NO_FIX_MESSAGE, null, null, headingIsReading = headingIsReading,
            statusFallbacks = listOf(NO_FIX_SHORT_TEXT), statusFallbacksFor = NO_FIX_MESSAGE, statusIsPositionStatus = true,
        )
    }
    if (target == null) {
        return NavigationHudReadout(
            headingText, northArrowDegrees, "No target", null, "—", false, NO_ORIGIN_TEXT, elevationText, coordinatesText, headingIsReading = headingIsReading,
            statusFallbacks = listOf(NO_ORIGIN_SHORT_TEXT), statusFallbacksFor = NO_ORIGIN_TEXT,
        )
    }

    val here = LatLng(liveFix.lat, liveFix.lng)
    val there = LatLng(target.lat, target.lng)
    // Where the needle aims: the route's lookahead while returning (decision D1), the target
    // itself with no route given. A route that is pending or withheld has nothing to aim at, so
    // there is no needle (decision D5: never a straight line standing in for the route).
    val aim = when (route) {
        null -> there
        is ReturnRoute.Ahead -> route.lookahead
        ReturnRoute.Pending, is ReturnRoute.Unavailable -> null
    }
    val bearing = aim?.let { GeoDistance.initialBearingDegrees(here, it) }
    val distanceMeters = GeoDistance.metersBetween(here, there)
    val age = liveFix.ageMillis(nowEpochMillis)
    val freshness = fixFreshness(age)
    // The needle's threshold — see the class doc's needle paragraph. Until dispatch 2026-09-28-578
    // (Amendment 1, -579) this was `approaching` and also decided the word; the word now has its own
    // zone (`approaching` below) and this gates only the needle and the target column, unchanged.
    val withinFixError = freshness != FixFreshness.LOST && isApproaching(distanceMeters, liveFix.accuracyMeters)
    // Dispatch -578 (Amendment 1, -579): "Approaching" within 100 m and not arrived; arrived, "Arrived"
    // stands alone. The same arrival rule the large slot reads below, so the two cannot disagree.
    val arrived = arrivedAtStart(liveFix, target, nowEpochMillis)
    val approaching = freshness != FixFreshness.LOST && !arrived && isInApproachingZone(distanceMeters)
    // Never more precision than the fix supports — "within 16 ft" inside the error circle, "≈ 10 m"
    // beyond it, today's formatting when no accuracy was reported. See formatDistanceWithAccuracy.
    val straightLineText = formatDistanceWithAccuracy(distanceMeters, liveFix.accuracyMeters, distanceUnit)
    val distanceText = when {
        freshness == FixFreshness.LOST -> "—"
        // Dispatch -497: the return's arrival at the start; navigation stays on until the walker ends it.
        // Dispatch -502 (Amendment 1): any target, a waypoint navigated to with no route as well, so this
        // and the map's ring read the one rule and agree. There is no arrival flag: until -502 this
        // branch was gated on a route being given.
        arrivedAtStart(liveFix, target, nowEpochMillis) -> ARRIVED_TEXT
        route == null -> straightLineText
        route is ReturnRoute.Ahead -> formatDistanceMeters(route.routeMeters, distanceUnit)
        route is ReturnRoute.Unavailable -> ROUTE_UNAVAILABLE_TEXT
        else -> "—"
    }
    // Dispatch -677: what the large figure measures. The straight line says "straight" unless it reads "within" the fix's
    // circle (formatDistanceWithAccuracy), which is a statement about the circle, not a measured line; the route says "by trail".
    val insideFixCircle = liveFix.accuracyMeters?.let { distanceMeters <= it } == true
    val distanceKind = when {
        freshness == FixFreshness.LOST || arrived -> null
        route == null -> DistanceKind.STRAIGHT.takeIf { !insideFixCircle }
        route is ReturnRoute.Ahead -> DistanceKind.BY_TRAIL
        else -> null
    }

    // Four things withhold the needle, and their ORDER IS DELIBERATE (compass-reliability
    // dispatch, owner decision) — do not let branch position imply it, and do not insert a fifth
    // term without deciding where it ranks:
    //   1. Lost fix wins. A needle needs heading AND position; the position failure is the more
    //      fundamental, and the user's remedy is different.
    //   2. Unreliable compass next. The heading exists and cannot be trusted.
    //   3. Approach threshold last. The only one of the three that is not a failure.
    //   4. No heading at all (no sensor) is the existing case — and since the two-data-corrections
    //      dispatch (Part C) it withholds the text as well, not just the needle.
    // `compassUnreliable` is named in the `if` even though headingDegrees is already null for that
    // state, so the term is visible where the order is stated rather than implied by a null.
    // A fifth term, ranked last (dispatch 2026-09-28-423): no aim, a route pending or withheld.
    // It is not a failure of the compass or the fix, so it ranks below all four.
    val targetArrowDegrees = if (headingDegrees != null && freshness != FixFreshness.LOST && !compassUnreliable && !withinFixError && bearing != null) relativeBearingDegrees(bearing, headingDegrees) else null
    val targetText = when {
        freshness == FixFreshness.LOST -> "Target"
        // Same rendering as the approach case below, and for the same reason the owner recorded
        // there: an absolute bearing you cannot orient to is a number without a use. Unreliable
        // leaves the user in that position.
        compassUnreliable -> ""
        // Nothing — not the distance (that was one number in two slots on device, "9 ft · 9 ft ·
        // Approaching"), not a dash, not a placeholder. The distance slot carries the one number.
        withinFixError -> ""
        // No aim: the large slot already says why (a dash, or "Unable to calculate route").
        bearing == null -> ""
        // Dispatch -677 (the owner, RECORD -656, "Plain words + labels"): "Slight left · 10°", never 0 to 359. Was "Turn N°".
        headingDegrees != null -> turnText(relativeBearingDegrees(bearing, headingDegrees))
        // No sensor. This branch used to read "Bearing N° X" — the one state that still showed the
        // absolute bearing as text. The compass-reliability dispatch asked for the unreliable case
        // to match it and was wrong about what it did; the follow-up (two-data-corrections dispatch,
        // Part C, owner decision) brought no-sensor into line with approach and unreliable instead:
        // a number the user cannot orient to is withheld, the distance slot carries the one number.
        else -> ""
    }
    // Dispatch 2026-09-28-423 (decision D3, option B): while returning, the straight line moves
    // here from the large slot, labelled, since the large slot now holds the route figure. It
    // takes the state where the line was empty, the one "Path home" took before it: a stale or
    // lost fix already owns the line with a message the walker needs more, and "Approaching" is
    // left as it is until plan task T7 moves it onto the start's glyph. One line of labelMedium
    // has no room for both a label and a stale fix's age.
    val statusText = when (freshness) {
        FixFreshness.LOST -> "No fix for ${formatFixAge(age)}"
        FixFreshness.STALE -> if (approaching) "$APPROACHING_PREFIX${staleFixText(age).replaceFirstChar { it.lowercaseChar() }}" else staleFixText(age)
        FixFreshness.FRESH -> when {
            // The owner: "Drop "Approaching" when arrived", "Arrived" alone; with a route that also
            // drops the straight line, which before this dispatch read under "Arrived" outside twice
            // the accuracy.
            arrived -> ""
            approaching -> "Approaching"
            // RECORD -942 (the owner: "Remove the 0.7m straight since the ≈800ft ahead is saying the same thing"; told it is the
            // straight line, not the trail, "Remove it"): while the large slot holds the distance by trail, the straight-line note
            // (dispatch -677's "0.3 mi straight") is not shown. The coder's reading, reported for the owner: before the first
            // route and with the route withheld the large slot holds a dash or the route message, so nothing else says how far
            // the start is, and the note stays there, as it was.
            route is ReturnRoute.Pending || route is ReturnRoute.Unavailable -> "$straightLineText ${DistanceKind.STRAIGHT.words}"
            else -> ""
        }
    }
    // T11 fixes (RECORD -766): the shorter forms, in the order NavigationHudFit.kt states. A warning has no empty form, so it
    // is never dropped; "Approaching" and the straight-line note may be.
    val statusFallbacks = when (freshness) {
        FixFreshness.LOST -> listOf(lostFixShortText(age))
        FixFreshness.STALE -> listOfNotNull(staleFixText(age).takeIf { approaching }, staleFixShortText(age))
        FixFreshness.FRESH -> when {
            arrived -> emptyList()
            approaching -> listOf("")
            route is ReturnRoute.Pending || route is ReturnRoute.Unavailable -> listOf(straightLineText, "")
            else -> emptyList()
        }
    }
    // RECORD -942: the straight-line note is shown only before the first route and with the route withheld.
    val statusIsStraightNote = freshness == FixFreshness.FRESH && !arrived && !approaching && (route is ReturnRoute.Pending || route is ReturnRoute.Unavailable)
    return NavigationHudReadout(
        headingText = headingText,
        northArrowDegrees = northArrowDegrees,
        targetText = targetText,
        targetArrowDegrees = targetArrowDegrees,
        distanceText = distanceText,
        distanceDeEmphasised = freshness == FixFreshness.STALE,
        statusText = statusText,
        elevationText = elevationText,
        coordinatesText = coordinatesText,
        routeRetryOffered = freshness != FixFreshness.LOST && route is ReturnRoute.Unavailable && route.canRetry,
        distanceKindText = distanceKind?.words,
        headingIsReading = headingIsReading,
        statusFallbacks = statusFallbacks,
        statusFallbacksFor = statusText,
        statusIsStraightNote = statusIsStraightNote,
    )
}

/** "Last fix 45 s ago": the stale line alone, and the short form of the approaching one. */
private fun staleFixText(ageMillis: Long): String = "Last fix ${formatFixAge(ageMillis)} ago"

private const val APPROACHING_PREFIX = "Approaching · "

/**
 * The status line to draw in [maxWidthPx] with no lead before it: [NavigationHudReadout.statusText] when [widthOf] says it
 * fits, else the longest shorter form that does (dispatch 2026-09-28-685, Amendment 1, RECORD -694; since T11, RECORD -766,
 * every form is checked in turn, and a line that may be dropped can come back empty). See [statusLineFit].
 */
internal fun statusTextThatFits(readout: NavigationHudReadout, maxWidthPx: Int, widthOf: (String) -> Int): String =
    statusLineFit(readout, kindInStatus = false, maxPx = maxWidthPx, leadSeparatorPx = 0, widthOf = widthOf).status

/** "48 s" under a minute, "6 min" from a minute on — coarse on purpose; the number's job is "old", not a stopwatch. */
internal fun formatFixAge(ageMillis: Long): String {
    val seconds = (ageMillis / 1_000L).coerceAtLeast(0L)
    return if (seconds < 60L) "$seconds s" else "${seconds / 60L} min"
}
