package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.theme.Spacing

/**
 * Workstream G3 (`docs/plans/pr26-rework.md`): the grid [LogEntryDetailScreen]'s "From Album"
 * button opens — every gallery photo, tap-to-pick, so an entry can *reference* one it doesn't
 * already own a copy of rather than importing a new file the way Camera/Import do. Uses
 * the same [DecodedPhoto] G2 built for exactly this extension, wrapped in its own clickable `Card`
 * — [DecodedPhoto] itself gains no selection affordance, per that component's own doc comment.
 *
 * Deliberately a separate composable from [PhotoGalleryScreen] rather than a "selection mode" on
 * it: that screen's own interaction is tap-to-delete (Workstream G3's own warn-then-remove flow),
 * and folding a second, incompatible interaction (tap-to-pick here) into the same component would
 * make both harder to read for a difference this small — a plain grid of `DecodedPhoto` tiles
 * either way, just with a different thing happening on tap.
 *
 * **Entry-photo-acquisition dispatch, Item 2:** Camera and Import buttons, alongside the pull
 * list rather than replacing it on an empty Album — this screen was pull-only since Stage 2b
 * ([AddPhotoToGalleryUseCase] existed precisely to create standalone photos, but nothing here
 * reached it). [onPhotoAcquired] is fired with the raw [PhotoSource]; composing "persist it" and
 * "attach it to whatever the user is standing in" is each caller's own job, not this screen's —
 * [JournalTab]'s own call site reuses `onAddPhoto`, `MushroomLogViewModel`'s already-correct
 * persist-attach-and-GPS-patch path for a find, unchanged; `CartographyScreen`'s composes across
 * two ViewModels instead (see its own `onAcquirePhotoForEntry` doc comment, and `MainActivity`'s
 * own call site, for why a plain `AddPhotoToGalleryUseCase` injected into `CartographyViewModel`
 * directly would have left `MushroomLogUiState.galleryPhotos` stale — `GetGalleryPhotosUseCase` is
 * a one-shot suspend call, not a reactive `Flow`). Reuses [rememberPhotoAcquisitionLaunchers]
 * unmodified, the same shared Camera-permission-then-capture and system-picker component
 * [LogEntryDetailScreen] and [PhotoGalleryScreen] already use — no second copy of that wiring.
 *
 * The buttons row is unconditional, above either the grid or the empty-state message, matching
 * [PhotoGalleryScreen]'s own shape (buttons above content, not swapped out when the list is
 * empty) — before this dispatch the empty state's own message sent the user elsewhere to find
 * buttons that, after this dispatch, sit right here.
 */
@Composable
internal fun PullPhotoPickerScreen(
    photos: List<GalleryPhoto>,
    onPhotoSelected: (LogPhoto) -> Unit,
    cameraCaptureFiles: CameraCaptureFiles,
    onPhotoAcquired: (PhotoSource) -> Unit,
    /** See [LogEntryDetailScreen]'s own `PhotosSection` — the identical camera/gallery-round-trip-vs-backgrounding conflation guard. [JournalTab]'s own call site reuses the exact same callback its `LogEntryDetailScreen` call already threads through, since the two are mutually exclusive branches of one `when`; [CartographyScreen] needed this guard newly built (see its own `photoAcquisitionInFlight` doc comment) — this is the first camera/gallery launch surface reachable from inside a Cartography entry. */
    onAcquisitionInFlightChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoAcquisition = rememberPhotoAcquisitionLaunchers(cameraCaptureFiles, onPhotoAcquired)
    LaunchedEffect(photoAcquisition.isAcquisitionInFlight) {
        onAcquisitionInFlightChanged(photoAcquisition.isAcquisitionInFlight)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(onClick = photoAcquisition.launchCamera) { Text("Camera") }
            Button(onClick = photoAcquisition.launchGallery) { Text("Import") }
        }

        if (photos.isEmpty()) {
            Text(
                "No photos in the Album yet. Use Camera or Import above to add one.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(photos, key = { it.photo.id }) { galleryPhoto ->
                Card(
                    onClick = { onPhotoSelected(galleryPhoto.photo) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = RoundedCornerShape(Spacing.sm),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DecodedPhoto(
                            relativePath = galleryPhoto.photo.relativePath,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
