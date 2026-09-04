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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.ui.theme.Spacing

/**
 * True when any of [entry]'s characteristic fields are still [Observed.NotObserved]/
 * [Feature.NotObserved] — the tile's "Incomplete" cue.
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
 * Records' Finds submenu — **one implementation, responsive layout**, restoring the same "one
 * composable, [columns] varies" shape [CartographyScreen] already uses, per the Stage 2b follow-up
 * dispatch's own point 1 ("restore the unify"). Replaces both of this codebase's former,
 * independently-diverged screens for browsing [MushroomLogEntry] finds: [JournalTab] (compact) used
 * to host a grid (the former `LogGalleryScreen`) and [LogPanel] (expanded) a plain scrolling list
 * (the former `LogEntryListScreen`) — two genuinely different shapes for the same data, an amendment
 * to the original Stage 2b dispatch had deliberately narrowed apart before this follow-up restored
 * the original "one implementation" decision. The grid shape wins, per the dispatch's explicit
 * "grid-based" — [columns] is the only thing that varies between hosts (2 compact / 3 expanded, the
 * same split [CartographyScreen] already uses), never a different arrangement.
 *
 * **No Album tab here** — Stage 2b follow-up dispatch, point 3. The former `LogGalleryScreen`
 * embedded [PhotoGalleryScreen] as a third tab (Log/Drafts/Album); that was a second, independent
 * path to the same [com.forager.app.domain.model.GalleryPhoto] data [CartographyScreen]'s own Album
 * submenu already shows, flagged as deliberate-but-unwanted duplication in the original Stage 2b
 * dispatch's closing disclosure. [CartographyScreen]'s Album is now the sole path from both window
 * classes' Records/Finds side; the drawer-hosted `DrawerPanel.PhotoGallery` destination
 * ([AvailabilityScreen]'s own, medium/expanded-only) is untouched — that duplication predates Stage
 * 2b and is out of this dispatch's scope. [LogEntryListScreen] never embedded an Album tab to begin
 * with, so nothing is newly unreachable for the expanded window either.
 */
@Composable
internal fun FindsGalleryScreen(
    entries: List<MushroomLogEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Every current draft — live edit sessions, incidentally-exited ones, and crash-orphaned ones alike (see [MushroomLogUiState.draftEntries]'s own doc comment) — shown only when the Drafts tab is selected. */
    draftEntries: List<MushroomLogEntry> = emptyList(),
    onOpenDraftEntry: (String) -> Unit = onOpenEntry,
    /**
     * Set when the last load failed — never hides [entries] that are already showing, only shown
     * above the grid (the "+" tile, if present, stays first regardless) when there is nothing to
     * show because the read failed, per docs/error-presentation-spec.md.
     */
    loadErrorMessage: String? = null,
    /**
     * `null` (the default) omits the "+" tile entirely — [LogPanel] passes no lambda here, matching
     * [LogEntryListScreen]'s own former shape: the expanded window starts a new find via the map's
     * "Log a find" flow, not a tile inside this list. [JournalTab] passes one, matching the former
     * [LogGalleryScreen]'s always-present tile.
     */
    onAddEntry: (() -> Unit)? = null,
    /** Grid column count — 2 for compact, more for expanded/tablet; see this composable's own doc comment. */
    columns: Int = 2,
) {
    var selectedTab by remember { mutableStateOf(FindsGalleryTab.LOG) }

    if (isLoading && entries.isEmpty() && draftEntries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(selected = selectedTab == FindsGalleryTab.LOG, onClick = { selectedTab = FindsGalleryTab.LOG }, text = { Text("Log") })
            Tab(
                selected = selectedTab == FindsGalleryTab.DRAFTS,
                onClick = { selectedTab = FindsGalleryTab.DRAFTS },
                text = { Text(if (draftEntries.isEmpty()) "Drafts" else "Drafts (${draftEntries.size})") },
            )
        }

        val visibleEntries = if (selectedTab == FindsGalleryTab.DRAFTS) draftEntries else entries
        if (visibleEntries.isEmpty() && loadErrorMessage != null) {
            Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The "+" tile only makes sense against the committed log — a new entry starts as a
            // draft either way (see MushroomLogViewModel.onStartNewEntry), but tapping "+" while
            // looking at Drafts would read as "add a draft," which isn't a distinct action from
            // "add an entry."
            if (selectedTab == FindsGalleryTab.LOG && onAddEntry != null) item { AddEntryTile(onClick = onAddEntry) }
            if (selectedTab == FindsGalleryTab.DRAFTS) {
                items(visibleEntries, key = { it.id }) { entry -> FindTile(entry = entry, onClick = { onOpenDraftEntry(entry.id) }, isDraft = true) }
            } else {
                items(visibleEntries, key = { it.id }) { entry -> FindTile(entry = entry, onClick = { onOpenEntry(entry.id) }) }
            }
        }
    }
}

/** Which of [FindsGalleryScreen]'s two tabs is selected — ordinal order matches display order. */
private enum class FindsGalleryTab { LOG, DRAFTS }

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
 * icon. [isDraft] renders a "Draft" badge instead of (never alongside) the "Incomplete" one — a
 * draft is always incomplete by [hasUnrecordedFields]'s own definition too, so showing both would
 * be redundant, not additive.
 */
@Composable
private fun FindTile(entry: MushroomLogEntry, onClick: () -> Unit, modifier: Modifier = Modifier, isDraft: Boolean = false) {
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
