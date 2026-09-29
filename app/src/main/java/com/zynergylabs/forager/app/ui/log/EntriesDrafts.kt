package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The Drafts banner at the top of Entries — journal redesign J2, T2 (plan J2; owner ruling "Full-
 * screen list (Recommended)"). It replaces the Drafts sub-tab: an unfinished entry is something to
 * go back to, not a destination of its own. Shown only when [count] > 0; the caller decides that.
 *
 * [onContinue] routes by count, in [CartographyScreen]: one draft opens it straight into the
 * editor (today's open-draft path); more than one opens [DraftsListScreen].
 */
@Composable
internal fun DraftsBanner(count: Int, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .testTag(DRAFTS_BANNER_TAG),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                draftsBannerLabel(count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("·", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onContinue, modifier = Modifier.testTag(DRAFTS_CONTINUE_TAG)) {
                Text("Continue ›")
            }
        }
    }
}

/** "✎ 1 unfinished entry" / "✎ N unfinished entries" (plan J2's wording). */
internal fun draftsBannerLabel(count: Int): String =
    "✎ $count unfinished ${if (count == 1) "entry" else "entries"}"

/**
 * The full-screen drafts list inside the Journal, opened by the banner's Continue when there is
 * more than one draft (J2, T2). Its content is what the Drafts sub-tab showed:
 * [CartographyEntryListScreen] over [CartographyUiState.draftEntries]. The back arrow and system
 * Back (a `BackHandler` in [CartographyScreen]) both return to Entries.
 */
@Composable
internal fun DraftsListScreen(
    drafts: List<CartographyEntry>,
    isLoading: Boolean,
    onOpenDraft: (String) -> Unit,
    onBack: () -> Unit,
    /** The user's distance unit, for a card's track stat (J3, C1: the shared list's cards need it). */
    distanceUnit: DistanceUnit,
    columns: Int,
    /** For a draft card's hero photo (J3, C2), as on the timeline. */
    galleryPhotos: List<GalleryPhoto> = emptyList(),
    /** For a draft card's track thumbnail (J3, C3), as on the timeline. */
    tracks: List<Track> = emptyList(),
    /** F3: a draft card's saved track paths, as on the timeline; see [CartographyEntryListScreen]. */
    getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    /** J4b L2: a draft card's swipe Delete (pending, with Undo); `null` leaves the cards unswipeable. See [CartographyEntryListScreen]. */
    onDeleteDraft: ((String) -> Unit)? = null,
    /** J4b L2: a draft card's swipe Edit. */
    onEditDraft: ((String) -> Unit)? = null,
    /** J5, L4: sideways cards with the long-press menu, in a short window. See [CartographyEntryListScreen]. */
    sideways: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(DRAFTS_LIST_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(DRAFTS_LIST_BACK_TAG)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Entries")
            }
            Text("Unfinished entries", style = MaterialTheme.typography.titleMedium)
        }
        CartographyEntryListScreen(
            entries = drafts,
            isLoading = isLoading,
            onOpenEntry = onOpenDraft,
            emptyMessage = "No drafts. An entry you haven't finished shows up here.",
            distanceUnit = distanceUnit,
            galleryPhotos = galleryPhotos,
            tracks = tracks,
            getSavedTrackPaths = getSavedTrackPaths,
            columns = columns,
            modifier = Modifier.weight(1f),
            onDeleteEntry = onDeleteDraft,
            onEditEntry = onEditDraft,
            sideways = sideways,
        )
    }
}

internal const val DRAFTS_BANNER_TAG = "entries-drafts-banner"
internal const val DRAFTS_CONTINUE_TAG = "entries-drafts-continue"
internal const val DRAFTS_LIST_TAG = "entries-drafts-list"
internal const val DRAFTS_LIST_BACK_TAG = "entries-drafts-list-back"
