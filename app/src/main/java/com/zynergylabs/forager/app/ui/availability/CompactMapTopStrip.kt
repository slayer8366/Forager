package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-15, UI split Phase 1, PR 9b).

import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.map.MapPillColorLight
import com.zynergylabs.forager.app.ui.map.MapPillColorDark
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.State
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge

/**
 * The compass strip, its measured size and the landscape join, moved out of [CompactMapTab] unchanged (RECORD -741). The three
 * measured sizes are the tab's state, handed in ([CompactMapLayoutState] since UI split Phase 2.3).
 *
 * Dispatch 2026-10-12-08 (RECORD -948, -950, -951): the strip stays while navigating, in both orientations, exactly where it is
 * and at the size it is (the owner: "The compass strip stays exactly where it is in both modes"). Its line drops the north arrow
 * and the heading ([CompassElevationStrip]'s `navigating`), and its own sundown and Back by lines go to the navigation info. In
 * portrait the navigation info ([portraitNavigationInfo]) is drawn inside the strip's card under a thin line, one card that
 * extends down ("have the card just extend down rather than a dual tuck"); in landscape it is a card of its own hanging under the
 * strip ([CompactMapNavigationDisplay]).
 */
@Composable
internal fun BoxScope.CompactMapTopStrip(
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    landscape: CompactMapLandscape,
    /** Dispatch 2026-10-13-02 (RECORD -967): whether the pill and the rail have slid away, leaving the strip's sides by them facing the map. */
    isFullscreen: Boolean,
    controlsPadding: PaddingValues,
    topInset: Dp,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    trueHeading: State<TrueHeadingReading>,
    positionNote: State<PositionNote?>,
    sundownClock: SundownClock,
    /** The three measured sizes, written here; see [CompactMapLayoutState]. */
    layout: CompactMapLayoutState,
    /** Portrait only: the navigation info inside the strip's card, under its line (see this function's doc). */
    portraitNavigationInfo: @Composable () -> Unit,
) {
    val isNavigating = navigation.isNavigating
    val railPortEdge = landscape.railPortEdge
    val punchHoleEdge = landscape.punchHoleEdge
    val onLandscapeStripHeightMeasured = landscape.onLandscapeStripHeightMeasured
    val onLandscapeStripWidthMeasured = landscape.onLandscapeStripWidthMeasured
    val compassStripDensity = LocalDensity.current
    // Dispatch 2026-10-12-06 (RECORD -932, -934): in portrait the strip is the rounded card tucked under the pill; none in
    // landscape, where the two sit side by side, or once the pill has slid away in fullscreen (mapCardTuck).
    val stripTuck = if (railPortEdge == null) mapCardTuck(topInset) else 0.dp
    val isDarkTheme = LocalForagerDarkTheme.current
    // Dispatch 2026-10-13-02 (RECORD -966, -967, -976): the strip's outline. Portrait: its bottom and its two sides, its top open
    // under the pill (portraitStripOutlineSides; the sides since -976). Landscape (its top and far end since -988): its bottom; its side
    // by the pill once the pill has slid away (the pill draws that join while shown); its rail side once the rail has, unless
    // that side is then on the screen's edge.
    val stripLayoutDirection = LocalLayoutDirection.current
    val stripOutlineSides = if (railPortEdge == null) {
        portraitStripOutlineSides()
    } else {
        landscapeColumnOutlineSides(
            railPortEdge = railPortEdge,
            railShown = !isFullscreen,
            railSideInset = if (railPortEdge == ScreenEdge.Left) {
                controlsPadding.calculateLeftPadding(stripLayoutDirection)
            } else {
                controlsPadding.calculateRightPadding(stripLayoutDirection)
            },
            innerSideDrawn = isFullscreen,
            // RECORD -988 (the owner: "The pill in landscape isn't fully outlined"): the strip too, all the way round: its top,
            // and its far end once the rail has slid away. Its side by the pill stays open while the pill is shown (the pill draws
            // that join) and its side by the rail while the rail is (the rail draws it). Until -988 its top was never drawn.
            top = true,
            bottom = true,
            railSideOnScreenEdgeDrawn = true,
        )
    }
    CompassElevationStrip(
        heading = trueHeading,
        elevationMeters = uiState.liveAltitudeMeters,
        unitSystem = uiState.unitSystem,
        location = uiState.liveLocation,
        showDecimalDegrees = showDecimalDegrees,
        onToggleCoordinateFormat = onToggleCoordinateFormat,
        // Full width, "just below" SearchEntryBar rather than a narrow floating pill with margins on both sides, per the project
        // owner's own redesign call; topInset is how that clearance reaches here (0.dp reproduces the flush-against-the-map-top
        // behaviour). In landscape, Landscape B2 (S4): the top corner on the rail side, below the status bar only.
        modifier = if (railPortEdge != null) {
            Modifier
                .align(if (railPortEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                .padding(controlsPadding)
                // RECORD -732 (the owner: "Join moves to fit the strip (Recommended)"; -729 had it at the window's centre): as wide
                // as its readouts need whole, the bar keeping its floor (landscapeStripFit). RECORD -951 (the owner: "Keep it exactly
                // like this size. Exactly as we have it today") restores it over RECORD -940's short-side width. The need measures
                // the heading at its widest form whether or not it is drawn, so starting navigation, which takes the heading off the
                // line, resizes nothing.
                .landscapeStripFit(
                    railPortEdge,
                    punchHoleEdge,
                    rememberLandscapeStripNeed(uiState.liveAltitudeMeters, uiState.unitSystem, uiState.liveLocation, quickSettings != null),
                    rememberLandscapeBarFloorPx(uiState, uiState.distanceUnit),
                )
                // RECORD -729: its measured height and width go up to the scaffold, which gives the bar that height and the rest
                // of the room; since RECORD -951 navigating too, and the navigation card hangs under it at this width.
                .onSizeChanged { layout.landscapeStripHeightPx = it.height; layout.landscapeStripWidthPx = it.width }
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .padding(controlsPadding)
                .fillMaxWidth()
                // Dispatch 2026-10-12-06: tucked under the pill, the card's fill reaching [stripTuck] above its own top, while its
                // own bounds start at the bar's bottom.
                .padding(top = topInset)
                // The strip's own height (item 2), for what sits below it, as before only while not navigating: navigating, the
                // card grows down by the navigation info, and what sat below the strip keeps the clearance it had while the
                // navigation display replaced the strip (dispatch 2026-10-12-08 changes none of it).
                .onSizeChanged { if (!isNavigating) layout.compassStripHeightPx = it.height }
        }.mapKeepOut(MapKeepOutIds.TOP_STRIP),
        contentWidth = false,
        // RECORD -735: the crossed-out compass for no compass or an unreliable one in a short landscape window; portrait keeps the
        // full words. Restored with main's landscape width (RECORD -951).
        shortHeadingStatus = railPortEdge != null,
        positionNote = positionNote,
        // Amendment 2 (RECORD -595): hidden until its window opens; see isShown. Dispatch 2026-10-12-08: not while navigating,
        // when the navigation info shows the evening lines once (RECORD -950: "Nothing repeats").
        sundownLine = if (isNavigating) null else recordingSundownLine?.let { sundownLineText(it, sundownClock) },
        backByLine = if (isNavigating) null else backByLineText(quickSettings?.backBy, sundownClock),
        // Amendment 3 (RECORD -648): "a 3 dot menu at the far right", placed by the strip, not the screen (RECORD -649): the
        // strip's own right end in every orientation. Dispatch 2026-10-12-08: navigating too, and only here (RECORD -947, -950).
        quickSettings = quickSettings,
        cardCorners = landscapeStripCorners(railPortEdge, punchHoleEdge),
        tuck = stripTuck,
        navigating = isNavigating,
        // RECORD -946, -951: in landscape the strip and the pill are the top bar, both in the pill's lighter shade.
        fill = if (railPortEdge != null) (if (isDarkTheme) MapPillColorDark else MapPillColorLight) else null,
        navigationInfo = if (railPortEdge == null) portraitNavigationInfo else null,
        outlineSides = stripOutlineSides,
        // Dispatch 2026-10-13-02, RECORD -983 (landscape) and -984 (the owner: "Have portrait more centered also. This will
        // matter most to people with OCD"): both orientations.
        readoutCentredOnMenu = true,
        // Dispatch 2026-10-13-02, RECORD -987: landscape, where the strip is sized for the wider coordinate form.
        coordinatesSizedForBothForms = railPortEdge != null,
    )
    DisposableEffect(Unit) { onDispose { layout.compassStripHeightPx = 0; layout.landscapeStripHeightPx = 0; layout.landscapeStripWidthPx = 0 } }
    // The portrait strip's measured height goes the moment navigation starts, as it did when the strip left then (see above).
    LaunchedEffect(isNavigating) { if (isNavigating) layout.compassStripHeightPx = 0 }
    // RECORD -754 (the owner's S26 on 1.0.3103: after a turn from portrait the bar kept a portrait-sized box, taller than the
    // strip and reaching the window's centre): out of landscape the landscape strip's measures go too. They are reported up only
    // when they change (below), and the scaffold zeroes its own copy whenever the bar stops meeting the strip, so a measure left
    // here from an earlier landscape turn was the same number on the next turn, was never reported again, and the bar stayed at
    // its own height and the half width it takes before the strip is measured. Found by a test turning into landscape twice
    // (LandscapeSearchFixesTest, "second turn"), not by reasoning.
    // RECORD -1466 (Sorrel's observation, confirmed from the code; the owner: "Fix now, show me (Recommended)"): and into landscape
    // the portrait strip's measured height goes, as it does when navigation starts. The turn keeps the Activity and this tab, the
    // landscape strip writes only its own two measures, so the portrait height stayed in use for the bubble's top limit, which
    // then sat lower than on a start in landscape (LandscapeStripHeightTest). One frame at the turn still has it, as above.
    LaunchedEffect(railPortEdge) { if (railPortEdge == null) { layout.landscapeStripHeightPx = 0; layout.landscapeStripWidthPx = 0 } else layout.compassStripHeightPx = 0 }
    // RECORD -729: the landscape strip's measured height, up to the scaffold, which gives the search bar that height; 0 while it
    // is not measured (portrait, before its first layout), where the bar keeps its own.
    LaunchedEffect(layout.landscapeStripHeightPx) { onLandscapeStripHeightMeasured(with(compassStripDensity) { layout.landscapeStripHeightPx.toDp() }) }
    LaunchedEffect(layout.landscapeStripWidthPx) { onLandscapeStripWidthMeasured(with(compassStripDensity) { layout.landscapeStripWidthPx.toDp() }) }
    // RECORD -729, item 4 drew a 1 dp vertical line where the bar and the strip meet in landscape. Dispatch 2026-10-12-06
    // (RECORD -934, the owner's answer confirming "in landscape, the pill and card touch edge to edge (no join line)"): the
    // line is gone; the two meet square on their joined side and round on their outer sides (landscapeStripCorners, the bar's
    // pillCorners in the scaffold). LANDSCAPE_BAR_STRIP_LINE_TAG is kept so a test can say no such line is drawn. Dispatch
    // 2026-10-13-02 (RECORD -967, the owner: "1 C one line between the two like there is now") replaces -934's "no join line":
    // the pill's map chrome outline runs down the join, one 1 dp line drawn by the pill (MapChromeOutlineSides); no node of its
    // own, so that tag is still never composed.
}

/** Landscape: rounded on its outer side and square where it meets the pill, edge to edge, no join line (RECORD -934); portrait, its lower two. */
private fun landscapeStripCorners(railPortEdge: ScreenEdge?, punchHoleEdge: ScreenEdge?): MapCardCorners = when {
    railPortEdge == null -> MapCardCorners.BOTTOM
    punchHoleEdge != null && railPortEdge != punchHoleEdge -> MapCardCorners.outerSide(railPortEdge)
    else -> MapCardCorners.ALL
}
