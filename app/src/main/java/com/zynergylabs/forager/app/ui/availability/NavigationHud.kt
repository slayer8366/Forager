package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.WordSwap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.IntSize
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import com.zynergylabs.forager.app.domain.hasArrived
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixFreshness
import com.zynergylabs.forager.app.domain.LOST_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.STALE_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteWithheldReason
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
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.COMPASS_NORTH_UP_TEXT
import com.zynergylabs.forager.app.ui.map.COMPASS_CALIBRATING_TEXT
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
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
internal const val NAVIGATION_HUD_RETRY_TAG = "navigation-hud-retry"
internal const val NAVIGATION_HUD_DISTANCE_KIND_TAG = "navigation-hud-distance-kind"
/** The needle: the target compass's arrow (T11 fixes, so a test can say nothing covers it). */
internal const val NAVIGATION_HUD_NEEDLE_TAG = "navigation-hud-needle"
/** The landscape display's one evening line (T11 fixes). Not drawn since RECORD -940, which lays landscape out as portrait; kept so a test can say so. */
internal const val NAVIGATION_HUD_EVENING_LINE_TAG = "navigation-hud-evening-line"
/** Every [LabelledReadout]'s label, on the display and the strip alike: a test finds them by text within a parent. */
internal const val NAVIGATION_LABEL_TAG = "navigation-label"
/** Dispatch 2026-10-12-06: the compass arrow inline before the heading (or the crossed-out compass in its place), so a test can say it sits beside the needle. */
internal const val NAVIGATION_HUD_NORTH_ARROW_TAG = "navigation-hud-north-arrow"
/** RECORD -941: each piece of the evening stack in the card's right column. */
internal const val NAVIGATION_HUD_EVENING_PIECE_TAG = "navigation-hud-evening-piece"
/** Dispatch 2026-10-12-06: each of the card's two column dividers. */
internal const val NAVIGATION_HUD_DIVIDER_TAG = "navigation-hud-divider"

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
 * ## The card (dispatch 2026-10-12-06)
 *
 * Since dispatch 2026-10-12-06 (RECORD -932, -934, -935) the display is a rounded card tucked under the search pill
 * (RoundedMapCard.kt), in three columns with thin dividers ([CardColumns]): the figure and its kind over the turn words and the
 * needle; "Alt" over the compass arrow and the heading; the grid reference over a short evening line. The X sits above the
 * three-dot button at the right edge. Under the columns, each only when it has something to say: the evening lines that do
 * not fit the right column, the route message with "Try again", and the status line, which keeps everything it carried
 * before (RECORD -935). What gives way when the columns do not fit is [cardColumnsFit]'s, in the owner's order. Parts of the
 * description below that speak of a first and second row describe the layout before that dispatch; the readout itself
 * ([navigationReadout]) is unchanged.
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
    onToggleCoordinateFormat: () -> Unit,
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
     * Its own last row; `null` draws nothing.
     */
    sundownLine: String? = null,
    /** Back by's line (dispatch 2026-09-28-645), under the sundown line, as on the strip it replaces; `null` draws nothing. */
    backByLine: String? = null,
    /**
     * The map's quick settings (dispatch 2026-09-28-645, Amendment 2, the owner: "Gear in nav display
     * too (Recommended)"; Amendment 3, RECORD -648, made it the three-dot button at 36 dp): the strip's
     * button, here just left of the exit, since the strip is hidden while navigating and the way back is
     * when "+30 min" matters. `null` draws none.
     */
    quickSettings: MapQuickSettings? = null,
    /**
     * T11 fixes (RECORD -766, P3): the display is in a landscape window, beside the search bar. Since RECORD -940 the card lays
     * out exactly as in portrait, so nothing reads this for layout any more; kept for the call site's record of where it is.
     */
    landscape: Boolean = false,
    /**
     * RECORD -770 (the owner: "Drop the evening line then (Recommended)"): the lowest the display may end, in the root, while the
     * landscape L is below it, so that the bar is covered by no more than its Fullscreen row; `null` puts no limit. Where the
     * evening line would take the display past it, the line leaves the display ([eveningLineFits]); the alerts are unaffected.
     */
    eveningLineBottomLimitInRootPx: Float? = null,
    /**
     * RECORD -844, -845: the return has ended on arrival and the display holds "Arrived" until it closes by
     * itself. The large slot reads "Arrived" whatever the fix says ([heldArrivalReadout]), so a drive-off
     * inside the hold does not turn it back into a distance.
     */
    arrivalHeld: Boolean = false,
    /** Dispatch 2026-10-12-06: the rounded card's corners, and how far its fill reaches up behind the pill (RoundedMapCard.kt). */
    cardCorners: MapCardCorners = MapCardCorners.BOTTOM,
    tuck: Dp = 0.dp,
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
    val liveReadout = hudReadout(reading, liveFix, approximateFix, lastKnownFix, target, distanceUnit, now, showDecimalDegrees, route, facing)
    val readout = if (arrivalHeld) heldArrivalReadout(liveReadout) else liveReadout

    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        val fill = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
        // Dispatch 2026-10-12-06 (RECORD -932, -934, -935): the rounded card, tucked under the pill in portrait. Still a plain
        // background with no pointer input (RoundedMapCard.kt): what covers the map is what it shows, and only its controls (the
        // X, the three-dot button, the grid reference and Try again) take touches; its margins and corner gaps reach the map.
        Box(
            modifier = modifier
                .mapCardSurface(fill = fill, corners = cardCorners, tuck = tuck)
                .testTag(NAVIGATION_HUD_TAG)
                .mapChromeContainerColor(fill),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val metrics = rememberCardMetrics()
                val cardPx = constraints.maxWidth
                val columnsPx = cardPx - metrics.fixedPx
                val fit = measuredCardColumnsFit(readout, columnsPx, metrics, sundownLine, backByLine)
                // RECORD -770: where the rows above the evening line end, which the line does not change, so the decision to leave
                // it out cannot feed back into what it is decided on.
                var rowsBottomInRootPx by remember { mutableFloatStateOf(Float.NaN) }
                var columnsTopInRootPx by remember { mutableFloatStateOf(Float.NaN) }
                var linesBelowHeightPx by remember { mutableFloatStateOf(0f) }
                // The X and the three-dot button stand in their own column at the card's right edge, beside the columns and the
                // lines under them, so the card is as tall as the taller of the two, not the sum (the landscape display's height
                // against the window's central third: Landscape B2, S10).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // RECORD -936 (the owner: "Yes, slightly tighter (Recommended)"): 4 dp at the card's sides.
                        // RECORD -943: the card's top padding under the tuck is trimmed to 2 dp to match the thinner pill.
                        .padding(start = CARD_SIDE_PADDING, end = CARD_SIDE_PADDING, top = CARD_TOP_PADDING, bottom = Spacing.xs),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // RECORD -941 (the owner: "Then stack it and don't fit the full length silly"): the evening lines sit in the
                        // right column under the grid reference, in short pieces, and the card grows for them. RECORD -770 still holds
                        // where a limit is given: the pieces stay only while the display, with them, ends within it. Worked out from
                        // parts that do not depend on the pieces (the columns' top, the lines under them), so leaving cannot feed
                        // back into the decision.
                        val pieces = eveningPieces(sundownLine, backByLine)
                        val eveningMeasurer = rememberTextMeasurer()
                        val eveningStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                        val pieceHeightPx = eveningMeasurer.measure("Sunset", eveningStyle, maxLines = 1).size.height
                        val bottomPaddingPx = with(LocalDensity.current) { Spacing.xs.toPx() }
                        val columnsHeightPx = metrics.topRowPx + metrics.bottomRowPx
                        val stackTopPx = if (fit.gridOwnLine) 0 else metrics.topRowPx
                        val withStackPx = maxOf(columnsHeightPx, stackTopPx + pieces.size * pieceHeightPx)
                        val eveningShown = pieces.isNotEmpty() && eveningLineFits(
                            columnsTopInRootPx + linesBelowHeightPx,
                            withStackPx.toFloat(),
                            bottomPaddingPx,
                            eveningLineBottomLimitInRootPx,
                        )
                        Column(modifier = Modifier.onGloballyPositioned { rowsBottomInRootPx = it.positionInRoot().y + it.size.height }) {
                            CardColumns(
                                readout = readout,
                                fit = fit,
                                metrics = metrics,
                                sundownLine = sundownLine.takeIf { eveningShown },
                                backByLine = backByLine.takeIf { eveningShown },
                                onToggleCoordinateFormat = onToggleCoordinateFormat,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { columnsTopInRootPx = it.positionInRoot().y },
                            )
                            // The lines under the columns, each only while it has something to say (RECORD -935, the owner: "Yes, keep
                            // everything"): the grid reference's own line when it has left the right column (RECORD -936), then the
                            // route and the status.
                            Column(modifier = Modifier.onGloballyPositioned { linesBelowHeightPx = it.size.height.toFloat() }) {
                                if (fit.gridOwnLine) readout.coordinatesText?.let { GridReferenceText(it, onToggleCoordinateFormat) }
                                CardRouteAndStatusLines(readout, fit, columnsPx, onRetryRoute)
                            }
                        }
                    }
                    // The owner (RECORD -932): "Have the X button go above the three dot menu in the top corner. The three dot menu can
                    // go in the bottom corner". Each keeps its own touch square: the X Material's icon button, the three dots RECORD
                    // -648's 36 dp.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BouncingIconButton(
                            onClick = onExit,
                            modifier = Modifier.testTag(NAVIGATION_HUD_EXIT_TAG),
                        ) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop navigating")
                        }
                        if (quickSettings != null) MapQuickSettingsButton(quickSettings)
                    }
                }
            }
        }
    }
}

