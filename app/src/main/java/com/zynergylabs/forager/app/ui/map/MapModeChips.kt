package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.MushroomDeep
import com.zynergylabs.forager.app.ui.theme.Spacing

/** One map type chip ([MapModeChipRow]), by mode, for tests. */
internal fun mapModeChipTag(mode: MapMode) = "map-mode-chip:${mode.name}"

/** The fill a "Map type" chip is given when selected, on its node, for tests (the colour the chip is told to draw). */
internal val MapModeChipSelectedFill = SemanticsPropertyKey<Color>("MapModeChipSelectedFill")
private var SemanticsPropertyReceiver.mapModeChipSelectedFill by MapModeChipSelectedFill

/**
 * Whether [mode], the chosen map type, is standing aside for Trail Map (dispatch 2026-10-22-06 Amendment 3; the
 * owner: "Have the toggle color be grey to reflect it's chosen, but offline.", then "Same meaning everywhere: grey =
 * chosen, but Trail is being shown instead."). Decided by the map's own rule, [drawsFromDownloads], so the chip and
 * the map cannot disagree. It draws Trail Map from the downloaded areas in place of a chosen Street or Topographical
 * map on two paths: no signal ([noSignal], [LocalNoSignal] after its hysteresis, the state the map reads), and "Use
 * offline tiles" ([offlineTiles], `MapRenderMode.useOfflineTiles`). For a Trail user it draws Trail Map, which is what
 * they chose. No signal with no downloaded area ([hasDownloadedArea] false) draws the chosen map, so the chip stays
 * normal (dispatch 2026-10-23-07, Elder's item 3: grey only when Trail Map is actually drawn instead). The one path
 * that shows something else to a Trail user is the -1513 fallback ([trailMapDraws] false):
 * once Trail Map's style has failed to load, the map draws OpenStreetMap's raster until the signal returns or the next cold start, and the
 * Trail chip is grey (the owner: "Same meaning everywhere: grey = chosen, but something else is shown."). The stored
 * choice never changes; only the chip's colour does.
 */
internal fun chosenMapModeStandsAside(
    mode: MapMode,
    noSignal: Boolean,
    hasDownloadedArea: Boolean,
    offlineTiles: Boolean = false,
    trailMapDraws: Boolean = true,
    /** The raster that stands in for a failed Trail Map ([trailMapStandIn]); a parameter so a test can give it none. */
    standIn: Basemap? = trailMapStandIn,
): Boolean {
    val fromDownloads = drawsFromDownloads(offlineTiles, mode.basemap, noSignal, hasDownloadedArea, trailMapDraws, standIn)
    return if (mode.basemap == Basemap.FORAGER_TRAIL) {
        // Trail chosen: something else is shown only when its style has failed to load (RECORD -1513) and the map draws
        // OpenStreetMap's raster in its place. From the downloads it is Trail Map again, which is what was chosen.
        !trailMapDraws && !fromDownloads
    } else {
        fromDownloads
    }
}

/** [LocalNoSignal]'s current value; `false` where nothing provides it, as the map reads it. */
@Composable
private fun rememberNoSignal(): Boolean {
    val flow = LocalNoSignal.current ?: return false
    val noSignal by flow.collectAsState()
    return noSignal
}

/**
 * The map type chips: one chip per [MapMode], [mapMode] selected, a tap choosing a mode (a real choice with or without
 * signal, as before). Drawn in the map type panel the Layers button opens ([MapTypePanel]); until dispatch 2026-10-28-02 it
 * was the Layers sheet's "Map type" row. Sized to its chips ([modifier] places it), so the panel is as small as they are.
 *
 * While the chosen mode stands aside for Trail Map ([chosenMapModeStandsAside]) its chip stays selected but is filled
 * `colorScheme.outlineVariant` (#D5CEBF light, #444444 dark) in place of the normal selected fill ([normalChipColors]),
 * with the normal label colour, `onSurface`, and no outline ([mapModeChipBorder]), so it reads as chosen and not as
 * switched off. Not Material's disabled colours:
 * those dim the label to 38%, which says "can't tap", and a tap here is still a real choice. The Trail chip is not
 * highlighted while Trail Map stands in. The contrast figures this comment gave were measured against the Layers sheet's
 * colour, which the panel does not use; the panel's are recorded in
 * `docs/audits/2026-10-28-layers-to-tools-completion-report.md`.
 */
