package com.zynergylabs.forager.app.ui.map

import android.util.Log
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Where MapLibre's attribution button and logo sit: the pixel sizes worked out from the insets the host hands in, and
 * the effect that applies them. Moved whole from [SightingsMap] by UI split Phase 2.2 (dispatch 2026-10-19-29), called
 * from the place it stood there and keyed exactly as it was. The inset parameters are [SightingsMap]'s own; MapLibre's
 * default margins, the map's size and the credit strip's width are read through [state]. The comments inside are the
 * block's own.
 */
@Composable
internal fun MapAttributionInsets(
    state: SightingsMapState,
    attributionBottomInset: Dp?,
    bottomInset: Dp,
    attributionEndInset: Dp,
    attributionKeepClear: Rect?,
    attributionHideUnder: List<Rect> = emptyList(),
) {
    // Part 1 layout fixes, item 5 (Part 1's device check, flag 3; planner message 2026-09-28-98): MapLibre's
    // attribution button ("i") keeps from the bottom what the always-visible caption keeps
    // (bottomInset: the nav's measured height, which includes the system navigation bar, and in
    // fullscreen the true edge, the caption's own treatment), and from its end edge the inset the host
    // hands in (the overlaid rail in a short landscape window at the rotation that puts it on that
    // edge). Without them it sat under the system navigation bar and the nav in portrait, and under
    // the rail and the system bar at 90. What MapLibre draws is device-only: this map cannot run
    // under Robolectric.
    val layoutDirection = LocalLayoutDirection.current
    val attributionBottomPx = with(LocalDensity.current) { (attributionBottomInset ?: bottomInset).roundToPx() }
    // Item 1 (dispatch 2026-09-29-57, amendment -262, "Move the 'i'"): moved inboard of the landscape L, which the host
    // hands over as its measured bounds, when the two would intersect. The map's own size is tracked for it in mapSizePx, above.
    val attributionEndPx = with(LocalDensity.current) {
        val defaults = state.attributionDefaultMargins
        val endInsetPx = attributionEndInset.roundToPx()
        if (defaults == null) {
            endInsetPx
        } else {
            attributionEndInsetClearOf(
                keepClear = attributionKeepClear,
                mapWidthPx = state.mapSizePx.width,
                mapHeightPx = state.mapSizePx.height,
                defaults = defaults,
                bottomInsetPx = attributionBottomPx,
                endInsetPx = endInsetPx,
                isRtl = layoutDirection == LayoutDirection.Rtl,
                buttonPx = ATTRIBUTION_BUTTON_DP.dp.roundToPx(),
                gapPx = ATTRIBUTION_CLEAR_GAP_DP.dp.roundToPx(),
            )
        }
    }
    // D24 (dispatch 2026-10-30-08, RECORD -1966; the owner: "Bottom right, as in portrait (Recommended)"): the button sits in the
    // bottom-end corner in every window. This replaces RECORD -954, which put it at the bottom start beside the credit strip in a
    // short landscape window.
    val logoBesideAttributionOffsetPx = with(LocalDensity.current) { (ATTRIBUTION_BUTTON_DP + LOGO_ATTRIBUTION_GAP_DP).dp.roundToPx() }
    // D28 with D30 (dispatch 2026-10-30-08, RECORD -1979, -1980): the button, and the logo beside it, hidden while a card covers the
    // button where it is placed, shown again when that clears (attributionHiddenUnder).
    val buttonPx = with(LocalDensity.current) { ATTRIBUTION_BUTTON_DP.dp.roundToPx() }
    val hidden = state.attributionDefaultMargins?.let { defaults ->
        attributionHiddenUnder(
            covers = attributionHideUnder,
            mapWidthPx = state.mapSizePx.width,
            mapHeightPx = state.mapSizePx.height,
            defaults = defaults,
            bottomInsetPx = attributionBottomPx,
            endInsetPx = attributionEndPx,
            isRtl = layoutDirection == LayoutDirection.Rtl,
            buttonPx = buttonPx,
        )
    } ?: false
    LaunchedEffect(state.mapLibreMap, hidden) {
        val map = state.mapLibreMap ?: return@LaunchedEffect
        if (map.uiSettings.isAttributionEnabled == !hidden && map.uiSettings.isLogoEnabled == !hidden) return@LaunchedEffect
        Log.i(ATTRIBUTION_LOG_TAG, if (hidden) "a card covers the attribution's corner: the \"i\" and the logo hidden" else "the attribution's corner is clear: the \"i\" and the logo shown")
        map.uiSettings.isAttributionEnabled = !hidden
        map.uiSettings.isLogoEnabled = !hidden
    }
    LaunchedEffect(state.mapLibreMap, state.attributionDefaultMargins, attributionBottomPx, attributionEndPx, layoutDirection) {
        val map = state.mapLibreMap ?: return@LaunchedEffect
        val defaults = state.attributionDefaultMargins ?: return@LaunchedEffect
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val attributionGravity = Gravity.BOTTOM or Gravity.END
        val attributionMargins = attributionMarginsPx(defaults, attributionBottomPx, attributionEndPx, isRtl = isRtl)
        map.uiSettings.setAttributionGravity(attributionGravity)
        val (left, top, right, bottom) = attributionMargins
        map.uiSettings.setAttributionMargins(left, top, right, bottom)
        // Dispatch 2026-10-18-07, RECORD -1309 and -1315 (the owner: "And yes have the maps Libre logo on the right"; then option
        // 1, a row beside the "i" at the bottom end, accepting that it meets the end of the long Topo caption in portrait). The
        // logo follows the "i" wherever the "i" goes, just past it on the side away from the edge (logoBesideAttribution).
        // It was MapLibre's default until then, bottom-start at 4 dp, under the start of the caption. Kept visible.
        val logo = logoBesideAttribution(attributionGravity, attributionMargins, logoBesideAttributionOffsetPx, isRtl)
        map.uiSettings.setLogoGravity(logo.gravity)
        val (logoLeft, logoTop, logoRight, logoBottom) = logo.margins
        map.uiSettings.setLogoMargins(logoLeft, logoTop, logoRight, logoBottom)
    }
}

/** D28: the log tag for the attribution's hide and show. */
private const val ATTRIBUTION_LOG_TAG = "ForagerAttribution"
