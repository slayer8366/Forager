package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapControlsUi.kt (dispatch 2026-10-19-12, UI split Phase 1, PR 8).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading

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
    /** Dispatch 2026-09-28-549: the Units setting, which the elevation follows. */
    unitSystem: UnitSystem,
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
    /**
     * RECORD -735 (the owner: "Same crossed-out icon (Recommended)"): in a short landscape window, with no compass or an
     * unreliable one, the heading slot shows a crossed-out compass ([StripHeadingStatusIcon]) in place of the words, which do
     * not fit the room sized for the heading's value (-733's "No compass" and "Compass?" were measured too wide). Portrait
     * keeps the full words. False everywhere else.
     */
    shortHeadingStatus: Boolean = false,
    /**
     * Dispatch 2026-09-28-510: what the strip says while the position is approximate or last known
     * ([rememberPositionNote]), read here, in this leaf, so its ticking age recomposes the strip alone.
     */
    positionNote: State<PositionNote?>? = null,
    /**
     * The sundown line while recording (dispatch 2026-09-28-592, plan task T3), on its own line under
     * the readout; `null` draws nothing and the strip is the height it was.
     */
    sundownLine: String? = null,
    /**
     * Back by's line (dispatch 2026-09-28-645): "Back by 3:30 PM", on its own line under the sundown
     * line, in the last hour before the time ([backByLineText]); `null` draws nothing.
     */
    backByLine: String? = null,
    /**
     * The map's quick settings (dispatch 2026-09-28-645, Amendments 1 to 3): the three-dot button at
     * the strip's far right, [MapQuickSettingsButton]. `null` draws no button, and the strip is the
     * height it was.
     */
    quickSettings: MapQuickSettings? = null,
    /** Dispatch 2026-10-12-06: the rounded card's corners, and how far its fill reaches up behind the pill (RoundedMapCard.kt). */
    cardCorners: MapCardCorners = MapCardCorners.BOTTOM,
    tuck: Dp = 0.dp,
    /**
     * Dispatch 2026-10-12-08 (RECORD -948, -950): the strip stays while navigating, and its line drops the north arrow and the
     * heading, which the navigation info shows once. Height, grid reference and the three-dot button stay here.
     */
    navigating: Boolean = false,
    /**
     * Dispatch 2026-10-12-08 (RECORD -946, -951): the strip's fill; `null` is the map chrome token. In landscape the strip and
     * the search pill are the top bar, both in the pill's lighter shade ([com.zynergylabs.forager.app.ui.map.MapPillColorLight]).
     */
    fill: Color? = null,
    /**
     * Dispatch 2026-10-12-08 (RECORD -950, portrait): "have the card just extend down rather than a dual tuck". The navigation
     * info, drawn inside this strip's own card under its line, so the two are one card with one fill and one shadow. `null`
     * draws nothing, and the strip is the height it was.
     */
    navigationInfo: (@Composable () -> Unit)? = null,
    /**
     * Dispatch 2026-10-13-02 (RECORD -966, -967): the sides of the strip's card that carry the map chrome outline
     * ([portraitStripOutlineSides], [landscapeColumnOutlineSides]); drawn at the card's size, so while navigating in portrait it
     * grows down with the card. `null` draws none.
     */
    outlineSides: MapChromeOutlineSides? = null,
    /**
     * Dispatch 2026-10-13-02, RECORD -983 and -984: the readout centred between the arrow (navigating, the strip's leading edge)
     * and the three-dot button's dots ([stripReadoutShiftToMenu]), in both orientations. False keeps it centred in its slot, as
     * before; the map screen passes true.
     */
    readoutCentredOnMenu: Boolean = false,
    /**
     * Dispatch 2026-10-13-02, RECORD -987: the readouts give way as if the wider of the two coordinate forms were showing
     * ([coordinatesBothFormsWidthPx]), as the landscape strip is sized for it, so tapping the coordinates changes only them.
     * False measures the form showing, as before (portrait, whose strip is the window's width whichever form shows).
     */
    coordinatesSizedForBothForms: Boolean = false,
) {
    // Dispatch 2026-10-12-08: while navigating the line shows no heading, so the heading State is not read here then, and only the
    // navigation info's leaf reads it at sensor rate, as when the strip was removed while navigating.
    val reading = if (navigating) TrueHeadingReading.NeedsFix else heading.value
    CompassElevationStripContent(
        heading = reading,
        elevationMeters = elevationMeters,
        unitSystem = unitSystem,
        location = location,
        showDecimalDegrees = showDecimalDegrees,
        onToggleCoordinateFormat = onToggleCoordinateFormat,
        modifier = modifier,
        contentWidth = contentWidth,
        shortHeadingStatus = shortHeadingStatus,
        positionNote = positionNote?.value,
        sundownLine = sundownLine,
        backByLine = backByLine,
        quickSettings = quickSettings,
        cardCorners = cardCorners,
        tuck = tuck,
        navigating = navigating,
        fill = fill,
        navigationInfo = navigationInfo,
        outlineSides = outlineSides,
        readoutCentredOnMenu = readoutCentredOnMenu,
        coordinatesSizedForBothForms = coordinatesSizedForBothForms,
    )
}
