package com.zynergylabs.forager.app.ui.map

import android.util.Log
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.TransitionOptions

/** The pattern fade's log tag, so its lines can be read on their own from logcat. */
internal const val PATTERN_FADE_LOG_TAG = "ForagerPatternFade"

/** The live style's fill layers as [PatternFadeTarget]. Device-only: a `Style` cannot be built under Robolectric. */
private class StylePatternFade(private val style: Style) : PatternFadeTarget {
    override fun patternLayerIds(): List<String> = style.layers.filterIsInstance<FillLayer>().filter { layer ->
        val pattern = layer.fillPattern
        !pattern.isNull && (pattern.isExpression || !pattern.value.isNullOrEmpty())
    }.map { it.id }

    override fun setPatternTransitionOff(layerId: String): Boolean {
        val layer = style.getLayer(layerId) as? FillLayer ?: return false
        // Signature checked with javap against the pinned 13.5.0 artifact: setFillPatternTransition(TransitionOptions).
        layer.fillPatternTransition = TransitionOptions(0, 0)
        return true
    }
}

/** [turnOffPatternFade] on a style that has just loaded; called first in the one `setStyle` callback ([MapStyleReload]). */
internal fun turnOffPatternFade(style: Style) {
    turnOffPatternFade(StylePatternFade(style)) { Log.i(PATTERN_FADE_LOG_TAG, it) }
}
