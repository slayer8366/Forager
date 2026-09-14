package com.zynergylabs.forager.app.ui.log

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraXCaptureSession
import com.zynergylabs.forager.app.photo.GalleryImportPhotoSource

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
 * [com.zynergylabs.forager.app.photo.FilePhotoStore] simply reads no EXIF location for whatever gets picked
 * (see that class's own doc comment).
 *
 * ## The camera no longer leaves this Activity (2026-09-14)
 *
 * `ActivityResultContracts.TakePicture` is gone from here. The Camera button now opens
 * [InAppCameraDialog] in this process, which is what lets a user take several photos in one go —
 * `ACTION_IMAGE_CAPTURE` returns after exactly one, by contract, so multi-shot was never a setting
 * to flip.
 *
 * **Deleted with it: `pendingCapture` and its custom `Saver`.** They existed because a capture's
 * destination had to survive Activity recreation while an external camera app was foregrounded —
 * `remember` alone reset it to `null` on restore, and a real, successfully-captured photo went
 * unclaimed (device-check patch, Item 2). Nothing recreates the Activity now: a `Dialog` composes
 * over the screen that opened it. The fix is not regressed, its precondition is gone, and it is
 * recorded here rather than left as dead state nothing reads.
 *
 * [PhotoAcquisitionLaunchers.isAcquisitionInFlight] **stays**, narrowed. It was the separate and
 * more common half of that same bug, and the gallery picker and the permission dialog still do
 * leave the app.
 */
@Composable
internal fun rememberPhotoAcquisitionLaunchers(
    cameraCaptureFiles: CameraCaptureFiles,
    onPhotoSourceSelected: (PhotoSource) -> Unit,
): PhotoAcquisitionLaunchers {
    val context = LocalContext.current
    // True from the moment a launcher below hands control to an external Activity — now the system
    // permission dialog and the photo picker, no longer a camera app — until it returns a result.
    // Device-check patch, Items 2/3. This composable's own caller threads it up to
    // `AvailabilityScreen`'s ON_STOP-triggered "the user backgrounded the app" heuristic, which
    // otherwise cannot distinguish that from "this app itself just launched something and expects
    // to resume" — both produce identical ON_PAUSE/ON_STOP events. That confusion is what was
    // silently closing the find being edited on every single camera round-trip: not process death,
    // not a rare low-memory case, but a deliberate lifecycle hook firing on a self-initiated
    // launch. The camera half of that is now structural rather than guarded (see this function's
    // own doc comment), and this remains for the two that still leave. `rememberSaveable` because a
    // real Activity recreation during a round-trip must not lose track of it either.
    var acquisitionInFlight by rememberSaveable { mutableStateOf(false) }

    // Whether the in-app camera is open. Not `rememberSaveable`: the camera rebinds from scratch on
    // an Activity recreation anyway, and a dialog reopening by itself after the user was sent away
    // is worse than making them tap Camera again.
    var isCameraOpen by remember { mutableStateOf(false) }

    // android.permission.CAMERA, requested at the moment the user asks for the camera rather than
    // at launch — the same shape as ACCESS_MEDIA_LOCATION below, and for the same reason. This
    // request was deleted on 2026-09-10 along with the manifest entry, when every capture went out
    // to the user's own camera app and the permission bought nothing; the in-app camera is the
    // "direct CameraX use" the manifest's own removal note named as the condition for both coming
    // back. A denial does **not** open the dialog: unlike the media-location grant, which only
    // enriches an import, without this there is no camera at all.
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        acquisitionInFlight = false
        isCameraOpen = granted
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
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                isCameraOpen = true
            } else {
                // In flight only across the system permission dialog, which really does background
                // the app. Opening our own camera never does, which is the whole reason the dialog
                // replaced the intent — see InAppCameraDialog's own doc comment.
                acquisitionInFlight = true
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
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
        isCameraOpen = isCameraOpen,
        onCameraDismissed = { isCameraOpen = false },
        cameraCaptureFiles = cameraCaptureFiles,
        onPhotoSourceSelected = onPhotoSourceSelected,
    )
}

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
    private val isCameraOpen: Boolean,
    private val onCameraDismissed: () -> Unit,
    private val cameraCaptureFiles: CameraCaptureFiles,
    private val onPhotoSourceSelected: (PhotoSource) -> Unit,
) {
    /**
     * The in-app camera, composed by each screen that offers a Camera button.
     *
     * **Placed by the caller on purpose, rather than emitted from inside
     * [rememberPhotoAcquisitionLaunchers].** That function could emit the `Dialog` itself and save
     * three call sites one line each, and a `remember*` function that quietly draws UI is exactly
     * the kind of thing that costs the next reader an hour. One visible line per screen is the
     * cheaper trade.
     */
    @Composable
    fun CameraDialog() {
        if (!isCameraOpen) return
        val session = rememberCameraXCaptureSession()
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = cameraCaptureFiles,
            onPhotoCaptured = onPhotoSourceSelected,
            onDismiss = onCameraDismissed,
            viewfinder = { modifier -> session.Viewfinder(modifier) },
        )
    }
}

/**
 * One [CameraXCaptureSession] per open camera, discarded when it closes — it holds a bound CameraX
 * use case, which must not outlive the viewfinder it draws into.
 */
@Composable
private fun rememberCameraXCaptureSession(): CameraXCaptureSession {
    val context = LocalContext.current.applicationContext
    return remember { CameraXCaptureSession(context) }
}

/** How many photos a single "Gallery" pick can select at once — the project owner's own cap, not a platform default. Shared by every acquisition surface via [rememberPhotoAcquisitionLaunchers]. */
internal const val MAX_PHOTOS_PER_PICK = 10
