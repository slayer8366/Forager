package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.map.layers.LayerPaint
import com.zynergylabs.forager.app.ui.map.layers.LayerRenderer
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory

/*
 * Step 8b (dispatch 2026-10-26-15 and its Amendments 1 and 2; RECORD -1633, -1680, -1681, -1682): the official-trails
 * switch on the loaded style. The band's layers ([LayerRenderer.STYLE_LAYER] specs in the registry, ids in
 * `MapLayerIds`) are Trail Map's own, in street-v3; the app never builds them and sets only their visibility.
 *
 * Live since the app loads street-v3 (dispatch 2026-10-26-21; the switch was its own PR, RECORD -1678). Styles without
 * the band (v2 and v1, which areas downloaded before the switch hold; Topographical; OpenStreetMap) report none: the
 * drawn state hides the layers (no caption credit, no tap target), the sheet offers no row, and every visibility write
 * below finds no layer and does nothing.
 */

/** Tag for the one line each style load logs about the band. */
private const val REFERENCE_TRAILS_LOG_TAG = "ReferenceTrails"

/**
 * The loaded style's layers by id, as much of a MapLibre `Style` as the switch uses: whether a layer is there, and its
 * `visibility`. A seam so the switch runs headless over a style fixture (`ReferenceTrailsVisibilityTest`).
 */
internal interface StyleLayerSwitches {
    fun has(layerId: String): Boolean
    fun setVisible(layerId: String, visible: Boolean)
}

/** [StyleLayerSwitches] over a loaded MapLibre [style]. */
internal class MapLibreStyleLayerSwitches(private val style: Style) : StyleLayerSwitches {
    override fun has(layerId: String): Boolean = style.getLayer(layerId) != null

    override fun setVisible(layerId: String, visible: Boolean) {
        style.getLayer(layerId)?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }
}

/** Every style-layer spec in [registry], in its order: the band's three layers. */
private fun styleLayerSpecs(registry: List<MapLayerSpec>): List<MapLayerSpec> = registry.filter { it.renderer == LayerRenderer.STYLE_LAYER }

/**
 * The one call in `MapStyleReload`'s style-load callback, after the night recolour and before the app's overlays are
 * added: whether this style draws the band, and each band layer's visibility set from the user's choice [chosen].
 *
 * The band is drawn when the style has every one of its layers and [drawnOnThisMap] (false under Topographical, whose
 * Street underlay at night is Trail Map's style but whose band would never show: "Trail Map only", RECORD -1633). There
 * its layers are hidden and the band is not reported. A style with only some of the layers is a style this app does not
 * know: logged, its layers hidden, not reported. A style with none (today's) writes nothing.
 *
 * The recolour before this walks every layer, hidden or not (`OfflineNightRecolourApply.kt`), so a band switched on
 * later in a night session is already in its night colour. Returns whether the band is drawn.
 */
internal fun applyReferenceTrailsOnStyleLoad(
    layers: StyleLayerSwitches,
    drawnOnThisMap: Boolean,
    chosen: MapLayersState,
    registry: List<MapLayerSpec> = MAP_LAYER_REGISTRY,
): Boolean {
    val specs = styleLayerSpecs(registry)
    val present = specs.filter { layers.has(it.id) }
    if (present.isEmpty()) return false
    val complete = present.size == specs.size
    if (!complete) {
        Log.w(REFERENCE_TRAILS_LOG_TAG, "The style has ${present.map { it.id }} but not ${(specs - present.toSet()).map { it.id }}; the band is hidden and not offered.")
    }
    val drawn = complete && drawnOnThisMap
    present.forEach { spec -> layers.setVisible(spec.id, drawn && layerPaintFor(spec, chosen, registry).visible) }
    Log.i(REFERENCE_TRAILS_LOG_TAG, "Style loaded with the band's layers: drawn=$drawn (complete=$complete, drawnOnThisMap=$drawnOnThisMap).")
    return drawn
}

/**
 * [paint] on a style layer, as `MapFan`'s walk of the registry applies every layer's paint when the switch changes: its
 * visibility only (a style-layer spec has no base opacity, so [paint] carries none). A layer the loaded style does not
 * have is the normal case on any style but street-v3, so it is passed over without a warning.
 */
internal fun applyStyleLayerPaint(layers: StyleLayerSwitches, paint: LayerPaint) {
    if (layers.has(paint.layerId)) layers.setVisible(paint.layerId, paint.visible)
}

/**
 * The state `SightingsMap` draws with: [state] with every style-layer spec hidden while the loaded style does not draw
 * the band ([loaded], what [applyReferenceTrailsOnStyleLoad] returned). So on any other style the caption carries no
 * band credit and no tap is aimed at the band, whatever the stored choice; the stored choice itself is not changed, so
 * the band comes back as the user left it once a style draws it. Every other layer is untouched.
 */
internal fun withReferenceTrailsHiddenUnlessLoaded(
    state: MapLayersState,
    loaded: Boolean,
    registry: List<MapLayerSpec> = MAP_LAYER_REGISTRY,
): MapLayersState {
    if (loaded) return state
    val owners = styleLayerSpecs(registry).filter { it.stateOwnerId == null }
    if (owners.isEmpty()) return state
    return state.copy(layers = state.layers + owners.associate { it.id to state.stateOf(it.id).copy(visible = false) })
}

/**
 * What a map's host holds for its Layers sheet: whether the map last reported that its loaded style draws the band.
 * [onShown] is one lambda for the holder's life, so a [MapRenderMode] carrying it still compares equal between
 * compositions. Remembered by each host (the Maps tab's screen, the Cartography entry map).
 */
@Stable
internal class ReferenceTrailsShown {
    var shown by mutableStateOf(false)
        private set
    val onShown: (Boolean) -> Unit = { shown = it }
}
