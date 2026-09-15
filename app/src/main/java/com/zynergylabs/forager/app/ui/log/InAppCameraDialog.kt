package com.zynergylabs.forager.app.ui.log

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/**
 * The in-app camera: shoot, shoot again, shoot again, then leave — owner's request, 2026-09-14,
 * *"Allow the in app camera to take multiple photos. When exiting the camera, take user back to
 * where they activated the camera. People can preview after they're done."*
 *
 * ## Why a `Dialog` and not a navigation destination
 *
 * "Take the user back to where they activated the camera" is a requirement about *state*, not about
 * routing, and a `Dialog` satisfies it by construction: the screen underneath is never left, never
 * recomposed away and never has anything to restore, so returning is simply dismissing. A real
 * destination would mean each of the three launch surfaces (the find edit form, the Album, the
 * pull-photo picker) saving and restoring its own position, which is three chances to get it wrong
 * and nothing gained. [PhotoViewerDialog] settled the same question the same way earlier in this
 * branch.
 *
 * It also repays a specific old bug. Handing off to an external camera app produced ON_PAUSE/ON_STOP
 * indistinguishable from the user backgrounding Forager, which is what silently closed the find
 * being edited on every capture until `isAcquisitionInFlight` was threaded up to suppress it. A
 * dialog never leaves the Activity, so that whole class of confusion does not arise for captures
 * any more. The flag stays for the gallery picker, which still leaves.
 *
 * ## Each photo is handed over the moment it lands
 *
 * [onPhotoCaptured] fires per shot rather than once with a list at the end. That is what *"people
 * can preview after they're done"* asks for: no per-shot confirm screen, nothing to accept or
 * retake, the camera stays up and ready. It also means a session interrupted by a phone call keeps
 * the photos already taken, which a batch handed over at dismissal would lose.
 *
 * The running count is the only feedback during a session, deliberately. Showing a thumbnail of the
 * last shot invites reviewing in here, which is the flow the owner asked to move to afterwards.
 *
 * ## Two arrangements, one chosen at open and held (device check on `51882cb`, step 3.4)
 *
 * Owner's ruling, superseding the earlier orientation-lock decisions: nothing in the camera layout
 * moves while the camera is open, and in a landscape window the shutter is on the right edge,
 * never along the bottom. The window lock became conditional on the setting
 * ([LockWindowOrientation]: `LOCKED` off, `PORTRAIT` on) and this dialog gained a landscape
 * arrangement, chosen once from the setting and the window's shape at open ([cameraArrangement])
 * and never reflowed — the `remember` below has no configuration key on purpose. Portrait is the
 * layout that existed before, unchanged; landscape puts the shutter on the right edge, vertically
 * centred, the count beside it, Done top-left, all upright: the window already matches the grip,
 * so `rotateWithDevice` does not apply there. The controls' angle in the portrait arrangement, the
 * sensor-minus-display expression, is as it was.
 *
 * ## What is tested, and what a green suite here does not mean
 *
 * [session] is [CameraCaptureSession], and [viewfinder] is a slot, so everything below is exercised
 * under Robolectric against a fake: the shutter producing one photo per tap, the count, a failed
 * capture surfacing instead of silently not appearing, the unavailable state, and dismissal. What
 * cannot be exercised anywhere is CameraX itself — no camera provider exists in that harness — so
 * whether a real camera opens, whether the viewfinder draws, and whether a captured JPEG is
 * right-way-up are **device checks**, not covered by any of it. See
 * [com.zynergylabs.forager.app.photo.CameraXCaptureSession].
 */
