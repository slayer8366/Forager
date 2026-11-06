package com.zynergylabs.forager.app.ui.log

// Moved from CartographyEntryReportScreen.kt unchanged (UI split Phase 1, PR 10e, dispatch 2026-10-19-22).

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/** Lets tests distinguish "the map section rendered" from "nothing resolved, no map section at all" without depending on [mapSlot]'s own real content — see this file's own doc comment, "No map section at all while loading, or if nothing resolved." */
/**
 * L2 (dispatch `prompts/preserved/2026-09-28-47.md`; the device evidence is
 * `docs/audits/2026-09-28-backlog-device-check-part-b-run-record.md`, item 7). **The rule:** in a
 * short window (under 480 dp tall, `isShortWindow`, whatever the orientation) the entry map's
 * preview is 4:3 by its width, as everywhere else, but never taller than this fraction of the
 * window's height: 40%, which is **153.6 dp** in the S22 Ultra's 384 dp landscape window.
 *
 * Why a cap was needed at all: in the 640 dp landscape column a 4:3 map is 480 dp tall, taller than
 * the whole window. `aspectRatio` measures it at 480 dp regardless, and Compose centres an oversized
 * child on its slot, so the map was drawn about 104 dp above its own slot, over the header row, and
 * the offline row and the text below got no height at all.
 *
 * Why 40%: what else must fit beside it in that window, measured headless at `w823dp-h384dp-land`:
 * the Journal's 48 dp short-window row and the report's 64 dp header row take 112 dp of the 384, so
 * a 154 dp map leaves about 118 dp for the offline-map row (about 64 dp on a device) and the start
 * of the scrolling text. Half the window (192 dp) would leave the text about 16 dp once the offline
 * row shows. Rejected: capping the width too, to keep 4:3 (205 x 154 dp), which wastes the column
 * for no gain, since a tap still opens the fullscreen map.
 */
internal const val SHORT_WINDOW_MAP_HEIGHT_FRACTION = 0.4f

/**
 * L2's map box: the full width it is given, and a height of 3/4 of that width, but no more than
 * [cap] and never more than the incoming constraints allow, so the box can never be measured taller
 * than its slot (the overflow `aspectRatio` produced). A layout modifier rather than `heightIn` on
 * `aspectRatio`, because `aspectRatio` falls back to its unconstrained size when nothing satisfies
 * the constraints, which is the overflow itself.
 */
internal fun Modifier.shortWindowEntryMapHeight(cap: Dp): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = minOf(width * 3 / 4, cap.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

internal const val CARTOGRAPHY_MAP_TEST_TAG = "cartography-entry-map"

/** The day entry report's own bounds, and its scrolling info column, for tests (dispatch 2026-10-18-01). */
internal const val ENTRY_REPORT_TAG = "cartography-entry-report"
internal const val ENTRY_REPORT_INFO_TAG = "cartography-entry-report-info"

/**
 * Dispatch 2026-10-18-01, the owner's answer to the planner's Q1 (RECORD -1260): "Largest that leaves info ≥240 dp
 * (Recommended)". Beside the landscape map the info column keeps at least this width. Every tab's content beside the rail is
 * capped at 640 dp ([com.zynergylabs.forager.app.ui.availability.READABLE_CONTENT_MAX_WIDTH]), so "4:3, as large as fits the
 * height" cannot always hold: on a landscape tablet a map as tall as the window would be wider than the whole column. Rejected
 * alternatives the owner was shown: the map at most half the width (320 x 240 everywhere), and lifting the 640 dp cap for the
 * entry, which reaches into the Journal tab's layout.
 *
 * On a large screen since dispatch 2026-10-18-10 (RECORD -1340) the Journal's column is 960 dp and the map is a square, so the
 * square is the largest that fits the height and leaves this width; the owner's "info next to it".
 */
internal val LANDSCAPE_ENTRY_INFO_MIN_WIDTH = 240.dp

/**
 * The day entry's three parts, placed. Not a Row or a Column chosen by orientation: a measure policy that only places, so
 * [header], [map] and [body] are the same composables in either arrangement and the map's one mapSlot call keeps its place in
 * the composition when the phone turns (MainActivity handles rotation itself, so a rebuilt map would be a real MapView torn
 * down; [CartographyEntryReportScreen]'s doc comment, "Fullscreen").
 *
 * - Not [split] (portrait, and any window without a map or with the map fullscreen): stacked as before. The header at the top
 *   at its own height; the map under it at its own height, at most what is left (all of it in [fullscreen]); the body takes
 *   the rest exactly, as the `weight(1f)` it replaced did.
 * - [split] (dispatch 2026-10-18-01, a landscape window with a map; a square box with [squareMap], dispatch 2026-10-18-10): the map on the left in a 4:3 box, the largest whose
 *   height fits and which leaves [infoMinWidth] beside it; the header at the top of the right side, pinned; the body under it
 *   taking the rest of the right side's height exactly, so it scrolls on its own.
 */
@Composable
internal fun EntryReportLayout(
    split: Boolean,
    fullscreen: Boolean,
    infoMinWidth: Dp,
    /** Dispatch 2026-10-18-10: a large screen's square box in [split] in place of the 4:3 one (the map's own ratio is the caller's). */
    squareMap: Boolean,
    header: @Composable () -> Unit,
    map: @Composable () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(header, map, body), modifier = modifier) { (headerParts, mapParts, bodyParts), constraints ->
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val loose = Constraints(maxWidth = width, maxHeight = height)
        if (split) {
            val boxWidth = minOf(if (squareMap) height else height * 4 / 3, (width - infoMinWidth.roundToPx()).coerceAtLeast(0))
            val boxHeight = if (squareMap) boxWidth else boxWidth * 3 / 4
            val mapPlaceables = mapParts.map { it.measure(Constraints(maxWidth = boxWidth, maxHeight = boxHeight)) }
            val infoWidth = width - boxWidth
            val headerPlaceables = headerParts.map { it.measure(Constraints(maxWidth = infoWidth, maxHeight = height)) }
            val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
            val bodyPlaceables = bodyParts.map { it.measure(Constraints.fixed(infoWidth, (height - headerHeight).coerceAtLeast(0))) }
            layout(width, height) {
                mapPlaceables.forEach { it.place(0, 0) }
                headerPlaceables.forEach { it.place(boxWidth, 0) }
                bodyPlaceables.forEach { it.place(boxWidth, headerHeight) }
            }
        } else {
            val headerPlaceables = headerParts.map { it.measure(loose) }
            val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
            val mapRoom = (height - headerHeight).coerceAtLeast(0)
            val mapPlaceables = mapParts.map {
                it.measure(if (fullscreen) Constraints.fixed(width, mapRoom) else Constraints(maxWidth = width, maxHeight = mapRoom))
            }
            val mapHeight = mapPlaceables.maxOfOrNull { it.height } ?: 0
            val bodyPlaceables = bodyParts.map { it.measure(Constraints.fixed(width, (mapRoom - mapHeight).coerceAtLeast(0))) }
            layout(width, height) {
                headerPlaceables.forEach { it.place(0, 0) }
                mapPlaceables.forEach { it.place(0, headerHeight) }
                bodyPlaceables.forEach { it.place(0, headerHeight + mapHeight) }
            }
        }
    }
}
