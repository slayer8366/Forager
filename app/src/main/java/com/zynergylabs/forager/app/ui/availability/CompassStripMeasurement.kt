package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapControlsUi.kt (dispatch 2026-10-19-12, UI split Phase 1, PR 8).

import androidx.compose.ui.semantics.text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatWholeLength
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The compass strip's heading text — one of the two places a heading can appear, never both at once (the strip hides while the HUD shows). */
internal const val COMPASS_STRIP_HEADING_TAG = "compass-strip-heading"

/** The compass strip's whole content while there is no fix — one message, [NO_FIX_MESSAGE], in place of three fragments. */
internal const val COMPASS_STRIP_NO_FIX_TAG = "compass-strip-no-fix"

/** The compass strip's elevation reading (dispatch 2026-09-28-677), its own node beside its label. */
internal const val COMPASS_STRIP_ELEVATION_TAG = "compass-strip-elevation"

/** The compass strip's coordinates, the tap that switches their format (dispatch 2026-09-28-685, Amendment 2). */
internal const val COMPASS_STRIP_COORDINATES_TAG = "compass-strip-coordinates"

/** The compass strip's north arrow's fixed slot (dispatch 2026-10-13-02, RECORD -983), for tests. */
internal const val COMPASS_STRIP_NEEDLE_TAG = "compass-strip-needle"

/**
 * Dispatch 2026-10-13-02, RECORD -983 (the owner: "Another thing for the landscape mode UI, have the telemetry centered between
 * the compass arrow and the three dot menu. Right now it's way too close to the arrow"), and RECORD -984 for portrait (the owner:
 * "Have portrait more centered also. This will matter most to people with OCD"): how far the strip's readout moves toward the
 * three-dot button so that its centre is midway between the arrow and the dots.
 *
 * The readout's slot runs from the arrow's trailing edge (or, navigating, with no arrow (RECORD -950), from the line's leading
 * padding, [Spacing.sm] in from the strip's leading edge) to [Spacing.sm] short of the button, and the readout is centred in it.
 * The dots start [QUICK_SETTINGS_DOTS_INSET] inside the button. So the slot's centre is off the wanted midpoint by half the
 * difference between what lies beyond each end: [Spacing.sm] plus the dots' inset after it, and nothing (the arrow) or
 * [Spacing.sm] (the strip's leading edge, navigating) before it. A placement only: nothing is measured differently, so the
 * readout's width, the fit rules (RECORD -699, -713) and the strip's width (RECORD -951) are untouched. At most it overlaps
 * the button's tap target by that half difference less the slack left of its slot, short of the dots.
 */
internal fun stripReadoutShiftToMenu(navigating: Boolean): Dp {
    val beyondEnd = Spacing.sm + QUICK_SETTINGS_DOTS_INSET
    val beforeStart = if (navigating) Spacing.sm else 0.dp
    return (beyondEnd - beforeStart) / 2
}


/**
 * RECORD -728, the owner: "Go up to 14 sp". The compass strip's readout line (heading, altitude and its label, coordinates,
 * the dots between them, "Location services unavailable" and the position note) is 14 sp, up from labelMedium's 12 sp, in
 * labelMedium's weight with labelLarge's 20 sp line height (labelLarge is 14 sp on 20 sp). The sundown and Back by lines under
 * it, the navigation display and the search bar keep labelMedium. readoutsFitBeside measures with this style, so what gives
 * way at a given width follows it.
 */
internal val STRIP_READOUT_FONT_SIZE = 14.sp
internal val STRIP_READOUT_LINE_HEIGHT = 20.sp

/** The strip's readout style: see [STRIP_READOUT_FONT_SIZE]. */
@Composable
internal fun stripReadoutStyle(): TextStyle =
    MaterialTheme.typography.labelMedium.copy(fontSize = STRIP_READOUT_FONT_SIZE, lineHeight = STRIP_READOUT_LINE_HEIGHT)

