package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.zynergylabs.forager.app.ui.theme.Spacing

/** One "Map type" chip in the Layers sheet, by mode, for tests. */
internal fun mapModeChipTag(mode: MapMode) = "map-mode-chip:${mode.name}"

/** The fill a "Map type" chip is given when selected, on its node, for tests (the colour the chip is told to draw). */
internal val MapModeChipSelectedFill = SemanticsPropertyKey<Color>("MapModeChipSelectedFill")
private var SemanticsPropertyReceiver.mapModeChipSelectedFill by MapModeChipSelectedFill

/**
 * Whether [mode], the chosen map type, is standing aside for Trail Map (dispatch 2026-10-22-06 Amendment 3; the
 * owner: "Have the toggle color be grey to reflect it's chosen, but offline.", then "Same meaning everywhere: grey =
 * chosen, but Trail is being shown instead."). Decided by the map's own rule, [drawsFromDownloads], so the chip and
 * the map cannot disagree. It draws Trail Map from the downloaded areas in place of a chosen OSM or Topographical
 * map on two paths: no signal ([noSignal], [LocalNoSignal] after its hysteresis, the state the map reads), and "Use
 * offline tiles" ([offlineTiles], `MapRenderMode.useOfflineTiles`). For a Trail user it draws Trail Map, which is what
 * they chose, so their chip is unchanged. The stored choice never changes; only the chip's colour does.
 */
internal fun chosenMapModeStandsAside(mode: MapMode, noSignal: Boolean, offlineTiles: Boolean = false): Boolean =
    mode.basemap != Basemap.FORAGER_TRAIL && drawsFromDownloads(offlineTiles, mode.basemap, noSignal)

/** [LocalNoSignal]'s current value; `false` where nothing provides it, as the map reads it. */
@Composable
private fun rememberNoSignal(): Boolean {
    val flow = LocalNoSignal.current ?: return false
    val noSignal by flow.collectAsState()
    return noSignal
}

/**
 * The Layers sheet's "Map type" row: one chip per [MapMode], [mapMode] selected, a tap choosing a mode (a real choice
 * with or without signal, as before). Centred between the sheet's sides (owner, 2026-09-29, dispatch 2026-09-28-104
 * item 9).
 *
 * While the chosen mode stands aside for Trail Map ([chosenMapModeStandsAside]) its chip stays selected but is filled
 * `colorScheme.outlineVariant` (#D5CEBF light, #444444 dark) in place of the normal selected fill, `secondaryContainer`,
 * with the normal label colour, `onSurface`, so it reads as chosen and not as switched off. Measured against the sheet's
 * own colour (`surfaceContainer`, #F4EFE2 and #202020): the grey fill stands 1.36:1 and 1.67:1 off it, where the normal
 * selected fill stands 1.12:1 and 1.88:1, and an unselected chip has no fill at all, only its outline; the label reads
 * 8.4:1 and 7.7:1 on the grey. Not Material's disabled colours: those dim the label to 38%, which says "can't tap",
 * and a tap here is still a real choice. The Trail chip is not highlighted while Trail Map stands in.
 */
@Composable
internal fun MapModeChipRow(mapMode: MapMode, onMapModeSelected: (MapMode) -> Unit, offlineTiles: Boolean = false) {
    val noSignal = rememberNoSignal()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        MapMode.entries.forEach { mode ->
            val standsAside = mode == mapMode && chosenMapModeStandsAside(mode, noSignal, offlineTiles)
            val colors = if (standsAside) {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            }
            FilterChip(
                selected = mode == mapMode,
                onClick = { onMapModeSelected(mode) },
                label = { Text(mode.label) },
                colors = colors,
                modifier = Modifier.testTag(mapModeChipTag(mode)).semantics { mapModeChipSelectedFill = colors.selectedContainerColor },
            )
        }
    }
}
