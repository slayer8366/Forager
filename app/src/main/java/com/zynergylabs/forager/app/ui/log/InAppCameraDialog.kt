package com.zynergylabs.forager.app.ui.log

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * layout that existed before, unchanged; landscape puts the shutter on the device's charger-port
 * edge, vertically centred, the count beside it.
 *
 * **No Done control** (owner, 2026-09-18). The navigation bar's back is the way out, through the
 * `Dialog`'s own `onDismissRequest`. Done sat top-left, which is the punch-hole edge in the portrait
 * and port-right landscape arrangements, the edge the camera's top strip is to occupy; the owner's
 * call was that it could simply go, not move. Every exit still ends in [onDismiss]: back, the
 * absence timeout (by the holder clearing its target) and the Activity going away.
 *
 * **One rotation rule in both arrangements** (owner, 2026-09-17): controls turn in place as the
 * phone turns, so their text reads in the current hold. The landscape arrangement was first built
 * with its controls held upright and no `rotateWithDevice`, on the reasoning that the window
 * already matches the grip. That reasoning was an exception written into the spec that nobody asked
 * for — the owner stated one rule — and it left "Done" (since removed) and the count sideways the
 * moment the phone turned. Both arrangements now use the same modifier with the same sensor-minus-display angle.
 * In landscape that angle is zero at open, since the device and the window agree, so the controls
 * still read upright the moment the camera opens; they differ from before only once the phone
 * moves.
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
    /**
     * What the camera's top strip holds, or `null` for an empty strip, which composes nothing and
     * takes no space. What goes in it is PR #103's to decide; until then the default is the
     * placeholder, so the owner can judge the strip's size and position. See [CameraStripPlaceholder]
     * for how to remove it.
     */
    stripContent: (@Composable () -> Unit)? = { CameraStripPlaceholder() },
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
    // The window's rotation at open decides which physical edge the shutter goes on, because the
    // two landscapes are mirror images and only one of them has the charger port on the screen's
    // right — see CameraArrangement. Read outside `remember` for the same reason windowIsLandscape
    // is: the value is captured at first composition and then held, not tracked.
    val displayRotation = currentDisplayRotation()
    val arrangement = remember(lockToPortrait) { cameraArrangement(lockToPortrait, windowIsLandscape, displayRotation) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        HideStatusBarForThisDialog()
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

            // Each control takes the real system-bar and cutout insets on the sides it needs them,
            // not the whole safe area (hide-status-bar dispatch, 2026-09-18). Two reasons. The
            // status bar is hidden while this dialog is open (HideStatusBarForThisDialog), so its
            // inset collapses and the space comes back to the controls, deliberately. And a centred
            // control centres on the whole screen, not on what an asymmetric safe area leaves:
            // the landscape shutter is centred on the full height (only horizontal insets), the
            // portrait column on the full width (only the bottom inset).
            // The viewfinder behind them takes no insets at all. Robolectric reports zero insets,
            // so all of this padding is device-only by construction (CLAUDE.md, known pitfalls).
            Box(modifier = Modifier.fillMaxSize()) {
                when (arrangement) {
                    // Count and shutter along the bottom, every control turning in place with the
                    // device (sensor minus display; see rotateWithDevice). Done used to be
                    // top-left; removed 2026-09-18, see the class doc.
                    CameraArrangement.Portrait -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                .padding(Spacing.lg),
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

                    // A landscape window, pinned where it is: the shutter on the device's
                    // charger-port edge, vertically centred, where the thumb of a two-handed
                    // landscape grip already is; the count (and a failure) beside it, not under
                    // it. The count and a failure turn in place with the
                    // device exactly as the portrait arrangement's do — one rule, every
                    // arrangement (owner, 2026-09-17). At open the device and the window agree, so
                    // sensor minus display is zero and they read upright; they turn only when the
                    // phone does. The shutter is a disc and has nothing to turn.
                    //
                    // Two mirrored cases, not one, because the port edge is a physical edge and
                    // the two landscapes put it on opposite screen sides — see CameraArrangement.
                    CameraArrangement.LandscapePortRight ->
                        LandscapeControls(
                            portOnRight = true,
                            deviceRotation = session.deviceRotation,
                            captureError = captureError,
                            photosTaken = photosTaken,
                            shutterEnabled = shutterEnabled,
                            onShutter = onShutter,
                        )

                    CameraArrangement.LandscapePortLeft ->
                        LandscapeControls(
                            portOnRight = false,
                            deviceRotation = session.deviceRotation,
                            captureError = captureError,
                            photosTaken = photosTaken,
                            shutterEnabled = shutterEnabled,
                            onShutter = onShutter,
                        )
                }

                // The top strip, on the device's punch-hole edge: the opposite of the shutter's,
                // from the same arrangement, so it follows the device the way the shutter does.
                stripContent?.let { content -> CameraStrip(edge = punchHoleEdge(arrangement), content = content) }
            }
        }
    }
}

/**
 * Hides the status bar for as long as the camera dialog is composed, on **the dialog's own window
 * only** (hide-status-bar dispatch, 2026-09-18). The navigation bar is left alone, by the owner's
 * call. The Activity's window is never touched, so there is no Activity state for any exit to
 * restore: every way out (back through the dialog's `onDismissRequest`, the four-minute
 * absence timeout through `InAppCameraViewModel.close`, and the Activity going away) ends with
 * this leaving composition and the dialog's window going with it. `onDispose` also asks for the
 * bar back on that window, so the restore does not depend on the platform dropping a destroyed
 * window's request promptly.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`: a swipe from the top edge of the screen (the top as the
 * system has it rotated, which the camera's window lock holds still) brings the bar back briefly
 * and the shade can be pulled down. The bar remains the system's; nothing here draws one.
 *
 * Must be called inside the `Dialog` content, where [LocalView]'s parent is the dialog's
 * [DialogWindowProvider]. Anywhere else there is no dialog window, and that is logged rather than
 * reaching for the Activity's window instead.
 */