/**
 * Dispatch 2026-10-12-06: the card's fixed parts, in pixels at this density and font. [fixedPx] is what the card takes from
 * its width before the three columns: its padding and the X and three-dot column (the dividers are the fit's).
 */
internal class CardMetrics(
    val paddingPx: Int,
    val fixedPx: Int,
    val dividerPx: Int,
    val gapPx: Int,
    val arrowPx: Int,
    val iconPx: Int,
    val topRowPx: Int,
    val bottomRowPx: Int,
)

@Composable
private fun rememberCardMetrics(): CardMetrics {
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    return with(density) {
        val small = measurer.measure("Mg", typography.labelMedium, maxLines = 1).size.height
        val figure = measurer.measure("Mg", cardFigureStyle(), maxLines = 1).size.height
        val padding = 2 * CARD_SIDE_PADDING.roundToPx()
        val divider = CARD_DIVIDER_WIDTH.roundToPx() + 2 * CARD_DIVIDER_GAP.roundToPx()
        CardMetrics(
            paddingPx = padding,
            // No gap before the X: its icon button carries its own padding around the icon (the landscape display at font 2.0
            // needed those 4 dp for the grid reference's own line, 239.5 dp, to stay whole at 780 dp).
            fixedPx = padding + HUD_EXIT_TOUCH_SIZE.roundToPx(),
            dividerPx = divider,
            gapPx = Spacing.xs.roundToPx(),
            arrowPx = COMPASS_ICON_SIZE.roundToPx(),
            iconPx = COMPASS_ICON_SIZE.roundToPx(),
            // The grid reference's tap band (its line and Spacing.xs above and below) is the tallest top part with a fix.
            topRowPx = maxOf(figure, small + 2 * Spacing.xs.roundToPx()),
            bottomRowPx = maxOf(COMPASS_ICON_SIZE.roundToPx(), small),
        )
    }
}

