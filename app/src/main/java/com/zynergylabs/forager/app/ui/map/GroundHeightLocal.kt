package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.staticCompositionLocalOf
import com.zynergylabs.forager.app.domain.GroundHeightSource
import com.zynergylabs.forager.app.domain.NoGroundHeightSource

/**
 * The ground heights for the waypoint bubble's "Elevation" line (dispatch 2026-11-03-07), provided by `MainActivity` from
 * `AppContainer.groundHeightSource` ([ForagerThemeWithNoSignal]), as [com.zynergylabs.forager.app.ui.format.LocalSeaLevelConverter]
 * is. One local rather than a field on each map's [MapRecordSources], so every map with bubbles reads it (the owner:
 * "Every map with bubbles (Recommended)") through [MapBubbleLayer], the one host they all share. Defaults to
 * [NoGroundHeightSource]: a map composed without one shows no line.
 */
val LocalGroundHeightSource = staticCompositionLocalOf<GroundHeightSource> { NoGroundHeightSource }
