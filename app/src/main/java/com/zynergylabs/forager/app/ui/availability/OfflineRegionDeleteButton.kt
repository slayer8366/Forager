package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * An offline map's row with a visible trash button at its end (dispatch 2026-10-29-10, RECORD -1817, -1818; the owner:
 * "Visible Delete button (Recommended)", whose option read "Each row in Offline maps shows a trash button. The swipe still
 * works and Undo stays."). Until then the only delete was the row's two-stage swipe, which nothing on screen showed.
 *
 * [onDelete] is the very callback the row's swipe calls (the caller passes the same `onDeleteOfflineRegion(region.id)`
 * to both), so the button asks for the same *pending* delete with the same Undo snackbar. No confirm dialog: the swipe
 * has none and Undo covers it.
 *
 * Composed by both screens that list offline maps, inside their [com.zynergylabs.forager.app.ui.log.TwoStageSwipeRow]:
 * the Offline maps panel ([OfflineRegionsSection]) and Records' All logbook. [row] is that screen's own
 * [OfflineRegionRow], unchanged; the button sits beside it rather than inside it, so a tap on the button never reaches
 * the row's own tap (which opens the region's details) and the row keeps every behaviour it had.
 *
 * A Material [IconButton]: 48 dp touch target, no fill of its own (so nothing here changes what the row draws over).
 */
@Composable
internal fun OfflineRegionRowWithDelete(
    region: OfflineRegionSummary,
    onDelete: () -> Unit,
    row: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) { row() }
        IconButton(onClick = onDelete, modifier = Modifier.testTag(offlineRegionDeleteButtonTag(region.id))) {
            Icon(Icons.Filled.Delete, contentDescription = offlineRegionDeleteLabel(region.name))
        }
    }
}

/**
 * The trash button's screen-reader label: "Delete <map name>". Proposed in the PR for the owner's approval before merge
 * (dispatch 2026-10-29-10: "The owner approves the wording before merge").
 */
internal fun offlineRegionDeleteLabel(regionName: String): String = "Delete $regionName"

/** The trash button on region [regionId]'s row, for tests. */
internal fun offlineRegionDeleteButtonTag(regionId: Long): String = "offline-region-delete-$regionId"
