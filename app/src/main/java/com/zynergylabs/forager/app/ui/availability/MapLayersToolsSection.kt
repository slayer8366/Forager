package com.zynergylabs.forager.app.ui.availability

// Dispatch 2026-10-28-02 (RECORD -1764 to -1766): the map's overlay switches, moved out of the Layers sheet into the Tools
// drawer as "Map Layers", under Trip Planner. LayerSwitchRow, ColourFieldRows and OpacityRow are moved from MapLayersSheet.kt
// (pre-main e2969abe4, lines 345-476), unchanged but for the row's `enabled` (the Trails row, below).

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_OVERLAY_LABEL
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldSpec
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.motion.shapedPressLayer
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/** The fold's title, the owner's words. */
internal const val MAP_LAYERS_SECTION_TITLE = "Map Layers"

/** One overlay switch row, by registry layer id. */
internal fun mapLayerSwitchTag(layerId: String) = "map-layer-switch:$layerId"

/** A colour field's opacity slider, by registry layer id. */
internal fun mapLayerOpacityTag(layerId: String) = "map-layer-opacity:$layerId"

/** A colour field's drag handle, by registry layer id. */
internal fun mapLayerReorderTag(layerId: String) = "map-layer-reorder:$layerId"

/** The official-trails band's switch, one line (the owner: "Just 'Trails'"; RECORD -1764). */
internal const val REFERENCE_TRAILS_LABEL = "Trails"

/** One overlay switch: its registry layer and its label. */
internal data class MapOverlayOption(val layerId: String, val label: String)

/**
 * The record overlays' switches, in order, in the owner's Title Case (Amendment 1, RECORD -1765: "Planned trips should be
 * Planned Trips, Offline maps should be Offline Maps, Journal entries should be Journal Entries."; "Recording Tracks" was
 * "Recording trail", RECORD -1764). "Offline Maps" is the offline region's fill, whose outline follows it (`stateOwnerId`),
 * so one switch hides both. "Journal Entries" is every journal-entry halo, which does not change which entries are shown.
 * Each label is this list's own: none is shared with another screen (verify-first report, section 7).
 */
internal val MAP_LAYER_OVERLAYS: List<MapOverlayOption> = listOf(
    MapOverlayOption(MapLayerIds.FINDS, "Finds"),
    MapOverlayOption(MapLayerIds.PHOTOS, "Photos"),
    MapOverlayOption(MapLayerIds.WAYPOINTS, "Waypoints"),
    MapOverlayOption(MapLayerIds.PLANNED_TRIPS, "Planned Trips"),
    MapOverlayOption(MapLayerIds.BREADCRUMB, "Recording Tracks"),
    MapOverlayOption(MapLayerIds.KEPT_TRACKS, "Tracks"),
    MapOverlayOption(MapLayerIds.OFFLINE_REGION_FILL, "Offline Maps"),
    MapOverlayOption(JOURNAL_ENTRIES_SWITCH_LAYER_ID, JOURNAL_ENTRIES_OVERLAY_LABEL),
)

/**
 * Tools > Map Layers: folded the first time, and once opened it stays open while the app runs ([open], held by
 * `AvailabilityScreen`). Open, it lists the record overlays ([MAP_LAYER_OVERLAYS]), then Trails, then the colour fields
 * that have data (`MapLayersControls.listedColourFields`), each with its opacity and reorder controls. No map type here:
 * that is the Layers button's panel. Every switch shows the user's stored choice ([MapLayersControls.stored]), the one the
 * Maps tab and the entry map both draw with, and a flip goes straight to it, so the map behind the drawer changes at once.
 *
 * **Trails** is always listed. It can do something only while the Maps tab's map is drawing the official-trails band
 * ([MapLayersControls.referenceTrailsShown], the map's own report; on Topo or OSM it is not). Otherwise it is grey, still
 * showing its stored on or off, and a tap does nothing; TalkBack reads it as a disabled switch (the owner, RECORD -1766:
 * "when selecting a map type that doesn't use Trails, just have it greyed out instead of removing it from the list").
 * It follows the Maps tab's map wherever Tools is opened, over the Journal too (option 1 of the same answer).
 */
@Composable
internal fun MapLayersToolsSection(controls: MapLayersControls, open: Boolean, onOpenChange: (Boolean) -> Unit) {
    CollapsibleSection(title = MAP_LAYERS_SECTION_TITLE, expanded = open, onExpandedChange = onOpenChange) {
        Column {
            val state = controls.stored
            MAP_LAYER_OVERLAYS.forEach { option ->
                LayerSwitchRow(
                    layerId = option.layerId,
                    label = option.label,
                    checked = state.stateOf(option.layerId).visible,
                    onCheckedChange = { controls.onVisibilityChanged(option.layerId, it) },
                )
            }
            LayerSwitchRow(
                layerId = MapLayerIds.REFERENCE_FS_BAND,
                label = REFERENCE_TRAILS_LABEL,
                checked = state.stateOf(MapLayerIds.REFERENCE_FS_BAND).visible,
                onCheckedChange = { controls.onVisibilityChanged(MapLayerIds.REFERENCE_FS_BAND, it) },
                enabled = controls.referenceTrailsShown,
            )
            ColourFieldRows(controls.listedColourFields, state, controls.onVisibilityChanged, controls.onOpacityChanged, controls.onColourFieldMoved)
        }
    }
}

