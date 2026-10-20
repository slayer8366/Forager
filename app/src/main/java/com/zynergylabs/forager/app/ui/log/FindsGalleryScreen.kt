package com.zynergylabs.forager.app.ui.log

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
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.ListRowShape
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.PageSlide
import com.zynergylabs.forager.app.ui.motion.StateCrossfade
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.ui.unit.IntOffset
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.format.is24HourClock
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.platform.testTag
import java.time.LocalDate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

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
 * embedded the standalone Photo Gallery screen (removed in J6) as a third tab (Log/Drafts/Album); that was a second, independent
 * path to the same [com.zynergylabs.forager.app.domain.model.GalleryPhoto] data [CartographyScreen]'s own Album
 * submenu already shows, flagged as deliberate-but-unwanted duplication in the original Stage 2b
 * dispatch's closing disclosure. [CartographyScreen]'s Album is now the sole path from both window
 * classes' Records/Finds side; the drawer-hosted `DrawerPanel.PhotoGallery` destination this note once
 * left untouched has since been removed (J6, the owner's ruling 2, 2026-09-28). [LogEntryListScreen] never embedded an Album tab to begin
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
     * above the grid when there is nothing to
     * show because the read failed, per docs/error-presentation-spec.md.
     */
    loadErrorMessage: String? = null,
    /**
     * `null` (the default) omits the "New find" button entirely (RECORD -751; until then the grid's first "+" tile) —
     * [LogPanel] passes no lambda here, matching [LogEntryListScreen]'s own former shape: the expanded window starts a new
     * find via the map's "Log a find" flow, not a control inside this list. [JournalTab] passes one.
     */
    onAddEntry: (() -> Unit)? = null,
    /** Grid column count — 2 for compact, more for expanded/tablet; see this composable's own doc comment. */
    columns: Int = 2,
    /**
     * J4b L1 (owner ruling "Lists swipe, grids long-press (Recommended)"): when set, a long-press on a
     * tile opens a menu whose Delete calls this with the find's id (a *pending* delete with Undo).
     * `null` (the default; `LogPanel` passes none) leaves the tiles tap-only.
     */
    onDeleteEntry: ((String) -> Unit)? = null,
    /** J4b L1: the menu's Edit, opening the find in its edit form. Only read when [onDeleteEntry] is set. */
    onEditEntry: ((String) -> Unit)? = null,
    /**
     * RECORD -753 (the owner: "Yes, match Entries (Recommended)"): set in a short landscape window, where "New find" sits in the
     * Journal's L1 row as "New entry" does on Entries, so this screen draws no floating button and its grid needs no clearance for
     * one. It is written with whether the button belongs on screen now (Log is the tab showing, the grid is not loading, and
     * [onAddEntry] is set), and set back to false when this screen leaves. `null` (the default) is the floating button, as before.
     */
    newFindInHeader: MutableState<Boolean>? = null,
) {
    var selectedTab by remember { mutableStateOf(FindsGalleryTab.LOG) }
    val galleryLoading = isLoading && entries.isEmpty() && draftEntries.isEmpty()
    if (newFindInHeader != null) {
        val belongs = onAddEntry != null && !galleryLoading && selectedTab == FindsGalleryTab.LOG
        SideEffect { if (newFindInHeader.value != belongs) newFindInHeader.value = belongs }
        DisposableEffect(newFindInHeader) { onDispose { newFindInHeader.value = false } }
    }

    // Motion Part 3, Amendment 2 (RECORD -682; scout F5): the spinner and the gallery crossfade instead of swapping in one frame
    // (motion/StateCrossfade.kt).
    StateCrossfade(
        targetState = galleryLoading,
        modifier = modifier.fillMaxSize(),
    ) { loading ->
    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(selected = selectedTab == FindsGalleryTab.LOG, onClick = { selectedTab = FindsGalleryTab.LOG }, text = { Text("Log") })
            Tab(
                selected = selectedTab == FindsGalleryTab.DRAFTS,
                onClick = { selectedTab = FindsGalleryTab.DRAFTS },
                text = { Text(if (draftEntries.isEmpty()) "Drafts" else "Drafts (${draftEntries.size})") },
            )
        }

        val glide = MotionTokens.listRowSpec<IntOffset>()
        // Motion Part 3, Amendment 1 (RECORD -681, "Same rule everywhere"; scout F4): Drafts opens over Log, sliding in from the
        // right, and Log slides it back out. The tab row above stays still. Each tab's grid is its own page, drawn from its own
        // tab, with its own rows.
        // RECORD -751: the "New find" button replaces the "+" tile. It belongs to Log, as the tile did, and sits over the grid
        // at its bottom end, as "New entry" sits over the Entries timeline; Log's grid gets that timeline's bottom clearance.
        // RECORD -753: not in a short landscape window, where the L1 row carries it (newFindInHeader).
        val showNewFind = onAddEntry != null && newFindInHeader == null
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        PageSlide(
            targetState = selectedTab,
            depthOf = { it.ordinal },
            modifier = Modifier.fillMaxSize(),
        ) { shownTab ->
        Column(modifier = Modifier.fillMaxSize()) {
        val visibleEntries = if (shownTab == FindsGalleryTab.DRAFTS) draftEntries else entries
        // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"): a deleted find's tile fades and shrinks, the grid
        // closes up after it with a glide, and Undo brings it back the way it went (motion/ListMotion.kt).
        val rows = rememberListRows(visibleEntries, key = { it.id })
        if (visibleEntries.isEmpty() && loadErrorMessage != null) {
            Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f).testTag(FINDS_GRID_TAG),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                top = Spacing.lg,
                end = Spacing.lg,
                bottom = if (showNewFind && shownTab == FindsGalleryTab.LOG) FAB_CLEARANCE else Spacing.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Data part D (the owner, RECORD -656: "group the entries by date and then keep the name they gave on the tile
            // instead of the date"; dispatched by -702): the tiles sit under one heading per day, newest day first, as the
            // Records logbook orders its days. A heading spans the grid's width. A leaving tile stays under its own day.
            for ((day, dayRows) in groupByDayNewestFirst(rows) { it.item.foundOn }) {
                item(key = findsDayHeaderKey(shownTab, day), span = { GridItemSpan(maxLineSpan) }) {
                    FindsDayHeader(day, Modifier.animateItem(fadeInSpec = null, placementSpec = glide, fadeOutSpec = null))
                }
                if (shownTab == FindsGalleryTab.DRAFTS) {
                    items(dayRows, key = { it.key }) { row ->
                        ListRowMotion(row, Modifier.animateItem(fadeInSpec = null, placementSpec = glide, fadeOutSpec = null), ListRowShape.TILE) { entry ->
                            FindTileWithOptions(entry = entry, onClick = { onOpenDraftEntry(entry.id) }, isDraft = true, onEdit = onEditEntry, onDelete = onDeleteEntry)
                        }
                    }
                } else {
                    items(dayRows, key = { it.key }) { row ->
                        ListRowMotion(row, Modifier.animateItem(fadeInSpec = null, placementSpec = glide, fadeOutSpec = null), ListRowShape.TILE) { entry ->
                            FindTileWithOptions(entry = entry, onClick = { onOpenEntry(entry.id) }, onEdit = onEditEntry, onDelete = onDeleteEntry)
                        }
                    }
                }
            }
        }
        }
        }
        // The button only makes sense against the committed log, as the "+" tile it replaces did: a new find starts as a
        // draft either way (see MushroomLogViewModel.onStartNewEntry), but "New find" shown over Drafts would read as "add a
        // draft", which is not a distinct action from "add a find". Its leaf is the Finds chip's (RecordsSubTab.chipIcon;
        // the owner: "The Finds leaf (Recommended)").
        if (showNewFind && selectedTab == FindsGalleryTab.LOG) {
            JournalNewItemButton(
                text = "New find",
                icon = Icons.Filled.Eco,
                onClick = onAddEntry,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg).testTag(FINDS_FAB_TAG),
            )
        }
        }
    }
    }
    }
}

