package com.forager.app.ui.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.FixFreshness
import com.forager.app.domain.GeoDistance
import com.forager.app.domain.LocationFix
import com.forager.app.domain.ageMillis
import com.forager.app.domain.fixFreshness
import com.forager.app.domain.isApproaching
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.formatDistanceMeters
import com.forager.app.domain.model.formatDistanceWithAccuracy
import com.forager.app.domain.relativeBearingDegrees
import com.forager.app.ui.map.TrueHeadingReading
import com.forager.app.ui.theme.Bark
import com.forager.app.ui.theme.LocalForagerDarkTheme
import com.forager.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal const val NAVIGATION_HUD_TAG = "navigation-hud"
internal const val NAVIGATION_HUD_EXIT_TAG = "navigation-hud-exit"
internal const val NAVIGATION_HUD_HEADING_TAG = "navigation-hud-heading"
internal const val NAVIGATION_HUD_DISTANCE_TAG = "navigation-hud-distance"
internal const val NAVIGATION_HUD_STATUS_TAG = "navigation-hud-status"
internal const val NAVIGATION_HUD_TARGET_TAG = "navigation-hud-target"
internal const val NAVIGATION_HUD_ELEVATION_TAG = "navigation-hud-elevation"
internal const val NAVIGATION_HUD_COORDINATES_TAG = "navigation-hud-coordinates"

/**
 * The one message the strip and the HUD both show when there is no fix. One cause, one statement —
 * not "compass needs a fix · elevation unavailable · coordinates unavailable", three fragments the
 * owner read on device as three separate breakages (navigation-chrome dispatch, item 4). Keyed on
 * the *missing fix*, never on [TrueHeadingReading.NeedsFix]: a phone with no magnetometer and no
 * fix reads [TrueHeadingReading.NoSensor] (`rememberTrueHeading` checks the sensor first), and is
 * still, first, a location problem.
 */
internal const val NO_FIX_MESSAGE = "Location services unavailable"

