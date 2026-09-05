package com.forager.app.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.forager.app.ui.motion.MotionTokens
import com.forager.app.ui.theme.Bark
import com.forager.app.ui.theme.Cream
import com.forager.app.ui.theme.LocalForagerDarkTheme
import com.forager.app.ui.theme.MapIconBarAccent
import com.forager.app.ui.theme.Spacing

/** M3's minimum touch target size — see [MapModeToggle]'s doc comment for where this was missed. */
internal val MIN_TOUCH_TARGET = 48.dp

/**
 * [MapIconBar]'s own inset from the true screen edge at its call site (`CompactMapTab`), and the
 * one value [MapIconBarMinimizeHandle]/[MapIconBarRestoreHandle] key their visible marks off so
 * those marks straddle the bar's own outer edge rather than sitting inside it — fullscreen-
 * slide-out-fixes dispatch, Item 3. Named here, not left as a bare `Spacing.sm` at each site,
 * precisely so the bar's padding and the handles' geometry can't drift apart.
 */
internal val MAP_ICON_BAR_EDGE_INSET = Spacing.sm

/**
 * Offset from [MapIconBar]'s own vertical center (where both `AddActionTile` and [MapModePicker]
 * anchor their `Alignment.CenterEnd`-based popups) to the center of one of its rows, counting from
 * the top. Promoted here from `AvailabilityScreen.kt` (fullscreen-fixes dispatch, Item 2) so the
 * Cartography entry map's own `MapModePicker` call can anchor against [MapIconBar]'s real row 4
 * the same way the main map's call already does, instead of duplicating this arithmetic or falling
 * back to a default meant for the medium/expanded window class's single floating button. Not
 * pixel-exact — no caller tracks its own button's real measured position — but close enough that
 * each popup visibly grows from that button's corner rather than from an unrelated point on screen.
 */
// rowCount re-derived directly against the tree, not assumed: this bar sits at 5 rows as of the
// fullscreen-maps dispatch (fullscreen, orientation-reset, locate-me, map mode, fifth row) — see
// MapIconBar's own doc comment.
internal fun mapIconBarRowAnchorOffset(rowIndexFromTop: Int): Dp {
    val rowCount = 5
    val contentHeight = MIN_TOUCH_TARGET * rowCount + Spacing.xs * (rowCount - 1)
    val rowCenterFromTop = (MIN_TOUCH_TARGET + Spacing.xs) * (rowIndexFromTop - 1) + MIN_TOUCH_TARGET / 2
    return rowCenterFromTop - contentHeight / 2
}

/**
 * [MapModePicker]'s own panel `Surface`, for tests that measure where the panel actually opened
 * (expanded-panels dispatch). A test hook only — no behaviour, layout, or API change to this
 * shared composable. See `ADD_ACTION_TILE_TAG`'s own doc comment for why the chips inside are not
 * a usable proxy for the panel's edges under Robolectric.
 */
internal const val MAP_MODE_PICKER_TAG = "map-mode-picker"

/**
 * The picker [MapModeToggle] (medium/expanded) and [MapIconBar]'s layers row (compact) both open —
 * three real [FilterChip]s, not a list of full-width text rows: the exact "too wide" hardware
 * finding [AddActionTile] carried (a full-width button per row makes the whole tile as wide as its
 * longest label) is avoided here from the start rather than repeated. `selected` shows the current
 * mode without a separate label; tapping any chip both applies it and dismisses the picker — one
 * tap, not select-then-confirm.
 *
 * Same theme-aware, 80%-opacity fill as [MapIconBar]
 * ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) rather than a plain
 * [MaterialTheme.colorScheme.surface] popup — one visual language for every control floating over
 * the map, not a Material default that reads as a different kind of thing next to the bar it opens
 * from.
 *
 * **Same animation/sizing/anchoring shape as [AddActionTile]**, per the project owner's own
 * comparison of the two once both existed on screen together: two independent [AnimatedVisibility]s
 * (scrim, then content, so the scrim's own fade doesn't gate the tile's expand/shrink), the content
 * one sized to what three chips need rather than [fillMaxSize], and anchored precisely against
 * whichever control opened it rather than a fixed corner offset that merely sat close by. [anchor]
 * and [anchorOffset] are how the two callers differ here where [AddActionTile] didn't need to: that
 * tile only ever grows from the icon bar's own add row, but this picker opens from two different
 * shapes of control — a lone circular [MapModeToggle] floating at the map's own top-right corner on
 * medium/expanded, and [MapIconBar]'s layers row (row 4 of 5) on compact — so the anchor is a
 * parameter instead of a second constant hardcoded in here. [mapIconBarRowAnchorOffset] computes
 * the compact case for both this picker's row 4 and the add row's row 5; the medium/expanded
 * default below matches [MapModeToggle]'s own fixed position.
 */
