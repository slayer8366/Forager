package com.forager.app.ui.availability

import androidx.compose.foundation.background
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

/**
 * Navigation HUD, stage one: a north compass and a target compass, both in **true** north, the
 * straight-line distance to the target, and an exit. The target is the active track's origin
 * waypoint; there is no picker in this stage.
 *
 * ## Where it mounts, and why
 *
 * A child of `CompactMapTab`'s own map `Box` — the same `fillMaxSize()` Box `mapSlot` lives in,
 * because chrome composed even one level outside it rendered fully opaque over the map's
 * `AndroidView` on a real device (see `searchBarSlot`'s own doc comment there). Inserted after
 * the taxon filter chip and **before** `ForagerBottomNav`: after the icon cluster so this panel's
 * own controls win any overlap with it, before the nav so the nav keeps winning its own band (a
 * later-composed nav once swallowed the picker's OK tap — that ordering is load-bearing). Top-
 * aligned, full width, wrapping its content, padded by the strip's clearance so it sits directly
 * under the compass strip and tracks the search bar's fullscreen slide the way the strip does. It
 * never wraps `mapSlot`, never touches the modifier passed to it, and feeds nothing into
 * `MapOverlayContent` or `MapRenderMode`, so no map effect keys on it and the map is never
 * animated, re-measured or re-fitted (`AvailabilityScreenLayoutTest`'s measured-height guard,
 * now with a HUD-open case).
 *
 * ## The exit
 *
 * The close button is part of this panel, so it is reachable whenever the HUD is — independent of
 * the icon cluster, which can be minimised or dragged to an edge. It sits at the panel's right
 * end but the panel is composed after the cluster, so even a cluster dragged up to the strip's
 * clearance (its upward bound) cannot cover it. System back exits too (`CompactMapTab`'s own
 * `BackHandler`). Exit means `stopReturn()` — stage one's HUD *is* the return mode, see
 * `TrackRecordingViewModel.startReturn`.
 *
 * ## What it shows, and when
 *
 * - **Headings** come from one [TrueHeadingReading] the strip reads too, so they cannot disagree.
 *   The north compass's arrow is rotated by *minus* the heading (it points to true north relative
 *   to the way the device faces); the target compass's arrow by the bearing *relative to the
 *   heading* ([relativeBearingDegrees]) — where to turn, not an absolute bearing. Both arrows are
 *   device-relative, deliberately the same convention.
 * - **No sensor**: the north compass says so and the target compass falls back to the absolute
 *   true bearing as text, no needle. **No fix yet** (`NeedsFix`): "Compass needs a fix" — see
 *   [TrueHeadingReading.NeedsFix] for why not magnetic-until-then. No GPS-course fallback.
 * - **Distance** is straight-line from the current fix to the target, in the user's unit. Never
 *   "arrived" — "Approaching" once inside twice the fix's reported accuracy ([isApproaching]).
 * - **Stale fix** ([fixFreshness], HUD-only policy): past 30 s the distance de-emphasises and its
 *   age is shown; past 5 min the distance and needle are withheld and only the age remains. A
 *   one-second ticker inside this leaf keeps the age moving; it recomposes this panel only.
 * - **No origin waypoint** (a track whose first gated fix never came): says so. Nothing is
 *   substituted.
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
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
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
    val readout = navigationReadout(reading, liveFix, target, distanceUnit, now)

    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        // A plain Box with a background, deliberately opaque to touches only where its content
        // is: this panel *is* chrome and wraps its own height, so what it covers is what it
        // shows — the same shape as the compass strip above it.
        Box(
            modifier = modifier
                .background(
                    color = if (isDarkTheme) CompassStripBackgroundColorDark else CompassStripBackgroundColorLight,
                    shape = RectangleShape,
                )
                .testTag(NAVIGATION_HUD_TAG),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
        }
    }
}

/** Everything the HUD draws, as plain values — the pure half, so a sign or threshold error is a pinned-literal test failure, not a visual one. */
internal data class NavigationHudReadout(
    val headingText: String,
    /** Rotation for the north arrow, device-relative; `null` when there is no heading to draw from. */
    val northArrowDegrees: Float?,
    val targetText: String,
    /** Rotation for the target arrow, device-relative; `null` when there is no needle to draw. */
    val targetArrowDegrees: Float?,
    val distanceText: String,
    val distanceDeEmphasised: Boolean,
    val statusText: String,
)

internal fun navigationReadout(
    heading: TrueHeadingReading,
    liveFix: LocationFix.Update?,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
): NavigationHudReadout {
    val headingDegrees = (heading as? TrueHeadingReading.Available)?.degrees
    val headingText = when (heading) {
        is TrueHeadingReading.Available -> "${heading.degrees.roundToInt() % 360}° ${cardinalDirection(heading.degrees)}"
        TrueHeadingReading.NoSensor -> "Compass unavailable"
        TrueHeadingReading.NeedsFix -> "Compass needs a fix"
    }
    val northArrowDegrees = headingDegrees?.let { -it }

    if (target == null) {
        return NavigationHudReadout(headingText, northArrowDegrees, "No target", null, "—", false, "No origin waypoint for this track")
    }
    if (liveFix == null) {
        return NavigationHudReadout(headingText, northArrowDegrees, "Target", null, "—", false, "Waiting for a fix")
    }

    val here = LatLng(liveFix.lat, liveFix.lng)
    val there = LatLng(target.lat, target.lng)
    val bearing = GeoDistance.initialBearingDegrees(here, there)
    val distanceMeters = GeoDistance.metersBetween(here, there)
    val age = liveFix.ageMillis(nowEpochMillis)
    val freshness = fixFreshness(age)

    val targetArrowDegrees = if (headingDegrees != null && freshness != FixFreshness.LOST) relativeBearingDegrees(bearing, headingDegrees) else null
    val targetText = when {
        freshness == FixFreshness.LOST -> "Target"
        headingDegrees != null -> "Turn ${relativeBearingDegrees(bearing, headingDegrees).roundToInt() % 360}°"
        else -> "Bearing ${bearing.roundToInt() % 360}° ${cardinalDirection(bearing.toFloat())}"
    }
    val distanceText = if (freshness == FixFreshness.LOST) "—" else formatDistanceMeters(distanceMeters, distanceUnit)
    val statusText = when (freshness) {
        FixFreshness.LOST -> "No fix for ${formatFixAge(age)}"
        FixFreshness.STALE -> "Last fix ${formatFixAge(age)} ago"
        FixFreshness.FRESH -> if (isApproaching(distanceMeters, liveFix.accuracyMeters)) "Approaching" else ""
    }
    return NavigationHudReadout(
        headingText = headingText,
        northArrowDegrees = northArrowDegrees,
        targetText = targetText,
        targetArrowDegrees = targetArrowDegrees,
        distanceText = distanceText,
        distanceDeEmphasised = freshness == FixFreshness.STALE,
        statusText = statusText,
    )
}

/** "48 s" under a minute, "6 min" from a minute on — coarse on purpose; the number's job is "old", not a stopwatch. */
internal fun formatFixAge(ageMillis: Long): String {
    val seconds = (ageMillis / 1_000L).coerceAtLeast(0L)
    return if (seconds < 60L) "$seconds s" else "${seconds / 60L} min"
}

private val COMPASS_ICON_SIZE = 22.dp
private const val AGE_TICK_MILLIS = 1_000L
