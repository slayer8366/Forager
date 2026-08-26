package com.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import com.forager.app.domain.model.Region
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.CentrePinLocationPicker
import com.forager.app.ui.map.MapSlot

/**
 * The mushroom log's drawer destination — one of the ModalNavigationDrawer's panels in
 * `AvailabilityScreen`, reached the same way `DrawerPanel.Settings` is (see that file's
 * `DrawerPanel` enum and `SettingsEntryRow`'s call site). Kept in its own package rather than
 * folded into `AvailabilityScreen.kt` alongside `Settings`/`OfflineMaps`'s panels: this feature's
 * form is large enough (seven characteristic sections, photos, an edit flow) that adding it to an
 * already-long file would make both harder to read, and — unlike Settings/OfflineMaps — nothing
 * here needs `AvailabilityScreen`'s own private state, only the callbacks passed in below.
 *
 * [uiState].editingEntry is this panel's own list/detail navigation state — see
 * [MushroomLogUiState]'s doc comment.
 *
 * [mapSlot]/[region]/[basemap] exist purely to host [LogEntryDetailScreen]'s "Add Location" picker
 * (Workstream L4, `docs/plans/pr26-rework.md`) — [JournalTab]'s own identical addition, mirrored
 * here since both composables render the same [LogEntryDetailScreen]. This window class has no
 * entry-creation entry point of its own (`LogEntryListScreen`, this panel's list state, has no "+"
 * tile — see that composable's own doc comment); an entry only ever arrives here already created,
 * via the map's "Log a find" option, so the picker below only ever edits an existing entry's
 * location, never places one for a not-yet-created entry the way it once did.
 */
@Composable
internal fun LogPanel(
    uiState: MushroomLogUiState,
    cameraCaptureFiles: CameraCaptureFiles,
    mapSlot: MapSlot,
    region: Region,
    basemap: Basemap,
    /** Night mode for the location picker this hosts — see [CentrePinLocationPicker]. */
    night: Boolean = false,
    /**
     * Opens a row and, if it's a committed entry, immediately begins editing it — one atomic
     * ViewModel operation ([MushroomLogViewModel.onOpenEntryForEditing]), not this composable
     * calling separate open/start-editing callbacks itself. This panel has no report step (see its
     * own doc comment), so opening a row always means "edit it" — see
     * [MushroomLogViewModel.onOpenEntryForEditing]'s own doc comment for why that combination lives
     * in the ViewModel rather than being composed here from two calls.
     */
    onOpenEntryForEditing: (String) -> Unit,
    onCloseEntry: () -> Unit,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onSaveEntry: () -> Unit,
    onCancelEditing: () -> Unit,
    onLeaveEditingIncidentally: () -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBackToSearch: () -> Unit,
    /** Clears [MushroomLogUiState.saveErrorMessage] once its Toast (below) has shown — see [MushroomLogViewModel.onSaveErrorDismissed]. */
    onSaveErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same one-shot-per-transition Toast shape as CompactMapTab's startRecordingErrorMessage
    // effect (AvailabilityScreen.kt) — belief-changing per docs/error-presentation-spec.md, so it
    // is told rather than absorbed. Unlike that field, this one also clears itself right after
    // showing: saveErrorMessage has no dismiss affordance to clear it from (the spec calls the
    // Toast provisional, explicitly ruling one out), so "cleared on dismiss" means clearing it the
    // moment the one-shot Toast has been shown — the other half of "dismiss or next successful
    // save, whichever comes first" is the five write sites in MushroomLogViewModel clearing it
    // themselves on success.
    val context = LocalContext.current
    LaunchedEffect(uiState.saveErrorMessage) {
        uiState.saveErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onSaveErrorDismissed()
        }
    }

    var pickingLocationForEditingEntry by remember { mutableStateOf(false) }
    // Same shape as pickingLocationForEditingEntry, for LogEntryDetailScreen's "From Album" button
    // (Workstream G3) instead of "Add Location".
    var pullingPhotoForEditingEntry by remember { mutableStateOf(false) }
    val editing = uiState.editingEntry

    // This panel had no BackHandler before pickers existed — nothing here previously needed to
    // intercept system back, since the drawer's own chrome handled it. Workstream L4b adds the
    // editing != null branch: back out of an open entry (with no picker open) is "leaving without
    // answering," the same incidental-exit auto-save as the form's own back arrow, a tab switch, or
    // backgrounding — never Cancel, which only the form's explicit button triggers. This closes a
    // gap the L4b scoping pulse found: this panel previously had no way to close an open entry via
    // back at all, falling through to whatever AvailabilityScreen's top-level handling did instead.
    BackHandler(enabled = editing != null || pickingLocationForEditingEntry || pullingPhotoForEditingEntry) {
        when {
            pickingLocationForEditingEntry -> pickingLocationForEditingEntry = false
            pullingPhotoForEditingEntry -> pullingPhotoForEditingEntry = false
            editing != null -> onLeaveEditingIncidentally()
        }
    }
    if (editing != null && pickingLocationForEditingEntry) {
        CentrePinLocationPicker(
            mapSlot = mapSlot,
            region = region,
            basemap = basemap,
            night = night,
            onConfirm = { location ->
                pickingLocationForEditingEntry = false
                onEntryChanged(editing.copy(foundAt = location))
            },
            onCancel = { pickingLocationForEditingEntry = false },
            modifier = modifier,
        )
    } else if (editing != null && pullingPhotoForEditingEntry) {
        PullPhotoPickerScreen(
            photos = uiState.galleryPhotos,
            onPhotoSelected = { photo ->
                pullingPhotoForEditingEntry = false
                onPullPhoto(photo)
            },
            modifier = modifier,
        )
    } else if (editing != null) {
        LogEntryDetailScreen(
            entry = editing,
            cameraCaptureFiles = cameraCaptureFiles,
            onEntryChanged = onEntryChanged,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            onPullPhoto = { pullingPhotoForEditingEntry = true },
            onAddLocation = { pickingLocationForEditingEntry = true },
            onSave = onSaveEntry,
            onCancel = onCancelEditing,
            onDeleteEntry = { onDeleteEntry(editing.id) },
            onBack = onLeaveEditingIncidentally,
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            LogHeader(onBack = onBackToSearch)
            LogEntryListScreen(
                entries = uiState.entries,
                draftEntries = uiState.draftEntries,
                isLoading = uiState.isLoadingEntries,
                // This panel has no separate report step (see its own doc comment) — opening an
                // entry goes straight to LogEntryDetailScreen below, so it must already be a draft
                // by the time that happens. onOpenEntryForEditing is a no-op-shaped success for a
                // row already a draft and creates the draft row for a committed one — correct for
                // either case on its own, so LogEntryListScreen's onOpenDraftEntry can default to
                // this same callback (its own default) rather than needing an override here.
                onOpenEntry = onOpenEntryForEditing,
                modifier = Modifier.weight(1f),
                loadErrorMessage = uiState.loadErrorMessage,
            )
        }
    }
}

/** Mirrors `AvailabilityScreen`'s `SettingsHeader` shape exactly — see that composable's own call site for why. */
@Composable
private fun LogHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = LogSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to search options")
        Text("Mushroom Log", style = MaterialTheme.typography.titleMedium)
    }
}