/** Finds' "New find" floating button (RECORD -751), for tests. */
internal const val FINDS_FAB_TAG = "finds-fab"

/** A day heading's key in the grid: a string, so it never collides with a tile's key (a find's id). */
private fun findsDayHeaderKey(tab: FindsGalleryTab, day: LocalDate): String = "finds-day-${tab.name}-$day"

/**
 * [items] grouped by [day], newest day first, each day's items in their given order: the Finds grid's headings (data part D,
 * RECORD -702), ordered as the Records logbook ordered its days until RECORD -868 grouped it by kind.
 */
internal fun <T> groupByDayNewestFirst(items: List<T>, day: (T) -> LocalDate): List<Pair<LocalDate, List<T>>> =
    items.groupBy(day).toList().sortedByDescending { it.first }

/** A day heading in the Finds grid, "Oct 7, 2026". Takes no touch. */
@Composable
private fun FindsDayHeader(day: LocalDate, modifier: Modifier = Modifier) {
    Text(
        displayDate(day),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.sm).testTag(findsDayHeaderTag(day)),
    )
}

/**
 * The Finds grid, for tests (RECORD -717): the grid is lazy, so a tile below the window is not composed, and a test scrolls the
 * grid to a tile ([androidx.compose.ui.test.performScrollToNode]) before touching or counting it.
 */
