package com.zynergylabs.forager.app.ui.map

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

/**
 * The map's credit line, bottom left over the map. Dispatch 2026-10-12-06, RECORD -937 (the owner: "The open street maps tint
 * strip can also have a rounded corner on the right side only, since the left runs to the edge"; then, correcting it, "curve
 * only the upper right side since it's the only side exposed"): only its top-right corner was rounded. Dispatch 2026-10-12-08,
 * RECORD -952 (the owner: "In either mode: can the openstreetmaps tint strip have all corners rounded instead of just the one
 * corner, like a pill?"): all four corners round, half its height, a pill, in both orientations; this replaces -938's top-right
 * corner only. Its tint is unchanged. A background and text, no pointer input, so a touch anywhere on it, its corner gaps
 * included, reaches the map, as it always did (`MapCreditCaptionTouchTest`).
 */
@Composable
internal fun MapCreditCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ComposeColor.White,
        modifier = modifier
            // The tag first, so its bounds are the tinted strip's, padding included.
            .testTag(MAP_CREDIT_CAPTION_TAG)
            .background(
                ComposeColor.Black.copy(alpha = 0.55f),
                androidx.compose.foundation.shape.RoundedCornerShape(percent = MAP_CREDIT_PILL_PERCENT),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** RECORD -952: the credit line's corners, all four at half its height: a pill. Was RECORD -938's top-right corner, 8 dp. */
internal const val MAP_CREDIT_PILL_PERCENT = 50

internal const val MAP_CREDIT_CAPTION_TAG = "map-credit-caption"

/**
 * MapLibre's attribution margins, as `[left, top, right, bottom]` px (Part 1 layout fixes, item 5):
 * [defaults], MapLibre's own, with [bottomInsetPx] added at the bottom and [endInsetPx] at the end edge,
 * which is the right in a left-to-right layout and the left in a right-to-left one (the button's
 * gravity is `BOTTOM or END`).
 */
/**
 * RECORD -954: MapLibre's attribution margins for its button at the bottom START, beside the map credit strip: the start margin is
 * the strip's width plus [gapPx] (MapLibre's own start default replaced, since the strip itself starts at the map's edge), and the
 * bottom margin is the strip's own bottom inset, so the two share a bottom edge. Top and end keep MapLibre's defaults. The pure
 * half, so the arithmetic is a unit test (`AttributionBesideCreditTest`); what MapLibre draws there is device-only.
 */
internal fun attributionBesideCreditMarginsPx(defaults: IntArray, creditWidthPx: Int, gapPx: Int, creditBottomPx: Int, isRtl: Boolean): IntArray {
    val (left, top, right, _) = defaults
    val start = creditWidthPx + gapPx
    return if (isRtl) intArrayOf(left, top, start, creditBottomPx) else intArrayOf(start, top, right, creditBottomPx)
}

/**
 * Where MapLibre's logo goes, as a gravity and `[left, top, right, bottom]` px margins for `UiSettings.setLogoGravity` and
 * `setLogoMargins` (dispatch 2026-10-18-07, RECORD -1309 and -1315, the owner's option 1): in a row with the attribution button
 * ("i"), just past it on the side away from the map's edge. Same gravity as the button, same margins except on that side, where
 * [offsetPx] (the button's side plus the gap, [ATTRIBUTION_BUTTON_DP] + [LOGO_ATTRIBUTION_GAP_DP]) is added. Which index that is
 * follows the convention [attributionMarginsPx] and [attributionBesideCreditMarginsPx] already use for the button: at the bottom
 * end, the right (index 2) in a left-to-right layout and the left (0) in a right-to-left one; at the bottom start (RECORD -954,
 * the short landscape window), the left (0), or the right (2) in right-to-left. Bottoms are shared, so the logo (88 x 23 dp in
 * MapLibre 13.5.0) and the button (21 dp) sit on one line. The pure half, so it is a unit test (`LogoBesideAttributionTest`);
 * what MapLibre draws there is device-only, since a real MapView cannot be built under Robolectric.
 */
internal fun logoBesideAttribution(attributionGravity: Int, attributionMargins: IntArray, offsetPx: Int, isRtl: Boolean): LogoPlacement {
    val atEnd = (attributionGravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.END
    val side = if (atEnd) (if (isRtl) 0 else 2) else (if (isRtl) 2 else 0)
    val margins = attributionMargins.copyOf()
    margins[side] += offsetPx
    return LogoPlacement(gravity = attributionGravity, margins = margins)
}

/** MapLibre's logo placement: see [logoBesideAttribution]. */
internal class LogoPlacement(val gravity: Int, val margins: IntArray)

/** The gap between the attribution button's assumed square ([ATTRIBUTION_BUTTON_DP]) and the logo beside it, in dp. */
internal const val LOGO_ATTRIBUTION_GAP_DP = 4f

/** RECORD -954: the gap between the map credit strip's end and the attribution button beside it. */
internal const val ATTRIBUTION_BESIDE_CREDIT_GAP_DP = 4f

internal fun attributionMarginsPx(defaults: IntArray, bottomInsetPx: Int, endInsetPx: Int, isRtl: Boolean): IntArray {
    val (left, top, right, bottom) = defaults
    return if (isRtl) {
        intArrayOf(left + endInsetPx, top, right, bottom + bottomInsetPx)
    } else {
        intArrayOf(left, top, right + endInsetPx, bottom + bottomInsetPx)
    }
}
