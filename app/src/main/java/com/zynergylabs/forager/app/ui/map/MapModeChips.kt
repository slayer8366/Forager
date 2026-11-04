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
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.MushroomDeep
import com.zynergylabs.forager.app.ui.theme.PureWhite
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
 * `colorScheme.outlineVariant` (#D5CEBF light, #444444 dark) in place of the normal selected fill (below),
 * with the normal label colour, `onSurface`, so it reads as chosen and not as switched off. Not Material's disabled colours:
 * those dim the label to 38%, which says "can't tap", and a tap here is still a real choice. The Trail chip is not
 * highlighted while Trail Map stands in. The contrast figures this comment gave were measured against the Layers sheet's
 * colour, which the panel does not use; the panel's are recorded in
 * `docs/audits/2026-10-28-layers-to-tools-completion-report.md`.
 *
 * In the light theme the normal selected chip keeps Material's own selected fill, `secondaryContainer` (#F6DFC8), and label:
 * the owner had Amendment 4 of dispatch 2026-10-28-02 (RECORD -1771) deleted with its code ("Delete amendment 4 and its
 * coding", then "Pale default + outline", RECORD -2073), and the cream that later replaced its fill went with it. In the dark
 * theme it is filled [darkSelectedChipColors], rust red (RECORD -2067; the owner: "Night mode gets the red").
 *
 * Every selected chip, normal or grey, in both themes, carries [selectedChipBorder], a 1 dp outline in the theme's
 * `onSurface` (Amendments 5 and 6, RECORD -1773 and -1774; the owner: "Give the grey chip an outline too", then "Both themes.
 * Make it uniform"). The unselected chips keep Material's own outline.
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
    val isDarkTheme = LocalForagerDarkTheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MapMode.entries.forEach { mode ->
            val standsAside = mode == mapMode && chosenMapModeStandsAside(mode, noSignal, hasDownloadedArea, offlineTiles, trailMapDraws)
            val selected = mode == mapMode
            val colors = when {
                standsAside -> FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                )
                isDarkTheme -> darkSelectedChipColors()
                else -> FilterChipDefaults.filterChipColors()
            }
            val selectedOutline = MaterialTheme.colorScheme.onSurface
            FilterChip(
                selected = selected,
                onClick = { onMapModeSelected(mode) },
                label = { Text(mode.label) },
                colors = colors,
                border = selectedChipBorder(selected),
                modifier = Modifier.testTag(mapModeChipTag(mode)).semantics {
                    mapModeChipSelectedFill = colors.selectedContainerColor
                    mapModeChipSelectedOutline = selectedOutline
                },
            )
        }
    }
}

/**
 * The dark theme's normal selected fill (RECORD -2067): [MushroomDeep] #A2612C, the light scheme's `secondary`, named because
 * the dark scheme's `secondary` is another colour (#E5A76B). The label is white ([PureWhite], 4.90:1 on it, AA; the theme's
 * dark `onSurface`, Cream, would be 3.85:1). The outline is [selectedChipBorder]'s, as for every selected chip.
 */
@Composable
private fun darkSelectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MushroomDeep,
    selectedLabelColor = PureWhite,
)

/**
 * The selected outline, normal or grey, both themes (Amendments 4 to 6): the theme's `onSurface` (#3B2E24 light, #EDE3D0
 * dark), 1 dp, the width of the unselected chips' outline.
 */
@Composable
private fun selectedChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    selectedBorderColor = MaterialTheme.colorScheme.onSurface,
    selectedBorderWidth = 1.dp,
)
