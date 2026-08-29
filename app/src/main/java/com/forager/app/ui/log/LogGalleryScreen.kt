package com.forager.app.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.ui.theme.Spacing

/**
 * True when any of [entry]'s characteristic fields are still [Observed.NotObserved]/
 * [Feature.NotObserved] — mirrors [LogEntryListScreen]'s private copy of the same check
 * ([MushroomLogEntry.hasUnrecordedFields] there is `private` to that file, so this is a second
 * copy rather than a shared one; both read the same seven sections and would need to change
 * together if a new one is ever added).
 */
private fun MushroomLogEntry.hasUnrecordedFields(): Boolean =
    cap.shape is Observed.NotObserved ||
        cap.surface is Observed.NotObserved ||
        cap.decorations is Feature.NotObserved ||
        cap.margin is Observed.NotObserved ||
        hymenophore.details is Observed.NotObserved ||
        stipe.details is Observed.NotObserved ||
        veil.annulus is Feature.NotObserved ||
        veil.volva is Feature.NotObserved ||
        contextFlesh.texture is Observed.NotObserved ||
        contextFlesh.colorChangeOnCutting is Feature.NotObserved ||
        contextFlesh.exudate is Feature.NotObserved ||
        sporePrint.details is Observed.NotObserved ||
        hostSubstrate.association is Observed.NotObserved ||
        hostSubstrate.forestType is Observed.NotObserved ||
        hostSubstrate.hostHealth is Observed.NotObserved

/**
 * The Journal bottom-nav destination's gallery: a grid of every logged entry plus one more tile —
 * always first, so it's never scrolled past — that starts a new one. Replaces
 * [LogEntryListScreen]'s plain list for the compact bottom nav (see [JournalTab]'s doc comment for
 * why that composable, and the drawer-hosted [LogPanel] it belongs to, stay untouched for the
 * medium/expanded window instead of being reused here).
 *
 * **Log / Drafts toggle (Workstream L4b-R, owner decision 2026-08-25):** a draft never appears
 * alongside committed entries — "unsaved work is held in a Drafts section." Implemented here as a
 * filter/toggle on this same screen rather than a separate destination, per the owner's own choice:
 * [selectedTab] selects which of [entries]/[draftEntries] the grid below actually shows, never both
 * at once.
 *
 * **Album, folded in as a third tab (map/navigation redesign dispatch B):** [CompactTab.PHOTOS] is
 * gone — the bottom nav has five destinations now (List, Seasonal, Maps, Journal, Tools), not six,
 * and the photo gallery was the one that had to give. It moves here rather than under Tools because
 * it is entry-adjacent, not a utility: [PhotoGalleryScreen] embeds unchanged as this tab's content,
 * receiving [photos]/[isLoadingPhotos]/[onDeletePhoto]/[photosLoadErrorMessage] the same way Log and
 * Drafts receive [entries]/[draftEntries] — [selectedTab] just picks which of the three the grid
 * below shows.
 */