/**
 * Navigation HUD, stage one, consolidated by the navigation-chrome dispatch: a north compass and a
 * target compass, both in **true** north, the straight-line distance to the target, an exit, and
 * — since the compass strip hides while navigating — the strip's elevation and coordinates on a
 * second row, the coordinates still tappable to toggle MGRS and decimal degrees. The target is
 * the active track's origin waypoint; there is no picker in this stage.
 *
 * ## Where it mounts, and why
 *
 * A child of `CompactMapTab`'s own map `Box` — the same `fillMaxSize()` Box `mapSlot` lives in,
 * because chrome composed even one level outside it rendered fully opaque over the map's
 * `AndroidView` on a real device (see `searchBarSlot`'s own doc comment there). Inserted after
 * the taxon filter chip and **before** `ForagerBottomNav`: after the icon cluster so this panel's
 * own controls win any overlap with it, before the nav so the nav keeps winning its own band (a
 * later-composed nav once swallowed the picker's OK tap — that ordering is load-bearing). Top-
 * aligned, full width, wrapping its content, padded by `topInset` alone: the compass strip is not
 * composed while this is (`CompactMapTab` gates both on one `isNavigating`), so there is nothing
 * above this panel to clear except the search bar, and it tracks that bar's fullscreen slide the
 * way the strip does. It never wraps `mapSlot`, never touches the modifier passed to it, and
 * feeds nothing into `MapOverlayContent` or `MapRenderMode`, so no map effect keys on it and the
 * map is never animated, re-measured or re-fitted (`AvailabilityScreenLayoutTest`'s measured-
 * height guard, with a HUD-open case that now also enters and leaves navigation).
 *
 * ## The exit
 *
 * The close button is part of this panel, so it is reachable whenever the HUD is — independent of
 * the icon cluster, which can be minimised or dragged to an edge. It sits at the panel's right
 * end but the panel is composed after the cluster, so even a cluster dragged up to its upward
 * bound cannot cover it. The control pill's lit return toggle exits too — two direct exits, kept
 * deliberately (navigation-chrome dispatch, owner's ruling): the toggle is the entry and a lit
 * toggle that ignores a second tap would be worse; the close button is the one guaranteed
 * reachable. **System back is not an exit** (navigation-chrome amendment): it raises a prompt
 * that back can only dismiss — see `AvailabilityScreen`'s back chain and the reason recorded
 * there. Exit means `stopReturn()` — stage one's HUD *is* the return mode, see
 * `TrackRecordingViewModel.startReturn`.
 *
 * ## What it shows, and when
 *
 * - **Heading** comes from the one [TrueHeadingReading] the strip reads when it is showing, so
 *   the two can never disagree — and since the strip hides while this shows, the heading is on
 *   screen exactly once. The north compass's arrow is rotated by *minus* the heading (it points
 *   to true north relative to the way the device faces); the target compass's arrow by the
 *   bearing *relative to the heading* ([relativeBearingDegrees]) — where to turn, not an absolute
 *   bearing. Both arrows are device-relative, deliberately the same convention.
 * - **No sensor**: the north compass says so and the target compass shows nothing — no needle and
 *   no text. It used to fall back to the absolute true bearing as text; the two-data-corrections
 *   dispatch (Part C, owner decision) withdrew that: an absolute bearing the user cannot orient to
 *   is a number without a use whether the compass is absent or untrusted, the same reasoning the
 *   approach and unreliable cases already recorded. **Unreliable compass** (compass-reliability dispatch — the
 *   sensor is present and its reading is not to be trusted, decided upstream by
 *   [com.forager.app.domain.CompassTrustJudge] with hysteresis): the north compass reads "Compass
 *   unreliable" with its arrow unrotated, and the needle and its text are withheld, ranked between
 *   a lost fix and the approach threshold — see the precedence comment in [navigationReadout]. **No fix**: the heading label is a dash and the status line
 *   carries [NO_FIX_MESSAGE] — one message for one cause; the second row is not shown at all,
 *   since "elevation unavailable · coordinates unavailable" would be the same cause twice more.
 *   See [TrueHeadingReading.NeedsFix] for why not magnetic-until-then. No GPS-course fallback.
 * - **Distance** is straight-line from the current fix to the target, in the user's unit, and
 *   never more precise than the fix: "within 16 ft" inside the error circle (the "0 ft" the owner
 *   saw on device), "≈ 10 m" beyond it, plain formatting only when no accuracy was reported
 *   ([formatDistanceWithAccuracy], location-accuracy dispatch item 2). Never "arrived" —
 *   "Approaching" once inside twice the fix's reported accuracy ([isApproaching]). The fix itself
 *   has already passed the live-fix gate upstream ([com.forager.app.domain.acceptLiveFix]): a fix
 *   worse than 50 m never reaches this panel, and the held one ages into the stale states below.
 * - **The needle is not drawn inside that same threshold** (navigation-chrome dispatch, item 5,
 *   diagnosed on device by the owner). Bearing to a nearby point is geometrically unstable: at
 *   10 m with 8 m accuracy, ordinary GPS drift swings the computed bearing through tens of
 *   degrees, and no heading smoothing can fix it because the heading is not what is wrong. Inside
 *   the threshold the target column shows **nothing** under its dimmed icon — the HUD shows the
 *   distance once, in the distance slot, and "Approaching" (owner's call; a first cut put the
 *   distance in the column too and the owner read "9 ft · 9 ft" on device) — and the no-sensor
 *   bearing text is withheld too, since an absolute bearing you cannot orient to is a number
 *   without a use. One constant gates both the needle and the word:
 *   [isApproaching]. Two thresholds would drift, producing a needle that vanishes before the label
 *   appears or the reverse. The bearing is never smoothed as a substitute — a smoothed unstable
 *   bearing is a stable wrong direction.
 * - **Stale fix** ([fixFreshness], HUD-only policy): past 30 s the distance de-emphasises and its
 *   age is shown — "Approaching · last fix 45 s ago" when both hold, since neither fact replaces
 *   the other; past 5 min the distance and needle are withheld and only the age remains. A
 *   one-second ticker inside this leaf keeps the age moving; it recomposes this panel only.
 * - **Path home** (path-home join dispatch): while returning, the status line's otherwise-empty
 *   state carries one more short string — "Path home 350 m", the walk back along the recorded
 *   track joined to itself ([com.forager.app.domain.pathHome], via
 *   [com.forager.app.ui.track.TrackRecordingUiState.pathHome]). One number, no time, no mode
 *   toggle: the distance slot keeps the straight line, which is what the needle and "Approaching"
 *   are about; this line says how far the *walk* is. It yields to every message the line already
 *   carried — stale, lost, approaching — see [navigationReadout].
 * - **No origin waypoint** (a track whose first gated fix never came): says so. Nothing is
 *   substituted.
 * - **Elevation and coordinates** come from the same fix, through the same [coordinatesStripText]
 *   the strip uses; the MGRS/decimal choice is hoisted to `CompactMapTab` and shared with the
 *   strip, so a format chosen here survives leaving navigation (CLAUDE.md, UX defaults). The
 *   coordinates segment's tap band is the row's remaining width by ~24dp — a real target, not the
 *   bare text — because with the cluster minimised in fullscreen this toggle is one of only three
 *   reachable affordances on screen.
 *
 * The map stays live underneath — dimming was settled against, since it saves nothing.
 */
