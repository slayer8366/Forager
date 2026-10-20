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

import androidx.compose.ui.semantics.text
import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatWholeLength
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapChromeOutline
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.IntSize
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.WordSwap
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.Cream
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.roundToInt

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
internal fun CompassElevationStripContent(
    heading: TrueHeadingReading,
    elevationMeters: Double?,
    unitSystem: UnitSystem,
    location: LatLng?,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    modifier: Modifier = Modifier,
    contentWidth: Boolean = false,
    shortHeadingStatus: Boolean = false,
    positionNote: PositionNote? = null,
    sundownLine: String? = null,
    backByLine: String? = null,
    quickSettings: MapQuickSettings? = null,
    cardCorners: MapCardCorners = MapCardCorners.BOTTOM,
    tuck: Dp = 0.dp,
    navigating: Boolean = false,
    fill: Color? = null,
    navigationInfo: (@Composable () -> Unit)? = null,
    outlineSides: MapChromeOutlineSides? = null,
    readoutCentredOnMenu: Boolean = false,
    coordinatesSizedForBothForms: Boolean = false,
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
                // Dispatch 2026-10-12-06 (RECORD -932, -934): the rounded card, tucked under the pill in portrait, still a
                // background with no pointer input of its own (RoundedMapCard.kt), so the map keeps every touch but the strip's
                // own controls, its corner gaps included.
                .mapCardSurface(
                    fill = fill ?: if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                    corners = cardCorners,
                    tuck = tuck,
                    outline = mapChromeOutline(),
                    outlineSides = outlineSides,
                )
                // Lets CompactMapTab's own onGloballyPositioned measure this strip's real height —
                // AnchoredAtScreenPoint's minY and the taxon filter chip's top padding both need to
                // clear it, and now that it wraps content instead of sitting at a fixed 48dp, a
                // measured value is the only one that stays correct as font scale or content change
                // it. Also still what this composable's own regression test targets directly.
                .testTag("compass-elevation-strip")
                .mapChromeContainerColor(fill ?: if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight),
        ) {
            // Dispatch 2026-09-28-645 (Amendment 2, the owner: "Taller strip (Recommended)"; Amendment 3,
            // RECORD -648: "a 3 dot menu at the far right", 36 dp): a Row so the quick-settings button
            // sits at the strip's far right, its whole QUICK_SETTINGS_TAP_TARGET square within the
            // strip's own height: the strip is at least that tall, and nothing hangs over the map. The
            // Row draws nothing and takes no touches; the button takes them in its own square. Its place
            // follows the strip, not the screen (RECORD -649, the owner: "far right in the strip").
            // Dispatch 2026-10-12-08: a Column around that Row, drawing nothing and taking no touch, so the navigation info sits
            // under the line inside this card (portrait, RECORD -950); the three-dot button stays on the line, above the X.
            Column(modifier = if (contentWidth) Modifier else Modifier.fillMaxWidth()) {
            Row(
                modifier = if (contentWidth) Modifier else Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // Dispatch 2026-09-28-592: a Column so the sundown line can sit under the readout. It draws
            // nothing and takes no touches of its own, like the Box around it.
            // RECORD -709 (the planner): in a landscape window the strip is content-width under the small fixes' cap, and this
            // column took all of it before the button was measured, leaving the three-dot button 0 dp wide at 780 x 360 dp. Weighted,
            // not filling, it is measured after the button, which always keeps its QUICK_SETTINGS_TAP_TARGET; the readouts then give
            // way inside what is left (readoutsFitBeside), coordinates first.
            Column(modifier = if (contentWidth) Modifier.weight(1f, fill = false) else Modifier.weight(1f)) {
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
                // Dispatch 2026-10-12-08 (RECORD -948): navigating, the line drops the north arrow with the heading.
                if (!navigating) Box(contentAlignment = Alignment.Center, modifier = Modifier.testTag(COMPASS_STRIP_NEEDLE_TAG)) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(STRIP_NEEDLE_SIZE)
                            .rotate((heading as? TrueHeadingReading.Available)?.degrees ?: 0f),
                    )
                }
                // Motion Part 2, Amendment 1 (RECORD -672), item 3 (scout C2): the swap between the readout, "Location services
                // unavailable" and the position note crossfades, on the word swap's spec, rather than jumping. The outgoing one
                // keeps drawing what it showed (lastNote, lastLocation) and takes no touch, so its coordinates cannot be tapped
                // as it fades (item 5). The width given to the three (the Row's remaining width, or content width in landscape)
                // is now on this box; each fills it, as each used to take it by weight. No size animation.
                val readoutKind = when {
                    positionNote != null -> StripReadout.POSITION_NOTE
                    location == null -> StripReadout.NO_FIX
                    navigating -> StripReadout.NAVIGATING
                    else -> StripReadout.READOUT
                }
                val lastNote = rememberLastShown(positionNote)
                val lastLocation = rememberLastShown(location)
                val readoutFade = MotionTokens.wordSwapSpec<Float>()
                AnimatedContent(
                    targetState = readoutKind,
                    modifier = (if (contentWidth) Modifier else Modifier.weight(1f))
                        // Dispatch 2026-10-13-02, RECORD -983, -984: centred between the arrow and the dots (see
                        // stripReadoutShiftToMenu); drawn and touched where it is placed, measured as before.
                        .then(if (readoutCentredOnMenu && quickSettings != null) Modifier.offset(x = stripReadoutShiftToMenu(navigating)) else Modifier),
                    transitionSpec = { (fadeIn(animationSpec = readoutFade) togetherWith fadeOut(animationSpec = readoutFade)).using(null) },
                    contentAlignment = Alignment.Center,
                    label = "stripReadout",
                ) { kind ->
                    // RECORD -691: no minimum touch target while it leaves, so a touch near a leaving piece under 48 dp is not handed to it (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
                    NoTouchTargetExpansion(active = kind != readoutKind) {
                    Box(
                        modifier = Modifier
                            .then(if (contentWidth) Modifier else Modifier.fillMaxWidth())
                            .leavingTakesNoTouches(leaving = kind != readoutKind),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (kind == StripReadout.POSITION_NOTE && lastNote != null) {
                            // Dispatch 2026-09-28-510 (the owner chose "Say so, no coordinates"): the heading,
                            // then what the position is, in place of elevation and coordinates, which a reading
                            // known to 100 m, or hours old, cannot honestly give. Not tappable: there is no
                            // coordinate pair to toggle. Before the no-fix case, since an approximate or last
                            // known position is something rather than nothing.
                            Row(
                                modifier = if (contentWidth) Modifier else Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                            ) {
                                // Motion Part 2, item 6: words crossfade, numbers change at once (WordSwap).
                                // Dispatch 2026-09-28-677: labelled, as on the navigation display (LabelledReadout).
                                // Dispatch 2026-10-12-08: navigating, the note alone; the heading is the navigation info's.
                                if (!navigating) {
                                    if (shortHeadingStatus && heading.isCompassStatus()) {
                                        StripHeadingStatusIcon(heading)
                                    } else {
                                        LabelledReadout(label = stripHeadingLabel(heading), labelStyle = stripReadoutStyle()) {
                                            WordSwap(text = stripHeadingText(heading)) { shown ->
                                                Text(
                                                    text = shown,
                                                    style = stripReadoutStyle().copy(fontFeatureSettings = "tnum"),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.testTag(COMPASS_STRIP_HEADING_TAG),
                                                )
                                            }
                                        }
                                    }
                                    Text("·", style = stripReadoutStyle())
                                }
                                WordSwap(text = lastNote.stripText, modifier = Modifier.weight(1f, fill = false)) { shown ->
                                    Text(
                                        text = shown,
                                        style = stripReadoutStyle().copy(fontFeatureSettings = "tnum"),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag(COMPASS_STRIP_POSITION_NOTE_TAG),
                                    )
                                }
                            }
                        } else if (kind == StripReadout.NAVIGATING && lastLocation != null) {
                            StripNavigatingReadout(
                                coordinatesSizedForBothForms = coordinatesSizedForBothForms,
                                elevationMeters = elevationMeters,
                                unitSystem = unitSystem,
                                location = lastLocation,
                                showDecimalDegrees = showDecimalDegrees,
                                onToggleCoordinateFormat = onToggleCoordinateFormat,
                            )
                        } else if (kind == StripReadout.NO_FIX || lastLocation == null) {
                            // One statement across the strip — see this composable's own doc comment. Not
                            // tappable: there is no coordinate pair to toggle, and nothing to fabricate one
                            // from. The Row's remaining width, so it centres where the three segments did.
                            Text(
                                text = NO_FIX_MESSAGE,
                                style = stripReadoutStyle(),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                // Landscape B2 (S4): no weight when content-width, or this one line
                                // would stretch the strip back across the window.
                                modifier = Modifier
                                    .then(if (contentWidth) Modifier else Modifier.fillMaxWidth())
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
                            // Dispatch 2026-09-28-685, Amendment 2 (RECORD -699; the owner: "Coordinates take priority
                            // (Recommended)"): when the strip runs short, the facing and altitude readouts give way first,
                            // shortening with "…" and then dropping out, and the coordinates stay whole. Since RECORD -713 the
                            // shortening is gone: labels drop first, then facing, then altitude, each shown value whole. Measured
                            // here, in the width this row is offered, by readoutsFitBeside; the coordinates are measured first.
                            // Facing drops before altitude: the needle beside it still shows the direction.
                            val readoutStyle = stripReadoutStyle().copy(fontFeatureSettings = "tnum")
                            val readoutLabelStyle = stripReadoutStyle()
                            val readoutMeasurer = rememberTextMeasurer()
                            val headingLabel = stripHeadingLabel(heading)
                            val headingText = stripHeadingText(heading)
                            // RECORD -735: the crossed-out compass in landscape, measured as the icon's square, not the words.
                            val headingIcon = shortHeadingStatus && heading.isCompassStatus()
                            val elevationLabel = ALTITUDE_LABEL.takeIf { elevationMeters != null }
                            val elevationText = elevationMeters?.let { formatWholeLength(it, unitSystem) } ?: ELEVATION_UNAVAILABLE_TEXT
                            val coordinatesText = coordinatesStripText(lastLocation, showDecimalDegrees)
                            BoxWithConstraints(contentAlignment = Alignment.Center) {
                                val density = LocalDensity.current
                                fun widthOf(text: String, style: TextStyle) = readoutMeasurer.measure(text, style, maxLines = 1, softWrap = false).size.width
                                fun labelPx(label: String?) = label?.let { widthOf(it, readoutLabelStyle) + with(density) { Spacing.xs.roundToPx() } } ?: 0
                                val availablePx = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
                                // RECORD -987: in landscape the readouts give way as if the wider coordinate form were showing, so
                                // switching forms changes only the coordinates, never which readouts or labels are shown.
                                val coordinatesPx = if (coordinatesSizedForBothForms) {
                                    coordinatesBothFormsWidthPx(lastLocation, ::widthOf, readoutStyle)
                                } else {
                                    widthOf(coordinatesText, readoutStyle)
                                }
                                val separatorPx = widthOf("·", readoutLabelStyle) + with(density) { 2 * Spacing.sm.roundToPx() }
                                // RECORD -713 (the owner: "Drop labels, then values (Recommended)"): labels go first, then facing,
                                // then altitude; whatever is shown is whole. See readoutsFitBeside.
                                val fit = readoutsFitBeside(
                                    availablePx = availablePx,
                                    coordinatesPx = coordinatesPx,
                                    labelsPx = listOf(labelPx(headingLabel), labelPx(elevationLabel)),
                                    valuesPx = listOf(
                                        if (headingIcon) with(density) { stripHeadingIconSize().roundToPx() } else widthOf(headingText, readoutStyle),
                                        widthOf(elevationText, readoutStyle),
                                    ),
                                    separatorPx = separatorPx,
                                )
                            Row(
                                // Landscape B2 (S4): no weight when content-width.
                                modifier = if (contentWidth) Modifier else Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                            ) {
                                // With a fix, the heading has two real states and one transient: a value,
                                // no sensor, or NeedsFix for the frame or two between the fix landing and
                                // the next sensor emission (rememberTrueHeading restarts its producer on
                                // that transition). The transient shows a dash, the same as the HUD — the
                                // "needs a fix" wording is gone, replaced by NO_FIX_MESSAGE above.
                                // Motion Part 2, item 6: its words (the compass point, or a status) crossfade; its
                                // degrees change at once (WordSwap). The elevation's likewise.
                                // Dispatch 2026-09-28-677 (the owner, RECORD -656: "heading and altitude labelled"): each
                                // reading after its short label, as on the navigation display (LabelledReadout); a status
                                // ("Compass unavailable", "Elevation unavailable") names itself and has none.
                                if (fit.shown[0]) {
                                    Box {
                                        if (headingIcon) StripHeadingStatusIcon(heading) else
                                        LabelledReadout(label = headingLabel.takeIf { fit.labels }, labelStyle = readoutLabelStyle) {
                                            WordSwap(text = headingText) { shownText ->
                                                Text(
                                                    text = shownText,
                                                    // Landscape B2 (S5): tabular figures, so the strip's width holds
                                                    // steady as the digits change. Both orientations; labelMedium kept.
                                                    style = readoutStyle,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.testTag(COMPASS_STRIP_HEADING_TAG),
                                                )
                                            }
                                        }
                                    }
                                    Text("·", style = readoutLabelStyle)
                                }
                                // Follows the Units setting (dispatch 2026-09-28-549); the value stays metres.
                                if (fit.shown[1]) {
                                    Box {
                                        LabelledReadout(label = elevationLabel.takeIf { fit.labels }, labelStyle = readoutLabelStyle) {
                                            WordSwap(text = elevationText) { shownText ->
                                                Text(
                                                    text = shownText,
                                                    style = readoutStyle,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.testTag(COMPASS_STRIP_ELEVATION_TAG),
                                                )
                                            }
                                        }
                                    }
                                    Text("·", style = readoutLabelStyle)
                                }
                                // Whole whenever it fits the strip at all: measured before the readouts (no weight). The
                                // ellipsis is the last resort for a strip too narrow even for the coordinates alone.
                                Text(
                                    text = coordinatesText,
                                    style = readoutStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickableWithShapedPress(onClick = onToggleCoordinateFormat)
                                        .testTag(COMPASS_STRIP_COORDINATES_TAG),
                                )
                            }
                            }
                        }
                    }
                    }
                }
            }
            // Motion Part 2, item 4 (dispatch 2026-09-28-666, scout C1; the owner, RECORD -651: "Fade and grow", from where it
            // belongs): when its window opens the line fades in and the strip grows down to hold it, from under the readout,
            // and the reverse when it closes; the fade alone, with the strip's height changing at once, under reduced motion.
            // Unlike the map's pop-ups this grow is laid out, not drawn: the strip's own background has to grow with it. The
            // strip takes no touch outside its coordinates (the Box's comment above), so the growing band moves no touch;
            // what reads the strip's height (the notice below the bar, the fan's keep-out) follows it frame by frame. Item 6:
            // a change in its words crossfades, a change in its times alone does not. The Back by line below does the same
            // (merged in with dispatch 2026-09-28-645).
            val sundownLineShown = rememberLastShown(sundownLine)
            val reduceMotion = LocalReduceMotion.current
            val lineFade = MotionTokens.mapPopUpFadeSpec<Float>()
            val lineGrow = MotionTokens.mapPopUpGrowSpec<IntSize>()
            AnimatedVisibility(
                visible = sundownLine != null,
                enter = if (reduceMotion) fadeIn(animationSpec = lineFade) else fadeIn(animationSpec = lineFade) + expandVertically(animationSpec = lineGrow, expandFrom = Alignment.Top),
                exit = if (reduceMotion) fadeOut(animationSpec = lineFade) else fadeOut(animationSpec = lineFade) + shrinkVertically(animationSpec = lineGrow, shrinkTowards = Alignment.Top),
                label = "stripSundownLine",
            ) {
                sundownLineShown?.let { line ->
                    WordSwap(
                        text = line,
                        modifier = Modifier
                            .padding(horizontal = Spacing.sm)
                            .then(if (contentWidth) Modifier else Modifier.fillMaxWidth()),
                        contentAlignment = Alignment.Center,
                    ) { shownLine ->
                        StripEveningLine(line = shownLine, tag = STRIP_SUNDOWN_LINE_TAG, contentWidth = contentWidth)
                    }
                }
            }
            // Back by's line (dispatch 2026-09-28-645; RECORD -687), under the sundown line, with the same pop-up grow and
            // word crossfade as the line above, so the two behave alike when either window opens or closes.
            val backByLineShown = rememberLastShown(backByLine)
            AnimatedVisibility(
                visible = backByLine != null,
                enter = if (reduceMotion) fadeIn(animationSpec = lineFade) else fadeIn(animationSpec = lineFade) + expandVertically(animationSpec = lineGrow, expandFrom = Alignment.Top),
                exit = if (reduceMotion) fadeOut(animationSpec = lineFade) else fadeOut(animationSpec = lineFade) + shrinkVertically(animationSpec = lineGrow, shrinkTowards = Alignment.Top),
                label = "stripBackByLine",
            ) {
                backByLineShown?.let { line ->
                    WordSwap(
                        text = line,
                        modifier = Modifier
                            .padding(horizontal = Spacing.sm)
                            .then(if (contentWidth) Modifier else Modifier.fillMaxWidth()),
                        contentAlignment = Alignment.Center,
                    ) { shownLine ->
                        StripEveningLine(line = shownLine, tag = STRIP_BACK_BY_LINE_TAG, contentWidth = contentWidth)
                    }
                }
            }
            }
            if (quickSettings != null) MapQuickSettingsButton(quickSettings)
            }
            navigationInfo?.invoke()
            }
        }
    }
}
/**
 * The strip's heading text, shared by its with-fix row and its position-note row (dispatch
 * 2026-09-28-510), so the two word the compass identically.
 */
