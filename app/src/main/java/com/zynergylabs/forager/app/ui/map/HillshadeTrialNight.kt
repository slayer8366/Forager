package com.zynergylabs.forager.app.ui.map

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.PropertyFactory

/**
 * NEVER MERGE: the S22 hill shading trial's night (dispatch 2026-11-01-coder-10; Sage's sage-11, ALT). The night
 * recolour walks background, fill, line and symbol colours only (NIGHT_RECOLOURED_PROPERTIES), so a hillshade keeps its
 * day white highlight at night and glows. With Night Maps on, the "hillshade" layer takes Sage's night pair instead.
 * Production code comes with the rollout.
 */
internal fun applyHillshadeTrialNight(style: Style) {
    val layer = style.getLayer("hillshade") as? HillshadeLayer ?: return
    layer.setProperties(
        // rgba(0,0,0,0.7), rgba(220,228,236,0.2), rgba(0,0,0,0.35): alpha 0.7 = 179/255, 0.2 = 51, 0.35 = 89.
        PropertyFactory.hillshadeShadowColor(Color.argb(179, 0, 0, 0)),
        PropertyFactory.hillshadeHighlightColor(Color.argb(51, 220, 228, 236)),
        PropertyFactory.hillshadeAccentColor(Color.argb(89, 0, 0, 0)),
    )
}