@Composable
internal fun MapModePicker(
    visible: Boolean,
    mapMode: MapMode,
    onModeSelected: (MapMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.TopEnd,
    anchorOffset: DpOffset = DpOffset(x = -Spacing.sm, y = MIN_TOUCH_TARGET + Spacing.lg),
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Box(modifier = modifier.fillMaxSize()) {
        // docs/motion-spec.md §2 "Panels and navigation" — same spec AddActionTile's own scrim uses.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()) +
                expandIn(animationSpec = MotionTokens.panelMotionSpec(), expandFrom = anchor),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()) +
                shrinkOut(animationSpec = MotionTokens.panelMotionSpec(), shrinkTowards = anchor),
            modifier = Modifier
                .align(anchor)
                .offset(x = anchorOffset.x, y = anchorOffset.y),
        ) {
            Surface(
                modifier = Modifier.testTag(MAP_MODE_PICKER_TAG),
                shape = RoundedCornerShape(Spacing.md),
                color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                contentColor = if (isDarkTheme) Color.White else Bark,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    MapMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == mapMode,
                            onClick = {
                                onModeSelected(mode)
                                onDismiss()
                            },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }
    }
}

/** The filled circle behind an active/permanent-accent row (add, record while recording) inside [MapIconBar] — smaller than [MIN_TOUCH_TARGET] so it reads as a badge within the row rather than filling it edge to edge. */
private val MAP_ICON_BAR_FILL_DIAMETER = 36.dp

/** Half [MIN_TOUCH_TARGET], so [MapIconBar]'s rounded corners read as a stadium/pill shape rather than a slightly-rounded rectangle. */
internal val MAP_ICON_BAR_CORNER_RADIUS = MIN_TOUCH_TARGET / 2

/**
 * [MapIconBar]'s fill — see [MapBarIconButton]. Picked per [com.forager.app.ui.theme.LocalForagerDarkTheme]
 * (the app's own "Night Mode" checkbox, not the device's system theme — see that local's own doc
 * comment for the bug reading [androidx.compose.foundation.isSystemInDarkTheme] directly here caused), independent of
 * the map's own night mode (deliberately not derived from it, the same way [MapPalette] is
 * deliberately not derived from the ambient scheme — this is app chrome over the map, not a mark
 * on it, but the two axes are still kept separate on principle: a device in light mode looking at
 * a map in night mode, or the reverse, are both real states, and neither should silently steer
 * the other).
 *
 * **History: opaque, plus a hairline edge, replacing the translucent fill these circles used
 * before.** Confirmed on real hardware (Portland-metro, USGS Topo): at a 78%-alpha fill, map data
 * underneath — contour lines, place labels — composited straight through the buttons, reading as
 * barely-there smudges; the stack's one already-opaque icon (the green "add" button,
 * [MaterialTheme.colorScheme.primary]) read perfectly on the same terrain in the same screenshot.
 * Opacity was the only difference. **Requested back down to 80% since** — a deliberate, informed
 * choice after that finding was raised again, not a reversion made without knowing it. Provisional
 * pending a hardware look at this specific value, the same status the border below already had.
 *
 * **Rejected alternative: tuning the alpha per basemap.** A single translucency value can be tuned
 * to look right against one basemap's palette, but this app ships pale topo, dark aerial imagery,
 * and (later) hillshade — a fill that reads on one will not read on the others, and per-basemap
 * tuning means re-solving this every time a basemap is added.
 *
 * The hairline border ([MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT]) is
 * what keeps a *dark* fill working on imagery specifically: an opaque dark circle alone reads fine
 * against pale topo but risks merging into imagery, which is dark nearly everywhere. A light edge
 * separates control from map regardless of what's underneath. The light-theme fill below is new,
 * unverified on hardware in either direction — its own dark hairline border mirrors the same
 * reasoning for the opposite risk (merging into snow, sand, or other pale terrain), but nobody has
 * looked at it on a real screen yet.
 */
