package com.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * Cartography, unified — Journal Stage 2b, owner decision #3: **one implementation, responsive
 * layout**, not two composables. [JournalTab] (compact) and [LogPanel] (expanded) both host this
 * same composable for their Cartography tab; the only thing that varies between them is [columns]
 * (more grid columns on expanded — "more of the same thing at once," not a different arrangement).
 *
 * Three submenus — **Entries**, **Drafts**, **Album** — the same flat `SecondaryTabRow` shape
 * [RecordsTab] established for Stage 1, no header, no back arrow. Entries/Drafts are both
 * [CartographyEntryListScreen] over the two halves of [CartographyUiState] ([CartographyUiState.entries]/
 * [CartographyUiState.draftEntries]); Album is the pre-existing, unmodified [PhotoGalleryScreen] —
 * unaffected by either amendment, since it was never part of the Entries/Drafts ambiguity they
 * resolved.
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
 * [com.forager.app.domain.model.MushroomLogEntry]'s report/edit split — not part of
 * [CartographyUiState], since which of the two screens the *user* sees for [uiState].editingEntry
 * depends on how they got there, not on anything inferable from the entry's own content (mirroring
 * [JournalEntryMode]'s own doc comment on that exact point). Every call site that can set
 * [CartographyUiState.editingEntry] non-null sets [mode] explicitly in the same action: the Entries
 * tab's own [onOpenEntry] sets [CartographyEntryMode.VIEW] (there is something to recount, and no
 * reason to assume an edit is wanted); the Drafts tab's, and starting a brand-new entry, set
 * [CartographyEntryMode.EDIT] (a draft is unfinished work — sending the user to a read-only view of
 * it would be wrong); [CartographyEntryReportScreen]'s own "Edit entry" menu item sets
 * [CartographyEntryMode.EDIT] too. No branch here reads [com.forager.app.domain.model.CartographyEntry.isDraft]
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
    cameraCaptureFiles: CameraCaptureFiles,
    onAddGalleryPhoto: (PhotoSource) -> Unit,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    basemap: Basemap,
    night: Boolean,
    getMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    getCoveringOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
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
    onFinishEntry: () -> Unit,
    /** Explicit Save for a committed entry — device-check patch, Item 1. See [CartographyEntryEditScreen]'s own doc comment on the Save/Discard/Cancel policy. */
    onSaveEntry: () -> Unit,
    /** The leave-prompt's Discard option. See [CartographyEntryEditScreen]'s own doc comment. */
    onDiscardEntryChanges: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Grid column count for the Entries/Drafts lists — 2 for compact, more for expanded/tablet. */
    columns: Int = 2,
) {
    var mode by remember { mutableStateOf(CartographyEntryMode.VIEW) }

    // Back-nav-and-save-flow dispatch, Item 1: system back on an open entry used to reach no
    // BackHandler at all — AvailabilityScreen's own comment on its outer four assumed "a Journal
    // entry... unwinds itself first via its own local BackHandler," but no such handler existed
    // here, so back fell straight through to that outer go-home one regardless of depth, on a
    // draft exactly as much as a committed entry. One definition here, not duplicated into
    // JournalTab/LogPanel: both simply host this composable, so this covers both window classes
    // for free — see this file's own class doc comment on why [JournalTab]/[LogPanel] share this
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

    fun requestLeaveEntry() {
        if (mode == CartographyEntryMode.EDIT && editingEntry != null && !editingEntry.isDraft && uiState.hasUnsavedChanges) {
            confirmingLeaveEntry = true
        } else {
            onCloseEntry()
        }
    }

    // Enabled only while an entry is open — disabled the instant editingEntry is null, so back at
    // this screen's own top level (the Entries/Drafts/Album tabs) falls straight through to
    // whatever's next (JournalTab/LogPanel's own selectedTopTab step, then AvailabilityScreen's
    // go-home). That fallthrough is the fix's whole point: this adds a step before go-home, it
    // never replaces it — a Journal back could never exit at all would be worse than the bug this
    // dispatch reports.
    BackHandler(enabled = editingEntry != null) {
        requestLeaveEntry()
    }

    if (editingEntry != null) {
        if (mode == CartographyEntryMode.EDIT) {
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
                onToggleKeptPhoto = onToggleKeptPhoto,
                onFinish = onFinishEntry,
                onSave = onSaveEntry,
                onDiscardChanges = onDiscardEntryChanges,
                onDeleteEntry = { onDeleteEntry(editingEntry.id) },
                onBack = onCloseEntry,
                modifier = modifier.fillMaxSize(),
            )
        } else {
            CartographyEntryReportScreen(
                entry = editingEntry,
                galleryPhotos = galleryPhotos,
                distanceUnit = distanceUnit,
                mapSlot = mapSlot,
                basemap = basemap,
                night = night,
                getMapData = getMapData,
                getCoveringOfflineRegion = getCoveringOfflineRegion,
                onEdit = { mode = CartographyEntryMode.EDIT },
                onDeleteEntry = { onDeleteEntry(editingEntry.id) },
                onBack = onCloseEntry,
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }

    if (uiState.isLoadingCandidates) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var selectedTab by remember { mutableStateOf(CartographyTab.ENTRIES) }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(selected = selectedTab == CartographyTab.ENTRIES, onClick = { selectedTab = CartographyTab.ENTRIES }, text = { Text("Entries") })
            Tab(
                selected = selectedTab == CartographyTab.DRAFTS,
                onClick = { selectedTab = CartographyTab.DRAFTS },
                text = { Text(if (uiState.draftEntries.isEmpty()) "Drafts" else "Drafts (${uiState.draftEntries.size})") },
            )
            Tab(selected = selectedTab == CartographyTab.ALBUM, onClick = { selectedTab = CartographyTab.ALBUM }, text = { Text("Album") })
        }

        if (uiState.candidatesErrorMessage != null) {
            Text(
                uiState.candidatesErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }

        when (selectedTab) {
            CartographyTab.ENTRIES -> CartographyEntryListScreen(
                entries = uiState.entries,
                isLoading = uiState.isLoadingEntries,
                onOpenEntry = { id -> mode = CartographyEntryMode.VIEW; onOpenEntry(id) },
                onAddEntry = { mode = CartographyEntryMode.EDIT; onStartEntry(LocalDate.now()) },
                loadErrorMessage = uiState.loadErrorMessage,
                columns = columns,
                modifier = Modifier.weight(1f),
            )

            CartographyTab.DRAFTS -> CartographyEntryListScreen(
                entries = uiState.draftEntries,
                isLoading = uiState.isLoadingEntries,
                onOpenEntry = { id -> mode = CartographyEntryMode.EDIT; onOpenEntry(id) },
                columns = columns,
                modifier = Modifier.weight(1f),
            )

            CartographyTab.ALBUM -> PhotoGalleryScreen(
                photos = galleryPhotos,
                isLoading = isLoadingGalleryPhotos,
                onDeletePhoto = onDeleteGalleryPhoto,
                cameraCaptureFiles = cameraCaptureFiles,
                onAddGalleryPhoto = onAddGalleryPhoto,
                loadErrorMessage = galleryLoadErrorMessage,
                cartographyEntryReferenceCounts = galleryPhotoEntryReferenceCounts,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Which of Cartography's three submenus is selected — ordinal order matches display order. */
private enum class CartographyTab { ENTRIES, DRAFTS, ALBUM }

/** Which screen [CartographyScreen] shows for [CartographyUiState.editingEntry] — Journal Stage 2c. See this file's own doc comment, "Tap opens the view, not the editor," for the full reasoning. */
internal enum class CartographyEntryMode { VIEW, EDIT }