@Composable
internal fun LogGalleryScreen(
    entries: List<MushroomLogEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Every current draft — live edit sessions, incidentally-exited ones, and crash-orphaned ones alike (see [MushroomLogUiState.draftEntries]'s own doc comment) — shown only when the Drafts tab is selected. */
    draftEntries: List<MushroomLogEntry> = emptyList(),
    onOpenDraftEntry: (String) -> Unit = onOpenEntry,
    /**
     * Set when the last load failed — see [LogEntryListScreen]'s own [loadErrorMessage] parameter
     * for why this never hides [entries] that are already showing, only shown above the grid (the
     * "+" tile stays first regardless, same as the empty-but-no-error case) when there is nothing
     * to show because the read failed, per docs/error-presentation-spec.md.
     */
    loadErrorMessage: String? = null,
    /** Every photo in the gallery, independent of any entry — see [PhotoGalleryScreen]'s own doc comment. Shown only when the Album tab is selected. */
    photos: List<GalleryPhoto> = emptyList(),
    isLoadingPhotos: Boolean = false,
    onDeletePhoto: (GalleryPhoto) -> Unit = {},
    photosLoadErrorMessage: String? = null,
) {
    var selectedTab by remember { mutableStateOf(LogGalleryTab.LOG) }

    if (isLoading && entries.isEmpty() && draftEntries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(selected = selectedTab == LogGalleryTab.LOG, onClick = { selectedTab = LogGalleryTab.LOG }, text = { Text("Log") })
            Tab(
                selected = selectedTab == LogGalleryTab.DRAFTS,
                onClick = { selectedTab = LogGalleryTab.DRAFTS },
                text = { Text(if (draftEntries.isEmpty()) "Drafts" else "Drafts (${draftEntries.size})") },
            )
            Tab(selected = selectedTab == LogGalleryTab.ALBUM, onClick = { selectedTab = LogGalleryTab.ALBUM }, text = { Text("Album") })
        }

        if (selectedTab == LogGalleryTab.ALBUM) {
            PhotoGalleryScreen(
                photos = photos,
                isLoading = isLoadingPhotos,
                onDeletePhoto = onDeletePhoto,
                modifier = Modifier.weight(1f),
                loadErrorMessage = photosLoadErrorMessage,
            )
            return@Column
        }

        val visibleEntries = if (selectedTab == LogGalleryTab.DRAFTS) draftEntries else entries
        if (visibleEntries.isEmpty() && loadErrorMessage != null) {
            Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The "+" tile only makes sense against the committed log — a new entry starts as a
            // draft either way (see MushroomLogViewModel.onStartNewEntry), but tapping "+" while
            // looking at Drafts would read as "add a draft," which isn't a distinct action from
            // "add an entry."
            if (selectedTab == LogGalleryTab.LOG) item { AddEntryTile(onClick = onAddEntry) }
            if (selectedTab == LogGalleryTab.DRAFTS) {
                items(visibleEntries, key = { it.id }) { entry -> LogEntryTile(entry = entry, onClick = { onOpenDraftEntry(entry.id) }, isDraft = true) }
            } else {
                items(visibleEntries, key = { it.id }) { entry -> LogEntryTile(entry = entry, onClick = { onOpenEntry(entry.id) }) }
            }
        }
    }
}

/** Which of [LogGalleryScreen]'s three tabs is selected — ordinal order matches display order (Log, Drafts, Album), read directly by [SecondaryTabRow]'s `selectedTabIndex`. */
private enum class LogGalleryTab { LOG, DRAFTS, ALBUM }

/**
 * The gallery's "start a new entry" tile — a blank journal-entry outline with a centered `+`, per
 * the project owner's own description of it, so it reads as "add" the same way an empty photo slot
 * does in a picker grid, rather than as one more entry among the real ones.
 */
@Composable
private fun AddEntryTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GALLERY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(Spacing.sm),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New log entry",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(ADD_TILE_ICON_SIZE_DP.dp),
            )
        }
    }
}

/**
 * One logged find in the gallery grid — a cover photo when one exists, otherwise a placeholder
 * icon. [isDraft] renders a "Draft" badge instead of (never alongside) the "Incomplete" one —
 * Workstream L4b-R's Drafts filter; a draft is always incomplete by [hasUnrecordedFields]'s own
 * definition too, so showing both would be redundant, not additive.
 */
@Composable
private fun LogEntryTile(entry: MushroomLogEntry, onClick: () -> Unit, modifier: Modifier = Modifier, isDraft: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GALLERY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(Spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // weight(1f), not aspectRatio(1f): a fixed square ate a disproportionate share of the
            // card's own fixed-aspect-ratio height, squeezing the caption below it -- on a draft
            // tile specifically, two lines ("Find on <date>" plus the "Draft" badge) rather than
            // one, so it clipped there first (Card clips its content to its own shape). weight(1f)
            // measures the caption Column at its real content height first and gives the image
            // whatever's left, so the caption is never squeezed regardless of tile width, font
            // scale, or how many lines it needs -- the image just stops being a perfect square.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val coverPhoto = entry.photos.firstOrNull()
                if (coverPhoto != null) {
                    DecodedPhoto(relativePath = coverPhoto.relativePath, modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ADD_TILE_ICON_SIZE_DP.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text(
                    "Find on ${entry.foundOn}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isDraft) {
                    Text(
                        "Draft",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (entry.hasUnrecordedFields()) {
                    Text(
                        "Incomplete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A little taller than wide, reading as a card/page rather than a square photo tile. */
private const val GALLERY_TILE_ASPECT_RATIO = 0.85f
private const val ADD_TILE_ICON_SIZE_DP = 40
