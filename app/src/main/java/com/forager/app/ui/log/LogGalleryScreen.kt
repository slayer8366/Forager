package com.forager.app.ui.log

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * True when any of [entry]'s characteristic fields are still [Observed.NotObserved]/
 * [Feature.NotObserved] — mirrors [LogEntryListScreen]'s private copy of the same check
 * ([MushroomLogEntry.hasUnrecordedFields] there is `private` to that file, so this is a second
 * copy rather than a shared one; both read the same seven sections and would need to change
 * together if a new one is ever added).
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
 * The Journal bottom-nav destination's gallery: a grid of every logged entry plus one more tile —
 * always first, so it's never scrolled past — that starts a new one. Replaces
 * [LogEntryListScreen]'s plain list for the compact bottom nav (see [JournalTab]'s doc comment for
 * why that composable, and the drawer-hosted [LogPanel] it belongs to, stay untouched for the
 * medium/expanded window instead of being reused here).
 */
@Composable
internal fun LogGalleryScreen(
    entries: List<MushroomLogEntry>,
    isLoading: Boolean,
    onOpenEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading && entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(LogSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LogSpacing.sm),
    ) {
        item { AddEntryTile(onClick = onAddEntry) }
        items(entries, key = { it.id }) { entry ->
            LogEntryTile(entry = entry, onClick = { onOpenEntry(entry.id) })
        }
    }
}

/**
 * The gallery's "start a new entry" tile — a blank journal-entry outline with a centered `+`, per
 * the project owner's own description of it, so it reads as "add" the same way an empty photo slot
 * does in a picker grid, rather than as one more entry among the real ones.
 */
@Composable
private fun AddEntryTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GALLERY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(LogSpacing.sm),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New log entry",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(ADD_TILE_ICON_SIZE_DP.dp),
            )
        }
    }
}

/** One logged find in the gallery grid — a cover photo when one exists, otherwise a placeholder icon. */
@Composable
private fun LogEntryTile(entry: MushroomLogEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GALLERY_TILE_ASPECT_RATIO),
        shape = RoundedCornerShape(LogSpacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                val coverPhoto = entry.photos.firstOrNull()
                if (coverPhoto != null) {
                    GalleryCoverThumbnail(relativePath = coverPhoto.relativePath)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ADD_TILE_ICON_SIZE_DP.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(LogSpacing.sm)) {
                Text(
                    "Find on ${entry.foundOn}",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.hasUnrecordedFields()) {
                    Text(
                        "Incomplete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Same decode-a-thumbnail-off-disk pattern [LogEntryDetailScreen]'s own photo thumbnail uses. */
@Composable
private fun GalleryCoverThumbnail(relativePath: String) {
    val context = LocalContext.current
    var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(relativePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = GALLERY_THUMBNAIL_SAMPLE_SIZE }
                BitmapFactory.decodeFile(File(context.filesDir, relativePath).absolutePath, options)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = "Log photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** A little taller than wide, reading as a card/page rather than a square photo tile. */
private const val GALLERY_TILE_ASPECT_RATIO = 0.85f
private const val ADD_TILE_ICON_SIZE_DP = 40
private const val GALLERY_THUMBNAIL_SAMPLE_SIZE = 4
