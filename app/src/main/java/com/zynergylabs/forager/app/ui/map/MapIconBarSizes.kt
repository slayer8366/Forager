package com.zynergylabs.forager.app.ui.map

// Moved from MapChrome.kt unchanged (UI split Phase 1, PR 7, dispatch 2026-10-19-16).

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing

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
 * The landscape L's row spacing and end padding (dispatch 2026-09-28-160; the owner's "A"): none. Five 48 dp rows are 240 dp,
 * and the horizontal pill uses the same zero so its two 48 dp buttons are 96 dp and record sits exactly under the bar's column.
 */
internal val MAP_ICON_BAR_LANDSCAPE_ROW_SPACING = 0.dp

/**
 * Offset from [MapIconBar]'s own vertical center (where both `AddActionTile` and `MapModePicker` (removed 2026-10-19, RECORD -1392)
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
internal fun mapIconBarRowAnchorOffset(rowIndexFromTop: Int, rowSpacing: Dp = Spacing.xs): Dp {
    val rowCount = 5
    val contentHeight = MIN_TOUCH_TARGET * rowCount + rowSpacing * (rowCount - 1)
    val rowCenterFromTop = (MIN_TOUCH_TARGET + rowSpacing) * (rowIndexFromTop - 1) + MIN_TOUCH_TARGET / 2
    return rowCenterFromTop - contentHeight / 2
}

/** The map icon bar's own fill (portrait cluster, landscape L, tablet and the entry map's bar), for tests reading its container colour. */
internal const val MAP_ICON_BAR_TAG = "map-icon-bar"

/** The filled circle behind an active/permanent-accent row (add, record while recording) inside [MapIconBar] — smaller than [MIN_TOUCH_TARGET] so it reads as a badge within the row rather than filling it edge to edge. */
internal val MAP_ICON_BAR_FILL_DIAMETER = 36.dp

/** Half [MIN_TOUCH_TARGET], so [MapIconBar]'s rounded corners read as a stadium/pill shape rather than a slightly-rounded rectangle. */
internal val MAP_ICON_BAR_CORNER_RADIUS = MIN_TOUCH_TARGET / 2
