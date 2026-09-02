package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
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
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/**
 * The photo gallery — Workstream G2 (`docs/plans/pr26-rework.md`): every photo the user has ever
 * added, independent of any entry, now that G1 made a photo a first-class gallery row rather than
 * an entry's own possession. Workstream G3 added this screen's deletion affordance — warn-then-
 * remove, the only place a photo can be deleted from, per the standing rule this repo's `CLAUDE.md`
 * states.
 *
 * **Camera and Gallery live here too, as of the standalone-photos dispatch.** A photo acquired
 * through either button has no owning find — Album is "a place you add to, not just a place things
 * appear" (the dispatch's own framing: "Album photos are moments, not data"). Reuses
 * [rememberPhotoAcquisitionLaunchers] — the exact same `ActivityResultContracts`/
 * [CameraCaptureFiles]/permission wiring [LogEntryDetailScreen]'s `PhotosSection` already uses for
 * a find's own photos, not a second implementation of it. Pulling an *existing* gallery photo into
 * an entry is still [PullPhotoPickerScreen]'s separate job, reached from the entry side.
 *
 * Originally a top-level destination on *both* window classes (owner decision, 2026-08-22: "the
 * gallery is a place the user goes, not a mode hidden inside the journal") — a standalone
 * `CompactTab.PHOTOS` for compact, [DrawerPanel.PhotoGallery] for medium/expanded, in
 * `AvailabilityScreen.kt`. Map/navigation redesign dispatch B folded the compact side into what
 * was then `LogGalleryScreen`'s own Album tab (this composable embedded unchanged as that tab's
 * content, now [CartographyScreen]'s own Album tab after the Stage 2b follow-up) —
 * [DrawerPanel.PhotoGallery] is untouched, so the medium/expanded window still reaches this
 * directly. **Both call sites render the identical composable, so Camera/Gallery land on both
 * Album surfaces at once** — consistent with the dispatch's own general "Album is a place you add
 * to" framing, not compact-only, so this is not treated as an asymmetry to design around.
 *
 * [photos] can contain a [GalleryPhoto] with an empty [GalleryPhoto.referencingEntryIds] — a real,
 * reachable state (see that type's own doc comment; every photo acquired here starts this way), not
 * a hypothetical one this screen can assume away. [GalleryPhotoTile]'s own confirmation dialog
 * reads that count directly: warn-then-remove for a referenced photo, a plain "delete this photo?"
 * with no entries-count line for an unreferenced one — there is nothing to warn about a zero count
 * changing.
 */
@Composable
internal fun PhotoGalleryScreen(
    photos: List<GalleryPhoto>,
    isLoading: Boolean,
    onDeletePhoto: (GalleryPhoto) -> Unit,
    cameraCaptureFiles: CameraCaptureFiles,
    /** A photo acquired via Camera or Gallery here — persisted and added to the gallery, standalone, never attached to anything. See [AddPhotoToGalleryUseCase]'s own doc comment. */
    onAddGalleryPhoto: (PhotoSource) -> Unit,
    modifier: Modifier = Modifier,
    /** Set when the last load failed — see [LogGalleryScreen]'s identical parameter for why this never hides [photos] already showing, only shown when there is nothing to show because the read failed. */
    loadErrorMessage: String? = null,
    /** How many Cartography entries currently keep each photo (by id) attached — Journal Stage 2b's 4b deletion warning, extended to photos. Shown in [GalleryPhotoTile]'s own confirm dialog alongside the existing find-reference count. */
    cartographyEntryReferenceCounts: Map<String, Int> = emptyMap(),
) {
    val photoAcquisition = rememberPhotoAcquisitionLaunchers(cameraCaptureFiles, onAddGalleryPhoto)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(onClick = photoAcquisition.launchCamera) { Text("Camera") }
            Button(onClick = photoAcquisition.launchGallery) { Text("Gallery") }
        }

        if (isLoading && photos.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        when {
            photos.isEmpty() && loadErrorMessage != null -> Text(
                loadErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )

            // Mirrors LogEntryListScreen's own former "nothing logged yet" empty state (wording,
            // styling, and priority-ordering after the loading/error branches) — the closest
            // existing precedent for an empty state in this codebase.
            photos.isEmpty() -> Text(
                "No photos yet. Use Camera or Gallery above to add one.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(photos, key = { it.photo.id }) { galleryPhoto ->
                    GalleryPhotoTile(
                        galleryPhoto,
                        onDelete = { onDeletePhoto(galleryPhoto) },
                        cartographyEntryCount = cartographyEntryReferenceCounts[galleryPhoto.photo.id] ?: 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryPhotoTile(
    galleryPhoto: GalleryPhoto,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    cartographyEntryCount: Int = 0,
) {
    var confirmingDelete by remember(galleryPhoto.photo.id) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().aspectRatio(GALLERY_PHOTO_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(Spacing.sm),
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
                modifier = Modifier.padding(Spacing.sm),
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
                    // No count line at all for a photo referenced by neither a find nor a
                    // Cartography entry (owner decision, 2026-08-22: "if nothing references it, no
                    // warning is needed") — there is nothing to warn about at zero either way.
                    buildList {
                        if (referencedCount > 0) {
                            add(
                                "This photo is used in $referencedCount ${if (referencedCount == 1) "entry" else "entries"}. " +
                                    "Deleting it will remove it from ${if (referencedCount == 1) "that entry" else "all of them"} too.",
                            )
                        }
                        if (cartographyEntryCount > 0) {
                            // Journal Stage 2b, 4b extended to photos: a wordless entry can consist
                            // mostly of attached photos, so this deserves the same warning
                            // track/waypoint/offline-region deletion gets. No permanence claim — see
                            // OfflineRegionsSection's identical dialog for why.
                            add(
                                "This photo appears in $cartographyEntryCount ${if (cartographyEntryCount == 1) "journal entry" else "journal entries"}.",
                            )
                        }
                        if (isEmpty()) add("This photo isn't used in any entry.")
                    }.joinToString(" "),
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
