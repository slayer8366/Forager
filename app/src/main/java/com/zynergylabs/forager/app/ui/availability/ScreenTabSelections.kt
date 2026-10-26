package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.ui.log.CartographyEntryMode
import com.zynergylabs.forager.app.ui.log.FindOverView
import com.zynergylabs.forager.app.ui.log.JournalEntryMode
import com.zynergylabs.forager.app.ui.log.JournalScreenState
import com.zynergylabs.forager.app.ui.log.rememberJournalScreenState

/**
 * The tab, Journal and map-filter state [rememberScreenTabSelections] remembers, read and written through the original State
 * objects, never copies of them (Stage G). The four entry States are passed on as themselves, as the body passed them.
 */
internal class ScreenTabSelections(
    private val selectedTabState: MutableState<ResultsTab>,
    private val compactTabState: MutableState<CompactTab>,
    val journalScreenState: JournalScreenState,
    val cartographyEntryModeState: MutableState<CartographyEntryMode>,
    val cartographyOpenEntryGroupsState: MutableState<Set<EntryGroup>>,
    val findEntryModeState: MutableState<JournalEntryMode>,
    val findOverViewState: MutableState<FindOverView?>,
    private val logPhotoAcquisitionInFlightState: MutableState<Boolean>,
    private val mapTaxonFilterState: MutableState<Long?>,
) {
    var selectedTab: ResultsTab
        get() = selectedTabState.value
        set(value) { selectedTabState.value = value }
    var compactTab: CompactTab
        get() = compactTabState.value
        set(value) { compactTabState.value = value }
    var logPhotoAcquisitionInFlight: Boolean
        get() = logPhotoAcquisitionInFlightState.value
        set(value) { logPhotoAcquisitionInFlightState.value = value }
    var mapTaxonFilter: Long?
        get() = mapTaxonFilterState.value
        set(value) { mapTaxonFilterState.value = value }
}

/**
 * The user's place on the screen, held above the tabs: the results tab, the bottom-nav tab, the Journal's places and open entry
 * modes, the photo round-trip flag and the map's "View on Map" filter. Moved out of [AvailabilityScreen] whole by dispatch
 * 2026-10-26-17 (RECORD -1682), a pure move, and called at the line where the group stood, so its remembers keep their order
 * and keys (-1484). The holder is built afresh on each composition and not remembered, so it adds no slot. The four
 * `rememberSaveable` calls move with it: their saved-state keys change with the move, which a process restore within one
 * build does not notice and an app update drops anyway (the S22, 2026-10-21: saved state does not survive an update).
 * The comments below moved with the code; "below" and "here" in them mean the screen's body, where these were written.
 */
@Composable
internal fun rememberScreenTabSelections(): ScreenTabSelections {
    // Map up front. The list is one tap away; the map is the thing this screen is arranged around.
    //
    // Saveable, like compactTab below (journal redesign J2, second coder; planner's call in
    // prompts/preserved/2026-09-27-19.md): compactTab restores after an Activity recreation, and a
    // plain-remember selectedTab reset to MAP beside it, so after a recreation on Seasonal the bottom
    // nav showed Seasonal while the LaunchedEffect below asked for the map's sightings, and a new
    // search did not reload Seasonal until a tab tap re-synced the two. Saving both keeps them equal
    // across a restore; nothing else about tab selection changed.
    val selectedTabState = rememberSaveable { mutableStateOf(ResultsTab.MAP) }

    // The compact bottom nav's own 5-way selection — see [CompactTab]'s doc comment for why this
    // is separate from selectedTab rather than extending ResultsTab itself (a 3-way enum). Kept in sync with selectedTab
    // below whenever the tapped destination is one of the three they share, so onMapTabSelected/
    // onSeasonalTabSelected's LaunchedEffect keeps firing correctly .
    //
    // Journal redesign J2, T5 (owner ruling "Yes, fold into J2 (Recommended)"): saveable, so an
    // Activity recreation (a night-mode toggle, a fold, process death; a plain rotation does not
    // recreate, see AndroidManifest.xml's configChanges) no longer drops the user back on Maps. An
    // enum saves as-is through the default saver. selectedTab above was left plain remember by T5,
    // which let the two disagree after a recreation; it is saveable too since the second J2 coder
    // (see its own comment).
    val compactTabState = rememberSaveable { mutableStateOf(CompactTab.MAP) }

    // Journal redesign J1, S1: the Journal's user-set UI state (top tab, Records selection), held
    // here beside compactTab rather than inside the Journal branch of `when (compactTab)`, so leaving
    // the Journal tab no longer disposes it, and saveable so an Activity recreation does not either.
    // See JournalScreenState's own doc comment.
    val journalScreenState = rememberJournalScreenState()
    // Intent 2026-09-28-44, F2 (the owner: "Return to the editor (Recommended)"): whether an open day
    // entry shows its report or its editor. Held here beside journalScreenState for the same reason,
    // not in it: JournalScreenState holds the places a user chose to be, and this is the state of an
    // open entry, which the ViewModel also holds. Saveable, so a recreation (a night-mode toggle, a
    // fold) does not bring an unsaved edit back in its report view either; the enum saves as-is.
    val cartographyEntryModeState = rememberSaveable { mutableStateOf(CartographyEntryMode.VIEW) }
    // Data part A (dispatch 2026-09-28-667): which groups of the entry editor's "In this entry" panel
    // the user opened, held here so a tab change does not close them (CLAUDE.md, UX defaults). Plain
    // remember, not saveable: it is a convenience within a session, and a recreation closing them
    // loses nothing the user wrote.
    val cartographyOpenEntryGroupsState = remember { mutableStateOf(emptySet<EntryGroup>()) }
    // Intent 2026-09-28-44, F3 ("Keep finds open too (Recommended)"): an open find's mode, and M1's
    // find over the view, held here for the same reason. The mode saves as its enum. FindOverView is
    // plain remember: it survives the tab change the ruling is about, not a recreation.
    val findEntryModeState = rememberSaveable { mutableStateOf(JournalEntryMode.REPORT) }
    val findOverViewState = remember { mutableStateOf<FindOverView?>(null) }

    // Device-check patch, Items 2/3: whether a find's camera/gallery round-trip is currently in
    // flight, reported up from JournalTab via
    // onPhotoAcquisitionInFlightChanged (see LogEntryDetailScreen's own doc comment on that
    // parameter). Read by this screen's own ON_STOP hook below, to suppress the "user backgrounded
    // the app" incidental-exit heuristic while the backgrounding is this app's own doing.
    val logPhotoAcquisitionInFlightState = remember { mutableStateOf(false) }

    // "View on Map" on a List-tab species row: which taxon (if any) the map tabs should limit
    // their sightings to. Lives here, alongside selectedTab/compactTab, because both the List and
    // Map tabs read and clear it and neither is an ancestor of the other
    // (compactMainScaffold shows one at a time, but both are built from this same function's state). Null means "no filter" — the ordinary, every
    // sighting view.
    val mapTaxonFilterState = remember { mutableStateOf<Long?>(null) }
    return ScreenTabSelections(
        selectedTabState = selectedTabState,
        compactTabState = compactTabState,
        journalScreenState = journalScreenState,
        cartographyEntryModeState = cartographyEntryModeState,
        cartographyOpenEntryGroupsState = cartographyOpenEntryGroupsState,
        findEntryModeState = findEntryModeState,
        findOverViewState = findOverViewState,
        logPhotoAcquisitionInFlightState = logPhotoAcquisitionInFlightState,
        mapTaxonFilterState = mapTaxonFilterState,
    )
}
