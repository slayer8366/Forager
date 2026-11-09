package com.zynergylabs.forager.app.ui.map

// Moved from MapChrome.kt unchanged (UI split Phase 1, PR 7, dispatch 2026-10-19-16).

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.MapIconBarAccent
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The add row's (and `MapFloatingIconButton`'s (removed 2026-10-19, RECORD -1392)) permanent accent fill, and the record row's while
 * active — deliberately [MapIconBarAccent], not [MaterialTheme.colorScheme.primary]/
 * [MaterialTheme.colorScheme.error] directly, even though every value it holds is one of those same
 * roles' own hues. See [MapIconBarAccent]'s own doc comment for the full reasoning (Material's tonal
 * inversion exists for legibility against a plain [MaterialTheme.colorScheme.surface]; these two
 * rows sit on [MapIconBar]'s own bar fill instead (the cluster's 0.8 composite, see
 * [MAP_ICON_CLUSTER_CONTAINER_ALPHA]), which already does that job, so reading
 * Material's roles directly here just reads backwards).
 */
private fun mapIconBarAddAccent(isDarkTheme: Boolean) =
    if (isDarkTheme) MapIconBarAccent.ADD_DARK else MapIconBarAccent.ADD_LIGHT

/** See [mapIconBarAddAccent] — the record row's while-active counterpart. */
internal fun mapIconBarRecordAccent(isDarkTheme: Boolean) =
    if (isDarkTheme) MapIconBarAccent.RECORD_DARK else MapIconBarAccent.RECORD_LIGHT

/**
 * The right-edge panel bar — supersedes the individual floating circles decision #3 in
 * `docs/plans/map-redesign.md` specified (see that doc's "Icon stack: superseded from 5 to a
 * 7-icon stopgap" section for the account of the intermediate step this replaces). One
 * translucent, rounded bar hugging the map's right edge, per the project owner's own request,
 * rather than eight separately-floating circles with gaps between them — every control that used
 * to be its own circle is now one row inside this shared bar instead, including
 * [onResetOrientation], which used to be MapLibre's own native compass view (disabled in
 * [SightingsMap] once this existed — see that composable's own doc comment).
 *
 * Top to bottom: fullscreen, orientation-reset, GPS/locate-me, map mode (slot 4 opens
 * `MapModePicker` (removed 2026-10-19, RECORD -1392) — the same picker [MapModeToggle] opens for the untouched MEDIUM/EXPANDED path,
 * restyled rather than reused directly so MEDIUM/EXPANDED's own styling stays untouched), add. The
 * add button keeps its own green fill — real state, not decoration — everything else tints its
 * icon rather than its own background, since the bar itself is the shared background now.
 *
 * **No return-to-vehicle row.** Field-test dispatch item 2 gave the compass strip's own
 * `ReturnToVehicleStripControl` a visible readout and kept this bar's identical row alongside it
 * deliberately, so the field test itself would decide which placement testers actually reached
 * for. The owner's verdict, from real hardware: the compass strip control alone — this row was a
 * confusing duplicate and is removed, not merely hidden.
 *
 * **No record row**, as of an earlier dispatch's Part B — record start/stop moved into
 * `ControlPill` alongside return-to-vehicle, the two Trailhead/Return controls, which belong
 * together rather than split across this bar and the compass strip.
 *
 * **No search row either, as of this dispatch.** The icon here used to open
 * [CompactToolsDrawerContent]; that drawer now opens from the bottom nav's own `CompactTab.TOOLS`
 * entry instead (see that entry's own doc comment) — one entry point, not two, now that Tools is a
 * real bottom-nav destination rather than a row buried in a map-verbs bar. Re-derived directly
 * against the tree rather than assumed: this bar was 6 rows before this change (fullscreen,
 * orientation-reset, locate-me, map mode, search, add), not 7 or 8 as either of the last two
 * dispatches' own text claimed at the time each was written; removing search leaves 5 — fullscreen,
 * orientation-reset, locate-me, map mode, add — which still reads as a coherent group of map verbs
 * (viewport and pin-drop actions only, now that both Trailhead controls and search have moved to
 * homes of their own). [mapIconBarRowAnchorOffset]'s `rowCount` and its callers' own row indices
 * are updated to match.
 */