/**
 * One switch row. The whole row is the toggle, tagged for tests, so a touch anywhere on it switches
 * it; the [Switch] inside draws the state and takes no input of its own. While not [enabled] it takes no
 * touch and its label and switch are drawn in Material's disabled colours.
 */
@Composable
private fun LayerSwitchRow(layerId: String, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    // Amendment 1 to motion Part 1 (RECORD -657): the row's press is drawn in a rounded shape; the toggle's touch is unchanged.
    val pressSource = remember { MutableInteractionSource() }
    val labelColor = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = DISABLED_CONTENT_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .toggleable(value = checked, interactionSource = pressSource, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .shapedPressLayer(pressSource)
            .testTag(mapLayerSwitchTag(layerId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = labelColor, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Material 3's disabled content opacity (the 38% its own disabled text and icons use). */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** The gap between two colour-field rows; part of the drag handle's step. */
private val COLOUR_FIELD_ROW_GAP = Spacing.sm

/**
 * The colour fields, top of the draw order first. Each row is its switch, its opacity slider and its
 * drag handle. The handle reorders by dragging (the prior-art convention in backcountry and topographic map apps) and by
 * TalkBack's custom actions "Move up" and "Move down". A drag moves the row with the finger and, on
 * release, steps the layer by the whole rows it was dragged ([ColourFieldMove.DOWN] for each row
 * down, since the list is top first).
 */
@Composable
private fun ColourFieldRows(
    fields: List<ColourFieldSpec>,
    state: MapLayersState,
    onVisibilityChanged: (String, Boolean) -> Unit,
    onOpacityChanged: (String, Float) -> Unit,
    onColourFieldMoved: (String, ColourFieldMove) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { COLOUR_FIELD_ROW_GAP.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(COLOUR_FIELD_ROW_GAP)) {
        fields.forEach { field ->
            key(field.layerId) {
                var rowHeightPx by remember { mutableIntStateOf(0) }
                var dragPx by remember { mutableFloatStateOf(0f) }
                val stepPx by rememberUpdatedState(rowHeightPx + gapPx)
                val currentOnMoved by rememberUpdatedState(onColourFieldMoved)
                val layerState = state.stateOf(field.layerId)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { rowHeightPx = it.height }
                        .graphicsLayer { translationY = dragPx },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            LayerSwitchRow(
                                layerId = field.layerId,
                                label = field.label,
                                checked = layerState.visible,
                                onCheckedChange = { onVisibilityChanged(field.layerId, it) },
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(MIN_TOUCH_TARGET)
                                .semantics {
                                    contentDescription = "Reorder ${field.label}"
                                    customActions = listOf(
                                        CustomAccessibilityAction("Move up") { onColourFieldMoved(field.layerId, ColourFieldMove.UP); true },
                                        CustomAccessibilityAction("Move down") { onColourFieldMoved(field.layerId, ColourFieldMove.DOWN); true },
                                    )
                                }
                                .pointerInput(field.layerId) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            val steps = if (stepPx > 0f) (dragPx / stepPx).roundToInt() else 0
                                            dragPx = 0f
                                            val move = if (steps > 0) ColourFieldMove.DOWN else ColourFieldMove.UP
                                            repeat(abs(steps)) { currentOnMoved(field.layerId, move) }
                                        },
                                        onDragCancel = { dragPx = 0f },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragPx += dragAmount
                                    }
                                }
                                .testTag(mapLayerReorderTag(field.layerId)),
                        ) {
                            Icon(Icons.Filled.DragHandle, contentDescription = null)
                        }
                    }
                    OpacityRow(field, layerState.opacity, onOpacityChanged)
                }
            }
        }
    }
}

/**
 * "Opacity", its slider (the L0a multiplier, 0 to 100%) and its value as a percent. TalkBack reads it
 * as "<layer> opacity, <n> percent" (planner's ruling on Q2). 100% draws the field at its base fill
 * opacity of 0.6 (owner's ruling on Q10), and the slider scales that. Each change goes to the map at
 * once and is stored.
 */
@Composable
private fun OpacityRow(field: ColourFieldSpec, opacity: Float, onOpacityChanged: (String, Float) -> Unit) {
    val percent = (opacity * 100).roundToInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Opacity", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = opacity,
            onValueChange = { onOpacityChanged(field.layerId, it) },
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "${field.label} opacity"
                    stateDescription = "$percent percent"
                }
                .testTag(mapLayerOpacityTag(field.layerId)),
        )
        Text("$percent%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 40.dp))
    }
}
