package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.ListRowShape
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.StateCrossfade
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.ui.unit.IntOffset
import java.time.YearMonth
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * The Entries/Drafts submenus' shared list — Journal Stage 2b. A grid, the same shape
 * [LogGalleryScreen]'s own gallery uses, not the plain scroll [LogEntryListScreen] uses for finds:
 * Cartography's grid columns are exactly the "more of the same thing at once" responsive knob owner
 * decision #3 calls for (one Cartography implementation, [columns] the only thing that changes
 * between window classes).
 *
 * **No "+" tile since journal redesign J2 (T4, plan J7).** It used to be the first grid cell, the
 * largest thing on screen; starting an entry is now [CartographyScreen]'s floating button, which
 * sits over this grid, so [bottomContentPadding] lets the last row scroll clear of it. [emptyMessage]
 * is what an empty list says (the timeline and the drafts list say different things).
 *
 * **Cards since journal redesign J3 (C1, plan J5):** each month's entries sit under a sticky
 * month header, and each entry is a [CartographyEntryCard] (day numeral and weekday, title, species
 * chips, stats by type) or, with nothing to draw large, a [CollapsedEntryRow]; see that file.
 *
 * A card names its date, its tag chips (if any), and kept-item stats — **never whether it has
 * writing**. Per `amendment-2b-optional-writing.md`: a wordless entry with kept items is complete,
 * not incomplete, so no card here carries an "Incomplete"-style badge (the find tiles' own such badge was removed on 2026-09-13) the way [LogGalleryScreen]'s
 * find tiles do; [MushroomLogEntry] is a different entity with a different completeness question,
 * and none of that framing carries over to entries that never claimed to be structured records.
 */
@Composable
internal fun CartographyEntryListScreen(
    entries: List<CartographyEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    /** Shown when there is nothing to list and no load error to show instead. */
    emptyMessage: String,
    /** The user's distance unit, for a card's track stat (J3, C1). */
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
    loadErrorMessage: String? = null,
    /** Grid column count — 2 for compact, more for expanded/tablet. See this composable's own doc comment on owner decision #3. */
    columns: Int = 2,
    /** Space below the last row, so a floating button over the grid does not cover it at the end of the list (J2, T4). */
    bottomContentPadding: Dp = Spacing.lg,
    /** The gallery photos the screen already holds, which a card's hero is resolved against (J3, C2; see [entryHeroPhoto]). */
    galleryPhotos: List<GalleryPhoto> = emptyList(),
    /** The already-loaded recorded tracks, which a card's thumbnail is looked up in by id (J3, C3; see [entryThumbnailTracks]). */
    tracks: List<Track> = emptyList(),
    /**
     * F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, read for the entries
     * whose kept track is not in [tracks] ([entriesNeedingSavedPaths]) and re-read when [entries] or [tracks]
     * change, so a track deleted on the Records side keeps its card's thumbnail with no refresh from elsewhere.
     * A failed read is the caller's to log; this draws no thumbnail for that entry.
     */
    getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    /**
     * J4b L2 (owner ruling "Lists swipe, grids long-press (Recommended)"): when set, every card is a
     * [TwoStageSwipeRow] whose revealed Delete, full swipe and "Delete" accessibility action call
     * this with the entry's id (a *pending* delete with Undo). `null` (the default, `LogPanel`'s
     * wide tree) leaves the cards as they were.
     */
    onDeleteEntry: ((String) -> Unit)? = null,
    /** J4b L2: the swipe row's Edit, opening the entry in its editor. Only read when [onDeleteEntry] is set. */
    onEditEntry: ((String) -> Unit)? = null,
    /**
     * Journal redesign J5, L4: a short window's sideways cards ([SidewaysEntryCard]: the 72 dp slot on
     * the left, owner's ruling 3) with J4b's long-press menu in place of the swipe (ruling 2), where
     * [onDeleteEntry] is set; its Edit is [onEditEntry]. `false` (portrait, `LogPanel`) is J3's cards
     * and J4b's swipe, unchanged.
     */
    sideways: Boolean = false,
) {
    // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"): a deleted card stays to fade and shrink while the rest
    // close up, and comes back the way it went on Undo; see motion/ListMotion.kt. The empty message waits on these rows, so
    // the last card of a list still leaves before the empty message shows.
    val rows = rememberListRows(entries, key = { it.id })
    val glide = MotionTokens.listRowSpec<IntOffset>()
    val months = remember(rows) { groupRowsByMonth(rows) }
    val photosById = remember(galleryPhotos) { galleryPhotos.associateBy { it.photo.id } }
    val tracksById = remember(tracks) { tracks.associateBy { it.id } }
    val savedPathsByEntry by produceState(initialValue = emptyMap<String, Map<String, List<LatLng>>>(), entries, tracksById) {
        value = entriesNeedingSavedPaths(entries, tracksById).associateWith { getSavedTrackPaths(it) }
    }
    // J4b L2: one open card at a time; a touch elsewhere on the list or a scroll closes it.
    val swipeGroup = rememberSwipeRevealGroup()
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, swipeGroup) {
        snapshotFlow { gridState.isScrollInProgress }.collect { scrolling -> if (scrolling) swipeGroup.closeAll() }
    }
    // Motion Part 3, Amendment 2 (RECORD -682; scout J10): the spinner, the empty message and the list crossfade into each other
    // instead of swapping in one frame (motion/StateCrossfade.kt).
    val listState = when {
        isLoading && rows.isEmpty() -> EntryListState.LOADING
        rows.isEmpty() && loadErrorMessage == null -> EntryListState.EMPTY
        else -> EntryListState.LIST
    }
    StateCrossfade(targetState = listState, modifier = modifier.fillMaxSize()) { shownState ->
    when (shownState) {
    EntryListState.LOADING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
    EntryListState.EMPTY -> Text(
        emptyMessage,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
    )
    EntryListState.LIST ->
    Column(modifier = Modifier.fillMaxSize()) {
        if (entries.isEmpty() && loadErrorMessage != null) {
            Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.weight(1f).swipeRevealTouchWatcher(swipeGroup).testTag(ENTRIES_GRID_TAG),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = bottomContentPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            months.forEachIndexed { run, (month, monthEntries) ->
                // J3, C1 (plan J5): a sticky header per month. The run index keeps keys unique where
                // a month recurs (the drafts list's order is by last update, not date).
                stickyHeader(key = "month-$month-$run", contentType = "month") { EntryMonthHeader(month) }
                items(monthEntries, key = { it.key }) { row ->
                  // Placement glide for a card that moves (another added or gone above it); the fade and the grow are the row's own.
                  ListRowMotion(
                    row = row,
                    modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = glide, fadeOutSpec = null),
                    shape = if (columns == 1) ListRowShape.ROW else ListRowShape.TILE,
                  ) { entry ->
                    val open = { onOpenEntry(entry.id) }
                    val hero = entryHeroPhoto(entry, photosById)
                    if (sideways) {
                        SidewaysEntryItem(
                            entry = entry,
                            hero = hero,
                            tracksById = tracksById,
                            savedPaths = savedPathsByEntry[entry.id].orEmpty(),
                            distanceUnit = distanceUnit,
                            onOpen = open,
                            onDelete = onDeleteEntry?.let { delete -> { delete(entry.id) } },
                            onEdit = onEditEntry?.let { edit -> { edit(entry.id) } },
                        )
                    } else {
                        val card: @Composable () -> Unit = {
                            if (isCollapsedEntry(entry, hasHero = hero != null)) {
                                CollapsedEntryRow(entry = entry, distanceUnit = distanceUnit, onClick = open)
                            } else {
                                CartographyEntryCard(
                                    entry = entry,
                                    distanceUnit = distanceUnit,
                                    onClick = open,
                                    hero = hero?.let { photo -> { EntryHeroPhoto(entry.id, photo) } },
                                    thumbnail = entryThumbnailTracksOrSaved(entry, tracksById, savedPathsByEntry[entry.id].orEmpty()).takeIf { it.isNotEmpty() }?.let { found -> { EntryTrackThumbnail(entry.id, found) } },
                                )
                            }
                        }
                        if (onDeleteEntry != null) {
                            TwoStageSwipeRow(
                                testTag = entrySwipeTag(entry.id),
                                rowKey = entry.id,
                                group = swipeGroup,
                                onDelete = { onDeleteEntry(entry.id) },
                                onEdit = onEditEntry?.let { edit -> { edit(entry.id) } },
                                // The cards' own shape (CartographyEntryCard, CollapsedEntryRow), for the close tap's highlight.
                                highlightShape = RoundedCornerShape(Spacing.sm),
                                content = card,
                            )
                        } else {
                            card()
                        }
                    }
                  }
                }
            }
        }
    }
    }
    }
}

