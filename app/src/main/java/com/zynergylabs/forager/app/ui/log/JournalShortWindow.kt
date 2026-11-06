package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.ui.motion.IconSwap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.adaptive.isLandscapeWindow
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The Journal in a short window (a phone on its side) — journal redesign J5
 * (`prompts/preserved/2026-09-27-26.md`; plan L1-L3 in `docs/plans/journal-redesign.md`, approved
 * as O6; owner's ruling 1). Height is the scarce axis there (`landscape-phone-design.md` P5): about
 * 384 dp for the whole window on the S22 Ultra, less its status bar. So the chrome above the
 * content shrinks to one pinned 48 dp row ([ShortWindowJournalHeader], L1) and one row that gets out
 * of the way while the content scrolls ([ShortWindowSecondRow], L3), and the app-wide search header
 * is hidden on this tab until the row's search icon brings it up (ruling 1; the header's condition
 * is `CompactMainScaffold`'s, reading [JournalScreenState.searchHeaderRevealed]).
 *
 * Portrait is untouched: every composable here is used only where [isLandscapeJournal] is
 * true, and the callers keep the same composition structure in both orientations (a frame whose
 * header slot is empty in portrait), so a rotation, which does not recreate the Activity, keeps
 * every piece of `remember` state below it (plan L7).
 */
@Composable
internal fun isLandscapeJournal(): Boolean =
    // The same test B1-B3 use (`AvailabilityScreen`'s isLandscapeWindow): a window in landscape (R15).
    // Until dispatch 2026-09-28-246 it was "under 480 dp tall and landscape", which no tablet meets;
    // now a landscape tablet gets the phone's sideways Journal too.
    isLandscapeWindow()

/**
 * L1: one pinned row, 48 dp tall, holding the Entries | Records switch at its start and, at its end,
 * the search icon (ruling 1) and the screen's own action ([action]: "New entry" on the timeline, the
 * photo button on the album; none elsewhere, since only those two had a floating button in portrait
 * to move here, L2).
 *
 * The search icon toggles [searchRevealed]: touching it brings the app-wide header up, touching it
 * again puts it away (and `JournalTab`'s Back handler does too). [showSearch] is false while an entry
 * is open, because the scaffold hides the header then anyway (its `isEditingJournalEntry` rule) and
 * the icon would do nothing a user could see.
 */
@Composable
internal fun ShortWindowJournalHeader(
    selectedTopTab: JournalTopTab,
    onSelectTopTab: (JournalTopTab) -> Unit,
    showSearch: Boolean,
    searchRevealed: Boolean,
    onToggleSearch: () -> Unit,
    action: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * Dispatch 2026-10-17-09, RECORD -1243 (the owner: "still have the chips be on the same line as the entries | records. But
     * centered between the entries | records and search icon, scrollable with offline maps showing and highlighted"; "All of
     * Records, landscape (Recommended)"): what sits between the switch and the search icon, centred in that space. Records'
     * filter chips; `null` (Entries, and every caller before -1243) leaves the space empty, as before.
     */
    middle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SHORT_HEADER_HEIGHT)
            .padding(horizontal = Spacing.lg)
            .testTag(SHORT_HEADER_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        JournalSwitch(selected = selectedTopTab, onSelect = onSelectTopTab)
        Box(modifier = Modifier.weight(1f).testTag(SHORT_HEADER_MIDDLE_TAG), contentAlignment = Alignment.Center) { middle?.invoke() }
        if (showSearch) {
            // Motion Part 1 (dispatch 2026-09-28-652, item 5, scout J19): Search and Search-off crossfade with a slight grow. The
            // label is on the button, because both icons are composed for the length of the swap.
            BouncingIconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .testTag(SHORT_SEARCH_TAG)
                    .semantics { contentDescription = if (searchRevealed) "Hide search" else "Search" },
            ) {
                IconSwap(targetState = searchRevealed) { revealed ->
                    Icon(if (revealed) Icons.Filled.SearchOff else Icons.Filled.Search, contentDescription = null)
                }
            }
        }
        action?.invoke()
    }
}

/** L2: the timeline's "✎ New" as an icon button in the L1 row, doing what the floating button did. */
@Composable
internal fun ShortWindowNewEntryButton(onClick: () -> Unit) {
    BouncingIconButton(onClick = onClick, modifier = Modifier.testTag(SHORT_NEW_TAG)) {
        Icon(Icons.Filled.Edit, contentDescription = "New entry")
    }
}

