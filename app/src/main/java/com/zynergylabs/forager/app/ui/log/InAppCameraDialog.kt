package com.zynergylabs.forager.app.ui.log

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
 * ## Three arrangements, one chosen at open and held; a region model with two bands (2026-09-18)
 *
 * Owner's ruling, superseding the earlier orientation-lock decisions: nothing in the camera layout
 * moves while the camera is open. The window lock is conditional on the setting
 * ([LockWindowOrientation]: `LOCKED` off, `PORTRAIT` on) and the arrangement is chosen once from
 * the setting, the window's shape and its rotation at open ([cameraArrangement]) and never
 * reflowed — the `remember` below has no configuration key on purpose.
 *
 * Every placement is stated in **device anatomy**, never a screen side: the shutter band sits on
 * the charger-port edge ([portEdge]) and the strip on the punch-hole edge ([punchHoleEdge]), and
 * both are bands of the region model in `CameraBands.kt` — zero-thick at the one full-bleed ratio,
 * so everything sits where it did, and the structure a later ratio needs is already named. There
 * is no Done control: the navigation bar's Back is the way out, and it always was the same close
 * (`onDismissRequest` is this [onDismiss]) — see `CameraStrip`. The status bar is hidden on this dialog's own
 * window ([HideStatusBarOnThisWindow]) and the safe area collapses: no `safeDrawing` padding, each
 * band clearing the cut-out and the navigation bar on its own edge only.
 *
 * **One rotation rule in every arrangement** (owner, 2026-09-17): controls turn in place as the
 * phone turns, by [rotateWithDevice], so their text reads in the current hold; the shutter is a
 * disc and has nothing to turn. And one contrast rule (`CameraOverlay.kt`): black outline on every
 * overlay element, white fill unless the fill carries meaning.
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
    /** The strip's slot (CameraBands.kt); the default is the gated placeholder. A test passes null for the empty strip. */
    stripContent: (@Composable (edge: ScreenEdge, deviceRotation: Int?, displayRotation: Int) -> Unit)? = defaultStripContent(),
    /** Hides the status bar on the dialog's own window; a test injects a fake to see which window was asked. */
    statusBarHider: StatusBarHider = SystemStatusBarHider,
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

    // The arrangement follows the window: it is derived from the setting, the window's shape and
    // the window's rotation, and re-derived whenever any of the three changes (owner, 2026-09-18).
    //
    // Until 2026-09-18 the `remember` was keyed on the setting alone, so the arrangement was
    // computed at first composition and held for the dialog's life, on the reasoning that the lock
    // above keeps the window still and a turning window "must not happen". That treated a turning
    // window as a failure mode only. It has two legitimate causes the held value was blind to:
    // **a background-and-return** — the platform re-resolves SCREEN_ORIENTATION_LOCKED when the
    // Activity becomes visible again, so a camera opened in landscape and returned to in portrait
    // inside the four-minute window came back as a portrait window carrying a landscape
    // arrangement (device, 2026-09-18) — and **a large screen where the platform ignores the lock**,
    // where the window turns in the user's hands and a held arrangement leaves the same mismatch.
    // Following the window is right in both; there is no visible reflow on a phone, because the
    // lock holds while anyone is watching and these inputs can only change across a return.
    // Do not restore a setting-only key: that is this bug.
    val windowIsLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The window's rotation decides which physical edge the shutter goes on, because the two
    // landscapes are mirror images and only one has the charger port on the screen's right — see
    // CameraArrangement. currentDisplayRotation reads LocalConfiguration so it recomposes when the
    // window turns, and the key below carries the new value into the arrangement.
    val displayRotation = currentDisplayRotation()
    val arrangement = remember(lockToPortrait, windowIsLandscape, displayRotation) {
        cameraArrangement(lockToPortrait, windowIsLandscape, displayRotation)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // On this window, the dialog's own, so every exit restores the bar by destroying the window
        // it was hidden on — see CameraWindowChrome.
        HideStatusBarOnThisWindow(statusBarHider)

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(IN_APP_CAMERA_TAG),
        ) {
            when (val state = session.state) {
                is CameraSessionState.Unavailable -> OverlayText(
                    state.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.lg)
                        .testTag(CAMERA_UNAVAILABLE_TAG),
                )

                CameraSessionState.Opening -> OverlayProgress(modifier = Modifier.align(Alignment.Center).testTag(CAMERA_OPENING_TAG))

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

            // The region model (CameraBands.kt): this Box is the viewfinder region, full-bleed, and
            // the two bands sit on its punch-hole and charger-port edges, zero-thick at the one
            // ratio that exists. No safeDrawing padding here any more: the status bar is hidden
            // and the safe area collapses; each band clears the cut-out and the navigation bar on
            // its own edge only, which is also what keeps the landscape shutter on the screen's
            // true centre.
            val port = portEdge(arrangement)
            // Both bands take the dialog-level displayRotation from above, the value the arrangement
            // turns on, rather than letting each glyph read its own: inside this Dialog's content
            // LocalConfiguration does not invalidate, so a glyph's own read goes stale the moment
            // the window turns and stays stale until something unrelated recomposes it (the
            // placeholder label after a setting-on landscape open, 2026-09-18). One source, one
            // invariant: what re-derives the arrangement is what turns the glyphs.
            CameraStrip(
                edge = punchHoleEdge(arrangement),
                deviceRotation = session.deviceRotation,
                displayRotation = displayRotation,
                content = stripContent,
            )
            CameraBand(edge = port, modifier = Modifier.testTag(CAMERA_SHUTTER_BAND_TAG)) {
                ShutterCluster(
                    edge = port,
                    deviceRotation = session.deviceRotation,
                    displayRotation = displayRotation,
                    captureError = captureError,
                    photosTaken = photosTaken,
                    shutterEnabled = shutterEnabled,
                    onShutter = onShutter,
                )
            }
        }
    }
}

