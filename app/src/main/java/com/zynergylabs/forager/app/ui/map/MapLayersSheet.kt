package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.motion.shapedPressLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldSpec
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_NO_FORECAST_HERE
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_RAMP_HIGH_LABEL
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_RAMP_LOW_LABEL
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_REFERENCE_CLASS
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_ZOOM_IN_FOR_FORECAST
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.MapLegend
import com.zynergylabs.forager.app.ui.map.layers.colourFieldsTopFirst
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/** The sheet, for tests. */
internal const val MAP_LAYERS_SHEET_TAG = "map-layers-sheet"

/**
 * The colour the sheet's container is given, on the sheet's own node ([MAP_LAYERS_SHEET_TAG]), for
 * tests: the value passed to `ModalBottomSheet`'s `containerColor`, which Material3 draws as is.
 */
internal val MapLayersSheetContainerColor = SemanticsPropertyKey<Color>("MapLayersSheetContainerColor")
private var SemanticsPropertyReceiver.mapLayersSheetContainerColor by MapLayersSheetContainerColor

/**
 * The content colour the sheet provides to what it holds (`LocalContentColor` as read inside the
 * sheet), on the sheet's content column, for tests.
 */
internal val MapLayersSheetContentColor = SemanticsPropertyKey<Color>("MapLayersSheetContentColor")
private var SemanticsPropertyReceiver.mapLayersSheetContentColor by MapLayersSheetContentColor

/** One overlay switch row, by registry layer id. */
internal fun mapLayerSwitchTag(layerId: String) = "map-layer-switch:$layerId"

/** A colour field's opacity slider, by registry layer id. */
internal fun mapLayerOpacityTag(layerId: String) = "map-layer-opacity:$layerId"

/** A colour field's drag handle, by registry layer id. */
internal fun mapLayerReorderTag(layerId: String) = "map-layer-reorder:$layerId"

/** The legend chip, for tests. */
internal const val MAP_LEGEND_CHIP_TAG = "map-legend-chip"

/**
 * The Layers control's description on all three hosts (map layers L0b, B1): the compact cluster's
 * Layers row, the wide layout's round control and the entry map's row. The text it replaces ended in
 * "Night mode on/off", which was already untrue: the picker never had a night control.
 */
internal fun layersButtonDescription(mapMode: MapMode): String =
    "Layers: ${mapMode.label} map. Choose the map type and overlays."

/** One overlay switch the sheet lists: its registry layer and its label (the dispatch, B1). */
internal data class MapOverlayOption(val layerId: String, val label: String)

/**
 * The Maps tab's overlay switches, in the sheet's order (the dispatch, B1). "Offline maps" is
 * the offline region's fill, whose outline follows it (`stateOwnerId`), so one switch hides both.
 * J8 adds "Journal entries" last, as layer ruling 3 lists it: every journal-entry halo follows that
 * one switch, which does not change which entries are shown.
 */
internal val MAPS_TAB_OVERLAYS: List<MapOverlayOption> = listOf(
    MapOverlayOption(MapLayerIds.FINDS, "Finds"),
    MapOverlayOption(MapLayerIds.PHOTOS, "Photos"),
    MapOverlayOption(MapLayerIds.WAYPOINTS, "Waypoints"),
    MapOverlayOption(MapLayerIds.PLANNED_TRIPS, "Planned trips"),
    MapOverlayOption(MapLayerIds.BREADCRUMB, "Recording trail"),
    MapOverlayOption(MapLayerIds.KEPT_TRACKS, "Tracks"),
    MapOverlayOption(MapLayerIds.OFFLINE_REGION_FILL, "Offline maps"),
    MapOverlayOption(JOURNAL_ENTRIES_SWITCH_LAYER_ID, JOURNAL_ENTRIES_OVERLAY_LABEL),
)

/**
 * The entry map's switches: only the layers it draws (owner's ruling 4, "Same sheet": "listing what
 * that map draws"). It draws an entry's kept finds, photos, waypoints, tracks and offline regions
 * (`CartographyEntryReportScreen`'s `MapOverlayContent`), never planned trips or the live trail.
 */
internal val ENTRY_MAP_OVERLAYS: List<MapOverlayOption> = MAPS_TAB_OVERLAYS.filter {
    // J8: the entry map draws no journal-entry highlight, so it offers no switch for one.
    it.layerId != MapLayerIds.PLANNED_TRIPS && it.layerId != MapLayerIds.BREADCRUMB && it.layerId != JOURNAL_ENTRIES_SWITCH_LAYER_ID
}

