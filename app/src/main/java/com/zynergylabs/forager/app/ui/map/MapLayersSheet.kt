package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldSpec
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_NO_FORECAST_HERE
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_RAMP_HIGH_LABEL
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_RAMP_LOW_LABEL
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_REFERENCE_CLASS
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_ZOOM_IN_FOR_FORECAST
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.MapLegend
import com.zynergylabs.forager.app.ui.map.layers.colourFieldsTopFirst
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The legend chip, for tests. */
internal const val MAP_LEGEND_CHIP_TAG = "map-legend-chip"

/**
 * The Layers button's description on both hosts, the Maps tab's icon bar and the entry map's. Since dispatch 2026-10-28-02
 * the button opens the map type panel only, so it says map type only (the owner, RECORD -1766: "…Choose the map type.").
 * The text before L0b ended in "Night mode on/off", which was already untrue: the picker never had a night control.
 */
internal fun layersButtonDescription(mapMode: MapMode): String =
    "Layers: ${mapMode.label} map. Choose the map type."

/**
 * What the Maps tab's hosts and the Tools drawer's Map Layers need for the switches, the legend chip and the saved records
 * (map layers L0b, B1 to B4; the switches moved from the Layers sheet to Tools by dispatch 2026-10-28-02), in one value so each file between `AvailabilityScreen` and a host
 * carries one parameter rather than eight.
 *
 * - [stored] is the user's choices as the ViewModel holds them, which is what the switches
 *   show. The map is given a different state: [stored] with every colour field that has no data
 *   hidden (`withUnavailableColourFieldsHidden`, built in `AvailabilityScreen` into `MapRenderMode`).
 * - [availableColourFields] names the colour-field layers the forecast store has data for this week;
 *   Map Layers lists only those, so a release build never lists one (planner's ruling on F2).
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
    /** Step 8b: the Maps tab's map reported its loaded style draws the official-trails band, so Tools' Trails switch works (grey otherwise, RECORD -1766). */
    val referenceTrailsShown: Boolean = false,
) {
    /** The colour fields Map Layers lists: the ones with data, top of the draw order first. */
    val listedColourFields: List<ColourFieldSpec>
        get() {
            val specs = COLOUR_FIELDS.associateBy { it.layerId }
            return colourFieldsTopFirst(MAP_LAYER_REGISTRY, stored)
                .filter { it.id in availableColourFields }
                .mapNotNull { specs[it.id] }
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