@Composable
internal fun InAppCameraDialog(
    session: CameraCaptureSession,
    cameraCaptureFiles: CameraCaptureFiles,
    /** Settings' "Lock camera to portrait": decides the window lock and, with the window's shape at open, the arrangement. */
    lockToPortrait: Boolean,
    onPhotoCaptured: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: @Composable (Modifier) -> Unit,
) {
    // rememberSaveable: a rotation mid-session must not reset the user's sense of how many they
    // have taken. The photos themselves are already handed over and safe either way.
    var photosTaken by rememberSaveable { mutableIntStateOf(0) }
    var captureError by rememberSaveable { mutableStateOf<String?>(null) }
    var isCapturing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // The screen opens the session, not the viewfinder — the deadlock fix of 2026-09-15, see
    // CameraCaptureSession.open. Enter opens, leave closes; the `when` below never changes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session, lifecycleOwner) {
        session.open(lifecycleOwner)
        onDispose { session.close() }
    }

    // The window stays put while the camera is open — pinned where it is, or forced portrait, by
    // the setting; reasoning on LockWindowOrientation. Outside the Dialog so the context here is
    // the Activity's, not the dialog window's.
    LockWindowOrientation(lockToPortrait)

    // The arrangement is chosen once, at open, from the setting and the window's shape as the
    // Activity's configuration reports it in this first composition, and held: `remember` with
    // no configuration key, on purpose. The lock above is what makes the held value right for
    // the life of the dialog (LOCKED pins the shape read here; PORTRAIT makes the setting-on
    // answer portrait whatever was read). Reading LocalConfiguration.current outside `remember`
    // would reflow if the window ever did turn, which is the one thing that must not happen.
    val windowIsLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val arrangement = remember(lockToPortrait) { cameraArrangement(lockToPortrait, windowIsLandscape) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(IN_APP_CAMERA_TAG),
        ) {
            when (val state = session.state) {
                is CameraSessionState.Unavailable -> Text(
                    state.reason,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.lg)
                        .testTag(CAMERA_UNAVAILABLE_TAG),
                )

                CameraSessionState.Opening -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).testTag(CAMERA_OPENING_TAG),
                )

                CameraSessionState.Ready -> viewfinder(Modifier.fillMaxSize())
            }

            val shutterEnabled = session.state == CameraSessionState.Ready && !isCapturing
            val onShutter: () -> Unit = {
                // Guarded here as well as by `enabled`: a second tap landing in the same frame as
                // the first would otherwise open two captures onto two files, and the shutter is
                // exactly the control people double-tap.
                if (!isCapturing) {
                    isCapturing = true
                    captureError = null
                    scope.launch {
                        val capture = cameraCaptureFiles.newCapture()
                        session.capture(capture.file).fold(
                            onSuccess = {
                                photosTaken += 1
                                onPhotoCaptured(CameraCapturePhotoSource(capture))
                            },
                            onFailure = {
                                // The empty destination is cleaned up rather than left as a
                                // zero-byte file the persist path would later try to read.
                                // Reported, never swallowed (CLAUDE.md).
                                cameraCaptureFiles.deleteCapture(capture)
                                captureError = CAPTURE_FAILED_MESSAGE
                            },
                        )
                        isCapturing = false
                    }
                }
            }

            // Controls sit inside the real system-bar insets; the viewfinder behind them does not.
            // Robolectric reports zero insets, so this padding is device-only by construction
            // (CLAUDE.md, known pitfalls) — nothing below says anything about it.
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (arrangement) {
                    // Exactly as it was before the landscape arrangement existed: Done top-left,
                    // count and shutter along the bottom, every control turning in place with the
                    // device (sensor minus display; see rotateWithDevice).
                    CameraArrangement.Portrait -> {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Spacing.sm)
                                .rotateWithDevice(session.deviceRotation)
                                .testTag(CAMERA_DONE_TAG),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                            Text(DONE_LABEL, color = Color.White, modifier = Modifier.padding(start = Spacing.xs))
                        }

                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            captureError?.let { message ->
                                Text(
                                    message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.rotateWithDevice(session.deviceRotation).testTag(CAMERA_ERROR_TAG),
                                )
                            }

                            Text(
                                photoCountLabel(photosTaken),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.rotateWithDevice(session.deviceRotation).testTag(CAMERA_COUNT_TAG),
                            )

                            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                ShutterButton(enabled = shutterEnabled, onClick = onShutter)
                            }
                        }
                    }

                    // A landscape window, pinned where it is: the shutter on the right edge,
                    // vertically centred, where the right thumb of a two-handed landscape grip
                    // already is; the count (and a failure) beside it, not under it; Done
                    // top-left. Upright, every one of them, and no rotateWithDevice: the window
                    // already matches the grip, so upright is the natural reading, and controls
                    // that read upright the moment the camera opens say it is ready.
                    CameraArrangement.Landscape -> {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Spacing.sm)
                                .testTag(CAMERA_DONE_TAG),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                            Text(DONE_LABEL, color = Color.White, modifier = Modifier.padding(start = Spacing.xs))
                        }

                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd).padding(Spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                captureError?.let { message ->
                                    Text(
                                        message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.testTag(CAMERA_ERROR_TAG),
                                    )
                                }

                                Text(
                                    photoCountLabel(photosTaken),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag(CAMERA_COUNT_TAG),
                                )
                            }

                            ShutterButton(enabled = shutterEnabled, onClick = onShutter)
                        }
                    }
                }
            }
        }
    }
}

/** The shutter: a plain ringed disc, the shape every camera app has trained people to recognise. */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE_DP.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = DISABLED_SHUTTER_ALPHA))
            .border(SHUTTER_RING_DP.dp, Color.White.copy(alpha = SHUTTER_RING_ALPHA), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = SHUTTER_DESCRIPTION }
            .testTag(CAMERA_SHUTTER_TAG),
    )
}

/**
 * "No photos yet" / "1 photo taken" / "4 photos taken". Singular is not decoration: the count is
 * the only feedback a session gives, so it should read like a sentence rather than a counter.
 */
internal fun photoCountLabel(count: Int): String = when (count) {
    0 -> "No photos yet"
    1 -> "1 photo taken"
    else -> "$count photos taken"
}

private const val SHUTTER_SIZE_DP = 72
private const val SHUTTER_RING_DP = 4
private const val SHUTTER_RING_ALPHA = 0.6f
private const val DISABLED_SHUTTER_ALPHA = 0.4f

internal const val IN_APP_CAMERA_TAG = "in-app-camera"
internal const val CAMERA_SHUTTER_TAG = "in-app-camera-shutter"
internal const val CAMERA_COUNT_TAG = "in-app-camera-count"
internal const val CAMERA_DONE_TAG = "in-app-camera-done"
internal const val CAMERA_ERROR_TAG = "in-app-camera-error"
internal const val CAMERA_OPENING_TAG = "in-app-camera-opening"
internal const val CAMERA_UNAVAILABLE_TAG = "in-app-camera-unavailable"
internal const val SHUTTER_DESCRIPTION = "Take photo"
internal const val DONE_LABEL = "Done"
internal const val CAPTURE_FAILED_MESSAGE = "That photo didn't save. Try again."