@Composable
private fun cardFigureStyle(): TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/**
 * The figure the left column draws: the route message moves to its own line under the columns (see [CardRouteAndStatusLines]);
 * with [short], "within 16 ft" reads "≤ 16 ft" (RECORD -936).
 */
private fun columnFigure(readout: NavigationHudReadout, short: Boolean = false): String = when {
    readout.distanceText == ROUTE_UNAVAILABLE_TEXT -> NO_HEADING_TEXT
    short -> distanceShortForm(readout.distanceText) ?: readout.distanceText
    else -> readout.distanceText
}

@Composable
private fun measuredCardColumnsFit(
    readout: NavigationHudReadout,
    columnsPx: Int,
    metrics: CardMetrics,
    sundownLine: String?,
    backByLine: String?,
): CardColumnsFit {
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium
    val evening = small.copy(fontFeatureSettings = "tnum")
    val figureStyle = cardFigureStyle()
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    val turn = readout.targetText
    val elevation = readout.elevationText
    return cardColumnsFit(
        CardColumnsWidths(
            availablePx = columnsPx,
            dividerPx = metrics.dividerPx,
            gapPx = metrics.gapPx,
            arrowPx = metrics.arrowPx,
            figurePx = widthOf(columnFigure(readout), figureStyle),
            figureShortPx = distanceShortForm(readout.distanceText)?.let { widthOf(it, figureStyle) },
            kindPx = readout.distanceKindText?.let { widthOf(it, small) },
            turnWholePx = turn.takeIf { it.isNotEmpty() }?.let { widthOf(it, small) },
            turnShortPx = turnShortForm(turn)?.let { widthOf(it, small) },
            altitudeLabelPx = elevation?.takeIf { it != ELEVATION_UNAVAILABLE_TEXT }?.let { widthOf(ALTITUDE_LABEL, small) },
            altitudePx = elevation?.let { widthOf(it, small) },
            headingPx = widthOf(readout.headingText, small),
            headingIsStatus = !readout.headingIsReading,
            headingIconPx = metrics.iconPx,
            coordinatesPx = readout.coordinatesText?.let { widthOf(it, small) },
            eveningPiecePx = eveningPieces(sundownLine, backByLine).maxOfOrNull { widthOf(it, evening) },
        ),
        turnWhole = turn,
        turnShort = turnShortForm(turn),
    ).let { fit -> if (fit.overflows) fit.also { android.util.Log.w(CARD_LOG_TAG, "navigation card columns overflow at $columnsPx px: $fit") } else fit }
}

