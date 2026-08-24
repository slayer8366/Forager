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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.Role
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed

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

@Composable
internal fun LogEntryListScreen(
    entries: List<MushroomLogEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Set when the last load failed — see [MushroomLogViewModel.loadEntries]'s `onFailure` branch.
     * Not belief-changing (the entries are on disk; only the read failed), so this never hides
     * [entries] that are already showing — only replaces the "nothing logged yet" empty state when
     * there is nothing to show *because* the read failed, per docs/error-presentation-spec.md.
     */
    loadErrorMessage: String? = null,
) {
    when {
        isLoading -> Column(modifier = modifier.fillMaxWidth().padding(LogSpacing.lg)) {
            CircularProgressIndicator()
        }

        entries.isNotEmpty() -> Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LogSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LogSpacing.sm),
        ) {
            // Most-recently-found first — see GetMushroomLogEntriesUseCase; this renders that
            // order rather than recomputing it.
            entries.forEach { entry -> LogEntryRow(entry = entry, onClick = { onOpenEntry(entry.id) }) }
        }

        loadErrorMessage != null -> Text(
            loadErrorMessage,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(LogSpacing.lg),
        )

        else -> Text(
            "No finds logged yet. Tap the add button on the map to log one.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(LogSpacing.lg),
        )
    }
}

@Composable
private fun LogEntryRow(entry: MushroomLogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = LogSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Find on ${entry.foundOn}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${"%.4f".format(entry.foundAt.lat)}, ${"%.4f".format(entry.foundAt.lng)}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (entry.hasUnrecordedFields()) {
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