@Composable
internal fun NavigationHud(
    heading: State<TrueHeadingReading>,
    liveFix: LocationFix.Update?,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** See `AvailabilityScreen`'s own `pathHomeMeters` doc comment, and [navigationReadout] for where it shows. */
    pathHomeMeters: Double? = null,
) {
    // Read here, in this leaf, never higher — see rememberTrueHeading's own doc comment.
    val reading by heading
    // The age ticker: the fix's timestamp is fixed, the clock moves. Lives in this composable so
    // only this panel recomposes each second.
    val now by produceState(initialValue = currentTime.nowEpochMillis(), currentTime) {
        while (true) {
            delay(AGE_TICK_MILLIS)
            value = currentTime.nowEpochMillis()
        }
    }
    val isDarkTheme = LocalForagerDarkTheme.current
    val readout = navigationReadout(reading, liveFix, target, distanceUnit, now, showDecimalDegrees, pathHomeMeters)

    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        // A plain Box with a background, deliberately opaque to touches only where its content
        // is: this panel *is* chrome and wraps its own height, so what it covers is what it
        // shows — the same shape as the compass strip it replaces while navigating.
        Box(
            modifier = modifier
                .background(
                    color = if (isDarkTheme) CompassStripBackgroundColorDark else CompassStripBackgroundColorLight,
                    shape = RectangleShape,
                )
                .testTag(NAVIGATION_HUD_TAG),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // North compass.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = null,
                            modifier = Modifier
                                .size(COMPASS_ICON_SIZE)
                                .rotate(readout.northArrowDegrees ?: 0f),
                        )
                        Text(
                            text = readout.headingText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.testTag(NAVIGATION_HUD_HEADING_TAG),
                        )
                    }
                    // Target compass.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = null,
                            tint = if (readout.targetArrowDegrees != null) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(COMPASS_ICON_SIZE)
                                .rotate(readout.targetArrowDegrees ?: 0f),
                        )
                        Text(
                            text = readout.targetText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.testTag(NAVIGATION_HUD_TARGET_TAG),
                        )
                    }
                    // Distance and status.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = readout.distanceText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                            color = if (readout.distanceDeEmphasised) LocalContentColor.current.copy(alpha = 0.5f) else LocalContentColor.current,
                            maxLines = 1,
                            modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_TAG),
                        )
                        Text(
                            text = readout.statusText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.testTag(NAVIGATION_HUD_STATUS_TAG),
                        )
                    }
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.testTag(NAVIGATION_HUD_EXIT_TAG),
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop navigating")
                    }
                }
                // Second row: what the hidden strip was carrying that this panel was not. Present
                // only with a fix — without one the status line above already says the one thing
                // there is to say. The coordinates segment takes the row's remaining width as its
                // tap band, padded to ~24dp tall; the elevation is a plain readout.
                if (readout.coordinatesText != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = readout.elevationText.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.testTag(NAVIGATION_HUD_ELEVATION_TAG),
                        )
                        Text("·", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = readout.coordinatesText,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onToggleCoordinateFormat)
                                .padding(vertical = Spacing.xs)
                                .testTag(NAVIGATION_HUD_COORDINATES_TAG),
                        )
                    }
                }
            }
        }
    }
}

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
)

