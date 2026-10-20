package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-15, UI split Phase 1, PR 9b).

import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.runtime.State
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.mapIconStackBorderColor
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The navigation info's inputs, worked out once for both places it is drawn (dispatch 2026-10-12-08): inside the compass strip's
 * card in portrait, and in its own card under the strip in landscape. The sundown line is the recording's, or with no recording
 * sunset and dark only, computed here; either hidden until its window opens (Amendment 2, RECORD -595).
 */
@Composable
internal fun compactMapEveningLines(
    uiState: AvailabilityUiState,
    currentTime: CurrentTimeProvider,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    sundownClock: SundownClock,
): Pair<String?, String?> {
    val sundown = (recordingSundownLine ?: rememberScreenSundownLine(uiState.headingFix?.let { com.zynergylabs.forager.app.domain.model.LatLng(it.lat, it.lng) }, currentTime))
        .let { sundownLineText(it, sundownClock) }
    return sundown to backByLineText(quickSettings?.backBy, sundownClock)
}

/**
 * Portrait (dispatch 2026-10-12-08, RECORD -948, -950): the navigation info inside the compass strip's card, under a thin line,
 * shown while navigating. The card extends down for it: the info grows down from under the line and fades in, laid out rather than
 * drawn so the card's own fill grows with it, on the navigation view's timing, exactly as long as the map's tilt (Motion Part 2,
 * RECORD -651, -672); under reduced motion it only fades. Takes no touch outside its own controls (the X and Try again).
 */
@Composable
internal fun CompactMapPortraitNavigationInfo(
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    cluster: MapIconClusterState,
    trueHeading: State<TrueHeadingReading>,
    navigationFacing: NavigationFacing,
    currentTime: CurrentTimeProvider,
    showDecimalDegrees: Boolean,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    sundownClock: SundownClock,
) {
    val isNavigating = navigation.isNavigating
    val reduceMotion = LocalReduceMotion.current
    val grow = MotionTokens.navigationViewChromeSpec<IntSize>()
    val fade = MotionTokens.navigationViewChromeSpec<Float>()
    val reducedFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val isDarkTheme = LocalForagerDarkTheme.current
    val fill = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    val (sundownLine, backByLine) = compactMapEveningLines(uiState, currentTime, recordingSundownLine, quickSettings, sundownClock)
    LaunchedEffect(isNavigating) { if (!isNavigating) cluster.navigationDisplayBottomInRootPx = null }
    NoTouchTargetExpansion(active = !isNavigating) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isNavigating,
        enter = if (reduceMotion) fadeIn(animationSpec = reducedFade) else expandVertically(animationSpec = grow, expandFrom = Alignment.Top) + fadeIn(animationSpec = fade),
        exit = if (reduceMotion) fadeOut(animationSpec = reducedFade) else shrinkVertically(animationSpec = grow, shrinkTowards = Alignment.Top) + fadeOut(animationSpec = fade),
        modifier = Modifier.leavingTakesNoTouches(leaving = !isNavigating),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The part of the strip's card the navigation info takes, tagged as the display was, with the card's fill.
                .testTag(NAVIGATION_HUD_TAG)
                .mapChromeContainerColor(fill)
                .onGloballyPositioned { coordinates -> if (isNavigating) cluster.navigationDisplayBottomInRootPx = coordinates.boundsInRoot().bottom },
        ) {
            // The owner (RECORD -948): "a line to separate the heading and nav info". The vertical dividers' colour and weight,
            // inset by the card's side padding. Draws only.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs)
                    .height(1.dp)
                    .background(mapIconStackBorderColor())
                    .testTag(NAVIGATION_INFO_LINE_TAG),
            )
            NavigationInfo(
                heading = trueHeading,
                liveFix = uiState.liveFix,
                target = navigation.navigationTarget,
                distanceUnit = uiState.distanceUnit,
                currentTime = currentTime,
                showDecimalDegrees = showDecimalDegrees,
                onExit = navigation.onToggleReturning,
                // Dispatch -502: no route to a chosen waypoint, so the straight-line display (decision D2).
                route = if (navigation.isNavigatingToWaypoint) null else navigation.returnRoute,
                onRetryRoute = navigation.onRetryRoute,
                facing = navigationFacing,
                approximateFix = uiState.approximateFix,
                lastKnownFix = uiState.lastKnownFix,
                sundownLine = sundownLine,
                backByLine = backByLine,
                // RECORD -845: "Arrived", held, after a return ended on arrival.
                arrivalHeld = navigation.isArrivalHeld,
            )
        }
    }
    }
}

/** Dispatch 2026-10-12-08: the thin line in the portrait strip's card between its line and the navigation info (RECORD -948). */
internal const val NAVIGATION_INFO_LINE_TAG = "navigation-info-line"
