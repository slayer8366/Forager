package com.forager.app.ui.log

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.photo.CameraCapturePhotoSource
import com.forager.app.photo.GalleryImportPhotoSource
import java.io.File

/**
 * The Camera-permission-then-capture and system-Gallery-picker launchers both [LogEntryDetailScreen]'s
 * `PhotosSection` and [PhotoGalleryScreen] need — extracted once both screens needed the exact same
 * `ActivityResultContracts`/[CameraCaptureFiles]/permission wiring (standalone-photos dispatch:
 * "reuse the existing contracts, `CameraCaptureFiles`, and FileProvider... reuse; do not
 * reimplement"), rather than a second hand-copy of it drifting from the first over time.
 *
 * Returns the two trigger functions only, not rendered buttons — each screen keeps its own button
 * layout and labels ([LogEntryDetailScreen]'s own `FlowRow` alongside its unrelated "From Album"
 * button; [PhotoGalleryScreen]'s a plain pair above its grid), which differ enough between the two
 * hosts that sharing the button `Composable`s themselves, not just the launcher logic, would need a
 * slot API for no real savings over each screen writing its own two-line `Button`.
 *
 * ## `ACCESS_MEDIA_LOCATION`, requested at the moment of first import (photo-geodata dispatch)
 *
 * [launchGallery] fires [requestMediaLocationPermission] immediately before [pickPhotos], every
 * time, regardless of the previous outcome — there is no "already asked, don't ask again" state
 * tracked here. That is deliberate: Android's own permission dialog already throttles repeat
 * requests after a denial (a second request within the same session re-prompts; a later one after
 * "Don't ask again" is a silent no-op that returns denied immediately), so a second layer of
 * throttling here would just duplicate platform behavior the OS already owns. A denial never blocks
 * the pick itself — [pickPhotos] still launches either way; without the grant,
 * [com.forager.app.photo.FilePhotoStore] simply reads no EXIF location for whatever gets picked
 * (see that class's own doc comment).
 *
 * ## `rememberSaveable`, not `remember` (device-check patch, Item 2)
 *
 * [pendingCapture] survives Activity recreation — a config change, or process death under memory
 * pressure — while the camera app is foregrounded; `remember` alone would silently reset it to
 * `null` on restore even though [takePicture]'s own callback is correctly redelivered by
 * `ActivityResultRegistry` either way, which is what let a real, successfully-captured photo go
 * unclaimed. [CameraCaptureFiles.Capture] isn't itself a `Bundle`-storable/`Parcelable` type, so this
 * uses a small custom [Saver] ([CaptureSaver], below) rather than the default one `rememberSaveable`
 * would otherwise fail to find. This is a narrower, independent fix from [PhotoAcquisitionLaunchers.isAcquisitionInFlight]
 * below — see that property's own doc comment for the separate, more common way a capture was being
 * lost that this alone does not fix.
 */
@Composable
internal fun rememberPhotoAcquisitionLaunchers(
    cameraCaptureFiles: CameraCaptureFiles,
    onPhotoSourceSelected: (PhotoSource) -> Unit,
): PhotoAcquisitionLaunchers {
    var pendingCapture by rememberSaveable(stateSaver = CaptureSaver) { mutableStateOf<CameraCaptureFiles.Capture?>(null) }

    // True from the moment a launcher below hands control to an external Activity (the camera app,
    // the system permission dialog, the photo picker) until that Activity returns a result —
    // device-check patch, Items 2/3. This composable's own caller threads it up to
    // `AvailabilityScreen`'s ON_STOP-triggered "the user backgrounded the app" heuristic, which
    // otherwise cannot distinguish that from "this app itself just launched the camera and expects
    // to resume" — both produce identical ON_PAUSE/ON_STOP events. That confusion is what was
    // silently closing the find being edited (and, with it, tearing down this very composable and
    // its pending capture) on every single camera round-trip: not process death, not a rare
    // low-memory case, but a deliberate lifecycle hook firing on a self-initiated launch. See the
    // device-check patch report for the full trace. `rememberSaveable`, matching [pendingCapture]
    // above, for the same reason: a real Activity recreation during the round-trip must not lose
    // track of "acquisition is still in flight" either.
    var acquisitionInFlight by rememberSaveable { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        acquisitionInFlight = false
        if (success && capture != null) {
            onPhotoSourceSelected(CameraCapturePhotoSource(capture.uri))
        } else {
            capture?.let(cameraCaptureFiles::deleteCapture)
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val capture = cameraCaptureFiles.newCapture()
            pendingCapture = capture
            takePicture.launch(capture.uri)
        } else {
            acquisitionInFlight = false
        }
    }

    // PickMultipleVisualMedia, not PickVisualMedia: the single-select contract only ever returns
    // one Uri — see LogEntryDetailScreen's own former doc comment on this exact bug, now this
    // function's history. onPhotoSourceSelected still takes one PhotoSource at a time; callers
    // issuing one back-to-back call per selected Uri is exactly what a multi-photo pick needs.
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PER_PICK),
    ) { uris ->
        acquisitionInFlight = false
        uris.forEach { uri -> onPhotoSourceSelected(GalleryImportPhotoSource(uri)) }
    }

    // Result ignored — see this function's own doc comment on why a denial never blocks the pick.
    val requestMediaLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    return PhotoAcquisitionLaunchers(
        launchCamera = {
            acquisitionInFlight = true
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        },
        launchGallery = {
            acquisitionInFlight = true
            // Below API 29 there is nothing ACCESS_MEDIA_LOCATION gates (MediaStore.setRequireOriginal
            // doesn't exist yet — see FilePhotoStore's own doc comment), so requesting it there would
            // just be a pointless prompt.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestMediaLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        isAcquisitionInFlight = acquisitionInFlight,
    )
}

/**
 * Decomposes [CameraCaptureFiles.Capture] into the two strings a `Bundle` can hold and back — it
 * carries a [Uri] and a [File], neither of which [rememberSaveable] recognizes on its own, and
 * `Capture` itself is a plain (non-`Parcelable`) data class. See [rememberPhotoAcquisitionLaunchers]'s
 * own doc comment for why this needs to survive Activity recreation at all.
 */
private val CaptureSaver = Saver<CameraCaptureFiles.Capture?, List<String>>(
    save = { capture -> capture?.let { listOf(it.uri.toString(), it.file.absolutePath) } ?: emptyList() },
    restore = { saved -> if (saved.isEmpty()) null else CameraCaptureFiles.Capture(uri = Uri.parse(saved[0]), file = File(saved[1])) },
)

/**
 * The two acquisition triggers [rememberPhotoAcquisitionLaunchers] hands back, plus whether either
 * is currently in flight — a plain data holder, not a sealed type, since no caller branches on which
 * trigger fired.
 */
internal class PhotoAcquisitionLaunchers(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit,
    /** See [rememberPhotoAcquisitionLaunchers]'s own doc comment — device-check patch, Items 2/3. */
    val isAcquisitionInFlight: Boolean,
)

/** How many photos a single "Gallery" pick can select at once — the project owner's own cap, not a platform default. Shared by every acquisition surface via [rememberPhotoAcquisitionLaunchers]. */
internal const val MAX_PHOTOS_PER_PICK = 10
