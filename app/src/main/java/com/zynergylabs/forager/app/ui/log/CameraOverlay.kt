package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one place the camera overlay's contrast treatment is defined (top-strip dispatch v2 and the
 * owner's decision of 2026-09-18). The rule, in the owner's words:
 *
 * > Every overlay element gets a black outline. The outline provides legibility. Fill colour
 * > carries meaning, and white is the default fill.
 *
 * Foraging means bright outdoor scenes, and white on a transparent background disappears over a
 * pale cap or snow; the outline keeps every element readable over light and dark alike, which is
 * what lets the strip and the rest of the overlay have no background of their own. Fill colour is
 * not decoration either: white for ordinary chrome (the count, the shutter, strip controls, the
 * opening and unavailable states), red for an error, so a failure still reads as a failure rather
 * than flattening into chrome, and red for recording controls when they arrive (owner's call).
 *
 * A control added in PR #103 uses [OverlayText] and [overlayOutline] and inherits the rule rather
 * than reinventing it. Nothing over the viewfinder sets its own colours.
 */
internal object CameraOverlay {
    /** The default fill: ordinary chrome. */
    val Fill: Color = Color.White

    /** The fill that carries "something went wrong". Kept off the theme so it is the same red over any scene. */
    val ErrorFill: Color = Color(0xFFFF5252)

    /** Every element's outline. */
    val Outline: Color = Color.Black

    /** Outline thickness for glyphs and text. */
    val OutlineWidth: Dp = 1.5.dp
}

/**
 * Text drawn twice: the outline underneath as a stroke, the fill on top. The stroke layer carries
 * no semantics, so the accessibility tree and the tests see one text node, not two.
 */
@Composable
internal fun OverlayText(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = CameraOverlay.Fill,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Box(modifier) {
        Text(
            text,
            color = CameraOverlay.Outline,
            style = style.copy(drawStyle = Stroke(width = with(LocalDensity.current) { (CameraOverlay.OutlineWidth * 2).toPx() })),
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(text, color = fill, style = style)
    }
}

/**
 * A black outline drawn just outside a shape's own edge, for the shutter and any other drawn glyph.
 * **Drawn, not laid out**: a stroke centred on the shape's edge, twice [CameraOverlay.OutlineWidth]
 * wide, painted behind the element so its own fill covers the inner half and the outer half shows
 * outside its bounds. Nothing about the element's size or position changes, which is the point:
 * the first version padded the outline inside the shutter's footprint and moved its disc 1.5 dp,
 * caught by the landscape tests' port-edge assertions.
 */
internal fun Modifier.overlayOutline(shape: Shape): Modifier = drawBehind {
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this),
        color = CameraOverlay.Outline,
        style = Stroke(width = (CameraOverlay.OutlineWidth * 2).toPx()),
    )
}