/**
 * What the Maps tab's two hosts need for the Layers sheet, the legend chip and the saved records
 * (map layers L0b, B1 to B4), in one value so each file between `AvailabilityScreen` and a host
 * carries one parameter rather than eight.
 *
 * - [stored] is the user's choices as the ViewModel holds them, which is what the sheet's switches
 *   show. The map is given a different state: [stored] with every colour field that has no data
 *   hidden (`withUnavailableColourFieldsHidden`, built in `AvailabilityScreen` into `MapRenderMode`).
 * - [availableColourFields] names the colour-field layers the forecast store has data for this week;
 *   the sheet lists only those, so a release build never lists one (planner's ruling on F2).
 * - [cellsShown] is what the map reported about the cells in view, for the legend's dates.
 * - [records] is every saved record the Maps tab draws, pending deletes already left out.
 * - [legendExpanded] is held above the tab, so it survives leaving the Maps tab and coming back
 *   (CLAUDE.md, UX defaults).
 */
data class MapLayersControls(
    val stored: MapLayersState = MapLayersState.DEFAULT,
    val availableColourFields: Set<String> = emptySet(),
    val cellsShown: Map<String, ForecastCellsShown> = emptyMap(),
    /** Data part C (dispatch -668): the map reported the camera below the colour fields' minimum zoom, for the legend's note. */
    val forecastZoomedOut: Boolean = false,
    val records: MapRecords = MapRecords.NONE,
    val legendExpanded: Boolean = false,
    val onLegendExpandedChange: (Boolean) -> Unit = {},
    val onVisibilityChanged: (layerId: String, visible: Boolean) -> Unit = { _, _ -> },
    val onOpacityChanged: (layerId: String, opacity: Float) -> Unit = { _, _ -> },
    val onColourFieldMoved: (layerId: String, move: ColourFieldMove) -> Unit = { _, _ -> },
    /**
     * J8: the saved entries shown on the map and the records they keep (`GetJournalEntryHighlightsUseCase`),
     * which each host hands its map (`MapOverlayContent.journalHighlights`) and whose
     * [JournalEntryHighlights.shownEntries] its chip lists. The halos follow the "Journal entries"
     * switch through the registry; the chip does not.
     */
    val journalHighlights: JournalEntryHighlights = JournalEntryHighlights.NONE,
    /** J8-3: the chip list's "Hide" for one entry: writes that entry's `shownOnMap` false. */
    val onHideJournalEntry: (entryId: String) -> Unit = {},
    /** J8-3: the chip list's "Hide all": writes `shownOnMap` false for every shown entry. */
    val onHideAllJournalEntries: () -> Unit = {},
) {
    /** The colour fields the sheet lists: the ones with data, top of the draw order first. */
    val listedColourFields: List<ColourFieldSpec>
        get() {
            val specs = COLOUR_FIELDS.associateBy { it.layerId }
            return colourFieldsTopFirst(MAP_LAYER_REGISTRY, stored)
                .filter { it.id in availableColourFields }
                .mapNotNull { specs[it.id] }
        }
}

