package com.forager.app.ui.log

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LogPhoto
import com.forager.app.ui.theme.Spacing

/**
 * Workstream G3 (`docs/plans/pr26-rework.md`): the grid [LogEntryDetailScreen]'s "From Album"
 * button opens — every gallery photo, tap-to-pick, so an entry can *reference* one it doesn't
 * already own a copy of rather than importing a new file the way Camera/system Gallery do. Uses
 * the same [DecodedPhoto] G2 built for exactly this extension, wrapped in its own clickable `Card`
 * — [DecodedPhoto] itself gains no selection affordance, per that component's own doc comment.
 *
 * Deliberately a separate composable from [PhotoGalleryScreen] rather than a "selection mode" on
 * it: that screen's own interaction is tap-to-delete (Workstream G3's own warn-then-remove flow),
 * and folding a second, incompatible interaction (tap-to-pick here) into the same component would
 * make both harder to read for a difference this small — a plain grid of `DecodedPhoto` tiles
 * either way, just with a different thing happening on tap.
 */
@Composable
internal fun PullPhotoPickerScreen(
    photos: List<GalleryPhoto>,
    onPhotoSelected: (LogPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) {
        Text(
            "No photos in the gallery yet. Add one with Camera or Gallery first.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
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