/**
 * The three columns (dispatch 2026-10-12-06; the owner's mockup and step path, RECORD -932): left, the figure and its kind over
 * the turn words and the needle; middle, "Alt" over the compass arrow and the heading; right, the grid reference over a short
 * evening line. The owner, RECORD -932 and again on 2026-10-12: "Have the altitude and the north compass switch sides so both
 * arrows can be side by side"; "The big part is to arrange it so both arrows are side by side". So the left column's lower row
 * ends at the divider with the needle, and the middle column's lower row starts at it with the compass arrow, and both lower
 * rows are one fixed height under one fixed-height upper row, so the two arrows share a line in every state the fit produces.
 */
@Composable
private fun CardColumns(
    readout: NavigationHudReadout,
    fit: CardColumnsFit,
    metrics: CardMetrics,
    sundownLine: String?,
    backByLine: String?,
    onToggleCoordinateFormat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val small = MaterialTheme.typography.labelMedium
    val topRow = with(density) { metrics.topRowPx.toDp() }
    val bottomRow = with(density) { metrics.bottomRowPx.toDp() }
    val distanceColor = if (readout.distanceDeEmphasised) LocalContentColor.current.copy(alpha = 0.5f) else LocalContentColor.current
    val dividerColor = if (LocalForagerDarkTheme.current) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT
    // Top-aligned: the right column grows with the evening stack (RECORD -941) while the left and middle keep their two fixed
    // rows, so the two arrows stay on one line whatever the stack's height.
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        // Left: the figure (and its kind while it fits), then the turn words and the needle, flush against the divider.
        Column(horizontalAlignment = Alignment.End) {
            Row(modifier = Modifier.height(topRow), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                WordSwap(text = columnFigure(readout, fit.figureShort), modifier = Modifier.alignByBaseline()) { shown ->
                    Text(
                        text = shown,
                        style = cardFigureStyle(),
                        color = distanceColor,
                        maxLines = 1,
                        softWrap = false,
                        // The route message's own tag is on its line when it moves there; the dash here is the column's.
                        modifier = if (readout.distanceText == ROUTE_UNAVAILABLE_TEXT) Modifier else Modifier.testTag(NAVIGATION_HUD_DISTANCE_TAG),
                    )
                }
                if (fit.kindInColumn) readout.distanceKindText?.let { kind -> DistanceKindText(kind, distanceColor, Modifier.alignByBaseline()) }
            }
            Row(modifier = Modifier.height(bottomRow), verticalAlignment = Alignment.CenterVertically) {
                // Always composed, empty when there is no turn to word (the approach, no compass, no aim), as before: the gap
                // before the needle only when there are words.
                WordSwap(text = fit.turnText, contentAlignment = Alignment.CenterEnd) { shown ->
                    Text(
                        text = shown,
                        style = small,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(end = if (shown.isEmpty()) 0.dp else Spacing.xs)
                            .testTag(NAVIGATION_HUD_TARGET_TAG),
                    )
                }
                // The needle stays whatever the words beside it do (T11 fixes), dimmed when there is nothing to point at. Its tag
                // is on the unrotated square it is laid out in, so its bounds are where it sits, not its rotated drawing's box.
                Box(modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NEEDLE_TAG)) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = if (readout.targetArrowDegrees != null) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(COMPASS_ICON_SIZE)
                            .rotate(readout.targetArrowDegrees ?: 0f),
                    )
                }
            }
        }
        CardDivider(height = topRow + bottomRow, color = dividerColor)
        // Middle: the altitude, then the compass arrow and the heading, the arrow flush against the divider.
        Column(horizontalAlignment = Alignment.Start) {
            Row(modifier = Modifier.height(topRow), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (fit.headingAsIcon) {
                    // RECORD -934: a compass status whose words do not fit the middle column takes RECORD -735's crossed-out
                    // compass, its words kept as the icon's description for a screen reader.
                    // The status's own words as its description, so a facing notice ("Compass calibrating…") is read as itself.
                    Icon(
                        imageVector = Icons.Filled.ExploreOff,
                        contentDescription = readout.headingText,
                        modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NORTH_ARROW_TAG),
                    )
                } else {
                    // The north compass, inline before the heading (RECORD -932): rotated by minus the heading, as before. Tagged
                    // on its unrotated square, as the needle is.
                    Box(modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NORTH_ARROW_TAG)) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = null,
                            modifier = Modifier
                                .size(COMPASS_ICON_SIZE)
                                .rotate(readout.northArrowDegrees ?: 0f),
                        )
                    }
                    // Motion Part 2, item 6 (RECORD -651: "Numbers instant, words fade"). With no fix and only the dash to say,
                    // the heading is not drawn, as the second row was not (NavigationHudReadout.secondRowShown): the status line
                    // carries NO_FIX_MESSAGE once.
                    if (readout.secondRowShown) WordSwap(text = readout.headingText) { shown ->
                        Text(
                            text = shown,
                            style = small.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag(NAVIGATION_HUD_HEADING_TAG),
                        )
                    }
                }
            }
            Row(modifier = Modifier.height(bottomRow), verticalAlignment = Alignment.CenterVertically) {
                if (fit.altitudeShown && readout.elevationText != null) {
                    val label = ALTITUDE_LABEL.takeIf { fit.altitudeLabel && readout.elevationText != ELEVATION_UNAVAILABLE_TEXT }
                    LabelledReadout(label = label) {
                        Text(
                            text = readout.elevationText,
                            style = small,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag(NAVIGATION_HUD_ELEVATION_TAG),
                        )
                    }
                }
            }
        }
        val pieces = eveningPieces(sundownLine, backByLine)
        val hasRightColumn = !fit.gridOwnLine || pieces.isNotEmpty()
        if (hasRightColumn) CardDivider(height = topRow + bottomRow, color = dividerColor)
        // Right: the grid reference, its tap band the column's whole width (a real target, not the bare text), then the evening
        // stack under it (RECORD -941), which stays in this column when the grid reference has gone to its own line (RECORD -936).
        if (!hasRightColumn) {
            Spacer(Modifier.weight(1f))
        } else {
            Column(modifier = Modifier.weight(1f)) {
                if (!fit.gridOwnLine) {
                    Box(modifier = Modifier.height(topRow).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        readout.coordinatesText?.let { GridReferenceText(it, onToggleCoordinateFormat) }
                    }
                }
                EveningStack(sundownLine, backByLine)
            }
        }
    }
}

