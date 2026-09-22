package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one definition of how anything drawn over the viewfinder is styled. Owner's rule,
 * 2026-09-18:
 *
 * > Every overlay element gets a black outline. The outline provides legibility. Fill colour
 * > carries meaning, and white is the default fill.
 *
 * Foraging means bright outdoor scenes. White on transparent disappears over a pale cap or snow;
 * the outline is what keeps a glyph readable over light and dark alike, and it is what lets the
 * strip and the controls have **no background of their own** — no scrim, no gradient, no panel.
 * Fill is the only thing that means anything: white for ordinary chrome (the count, the shutter,
 * the loading and unavailable states, strip controls), and red for an error so a failure still
 * reads as one.
 *
 * Everything here takes the outline from [OverlayOutline] and the default fill from [OverlayFill],
 * so a control added later inherits the rule by using these composables rather than by knowing it.
 * The nine sites that styled overlay content on their own before this file existed are listed in
 * `docs/audits/2026-09-18-camera-overlay-prebuild-report.md`; none of them sets a colour any more.
 *
 * ## How the outline is drawn, and why each way
 *
 * - **Text**: the same string twice in one `Box` — a stroked pass in the outline colour beneath a
 *   filled pass on top. Compose's `TextStyle.drawStyle = Stroke` changes only how glyphs are
 *   painted, not how they measure, so the two passes are the same size and the box is the size of
 *   the text. The stroked copy clears its semantics, so accessibility and tests see one text node,
 *   and the caller's modifier (tag, rotation) goes on the box so both passes turn together.
 * - **Icons**: an `ImageVector` cannot be stroked, so the outline is the same icon painted in the
 *   outline colour at eight offsets of the outline width around the filled one. Coarser than a
 *   true stroke, adequate at 24 dp, and it needs no second asset.
 * - **The shutter's ring** and **the progress spinner** are circles, and get a black ring outside
 *   their white one by a second border or a second indicator with a wider stroke underneath.
 *
 * The outline is drawn *inside* each element's bounds (text and icons overflow by the outline width,
 * which is within the padding every caller already has; the shutter's ring is drawn inside its 72 dp)
 * so that applying the rule moves nothing — the landscape port-edge tests hold the shutter to 0.51 dp.
 */
internal val OverlayOutline: Color = Color.Black
internal val OverlayFill: Color = Color.White

/** Stroke width for text and the offset for icons. Three dp reads as an outline at body size and does not fill a letter's counter. */
internal val OVERLAY_OUTLINE_WIDTH: Dp = 3.dp

/** Outlined text. [fill] is white unless the text carries meaning — an error passes the error colour. */
@Composable
internal fun OverlayText(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = OverlayFill,
    style: TextStyle = LocalTextStyle.current,
) {
    val strokeWidthPx = with(LocalDensity.current) { OVERLAY_OUTLINE_WIDTH.toPx() }
    Box(modifier) {
        Text(
            text,
            style = style.copy(color = OverlayOutline, drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round)),
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(text, style = style.copy(color = fill))
    }
}

/** An outlined icon: eight outline-coloured copies offset by the outline width, then the filled one. */
@Composable
internal fun OverlayIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fill: Color = OverlayFill,
) {
    val w = OVERLAY_OUTLINE_WIDTH / 2
    Box(modifier) {
        for ((dx, dy) in listOf(-w to -w, 0.dp to -w, w to -w, -w to 0.dp, w to 0.dp, -w to w, 0.dp to w, w to w)) {
            Icon(imageVector, contentDescription = null, tint = OverlayOutline, modifier = Modifier.offset(dx, dy).clearAndSetSemantics { })
        }
        Icon(imageVector, contentDescription = contentDescription, tint = fill)
    }
}

/** The loading spinner, outlined: an outline-coloured ring with a wider stroke beneath the white one. */
@Composable
internal fun OverlayProgress(modifier: Modifier = Modifier) {
    Box(modifier) {
        CircularProgressIndicator(color = OverlayOutline, strokeWidth = 4.dp + OVERLAY_OUTLINE_WIDTH / 2, modifier = Modifier.clearAndSetSemantics { })
        CircularProgressIndicator(color = OverlayFill, strokeWidth = 4.dp)
    }
}

/**
 * A black ring at the outer edge of a circular element, drawn inside its bounds. The shutter's
 * white ring sits just inside it, so the disc reads over a white scene without growing.
 */
internal fun Modifier.overlayRing(width: Dp = OVERLAY_OUTLINE_WIDTH / 2): Modifier =
    border(width, OverlayOutline, CircleShape)
