package com.zynergylabs.forager.app.ui.log

// Moved unchanged from RecordDetailsSheet.kt (dispatch 2026-10-19-07, UI split Phase 1, PR 4).

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.Role

@Composable
internal fun DetailsTitle(title: String, stale: Boolean = false, label: String? = null) {
    // Dispatch 2026-09-28-685, fix 1 (the owner, RECORD -644: "Fix it (Recommended)"): beside a long title the
    // "Imported" label was measured last into what the title left, and drew one letter per line (an imported
    // file's name, "forager-track-2026-09-27-192346", on the S22). Now the title takes only what the label and
    // "Stale" leave (a weight that does not fill), the words beside it never wrap, and a title with a word beside
    // it stays on one line and shortens with "…". A title with nothing beside it wraps as before.
    val besideTitle = label != null || stale
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = if (besideTitle) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag(RECORD_DETAILS_TITLE_TAG),
        )
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(RECORD_DETAILS_LABEL_TAG),
            )
        }
        if (stale) {
            Text(
                "Stale",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(RECORD_DETAILS_STALE_TAG),
            )
        }
    }
}

/** One labelled value. Merged for accessibility, so TalkBack reads "Radius, 9 mi" as one item. */
@Composable
internal fun DetailField(key: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(recordDetailsFieldTag(key)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(FIELD_LABEL_WIDTH),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * "Details", a fold for the raw figures (dispatch 2026-09-28-677; the owner, RECORD -656: "Points and other raw figures go
 * in a "Details" fold"). Closed at first. A tap anywhere on its 48 dp header row opens or closes it, and a screen reader
 * hears it as a button that says which it will do. Whether it is open is [LocalTrackDetailsFold]'s, held by
 * `AvailabilityScreen` above every place this sheet opens, so it survives closing the sheet, opening another walk, a
 * waypoint and Back, and leaving the tab, within a session (CLAUDE.md, UX defaults); with no holder it is this sheet's own.
 */
@Composable
internal fun DetailsFold(content: @Composable () -> Unit) {
    val state = LocalTrackDetailsFold.current ?: remember { mutableStateOf(false) }
    var open by state
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DETAILS_FOLD_MIN_HEIGHT)
            .clickableWithShapedPress(
                role = Role.Button,
                onClickLabel = if (open) DETAILS_FOLD_HIDE_LABEL else DETAILS_FOLD_SHOW_LABEL,
                onClick = { open = !open },
            )
            .testTag(RECORD_DETAILS_FOLD_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(DETAILS_FOLD_TITLE, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }
    if (open) content()
}

/**
 * Whether the track sheet's "Details" fold is open ([DetailsFold]): one flag for every track sheet, provided by
 * `AvailabilityScreen` in `rememberSaveable` state. `null` (no holder, as in a test that composes the sheet alone) keeps the
 * flag in the sheet.
 */
internal val LocalTrackDetailsFold = staticCompositionLocalOf<MutableState<Boolean>?> { null }

@Composable
internal fun DetailsActions(content: @Composable () -> Unit) {
    Row(modifier = Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
}
