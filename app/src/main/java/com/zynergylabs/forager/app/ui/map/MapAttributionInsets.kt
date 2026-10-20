package com.zynergylabs.forager.app.ui.map

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
    attributionBesideCredit: Boolean,
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
    // RECORD -954 (the owner: "Have the map i next to the open street maps logo"; "Just landscape"): in a short landscape window
    // the button moves to the bottom start, just past the credit strip's measured end, its bottom on the strip's bottom. It is
    // MapLibre's own attribution control, so it moves through the same two UiSettings calls as before (gravity and margins), not
    // as a composable; it opens what it always opened. Portrait and every other map keep the bottom-end corner.
    // The credit strip's laid-out width is creditWidthPx, above.
    val besideCreditGapPx = with(LocalDensity.current) { ATTRIBUTION_BESIDE_CREDIT_GAP_DP.dp.roundToPx() }
    val creditBottomPx = with(LocalDensity.current) { bottomInset.roundToPx() }
    val logoBesideAttributionOffsetPx = with(LocalDensity.current) { (ATTRIBUTION_BUTTON_DP + LOGO_ATTRIBUTION_GAP_DP).dp.roundToPx() }
    LaunchedEffect(state.mapLibreMap, state.attributionDefaultMargins, attributionBottomPx, attributionEndPx, layoutDirection, attributionBesideCredit, state.creditWidthPx, creditBottomPx) {
        val map = state.mapLibreMap ?: return@LaunchedEffect
        val defaults = state.attributionDefaultMargins ?: return@LaunchedEffect
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val attributionGravity: Int
        val attributionMargins: IntArray
        if (attributionBesideCredit && state.creditWidthPx > 0) {
            attributionGravity = Gravity.BOTTOM or Gravity.START
            attributionMargins = attributionBesideCreditMarginsPx(defaults, state.creditWidthPx, besideCreditGapPx, creditBottomPx, isRtl)
        } else {
            attributionGravity = Gravity.BOTTOM or Gravity.END
            attributionMargins = attributionMarginsPx(defaults, attributionBottomPx, attributionEndPx, isRtl = isRtl)
        }
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
