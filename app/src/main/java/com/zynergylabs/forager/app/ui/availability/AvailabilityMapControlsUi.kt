package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage F: the map's controls, moved verbatim out of
// AvailabilityScreen.kt. Five blocks, lines of the file as of 3bd0efe: 3317-3321
// (CompassStripBackgroundColorDark/Light, already internal), 4436-4440 (COMPASS_STRIP_HEADING_TAG,
// COMPASS_STRIP_NO_FIX_TAG), 4445-4802 (TrailheadControls, ControlPill, CompassElevationStrip,
// CompassElevationStripContent, returnToStartStripText), 4866-4996 (AddActionTile,
// ADD_ACTION_TILE_SCRIM_TAG, ADD_ACTION_TILE_TAG) and 4998-5003 (ADD_TILE_ANCHOR_OFFSET). Same
// package as Stages A to E, so every same-package reference resolves unchanged. Pure move: no
// signature, name or body changed. Four widenings, private -> internal: TrailheadControls,
// CompassElevationStrip, AddActionTile and ADD_TILE_ANCHOR_OFFSET, all used by CompactMapTab in
// AvailabilityCompactMapUi.kt. No symbol left behind is reached from here. Seam F (the wide layout)
// was released by the owner for this split, as recorded in the Understory amendment merged in #130.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_CORNER_RADIUS
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapBarIconButton
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.mapIconClusterChildColor
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.MapModePicker
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.map.mapIconBarRecordAccent
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING
import com.zynergylabs.forager.app.ui.map.mapIconBarRowAnchorOffset
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.Cream
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.roundToInt

/** Translucent background for [CompassElevationStripContent] and [SearchDropdown]'s own panel — dark-theme value. Was 0.78, one alpha step off the app's settled 80% map-chrome opacity ([MapIconStackButtonColorDark]'s own value); the map/navigation search-UI redo dispatch names 80% as the one value all map chrome shares, so this now matches rather than carrying its own near-miss. */
internal val CompassStripBackgroundColorDark = Bark.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/** [CompassStripBackgroundColorDark]'s light-theme counterpart — same reasoning as [MapIconStackButtonColorLight]: picked per [com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme], independent of the map's own night mode, unverified on hardware. */
internal val CompassStripBackgroundColorLight = Cream.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/** The compass strip's heading text — one of the two places a heading can appear, never both at once (the strip hides while the HUD shows). */
internal const val COMPASS_STRIP_HEADING_TAG = "compass-strip-heading"

/** The compass strip's whole content while there is no fix — one message, [NO_FIX_MESSAGE], in place of three fragments. */
internal const val COMPASS_STRIP_NO_FIX_TAG = "compass-strip-no-fix"

/**
 * The two Trailhead/Return controls — record start/stop and return-to-vehicle — anchored together
 * below [MapIconBar], per this dispatch's own Part B: they used to be split across a
 * [MapIconBar] row and a duplicate compass-strip readout; now they share one home.
 *
 * **Icon-bar-unify-container dispatch: laid out inside the cluster container's own Column, directly
 * below [MapIconBar], no anchoring of its own.** It used to offset itself by the bar's measured
 * bottom edge (`mapIconBarBottomPx`, from [CompactMapTab]'s `onGloballyPositioned` on the bar's
 * wrapper) — measured rather than derived from the bar's row count, which changed twice in quick
 * succession at the time. That measurement is gone: the Column's `spacedBy` is the gap now, and
 * the cluster container is what gets measured, dragged, clamped and minimised, so this pill is in
 * bounds because it is inside the thing that is bounded. On device before this change the record
 * pill hung off the bottom in fullscreen while the bar sat legally, and the directions pill was
 * left sitting on the nav after exiting — the bar was in bounds, the pills extended past it.
 *
 * **Icon-bar-drag-refinements dispatch, Items 2-3: [onLeftSide] follows [MapIconBar]'s own side.**
 * [ControlPill] stays pinned to the container's outer edge on whichever side (the Column's
 * `horizontalAlignment`). It hides and restores with the bar when minimised — by construction now.
 *
 * **The distance arm is gone (navigation-chrome dispatch, item 3).** `DistanceArm` used to extend
 * downward from [ControlPill] while returning, showing the return distance; navigation HUD stage
 * one then made the HUD visible on exactly the condition the arm rendered on, so the two were
 * always on screen together showing the same number ("0 ft beside 0 ft", on device). Confirmed
 * before removal: no state showed the arm without the HUD — both keyed on the one `isReturning`,
 * both in this tab's map Box — while the reverse (HUD without arm, cluster minimised) did exist.
 * The return row's `contentDescription` still carries the full bearing/distance/elevation
 * sentence, so the TalkBack path is unchanged; the visible distance is the HUD's. This Column is
 * kept rather than inlining the pill: it is the cluster's named "Trailhead/Return controls" slot,
 * and stage two's picker entry is expected to land here.
 *
 * `isRecording` is passed through as a plain parameter, not a presence check gating whether this
 * composable runs at all — record start/stop must stay reachable before the first recording
 * starts, the same as when it was an always-enabled [MapIconBar] row.
 */