internal val MapIconStackButtonColorDark = Bark.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/** [MapIconStackButtonColorDark]'s light-theme counterpart — see that color's own doc comment. */
internal val MapIconStackButtonColorLight = Cream.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA)

/** The standing opacity for chrome floating over the map — the one value every fill here targets. */
internal const val MAP_CHROME_OVER_MAP_ALPHA = 0.8f

/**
 * Icon-bar-unify-container dispatch: [MapIconBar] and `TrailheadControls` now sit inside one
 * filled container (their cluster — measured, dragged, clamped and minimised as one), and the
 * owner's design layers two lighter fills so the cluster still reads at [MAP_CHROME_OVER_MAP_ALPHA]
 * where a child sits over the container. **Layered alpha composites; it does not add.** Two
 * same-colour layers read as `top + bottom × (1 − top)` ([srcOverAlpha]), so these two were chosen
 * to land on exactly 0.8 — 0.6 under 0.5 — not summed to it (0.5 under 0.3 would read as ~0.65).
 * `MapChromeAlphaTest` asserts the composite of these declared constants, so the pair cannot drift
 * apart without a test saying so; it does not pretend to verify appearance, which only a real
 * screen over real terrain can. The owner read "the pills a lighter one again" literally: the
 * children are the lighter layer. Where the container extends past its children (the gap between
 * bar and pill, the arm's own width) it reads at the container's own 0.6 — intentional, that
 * region holds no control. Every icon sits on a child, i.e. on the full 0.8 composite, so icon
 * legibility against bright terrain is unchanged from before the container existed.
 */
internal const val MAP_ICON_CLUSTER_CONTAINER_ALPHA = 0.6f

/** See [MAP_ICON_CLUSTER_CONTAINER_ALPHA]. Applied to [MapIconBar] (via its `fillColor`) and both Trailhead pills inside the cluster — the bar is a child too, or its region would composite to 0.92, not 0.8. */
internal const val MAP_ICON_CLUSTER_CHILD_ALPHA = 0.5f

/** Source-over compositing of two same-colour translucent layers: the resulting alpha of [top] drawn over [bottom]. */
internal fun srcOverAlpha(top: Float, bottom: Float): Float = top + bottom * (1f - top)

/** The cluster container's own fill — see [MAP_ICON_CLUSTER_CONTAINER_ALPHA]. */
@Composable
@ReadOnlyComposable
internal fun mapIconClusterContainerColor(): Color =
    (if (LocalForagerDarkTheme.current) Bark else Cream).copy(alpha = MAP_ICON_CLUSTER_CONTAINER_ALPHA)

/** The fill of each child inside the cluster container — see [MAP_ICON_CLUSTER_CHILD_ALPHA]. */
@Composable
@ReadOnlyComposable
internal fun mapIconClusterChildColor(): Color =
    (if (LocalForagerDarkTheme.current) Bark else Cream).copy(alpha = MAP_ICON_CLUSTER_CHILD_ALPHA)

/** The hairline edge for whichever theme is current — [MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT]. */
@Composable
@ReadOnlyComposable
internal fun mapIconStackBorderColor(): Color =
    if (LocalForagerDarkTheme.current) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT

/** The hairline edge on [MapIconStackButtonColorDark] circles — see that color's own doc comment. */
internal val MAP_ICON_STACK_BORDER_COLOR_DARK = Color.White.copy(alpha = 0.4f)