/**
 * The Layers sheet (map layers L0b, B1; owner's layer ruling 3, "One sheet, two sections"): a
 * Material 3 modal bottom sheet in the pattern of J5c's details sheet (`RecordDetailsSheet`: opened
 * fully expanded, the sheet's own inset handling, and content that scrolls, so in a short landscape
 * window it takes the height it has). It replaces the basemap-only `MapModePicker` popover on all
 * three hosts.
 *
 * - **"Map type"**: Street and Topographical, one chosen (Satellite removed, dispatch
 *   2026-09-28-708; see [MapMode]). A tap applies it and the sheet **stays open** (owner's ruling on
 *   Q3, "Stays open (Recommended)"); it closes on a swipe, Back or a tap outside, all through
 *   [onDismiss]. On the Maps tab the choice persists across restarts ([MapMode]'s doc).
 * - **"Overlays"**: one switch per [overlays] entry, then the colour fields [colourFields] (only the
 *   ones with data, top of the draw order first, the way backcountry and topographic map apps list stacked layers), each
 *   with its switch, an "Opacity" slider and a drag handle. Listed after the record overlays because
 *   they draw below every one of them. A reorder is drawn at the map's next style load (terminal
 *   `2026-09-27-72`, Deviations 4); until then the sheet shows the new order and the map the old one.
 *
 * Every switch shows [state], the user's stored choice, never the effective state the map is given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapLayersSheet(
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    overlays: List<MapOverlayOption>,
    colourFields: List<ColourFieldSpec>,
    state: MapLayersState,
    onVisibilityChanged: (layerId: String, visible: Boolean) -> Unit,
    onOpacityChanged: (layerId: String, opacity: Float) -> Unit,
    onColourFieldMoved: (layerId: String, move: ColourFieldMove) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Material3's own default container role for a sheet (`BottomSheetDefaults.ContainerColor`,
    // `colorScheme.surfaceContainerLow` in material3 1.5.0-alpha26, SheetDefaults.kt:522), at the map
    // chrome's standing opacity (owner: "Can this panel be given 80% opacity like the rest of the map
    // chrome?"). The scrim stays Material3's default, `colorScheme.scrim` (Bark in both themes,
    // Theme.kt) at 0.32 alpha (SheetDefaults.kt:529), so the map reads through both.
    // C1: the navigation bar's colour, not Material3's surfaceContainerLow (owner: "Make sure any other pop up or bubble, or the tool panel, is the same color as the app navigation bar").
    val containerColor = mapChromeFill(navigationBarContainerColor(), overMap = true)
    // The content colour is pinned to the default's own (`contentColorFor` of the unaltered
    // container role, `onSurface`), because `contentColorFor` matches a colour-scheme role exactly:
    // given a container at any other alpha it matches none and falls back to `LocalContentColor`.
    val contentColor = contentColorFor(navigationBarContainerColor())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(MAP_LAYERS_SHEET_TAG).semantics { mapLayersSheetContainerColor = containerColor },
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        MapChromeSheetNavigationBar()
        val providedContentColor = LocalContentColor.current
        Column(
            modifier = Modifier
                .semantics { mapLayersSheetContentColor = providedContentColor }
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Layers", style = MaterialTheme.typography.titleLarge)
            Text("Map type", style = MaterialTheme.typography.titleSmall)
            // Centred between the sheet's sides (owner, 2026-09-29: "have the map street/topo/satellite chips be centered between the
            // panel sides. The height position on the panel is fine as is."; dispatch 2026-09-28-104, item 9). The column above has
            // equal padding at both ends, so centring in it is centring in the sheet; the spacing between chips and the row's height
            // are unchanged.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
            ) {
                MapMode.entries.forEach { mode ->
                    FilterChip(
                        selected = mode == mapMode,
                        onClick = { onMapModeSelected(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text("Overlays", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            overlays.forEach { option ->
                LayerSwitchRow(
                    layerId = option.layerId,
                    label = option.label,
                    checked = state.stateOf(option.layerId).visible,
                    onCheckedChange = { onVisibilityChanged(option.layerId, it) },
                )
            }
            ColourFieldRows(colourFields, state, onVisibilityChanged, onOpacityChanged, onColourFieldMoved)
        }
    }
}

/**
 * One switch row. The whole row is the toggle, tagged for tests, so a touch anywhere on it switches
 * it; the [Switch] inside draws the state and takes no input of its own.
 */
