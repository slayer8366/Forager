package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.zynergylabs.forager.app.domain.EntryGroup
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.ui.motion.PageSlide
import com.zynergylabs.forager.app.ui.motion.StateCrossfade
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * Cartography, unified — Journal Stage 2b, owner decision #3: **one implementation, responsive
 * layout**, not two composables. [JournalTab] hosts it for the Cartography tab; the only thing that
 * varies with the window is [columns]. (A second host, the tablet's `LogPanel`, was removed in dispatch
 * 2026-09-28-245: a tablet is a big phone.)
 *
 * **Journal redesign J2 replaced the three submenus** (Entries, Drafts, Album, a `SecondaryTabRow`)
 * with one screen: a toolbar whose toggle switches between the entries timeline
 * ([CartographyEntryListScreen] over [CartographyUiState.entries]) and the album ([EntriesAlbum],
 * every gallery photo grouped by day), and, when there are drafts, a banner ([DraftsBanner]) whose
 * Continue opens the one draft or the full-screen [DraftsListScreen] over
 * [CartographyUiState.draftEntries]. The toggle's value is `JournalScreenState`'s when [JournalTab]
 * hosts this. Back unwinds, innermost first: an open entry, the
 * drafts list, the album view.
 *
 * [uiState].editingEntry doubles as this screen's own navigation state, the same convention
 * [MushroomLogUiState.editingEntry] uses: non-null means "showing an entry" (which of
 * [CartographyEntryReportScreen]/[CartographyEntryEditScreen] depends on [mode], below), null means
 * "showing the submenu tabs."
 *
 * ## Tap opens the view, not the editor (Journal Stage 2c)
 *
 * [mode] is a local `remember`-scoped [CartographyEntryMode], the same convention
 * [JournalTab]'s own `JournalEntryMode` already establishes for the identical problem on
 * [com.zynergylabs.forager.app.domain.model.MushroomLogEntry]'s report/edit split — not part of
 * [CartographyUiState], since which of the two screens the *user* sees for [uiState].editingEntry
 * depends on how they got there, not on anything inferable from the entry's own content (mirroring
 * [JournalEntryMode]'s own doc comment on that exact point). Every call site that can set
 * [CartographyUiState.editingEntry] non-null sets [mode] explicitly in the same action: the Entries
 * tab's own [onOpenEntry] sets [CartographyEntryMode.VIEW] (there is something to recount, and no
 * reason to assume an edit is wanted); the Drafts tab's, and starting a brand-new entry, set
 * [CartographyEntryMode.EDIT] (a draft is unfinished work — sending the user to a read-only view of
 * it would be wrong); [CartographyEntryReportScreen]'s own "Edit entry" menu item sets
 * [CartographyEntryMode.EDIT] too. No branch here reads [com.zynergylabs.forager.app.domain.model.CartographyEntry.isDraft]
 * at all — [mode] alone decides, the same shape [JournalTab]'s own `when` uses.
 */
