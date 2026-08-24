package com.forager.app.ui.log

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forager.app.R
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.photo.ContentUriPhotoSource
import com.forager.app.ui.availability.CollapsibleSection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The entry's detail/edit form — one screen for both, since [entry] is already persisted by the
 * time this shows (see [MushroomLogViewModel.onStartNewEntry]): "creating" and "editing" are the
 * same action here, autosaving through [onEntryChanged] on every field change. Each characteristic
 * section is a [CollapsibleSection] (reused from `AvailabilityScreen`) so the form doesn't dump
 * every field on screen at once — the same "single line until tapped" shape the drawer's own
 * Search/Trip Planner sections use.
 */
@Composable
internal fun LogEntryDetailScreen(
    entry: MushroomLogEntry,
    cameraCaptureFiles: CameraCaptureFiles,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onDeleteEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LogSpacing.lg, vertical = LogSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to your log")
                }
                Text("Find on ${entry.foundOn}", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onDeleteEntry) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete this entry")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LogSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LogSpacing.lg),
        ) {
            Text(
                entry.foundAt?.let { location -> "Found at ${"%.4f".format(location.lat)}, ${"%.4f".format(location.lng)}" }
                    ?: stringResource(R.string.log_entry_no_location),
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = entry.ownIdentification.orEmpty(),
                onValueChange = { text -> onEntryChanged(entry.copy(ownIdentification = text.ifBlank { null })) },
                label = { Text("Your own identification (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PhotosSection(
                photos = entry.photos,
                cameraCaptureFiles = cameraCaptureFiles,
                onPhotoSourceSelected = onAddPhoto,
                onRemovePhoto = onRemovePhoto,
            )

            HorizontalDivider()

            CollapsibleSection(title = "Cap") {
                CapEditor(entry.cap, onChanged = { onEntryChanged(entry.copy(cap = it)) })
            }
            CollapsibleSection(title = "Hymenophore") {
                HymenophoreEditor(entry.hymenophore, onChanged = { onEntryChanged(entry.copy(hymenophore = it)) })
            }
            CollapsibleSection(title = "Stipe") {
                StipeEditor(entry.stipe, onChanged = { onEntryChanged(entry.copy(stipe = it)) })
            }
            CollapsibleSection(title = "Veil remnants") {
                VeilEditor(entry.veil, onChanged = { onEntryChanged(entry.copy(veil = it)) })
            }
            CollapsibleSection(title = "Context / flesh") {
                ContextFleshEditor(entry.contextFlesh, onChanged = { onEntryChanged(entry.copy(contextFlesh = it)) })
            }
            CollapsibleSection(title = "Spore print") {
                SporePrintEditor(entry.sporePrint, onChanged = { onEntryChanged(entry.copy(sporePrint = it)) })
            }
            CollapsibleSection(title = "Host & substrate") {
                HostSubstrateEditor(entry.hostSubstrate, onChanged = { onEntryChanged(entry.copy(hostSubstrate = it)) })
            }

            NotesField(entry.notes, onValueChanged = { onEntryChanged(entry.copy(notes = it)) })

            Spacer(modifier = Modifier.heightIn(min = LogSpacing.lg))
        }
    }
}

@Composable
private fun PhotosSection(
    photos: List<LogPhoto>,
    cameraCaptureFiles: CameraCaptureFiles,
    onPhotoSourceSelected: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
) {
    var pendingCapture by remember { mutableStateOf<CameraCaptureFiles.Capture?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) {
            onPhotoSourceSelected(ContentUriPhotoSource(capture.uri))
        } else {
            capture?.let(cameraCaptureFiles::deleteCapture)
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val capture = cameraCaptureFiles.newCapture()
            pendingCapture = capture
            takePicture.launch(capture.uri)
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoSourceSelected(ContentUriPhotoSource(uri))
    }

    Column(verticalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
        Text("Photos", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
            Button(onClick = { requestCameraPermission.launch(Manifest.permission.CAMERA) }) { Text("Camera") }
            Button(
                onClick = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text("Gallery") }
        }
        if (photos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LogSpacing.sm)) {
                photos.forEach { photo -> LogPhotoThumbnail(photo = photo, onRemove = { onRemovePhoto(photo) }) }
            }
        }
    }
}

private const val PHOTO_THUMBNAIL_SIZE_DP = 88
private const val PHOTO_THUMBNAIL_SAMPLE_SIZE = 4

@Composable
private fun LogPhotoThumbnail(photo: LogPhoto, onRemove: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo.id) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = PHOTO_THUMBNAIL_SAMPLE_SIZE }
                BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, options)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(modifier = Modifier.size(PHOTO_THUMBNAIL_SIZE_DP.dp)) {
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
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove photo")
        }
    }
}