@Composable
private fun HideStatusBarForThisDialog() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            Log.w(TAG, "No dialog window to hide the status bar on; it stays visible.")
            return@DisposableEffect onDispose {}
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

/**
 * The camera's top strip (camera-top-strip dispatch, 2026-09-18): a band [CAMERA_STRIP_THICKNESS]
 * deep along [edge], the device's punch-hole edge, full length. Geometry only; what it holds is
 * decided in PR #103.
 *
 * **It clears the cut-out by sitting inboard of it.** The band starts where the safe-drawing inset
 * on its own edge ends, and that inset is the cut-out's depth (the status bar is hidden while the
 * camera is open, so nothing else contributes): below the punch-hole in portrait, beside it in
 * landscape. The cut-out's own band is left to the viewfinder. The inset on the two ends is taken
 * too, so a side strip stops short of a bottom navigation handle. Splitting the strip around the
 * hole, to use that band, was not done: it would put controls either side of the camera lens
 * and needs a content decision first.
 *
 * **Empty is nothing.** This is only composed when there is content, so an empty strip reserves no
 * band across the viewfinder.
 *
 * `ScreenEdge.Bottom` cannot be reached, since the punch-hole edge is never the port edge by
 * construction, but it is handled rather than thrown on, as [cameraArrangement] handles rotations
 * it cannot see.
 */
@Composable
private fun BoxScope.CameraStrip(edge: ScreenEdge, content: @Composable () -> Unit) {
    val along = when (edge) {
        ScreenEdge.Top ->
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .height(CAMERA_STRIP_THICKNESS)
        ScreenEdge.Bottom ->
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .height(CAMERA_STRIP_THICKNESS)
        ScreenEdge.Left ->
            Modifier.align(Alignment.CenterStart).fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Left + WindowInsetsSides.Vertical))
                .width(CAMERA_STRIP_THICKNESS)
        ScreenEdge.Right ->
            Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Right + WindowInsetsSides.Vertical))
                .width(CAMERA_STRIP_THICKNESS)
    }
    Box(modifier = along.testTag(CAMERA_STRIP_TAG), contentAlignment = Alignment.Center) { content() }
}

/**
 * **Placeholder, not a control.** A translucent band filling the strip, so the owner can judge its
 * size and position before PR #103 puts anything in it. Whether it ships is the owner's decision.
 *
 * **To remove it:** delete this function and change `stripContent`'s default in [InAppCameraDialog]
 * to `null`. That is one edit at the call site plus this deletion; there is no flag.
 */
@Composable
private fun CameraStripPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.6f))
            .testTag(CAMERA_STRIP_PLACEHOLDER_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(STRIP_PLACEHOLDER_LABEL, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * The landscape arrangement's controls, mirrored by which screen side the device's charger-port
 * edge is on. One composable rather than two near-identical blocks, because the difference is
 * exactly two things — which side the cluster aligns to, and whether the shutter comes before or
 * after the count in reading order — and a second copy is how the two halves drift apart.
 *
 * The shutter is always the outermost child, hard against the port edge, with the count inboard of
 * it. That ordering is the point: the shutter's distance from the edge the thumb wraps around is
 * what the motor habit is built on, and putting the count outboard would move it.
 */
@Composable
private fun BoxScope.LandscapeControls(
    portOnRight: Boolean,
    /** The session's device reading, for [rotateWithDevice] — the same value the portrait arrangement passes. */
    deviceRotation: Int?,
    captureError: String?,
    photosTaken: Int,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(if (portOnRight) Alignment.CenterEnd else Alignment.CenterStart)
            // Horizontal insets only: kept off the nav bar or cutout on the port edge, centred on
            // the screen's full height rather than between the status bar and the bottom inset.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val readout: @Composable () -> Unit = {
            Column(
                horizontalAlignment = if (portOnRight) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                captureError?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.rotateWithDevice(deviceRotation).testTag(CAMERA_ERROR_TAG),
                    )
                }

                Text(
                    photoCountLabel(photosTaken),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.rotateWithDevice(deviceRotation).testTag(CAMERA_COUNT_TAG),
                )
            }
        }

        if (portOnRight) {
            readout()
            ShutterButton(enabled = shutterEnabled, onClick = onShutter)
        } else {
            ShutterButton(enabled = shutterEnabled, onClick = onShutter)
            readout()
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
internal const val CAMERA_STRIP_TAG = "in-app-camera-strip"
internal const val CAMERA_STRIP_PLACEHOLDER_TAG = "in-app-camera-strip-placeholder"
internal const val STRIP_PLACEHOLDER_LABEL = "Strip"

/** One row of controls at Material's minimum touch target. */
internal val CAMERA_STRIP_THICKNESS = 48.dp
internal const val CAMERA_ERROR_TAG = "in-app-camera-error"
internal const val CAMERA_OPENING_TAG = "in-app-camera-opening"
internal const val CAMERA_UNAVAILABLE_TAG = "in-app-camera-unavailable"
internal const val SHUTTER_DESCRIPTION = "Take photo"
internal const val CAPTURE_FAILED_MESSAGE = "That photo didn't save. Try again."

private const val TAG = "InAppCamera"
