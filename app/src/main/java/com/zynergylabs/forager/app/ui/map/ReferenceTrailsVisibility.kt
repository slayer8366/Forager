package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.map.layers.LayerPaint
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import org.maplibre.android.maps.Style

/** STUB (fail-first): the style's layers by id. */
internal interface StyleLayerSwitches {
    fun has(layerId: String): Boolean
    fun setVisible(layerId: String, visible: Boolean)
}

/** STUB (fail-first). */
internal class MapLibreStyleLayerSwitches(private val style: Style) : StyleLayerSwitches {
    override fun has(layerId: String): Boolean = false
    override fun setVisible(layerId: String, visible: Boolean) = Unit
}

/** STUB (fail-first). */
internal fun applyReferenceTrailsOnStyleLoad(
    layers: StyleLayerSwitches,
    drawnOnThisMap: Boolean,
    chosen: MapLayersState,
    registry: List<MapLayerSpec> = com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY,
): Boolean = false

/** STUB (fail-first). */
internal fun applyStyleLayerPaint(layers: StyleLayerSwitches, paint: LayerPaint) = Unit

/** STUB (fail-first). */
internal fun withReferenceTrailsHiddenUnlessLoaded(
    state: MapLayersState,
    loaded: Boolean,
    registry: List<MapLayerSpec> = com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY,
): MapLayersState = state

/** STUB (fail-first). */
internal class ReferenceTrailsShown {
    var shown by mutableStateOf(false)
        private set
    val onShown: (Boolean) -> Unit = { }
}
