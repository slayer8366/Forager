package com.forager.app.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.Role
import com.forager.app.R
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.ui.theme.Spacing

/**
 * True when any of [entry]'s characteristic fields are still [Observed.NotObserved]/
 * [Feature.NotObserved] — the list row's "Incomplete" cue, so a half-finished entry (see
 * [MushroomLogEntry]'s doc comment on deferred observation) reads as half-finished while browsing,
 * not only once opened.
 */
private fun MushroomLogEntry.hasUnrecordedFields(): Boolean =
    cap.shape is Observed.NotObserved ||
        cap.surface is Observed.NotObserved ||
        cap.decorations is Feature.NotObserved ||
        cap.margin is Observed.NotObserved ||
        hymenophore.details is Observed.NotObserved ||
        stipe.details is Observed.NotObserved ||
        veil.annulus is Feature.NotObserved ||
        veil.volva is Feature.NotObserved ||
        contextFlesh.texture is Observed.NotObserved ||
        contextFlesh.colorChangeOnCutting is Feature.NotObserved ||
        contextFlesh.exudate is Feature.NotObserved ||
        sporePrint.details is Observed.NotObserved ||
        hostSubstrate.association is Observed.NotObserved ||
        hostSubstrate.forestType is Observed.NotObserved ||
        hostSubstrate.hostHealth is Observed.NotObserved

/**
 * **Log / Drafts toggle (Workstream L4b-R, owner decision 2026-08-25):** see [LogGalleryScreen]'s
 * identical toggle for the full reasoning — a filter on this same screen, not a separate
 * destination, selecting [entries] or [draftEntries] exclusively.
 */
@Composable
internal fun LogEntryListScreen(
    entries: List<MushroomLogEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Every current draft — see [MushroomLogUiState.draftEntries]'s own doc comment — shown only when the Drafts tab is selected, each with a "Draft" row instead of "Incomplete." */
    draftEntries: List<MushroomLogEntry> = emptyList(),
    onOpenDraftEntry: (String) -> Unit = onOpenEntry,
    /**
     * Set when the last load failed — see [MushroomLogViewModel.loadEntries]'s `onFailure` branch.
     * Not belief-changing (the entries are on disk; only the read failed), so this never hides
     * [entries] that are already showing — only replaces the "nothing logged yet" empty state when
     * there is nothing to show *because* the read failed, per docs/error-presentation-spec.md.
     */
    loadErrorMessage: String? = null,
) {
    var showingDrafts by remember { mutableStateOf(false) }
    val visibleEntries = if (showingDrafts) draftEntries else entries

    Column(modifier = modifier.fillMaxWidth()) {
        SecondaryTabRow(selectedTabIndex = if (showingDrafts) 1 else 0) {
            Tab(selected = !showingDrafts, onClick = { showingDrafts = false }, text = { Text("Log") })
            Tab(
                selected = showingDrafts,
                onClick = { showingDrafts = true },
                text = { Text(if (draftEntries.isEmpty()) "Drafts" else "Drafts (${draftEntries.size})") },
            )
        }
        when {
            isLoading -> Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
                CircularProgressIndicator()
            }

            visibleEntries.isNotEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (showingDrafts) {
                    visibleEntries.forEach { entry -> LogEntryRow(entry = entry, onClick = { onOpenDraftEntry(entry.id) }, isDraft = true) }
                } else {
                    // Most-recently-found first — see GetMushroomLogEntriesUseCase; this renders
                    // that order rather than recomputing it.
                    visibleEntries.forEach { entry -> LogEntryRow(entry = entry, onClick = { onOpenEntry(entry.id) }) }
                }
            }

            loadErrorMessage != null && !showingDrafts -> Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )

            showingDrafts -> Text(
                "No drafts. Unsaved edits show up here.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )

            else -> Text(
                "No finds logged yet. Tap the add button on the map to log one.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
        }
    }
}

/** [isDraft] renders a "Draft" row instead of (never alongside) "Incomplete" — see [LogGalleryScreen]'s identical [LogEntryTile] parameter for why. */
@Composable
private fun LogEntryRow(entry: MushroomLogEntry, onClick: () -> Unit, isDraft: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Find on ${entry.foundOn}", style = MaterialTheme.typography.bodyLarge)
            Text(
                entry.foundAt?.let { location -> "${"%.4f".format(location.lat)}, ${"%.4f".format(location.lng)}" }
                    ?: stringResource(R.string.log_entry_no_location),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isDraft) {
                Text(
                    "Draft — not yet saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontStyle = FontStyle.Italic,
                )
            } else if (entry.hasUnrecordedFields()) {
                Text(
                    "Incomplete — some fields not yet recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
        if (entry.photos.isNotEmpty()) {
            Text("${entry.photos.size} photo${if (entry.photos.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