@Composable
private fun LayerSwitchRow(layerId: String, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // Amendment 1 to motion Part 1 (RECORD -657): the row's press is drawn in a rounded shape; the toggle's touch is unchanged.
    val pressSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .toggleable(value = checked, interactionSource = pressSource, indication = null, role = Role.Switch, onValueChange = onCheckedChange)
            .shapedPressLayer(pressSource)
            .testTag(mapLayerSwitchTag(layerId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

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

/**
 * How far above its corner's bottom edge the legend chip sits, so it stacks above MapLibre's own "i"
 * attribution button in that corner (`SightingsMap` moves it to bottom-end; owner's ruling 5, "above
 * the 'i'"). The button's drawable is 21 dp (`maplibre_info_icon_default.xml` in the pinned
 * `android-sdk-13.5.0` aar), inside a margin the SDK sets and this file does not read, so the clearance
 * is a round 32 dp. Where the button actually lands is device-only.
 */
internal val LEGEND_ATTRIBUTION_CLEARANCE: Dp = 32.dp

/** The widest the expanded legend grows; the reference class wraps inside it. */
private val LEGEND_MAX_WIDTH = 280.dp

/**
 * The tallest the legend grows; past it, its contents scroll inside it (N1, the owner's ruling "Cap
 * height, scroll (Recommended)"). Chosen so that in portrait the icon cluster still fits above the
 * expanded chip by Q4's display-only clamp: at `w384dp-h823dp` the cluster is 380 dp tall and can rise
 * no higher than the search chrome (its bottom then at 601.7 dp), the chip's bottom is at 711 dp and the
 * clamp keeps `Spacing.sm` between them, which leaves 101.3 dp; 96 dp is that, rounded down. It is
 * measured under Robolectric's zero insets: on a device the real status and navigation bars take from
 * the same room, so whether the cluster clears the expanded chip there is device-only.
 */
internal val LEGEND_MAX_HEIGHT: Dp = 96.dp

/** Each ramp's drawn width in the expanded legend. */
private val LEGEND_RAMP_WIDTH = 160.dp

/**
 * The legend chip (map layers L0b, B4, as planner message 2 gave it). Collapsed, it names the one
 * visible colour field, or reads "2 layers"; a tap expands it and a second tap collapses it ("Show
 * legend" and "Hide legend" for TalkBack, planner's ruling on Q2). Expanded, it lists every visible
 * field top first: its name, its ramp with 0% and 100% end labels, its week and data dates once any of
 * its cells is in view, and "no forecast here" beside an empty outlined square; then the reference
 * class, once, under the ramps. Percent appears only here and on chance layers (R8), which every
 * colour field is today (`ColourFieldSpec`'s doc comment).
 *
 * **Bounded to its content** (CLAUDE.md, the Surface pitfall): nothing inside fills its parent, and its
 * width and height are capped ([LEGEND_MAX_HEIGHT]: past it the contents scroll), so the map around it
 * keeps its touches. The caller composes it only for a non-null
 * [legend], so it draws nothing while no colour field is visible.
 */
@Composable
internal fun MapLegendChip(
    legend: MapLegend,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val contentColor = if (isDarkTheme) Color.White else Bark
    // Motion Part 1 (dispatch 2026-09-28-652, item 3, scout M4; the owner, RECORD -651: "Yes, round them all"): the tap stays on
    // the Surface's modifier, where it was (outside the Surface's clip, so its touch area is the whole box as before), but no
    // longer draws its own square ripple; the Column inside, which fills the Surface and is clipped to its rounded shape, draws
    // the same press instead.
    val tapInteraction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(Spacing.md),
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = contentColor,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
        modifier = modifier
            .widthIn(max = LEGEND_MAX_WIDTH)
            .heightIn(max = LEGEND_MAX_HEIGHT)
            .testTag(MAP_LEGEND_CHIP_TAG)
            .mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight)
            .clickable(
                interactionSource = tapInteraction,
                indication = null,
                onClickLabel = if (expanded) "Hide legend" else "Show legend",
            ) { onExpandedChange(!expanded) },
    ) {
        Column(
            modifier = Modifier
                .indication(tapInteraction, LocalIndication.current)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(legend.collapsedLabel, style = MaterialTheme.typography.labelLarge)
            // Data part C (dispatch -668): said collapsed as well as expanded, so the user is told why
            // the field is empty without having to open the legend to find out.
            if (legend.zoomedOut) {
                Text(LEGEND_ZOOM_IN_FOR_FORECAST, style = MaterialTheme.typography.labelSmall)
            }
            if (expanded) {
                legend.layers.forEach { layer ->
                    Column(modifier = Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(layer.name, style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .size(width = LEGEND_RAMP_WIDTH, height = 12.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colorStops = layer.ramp.stops.map { it.chance to Color(it.argb) }.toTypedArray(),
                                    ),
                                ),
                        )
                        Row(modifier = Modifier.width(LEGEND_RAMP_WIDTH)) {
                            Text(LEGEND_RAMP_LOW_LABEL, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(LEGEND_RAMP_HIGH_LABEL, style = MaterialTheme.typography.labelSmall)
                        }
                        layer.dates?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            // The empty-cell swatch: an outlined square with no fill, because an
                            // unscored cell draws nothing (R5).
                            Box(modifier = Modifier.size(12.dp).border(1.dp, contentColor))
                            Text(LEGEND_NO_FORECAST_HERE, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(LEGEND_REFERENCE_CLASS, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
}
