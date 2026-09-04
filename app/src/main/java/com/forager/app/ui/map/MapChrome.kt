package com.forager.app.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * medium/expanded, and [MapIconBar]'s layers row (row 4 of 8) on compact — so the anchor is a
 * parameter instead of a second constant hardcoded in here. [MAP_MODE_PICKER_COMPACT_ANCHOR_OFFSET]
 * computes the compact case the same way [ADD_TILE_ANCHOR_OFFSET] already computes the add row's;
 * the medium/expanded default below matches [MapModeToggle]'s own fixed position.
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
internal val MapIconStackButtonColorDark = Bark.copy(alpha = 0.8f)

/** [MapIconStackButtonColorDark]'s light-theme counterpart — see that color's own doc comment. */
internal val MapIconStackButtonColorLight = Cream.copy(alpha = 0.8f)

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
 * homes of their own). [mapIconBarRowAnchorOffset]'s `rowCount` and [ADD_TILE_ANCHOR_OFFSET]'s row
 * index are updated to match.
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
     * Night mode as it currently resolves — Settings' "Night Maps" checkbox
     * ([AvailabilityUiState.nightModeMaps]), shown here in slot 4's content description so the
     * state is readable rather than merely visible. No longer toggleable from this bar directly
     * (a long-press here used to hold it; that control moved to Settings — see
     * [com.forager.app.domain.MapPreferencesRepository.getNightModeMaps]'s own doc comment).
     */
    isNightMode: Boolean = false,
) {
    // Independent of the map's own night mode -- see MapIconStackButtonColorDark's own doc
    // comment for why the two axes are kept separate rather than one steering the other.
    val isDarkTheme = LocalForagerDarkTheme.current
    Surface(
        shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
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
            MapBarIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Plan a trip or log a find here",
                onClick = onAdd,
                filled = true,
                fillColor = mapIconBarAddAccent(isDarkTheme).fill,
                fillContentColor = mapIconBarAddAccent(isDarkTheme).onFill,
            )
        }
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
