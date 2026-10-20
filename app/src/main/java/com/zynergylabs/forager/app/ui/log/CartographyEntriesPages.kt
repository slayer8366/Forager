package com.zynergylabs.forager.app.ui.log

// Moved from CartographyScreen.kt unchanged (UI split Phase 1, PR 10e, dispatch 2026-10-19-22).

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zynergylabs.forager.app.domain.model.CartographyEntry

/**
 * A branch of [CartographyScreen] under the short window's L1 row (journal redesign J5). The same
 * Column in every window, with an empty header slot outside a short window, so a branch keeps its
 * place in the composition when the phone turns (plan L7: the manifest handles rotation, so a turn
 * is not a recreation, and a moved call site would drop an open editor's local state). [content]
 * gets the modifier that fills what the row leaves.
 */
@Composable
internal fun ShortWindowFrame(
    header: (@Composable ((@Composable () -> Unit)?) -> Unit)?,
    action: (@Composable () -> Unit)?,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        header?.invoke(action)
        content(Modifier.weight(1f).fillMaxWidth())
    }
}

/**
 * A page of [CartographyScreen] (motion Part 3, item 1): Entries' own top level, the drafts list, the spinner while a new entry's
 * candidates load, an open entry in its report or editor, and the editor's album picker. [depth] is how far in it is (a deeper
 * page slides in over the current one; a shallower one is uncovered by the current one sliding out), and [kind] is which page it
 * is: the spinner and the editor are one page, so a new entry's editor replaces its spinner in place, and an entry's fields
 * changing, or one open entry swapped for another in its report, updates the page in place with no slide.
 */
internal sealed interface EntriesPage {
    val depth: Int
    val kind: String

    data object Home : EntriesPage {
        override val depth = 0
        override val kind = "home"
    }

    data object Drafts : EntriesPage {
        override val depth = 1
        override val kind = "drafts"
    }

    data object Loading : EntriesPage {
        override val depth = EDITOR_DEPTH
        override val kind = EDITOR_KIND
    }

    data class Entry(val entry: CartographyEntry, val mode: CartographyEntryMode) : EntriesPage {
        override val depth = if (mode == CartographyEntryMode.EDIT) EDITOR_DEPTH else 2
        override val kind = if (mode == CartographyEntryMode.EDIT) EDITOR_KIND else "report"
    }

    data class PullPhoto(val entry: CartographyEntry) : EntriesPage {
        override val depth = EDITOR_DEPTH + 1
        override val kind = "pull-photo"
    }
}

private const val EDITOR_DEPTH = 3
private const val EDITOR_KIND = "editor"

/** Which screen [CartographyScreen] shows for [CartographyUiState.editingEntry] — Journal Stage 2c. See this file's own doc comment, "Tap opens the view, not the editor," for the full reasoning. */
internal enum class CartographyEntryMode { VIEW, EDIT }