@Composable
internal fun TrailheadControls(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    /** For the return row's `contentDescription` sentence — the same [formatDistanceMeters] the HUD's visible distance uses. */
    distanceUnit: DistanceUnit,
    onLeftSide: Boolean,
    modifier: Modifier = Modifier,
    /** Landscape L (dispatch 2026-09-28-160): turns the pill horizontal, record then return; see [ControlPill]. */
    horizontal: Boolean = false,
    /** Landscape L: the pill's own fill, at the standing chrome alpha, where no container sits under it; default is the cluster child fill. */
    fillColor: Color = Color.Unspecified,
    /** Landscape L: spacing and end padding inside the pill; [Spacing.xs] as it has always been, zero in the L. */
    rowSpacing: Dp = Spacing.xs,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (onLeftSide) Alignment.Start else Alignment.End,
    ) {
        ControlPill(
            horizontal = horizontal,
            onLeftSide = onLeftSide,
            fillColor = fillColor,
            rowSpacing = rowSpacing,
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
            returnToStart = returnToStart,
            isReturning = isReturning,
            isOffTrack = isOffTrack,
            onToggleReturning = onToggleReturning,
            distanceUnit = distanceUnit,
        )
    }
}

/**
 * The vertical control pill at the bottom right, below [MapIconBar] — record start/stop and
 * return-to-vehicle, the two Trailhead/Return controls, sharing one home per this dispatch's own
 * Part B rather than split across a [MapIconBar] row and a duplicate compass-strip readout.
 *
 * Same visual language as [MapIconBar] on purpose — same [MAP_ICON_BAR_CORNER_RADIUS] stadium
 * shape, same theme-aware surface/border colors, same [MapBarIconButton] rows — so this reads as a
 * sibling of the bar, not a new kind of floating control. Both rows carry over unchanged from
 * [MapIconBar]'s own former record/return-to-vehicle rows, including their accents
 * ([mapIconBarRecordAccent]) and the return row's disabled-while-not-recording treatment.
 */
