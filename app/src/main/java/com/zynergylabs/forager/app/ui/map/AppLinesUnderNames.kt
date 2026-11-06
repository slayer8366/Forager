package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.ZGroup

// STUB for the red run (dispatch 2026-11-06-01 Amendment 1, category I): every answer is today's placement.

/** A basemap layer as the placement reads it: its id, and whether it writes or marks something (a symbol layer). */
internal data class BasemapLayer(val id: String, val namesOrIcons: Boolean)

internal fun namesAnchorOf(basemap: List<BasemapLayer>): String? = null

internal val GROUPS_UNDER_NAMES: Set<ZGroup> = emptySet()

internal sealed interface OverlayPlacement {
    data object OnTop : OverlayPlacement
    data class Below(val layerId: String) : OverlayPlacement
}

internal fun overlayPlacement(spec: MapLayerSpec, namesAnchor: String?): OverlayPlacement = OverlayPlacement.OnTop

internal fun namesAnchorLogLine(namesAnchor: String?, basemap: List<BasemapLayer>): String = "stub"
