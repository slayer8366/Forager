package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-15, UI split Phase 1, PR 9b).

import androidx.compose.foundation.layout.BoxScope
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.map.JournalEntriesMapChip
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.motion.MapPopUp
import com.zynergylabs.forager.app.ui.motion.PopUpPivot
import com.zynergylabs.forager.app.ui.motion.isOnScreen
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import com.zynergylabs.forager.app.ui.motion.rememberPopUpState
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The taxon and journal-entries chips, moved out of [CompactMapTab] unchanged (RECORD -741). */
@Composable
internal fun BoxScope.CompactMapChips(
    mapTaxonFilterLabel: String?,
    onClearTaxonFilter: () -> Unit,
    mapLayers: MapLayersControls,
    landscape: CompactMapLandscape,
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    topInset: Dp,
    compassStripBottomClearance: Dp,
) {
    val punchHoleEdge = landscape.punchHoleEdge
    val landscapeSearchWidth = landscape.landscapeSearchWidth
    val isMapIconBarOnLeftSide by cluster::isOnLeftSide
    val mapIconClusterWidthPx by cluster::clusterWidthPx
    // Below the compass strip (topInset + compassStripClearance as top padding), same
    // reasoning as AnchoredAtScreenPoint's own minY: the strip is drawn across the map's
    // full width, so a chip placed underneath it would be hidden behind it, and where it
    // sat under the strip's coordinates or its quick-settings button (dispatch -645), its
    // "Show all species" tap would go to them. Corrected in dispatch 2026-09-28-645: this
    // said "the strip's Surface intercepts touches across its full width", which stopped
    // being true when the strip became a plain Box that takes no touches of its own
    // (CompassElevationStripContent); its real long-press tests reach the map through it. topInset itself (see this composable's own doc comment) clears whatever
    // chrome floats above the strip too — SearchEntryBar, on the Map tab.
    //
    // J8-3 (owner: "Top, by the species chip (Recommended)"): the journal-entries chip sits in
    // the row with the taxon chip, after it, at the same place in each window. A FlowRow sized
    // to its chips (it draws nothing and takes no touches itself, so the map keeps every touch
    // around them: CLAUDE.md, the Surface pitfall), so two chips wider than the room wrap to a
    // second line instead of running off the screen. It holds whichever chips there are.
    val shownJournalEntries = mapLayers.journalHighlights.shownEntries
    // Motion Part 2, item 4 (scout M1; the owner, RECORD -651: "Fade and grow", from where each belongs): each chip
    // fades and grows down from its top centre, under the strip it hangs from, and shrinks back up to it; item 5: a
    // leaving chip takes no touch. The row stays composed until its last chip has gone, and gives up its keep-out
    // the moment no chip is meant to show.
    val taxonChipShown = rememberLastShown(mapTaxonFilterLabel)
    val taxonChip = rememberPopUpState(mapTaxonFilterLabel != null)
    val journalChipShown = rememberLastShown(shownJournalEntries.takeIf { it.isNotEmpty() })
    val journalChip = rememberPopUpState(shownJournalEntries.isNotEmpty())
    val chipsMeantToShow = mapTaxonFilterLabel != null || shownJournalEntries.isNotEmpty()
    if (taxonChip.isOnScreen || journalChip.isOnScreen) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            // Part 1 layout fixes (the owner's "Option B for the chips", planner message 2026-09-29-07): in
            // short landscape the wrapped second line follows the first with no gap, so the drawn chips of a
            // two-line row end above the window's central third. J8's chip keeps its 48 dp layout box (its
            // drawn pill is centred in it, 8 dp of margin above and below), so the margin, not the chip,
            // takes the space. Portrait keeps its 4 dp.
            verticalArrangement = Arrangement.spacedBy(if (punchHoleEdge != null && landscapeSearchWidth != null) 0.dp else Spacing.xs),
            modifier = if (punchHoleEdge != null && landscapeSearchWidth != null) {
                // Landscape B2 (S3): directly under the search bar (the strip is in
                // the rail corner now, not under the bar), in a column the bar's own
                // width on the punch-hole side, aligned to the bar's start.
                //
                // Part 1 layout fixes, item 7 (the owner's "1 A", planner message 2026-09-29-04): the
                // row aligns to the bar's end away from the cluster's current side, and its width is
                // capped at the bar's width less the cluster's edge inset, measured width and the gap
                // beside it, so two chips that do not fit wrap onto two lines instead of reaching
                // under the cluster. Both follow the cluster when it is dragged or snapped across.
                // Planner message 2026-09-29-05: the cap only where the cluster sits under the bar's
                // reach (it is on the bar's side); elsewhere the chips have room and stay on one line.
                val clusterUnderBar = isMapIconBarOnLeftSide == (punchHoleEdge == ScreenEdge.Left)
                val clusterColumnDp = MAP_ICON_BAR_EDGE_INSET + with(LocalDensity.current) { mapIconClusterWidthPx.toDp() } + Spacing.sm
                Modifier
                    .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                    .padding(controlsPadding)
                    .padding(top = topInset + Spacing.sm)
                    .width(landscapeSearchWidth)
                    .wrapContentWidth(if (isMapIconBarOnLeftSide) AbsoluteAlignment.Right else AbsoluteAlignment.Left)
                    .widthIn(max = if (clusterUnderBar) (landscapeSearchWidth - clusterColumnDp).coerceAtLeast(0.dp) else landscapeSearchWidth)
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(controlsPadding)
                    .padding(top = topInset + compassStripBottomClearance + Spacing.sm)
            }.then(if (chipsMeantToShow) Modifier.mapKeepOut(MapKeepOutIds.CHIPS) else Modifier),
        ) {
            MapPopUp(state = taxonChip, pivot = PopUpPivot.TopCentre) {
                taxonChipShown?.let { label -> TaxonMapFilterChip(label = label, onClear = onClearTaxonFilter) }
            }
            MapPopUp(state = journalChip, pivot = PopUpPivot.TopCentre) {
                journalChipShown?.let { entries ->
                    JournalEntriesMapChip(
                        entries = entries,
                        onHide = mapLayers.onHideJournalEntry,
                        onHideAll = mapLayers.onHideAllJournalEntries,
                    )
                }
            }
        }
    }
}
