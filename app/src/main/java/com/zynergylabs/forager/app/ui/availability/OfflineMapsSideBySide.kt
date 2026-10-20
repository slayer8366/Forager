package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityOfflineMapsUi.kt (dispatch 2026-10-19-19, UI split Phase 1, PR 10c), apart from private to internal on the declarations the panel calls.

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.adaptive.isLargeScreenWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.log.LANDSCAPE_ENTRY_INFO_MIN_WIDTH
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * L1 (dispatch `prompts/preserved/2026-09-28-47.md`, continuation `2026-09-28-49`; the device
 * evidence is `docs/audits/2026-09-28-backlog-device-check-part-b-run-record.md`, item 7): the
 * Offline Maps picker in a short landscape window, where the stacked panel's 4:3 map (480 dp at the
 * 640 dp column) is taller than the window and, once scrolled, leaves nothing but the map to drag.
 *
 * - **Side by side** (owner, "Side by side (Recommended)"): the map on one side at full height, the
 *   controls on the other, half the width each.
 * - **Pinned actions** (owner, "Pin OK/Download, rest scrolls (Recommended)", which supersedes "all
 *   reachable without scrolling": that cannot hold at 384 dp): OK with its Cancel, and the "Pin at"
 *   line that sits over them in the stacked layout, and Download are pinned at the bottom of the
 *   controls side, always visible. Everything else scrolls above them, in the stacked order.
 *   Since RECORD -1253 (the owner: "C: OK, Cancel and Download on one line"), OK, Cancel and Download share one line under
 *   "Pin at", so the radius slider is in view on open under the Name box (RECORD -1243). Was OK and Cancel on one line and
 *   Download on its own below them.
 *   Since dispatch 2026-10-18-03 (Forager RECORD -1269), no OK and no Cancel: "Pin at", then Download Maps across the side.
 * - **Which side** (the planner's ruling, 2026-09-28-49): the map on the punch-hole side and the
 *   controls on the rail side, as the landscape search sheet (P8), the navigation HUD (P9) and the
 *   tools drawer (P12) sit by the rail with the map beyond them
 *   (`docs/plans/landscape-phone-design.md`). [controlsFirst] is true when the rail's port edge is
 *   the window's left (ROTATION_270), so the sides follow the rail on a turn between 90 and 270.
 *
 * A drag on the map pans the map: it is its own region of the row, not inside the controls' scroll.
 * The window test is the one B1-B3 and J5 use (`isLandscapeWindow`).
 */
@Composable
internal fun OfflineMapsSideBySide(
    modifier: Modifier,
    controlsFirst: Boolean,
    map: @Composable (Modifier) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    /** RECORD -1245: the continental-US note, the last line of the scrolling part. */
    usContinentalNote: @Composable () -> Unit,
    regionsSection: @Composable () -> Unit,
    /** Dispatch 2026-10-18-03: "Pin at", then Download Maps, pinned under the scrolling part. */
    pinnedActions: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxSize()) {
        val controls: @Composable RowScope.() -> Unit = {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag(OFFLINE_PICKER_CONTROLS_SCROLL_TAG),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        content = details,
                    )
                    regionsSection()
                    usContinentalNote()
                }
                pinnedActions()
            }
        }
        if (controlsFirst) controls()
        // RECORD -1330 to -1333 (the owner: "No, the maps do not move at all"): on the Journal the map stands below the room the
        // search pill has not taken, plus a small gap, so it keeps its place while the switch line slides down with the pill.
        // Dispatch 2026-10-18-10 (RECORD -1337, -1340): on a large screen the map is a square whose side is its height after the
        // search gap, which holds through the reveal (the gap and the room shrink together), so the square keeps its place and its
        // size too. Capped so the controls keep OFFLINE_CONTROLS_MIN_WIDTH; the controls take the rest. A phone keeps half the width.
        val searchRoom = LocalJournalSearchPillRoom.current
        val mapModifier = if (isLargeScreenWindow()) {
            Modifier.fillMaxHeight().journalSearchGap(searchRoom).squareFromHeight(OFFLINE_CONTROLS_MIN_WIDTH)
        } else {
            Modifier.weight(1f).fillMaxHeight().journalSearchGap(searchRoom)
        }
        map(mapModifier.testTag(OFFLINE_PICKER_MAP_TAG))
        if (!controlsFirst) controls()
    }
}

/**
 * Dispatch 2026-10-18-10: a map's square, side by side with something else in a Row, as a non-weighted child. The side is the
 * height it is given, at most the width less [otherSideMin], so the weighted part beside it keeps at least that. Placed at the top
 * of its slot when the width decides, so it does not move down when it is smaller than the slot's height.
 */
private fun Modifier.squareFromHeight(otherSideMin: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight && !constraints.hasBoundedWidth) {
        val unbounded = measurable.measure(constraints)
        return@layout layout(unbounded.width, unbounded.height) { unbounded.place(0, 0) }
    }
    val byWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - otherSideMin.roundToPx()).coerceAtLeast(0) else Int.MAX_VALUE
    val byHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
    val side = minOf(byWidth, byHeight)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(constraints.constrainWidth(side), constraints.constrainHeight(side)) { placeable.place(0, 0) }
}

/**
 * Dispatch 2026-10-18-10 (RECORD -1340): beside the square map on a large screen the controls keep at least this width. The owner's
 * 240 dp for the info beside the entry page's map (RECORD -1260), reused so both of the Journal's maps leave the same room.
 */
internal val OFFLINE_CONTROLS_MIN_WIDTH: Dp = LANDSCAPE_ENTRY_INFO_MIN_WIDTH

/** The offline-maps picker's map, in either layout, for tests (dispatch 2026-10-18-10). */
internal const val OFFLINE_PICKER_MAP_TAG = "offline-picker-map"

/** [OfflineMapsSideBySide]'s scrolling part, above the pinned actions: what a test drags to reach the slider. */
internal const val OFFLINE_PICKER_CONTROLS_SCROLL_TAG = "offline-picker-controls-scroll"

/**
 * What [OfflineMapsPanel]'s picker shows for its own last download attempt — every branch says
 * something, per CLAUDE.md, except [OfflineMapStatus.Idle]/[OfflineMapStatus.Succeeded], which
 * deliberately render nothing: a completed download is already reflected in
 * [OfflineRegionsSection]'s list right below, so there is nothing left for this transient status to
 * say once it succeeds.
 */
@Composable
internal fun OfflineDownloadStatusContent(status: OfflineMapStatus) {
    when (status) {
        OfflineMapStatus.Idle, OfflineMapStatus.Succeeded -> Unit

        is OfflineMapStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (status.total > 0) {
                LinearProgressIndicator(
                    progress = { status.downloaded.toFloat() / status.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${status.downloaded} / ${status.total} tiles", style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Starting download…", style = MaterialTheme.typography.bodySmall)
            }
        }

        is OfflineMapStatus.Failed -> Text(
            status.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
