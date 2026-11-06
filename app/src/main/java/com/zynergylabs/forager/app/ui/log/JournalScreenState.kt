package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The Journal destination's user-set UI state: which top tab is showing and what Records is
 * filtered to. Journal redesign J1, S1 (`prompts/preserved/2026-09-27-16.md`; plan J10 and L7 in
 * `docs/plans/journal-redesign.md`; owner ruling 4 in `docs/audits/2026-09-27-journal-j0-pulse.md`:
 * "Hoisted, saveable").
 *
 * **Why it exists.** Until J1 every one of these choices was plain `remember` inside the Journal
 * branch of `when (compactTab)` (`JournalTab`'s own `selectedTopTab`, `RecordsTab`'s own
 * `selectedTab`), so leaving the Journal bottom-nav tab disposed them and coming back reset the user
 * to Cartography and Waypoints — a reset CLAUDE.md's UX defaults call a bug. This holder is created
 * with [rememberJournalScreenState] in `AvailabilityScreen`, above that branch, beside `compactTab`,
 * and passed down, so it outlives the branch.
 *
 * **Why saveable, not only hoisted.** A plain rotation does not recreate the Activity
 * (`AndroidManifest.xml`'s `configChanges`, J0 A3), but a night-mode toggle, a fold, a locale or
 * font-scale change and process death still do, and the owner's ruling covers those. [Saver] writes
 * enum *names*, not ordinals, so reordering an enum (J1 reorders [RecordsSubTab] to chip order) can
 * never silently restore a different value; an unknown name fails loudly in `valueOf` rather than
 * falling back to a default nobody logged (CLAUDE.md, errors).
 *
 * **What is not here, on purpose.** Transient state stays with the composable that owns it
 * (`JournalTab`'s `mode`, its two picker flags, the one-shot `recordsPendingSubTab` latch, the
 * external `PendingJournalDestination`, every dialog): those describe an interaction in progress,
 * not a place the user chose to be. `LogPanel` (the wide tree) keeps its own local state; bringing it
 * onto this holder is plan stage J6.
 *
 * **Added in J2.** The Entries view mode (timeline/album), [entriesView], is the third element of
 * [Saver]'s list. **Added in J5:** [searchHeaderRevealed], the fourth. Scroll positions (plan J10) are still not built; add each as a field and as one
 * more element of that list, keeping names rather than ordinals.
 */
@Stable
internal class JournalScreenState(
    initialTopTab: JournalTopTab = JournalTopTab.CARTOGRAPHY,
    initialRecordsFilter: RecordsSubTab = DEFAULT_RECORDS_FILTER,
    initialEntriesView: EntriesViewMode = EntriesViewMode.TIMELINE,
    initialSearchHeaderRevealed: Boolean = false,
) {
    /** Which of the Journal's two top tabs is showing. */
    var topTab: JournalTopTab by mutableStateOf(initialTopTab)

    /**
     * What Records shows. A [MutableState] rather than only a delegated property because
     * `RecordsTab` takes it as a state object: `LogPanel`, out of scope until J6, calls `RecordsTab`
     * without one and gets `RecordsTab`'s own local default, with no change to `LogPanel.kt`.
     */
    val recordsFilterState: MutableState<RecordsSubTab> = mutableStateOf(initialRecordsFilter)
    var recordsFilter: RecordsSubTab by recordsFilterState

    /**
     * Whether Entries shows its timeline or its album — journal redesign J2, T3 (plan J3, J10). A
     * [MutableState] for the same reason as [recordsFilterState]: `CartographyScreen` takes it as a
     * state object, and `LogPanel` (J6) calls `CartographyScreen` without one and gets a local
     * default.
     */
    val entriesViewState: MutableState<EntriesViewMode> = mutableStateOf(initialEntriesView)
    var entriesView: EntriesViewMode by entriesViewState

    /**
     * Whether the app-wide search header is brought up on the Journal tab in a short window —
     * journal redesign J5 (owner's ruling 1, "Hide header; icon reveals it (Recommended)"). In a
     * short landscape window the header is hidden on the Journal tab (height is the scarce axis);
     * the short-window header row's search icon sets this, and the same icon or Back clears it.
     * Portrait and every other tab ignore it. Here rather than in `JournalTab` because the header is
     * composed by `CompactMainScaffold`, above the Journal branch, which reads it; saved with the rest
     * so a user who brought the header up finds it up again after leaving the tab (CLAUDE.md, UX
     * defaults: a panel the user opened stays open).
     */
    var searchHeaderRevealed: Boolean by mutableStateOf(initialSearchHeaderRevealed)

    // Scroll positions go here when a stage builds them (see the class doc comment).

    companion object {
        val Saver: Saver<JournalScreenState, Any> = listSaver(
            save = { state -> listOf(state.topTab.name, state.recordsFilter.name, state.entriesView.name, state.searchHeaderRevealed.toString()) },
            restore = { saved ->
                JournalScreenState(
                    initialTopTab = JournalTopTab.valueOf(saved[0]),
                    initialRecordsFilter = RecordsSubTab.valueOf(saved[1]),
                    initialEntriesView = EntriesViewMode.valueOf(saved[2]),
                    // toBooleanStrict: anything but "true"/"false" fails loudly, as valueOf does above.
                    initialSearchHeaderRevealed = saved[3].toBooleanStrict(),
                )
            },
        )
    }
}

/** Creates the Journal's [JournalScreenState], saved and restored across Activity recreation — see that class. */
@Composable
internal fun rememberJournalScreenState(): JournalScreenState =
    rememberSaveable(saver = JournalScreenState.Saver) { JournalScreenState() }

/**
 * What Records shows when nothing has chosen otherwise — the holder's initial value and
 * `RecordsTab`'s own local default for a caller that passes no state (tests).
 *
 * Finds since RECORD -868 (the owner: "Have Finds be the default chip the Records tab opens up to"; "Your last pick
 * until the app restarts (Recommended)"); it was All from journal redesign J1. "Until the app restarts" needs nothing
 * more than this value: the holder is hoisted above the Journal tab, so a pick survives leaving the tab, and a cold
 * start has no saved state, so it opens here. One edge, inferred from how saved state works and not checked on a
 * phone: a process the system killed in the background and then restored keeps the last pick, which to the user is
 * the app resuming, not restarting.
 */
internal val DEFAULT_RECORDS_FILTER: RecordsSubTab = RecordsSubTab.FINDS

/** Entries' two views (J2, T3; plan J3): the entries timeline, or the album of every photo grouped by day. */
internal enum class EntriesViewMode { TIMELINE, ALBUM }
