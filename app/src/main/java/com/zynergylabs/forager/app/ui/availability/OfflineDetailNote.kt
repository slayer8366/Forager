package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Known bug 14's line in the Offline Maps picker (dispatch 2026-11-02-02), shown while the map server has answered that some
 * of [area] has no detail ([answer]'s `someOutside`) and [answer] is for [area], the area on screen now. The owner's words,
 * exactly (RECORD -1951): "Detailed offline maps aren't available here yet. A download here will be blank when you're
 * offline." Styled as the picker's other notes (A5, the planner's call on the Phase 1 report's S3): `bodySmall`, the default
 * colour, padded [Spacing.lg] by [Spacing.sm], like the continental note. Download Maps is not touched: it works either way.
 */
@Composable
internal fun OfflineDetailNote(answer: OfflineDetailAnswer?, area: Region) {
    if (answer == null || answer.area != area || !answer.someOutside) return
    Text(
        OFFLINE_DETAIL_NOT_HERE_NOTE,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag(OFFLINE_DETAIL_NOTE_TAG),
    )
}

/** The owner's words (RECORD -1951), exactly. */
internal const val OFFLINE_DETAIL_NOT_HERE_NOTE =
    "Detailed offline maps aren't available here yet. A download here will be blank when you're offline."

/** The line, for tests reading its place on screen. */
internal const val OFFLINE_DETAIL_NOTE_TAG = "offline-detail-note"
