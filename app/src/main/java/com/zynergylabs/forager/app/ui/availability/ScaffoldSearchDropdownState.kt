package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.MutableState

/**
 * The search dropdown's two pieces of state in the frame ([CompactMainScaffold]), carried as one object so the dropdown's own
 * file ([ScaffoldSearchDropdownLayer]) reads and writes them (UI split Phase 2.5, dispatch 2026-10-19-39; survey
 * `docs/audits/2026-10-18-ui-split-survey.md` on branch `ui-split-survey`, section 5.4).
 *
 * **A view over State objects, never a copy** (the pattern of [CompactMapLayoutState]). [CompactMainScaffold] still remembers both
 * States where it always did, unkeyed and with the same start value, and builds this holder from them straight after the second,
 * on every composition, without a remember of its own: the frame's slots are unchanged, and every instance wraps the same two
 * States. Each read here is a getter on that State and each write a setter on it. A value copied in would be stale the moment the
 * State changed: the trap the frame's extraction found (`docs/audits/2026-09-26-split-stage-g-completion-report.md`).
 */
internal class ScaffoldSearchDropdownState(
    private val showSearchDropdownState: MutableState<Boolean>,
    private val pickingSearchLocationOnMapState: MutableState<Boolean>,
) {
    /** Whether the search panel under the search bar is open (the frame's `showSearchDropdown`). */
    var showSearchDropdown: Boolean
        get() = showSearchDropdownState.value
        set(value) { showSearchDropdownState.value = value }

    /** Whether the Maps tab is picking the search's location with the centre pin (the frame's `pickingSearchLocationOnMap`). */
    var pickingSearchLocationOnMap: Boolean
        get() = pickingSearchLocationOnMapState.value
        set(value) { pickingSearchLocationOnMapState.value = value }
}