/** What [CartographyEntryListScreen] shows (motion Part 3, Amendment 2): one value, so the three can crossfade. */
private enum class EntryListState { LOADING, EMPTY, LIST }

/**
 * [groupEntriesByMonth] for the rows the list draws (motion Part 3), so a leaving card keeps its month, and its month header stays
 * until it has gone.
 */
private fun groupRowsByMonth(
    rows: List<com.zynergylabs.forager.app.ui.motion.ListRow<CartographyEntry>>,
): List<Pair<YearMonth, List<com.zynergylabs.forager.app.ui.motion.ListRow<CartographyEntry>>>> {
    val runs = mutableListOf<Pair<YearMonth, MutableList<com.zynergylabs.forager.app.ui.motion.ListRow<CartographyEntry>>>>()
    for (row in rows) {
        val month = YearMonth.from(row.item.date)
        val last = runs.lastOrNull()
        if (last != null && last.first == month) last.second += row else runs += month to mutableListOf(row)
    }
    return runs
}

/**
 * One sideways entry in a short window (J5, L4): [SidewaysEntryCard] with its slot chosen by
 * [entrySlotContent] (ruling 3), or J3's collapsed row, either one inside J4b's
 * [LongPressOptionsBox] when [onDelete] is set (ruling 2: "Long-press, like grids").
 */
