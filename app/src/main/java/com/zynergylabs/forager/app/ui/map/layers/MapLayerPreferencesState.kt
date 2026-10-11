package com.zynergylabs.forager.app.ui.map.layers

import com.zynergylabs.forager.app.domain.MapLayerPreferences

/** [state] restored from stored choices, and one line for every stored choice that was refused. */
data class LayerStateRestore(val state: MapLayersState, val rejected: List<String>)

/**
 * The map's layer state from its stored choices (map layers L0b, B3), checked against [registry]: a
 * stored visibility is kept only for a layer the sheet can switch ([MapLayerSpec.userToggleable]), an
 * opacity only for a layer with a slider ([MapLayerSpec.userOpacity]) and only from 0 to 1, and the order
 * only for reorderable layers. Anything else is **refused and reported**, never clamped or applied: a
 * stale or corrupt store must not be able to hide the sighting dots or give a line a slider, and the
 * caller logs every line of [LayerStateRestore.rejected] (CLAUDE.md, Errors). A refused choice leaves
 * that layer at its default.
 *
 * With nothing stored this is [MapLayersState.DEFAULT]: every overlay on, colour fields at 100% in
 * registry order (owner: "On by default").
 */
fun restoreMapLayersState(preferences: MapLayerPreferences, registry: List<MapLayerSpec>): LayerStateRestore {
    val byId = registry.associateBy { it.id }
    val rejected = mutableListOf<String>()
    val layers = mutableMapOf<String, LayerState>()

    preferences.visibility.forEach { (id, visible) ->
        val spec = byId[id]
        when {
            spec == null -> rejected += "stored visibility for $id: no such layer"
            !spec.userToggleable -> rejected += "stored visibility for $id: the Layers sheet does not switch this layer"
            else -> layers[id] = (layers[id] ?: LayerState.DEFAULT).copy(visible = visible)
        }
    }
    preferences.opacity.forEach { (id, opacity) ->
        val spec = byId[id]
        when {
            spec == null -> rejected += "stored opacity for $id: no such layer"
            !spec.userOpacity -> rejected += "stored opacity for $id: this layer has no opacity slider"
            !(opacity in 0f..1f) -> rejected += "stored opacity for $id: $opacity is outside 0 to 1"
            else -> layers[id] = (layers[id] ?: LayerState.DEFAULT).copy(opacity = opacity)
        }
    }
    val order = preferences.order.filter { id ->
        val reorderable = byId[id]?.userReorderable == true
        if (!reorderable) rejected += "stored order names $id, which is not a reorderable layer"
        reorderable
    }
    return LayerStateRestore(MapLayersState(layers = layers, reorderableOrder = order), rejected)
}

/** A step in the colour fields' order: [UP] draws the layer one place higher, [DOWN] one lower. */
enum class ColourFieldMove { UP, DOWN }

/**
 * [registry]'s colour fields in the order the Layers sheet and the legend list them: **top of the draw
 * order first**, the way backcountry and topographic map apps list stacked layers, so the row at the top is the layer drawn
 * on top.
 */
fun colourFieldsTopFirst(registry: List<MapLayerSpec>, state: MapLayersState): List<MapLayerSpec> =
    orderedLayers(registry, state).filter { it.zGroup == ZGroup.COLOUR_FIELDS }.reversed()

/**
 * [state] with [layerId] moved one place in the colour fields' order: the sheet's "Move up" and "Move
 * down" actions and each step of its drag handle (map layers L0b, B1). [MapLayersState.reorderableOrder]
 * comes back naming every colour field, bottom to top, so the stored order is complete. A move past
 * either end, or of a layer that is not a colour field, returns [state] unchanged.
 */
fun moveColourField(state: MapLayersState, registry: List<MapLayerSpec>, layerId: String, move: ColourFieldMove): MapLayersState {
    val bottomToTop = orderedLayers(registry, state).filter { it.zGroup == ZGroup.COLOUR_FIELDS && it.userReorderable }.map { it.id }
    val index = bottomToTop.indexOf(layerId)
    if (index < 0) return state
    val target = if (move == ColourFieldMove.UP) index + 1 else index - 1
    if (target !in bottomToTop.indices) return state
    val reordered = bottomToTop.toMutableList().apply { add(target, removeAt(index)) }
    return state.copy(reorderableOrder = reordered)
}

/**
 * The state the map is given: [state] with every colour field the store has no data for hidden (map
 * layers L0b, B5). The stored choice is not rewritten, so a layer the user left on comes back on when its
 * data does. This is what keeps a release build's registry, which lists the synthetic layers, from ever
 * drawing them, offering them in the sheet, showing the legend or adding their credit: its store never
 * has data. Layers other than colour fields are untouched.
 */
fun withUnavailableColourFieldsHidden(state: MapLayersState, registry: List<MapLayerSpec>, available: Set<String>): MapLayersState {
    val unavailable = registry.filter { it.zGroup == ZGroup.COLOUR_FIELDS && it.id !in available }
    if (unavailable.isEmpty()) return state
    return state.copy(layers = state.layers + unavailable.associate { it.id to state.stateOf(it.id).copy(visible = false) })
}