/**
 * The shutter band's contents: the shutter hard against the charger-port edge, the count (and a
 * failure) inboard of it. Along a horizontal edge — portrait, the port edge at the bottom — that is
 * a column with the shutter last; along a vertical edge it is a row with the shutter outermost,
 * mirrored by which side the port is on. One composable for all three, because the difference is
 * an axis and an order, and separate copies are how the halves drift apart.
 *
 * The shutter is always the outermost child, with the count inboard. That ordering is the point:
 * the shutter's distance from the edge the thumb wraps around is what the motor habit is built on.
 * Every glyph turns in place by [rotateWithDevice]; the shutter is a disc and has nothing to turn.
 */
@Composable
private fun ShutterCluster(
    edge: ScreenEdge,
    deviceRotation: Int?,
    /** The dialog-level window rotation, not read here — see the CameraStrip call site for why. */
    displayRotation: Int,
    captureError: String?,
    photosTaken: Int,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
) {
    val readout: @Composable (horizontalAlignment: Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            captureError?.let { message ->
                OverlayText(
                    message,
                    fill = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_ERROR_TAG),
                )
            }
            OverlayText(
                photoCountLabel(photosTaken),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_COUNT_TAG),
            )
        }
    }
    if (edge.isHorizontal) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            readout(Alignment.CenterHorizontally)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                ShutterButton(enabled = shutterEnabled, onClick = onShutter)
            }
        }
    } else {
        val portOnRight = edge == ScreenEdge.Right
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (portOnRight) {
                readout(Alignment.End)
                ShutterButton(enabled = shutterEnabled, onClick = onShutter)
            } else {
                ShutterButton(enabled = shutterEnabled, onClick = onShutter)
                readout(Alignment.Start)
            }
        }
    }
}

/**
 * The shutter: a plain ringed disc, the shape every camera app has trained people to recognise,
 * with the overlay rule's black ring at its outer edge — drawn inside the same 72 dp, so the disc
 * reads over a white scene without moving.
 */
@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE_DP.dp)
            .clip(CircleShape)
            .background(if (enabled) OverlayFill else OverlayFill.copy(alpha = DISABLED_SHUTTER_ALPHA))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = SHUTTER_DESCRIPTION }
            .testTag(CAMERA_SHUTTER_TAG)
            // Drawing only, after the node is sized, tagged and clickable at its full 72 dp: the
            // black ring at the outer edge, then the white ring inset by it. The first cut put the
            // padding before the tag and the tagged node shrank 1.5 dp — the shutter had not moved
            // and the tests said it had, which is the spec's "lead worth checking" in miniature.
            .overlayRing()
            .padding(OVERLAY_OUTLINE_WIDTH / 2)
            .border(SHUTTER_RING_DP.dp, OverlayFill.copy(alpha = SHUTTER_RING_ALPHA), CircleShape),
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
internal const val CAMERA_SHUTTER_BAND_TAG = "in-app-camera-shutter-band"
internal const val CAMERA_COUNT_TAG = "in-app-camera-count"
internal const val CAMERA_ERROR_TAG = "in-app-camera-error"
internal const val CAMERA_OPENING_TAG = "in-app-camera-opening"
internal const val CAMERA_UNAVAILABLE_TAG = "in-app-camera-unavailable"
internal const val SHUTTER_DESCRIPTION = "Take photo"
internal const val CAPTURE_FAILED_MESSAGE = "That photo didn't save. Try again."