/** The hairline edge on [MapIconStackButtonColorLight] circles — the inverse of [MAP_ICON_STACK_BORDER_COLOR_DARK], for the inverse risk (a light fill merging into pale terrain rather than a dark one merging into dark imagery). */
internal val MAP_ICON_STACK_BORDER_COLOR_LIGHT = Bark.copy(alpha = 0.4f)

/**
 * The add row's (and [MapFloatingIconButton]'s) permanent accent fill, and the record row's while
 * active — deliberately [MapIconBarAccent], not [MaterialTheme.colorScheme.primary]/
 * [MaterialTheme.colorScheme.error] directly, even though every value it holds is one of those same
 * roles' own hues. See [MapIconBarAccent]'s own doc comment for the full reasoning (Material's tonal
 * inversion exists for legibility against a plain [MaterialTheme.colorScheme.surface]; these two
 * rows sit on [MapIconBar]'s own opaque bar fill instead, which already does that job, so reading
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
 * [MapModePicker] — the same picker [MapModeToggle] opens for the untouched MEDIUM/EXPANDED path,
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
    onOpenMapModePicker: () -> Unit,
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
     * Night mode as it currently resolves — Settings' "Night Maps" checkbox
     * ([AvailabilityUiState.nightModeMaps]), shown here in slot 4's content description so the
     * state is readable rather than merely visible. No longer toggleable from this bar directly
     * (a long-press here used to hold it; that control moved to Settings — see
     * [com.forager.app.domain.MapPreferencesRepository.getNightModeMaps]'s own doc comment).
     */
    isNightMode: Boolean = false,
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
        )
    },
) {
    // Independent of the map's own night mode -- see MapIconStackButtonColorDark's own doc
    // comment for why the two axes are kept separate rather than one steering the other.
    val isDarkTheme = LocalForagerDarkTheme.current
    Surface(
        shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
        color = fillColor.takeOrElse { if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight },
        contentColor = if (isDarkTheme) Color.White else Bark,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                MapBarIconButton(
                    icon = Icons.Filled.Layers,
                    contentDescription = buildString {
                        append("Map mode: ${mapMode.label}. Choose Street, Topographical, or Satellite.")
                        // Appended rather than replacing the tap description: the button still
                        // primarily opens the map mode picker, and a reader needs to know night mode
                        // is on — now toggled from Settings' "Night Maps" checkbox, not from here.
                        append(if (isNightMode) " Night mode on." else " Night mode off.")
                    },
                    onClick = onOpenMapModePicker,
                )
            }
            fifthRow(isDarkTheme)
        }
    }
}

/**
 * The trigger that minimises [MapIconBar] — fullscreen-fixes dispatch, Item 3. The dispatch
 * specifies the restore affordance precisely ([MapIconBarRestoreHandle]'s own rectangular-outline
 * design) but leaves open how the bar is minimised in the first place — a decision made here rather
 * than flagged, since it is mechanical rather than architectural.
 *
 * **Owner's design for this control:** a drag box on the right side of the icon bar, at mid-height
 * — attached to the bar's own edge rather than stacked above or below its rows, rectangular (not a
 * circle/icon button), and themed the same opaque-fill-plus-hairline-border way as [MapIconBar]
 * itself ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) so it reads as an extension
 * of the bar rather than an unrelated new control. Tapping it slides the bar (and, at the call
 * site, [TrailheadControls] alongside it — see that composable's own doc comment) away.
 *
 * Kept as its own [Surface], a sibling of [MapIconBar] at the call site rather than folded into
 * that composable's own Column as a 6th row: [mapIconBarRowAnchorOffset]'s own `rowCount` and the
 * two popups anchored against specific rows ([MapModePicker], `AddActionTile`) assume exactly 5,
 * re-derived directly against the tree per that function's own doc comment — adding a 6th row here
 * would silently invalidate that arithmetic for an unrelated feature.
 *
 * Sized [MIN_TOUCH_TARGET] wide by 1.5x that tall — clearly a rectangle, not a square icon button,
 * per the owner's own description, while still meeting the touch-target floor in both dimensions
 * (exceeding it in height) rather than trading width for a "thinner" look the way a real drag
 * handle might.
 */