internal fun stripHeadingText(heading: TrueHeadingReading): String = when (heading) {
    is TrueHeadingReading.Available -> "${heading.degrees.roundToInt() % 360}° ${cardinalDirection(heading.degrees)}"
    TrueHeadingReading.NoSensor -> "Compass unavailable"
    // Present but not to be trusted (compass-reliability dispatch).
    // Names no cause: the status cannot tell a truck from a poorly
    // calibrated sensor, and the remedies differ — telling someone to
    // calibrate beside a truck is wrong advice confidently given.
    TrueHeadingReading.Unreliable -> "Compass unreliable"
    TrueHeadingReading.NeedsFix -> NO_HEADING_TEXT
}

/**
 * Dispatch 2026-09-28-677 gave the heading a label on the strip, "Facing", for a reading only. RECORD -728, the owner: "Keep
 * the the 330° NW metric, just remove the word "facing" and nothing else." So no heading state has a label now.
 */
@Suppress("UNUSED_PARAMETER")
private fun stripHeadingLabel(heading: TrueHeadingReading): String? = null

/** What the compass strip's readout shows (motion Part 2, Amendment 1, item 3): the key its crossfade runs on. */
private enum class StripReadout { POSITION_NOTE, NO_FIX, READOUT, NAVIGATING }