/** What [readoutsFitBeside] keeps: whether the readouts' labels are drawn, and which readouts are. */
internal data class ReadoutsFit(val labels: Boolean, val shown: List<Boolean>)

/**
 * RECORD -713 (the owner: "Drop labels, then values (Recommended)"), for the strip and the navigation display's second row
 * alike. Replaces the earlier rule (readoutsKeptBeside, which shortened them with "…"; deleted with RECORD -714) in both places. The coordinates are measured first and stay
 * whole. Then, in order, the first arrangement that fits whole is kept:
 * every readout with its label; every readout without labels ("315° NW · 9843 ft · grid ref"); then readouts dropped from
 * the front (facing first, then altitude), still without labels; then none. A readout is never cut and never ends in "…".
 *
 * [labelsPx] is each readout's label with its gap (0 for one that names itself, "Compass unavailable"); [valuesPx] its
 * value. Each readout kept costs one [separatorPx] (the dot and its gaps): one before the coordinates when there are any
 * ([withCoordinates]), else one between readouts. [coordinatesPx] is 0 with no coordinates.
 */
internal fun readoutsFitBeside(
    availablePx: Int,
    coordinatesPx: Int,
    labelsPx: List<Int>,
    valuesPx: List<Int>,
    separatorPx: Int,
    withCoordinates: Boolean = true,
): ReadoutsFit {
    val n = valuesPx.size
    fun fits(labels: Boolean, dropped: Int): Boolean {
        val kept = (dropped until n)
        val separators = if (withCoordinates) kept.count() else (kept.count() - 1).coerceAtLeast(0)
        val needed = coordinatesPx.toLong() + separators.toLong() * separatorPx +
            kept.sumOf { valuesPx[it].toLong() + if (labels) labelsPx[it].toLong() else 0L }
        return needed <= availablePx
    }
    if (fits(labels = true, dropped = 0)) return ReadoutsFit(labels = true, shown = List(n) { true })
    for (dropped in 0..n) {
        if (fits(labels = false, dropped = dropped)) return ReadoutsFit(labels = false, shown = List(n) { it >= dropped })
    }
    return ReadoutsFit(labels = false, shown = List(n) { false })
}

/**
 * RECORD -732 (the owner: "Join moves to fit the strip (Recommended)"): what the landscape strip needs to show its readout
 * line whole, in pixels, measured the way the strip and readoutsFitBeside measure it: the row's padding (Spacing.sm each side),
 * the needle's 18 dp, the three-dot button's square when there is one, and the readouts without labels (heading, altitude,
 * coordinates, each after its separator). The heading is measured as its widest form ("000°" and the widest compass point,
 * tabular figures), not its current one, so the join does not move as the phone turns. With no fix, the no-fix line. Read by
 * [landscapeStripFit]; the coordinates-only figure is the narrowest the strip may be (coordinates never drop).
 *
 * Dispatch 2026-10-13-02, RECORD -987 (the owner: "tapping the coordinates in the strip changes it as expected, but the change
 * causes strip to change size, and the navigation HUD at the bottom changes along with it ... can the strip stay the same
 * size?"): the coordinates are measured as the wider of their two forms at this position, the grid reference and the decimal
 * latitude/longitude ([coordinatesBothFormsWidthPx]), not the one showing, so switching between them leaves the strip's width,
 * and the bar's and the navigation card's with it, where they were. It no longer reads which form is showing.
 */