@Composable
internal fun MapIconBarMinimizeHandle(
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Direct owner request, layered on top of Item 3: the bar (and both its handles) can be
     * dragged to either screen edge. `false` (the bar's own original right-side position) rounds
     * the corners facing into the map, matching [MapIconBarRestoreHandle]'s own "rounded toward
     * the map, square toward the true edge" reasoning; `true` mirrors that shape for the left edge
     * so the rounding still faces inward rather than reading backwards once the bar is over there.
     */
    onLeftSide: Boolean = false,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val shape = if (onLeftSide) {
        RoundedCornerShape(topEnd = Spacing.xs, bottomEnd = Spacing.xs, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = Spacing.xs, bottomStart = Spacing.xs, topEnd = 0.dp, bottomEnd = 0.dp)
    }
    // Box + Modifier.clickable, matching MapBarIconButton's own proven pattern elsewhere in this
    // file, rather than a Material3 Surface(onClick = ...) — confirmed, via an isolated standalone
    // test, that either shape correctly invokes onMinimize on its own; picked Box+clickable for
    // consistency with every other row in this bar rather than for a functional difference between
    // the two. (A Surface(onClick=...) version of this control was tried first and, embedded in the
    // real AvailabilityScreen tree, never invoked onMinimize under Robolectric even once across ten
    // retried performClick() calls — but the same isolated test proved that specific composable
    // shape innocent; the actual cause was the pre-existing, documented
    // docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md harness bug, which the
    // integration test this control needed happened to trigger via searchAReferenceRegion()'s own
    // still-open SearchDropdown, not this Surface/Box choice.)
    //
    // Icon-bar-drag-refinements dispatch, Item 1: the outer Box below is the full 48×72dp tap
    // target, undecorated — the same "hit area never shrinks, only the visible mark does, inset by
    // padding" pattern [MapIconBarRestoreHandle] already established (see that composable's own doc
    // comment and CLAUDE.md's own documented pitfall on this exact class of control). The owner's
    // own device report: the first version drew the fill across the *entire* tap width, reading as
    // a wide slab eating most of the bar's own width — this narrows the drawn mark to
    // [HANDLE_VISIBLE_MARK_WIDTH] while the tap target itself stays exactly what it was.
    //
    // Fullscreen-slide-out-fixes dispatch, Item 3: the mark straddles the bar's own outer edge
    // rather than sitting centered in the tap box. The tap box's outer side is flush with the true
    // screen edge and the bar's outer edge is [MAP_ICON_BAR_EDGE_INSET] in from it — so a mark
    // centered in the box sat 19dp in from the true edge, 11dp *inside* the bar, exactly the "sits
    // within the bar's width" the owner saw. Aligning the mark to the box's outer side, padded by
    // [HANDLE_MARK_EDGE_PADDING], centers it on the bar's edge: half overlapping the bar, half
    // protruding outward, mirrored per side. Padding only — the tap box never moves or shrinks.
    Box(
        modifier = modifier
            .size(width = MIN_TOUCH_TARGET, height = MIN_TOUCH_TARGET * 1.5f)
            .clickable(onClick = onMinimize)
            .semantics { contentDescription = "Hide map controls" }
            .testTag("map-icon-bar-minimize-handle"),
    ) {
        Box(
            modifier = Modifier
                .align(if (onLeftSide) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (onLeftSide) HANDLE_MARK_EDGE_PADDING else 0.dp,
                    end = if (onLeftSide) 0.dp else HANDLE_MARK_EDGE_PADDING,
                )
                .padding(vertical = Spacing.md)
                .fillMaxHeight()
                .width(HANDLE_VISIBLE_MARK_WIDTH)
                .testTag("map-icon-bar-minimize-handle-mark")
                .background(color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight, shape = shape)
                .border(
                    width = 1.dp,
                    color = if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT,
                    shape = shape,
                ),
        )
    }
}

/** How much of [MapIconBarMinimizeHandle]'s / [MapIconBarRestoreHandle]'s own 48dp-wide tap target is actually drawn — see either composable's own doc comment for why this is much narrower than the hit area itself. */
private val HANDLE_VISIBLE_MARK_WIDTH = 10.dp

/**
 * Padding between a handle's tap box's outer side and its visible mark, chosen so the mark's own
 * centre lands exactly on [MapIconBar]'s outer edge ([MAP_ICON_BAR_EDGE_INSET] in from the true
 * screen edge, which the tap box is flush with): half the mark overlaps the bar, half protrudes.
 * Derived, not a magic number — see [MapIconBarMinimizeHandle]'s own Item 3 comment.
 */
private val HANDLE_MARK_EDGE_PADDING = (MAP_ICON_BAR_EDGE_INSET - HANDLE_VISIBLE_MARK_WIDTH / 2).coerceAtLeast(0.dp)

/**
 * The restore control for a minimised [MapIconBar] — fullscreen-fixes dispatch, Item 3.
 * [MapIconBar] holds the only way out of fullscreen, and that same dispatch's Item 2 slides
 * `ForagerBottomNav` fully off-screen while fullscreen, so a minimised bar with no restore control
 * would leave a fullscreen user stuck on a bare map with no way back. This handle is composed in
 * [MapIconBar]'s place whenever the bar is minimised, in every state — independent of
 * `isMapFullscreen`, per this feature's own "do not tie it to isMapFullscreen" instruction — so it
 * is always there to tap.
 *
 * The tappable region is a full [MIN_TOUCH_TARGET] square, the same floor every other control in
 * this file meets; the "small rectangular outline peeking out from the right edge" the project
 * owner asked for is a thin bordered rectangle centered inside it via padding — visually much
 * smaller than the tap target, without the tap target itself shrinking to match. That distinction
 * matters here specifically: this is a small `Surface`-shaped control sitting at the map's own
 * edge, precisely the shape of thing that has silently swallowed map touches three times before
 * (see this file's own history on [MapIconBar] and `CLAUDE.md`'s "Known pitfalls") — but the fix
 * for that failure mode is a full-size hit area with a smaller visible mark inside it, not a
 * smaller hit area, which is a different (and here, wrong) way to shrink a control.
 *
 * Filled, at the standing over-map value ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight],
 * [MAP_CHROME_OVER_MAP_ALPHA]) — persist-fullscreen dispatch, Item 2, from the owner's device
 * pass: the bare outline read as a white mark over the map and was hard to spot against pale
 * terrain. The cluster it stands in for layers 0.6 under 0.5 to composite to 0.8, but a lone
 * handle has nothing beneath it to composite against, so the single value that matches the
 * cluster's weight is the composite itself, not either layer. Findability, not prominence: the
 * size, the padding-inset mark, the outline and the full 48dp tap box are all unchanged; only the
 * fill is new. Rounding only the two corners facing into the map (the edge nearer the screen's
 * own edge stays square) so the shape itself reads as something tucked against, and emerging
 * from, that edge. Legibility over real terrain in either theme is a device check.
 */
@Composable
internal fun MapIconBarRestoreHandle(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    /** See [MapIconBarMinimizeHandle]'s own `onLeftSide` doc comment — same reasoning, mirrored shape. */
    onLeftSide: Boolean = false,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val outlineShape = if (onLeftSide) {
        RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp, topStart = 0.dp, bottomStart = 0.dp)
    } else {
        RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }
    // Fullscreen-slide-out-fixes dispatch, Item 3 (extended to this handle on the owner's own
    // call): the outline straddles where the bar's own outer edge sits — [HANDLE_MARK_EDGE_PADDING]
    // in from the tap box's outer side — rather than centered in the tap box, so it genuinely
    // peeks from the edge instead of floating 19dp inboard of it. Same padding-only mechanism as
    // [MapIconBarMinimizeHandle]; the 48dp tap box itself never moves or shrinks.
    Box(
        modifier = modifier
            .size(MIN_TOUCH_TARGET)
            .clickable(onClick = onRestore)
            .semantics { contentDescription = "Show map controls" },
    ) {
        Box(
            modifier = Modifier
                .align(if (onLeftSide) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (onLeftSide) HANDLE_MARK_EDGE_PADDING else 0.dp,
                    end = if (onLeftSide) 0.dp else HANDLE_MARK_EDGE_PADDING,
                )
                .padding(vertical = Spacing.md)
                .fillMaxHeight()
                .width(HANDLE_VISIBLE_MARK_WIDTH)
                .testTag("map-icon-bar-restore-handle-mark")
                .background(
                    color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                    shape = outlineShape,
                )
                .border(
                    width = 1.5.dp,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Bark.copy(alpha = 0.7f),
                    shape = outlineShape,
                ),
        )
    }
}