/**
 * The evening lines broken at their " · " joins into short pieces, the sundown line's first, then Back by's (RECORD -941: "Sunset
 * 12:41 AM", "in 2 h 28 min", "dark 1:13", "Back by …").
 */
internal fun eveningPieces(sundownLine: String?, backByLine: String?): List<String> =
    listOfNotNull(sundownLine, backByLine).flatMap { line -> line.split(" · ").map(String::trim).filter(String::isNotEmpty) }

/**
 * RECORD -941: the evening lines stacked in the right column, one piece per line, never ending in "…" (a piece wider than the
 * column wraps; the fit reserves the widest piece's width, so it only wraps where the fit reports an overflow). Each line keeps
 * its own tag on a column whose text is the whole line, so it reads as it did; each piece carries [NAVIGATION_HUD_EVENING_PIECE_TAG].
 * Each line fades and grows as before (RECORD -672), or only fades under reduced motion.
 */
@Composable
private fun EveningStack(sundownLine: String?, backByLine: String?) {
    val reduceMotion = LocalReduceMotion.current
    val lineFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val lineGrow = MotionTokens.mapPopUpGrowSpec<IntSize>()
    val enter = if (reduceMotion) fadeIn(animationSpec = lineFade) else fadeIn(animationSpec = lineFade) + expandVertically(animationSpec = lineGrow, expandFrom = Alignment.Top)
    val exit = if (reduceMotion) fadeOut(animationSpec = lineFade) else fadeOut(animationSpec = lineFade) + shrinkVertically(animationSpec = lineGrow, shrinkTowards = Alignment.Top)
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    for ((line, tag) in listOf(sundownLine to NAVIGATION_HUD_SUNDOWN_LINE_TAG, backByLine to NAVIGATION_HUD_BACK_BY_LINE_TAG)) {
        val shownLine = rememberLastShown(line)
        AnimatedVisibility(visible = line != null, enter = enter, exit = exit, label = "hudEveningStack") {
            shownLine?.let { whole ->
                Column(modifier = Modifier.semantics { text = AnnotatedString(whole) }.testTag(tag)) {
                    eveningPieces(whole, null).forEach { piece ->
                        WordSwap(text = piece) { shown ->
                            Text(text = shown, style = style, modifier = Modifier.testTag(NAVIGATION_HUD_EVENING_PIECE_TAG))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The grid reference, its tap band the whole width it is given (a real target, not the bare text), padded to about 24 dp tall,
 * toggling MGRS and decimal degrees. Whole: measured into the fit before it is placed (RECORD -699, -936); "…" only as the last
 * resort the fit reports as an overflow.
 */
@Composable
private fun GridReferenceText(coordinates: String, onToggleCoordinateFormat: () -> Unit) {
    Text(
        text = coordinates,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithShapedPress(onClick = onToggleCoordinateFormat)
            .padding(vertical = Spacing.xs)
            .testTag(NAVIGATION_HUD_COORDINATES_TAG),
    )
}

/** A thin vertical line between two columns, with [CARD_DIVIDER_GAP] either side. Draws only; takes no touch. */
@Composable
private fun CardDivider(height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = CARD_DIVIDER_GAP)
            .width(CARD_DIVIDER_WIDTH)
            .height(height)
            .background(color)
            .testTag(NAVIGATION_HUD_DIVIDER_TAG),
    )
}

/**
 * The lines under the columns that say what is wrong or what else is true (RECORD -935: everything today's status line said,
 * shown only when it has something to say), in [widthPx]:
 * - the route message, when the route home cannot be calculated, with "Try again" beside it when recomputing could change that
 *   (RECORD -932, step 3; in landscape too, RECORD -934). It carries [NAVIGATION_HUD_DISTANCE_TAG], the large slot's tag, since
 *   it is the large slot's words moved here; the left column shows a dash meanwhile. Where even "No route" cannot sit beside
 *   "Try again" (font 2.0 in a narrow window), "Try again" takes its own line under it, as before;
 * - the status line, led by the distance's kind when the kind gave way in the left column (RECORD -713, -934: nothing lost).
 */
@Composable
private fun CardRouteAndStatusLines(
    readout: NavigationHudReadout,
    fit: CardColumnsFit,
    widthPx: Int,
    onRetryRoute: () -> Unit,
) {
    val statusStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    val gapPx = with(density) { Spacing.sm.roundToPx() }
    if (readout.distanceText == ROUTE_UNAVAILABLE_TEXT) {
        val retryPx = with(density) { (RETRY_ICON_SIZE + Spacing.xs).roundToPx() } + widthOf(ROUTE_RETRY_TEXT, retryTextStyle())
        val besideRoom = if (readout.routeRetryOffered) widthPx - retryPx - gapPx else widthPx
        val message = if (widthOf(ROUTE_UNAVAILABLE_TEXT, statusStyle) <= besideRoom) ROUTE_UNAVAILABLE_TEXT else ROUTE_UNAVAILABLE_SHORT_TEXT
        val beside = !readout.routeRetryOffered || retryFitsBeside(widthPx, retryPx, gapPx, widthOf(ROUTE_UNAVAILABLE_SHORT_TEXT, statusStyle))
        val messageText: @Composable () -> Unit = {
            WordSwap(text = if (beside) message else ROUTE_UNAVAILABLE_TEXT) { shown ->
                Text(
                    text = shown,
                    style = statusStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_TAG),
                )
            }
        }
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(modifier = Modifier.weight(1f, fill = false)) { messageText() }
                if (readout.routeRetryOffered) RouteRetryRow(onRetryRoute)
            }
        } else {
            messageText()
            RouteRetryRow(onRetryRoute)
        }
    }
    val kindInStatus = !fit.kindInColumn && readout.distanceKindText != null
    val separatorPx = widthOf("·", statusStyle) + 2 * with(density) { Spacing.xs.roundToPx() }
    val statusFit = statusLineFit(readout, kindInStatus, widthPx, separatorPx) { widthOf(it, statusStyle) }
    val distanceColor = if (readout.distanceDeEmphasised) LocalContentColor.current.copy(alpha = 0.5f) else LocalContentColor.current
    if (statusFit.kindLead == null && statusFit.status.isEmpty()) {
        // Nothing to say: no line (RECORD -935, "only when it has something to say"). An empty, zero-height node keeps the
        // status's tag, reading "", so "the status line is empty" stays a statement a test can check.
        Text(text = "", style = statusStyle, modifier = Modifier.height(0.dp).testTag(NAVIGATION_HUD_STATUS_TAG))
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            statusFit.kindLead?.let { lead ->
                DistanceKindText(lead, distanceColor)
                if (statusFit.status.isNotEmpty()) Text("·", style = statusStyle)
            }
            WordSwap(text = statusFit.status) { shown ->
                Text(
                    text = shown,
                    style = statusStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(NAVIGATION_HUD_STATUS_TAG),
                )
            }
        }
    }
}

/**
 * A short label and its reading on one line, a space apart (dispatch 2026-09-28-677; the owner, RECORD -656: "heading and
 * altitude labelled"): "Facing 123° SE", "Alt 1352 ft". [label] `null` draws the reading alone, for a status that names
 * itself. Shared by the navigation display's second row and the compass strip, so the two label alike. The label is its own
 * text node ([NAVIGATION_LABEL_TAG]), so the reading's node, and every test that reads it, holds the reading alone.
 */
@Composable
internal fun LabelledReadout(
    label: String?,
    /** The label's style: labelMedium on the navigation display; the strip's 14 sp readout style there (RECORD -728). */
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
    reading: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (label != null) {
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(NAVIGATION_LABEL_TAG),
            )
        }
        reading()
    }
}