@Composable
private fun ControlPill(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    /** Which side the cluster is on: the horizontal pill keeps record at the outer end, under the bar, so its order mirrors. */
    onLeftSide: Boolean = true,
    fillColor: Color = Color.Unspecified,
    rowSpacing: Dp = Spacing.xs,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    // A child of the cluster container — see MAP_ICON_CLUSTER_CHILD_ALPHA's own doc comment — unless the landscape L hands it
    // its own single-layer fill.
    val pillFill = fillColor.takeOrElse { mapIconClusterChildColor() }
    val pillContentColor = if (isDarkTheme) Color.White else Bark
    val pillBorder = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT)
    val buttons: @Composable () -> Unit = {
        // The two buttons, once: the vertical pill lays them in a Column, the landscape L's horizontal pill (record under the bar's
        // column, return extending inboard) in a Row. Same buttons, tags, states and accents either way.
        val record: @Composable () -> Unit = {
            MapBarIconButton(
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) "Stop recording track" else "Start recording track",
                onClick = onToggleRecording,
                filled = isRecording,
                fillColor = mapIconBarRecordAccent(isDarkTheme).fill,
                fillContentColor = mapIconBarRecordAccent(isDarkTheme).onFill,
                modifier = Modifier.testTag("control-pill-record"),
            )
        }
        val returnToVehicle: @Composable () -> Unit = {
            MapBarIconButton(
                icon = Icons.Filled.Directions,
                contentDescription = returnToStartStripText(isRecording, returnToStart, distanceUnit)
                    .ifBlank { "Return to vehicle — start recording first" },
                onClick = onToggleReturning,
                enabled = isRecording,
                activeColor = when {
                    isOffTrack -> MaterialTheme.colorScheme.error
                    isReturning -> MaterialTheme.colorScheme.primary
                    else -> null
                },
                modifier = Modifier.testTag("control-pill-return-to-vehicle"),
            )
        }
        if (horizontal) {
            Row(
                modifier = Modifier.padding(horizontal = rowSpacing),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onLeftSide) {
                    record()
                    returnToVehicle()
                } else {
                    returnToVehicle()
                    record()
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(vertical = rowSpacing),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                record()
                returnToVehicle()
            }
        }
    }
    if (horizontal) {
        // Owner's ruling (d), continuation 2026-09-28-172: both buttons take touches across their full 48 x 48 squares, corners
        // included. The drawn pill is a content-less Surface underneath, so its rounded ends do not clip the buttons' hit areas.
        Box(modifier = modifier.testTag("control-pill")) {
            Surface(
                shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
                color = pillFill,
                shadowElevation = 2.dp,
                border = pillBorder,
                modifier = Modifier.matchParentSize(),
            ) {}
            CompositionLocalProvider(LocalContentColor provides pillContentColor) { buttons() }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
            color = pillFill,
            contentColor = pillContentColor,
            shadowElevation = 2.dp,
            border = pillBorder,
            modifier = modifier.testTag("control-pill"),
        ) { buttons() }
    }
}

/**
 * Decisions #7-8: compass heading + GPS elevation folded into one bar at the top of the map — a
 * compass-tape-style heading readout, not a separate elevation/speed stats pill (that would be
 * tied to active track recording, out of scope here — see the plan doc's decision #9). The MGRS
 * grid reference on its own line below extends this same strip rather than becoming a separate
 * "navigator screen" — position and heading are the one thing a field navigator needs together at
 * a glance, and [MgrsConverter] already exists ([PlannedTripRow] uses it the same way).
 *
 * A thin wrapper around [CompassElevationStripContent] that does the one impure thing (collecting
 * [compassProvider]'s [Flow][kotlinx.coroutines.flow.Flow]) so that content composable stays a pure
 * function of primitive values — directly testable without a real sensor, per the plan doc's test
 * requirements, and so heading ticks recompose only this small leaf rather than the whole map tab
 * (which would otherwise fight the user's own pan/zoom on every sensor update).
 */
@Composable
internal fun CompassElevationStrip(
    /**
     * **True north, as of navigation HUD stage one** — the one smoothed, declination-corrected
     * reading the HUD reads too ([rememberTrueHeading]), so the strip and the HUD can never
     * disagree. This strip used to rotate the raw magnetic value, which differs from the HUD's
     * true-north bearings by local declination — about 15° in the Pacific Northwest, a fixed
     * offset that no averaging removes. Do not move it back to magnetic: a needle and a readout on
     * the same screen that disagree by 15° is the exact failure the foundations work exists to
     * prevent. Read as a [State] here, in this leaf, and nowhere above — see
     * [rememberTrueHeading]'s own doc comment.
     */
    heading: State<TrueHeadingReading>,
    elevationMeters: Double?,
    location: LatLng?,
    /** The MGRS/decimal choice, hoisted to [CompactMapTab] and shared with [NavigationHud] — see that call site. */
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Landscape B2 (S4): wrap the content instead of spanning the width — the strip sits in the
     * top corner on the rail side there. False (full width) everywhere else, as before.
     */
    contentWidth: Boolean = false,
) {
    val reading by heading
    CompassElevationStripContent(
        heading = reading,
        elevationMeters = elevationMeters,
        location = location,
        showDecimalDegrees = showDecimalDegrees,
        onToggleCoordinateFormat = onToggleCoordinateFormat,
        modifier = modifier,
        contentWidth = contentWidth,
    )
}

