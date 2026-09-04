package com.forager.app.ui.availability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.MgrsConverter
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MgrsCoordinate
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.TripWindowReport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How long ago [thenEpochMillis] was, in the coarsest unit that still says something useful —
 * "just now", "5 minutes ago", "3 hours ago", "2 days ago".
 *
 * Coarse on purpose: this labels a cached search, and the difference between 181 and 184 minutes
 * changes nothing about whether somebody wants to re-run it. Both callers pass a clock-provided
 * [nowEpochMillis] rather than reading [System.currentTimeMillis] here, so the output is a pure
 * function of its arguments and can be asserted on directly — see [CurrentTimeProvider].
 *
 * A [thenEpochMillis] in the future (a device clock moved backwards, or a row written under a
 * clock that was ahead) reads as "just now" rather than as a negative age. It is not a state this
 * app can produce on its own, and inventing a phrase for it would be a claim about a clock this
 * code cannot check.
 */
internal fun relativeTimeLabel(thenEpochMillis: Long, nowEpochMillis: Long): String {
    val elapsedMillis = nowEpochMillis - thenEpochMillis
    val minutes = elapsedMillis / MILLIS_PER_MINUTE
    val hours = elapsedMillis / MILLIS_PER_HOUR
    val days = elapsedMillis / MILLIS_PER_DAY
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes ${plural(minutes, "minute")} ago"
        hours < 24 -> "$hours ${plural(hours, "hour")} ago"
        else -> "$days ${plural(days, "day")} ago"
    }
}

private fun plural(count: Long, singular: String) = if (count == 1L) singular else "${singular}s"

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

/** "412 m" below 1 km, "1.2 km" at or above — shared by [returnToStartStripText] and [DistanceArm]'s own readout. */
internal fun formatReturnDistance(distanceMeters: Double): String =
    if (distanceMeters < 1000) "${distanceMeters.roundToInt()} m" else "${"%.1f".format(distanceMeters / 1000)} km"

/**
 * "Coordinates unavailable" before a first fix (distinct wording from [MgrsCoordinate.Unsupported]
 * — one is "no fix yet", the other is "this location can't be expressed in MGRS at all", and
 * collapsing them into one message would hide which is actually true).
 *
 * **MGRS by default; decimal degrees only on tap ([showDecimalDegrees]).** This strip used to show
 * both at once ("`<mgrs> · Lat. X Long. Y`"), but a real hardware pass found the combined line
 * truncates mid-coordinate on a metro-width screen ("Lat. 45.3262 Lon…") — half a coordinate is not
 * a coordinate, on the one element whose job is telling you where you are. MGRS is the better fit
 * for a strip this narrow: compact, unambiguous, built for grid work.
 *
 * **Decimal degrees stay reachable, not deleted** — tapping the coordinates segment toggles
 * [showDecimalDegrees] (see [CompassElevationStripContent]) — because the two formats serve
 * different readers and neither substitutes for the other: MGRS suits a paper-map/compass workflow,
 * decimal degrees is what everything else speaks (pasting a point into another app, giving
 * coordinates to a dispatcher, sharing a location by text). Checked before this landed: this
 * project has no configurable-coordinate-format setting (lat/lon vs. UTM vs. MGRS vs. decimal
 * degrees) wired anywhere yet, and no "emergency card" or share/copy path exists to keep the pair
 * reachable through instead — `grep -rn "CoordinateFormat"` finds nothing, and `docs/plans/` doesn't
 * specify either as built. Tap-to-reveal is what's actually shippable today, per the project's own
 * instruction for exactly this case: if the setting isn't wired, hardcode MGRS and say so, rather
 * than build the setting or the emergency card as unplanned scope here.
 *
 * The decimal pair is labeled ("Lat."/"Long.", the project owner's own wording) rather than the bare
 * "45.5231, -122.6414" [PlannedTripRow] shows — that row sits next to a named trip, where the pair
 * reads as "that trip's location" from context; this strip has no such context, so an unlabeled pair
 * here could as easily read as two unrelated numbers. `"%.4f"` matches the same precision every
 * other decimal-degree display in this file already uses (`PlannedTripRow`, `RecentSearchRow`, the
 * offline-map picker), not a new precision invented for this one line.
 */
internal fun coordinatesStripText(location: LatLng?, showDecimalDegrees: Boolean): String {
    if (location == null) return "Coordinates unavailable"
    if (showDecimalDegrees) {
        return "Lat. ${"%.4f".format(location.lat)} Long. ${"%.4f".format(location.lng)}"
    }
    return when (val mgrs = MgrsConverter.convert(location)) {
        is MgrsCoordinate.Grid -> mgrs.value
        is MgrsCoordinate.Unsupported -> "MGRS unavailable"
    }
}