@Composable
internal fun CartographyScreen(
    uiState: CartographyUiState,
    galleryPhotos: List<GalleryPhoto>,
    isLoadingGalleryPhotos: Boolean,
    galleryLoadErrorMessage: String?,
    galleryPhotoEntryReferenceCounts: Map<String, Int>,
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit,
    /** The Album tab's Camera button, and the entry editor's — two targets, one hoisted dialog; see [InAppCameraHost]. */
    onOpenCameraForAlbum: () -> Unit,
    onOpenCameraForEntry: () -> Unit,
    onAddGalleryPhoto: (PhotoSource) -> Unit,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    night: Boolean,
    modifier: Modifier = Modifier,
    /** Threaded to [CartographyEntryReportScreen] only: the Maps tab's basemap, which an entry map opens on. */
    initialMapMode: MapMode = MapMode.DEFAULT,
    /**
     * Map layers L0b (owner's ruling 4, "Same sheet"): the layer choices the Maps tab and the entry map
     * share, and the entry map's Layers-sheet switch. Threaded through to [CartographyEntryReportScreen]
     * only; nothing here reads them. Defaulted so other callers and tests are unchanged.
     */
    mapLayers: MapLayersState = MapLayersState.DEFAULT,
    onMapLayerVisibilityChanged: (layerId: String, visible: Boolean) -> Unit = { _, _ -> },
    getMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    getCoveringOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
    /** See [CartographyEntryReportScreen]'s own doc comment, "Fullscreen." */
    getCurrentLocation: suspend () -> LocationResult,
    onOpenEntry: (String) -> Unit,
    onStartEntry: (LocalDate) -> Unit,
    onCloseEntry: () -> Unit,
    onTextChanged: (String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    onSetFindDecision: (String, Boolean) -> Unit,
    onSetTrackDecision: (String, Boolean) -> Unit,
    onSetWaypointDecision: (String, Boolean) -> Unit,
    onSetOfflineRegionDecision: (Long, Boolean) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    /** Entry-photo-acquisition dispatch, Item 2 — see [CartographyEntryEditScreen]'s own doc comment on this same parameter for the full reasoning, and this file's own lifecycle-observer doc comment for why [photoAcquisitionInFlight] below exists alongside it. */
    onAcquirePhotoForEntry: (PhotoSource) -> Unit,
    onFinishEntry: () -> Unit,
    /** Explicit Save for a committed entry — device-check patch, Item 1. See [CartographyEntryEditScreen]'s own doc comment on the Save/Discard/Cancel policy. */
    onSaveEntry: () -> Unit,
    /** The leave-prompt's Discard option. See [CartographyEntryEditScreen]'s own doc comment. */
    onDiscardEntryChanges: () -> Unit,
    /** The backgrounding-return prompt's "Save as draft" option — pending-edit-and-fixes dispatch, Item 1. See this file's own lifecycle-observer doc comment below. */
    onSaveEntryAsDraft: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    /** Grid column count for the Entries/Drafts lists — two on the phone. */
    columns: Int = 2,
    /**
     * Timeline or album (journal redesign J2, T3). `JournalTab` passes `JournalScreenState`'s, so
     * the choice survives a tab change and a restore; the default is local and unsaved, for a caller that hosts this
     * screen on its own (tests).
     */
    entriesViewState: MutableState<EntriesViewMode> = remember { mutableStateOf(EntriesViewMode.TIMELINE) },
    /**
     * The already-loaded recorded tracks (`TrackRecordingUiState.tracks`), for the cards' track
     * thumbnails (J3, C3; owner ruling "Join in memory (Recommended)"). `JournalTab` passes its own;
     * the default draws no thumbnail.
     */
    tracks: List<Track> = emptyList(),
    /** F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id; see [CartographyEntryListScreen]. */
    getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    /** Ids of draft finds, for the album's find badge (J3, C5); see [EntriesAlbum]. */
    draftFindIds: Set<String> = emptySet(),
    /**
     * An entry card's swipe Delete (J4b L2): a *pending* delete with Undo
     * (`CartographyViewModel.requestDeleteEntry`), on the timeline and in the drafts list. [onDeleteEntry]
     * stays the report's and edit screen's immediate delete behind their confirm dialogs. `null`
     * (the default) leaves the cards without the swipe.
     */
    onRequestDeleteEntry: ((String) -> Unit)? = null,
    /** An album photo's long-press Delete (J4b L3): a *pending* delete with Undo. `null` (the default) leaves the photos without the menu. */
    onRequestDeleteGalleryPhoto: ((String) -> Unit)? = null,
    /**
     * Journal redesign J5 (plan L1-L4, L6): the short window's pinned L1 row, which `JournalTab`
     * builds (the Entries | Records switch and the search icon) and this screen draws at its top with
     * its own action in the row's last slot: "New entry" on the timeline, the photo button on the
     * album, nothing while an entry or the drafts list is open. Non-null means a short landscape
     * window, and with it: no floating button (L2), the drafts chip and view toggle in a second row
     * that hides on scroll (L3), sideways cards with the long-press menu (L4) and a 5-column album
     * (L6). `null` (portrait) is this screen exactly as before.
     */
    shortWindowHeader: (@Composable ((@Composable () -> Unit)?) -> Unit)? = null,
    /**
     * Motion Part 3, Amendment 1 (RECORD -681; the owner: "Keep it still (Recommended)"): set when the caller draws the short
     * window's L1 row itself, above this screen's sliding pages, so the row stays put while only the page below it slides. This
     * screen then draws no L1 row, and puts the action that belongs in the row's last slot here instead (New entry on the
     * timeline, the photo button on the album, nothing otherwise). `null` (the default) is this screen drawing the row itself.
     */
    shortWindowHeaderAbove: MutableState<(@Composable () -> Unit)?>? = null,
    /** Off while the Tools drawer is open over the Journal, so Back closes the drawer (intent 2026-09-28-28); see [JournalTab]'s parameter of the same name. `true` (the default) is every other caller, unchanged. */
    backEnabled: Boolean = true,
    /**
     * M1: what the entry map's glyph bubbles look records up in and open (the J5c sheet's inputs, the
     * photos, and "Open find"), threaded through to [CartographyEntryReportScreen] only; nothing here
     * reads it. Defaulted so other callers and tests are unchanged.
     */
    mapBubbleSources: MapRecordSources = MapRecordSources(),
    /**
     * Whether the open entry shows its report or its editor ([mode]). Intent 2026-09-28-44, F2 (the
     * owner: "Return to the editor (Recommended)"): `JournalTab` passes state held in
     * `AvailabilityScreen`, above the `when (compactTab)` branch this screen is composed in, so an
     * entry left in its editor comes back in its editor, with its leave prompt, and a report never
     * draws an unsaved edit as if it were saved. It was a plain `remember` here, so every return to
     * the Journal reset it to [CartographyEntryMode.VIEW] while the unsaved edit stayed open in the
     * ViewModel (`docs/audits/2026-09-28-leaving-the-journal-investigation.md`, Behaviour 2). The
     * default, local and unsaved, is for callers that host this screen on its own (tests).
     */
    entryModeState: MutableState<CartographyEntryMode> = remember { mutableStateOf(CartographyEntryMode.VIEW) },
    /**
     * J8-4, the Maps tab's "Open entry" (the owner's Q1 ruling, "Open in Journal, prompt first
     * (Recommended)"): an entry to open in its report, handed down once by `JournalTab`,
     * which [onOpenEntryRequestConsumed] clears. An entry open in its editor with unsaved changes gets
     * the existing "Save your changes?" first, and the requested one opens only after Save or Discard;
     * Cancel keeps the edit open and opens nothing. Any other open entry (a report, an unchanged editor,
     * a draft) just closes first. `null` (the default) is every other caller, unchanged.
     */
    openEntryRequest: String? = null,
    onOpenEntryRequestConsumed: () -> Unit = {},
    /** J8-3: the report menu's "Show on map" and "Hide from map" for a saved entry. `null` (the default) offers neither. */
    onSetShownOnMap: ((entryId: String, shown: Boolean) -> Unit)? = null,
    /**
     * Dispatch 2026-09-28-387, Part B: the open entry was left. [fromReport] is whether it was in its report, not its editor, when it went. Called when the user
     * leaves it (Back, the arrow) and again when the open entry goes by any other route, so a request that is stale is dropped. The caller decides whether that
     * is a return to the map; `{}` by default is every other caller.
     */
    onEntryClosed: (entryId: String, fromReport: Boolean) -> Unit = { _, _ -> },
    /** Data part A (dispatch 2026-09-28-667): a group switch in the editor's "In this entry" panel, [CartographyViewModel.onSetEntryGroupIncluded]. `{ _, _ -> }` by default is every other caller. */
    onSetEntryGroupIncluded: (EntryGroup, Boolean) -> Unit = { _, _ -> },
    /** Which of that panel's groups are open: hoisted to the caller (`AvailabilityScreen` holds it), as [entryModeState] is, so it survives a tab change. */
    openEntryGroupsState: MutableState<Set<EntryGroup>> = remember { mutableStateOf(emptySet()) },
) {
    var mode by entryModeState
    var openEntryGroups by openEntryGroupsState
    val shortWindow = shortWindowHeader != null
    // The L1 row as this screen draws it: not at all when the caller draws it above the pages (Amendment 1).
    val headerHere: (@Composable ((@Composable () -> Unit)?) -> Unit)? = if (shortWindowHeaderAbove != null) null else shortWindowHeader

    // The album's Take photo / Import. Held here, above every branch, rather than inside the album
    // (where J2 had it), so the album's floating button (portrait) and the L1 row's photo button (a
    // short window) share one set: a rotation is not a recreation here (the manifest's
    // configChanges), and a launcher that moved with the layout would lose a picker result that
    // arrives after the turn.
    val albumPhotoAcquisition = rememberPhotoAcquisitionLaunchers(onAddGalleryPhoto, onOpenCameraForAlbum)

    // Back-nav-and-save-flow dispatch, Item 1: system back on an open entry used to reach no
    // BackHandler at all — AvailabilityScreen's own comment on its outer four assumed "a Journal
    // entry... unwinds itself first via its own local BackHandler," but no such handler existed
    // here, so back fell straight through to that outer go-home one regardless of depth, on a
    // draft exactly as much as a committed entry. One definition here, not duplicated into
    // JournalTab: both simply host this composable, so this covers both window classes
    // for free — see this file's own class doc comment on why [JournalTab] share this
    // one implementation.
    //
    // requestLeaveEntry() is the single decision both the arrow (CartographyEntryEditScreen's own
    // onBack, wired below) and system back (the BackHandler right after it) drive — hoisted here,
    // not left local to CartographyEntryEditScreen, specifically so both triggers show the exact
    // same Save/Discard/Cancel prompt rather than the arrow consulting it and system back routing
    // around it (Item 2's whole bug). A report-mode entry is never dirty (only EDIT mutates), so
    // this same check naturally always falls to onCloseEntry() for CartographyEntryReportScreen's
    // own back arrow too, with no separate branch needed.
    var confirmingLeaveEntry by remember { mutableStateOf(false) }

    val editingEntry = uiState.editingEntry

    // J8-4: the entry an "Open entry" request is waiting to open while the leave prompt is answered,
    // and whether it was answered with Save or Discard (both close the open entry: Save at once,
    // Discard once its reload lands), as against Cancel, which drops the request.
    var pendingOpenEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var leavePromptResolved by rememberSaveable { mutableStateOf(false) }

    // Leaving the open entry the way the user does (Back, the arrow): read which entry it is, and whether it was in its report, before the close lands, then say so.
    fun closeEntryByUser() {
        val closing = editingEntry?.id
        val inReport = mode == CartographyEntryMode.VIEW
        onCloseEntry()
        if (closing != null) onEntryClosed(closing, inReport)
    }

    fun requestLeaveEntry() {
        // A leave the user starts themselves is not the request's: it no longer waits on this prompt.
        pendingOpenEntryId = null
        leavePromptResolved = false
        if (mode == CartographyEntryMode.EDIT && editingEntry != null && !editingEntry.isDraft && uiState.hasUnsavedChanges) {
            confirmingLeaveEntry = true
        } else {
            closeEntryByUser()
        }
    }

    fun openEntryInReport(id: String) {
        mode = CartographyEntryMode.VIEW
        onOpenEntry(id)
    }

    // J8-4, the Maps tab's "Open entry" (see openEntryRequest): consumed at once, then either opened
    // or held behind the existing leave prompt. Never a silent drop of an unsaved edit: the only path
    // that closes a dirty committed entry here is that prompt's own Save or Discard.
    LaunchedEffect(openEntryRequest) {
        val requested = openEntryRequest ?: return@LaunchedEffect
        onOpenEntryRequestConsumed()
        val open = uiState.editingEntry
        when {
            open == null -> openEntryInReport(requested)
            open.id == requested && mode == CartographyEntryMode.VIEW -> Unit
            mode == CartographyEntryMode.EDIT && !open.isDraft && uiState.hasUnsavedChanges -> {
                pendingOpenEntryId = requested
                leavePromptResolved = false
                confirmingLeaveEntry = true
            }
            else -> {
                onCloseEntry()
                openEntryInReport(requested)
            }
        }
    }
    // The same notice for every other way the open entry goes (a Save or Discard answered at the leave prompt, a deletion, a swap by an "Open entry" request): the
    // last entry seen open and whether it was in its report. Idempotent with [closeEntryByUser], which has already said it.
    var lastOpenEntry by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val openEntryId = editingEntry?.id
    val openEntryInReport = mode == CartographyEntryMode.VIEW
    LaunchedEffect(openEntryId, openEntryInReport) {
        val before = lastOpenEntry
        if (before != null && before.first != openEntryId) onEntryClosed(before.first, before.second)
        lastOpenEntry = openEntryId?.let { it to openEntryInReport }
    }
    LaunchedEffect(pendingOpenEntryId, editingEntry == null, confirmingLeaveEntry, leavePromptResolved) {
        val pending = pendingOpenEntryId ?: return@LaunchedEffect
        when {
            // Saved or discarded, and closed: the requested entry opens now.
            editingEntry == null -> {
                pendingOpenEntryId = null
                leavePromptResolved = false
                openEntryInReport(pending)
            }
            // Cancelled (or the dialog dismissed): the edit stays open and unsaved, and nothing opens.
            !confirmingLeaveEntry && !leavePromptResolved -> pendingOpenEntryId = null
            else -> Unit
        }
    }

    // Enabled only while an entry is open — disabled the instant editingEntry is null, so back at
    // this screen's own top level (the Entries/Drafts/Album tabs) falls straight through to
    // whatever's next (JournalTab's own selectedTopTab step, then AvailabilityScreen's
    // go-home). That fallthrough is the fix's whole point: this adds a step before go-home, it
    // never replaces it — a Journal back could never exit at all would be worse than the bug this
    // dispatch reports.
    BackHandler(enabled = backEnabled && editingEntry != null) {
        requestLeaveEntry()
    }

    // Pending-edit-and-fixes dispatch, Item 1: backgrounding must not commit a dirty committed
    // entry. Self-contained here (owner decision, over threading through AvailabilityScreen's own
    // observer) — one new parameter (onSaveEntryAsDraft) instead of a boolean plus two callbacks
    // crossing JournalTab/AvailabilityScreen, and it keeps this screen's whole
    // backgrounding-and-return story next to the leave-prompt's own identical hoisted-state shape.
    // On ON_STOP: no ViewModel call at all — the pending edit already sits live in
    // CartographyUiState.editingEntry/hasUnsavedChanges (see CartographyViewModel.persist's own doc
    // comment), so "hold it in memory" costs nothing beyond not calling onSaveEntry. This replaces
    // the previous device-check-patch behavior, which called onSaveEntry here and silently
    // committed an edit the user never approved — AvailabilityScreen's own ON_STOP observer no
    // longer touches Cartography at all.
    // On ON_RESUME: if the app was backgrounded while dirty, show the return prompt instead.
    // rememberSaveable on both flags — plain remember would drop them on a real Activity
    // recreation (a config change during backgrounding, or process death short of losing the
    // process outright) and silently skip the prompt, the same catch the device-check patch's
    // rememberSaveable fix for PhotoAcquisitionLaunchers already established.
    // Part 2 follow-ups F1 item 8: the observer and its two saveable flags now live in
    // rememberReturnPromptState (ReturnPromptState.kt), unchanged in behaviour by the move.
    // Search-focus-and-hide dispatch, Item 1 — the one piece of that item actually built. A general
    // LaunchedEffect(isEditingJournalEntry) { focusManager.clearFocus() } in AvailabilityScreen was
    // tried first and made the dropdown-scrim bug worse, not better (see that file's own
    // `isEditingJournalEntry` doc comment for why); AvailabilityScreen's own Item 2 hide condition
    // does that job instead. This call stays because it's not part of that same hide/remount race —
    // it fires here, in this screen's own ON_RESUME, while the edit screen is still open and the
    // search bar is already hidden, for the one moment the owner's own on-device account (Android
    // silently restoring focus to the search field on resume) actually names. Unconditional on
    // `editingEntry != null` alone, not gated behind `backgroundedWhileDirty`/the return prompt below:
    // a clean entry or an open draft can sit through a background/resume cycle too, with nothing else
    // here to clear focus for either of those.
    // Entry-photo-acquisition dispatch, Item 2: this screen's own Camera/Import launch (inside
    // PullPhotoPickerScreen, reached from CartographyEntryEditScreen) is a new reachable state that
    // needed this exact guard for the first time — the device-check patch already fixed the
    // identical failure mode for find-editing (AvailabilityScreen's own latestPhotoAcquisitionInFlight,
    // suppressing its incidental-exit call), and PhotoAcquisitionLaunchers' own camera round trip
    // fires ON_STOP/ON_RESUME on the app's own camera launch, indistinguishable from real
    // backgrounding to this observer otherwise. Without this: launching the camera from inside a
    // dirty committed entry would set backgroundedWhileDirty on the resulting ON_STOP purely because
    // the camera app opened, then show the "Welcome back" prompt on return from a photo the user
    // just took, not from backgrounding. rememberSaveable to match backgroundedWhileDirty/
    // showReturnPrompt above, for the same reason: a real Activity recreation mid-capture must not
    // lose track of this either.
    var photoAcquisitionInFlight by rememberSaveable { mutableStateOf(false) }
    val returnPrompt = rememberReturnPromptState(
        entryDirty = editingEntry != null && !editingEntry.isDraft && uiState.hasUnsavedChanges,
        entryOpen = editingEntry != null,
        photoAcquisitionInFlight = photoAcquisitionInFlight,
    )

    // Journal redesign J2, T2 (plan J2, owner ruling "Full-screen list (Recommended)"): the Drafts
    // sub-tab became the banner below, and with more than one draft its Continue opens the
    // full-screen list in place of Entries. Whether the list is open is transient navigation state
    // (the dispatch's words): saveable, so a night-mode toggle or a fold keeps it, but held here, not
    // in JournalScreenState, so leaving the Journal tab closes it.
    //
    // Declared above the open-entry early return below (moved by the second J2 coder, owner ruling
    // "Back to the list (Recommended)", prompts/preserved/2026-09-27-19.md), so it outlives a draft
    // opened from the list: closing that draft lands back on the list, and Back from the list lands
    // on Entries. The first coder had it after the return, which forgot it and landed on Entries, as
    // the old Drafts sub-tab did. A draft opened from the banner (one draft) never sets it, so that
    // one still closes onto Entries. The list's own BackHandler stays after the return, so while a
    // draft is open the entry's handler above is the only one enabled here.
    var draftsListOpen by rememberSaveable { mutableStateOf(false) }

    // Motion Part 3 (scout J7): whether the open entry's editor has its album picker open. Held here, one per open entry as the
    // editor held it (`remember(entry.id)`), so the picker can be this screen's own page.
    val entryPullingPhotoState = remember(editingEntry?.id) { mutableStateOf(false) }
    var entryPullingPhoto by entryPullingPhotoState

    // The open entry's report or editor: drawn in place of the list, or, with a slot, in the wide
    // tree's right side. One definition for both, so the two can not drift.
    //
    // Motion Part 3: drawn from the page it is handed (the entry and the mode it shows), not from the screen's current state, so
    // a report or editor sliding out after its entry has closed still draws (motion/PageSlide.kt). The parameter is named
    // editingEntry on purpose, over the screen's own value, so every line below reads the page's entry.
    val entryDetail: @Composable (CartographyEntry, CartographyEntryMode, Modifier) -> Unit = { editingEntry, pageMode, contentModifier ->
        run {
            if (pageMode == CartographyEntryMode.EDIT) {
                CartographyEntryEditScreen(
                    entry = editingEntry,
                    candidates = uiState.candidatesForEditingEntry,
                    candidateOfflineRegions = uiState.candidateOfflineRegionsForEditingEntry,
                    isLoadingCandidates = uiState.isLoadingCandidates,
                    galleryPhotos = galleryPhotos,
                    distanceUnit = distanceUnit,
                    hasUnsavedChanges = uiState.hasUnsavedChanges,
                    showLeavePrompt = confirmingLeaveEntry,
                    onRequestBack = ::requestLeaveEntry,
                    onDismissLeavePrompt = { confirmingLeaveEntry = false },
                    onTextChanged = onTextChanged,
                    onTagsChanged = onTagsChanged,
                    onSetFindDecision = onSetFindDecision,
                    onSetTrackDecision = onSetTrackDecision,
                    onSetWaypointDecision = onSetWaypointDecision,
                    onSetOfflineRegionDecision = onSetOfflineRegionDecision,
                    onSetGroupIncluded = onSetEntryGroupIncluded,
                    openGroups = openEntryGroups,
                    onToggleGroupOpen = { group -> openEntryGroups = if (group in openEntryGroups) openEntryGroups - group else openEntryGroups + group },
                    onToggleKeptPhoto = onToggleKeptPhoto,
                    onOpenCamera = onOpenCameraForEntry,
                    onAcquirePhoto = onAcquirePhotoForEntry,
                    onAcquisitionInFlightChanged = { inFlight -> photoAcquisitionInFlight = inFlight },
                    onFinish = onFinishEntry,
                    onSave = { leavePromptResolved = true; onSaveEntry() },
                    onDiscardChanges = { leavePromptResolved = true; onDiscardEntryChanges() },
                    showReturnPrompt = returnPrompt.showReturnPrompt,
                    onContinueEditing = { returnPrompt.showReturnPrompt = false },
                    onCommit = { returnPrompt.showReturnPrompt = false; onSaveEntry() },
                    onSaveAsDraft = { returnPrompt.showReturnPrompt = false; onSaveEntryAsDraft() },
                    onDeleteEntry = { onDeleteEntry(editingEntry.id) },
                    onBack = ::closeEntryByUser,
                    modifier = contentModifier,
                    backEnabled = backEnabled,
                    // Motion Part 3 (scout J7): the album picker is this screen's own page, sliding in over the editor.
                    pullingPhotoState = entryPullingPhotoState,
                    drawsPhotoPicker = false,
                )
            } else {
                CartographyEntryReportScreen(
                    entry = editingEntry,
                    galleryPhotos = galleryPhotos,
                    distanceUnit = distanceUnit,
                    mapSlot = mapSlot,
                    night = night,
                    initialMapMode = initialMapMode,
                    getMapData = getMapData,
                    getCoveringOfflineRegion = getCoveringOfflineRegion,
                    getCurrentLocation = getCurrentLocation,
                    layersState = mapLayers,
                    onLayerVisibilityChanged = onMapLayerVisibilityChanged,
                    mapBubbleSources = mapBubbleSources,
                    onSetShownOnMap = onSetShownOnMap?.let { set -> { shown: Boolean -> set(editingEntry.id, shown) } },
                    candidates = uiState.candidatesForEditingEntry,
                    onEdit = { mode = CartographyEntryMode.EDIT },
                    onDeleteEntry = { onDeleteEntry(editingEntry.id) },
                    onBack = ::closeEntryByUser,
                    modifier = contentModifier,
                    backEnabled = backEnabled,
                )
            }
        }
    }
    // The list is where a closed draft returns to only while drafts remain (the option as the owner
    // was offered it: "return to the list while drafts remain"). Finishing the last one leaves no
    // list to return to, so the flag is dropped and the screen lands on Entries; dropping it, rather
    // than only hiding the list, keeps a later new draft from reopening a list nobody asked for.
    // Runs only at this screen's top level (no entry open, nothing loading), where it ran before motion Part 3 moved the
    // branches below into pages.
    val drafts = uiState.draftEntries
    val atTopLevel = editingEntry == null && !uiState.isLoadingCandidates
    LaunchedEffect(draftsListOpen, drafts.isEmpty(), atTopLevel) {
        if (atTopLevel && draftsListOpen && drafts.isEmpty()) draftsListOpen = false
    }
    val showDraftsList = draftsListOpen && drafts.isNotEmpty()

    val startNewEntry = { mode = CartographyEntryMode.EDIT; onStartEntry(LocalDate.now()) }
    // The L1 row's action on Entries' home, one remembered lambda per view, so publishing it to a caller that draws the row
    // above (Amendment 1) changes that caller's state only when the view changes, never on every composition.
    val latestStartNewEntry by rememberUpdatedState(startNewEntry)
    val newEntryAction = remember<@Composable () -> Unit> { { ShortWindowNewEntryButton(onClick = { latestStartNewEntry() }) } }
    // Remembered with no key, reading the launchers through rememberUpdatedState (RECORD -691): rememberPhotoAcquisitionLaunchers
    // builds a new PhotoAcquisitionLaunchers on every composition, so a lambda keyed on it was new every time, and publishing it
    // recomposed the Journal, which composed this screen again, without end (the album cases of the short-window tests at the
    // first build).
    val latestAlbumAcquisition by rememberUpdatedState(albumPhotoAcquisition)
    val addPhotoAction = remember<@Composable () -> Unit> {
        { ShortWindowAddPhotoButton(onTakePhoto = { latestAlbumAcquisition.launchCamera() }, onImport = { latestAlbumAcquisition.launchGallery() }) }
    }
    val homeHeaderAction: @Composable () -> Unit = when (entriesViewState.value) {
        EntriesViewMode.TIMELINE -> newEntryAction
        EntriesViewMode.ALBUM -> addPhotoAction
    }

    // Motion Part 3, item 1 (RECORD -651, Journal pages: "Slide in, slide back"; scout J3, J4): Entries, the drafts list, and an
    // open entry's report and editor are a stack of pages. An opened page slides in from the right over the one beneath, and
    // Back (or the arrow) slides it out to the right, uncovering it (motion/PageSlide.kt). Each branch is the one this screen
    // drew before, in the same order of precedence; their Back handlers moved into their pages, so each is composed only while
    // its page is the one showing, as before.
    val page: EntriesPage = when {
        editingEntry != null && mode == CartographyEntryMode.EDIT && entryPullingPhoto -> EntriesPage.PullPhoto(editingEntry)
        editingEntry != null -> EntriesPage.Entry(editingEntry, mode)
        uiState.isLoadingCandidates -> EntriesPage.Loading
        showDraftsList -> EntriesPage.Drafts
        else -> EntriesPage.Home
    }
    // Amendment 1: the action for the L1 row the caller draws above, the home's while home is the page, otherwise none. Written
    // after composition, and only when it changes.
    shortWindowHeaderAbove?.let { holder ->
        val published: (@Composable () -> Unit)? = if (page == EntriesPage.Home) homeHeaderAction else null
        SideEffect { if (holder.value !== published) holder.value = published }
    }

    // The editor's "+ Add a photo from the Album" picker (scout J7), as a page over the editor. Its Back step closes it, as the
    // editor's own handler did when the editor drew it (entry-photo-acquisition dispatch, Item 3); composed only while it is the
    // page showing, after this screen's entry handler, so it wins.
    val pullPhotoPage: @Composable () -> Unit = {
        BackHandler(enabled = backEnabled) { entryPullingPhoto = false }
        ShortWindowFrame(headerHere, action = null, modifier = Modifier) { contentModifier ->
            PullPhotoPickerScreen(
                photos = galleryPhotos,
                onPhotoSelected = { photo -> onToggleKeptPhoto(photo.id); entryPullingPhoto = false },
                onOpenCamera = onOpenCameraForEntry,
                onPhotoAcquired = onAcquirePhotoForEntry,
                onAcquisitionInFlightChanged = { inFlight -> photoAcquisitionInFlight = inFlight },
                modifier = contentModifier.fillMaxSize(),
            )
        }
    }

    // The drafts list (journal redesign J2, T2), with its own Back step: composed only while it is the page showing.
    val draftsPage: @Composable () -> Unit = {
        BackHandler(enabled = backEnabled) { draftsListOpen = false }
        ShortWindowFrame(headerHere, action = null, modifier = Modifier) { contentModifier ->
            DraftsListScreen(
                drafts = uiState.draftEntries,
                isLoading = uiState.isLoadingEntries,
                onOpenDraft = { id -> mode = CartographyEntryMode.EDIT; onOpenEntry(id) },
                onBack = { draftsListOpen = false },
                distanceUnit = distanceUnit,
                galleryPhotos = galleryPhotos,
                tracks = tracks,
                getSavedTrackPaths = getSavedTrackPaths,
                columns = columns,
                modifier = contentModifier,
                onDeleteDraft = onRequestDeleteEntry,
                // J4b L2: a draft's swipe Edit is its tap, the open-draft path above (a draft opens in EDIT).
                onEditDraft = { id -> mode = CartographyEntryMode.EDIT; onOpenEntry(id) },
                // J5, L4: the drafts list's cards turn sideways in a short window too.
                sideways = shortWindow,
            )
        }
    }

    // Entries' own top level: the timeline or the album, with the toolbar and the drafts banner. Composed only while it is the
    // page showing, as it was only reached at this top level before.
    val entriesHome: @Composable () -> Unit = {
    // Journal redesign J2, T3 (plan J3): the Album sub-tab became a view of Entries, switched by
    // the toolbar's toggle, and with Drafts already a banner (T2) the SecondaryTabRow is gone. The
    // view lives in JournalScreenState (entriesViewState), so it survives a tab change and a
    // restore. Back from the album steps to the timeline before anything else takes Back: this
    // handler is composed after JournalTab's, so it wins while enabled, and it is only composed
    // at this top level (no entry open, drafts list closed).
    var viewMode by entriesViewState
    BackHandler(enabled = backEnabled && viewMode == EntriesViewMode.ALBUM) { viewMode = EntriesViewMode.TIMELINE }

    // The banner's Continue, and in a short window the drafts chip's (J5, L3): one draft straight
    // into it, the Drafts sub-tab's open-draft path (a draft is unfinished work, so EDIT, never the
    // read-only view); more than one, the list.
    val continueDrafts = {
        if (drafts.size == 1) {
            mode = CartographyEntryMode.EDIT
            onOpenEntry(drafts.single().id)
        } else {
            draftsListOpen = true
        }
    }
    // J5, L3: the second row's hide-on-scroll state, fed by the content below it (nestedScroll).
    val secondRowScroll = rememberHideOnScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ENTRIES_HOME_TAG)
            .then(if (shortWindow) Modifier.nestedScroll(secondRowScroll.connection) else Modifier),
    ) {
        if (shortWindowHeader != null) {
            // J5, L1 and L2: the pinned row, with this view's action where the floating button was (drawn here unless the caller
            // draws it above the pages, Amendment 1).
            headerHere?.invoke(homeHeaderAction)
            // J5, L3: the drafts chip and the view toggle share one row, which hides while the
            // content scrolls down and returns on a scroll up.
            ShortWindowSecondRow(secondRowScroll) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    if (drafts.isNotEmpty()) ShortWindowDraftsChip(count = drafts.size, onClick = continueDrafts)
                    EntriesToolbar(viewMode = viewMode, onViewModeChange = { viewMode = it })
                }
            }
        } else {
            EntriesToolbar(viewMode = viewMode, onViewModeChange = { viewMode = it })

            if (drafts.isNotEmpty()) {
                DraftsBanner(count = drafts.size, onContinue = continueDrafts)
            }
        }

        if (uiState.candidatesErrorMessage != null) {
            Text(
                uiState.candidatesErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }

        // J2, T4 (plan J7): the floating button replaced the timeline's "+" tile and sits over
        // the content, bottom end; the timeline gets FAB_CLEARANCE of bottom padding so its last
        // row scrolls clear of it. The album draws its own "Add photo" button (EntriesAlbum's
        // AddPhotoButton, whose menu needs the album's photo launchers) with the same clearance.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Amendment 1 (RECORD -681, "Same rule everywhere"; scout J5): the album opens over the timeline, sliding in from the
            // right, and Back (or the toggle) slides it out again. The toolbar above stays still; the floating button below
            // belongs to the timeline and comes and goes with it, as before.
            PageSlide(targetState = viewMode, depthOf = { it.ordinal }, modifier = Modifier.fillMaxSize()) { shownView ->
            when (shownView) {
                EntriesViewMode.TIMELINE -> CartographyEntryListScreen(
                    entries = uiState.entries,
                    isLoading = uiState.isLoadingEntries,
                    onOpenEntry = { id -> mode = CartographyEntryMode.VIEW; onOpenEntry(id) },
                    emptyMessage = "No entries yet. Use New entry to start one.",
                    distanceUnit = distanceUnit,
                    galleryPhotos = galleryPhotos,
                    tracks = tracks,
                    getSavedTrackPaths = getSavedTrackPaths,
                    loadErrorMessage = uiState.loadErrorMessage,
                    columns = columns,
                    // No floating button in a short window (J5, L2), so nothing to clear.
                    bottomContentPadding = if (shortWindow) Spacing.lg else FAB_CLEARANCE,
                    modifier = Modifier.fillMaxSize(),
                    onDeleteEntry = onRequestDeleteEntry,
                    // J4b L2: Edit opens the editor the way the app already reaches it for an entry:
                    // open it with mode EDIT, as the drafts list does and as the report's own "Edit
                    // entry" does once the entry is open (both above in this file).
                    onEditEntry = { id -> mode = CartographyEntryMode.EDIT; onOpenEntry(id) },
                    // J5, L4: sideways cards with the long-press menu in a short window.
                    sideways = shortWindow,
                )

                EntriesViewMode.ALBUM -> EntriesAlbum(
                    photos = galleryPhotos,
                    isLoading = isLoadingGalleryPhotos,
                    onDeletePhoto = onDeleteGalleryPhoto,
                    photoAcquisition = albumPhotoAcquisition,
                    loadErrorMessage = galleryLoadErrorMessage,
                    cartographyEntryReferenceCounts = galleryPhotoEntryReferenceCounts,
                    draftFindIds = draftFindIds,
                    modifier = Modifier.fillMaxSize(),
                    onRequestDeletePhoto = onRequestDeleteGalleryPhoto,
                    // J5: 5 columns at 640 dp (L6), and the photo button is in the L1 row (L2).
                    columns = if (shortWindow) SHORT_WINDOW_ALBUM_COLUMNS else ALBUM_COLUMNS,
                    showAddPhotoButton = !shortWindow,
                )
            }
            }
            if (viewMode == EntriesViewMode.TIMELINE && !shortWindow) {
                // RECORD -751: the shared Journal button, which Finds' "New find" reuses (JournalNewItemButton's doc comment).
                JournalNewItemButton(
                    text = "New entry",
                    icon = Icons.Filled.Edit,
                    onClick = startNewEntry,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg).testTag(ENTRIES_FAB_TAG),
                )
            }
        }
    }
    }

    PageSlide(
        targetState = page,
        depthOf = { it.depth },
        contentKey = { it.kind },
        modifier = modifier.fillMaxSize(),
    ) { shown ->
        when (shown) {
            // Amendment 2 follow-up (the planner, after RECORD -682; scout J6): the spinner before a new entry's editor and the
            // editor are one page (no slide between them), and the spinner now crossfades into the editor, as every other
            // loading state does (motion/StateCrossfade.kt).
            is EntriesPage.Entry, EntriesPage.Loading -> StateCrossfade(
                targetState = shown,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it is EntriesPage.Loading },
            ) { state ->
                when (state) {
                    is EntriesPage.Entry -> ShortWindowFrame(headerHere, action = null, modifier = Modifier) { contentModifier ->
                        entryDetail(state.entry, state.mode, contentModifier)
                    }
                    EntriesPage.Loading -> ShortWindowFrame(headerHere, action = null, modifier = Modifier) { contentModifier ->
                        Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> Unit
                }
            }
            is EntriesPage.PullPhoto -> pullPhotoPage()
            EntriesPage.Drafts -> draftsPage()
            EntriesPage.Home -> entriesHome()
        }
    }
}

/**
 * Entries' own top level: present only while no entry is open, so a test can tell "Entries is
 * showing its list" apart from the Entries | Records switch label, which reads "Entries" whenever the
 * Journal is on screen (journal redesign J2, T1).
 */
internal const val ENTRIES_HOME_TAG = "entries-home"

/** Entries' floating button (J2, T4): "New entry" on the timeline. */
internal const val ENTRIES_FAB_TAG = "entries-fab"

/**
 * Bottom padding that lets the timeline's last row scroll clear of the floating button: the
 * button's 56 dp height plus its 16 dp margin, plus 16 dp of air above it. A fixed figure is right
 * here because the button sits inside this screen's own bounds, above the bottom bar, not over a
 * system inset (CLAUDE.md, the Robolectric insets pitfall: nothing here depends on a real inset).
 */
internal val FAB_CLEARANCE = 88.dp