/**
 * One row inside [MapIconBar] — a plain tap target tinted by state, not its own circle: the bar
 * itself is the shared background now, so a per-icon background is reserved for the two rows that
 * carry real, not merely decorative, fill state ([filled]/[fillColor]/[fillContentColor]: the add
 * button's permanent accent, the record button's error accent while active — see
 * [MapIconBarAccent]'s own doc comment for why each caller passes its own theme-swapped pair here
 * rather than reading [MaterialTheme.colorScheme.primary]/[error] directly).
 */
@Composable
internal fun MapBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gives this row its own filled circle in [fillColor] rather than tinting just the icon. */
    filled: Boolean = false,
    fillColor: Color = Color.Unspecified,
    /** The icon's own tint while [filled] — must be picked for contrast against [fillColor] specifically, not [MapIconBar]'s own bar-level contentColor. */
    fillContentColor: Color = Color.White,
    /**
     * `false` dims and disables the tap target — the return-to-vehicle row's state while nothing
     * is recording, when there is nothing yet to return to.
     */
    enabled: Boolean = true,
    /** Tints just the icon (not a background) for a toggle that is currently "on" but not [filled] — the return-to-vehicle row. */
    activeColor: Color? = null,
) {
    Box(
        modifier = modifier
            .size(MIN_TOUCH_TARGET)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            Box(
                modifier = Modifier
                    .size(MAP_ICON_BAR_FILL_DIAMETER)
                    .background(color = fillColor, shape = CircleShape),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // filled rows sit on their own saturated circle (green/error), so their icon needs
            // fillColor's own contrast pair, not MapIconBar's bar-level contentColor; everything
            // else inherits that bar-level color via LocalContentColor, unless a state override
            // (activeColor) says otherwise.
            tint = if (filled) fillContentColor else activeColor ?: LocalContentColor.current,
        )
    }
}

