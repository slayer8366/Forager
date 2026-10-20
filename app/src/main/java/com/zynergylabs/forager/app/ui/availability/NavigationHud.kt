package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Box
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapChromeOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.fixFreshness
import com.zynergylabs.forager.app.domain.isApproaching
import com.zynergylabs.forager.app.domain.isInApproachingZone
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import com.zynergylabs.forager.app.domain.relativeBearingDegrees
import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.turnText
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme

/**
 * Navigation HUD, stage one, consolidated by the navigation-chrome dispatch: a north compass and a
 * target compass, both in **true** north, the straight-line distance to the target, an exit, and
 * — since the compass strip hides while navigating — the strip's elevation and coordinates on a
 * second row, the coordinates still tappable to toggle MGRS and decimal degrees. The target is
 * the active track's origin waypoint; there is no picker in this stage.
 *
 * ## The card (dispatch 2026-10-12-06, rearranged by dispatch 2026-10-12-08)
 *
 * Since dispatch 2026-10-12-08 (RECORD -945, -948, -950, -951) the compass strip stays while navigating and its line drops the
 * north arrow and the heading; the navigation info ([NavigationInfo]) shows the distance, the turn line with both arrows and the
 * heading, the kind word, the evening stack and the X. In portrait it is inside the strip's own card, under a thin line; in
 * landscape it is this composable's card, hanging under the strip at the strip's width. Height, the grid reference and the
 * three-dot button are the strip's. Parts of the description below that speak of rows, of elevation and coordinates on this
 * display, or of the strip being hidden while navigating describe the layout before those dispatches; the readout itself
 * ([navigationReadout]) is unchanged apart from [NavigationHudReadout.statusIsPositionStatus].
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
 * - **Words and labels** (dispatch 2026-09-28-677, data part B; the owner, RECORD -656, "Plain words + labels"): the turn
 *   under the target arrow reads "Slight left · 10°" ([turnText], bands in `domain/NavigationWords.kt`), where it read
 *   "Turn 350°"; the large figure is followed by what it measures, "by trail" or "straight" ([DistanceKind]); the heading
 *   and the elevation carry short labels ([ALTITUDE_LABEL]; the heading's "Facing" was removed by RECORD -728). The heading moved from under the north arrow
 *   to the second row to make room: at 360 dp a labelled heading, the longest turn words and a distance with its kind
 *   cannot share the first row (widths in `docs/ui/2026-10-07-data-b-track-nav-report.md`).
 * - **No sensor**: the north compass says so and the target compass shows nothing — no needle and
 *   no text. It used to fall back to the absolute true bearing as text; the two-data-corrections
 *   dispatch (Part C, owner decision) withdrew that: an absolute bearing the user cannot orient to
 *   is a number without a use whether the compass is absent or untrusted, the same reasoning the
 *   approach and unreliable cases already recorded. **Unreliable compass** (compass-reliability dispatch — the
 *   sensor is present and its reading is not to be trusted, decided upstream by
 *   [com.zynergylabs.forager.app.domain.CompassTrustJudge] with hysteresis): the north compass reads "Compass
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
 *   has already passed the live-fix gate upstream ([com.zynergylabs.forager.app.domain.acceptLiveFix]): a fix
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
 *   **Superseded for the word by dispatch 2026-09-28-578 (Amendment 1, RECORD -579):** arrival's
 *   radius always held [isApproaching], so "Approaching" never showed without "Arrived". The word now
 *   has its own zone, within 100 m and not arrived ([isInApproachingZone]), where the needle still
 *   points; arrived, "Arrived" stands alone and the status line is empty, or the fix's age when stale.
 *   [isApproaching] still gates the needle and the target column, as above.
 * - **Stale fix** ([fixFreshness], HUD-only policy): past 30 s the distance de-emphasises and its
 *   age is shown — "Approaching · last fix 45 s ago" when both hold, since neither fact replaces
 *   the other; past 5 min the distance and needle are withheld and only the age remains. A
 *   one-second ticker inside this leaf keeps the age moving; it recomposes this panel only.
 * - **The route home** (dispatch 2026-09-28-423, plan task T6; decisions D1, D3 and D5), given as
 *   [ReturnRoute] while returning. The needle aims at the route's lookahead point, a point of the
 *   walked route, not straight at the start. The large figure is the distance home **along the
 *   route** (D3, option B), in plain formatting: a sum over many stored points, not one fix's
 *   radius. The status line's otherwise-empty state carries the straight line to the start,
 *   labelled "Straight line" so the two figures cannot be confused, with the accuracy-aware
 *   formatting that belongs to it. It yields to the messages that line already carried (stale,
 *   lost, approaching), as "Path home" did before it. A withheld route reads "Unable to calculate
 *   route" in the large slot (the owner's words) with no needle, and offers "Try again" only where
 *   recomputing could change the answer. Before the first route result the large slot is a dash
 *   (the planner's ruling on question 3). "Approaching" is still measured against the start
 *   itself. With no [ReturnRoute] at all the HUD is the straight-line HUD it was before, which
 *   decision D2 keeps for navigating to a waypoint: since dispatch 2026-09-28-502 that is how
 *   `CompactMapTab` passes it while a chosen waypoint is the target. "Arrived" reads in either mode.
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
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** The route home while returning; see this composable's "The route home" and [ReturnRoute]. */
    route: ReturnRoute? = null,
    /** "Try again": recompute the route now. Offered only when [NavigationHudReadout.routeRetryOffered]. */
    onRetryRoute: () -> Unit = {},
    /**
     * Which way the map faces while navigating (dispatch 2026-09-28-430). While a stuck compass is
     * retried, and once the map has turned north-up, the heading label says so; see [navigationReadout].
     */
    facing: NavigationFacing = NavigationFacing.FACING_UP,
    /**
     * Dispatch 2026-09-28-510: the newest reading the live gate refused, and the platform's last known
     * location. Shown in place of the GPS fix only by [hudReadout]'s rule; never measured from for
     * "Arrived", which reads [liveFix] alone.
     */
    approximateFix: LocationFix.Update? = null,
    lastKnownFix: LocationFix.Update? = null,
    /**
     * The sundown line (dispatch 2026-09-28-592, plan task T3): the recording's, moved here from the
     * strip once Return or Navigate is tapped, or, navigating with no recording, sunset and dark only.
     * `null` draws nothing.
     */
    sundownLine: String? = null,
    /** Back by's line (dispatch 2026-09-28-645), under the sundown line; `null` draws nothing. */
    backByLine: String? = null,
    /**
     * RECORD -770 (the owner: "Drop the evening line then (Recommended)"): the lowest the display may end, in the root, while the
     * landscape L is below it, so that the bar is covered by no more than its Fullscreen row; `null` puts no limit. Where the
     * evening lines would take the display past it, they leave the display ([eveningLineFits]); the alerts are unaffected.
     */
    eveningLineBottomLimitInRootPx: Float? = null,
    /**
     * RECORD -955 ("C yes", extending -770 to the landscape card on the bottom edge, RECORD -953): the highest the card's top may
     * reach, in the root, so that it covers neither Reset north nor the walker's dot; `null` puts no limit. Where the evening
     * lines would take the card's top above it, they leave the card ([eveningStackFitsBelow]); the alerts are unaffected.
     */
    eveningLineTopLimitInRootPx: Float? = null,
    /**
     * RECORD -844, -845: the return has ended on arrival and the display holds "Arrived" until it closes by
     * itself. The large slot reads "Arrived" whatever the fix says ([heldArrivalReadout]), so a drive-off
     * inside the hold does not turn it back into a distance.
     */
    arrivalHeld: Boolean = false,
    /** Dispatch 2026-10-12-06: the rounded card's corners, and how far its fill reaches up behind the piece above (RoundedMapCard.kt). */
    cardCorners: MapCardCorners = MapCardCorners.BOTTOM,
    tuck: Dp = 0.dp,
    /** Dispatch 2026-10-12-08: the corners of the piece the card is tucked under (the landscape compass strip). */
    upperCorners: MapCardCorners = MapCardCorners.ALL,
    /** Dispatch 2026-10-13-02 (RECORD -966, -967): the card's outlined sides ([landscapeColumnOutlineSides]); `null` draws none. */
    outlineSides: MapChromeOutlineSides? = null,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        val fill = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
        // The rounded card on its own: in landscape, hanging under the compass strip at its width (RECORD -946, -950, -951), and
        // wherever a test composes the display alone. Still a plain background with no pointer input (RoundedMapCard.kt): what
        // covers the map is what it shows, and only its controls (the X and Try again) take touches.
        Box(
            modifier = modifier
                .mapCardSurface(fill = fill, corners = cardCorners, tuck = tuck, upperCorners = upperCorners, outline = mapChromeOutline(), outlineSides = outlineSides)
                .testTag(NAVIGATION_HUD_TAG)
                .mapChromeContainerColor(fill),
        ) {
            NavigationInfo(
                heading = heading,
                liveFix = liveFix,
                target = target,
                distanceUnit = distanceUnit,
                currentTime = currentTime,
                showDecimalDegrees = showDecimalDegrees,
                onExit = onExit,
                route = route,
                onRetryRoute = onRetryRoute,
                facing = facing,
                approximateFix = approximateFix,
                lastKnownFix = lastKnownFix,
                sundownLine = sundownLine,
                backByLine = backByLine,
                eveningLineBottomLimitInRootPx = eveningLineBottomLimitInRootPx,
                eveningLineTopLimitInRootPx = eveningLineTopLimitInRootPx,
                arrivalHeld = arrivalHeld,
            )
        }
    }
}
