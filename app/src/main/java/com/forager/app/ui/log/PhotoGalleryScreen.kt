package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.forager.app.domain.model.GalleryPhoto
import java.time.Instant
import java.time.ZoneId

/**
 * The photo gallery — Workstream G2 (`docs/plans/pr26-rework.md`): every photo the user has ever
 * added, independent of any entry, now that G1 made a photo a first-class gallery row rather than
 * an entry's own possession. Workstream G3 adds this screen's one interactive affordance —
 * deleting a photo, warn-then-remove — the only place a photo can be deleted from, per the
 * standing rule this repo's `CLAUDE.md` states. Pulling a photo into an entry is
 * [PullPhotoPickerScreen]'s job instead, reached from the entry side, not from here.
 *
 * A top-level destination on *both* window classes (owner decision, 2026-08-22: "the gallery is a
 * place the user goes, not a mode hidden inside the journal") — [CompactTab.PHOTOS] for compact,
 * [DrawerPanel.PhotoGallery] for medium/expanded, in `AvailabilityScreen.kt` — rather than a branch
 * inside [JournalTab]/[LogPanel], which would need the identical dual-file wiring L4a's Add
 * Location button already paid for once.
 *
 * [photos] can contain a [GalleryPhoto] with an empty [GalleryPhoto.referencingEntryIds] — a real,
 * reachable state (see that type's own doc comment), not a hypothetical one this screen can assume
 * away. [GalleryPhotoTile]'s own confirmation dialog reads that count directly: warn-then-remove
 * for a referenced photo, a plain "delete this photo?" with no entries-count line for an
 * unreferenced one — there is nothing to warn about a zero count changing.
 */
@Composable
internal fun PhotoGalleryScreen(
    photos: List<GalleryPhoto>,
    isLoading: Boolean,
    onDeletePhoto: (GalleryPhoto) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when the last load failed — see [LogGalleryScreen]'s identical parameter for why this never hides [photos] already showing, only shown when there is nothing to show because the read failed. */
    loadErrorMessage: String? = null,
) {
    if (isLoading && photos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when {
        photos.isEmpty() && loadErrorMessage != null -> Text(
            loadErrorMessage,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(LogSpacing.lg),
        )

        // Mirrors LogEntryListScreen's own "nothing logged yet" empty state (wording, styling, and
        // priority-ordering after the loading/error branches) — the closest existing precedent for
        // an empty state in this codebase; see the G2 scoping pulse's own findings on why
        // LogGalleryScreen's own "+"-tile-always-shown shape has no equivalent text to copy instead.
        photos.isEmpty() -> Text(
            "No photos yet. Add one from a log entry's Photos section.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(LogSpacing.lg),
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(LogSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LogSpacing.sm),
        ) {
            items(photos, key = { it.photo.id }) { galleryPhoto ->
                GalleryPhotoTile(galleryPhoto, onDelete = { onDeletePhoto(galleryPhoto) })
            }
        }
    }
}

@Composable
private fun GalleryPhotoTile(galleryPhoto: GalleryPhoto, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var confirmingDelete by remember(galleryPhoto.photo.id) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(GALLERY_PHOTO_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(LogSpacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                DecodedPhoto(relativePath = galleryPhoto.photo.relativePath, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete this photo")
                }
            }
            Text(
                // Do not fabricate a timestamp for a migrated photo — its real creation time isn't
                // knowable (see LogPhoto.createdAtEpochMillis's own doc comment), so this reads
                // "Date unknown" rather than inventing a date that would look just as legitimate as
                // a real one.
                galleryPhoto.photo.createdAtEpochMillis?.let(::photoDateLabel) ?: "Date unknown",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(LogSpacing.sm),
            )
        }
    }

    if (confirmingDelete) {
        val referencedCount = galleryPhoto.referencingEntryIds.size
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this photo?") },
            text = {
                Text(
                    // No entries-count line for an unreferenced photo (owner decision, 2026-08-22:
                    // "if nothing references it, no warning is needed") — there is nothing for the
                    // count to warn about at zero. Still a confirmation either way: deletion is
                    // irreversible regardless of how many entries are affected.
                    if (referencedCount > 0) {
                        "This photo is used in $referencedCount ${if (referencedCount == 1) "entry" else "entries"}. " +
                            "Deleting it will remove it from ${if (referencedCount == 1) "that entry" else "all of them"} too."
                    } else {
                        "This photo isn't used in any entry."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** The device's local calendar date for [epochMillis], as a plain ISO string — the same "just show LocalDate.toString()" convention [LogEntryDetailScreen]'s/[LogEntryListScreen]'s own "Find on ${entry.foundOn}" text already uses, rather than introducing a new date-formatting pattern for this one screen. */
private fun photoDateLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** Matches [LogGalleryScreen]'s own tile aspect ratio — the same grid shape, a photo where that one shows a cover-photo-or-placeholder card. */
private const val GALLERY_PHOTO_TILE_ASPECT_RATIO = 0.85f