/**
 * A single freestanding circular icon button — MEDIUM/EXPANDED's own add-trip/log-find trigger is
 * the one remaining user of the compact bar's old per-icon-circle look, now that [MapIconBar]'s
 * rows share one background instead. Kept as its own small composable rather than folded into
 * [MapBarIconButton]: a lone button floating directly over the map still needs its own opaque
 * fill plus hairline border to read against the map, the way [MapIconStackButtonColorDark]'s own
 * doc comment documents — a bar row can lean on the shared bar background for that instead.
 *
 * `filled`'s own accent is [mapIconBarAddAccent], the same theme-swapped [MapIconBarAccent]
 * [MapIconBar]'s add row uses and for the same reason (see that type's own doc comment) — this is
 * the same button on a window class with no bar for it to sit inside, so it keeps the same accent
 * rather than falling back to `MaterialTheme.colorScheme.primary`.
 */
@Composable
internal fun MapFloatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val addAccent = mapIconBarAddAccent(isDarkTheme)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            filled -> addAccent.fill
            isDarkTheme -> MapIconStackButtonColorDark
            else -> MapIconStackButtonColorLight
        },
        contentColor = when {
            filled -> addAccent.onFill
            isDarkTheme -> Color.White
            else -> Bark
        },
        shadowElevation = 2.dp,
        border = if (filled) {
            null
        } else {
            BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT)
        },
        modifier = modifier.size(MIN_TOUCH_TARGET),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}