/**
 * RECORD -753 (the owner: "Yes, match Entries (Recommended)"): Finds' "New find" as an icon button in the L1 row, doing what its
 * floating button does, the same way [ShortWindowNewEntryButton] does for "New entry". The leaf is the floating button's (the
 * Finds chip's; RECORD -751).
 */
@Composable
internal fun ShortWindowNewFindButton(onClick: () -> Unit) {
    BouncingIconButton(onClick = onClick, modifier = Modifier.testTag(SHORT_NEW_FIND_TAG)) {
        Icon(Icons.Filled.Eco, contentDescription = "New find")
    }
}

/**
 * L2: the album's "📷" as an icon button in the L1 row, opening the same Take photo / Import menu the
 * album's floating button opens (J2's `AddPhotoButton`), with the same two actions and item tags.
 * The launchers come from the caller, which holds one set for both buttons, so turning the phone
 * mid-import (the system picker is up) does not drop the result on a launcher that left the
 * composition.
 */
@Composable
internal fun ShortWindowAddPhotoButton(onTakePhoto: () -> Unit, onImport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        BouncingIconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(SHORT_PHOTO_TAG)) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                onClick = { menuOpen = false; onTakePhoto() },
                modifier = Modifier.testTag(ENTRIES_FAB_MENU_TAKE_PHOTO_TAG),
            )
            DropdownMenuItem(
                text = { Text("Import") },
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                onClick = { menuOpen = false; onImport() },
                modifier = Modifier.testTag(ENTRIES_FAB_MENU_IMPORT_TAG),
            )
        }
    }
}

/**
 * L3: the drafts banner as a chip, "✎ N drafts ›". [onClick] is the banner's Continue, routed by
 * count in `CartographyScreen` (one draft opens it, several open the full-screen list).
 */
@Composable
internal fun ShortWindowDraftsChip(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        label = { Text(shortWindowDraftsChipLabel(count)) },
        modifier = modifier.testTag(SHORT_DRAFTS_CHIP_TAG),
    )
}

/** "✎ 1 draft ›" / "✎ N drafts ›" (plan L3's wording). */
internal fun shortWindowDraftsChipLabel(count: Int): String =
    "✎ $count ${if (count == 1) "draft" else "drafts"} ›"

/**
 * L3's hide-on-scroll: the second row hides when the content below it scrolls down (the list moves
 * up) and returns when it scrolls back up. Read from what the scrolling child actually consumed
 * ([onPostScroll]), not from the finger alone, so a drag on a list that cannot move changes nothing.
 * Transient per screen, deliberately not in [JournalScreenState]: it follows the scroll, and is not a
 * choice the user made.
 */
@Stable
internal class HideOnScrollState {
    var visible: Boolean by mutableStateOf(true)
        private set

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            when {
                consumed.y < -SCROLL_SLOP_PX -> visible = false
                consumed.y > SCROLL_SLOP_PX -> visible = true
            }
            return Offset.Zero
        }
    }

    private companion object {
        /** Ignore sub-pixel jitter; any real scroll is far more than this. */
        const val SCROLL_SLOP_PX = 1f
    }
}

@Composable
internal fun rememberHideOnScrollState(): HideOnScrollState = remember { HideOnScrollState() }

/** L3's second row, shown or hidden by [state]; its content is the caller's (the toggle and chip, or the filter chips). */
@Composable
internal fun ColumnScope.ShortWindowSecondRow(state: HideOnScrollState, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = state.visible, enter = expandVertically(), exit = shrinkVertically()) {
        content()
    }
}

/** L1's height (plan L1: 48 dp). */
internal val SHORT_HEADER_HEIGHT = 48.dp

internal const val SHORT_HEADER_TAG = "journal-short-header"
internal const val SHORT_SEARCH_TAG = "journal-short-search"

/** RECORD -1243: the L1 row's space between the switch and the search icon, where Records' chips are centred. */
internal const val SHORT_HEADER_MIDDLE_TAG = "journal-short-header-middle"
internal const val SHORT_NEW_TAG = "journal-short-new"
internal const val SHORT_NEW_FIND_TAG = "journal-short-new-find"
internal const val SHORT_PHOTO_TAG = "journal-short-photo"
internal const val SHORT_DRAFTS_CHIP_TAG = "entries-drafts-chip"