@Composable
internal fun MapModeChipRow(
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    offlineTiles: Boolean = false,
    fallback: OwnTrailFallback = OWN_TRAIL_FALLBACK,
    modifier: Modifier = Modifier,
) {
    val noSignal = rememberNoSignal()
    val hasDownloadedArea = LocalHasDownloadedArea.current
    // RECORD -1513: false once Trail Map's style has failed to load in this process; Compose state, so the chip follows it.
    val trailMapDraws = fallback.ownTrail()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MapMode.entries.forEach { mode ->
            val standsAside = mode == mapMode && chosenMapModeStandsAside(mode, noSignal, hasDownloadedArea, offlineTiles, trailMapDraws)
            val colors = if (standsAside) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                normalChipColors()
            }
            FilterChip(
                selected = mode == mapMode,
                onClick = { onMapModeSelected(mode) },
                label = { Text(mode.label) },
                colors = colors,
                border = mapModeChipBorder(selected = mode == mapMode, standsAside = standsAside),
                modifier = Modifier.testTag(mapModeChipTag(mode)).semantics { mapModeChipSelectedFill = colors.selectedContainerColor },
            )
        }
    }
}

/**
 * A map type chip's colours when it is not standing aside (dispatch 2026-11-04-07, RECORD -2089). By day, Material's own:
 * the pale `secondaryContainer` #F6DFC8 with its #3A2008 label (A2; the owner: "keep it the same color as it is now").
 * At night, [nightNormalChipColors]. Picked by [LocalForagerDarkTheme], the app's own Night Mode, which is provided from the
 * same value as the colour scheme (Theme.kt), so the fill and the outline in [mapModeChipBorder] cannot disagree.
 */
@Composable
private fun normalChipColors(): SelectableChipColors =
    if (LocalForagerDarkTheme.current) nightNormalChipColors() else FilterChipDefaults.filterChipColors()

/**
 * Night (A3, A6, A7): the selected fill is the rust red [MushroomDeep] #A2612C. The owner: "Rust is a red color. That's
 * meant for night vision", and "Brown reduces that ease at night" (RECORD -2069), so it is a night-vision feature, not a
 * style choice. Only the fill is overridden: the label is Material's own selected label at night, `onSecondaryContainer`
 * #F6DFC8, which the owner chose ("Pale #F6DFC8", RECORD -2093) knowing it stands out 3.81:1 on the rust red.
 *
 * The fill stays solid over the map: the owner's exception to the 80% map chrome rule, for the map type chips only ("Keep
 * them solid as they are today"; "Map type chips only", RECORD -2094).
 */
@Composable
private fun nightNormalChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = MushroomDeep)

/**
 * The outline (A2, A3; the owner: "Let's design a dark outline for the map style chips in day mode. And have the night mode
 * use a white outline as shown"): a 1 dp `onSurface` outline on the selected chip, Bark #3B2E24 by day and #EDE3D0 at night,
 * so the one value covers both themes. The unselected chips keep Material's own 1 dp `outlineVariant` outline, since only
 * the selected parameters are set. The grey chip that stands aside keeps Material's own border, which draws no outline on a
 * selected chip (the owner: "No, normal chip only").
 */
@Composable
private fun mapModeChipBorder(selected: Boolean, standsAside: Boolean): BorderStroke =
    if (standsAside) {
        FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
    } else {
        FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = MaterialTheme.colorScheme.onSurface,
            selectedBorderWidth = 1.dp,
        )
    }