/** The X's touch box: Material's icon button at its 48 dp minimum interactive size. */
private val HUD_EXIT_TOUCH_SIZE = 48.dp

/** What the large figure measures, "by trail" or "straight", in the status line's type (dispatch -677; RECORD -713). */
@Composable
private fun DistanceKindText(kind: String, color: Color, modifier: Modifier = Modifier) {
    WordSwap(text = kind, modifier = modifier) { shown ->
        Text(
            text = shown,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_KIND_TAG),
        )
    }
}

/**
 * "⟳ Try again", on its own line under "Unable to calculate route" (dispatch 2026-09-28-423; the
 * owner chose "Own line under it", shown that on one line beside the message the owner's sentence
 * was cut to "Unable to ca…" on a 360 dp phone and to 16 of its 25 characters on the S22). The
 * message keeps the large slot's line whole, and this row makes the HUD one row taller **only while
 * a route is withheld and recomputing could change that**; with no usable points it is not drawn,
 * and that state keeps the HUD's usual height.
 *
 * At least 48 dp tall, the minimum touch target, laid out at that height rather than relying on
 * hit testing to extend a shorter row, so what a finger can reach is what is drawn. "Try again"
 * reads as something to tap, not as more text: the primary colour, underlined, behind a refresh
 * icon. A screen reader announces the row as a button whose action is "Try again".
 */
