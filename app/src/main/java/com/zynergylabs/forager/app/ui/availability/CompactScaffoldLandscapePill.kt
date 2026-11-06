package com.zynergylabs.forager.app.ui.availability

// Moved from AvailabilityCompactScaffold.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * Whether the Maps tab, live map and all, is kept on screen through its fade when another tab is chosen (motion Part 2, item 1;
 * Amendment 1, RECORD -672, item 8: "keep as built", with the fallback ready for the S22 check). RECORD -651: the phone cost of
 * keeping the live map through the fade "is checked on the S22 before it is kept". `false` is the fallback: the Maps tab goes
 * at once as it used to, while the arriving tab still fades in and the bar still fades with it. Not a setting; one build-time
 * switch for the device check.
 */
internal const val KEEP_MAP_THROUGH_TAB_FADE = true

/** Landscape B2 (S2): the search bar's width cap in a short landscape window. */
internal val LANDSCAPE_SEARCH_MAX_WIDTH = 384.dp

/** Landscape B2 (S2): how far short of the map's centre line the capped search bar ends. */
internal val LANDSCAPE_SEARCH_CENTRE_GAP = 8.dp

/**
 * Dispatch 2026-10-18-03, item 2 (Forager RECORD -1269; Elder's top-up audit, item 2): the landscape pill on List, Seasonal and
 * Journal at the Maps pill's width and place from the first frame. Goes after the area's own padding (the navigation bar on
 * the far side), so the width offered is the room the Maps strip and pill share (the Maps pill's room is the window less the
 * map's controls insets; with the rail beside the content here the two are equal, as TabsMapChromeShapeTest's no-jump checks
 * and TabsFirstVisitPillTest compare whole). The strip's width is [landscapeStripWidthPx] on that room, from the need and bar
 * floor CompactMapTab's [landscapeStripFit] uses, so the pill ends where the strip would begin, on the far side ([farIsLeft]).
 * Worked out from text measurements at layout time, never read back from the Maps tab's layout, as landscapeStripFit is.
 */
internal fun Modifier.landscapeTabPillFit(farIsLeft: Boolean, need: LandscapeStripNeed, barFloorPx: Int): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val room = constraints.maxWidth
        val width = (room - landscapeStripWidthPx(room, need, barFloorPx)).coerceIn(0, room)
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(room, placeable.height) { placeable.place(if (farIsLeft) 0 else room - width, 0) }
    }

/**
 * Dispatch 2026-10-18-03, item 2 (H2, the planner's call): the landscape compass strip's height with its readout line alone,
 * which the Maps pill takes (RECORD -729): the larger of the three-dot button's square ([QUICK_SETTINGS_TAP_TARGET], when there
 * is one) and the readout line as laid out (the coordinates, or the no-fix line), in the strip's readout style. That is what
 * LandscapeStripPortraitHeightTest measures the strip against. The strip's evening lines (sundown while recording, Back by in
 * its last hour) add to its height and are not modelled here: the scaffold prefers the strip's own measurement once Maps has
 * been shown, and the gap before that is listed in docs/process/known-bugs.md.
 */
@Composable
internal fun rememberLandscapeStripLineHeight(location: LatLng?, hasQuickSettings: Boolean): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val readoutStyle = stripReadoutStyle().copy(fontFeatureSettings = "tnum")
    val text = if (location == null) NO_FIX_MESSAGE else coordinatesStripText(location, false)
    val lineHeightPx = measurer.measure(text, readoutStyle, maxLines = 1, softWrap = false).size.height
    return with(density) {
        maxOf(lineHeightPx.toDp(), if (hasQuickSettings) QUICK_SETTINGS_TAP_TARGET else 0.dp)
    }
}