/**
 * Heading, elevation, and coordinates only — one centered line. Pure readout, per this dispatch's
 * Part A item 3: the return-to-vehicle readout field-test dispatch item 2 added here is removed,
 * not merely hidden — it moved into [ControlPill] instead, alongside record start/stop, so the two
 * Trailhead/Return controls live in one place rather than split between this strip and
 * [MapIconBar]. **Corrected 2026-08-28**: named `MapIconStack` here before that composable was
 * renamed. **Navigation-chrome dispatch**: not composed while navigating (see the call site); the
 * MGRS/decimal toggle state is hoisted and shared with [NavigationHud]; and with no fix the whole
 * strip is one statement, [NO_FIX_MESSAGE], not three fragments.
 *
 * Two causes, two messages, and the third combination folds into the first: **no fix** (whatever
 * the sensor says) is [NO_FIX_MESSAGE] alone; **no sensor with a fix** is "Compass unavailable"
 * with elevation and coordinates still shown. Keyed on [location], not on
 * [TrueHeadingReading.NeedsFix] — `rememberTrueHeading` reports [TrueHeadingReading.NoSensor]
 * before it checks for a fix, so keying on the heading would leave a phone with no magnetometer
 * and no fix showing three fragments again.
 */
@Composable
private fun CompassElevationStripContent(
    heading: TrueHeadingReading,
    elevationMeters: Double?,
    location: LatLng?,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    modifier: Modifier = Modifier,
    contentWidth: Boolean = false,
) {
    // A plain Box + background, not Surface: Surface (even with no onClick) intercepts pointer
    // input for the area it occupies, which — now that this strip is full-width — swallowed the
    // map's own pan/tap gestures underneath it (the same failure class IntrinsicSize.Max fixed
    // for the narrow pill, but that fix meant shrinking the strip back down, which isn't an option
    // now that full width is the point). A Box with no pointer/click handling of its own doesn't
    // intercept anything, so the map keeps receiving touches everywhere except this strip's own
    // real interactive child (the coordinates segment below).
    //
    // Independent of the map's own night mode -- see MapIconStackButtonColorDark's own doc
    // comment for why the two axes are kept separate rather than one steering the other.
    val isDarkTheme = LocalForagerDarkTheme.current
    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        Box(
            modifier = modifier
                // No heightIn(min = ...) any more — Part A item 1's revert. The 48dp touch-target
                // floor field-test dispatch item 2 pinned here existed only to give the strip's own
                // return-to-vehicle IconButton a real touch target; that control moved out to
                // ControlPill (Part B), so this strip wraps its Row's natural text-content height
                // again, the same as before that dispatch.
                .background(
                    color = if (isDarkTheme) CompassStripBackgroundColorDark else CompassStripBackgroundColorLight,
                    shape = RectangleShape,
                )
                // Lets CompactMapTab's own onGloballyPositioned measure this strip's real height —
                // AnchoredAtScreenPoint's minY and the taxon filter chip's top padding both need to
                // clear it, and now that it wraps content instead of sitting at a fixed 48dp, a
                // measured value is the only one that stays correct as font scale or content change
                // it. Also still what this composable's own regression test targets directly.
                .testTag("compass-elevation-strip"),
        ) {
            Row(
                // fillMaxWidth, not fillMaxSize — see this Box's own doc comment above for the
                // hardware-caught bug an unbounded-height descendant caused here previously; nothing
                // in this Row asks for available height any more, but the fillMaxWidth-not-fillMaxSize
                // choice stays deliberate rather than reverting to whichever one happens to still work.
                modifier = Modifier
                    // A little horizontal padding again, unlike the zero this Row held right before
                    // Part A: that zero relied on two MIN_TOUCH_TARGET end boxes (this icon's, and
                    // the return-to-vehicle control's) to supply real whitespace at both edges by
                    // themselves. The return-to-vehicle box is gone (moved to ControlPill) and this
                    // icon's own box no longer holds itself to a 48dp touch target (it isn't one —
                    // see below), so nothing is left to keep the text off the strip's own edges
                    // without this. Re-added once, in the direction opposite the three prior trims
                    // Part A item 4 warns about, and explained rather than blindly re-trimmed.
                    .padding(horizontal = Spacing.sm)
                    // Landscape B2 (S4): content-width in landscape; full width otherwise.
                    .then(if (contentWidth) Modifier else Modifier.fillMaxWidth()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fixed-width slot, not inline in the text group below — a hardware report found the
                // compass needle visibly drifting left/right as the heading/elevation/coordinates
                // text next to it changed length, because it used to be the first child of that
                // centered group rather than its own fixed slot; this box's position never depends on
                // any text's width. Sized to its own content (no explicit .size()), not
                // MIN_TOUCH_TARGET: Part A item 2's revert — this icon is decorative (no click
                // affordance of its own), so pinning it to a 48dp touch-target box only existed to
                // match the return-to-vehicle control's box that shared this Row; that control is
                // gone, and holding this box at 48dp anyway would defeat item 1's height revert by
                // becoming the Row's own tallest child in the vehicle box's place.
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate((heading as? TrueHeadingReading.Available)?.degrees ?: 0f),
                    )
                }
                if (location == null) {
                    // One statement across the strip — see this composable's own doc comment. Not
                    // tappable: there is no coordinate pair to toggle, and nothing to fabricate one
                    // from. The Row's remaining width, so it centres where the three segments did.
                    Text(
                        text = NO_FIX_MESSAGE,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        // Landscape B2 (S4): no weight when content-width, or this one line
                        // would stretch the strip back across the window.
                        modifier = Modifier
                            .then(if (contentWidth) Modifier else Modifier.weight(1f))
                            .testTag(COMPASS_STRIP_NO_FIX_TAG),
                    )
                } else {
                    // Heading, elevation, and coordinates, taking whatever width is left after the fixed
                    // compass-icon box above — this group used to share its weight(1f) budget with that
                    // icon's inline width, which is exactly the width the coordinates segment's own
                    // ellipsis was giving up first on a narrow screen (a hardware report: "cut off for no
                    // reason" — there was room, it just wasn't reaching this Text). Pulling the icon out
                    // of this Row's own measurement entirely is the fix, not a wider budget. Only one
                    // fixed sibling now (Part A item 3 removed the strip's own return-to-vehicle box),
                    // so this group's own available width is wider still than when that box also took a
                    // share. TextOverflow.Ellipsis on the coordinates segment stays as the last-resort
                    // safety net for a screen too narrow for all three fields regardless, not
                    // horizontalScroll — see this composable's own doc comment above for why
                    // horizontalScroll was rejected (it intercepts touches meant for the map underneath).
                    Row(
                        // Landscape B2 (S4): no weight when content-width.
                        modifier = if (contentWidth) Modifier else Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                    ) {
                        Text(
                            // With a fix, the heading has two real states and one transient: a value,
                            // no sensor, or NeedsFix for the frame or two between the fix landing and
                            // the next sensor emission (rememberTrueHeading restarts its producer on
                            // that transition). The transient shows a dash, the same as the HUD — the
                            // "needs a fix" wording is gone, replaced by NO_FIX_MESSAGE above.
                            text = when (heading) {
                                is TrueHeadingReading.Available -> "${heading.degrees.roundToInt() % 360}° ${cardinalDirection(heading.degrees)}"
                                TrueHeadingReading.NoSensor -> "Compass unavailable"
                                // Present but not to be trusted (compass-reliability dispatch).
                                // Names no cause: the status cannot tell a truck from a poorly
                                // calibrated sensor, and the remedies differ — telling someone to
                                // calibrate beside a truck is wrong advice confidently given.
                                TrueHeadingReading.Unreliable -> "Compass unreliable"
                                TrueHeadingReading.NeedsFix -> "—"
                            },
                            // Landscape B2 (S5): tabular figures, so the strip's width holds
                            // steady as the digits change. Both orientations; labelMedium kept.
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            modifier = Modifier.testTag(COMPASS_STRIP_HEADING_TAG),
                        )
                        Text("·", style = MaterialTheme.typography.labelMedium)
                        Text(
                            // Meters, matching this app's existing metric convention (radiusKm) rather
                            // than introducing feet — nothing else in the app displays imperial units.
                            text = elevationMeters?.let { "${it.roundToInt()} m" } ?: "Elevation unavailable",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                        Text("·", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = coordinatesStripText(location, showDecimalDegrees),
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onToggleCoordinateFormat),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The return-to-vehicle line's text — blank while not recording (nothing to return to), a status
 * message once recording starts but before the first breadcrumb lands (bearing/distance need a
 * start point), then bearing, distance, and elevation difference once [info] is real. Now
 * [ControlPill]'s own `contentDescription` for its return-to-vehicle row (this used to also feed
 * the compass strip's own visible readout via `ReturnToVehicleStripControl`, removed in this
 * dispatch's Part A — see [CompassElevationStripContent]'s own doc comment).
 * [ReturnToStartInfo]'s own doc comment covers why there's no ETA here.
 */
internal fun returnToStartStripText(isRecording: Boolean, info: ReturnToStartInfo?, distanceUnit: DistanceUnit): String {
    if (!isRecording) return ""
    if (info == null) return "Recording — waiting for a fix to compute the way back"
    val elevationText = info.elevationDifferenceMeters?.let {
        "${if (it >= 0) "+" else ""}${it.roundToInt()} m"
    } ?: "elevation diff. unavailable"
    val bearing = info.bearingDegrees.roundToInt()
    return "Return: $bearing° ${cardinalDirection(info.bearingDegrees.toFloat())} · ${formatDistanceMeters(info.distanceMeters, distanceUnit)} · $elevationText"
}

/**
 * [CompactMapTab]'s own version of the same three-way chooser [ThreeWayActionDialog] shows on
 * medium/expanded windows — same three choices, same shared [PendingMapAction] state, but
 * presented as a small tile that grows out of the add button's own corner of the icon bar rather
 * than [AlertDialog]'s centered scale-in, per the project owner's own description of how it should
 * open. Compact-only: the medium/expanded window's own add button (added alongside this rework —
 * see [MapTab]'s doc comment) has no icon bar for a tile to grow out of, so [MapTab] keeps the
 * plain dialog instead.
 *
 * **Real [AssistChip]s, short labels, no title row.** The original had a "Add..." title plus four
 * full-width [TextButton]s (three choices, and its own "Cancel") — a real hardware-reported
 * problem, not a style preference: a full-width button is as wide as its longest label, which made
 * the whole tile noticeably wider than the icon bar it grows out of. Short labels ("Trip"/
 * "Find"/"Waypoint") in a [Row] instead of a [Column] let the tile's own width shrink to what three
 * short buttons actually need, rather than the longest of three sentences. No separate "Cancel"
 * button either — the scrim below already dismisses on a tap outside, and a fourth wide row would
 * undo the same fit this rewrite exists for.
 *
 * **[AssistChip], not the filled Material [Button] this tile used at first.** A solid
 * `colorScheme.primary`-filled button is its own large block of colour, and three of them side by
 * side visually crowded out the tile's own theme-aware card fill entirely — from the project
 * owner's own side-by-side comparison against [MapModePicker], whose unselected [FilterChip]s stay
 * outlined and let that same card fill read clearly instead. [AssistChip] is the semantically
 * correct match, not [FilterChip] borrowed wholesale: these three rows perform an action each and
 * share no "currently selected" state the way [MapModePicker]'s three modes do, so nothing here is
 * ever passed a `selected` value.
 *
 * Same theme-aware, 80%-opacity fill as [MapIconBar]
 * ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) rather than a plain
 * [MaterialTheme.colorScheme.surface] — one visual language for every control floating over the
 * map, the same reasoning [MapModePicker] gives for the same choice.
 *
 * A scrim (its own [AnimatedVisibility], faded independently of the tile) makes the map behind it
 * unmistakably unavailable to tap while a choice is pending — the same modal intent the dialog it
 * replaces had, just without borrowing [AlertDialog]'s fixed presentation. The scrim carries no
 * visible label of its own (unlike the three buttons beside it), so it is addressed in tests by
 * [ADD_ACTION_TILE_SCRIM_TAG] rather than text.
 *
 * [anchor]/[anchorOffset] mirror [MapModePicker]'s own pair exactly (expanded-panels dispatch):
 * this used to hardcode `CenterEnd` / `-Spacing.sm` / [ADD_TILE_ANCHOR_OFFSET] internally, which
 * was fine while [MapIconBar] could only ever sit at its default right-centre position, and
 * silently wrong once the bar became draggable — the tile kept opening at the bar's *old*
 * position. The single caller now feeds the bar's live side and drag offset. [growsFrom] is the
 * corner the tile grows out of / shrinks back into, the add row's own corner of the bar: bottom-
 * end on the right, bottom-start once the bar is snapped to the left — passed explicitly rather
 * than derived from [anchor] here, so the caller's intent is visible at the call site.
 */
@Composable
internal fun AddActionTile(
    visible: Boolean,
    onPlanTrip: () -> Unit,
    onLogFind: () -> Unit,
    onDropWaypoint: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.CenterEnd,
    anchorOffset: DpOffset = DpOffset(x = -MAP_ICON_BAR_EDGE_INSET, y = ADD_TILE_ANCHOR_OFFSET),
    growsFrom: Alignment = Alignment.BottomEnd,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Box(modifier = modifier) {
        // docs/motion-spec.md §2 "Panels and navigation": panels accept mild spring overshoot as
        // a taste call, per docs/adr/0002-motion-scheme-adoption.md. Passing MotionTokens's spec
        // explicitly here removes any dependence on AnimatedVisibility's own default spec.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ADD_ACTION_TILE_SCRIM_TAG)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()) +
                expandIn(
                    animationSpec = MotionTokens.panelMotionSpec(),
                    expandFrom = growsFrom,
                ),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()) +
                shrinkOut(
                    animationSpec = MotionTokens.panelMotionSpec(),
                    shrinkTowards = growsFrom,
                ),
            modifier = Modifier
                .align(anchor)
                .offset(x = anchorOffset.x, y = anchorOffset.y),
        ) {
            Surface(
                modifier = Modifier.testTag(ADD_ACTION_TILE_TAG),
                shape = RoundedCornerShape(Spacing.md),
                shadowElevation = 4.dp,
                color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                contentColor = if (isDarkTheme) Color.White else Bark,
                border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AssistChip(onClick = onPlanTrip, label = { Text("Trip") })
                    AssistChip(onClick = onLogFind, label = { Text("Find") })
                    AssistChip(onClick = onDropWaypoint, label = { Text("Waypoint") })
                }
            }
        }
    }
}

/** See [AddActionTile]'s own doc comment — its scrim has no visible label, so tests address it by tag. */
internal const val ADD_ACTION_TILE_SCRIM_TAG = "add-action-tile-scrim"

/**
 * [AddActionTile]'s own panel `Surface` — what the expanded-panels dispatch's tests measure its
 * real edges by. Its chips are not a usable proxy for those edges: under Robolectric's near-zero-
 * width fonts each chip measures under 48dp wide, so Material's `minimumInteractiveComponentSize`
 * wrapper pads it out and the chip's own semantics bounds stop short of the panel's edge by a
 * font-dependent margin (measured: ~5.5–7dp) that a real device would not show.
 */
internal const val ADD_ACTION_TILE_TAG = "add-action-tile"

/**
 * [MapIconBar]'s add row is its 5th (last) of 5 — see [mapIconBarRowAnchorOffset], promoted into
 * `MapChrome.kt` (fullscreen-fixes dispatch, Item 2) so the Cartography entry map's own
 * `MapModePicker` call can use the same row-anchor arithmetic this file's calls already did.
 */
internal val ADD_TILE_ANCHOR_OFFSET = mapIconBarRowAnchorOffset(rowIndexFromTop = 5)

/**
 * [ADD_TILE_ANCHOR_OFFSET] for the landscape L's bar (dispatch 2026-09-28-160), whose rows are 48 dp apart, not 52: the add row's
 * centre is 96 dp below the bar's, not 104.
 */
internal val ADD_TILE_ANCHOR_OFFSET_LANDSCAPE = mapIconBarRowAnchorOffset(rowIndexFromTop = 5, rowSpacing = MAP_ICON_BAR_LANDSCAPE_ROW_SPACING)