@Composable
private fun SidewaysEntryItem(
    entry: CartographyEntry,
    hero: GalleryPhoto?,
    tracksById: Map<String, Track>,
    savedPaths: Map<String, List<LatLng>>,
    distanceUnit: DistanceUnit,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)?,
) {
    val item: @Composable (TileOptions?) -> Unit = { options ->
        if (isCollapsedEntry(entry, hasHero = hero != null)) {
            SidewaysCollapsedEntryRow(entry = entry, distanceUnit = distanceUnit, onClick = onOpen, options = options)
        } else {
            val slot = entrySlotContent(hero, entryThumbnailTracksOrSaved(entry, tracksById, savedPaths), entryStats(entry, distanceUnit))
            SidewaysEntryCard(
                entry = entry,
                distanceUnit = distanceUnit,
                onClick = onOpen,
                slot = { EntrySlotView(entry.id, slot) },
                options = options,
            )
        }
    }
    if (onDelete != null) {
        LongPressOptionsBox(
            longClickLabel = "Options for entry on ${displayDate(entry.date)}",
            onEdit = onEdit,
            onDelete = onDelete,
        ) { options -> item(options) }
    } else {
        item(null)
    }
}

/** The Entries grid itself (J5: the short-window tests scroll and measure it). */
internal const val ENTRIES_GRID_TAG = "entries-grid"

/** The test tag of an entry card's two-stage swipe row (J4b L2). */
internal fun entrySwipeTag(entryId: String): String = "entries-swipe-$entryId"
