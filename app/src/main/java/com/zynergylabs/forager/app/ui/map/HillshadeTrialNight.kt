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
        // MapLibre 13.5.0 takes shadow and highlight as colour arrays (one per light, for multidirectional hillshade).
        PropertyFactory.hillshadeShadowColor(arrayOf("rgba(0, 0, 0, 0.7)")),
        PropertyFactory.hillshadeHighlightColor(arrayOf("rgba(220, 228, 236, 0.2)")),
        // Accent is one colour: rgba(0,0,0,0.35), alpha 0.35 = 89/255.
        PropertyFactory.hillshadeAccentColor(Color.argb(89, 0, 0, 0)),
    )
}