/** Nearest 45°-wide compass point for [headingDegrees], `[0, 360)`. */
internal fun cardinalDirection(headingDegrees: Float): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((headingDegrees % 360f) + 360f) % 360f / 45f).roundToInt() % points.size
    return points[index]
}

/**
 * The name a newly-placed trip pin is pre-filled with: `"Trip N"`, `N` being one more than how
 * many trips already exist. The simplest possible default, per the user's own framing of this —
 * it doesn't guarantee uniqueness against a renamed or since-deleted trip (there is no rename, and
 * deleting trip 1 of 2 then adding a new one names it "Trip 2" again, alongside the surviving
 * "Trip 2"), but a collision here is cosmetic, not a broken invariant: [PlannedTrip.id] is what
 * actually identifies a trip, and the name stays freely editable in [TripDatePickerDialog] before
 * it's saved.
 */
internal fun defaultTripName(existingTripCount: Int): String = "Trip ${existingTripCount + 1}"

/** Same reasoning as [defaultTripName], applied to waypoints — see that function's own doc comment. */
internal fun defaultWaypointName(existingWaypointCount: Int): String = "Waypoint ${existingWaypointCount + 1}"

/** Shown when no navigation app can handle [directionsIntent] — CLAUDE.md: report, don't swallow. */
private const val NO_MAPS_APP_MESSAGE = "No maps app is installed to show directions."

/**
 * The `geo:` intent for a point named [name] at [location]: a `geo:0,0?q=lat,lng(label)` URI
 * resolves to whatever navigation app is installed rather than assuming Google Maps specifically,
 * which is the portable choice — see this file's own doc comment for why this lives here and not
 * in the ViewModel or `domain/`. Exposed as its own function (rather than inlined into
 * [launchDirections]) so a test can assert on its action/data without needing a resolvable package
 * or a running Activity.
 */
internal fun directionsIntent(name: String, location: LatLng): Intent {
    val label = Uri.encode(name)
    val uri = Uri.parse("geo:0,0?q=${location.lat},${location.lng}($label)")
    return Intent(Intent.ACTION_VIEW, uri)
}

/** [directionsIntent] for a [PlannedTrip] specifically — see [WaypointRow] for the other caller of the shared, name-plus-location overload. */
internal fun directionsIntent(trip: PlannedTrip): Intent = directionsIntent(trip.name, trip.location)

/**
 * Opens directions to [location] in whatever navigation app [directionsIntent] resolves to.
 * `Intent`/`Context` are Android framework types, so this launch has to happen from the Compose UI
 * layer — CLAUDE.md keeps both out of `domain/` and the ViewModel.
 *
 * Resolves the intent before launching, in addition to catching [ActivityNotFoundException]:
 * checking first is what lets this show one exact, always-correct message rather than depending
 * on whichever exception a given OEM build happens to raise for an unresolvable implicit intent —
 * the catch is the belt to the resolve check's suspenders, covering the narrow race where the
 * only maps app is uninstalled between the check and the launch. Either path shows a real message
 * (a [Toast]) rather than crashing or failing silently.
 */
internal fun launchDirections(context: Context, name: String, location: LatLng) {
    val intent = directionsIntent(name, location)
    if (intent.resolveActivity(context.packageManager) == null) {
        Toast.makeText(context, NO_MAPS_APP_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, NO_MAPS_APP_MESSAGE, Toast.LENGTH_SHORT).show()
    }
}

/** [launchDirections] for a [PlannedTrip] specifically — see [WaypointRow] for the other caller of the shared, name-plus-location overload. */
internal fun launchDirections(context: Context, trip: PlannedTrip) = launchDirections(context, trip.name, trip.location)

/** Shown when nothing can handle [inaturalistObservationIntent] — CLAUDE.md: report, don't swallow. */
private const val NO_INATURALIST_LINK_MESSAGE = "Nothing installed can open this observation."

/**
 * iNaturalist's own Android app package — confirmed on the Play Store listing
 * (`https://play.google.com/store/apps/details?id=org.inaturalist.android`), not guessed. Used to
 * target the app explicitly rather than relying on an implicit intent's own app-link resolution:
 * hardware testing found the plain implicit `ACTION_VIEW` below always opened a browser even with
 * the iNaturalist app installed, meaning its own intent filter for inaturalist.org isn't a verified
 * Android App Link on real devices — an unverified `BROWSABLE` filter only wins a disambiguation
 * dialog, never an automatic hand-off, so an implicit intent alone can't do what item 2's dispatch
 * ("open in the app first, else a browser") actually wants.
 */