internal fun navigationReadout(
    heading: TrueHeadingReading,
    liveFix: LocationFix.Update?,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    showDecimalDegrees: Boolean = false,
    pathHomeMeters: Double? = null,
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
        TrueHeadingReading.NeedsFix -> "—"
    }
    val compassUnreliable = heading is TrueHeadingReading.Unreliable
    val northArrowDegrees = headingDegrees?.let { -it }
    val elevationText = liveFix?.let { fix -> fix.altitude?.let { "${it.roundToInt()} m" } ?: "Elevation unavailable" }
    val coordinatesText = liveFix?.let { coordinatesStripText(LatLng(it.lat, it.lng), showDecimalDegrees) }

    if (liveFix == null) {
        return NavigationHudReadout(headingText, northArrowDegrees, "Target", null, "—", false, NO_FIX_MESSAGE, null, null)
    }
    if (target == null) {
        return NavigationHudReadout(headingText, northArrowDegrees, "No target", null, "—", false, "No origin waypoint for this track", elevationText, coordinatesText)
    }

    val here = LatLng(liveFix.lat, liveFix.lng)
    val there = LatLng(target.lat, target.lng)
    val bearing = GeoDistance.initialBearingDegrees(here, there)
    val distanceMeters = GeoDistance.metersBetween(here, there)
    val age = liveFix.ageMillis(nowEpochMillis)
    val freshness = fixFreshness(age)
    // The one threshold — see the class doc's needle paragraph.
    val approaching = freshness != FixFreshness.LOST && isApproaching(distanceMeters, liveFix.accuracyMeters)
    // Never more precision than the fix supports — "within 16 ft" inside the error circle, "≈ 10 m"
    // beyond it, today's formatting when no accuracy was reported. See formatDistanceWithAccuracy.
    val distanceText = if (freshness == FixFreshness.LOST) "—" else formatDistanceWithAccuracy(distanceMeters, liveFix.accuracyMeters, distanceUnit)

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
    val targetArrowDegrees = if (headingDegrees != null && freshness != FixFreshness.LOST && !compassUnreliable && !approaching) relativeBearingDegrees(bearing, headingDegrees) else null
    val targetText = when {
        freshness == FixFreshness.LOST -> "Target"
        // Same rendering as the approach case below, and for the same reason the owner recorded
        // there: an absolute bearing you cannot orient to is a number without a use. Unreliable
        // leaves the user in that position.
        compassUnreliable -> ""
        // Nothing — not the distance (that was one number in two slots on device, "9 ft · 9 ft ·
        // Approaching"), not a dash, not a placeholder. The distance slot carries the one number.
        approaching -> ""
        headingDegrees != null -> "Turn ${relativeBearingDegrees(bearing, headingDegrees).roundToInt() % 360}°"
        // No sensor. This branch used to read "Bearing N° X" — the one state that still showed the
        // absolute bearing as text. The compass-reliability dispatch asked for the unreliable case
        // to match it and was wrong about what it did; the follow-up (two-data-corrections dispatch,
        // Part C, owner decision) brought no-sensor into line with approach and unreliable instead:
        // a number the user cannot orient to is withheld, the distance slot carries the one number.
        else -> ""
    }
    // Path-home join dispatch: the one more short string this line can carry (pre-build report,
    // §E) — the walk back along the track, joined to itself, as one number. Only in the state
    // where the line was empty: a stale or lost fix already owns the line with a message the
    // walker needs more, and an approaching walker is metres from the origin, where a second
    // small number beside "within 4 m" is the "9 ft · 9 ft" duplicate the owner struck. Plain
    // formatting, not the accuracy-aware kind: this is a sum over many stored points, not one
    // fix's radius. Never "arrived", never a time — the walking time has no caller, on purpose.
    val pathHomeText = pathHomeMeters?.let { "Path home ${formatDistanceMeters(it, distanceUnit)}" }
    val statusText = when (freshness) {
        FixFreshness.LOST -> "No fix for ${formatFixAge(age)}"
        FixFreshness.STALE -> if (approaching) "Approaching · last fix ${formatFixAge(age)} ago" else "Last fix ${formatFixAge(age)} ago"
        FixFreshness.FRESH -> if (approaching) "Approaching" else pathHomeText.orEmpty()
    }
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
    )
}

/** "48 s" under a minute, "6 min" from a minute on — coarse on purpose; the number's job is "old", not a stopwatch. */
internal fun formatFixAge(ageMillis: Long): String {
    val seconds = (ageMillis / 1_000L).coerceAtLeast(0L)
    return if (seconds < 60L) "$seconds s" else "${seconds / 60L} min"
}

private val COMPASS_ICON_SIZE = 22.dp
private const val AGE_TICK_MILLIS = 1_000L
