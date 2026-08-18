package com.forager.app.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles

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
 */
@Composable
internal fun LogPanel(
    uiState: MushroomLogUiState,
    cameraCaptureFiles: CameraCaptureFiles,
    onOpenEntry: (String) -> Unit,
    onCloseEntry: () -> Unit,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBackToSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = uiState.editingEntry
    if (editing != null) {
        LogEntryDetailScreen(
            entry = editing,
            cameraCaptureFiles = cameraCaptureFiles,
            onEntryChanged = onEntryChanged,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            onDeleteEntry = { onDeleteEntry(editing.id) },
            onBack = onCloseEntry,
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            LogHeader(onBack = onBackToSearch)
            LogEntryListScreen(
                entries = uiState.entries,
                isLoading = uiState.isLoadingEntries,
                onOpenEntry = onOpenEntry,
                modifier = Modifier.weight(1f),
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