@Composable
private fun RouteRetryRow(onRetryRoute: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .heightIn(min = RETRY_ROW_MIN_HEIGHT)
            .clickableWithShapedPress(role = Role.Button, onClickLabel = ROUTE_RETRY_TEXT, onClick = onRetryRoute)
            .testTag(NAVIGATION_HUD_RETRY_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = primary, modifier = Modifier.size(RETRY_ICON_SIZE))
        Text(
            text = ROUTE_RETRY_TEXT,
            style = retryTextStyle(),
            color = primary,
            maxLines = 1,
        )
    }
}

/** "Try again"'s type: the primary colour comes from the row; measured with this by the landscape layout (T11 fixes). */
@Composable
private fun retryTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline)

/** The refresh control's label, the planner's wording (dispatch 2026-09-28-423), shown to the owner with its placement. */
internal const val ROUTE_RETRY_TEXT = "Try again"

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

/**
 * The labels for the heading and the altitude on the navigation display and the compass strip (dispatch 2026-09-28-677, data
 * part B; the owner, RECORD -656: "heading and altitude labelled"). Short, because at 360 dp the strip's one line holds
 * "Facing 123° SE · Alt 1352 ft · 10T ER 25118 40235" with a few dp to spare. **New words, a stop for the owner.**
 *
 * RECORD -728, the owner: "Keep the the 330° NW metric, just remove the word "facing" and nothing else." The heading's label,
 * "Facing", is gone from the strip and the navigation display's second row; the heading reads "330° NW" alone. "Alt" stays.
 */
