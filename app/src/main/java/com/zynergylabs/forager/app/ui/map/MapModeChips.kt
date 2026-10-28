package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.zynergylabs.forager.app.ui.theme.Spacing

/** One map type chip ([MapModeChipRow]), by mode, for tests. */
internal fun mapModeChipTag(mode: MapMode) = "map-mode-chip:${mode.name}"

/** The fill a "Map type" chip is given when selected, on its node, for tests (the colour the chip is told to draw). */
internal val MapModeChipSelectedFill = SemanticsPropertyKey<Color>("MapModeChipSelectedFill")
private var SemanticsPropertyReceiver.mapModeChipSelectedFill by MapModeChipSelectedFill

/** The outline a "Map type" chip is given when selected, on its node, for tests; [Color.Unspecified] when it has none. */
internal val MapModeChipSelectedOutline = SemanticsPropertyKey<Color>("MapModeChipSelectedOutline")
private var SemanticsPropertyReceiver.mapModeChipSelectedOutline by MapModeChipSelectedOutline

/**
 * Whether [mode], the chosen map type, is standing aside for Trail Map (dispatch 2026-10-22-06 Amendment 3; the
 * owner: "Have the toggle color be grey to reflect it's chosen, but offline.", then "Same meaning everywhere: grey =
 * chosen, but Trail is being shown instead."). Decided by the map's own rule, [drawsFromDownloads], so the chip and
 * the map cannot disagree. It draws Trail Map from the downloaded areas in place of a chosen OSM or Topographical
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
): Boolean {
    val fromDownloads = drawsFromDownloads(offlineTiles, mode.basemap, noSignal, hasDownloadedArea, trailMapDraws)
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
 * `colorScheme.outlineVariant` (#D5CEBF light, #444444 dark) in place of the normal selected fill, `secondaryContainer`,
 * with the normal label colour, `onSurface`, so it reads as chosen and not as switched off. Not Material's disabled colours:
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
    fallback: OwnStreetFallback = OWN_STREET_FALLBACK,
    modifier: Modifier = Modifier,
) {
    val noSignal = rememberNoSignal()
    val hasDownloadedArea = LocalHasDownloadedArea.current
    // RECORD -1513: false once Trail Map's style has failed to load in this process; Compose state, so the chip follows it.
    val trailMapDraws = fallback.ownStreet()
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
                FilterChipDefaults.filterChipColors()
            }
            FilterChip(
                selected = mode == mapMode,
                onClick = { onMapModeSelected(mode) },
                label = { Text(mode.label) },
                colors = colors,
                modifier = Modifier.testTag(mapModeChipTag(mode)).semantics {
                    mapModeChipSelectedFill = colors.selectedContainerColor
                    mapModeChipSelectedOutline = Color.Unspecified
                },
            )
        }
    }
}
