package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.BouncingFilledIconToggleButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The toolbar row at the top of Entries — journal redesign J2, T3 (plan J3): the two-button view
 * toggle, timeline (☰) and album (▦), right-aligned as in the plan's mockup. Each button is a
 * toggle whose "on" state is the current view; touching the one already on does nothing, so the
 * pair always has exactly one on (a single choice, not two independent switches).
 */
@Composable
internal fun EntriesToolbar(
    viewMode: EntriesViewMode,
    onViewModeChange: (EntriesViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BouncingFilledIconToggleButton(
            checked = viewMode == EntriesViewMode.TIMELINE,
            onCheckedChange = { if (it) onViewModeChange(EntriesViewMode.TIMELINE) },
            modifier = Modifier.testTag(ENTRIES_VIEW_TIMELINE_TAG),
        ) {
            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Timeline view")
        }
        BouncingFilledIconToggleButton(
            checked = viewMode == EntriesViewMode.ALBUM,
            onCheckedChange = { if (it) onViewModeChange(EntriesViewMode.ALBUM) },
            modifier = Modifier.testTag(ENTRIES_VIEW_ALBUM_TAG),
        ) {
            Icon(Icons.Filled.GridView, contentDescription = "Album view")
        }
    }
}

internal const val ENTRIES_VIEW_TIMELINE_TAG = "entries-view-timeline"
internal const val ENTRIES_VIEW_ALBUM_TAG = "entries-view-album"