internal const val ALTITUDE_LABEL = "Alt"

/** The heading's dash while the compass waits for a fix: the status line carries [NO_FIX_MESSAGE] (owner's call). */
internal const val NO_HEADING_TEXT = "—"

/** A fix that carries no altitude. A status that names itself, so it wears no [ALTITUDE_LABEL]. */
internal const val ELEVATION_UNAVAILABLE_TEXT = "Elevation unavailable"

/** The large slot's words once the walker has arrived at the start (dispatch 2026-09-28-497). */
internal const val ARRIVED_TEXT = "Arrived"

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

/** The large slot's words when the route is withheld: the owner's, 2026-09-12. */
internal const val ROUTE_UNAVAILABLE_TEXT = "Unable to calculate route"


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
            statusFallbacks = listOf(NO_FIX_SHORT_TEXT), statusFallbacksFor = NO_FIX_MESSAGE,
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

private val COMPASS_ICON_SIZE = 22.dp

/** Dispatch 2026-10-12-06: the card's column dividers, thin lines with this gap either side (6 dp, RECORD -936's tighter spacing). */
private val CARD_DIVIDER_WIDTH = 1.dp
private val CARD_DIVIDER_GAP = 6.dp
/** RECORD -936 (the owner: "Yes, slightly tighter (Recommended)"): the card's own side padding. */
private val CARD_SIDE_PADDING = Spacing.xs
/** RECORD -943: the card's top padding, under the pill. */
private val CARD_TOP_PADDING = 2.dp
private const val CARD_LOG_TAG = "NavigationCard"
private val RETRY_ICON_SIZE = 18.dp
private val RETRY_ROW_MIN_HEIGHT = 48.dp
private const val AGE_TICK_MILLIS = 1_000L

/** The navigation display's back-by line (dispatch 2026-09-28-645). */
internal const val NAVIGATION_HUD_BACK_BY_LINE_TAG = "navigation-hud-back-by-line"