@Composable
internal fun rememberLandscapeStripNeed(
    elevationMeters: Double?,
    unitSystem: UnitSystem,
    location: LatLng?,
    hasQuickSettings: Boolean,
): LandscapeStripNeed {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val readoutStyle = stripReadoutStyle().copy(fontFeatureSettings = "tnum")
    val labelStyle = stripReadoutStyle()
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    return with(density) {
        val chrome = 2 * Spacing.sm.roundToPx() + STRIP_NEEDLE_SIZE.roundToPx() + if (hasQuickSettings) QUICK_SETTINGS_TAP_TARGET.roundToPx() else 0
        if (location == null) {
            val noFix = chrome + widthOf(NO_FIX_MESSAGE, labelStyle)
            LandscapeStripNeed(noFix, noFix)
        } else {
            val coordinates = coordinatesBothFormsWidthPx(location, ::widthOf, readoutStyle)
            val separator = widthOf("·", labelStyle) + 2 * Spacing.sm.roundToPx()
            val heading = landscapeHeadingValueWidthPx(::widthOf, readoutStyle)
            val elevation = widthOf(elevationMeters?.let { formatWholeLength(it, unitSystem) } ?: ELEVATION_UNAVAILABLE_TEXT, readoutStyle)
            LandscapeStripNeed(fullPx = chrome + coordinates + 2 * separator + heading + elevation, coordinatesOnlyPx = chrome + coordinates)
        }
    }
}

/**
 * Dispatch 2026-10-13-02, RECORD -987: the coordinates' width at [location] in whichever of their two forms is wider, the grid
 * reference or the decimal latitude/longitude, in tabular figures as the strip draws them. The landscape strip is sized from
 * this and gives way by it, so switching forms moves nothing.
 */
internal fun coordinatesBothFormsWidthPx(location: LatLng, widthOf: (String, TextStyle) -> Int, readoutStyle: TextStyle): Int =
    maxOf(widthOf(coordinatesStripText(location, false), readoutStyle), widthOf(coordinatesStripText(location, true), readoutStyle))

/**
 * RECORD -732, -733: the width the landscape strip keeps for its heading, its widest value form ("000°" and the widest compass
 * point, tabular figures). The strip is sized from this alone, so the join is the same in every compass state; since RECORD
 * -735 the status there is [StripHeadingStatusIcon], whose square ([stripHeadingIconSize]) fits within this width at fonts 1.0
 * and 2.0 (LandscapeHeadingIconFitTest).
 */
internal fun landscapeHeadingValueWidthPx(widthOf: (String, TextStyle) -> Int, readoutStyle: TextStyle): Int =
    (0 until 8).maxOf { widthOf("000° ${cardinalDirection(it * 45f)}", readoutStyle) }

/** RECORD -735: no compass, or an unreliable one: the readings the landscape strip shows as the crossed-out compass. */
internal fun TrueHeadingReading.isCompassStatus(): Boolean =
    this == TrueHeadingReading.NoSensor || this == TrueHeadingReading.Unreliable

/** RECORD -735: the crossed-out compass's square, the strip readout's line height (20 sp), so it sits in the line. */
@Composable
internal fun stripHeadingIconSize(): Dp = with(LocalDensity.current) { STRIP_READOUT_LINE_HEIGHT.toDp() }

/**
 * RECORD -735 (the owner: "Same crossed-out icon (Recommended)"): Material's `Icons.Filled.ExploreOff`, the crossed-out
 * compass, in the landscape strip's heading slot for both no compass and an unreliable one. Its content description is the
 * portrait words, "Compass unavailable" or "Compass unreliable", so a screen reader still tells them apart. No heading value is
 * shown or carried: an unreliable reading has none (`TrueHeadingReading.Unreliable`), on purpose.
 */
@Composable
internal fun StripHeadingStatusIcon(heading: TrueHeadingReading) {
    Icon(
        imageVector = Icons.Filled.ExploreOff,
        contentDescription = stripHeadingText(heading),
        modifier = Modifier.size(stripHeadingIconSize()).testTag(COMPASS_STRIP_HEADING_ICON_TAG),
    )
}

/** RECORD -735: the crossed-out compass in the landscape strip's heading slot. */
internal const val COMPASS_STRIP_HEADING_ICON_TAG = "compass-strip-heading-icon"

/** The strip's compass needle (RECORD -732 measures the strip's need with it). */
internal val STRIP_NEEDLE_SIZE = 18.dp

/** The strip's back-by line (dispatch 2026-09-28-645). */
internal const val STRIP_BACK_BY_LINE_TAG = "strip-back-by-line"