internal const val FINDS_GRID_TAG = "finds-grid"

/** A Finds grid day heading, for tests. */
internal fun findsDayHeaderTag(day: LocalDate): String = "finds-day-header-$day"

/** Which of [FindsGalleryScreen]'s two tabs is selected — ordinal order matches display order. */
private enum class FindsGalleryTab { LOG, DRAFTS }

/**
 * One logged find in the gallery grid — a cover photo when one exists, otherwise a placeholder
 * icon. [isDraft] renders a "Draft" badge under the date; a committed find shows the date alone
 * (the former "Incomplete" badge was removed on 2026-09-13 — see the comment at that spot).
 */
@Composable
internal fun FindTile(entry: MushroomLogEntry, onClick: () -> Unit, modifier: Modifier = Modifier, isDraft: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GALLERY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(Spacing.sm),
    ) {
        FindTileBody(entry = entry, isDraft = isDraft)
    }
}

/**
 * [FindTile] with a long-press menu (J4b L1): the same tile, whose card takes the tap and the
 * long-press on one node ([tileClickable]), inside a [LongPressOptionsBox] offering Edit and Delete.
 * With no [onDelete] it is [FindTile] exactly, so a caller that gives no delete (`LogPanel`) is
 * unchanged. The long-click reads "Options for <the tile's title>" ([findTitle]), or for its screen-reader label when blank.
 */
@Composable
internal fun FindTileWithOptions(
    entry: MushroomLogEntry,
    onClick: () -> Unit,
    onEdit: ((String) -> Unit)?,
    onDelete: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    isDraft: Boolean = false,
) {
    if (onDelete == null) {
        FindTile(entry = entry, onClick = onClick, modifier = modifier, isDraft = isDraft)
        return
    }
    LongPressOptionsBox(
        longClickLabel = "Options for ${findTitle(entry, is24HourClock()) ?: findBlankTitleLabel(entry)}",
        onEdit = onEdit?.let { edit -> { edit(entry.id) } },
        onDelete = { onDelete(entry.id) },
        modifier = modifier,
    ) { options ->
        val shape = RoundedCornerShape(Spacing.sm)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(GALLERY_TILE_ASPECT_RATIO)
                .clip(shape)
                .tileClickable(onClick = onClick, options = options),
            shape = shape,
        ) {
            FindTileBody(entry = entry, isDraft = isDraft)
        }
    }
}

/** What a find tile draws inside its card: the cover photo or placeholder, then the caption. */
@Composable
private fun FindTileBody(entry: MushroomLogEntry, isDraft: Boolean) {
    run {
        Column(modifier = Modifier.fillMaxSize()) {
            // weight(1f), not aspectRatio(1f): a fixed square ate a disproportionate share of the
            // card's own fixed-aspect-ratio height, squeezing the caption below it -- on a draft
            // tile specifically, two lines (the title plus the "Draft" badge) rather than
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
                // Data part D (RECORD -702, -703): the find's name, or the time it was found, or nothing ([findTitle]). A blank
                // title keeps its line, so tiles in a row stay the same height, and says "Find, <date>" to a screen reader.
                val title = findTitle(entry, is24HourClock())
                val blankLabel = findBlankTitleLabel(entry)
                Text(
                    title ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (title == null) Modifier.semantics { contentDescription = blankLabel } else Modifier,
                )
                // Owner ruling, 2026-09-13: no "Incomplete" badge any more. It read from the seven
                // morphology fields, which the edit form no longer offers, so it described fields
                // a user cannot fill in; the date stands alone on a committed tile. The private
                // hasUnrecordedFields() that drove it had no other reader and went with it.
                if (isDraft) {
                    Text(
                        "Draft",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** A little taller than wide, reading as a card/page rather than a square photo tile. */
private const val GALLERY_TILE_ASPECT_RATIO = 0.85f
private const val ADD_TILE_ICON_SIZE_DP = 40