private const val INATURALIST_PACKAGE = "org.inaturalist.android"

/**
 * The web URL for a single iNaturalist observation — the only per-observation link iNaturalist
 * itself publishes, not a scheme this app owns.
 */
internal fun inaturalistObservationIntent(observationId: Long): Intent {
    val uri = Uri.parse("https://www.inaturalist.org/observations/$observationId")
    return Intent(Intent.ACTION_VIEW, uri)
}

/**
 * Resolves [webIntent] against [INATURALIST_PACKAGE] first, falling back to the plain implicit
 * intent (typically a browser) when the app doesn't claim it, and to a [Toast] when nothing does —
 * shared with [launchINaturalistObservation]'s own doc comment, same as [launchDirections].
 */
private fun launchINaturalist(context: Context, webIntent: Intent) {
    val appIntent = Intent(webIntent).setPackage(INATURALIST_PACKAGE)
    val packageManager = context.packageManager
    val intent = if (appIntent.resolveActivity(packageManager) != null) appIntent else webIntent
    if (intent.resolveActivity(packageManager) == null) {
        Toast.makeText(context, NO_INATURALIST_LINK_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, NO_INATURALIST_LINK_MESSAGE, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens [observationId]'s iNaturalist page, preferring the installed iNaturalist app over a
 * browser. Tries [INATURALIST_PACKAGE] explicitly first ([Intent.setPackage] skips any
 * disambiguation and resolves straight to that app if it declares a matching intent filter at
 * all, verified or not); only when that doesn't resolve does this fall back to the plain implicit
 * intent, which resolves to whatever the device would otherwise pick (typically a browser).
 */
internal fun launchINaturalistObservation(context: Context, observationId: Long) =
    launchINaturalist(context, inaturalistObservationIntent(observationId))

/**
 * The point where a ray from a `[-halfWidth, halfWidth] x [-halfHeight, halfHeight]` rectangle's
 * own center, at [angleDeg] (clockwise from up, screen convention: +x right, +y down), exits the
 * rectangle's boundary — the standard "ray from an axis-aligned box's center" intersection, picking
 * whichever of the box's four edges the ray reaches first. Pure geometry, shared by
 * [AnchoredAtScreenPoint] (to place [ObservationBubble] so its own arrow tip lands exactly on the
 * dot, whichever edge or corner region the current bearing puts the arrow on) and
 * [ObservationBubble] itself (to draw that same arrow, from its own measured [size][androidx.compose.ui.geometry.Size] at
 * draw time) — the one thing both need to agree on, computed once rather than twice.
 */
internal fun rectEdgeIntersection(halfWidth: Float, halfHeight: Float, angleDeg: Float): Offset {
    val radians = Math.toRadians(angleDeg.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val tx = if (dx != 0f) halfWidth / abs(dx) else Float.POSITIVE_INFINITY
    val ty = if (dy != 0f) halfHeight / abs(dy) else Float.POSITIVE_INFINITY
    val t = min(tx, ty)
    return Offset(dx * t, dy * t)
}

/**
 * [Sighting.positionalAccuracyMeters] as a note the bubble can show verbatim.
 *
 * Never omitted for a null accuracy — a missing figure is not the same as a good one, and
 * silence would read as "precise" to a user who has no way to tell the difference.
 */
internal fun accuracyLabel(accuracyMeters: Int?): String =
    if (accuracyMeters != null) "±$accuracyMeters m accuracy" else "Accuracy not reported"

/**
 * Why no window was found, stated specifically with the numbers behind it — never a bare "none
 * found" (CLAUDE.md: partial or empty results are reported as such).
 */
internal fun noTripWindowMessage(report: TripWindowReport): String = when (val reason = report.noWindowReason) {
    is NoTripWindowReason.NoQualifyingRainEvent ->
        "No run of rain in the last ${reason.daysExamined} days totaled the " +
            "${"%.0f".format(reason.requiredTotalMm)}mm this search treats as a soaking event — the " +
            "wettest run reached ${"%.0f".format(reason.largestRunTotalMm)}mm."

    is NoTripWindowReason.LagRangeOutsideHorizon ->
        "The most recent qualifying rain ended ${TRIP_WINDOW_DATE_FORMAT.format(reason.mostRecentEventEnd)}. " +
            "The ${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
            "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} day window it points to is " +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.lagRangeStart)}–" +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.lagRangeEnd)}, past the " +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.horizonEnd)} horizon this search plans within."

    is NoTripWindowReason.NoForecastDays ->
        "No forecast days were returned for this location, so there's nothing to plan against."

    null -> "" // Unreachable: report.windows.isEmpty() implies a non-null reason.
}