@Composable
internal fun MapIconBar(
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
    onResetOrientation: () -> Unit,
    mapMode: MapMode,
    /** Opens the Layers sheet (map layers L0b, B1), which replaced the basemap-only `MapModePicker`. */
    onOpenLayers: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Icon-bar-unify-container dispatch: the bar's fill, defaulting to the standing 80% chrome
     * fill ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) so the Cartography entry
     * map's own bare call is unchanged; `CompactMapTab` passes [mapIconClusterChildColor], the
     * lighter fill that composites back to 80% over its cluster container.
     */
    fillColor: Color = Color.Unspecified,
    /**
     * Whether slot 4 (map mode) is present at all — fullscreen-maps dispatch: the Cartography entry
     * map's own offline-tiles toggle needs this row gone while offline tiles are in use, since
     * offline is a single fixed style with nothing to choose between. `true` for every existing
     * caller (nothing before this dispatch had a concept of "offline" to gate it on).
     *
     * **Hidden, not disabled — reversed from that dispatch's original call, deliberately (fullscreen-
     * fixes dispatch, Item 3).** A disabled control with explanatory copy reads as a limitation the
     * app has; an absent one reads as a feature that isn't built yet. Only the second is true here —
     * offline downloads currently produce one vector PMTiles style, structurally unlike the three
     * raster basemaps, and hosting a second needs an account upgrade the owner intends to make. Same
     * principle as this codebase's own "no dormant columns, no disabled placeholders for unbuilt
     * features" rule: don't ship the shape of something before the thing exists. The parameter itself
     * stays — the mechanism this row's presence is gated on — so the row returns unchanged once a
     * second offline style is hosted; only the disabled-state branch and its copy are gone.
     */
    mapModePickerEnabled: Boolean = true,
    /**
     * Landscape L (dispatch 2026-09-28-160): the gap between rows and the padding at the bar's two ends. [Spacing.xs] for every
     * caller that has always had it (the portrait cluster, the Cartography entry map); the short-landscape cluster passes
     * [MAP_ICON_BAR_LANDSCAPE_ROW_SPACING], no spacing at all, so its five 48 dp rows are 240 dp.
     */
    rowSpacing: Dp = Spacing.xs,
    /**
     * Landscape L (owner's ruling (d), continuation 2026-09-28-172): every row takes touches across its full 48 x 48 square, corners
     * included, even where the bar's rounded end curves away. The default draws the fill, border and shadow on a `Surface` that
     * also holds the rows, so the `Surface`'s rounded clip decides which touches the end rows get; with this set the drawn shape is a
     * sibling underneath (a content-less `Surface` sized to the rows) and the rows sit above it unclipped. Nothing outside the rows'
     * squares takes a touch: the shape lies inside their union. `false` for every other caller.
     */
    fullSquareHits: Boolean = false,
    /**
     * The bar's 5th (last) row — fullscreen-maps dispatch: a second map surface (the Cartography
     * entry map) needs this row to mean something other than "plan a trip or log a find here,"
     * since neither concept exists there. A "+" button captioned for trip-planning that actually
     * toggled offline tiles would be wrong on its face, and forking this whole composable just to
     * change one row would defeat the split that put it here in the first place — so the row itself
     * is caller-supplied, defaulting to today's exact add row, unchanged, which is why
     * `CompactMapTab`'s own call site (the only caller before this dispatch) needs no changes at
     * all. [isDarkTheme] is handed in rather than left for the row to re-read from
     * [LocalForagerDarkTheme] itself, matching how the default row below already needs it for
     * [mapIconBarAddAccent].
     */
    fifthRow: @Composable (isDarkTheme: Boolean) -> Unit = { isDarkTheme ->
        MapBarIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Plan a trip or log a find here",
            onClick = onAdd,
            filled = true,
            fillColor = mapIconBarAddAccent(isDarkTheme).fill,
            fillContentColor = mapIconBarAddAccent(isDarkTheme).onFill,
            highlight = MapBarHighlight.BADGE,
        )
    },
) {
    // Independent of the map's own night mode -- see MapIconStackButtonColorDark's own doc
    // comment for why the two axes are kept separate rather than one steering the other.
    val isDarkTheme = LocalForagerDarkTheme.current
    val barFill = fillColor.takeOrElse { if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight }
    val barContentColor = if (isDarkTheme) Color.White else Bark
    // Dispatch 2026-10-13-02: the shared outline, the same 1 dp and colour this line spelled out before.
    val barBorder = mapChromeOutline()
    val rows: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(vertical = rowSpacing),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapBarIconButton(
                icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                onClick = onToggleFullscreen,
            )
            MapBarIconButton(
                icon = Icons.Filled.Navigation,
                contentDescription = "Reset orientation to north",
                onClick = onResetOrientation,
            )
            MapBarIconButton(
                icon = Icons.Filled.MyLocation,
                contentDescription = "Center on my location",
                onClick = onLocateMe,
            )
            if (mapModePickerEnabled) {
                // Map layers L0b, B1: the Layers sheet. The old description's night clause ("Night
                // mode on/off") went with it: the picker never had a night control, and Night Maps
                // is Settings' checkbox.
                MapBarIconButton(
                    icon = Icons.Filled.Layers,
                    contentDescription = layersButtonDescription(mapMode),
                    onClick = onOpenLayers,
                )
            }
            fifthRow(isDarkTheme)
        }
    }
    if (fullSquareHits) {
        Box(modifier = modifier) {
            Surface(
                shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
                color = barFill,
                shadowElevation = 2.dp,
                border = barBorder,
                modifier = Modifier.matchParentSize().testTag(MAP_ICON_BAR_TAG).mapChromeContainerColor(barFill),
            ) {}
            CompositionLocalProvider(LocalContentColor provides barContentColor) { rows() }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
            color = barFill,
            contentColor = barContentColor,
            shadowElevation = 2.dp,
            border = barBorder,
            modifier = modifier.testTag(MAP_ICON_BAR_TAG).mapChromeContainerColor(barFill),
        ) { rows() }
    }
}
