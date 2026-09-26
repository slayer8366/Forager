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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.LevelProvider
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.TimerMode
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
 * ## Not a Dialog any more (2026-09-19), and the name is the only thing left of that
 *
 * This draws in **the Activity's own window**, as a full-screen `Box` over the screen's content,
 * because a fullscreen `Dialog` above the Activity makes seamless rotation unreachable: the
 * platform picks a rotation's animation from the task's main window, which is only ever
 * `TYPE_BASE_APPLICATION`, and then requires that window to be the top fullscreen opaque one, which
 * the dialog was. Measured both ways — `docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`
 * for the dialog, the completion report beside it for this. Owner's ruling.
 *
 * The file and this composable keep the name `InAppCameraDialog`: renaming touches five test
 * classes and a dozen doc references, and that churn was not part of the ruling. **It is a
 * historical name, not a description** — there is no `Dialog` here, no second window, and no
 * `DialogWindowProvider`. Flagged to the owner as a follow-up rather than taken.
 *
 * ## Three arrangements, re-derived as the window turns; a region model with two bands
 *
 * The window follows the device with the setting off and is forced portrait with it on
 * ([RequestWindowOrientation]: `FULL_SENSOR` off, `PORTRAIT` on), and the arrangement is derived
 * from the setting, the window's shape and its rotation ([cameraArrangement]) and re-derived on
 * every change of those — the keyed `remember` below. The turn carries no animation, because the
 * Activity's window asks for seamless rotation while the camera is open ([RequestSeamlessRotation]):
 * it is re-laid out in the new rotation and the next frame is simply the new layout. *Superseded (2026-09-19):* this paragraph read
 * "nothing in the camera layout moves while the camera is open … the window lock is conditional
 * on the setting (`LOCKED` off) … chosen once at open and never reflowed"; the lock could not put
 * the system status bar on the phone's top edge, and the owner reversed it — reasoning on
 * [RequestWindowOrientation].
 *
 * Every placement is stated in **device anatomy**, never a screen side: the shutter band sits on
 * the charger-port edge ([portEdge]) and the strip on the punch-hole edge ([punchHoleEdge]), and
 * both are bands of the region model in `CameraBands.kt` — zero-thick at the one full-bleed ratio,
 * so everything sits where it did, and the structure a later ratio needs is already named. There
 * is no Done control: the navigation bar's Back is the way out, and it always was the same close
 * (a `BackHandler` reaching this [onDismiss], where until 2026-09-19 it was the dialog's
 * `onDismissRequest` reaching the same lambda) — see `CameraStrip`. The status bar is hidden on the
 * Activity's window ([HideStatusBarWhileCameraIsOpen]) and put back when the camera leaves
 * composition; the safe area collapses, so no `safeDrawing` padding, each band clearing the cut-out
 * and the navigation bar on its own edge only.
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
    /** The grid chip's mode, persisted by `CameraGridModeViewModel`; the grid and level draw from it. */
    gridMode: GridMode,
    /** Asks for a new grid mode; the chip shows it once it is stored, not before. */
    onGridModeChanged: (GridMode) -> Unit,
    /** The level line's roll; collected only while the level is shown. */
    levelProvider: LevelProvider,
    modifier: Modifier = Modifier,
    /** Hides the status bar on the Activity's window and puts it back on leaving; a test injects a fake to see which window was asked, and that it was restored. */
    statusBarHider: StatusBarHider = SystemStatusBarHider,
    viewfinder: @Composable (Modifier) -> Unit,
) {
    // rememberSaveable: a rotation mid-session must not reset the user's sense of how many they
    // have taken. The photos themselves are already handed over and safe either way.
    var photosTaken by rememberSaveable { mutableIntStateOf(0) }
    var captureError by rememberSaveable { mutableStateOf<String?>(null) }
    var isCapturing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var timerMode by rememberSaveable { mutableStateOf(TimerMode.Off) } // STUB

    // The screen opens the session, not the viewfinder — the deadlock fix of 2026-09-15, see
    // CameraCaptureSession.open. Enter opens, leave closes; the `when` below never changes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session, lifecycleOwner) {
        session.open(lifecycleOwner)
        onDispose { session.close() }
    }

    // The window follows the device, or is forced portrait, by the setting; reasoning on
    // RequestWindowOrientation. Outside the Dialog so the context here is the Activity's, not the
    // dialog window's.
    RequestWindowOrientation(lockToPortrait)

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
    // Following the window is right in both. Since 2026-09-19 it is also the ordinary case: with
    // the setting off the window follows the device (FULL_SENSOR), so these inputs change on every
    // hold, and the re-derived layout appears in the same frame as the turned window because the
    // window rotates seamlessly (RequestSeamlessRotation). Do not restore a setting-only key:
    // that is this bug.
    val windowIsLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The window's rotation decides which physical edge the shutter goes on, because the two
    // landscapes are mirror images and only one has the charger port on the screen's right — see
    // CameraArrangement. currentDisplayRotation reads LocalConfiguration so it recomposes when the
    // window turns, and the key below carries the new value into the arrangement.
    val displayRotation = currentDisplayRotation()
    val arrangement = remember(lockToPortrait, windowIsLandscape, displayRotation) {
        cameraArrangement(lockToPortrait, windowIsLandscape, displayRotation)
    }

    // Back is the only way out, and on the Activity's window it is the Activity's own dispatcher
    // rather than a dialog's — it reaches the same `onDismiss` that `onDismissRequest` used to, so
    // everything downstream of closing is unchanged. Enabled for exactly as long as this composable
    // is in composition, which is exactly as long as the camera is open.
    BackHandler { onDismiss() }

    // Both claims are on the Activity's own window now, and both are restored when this leaves
    // composition — see CameraWindowChrome for why that covers every exit, and for why the window
    // had to change for the turn below to carry no animation.
    HideStatusBarWhileCameraIsOpen(statusBarHider)
    RequestSeamlessRotation()

    Box(
        // Two separate things keep the screen underneath from being reached, and the `Dialog` used
        // to provide both for free by being a window of its own. **Order**: the camera is composed
        // *after* the screen's width-class branch, so it draws and hit-tests above it —
        // `Modifier.zIndex(1f)` was tried first, to keep the call textually where it was, and
        // measured insufficient. **Touch**: a `Box` that merely draws a background is not a
        // hit-test target at all, so touches fell straight through to the controls behind even once
        // the order was right. `swallowTouchesBelow` below is what stops them, and it is this
        // repo's own recorded pitfall read backwards — a container that attaches pointer input
        // intercepts its whole bounds, which here is the wanted behaviour rather than the bug.
        //
        // Both were caught by one test and neither by review: the occlusion test in
        // `AvailabilityScreenInAppCameraTest` failed with the Camera button behind the camera still
        // firing on a real touch ("expected:<[ALBUM]> but was:<[ALBUM, ALBUM]>"), twice, for these
        // two different reasons. It is a coordinate touch rather than a semantic click for the
        // reason CLAUDE.md gives: a semantic click would have passed on every one of these builds.
        modifier = modifier
            .fillMaxSize()
            .swallowTouchesBelow()
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
        // Over the preview, under the bands: composed after the viewfinder and before the strip
        // and the shutter band, so both draw above them. Only once there is a preview to divide.
        if (session.state == CameraSessionState.Ready) {
            GridOverlay(gridMode)
            LevelLine(gridMode, levelProvider, displayRotation)
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
        // Both bands take the displayRotation read above, the value the arrangement turns on,
        // rather than letting each glyph read its own. That was load-bearing while this was a
        // Dialog, where LocalConfiguration does not invalidate inside the dialog's content and a
        // glyph's own read went stale the moment the window turned (the strip's since-retired
        // placeholder label after a setting-on landscape open, 2026-09-18). In the Activity's window that local does
        // invalidate, so the defect cannot recur here — the single source is kept because one
        // source and one invariant is the right shape, not because it is still the only safe one.
        CameraStrip(
            edge = punchHoleEdge(arrangement),
            deviceRotation = session.deviceRotation,
            displayRotation = displayRotation,
            chips = stripChips(session, timerMode, { timerMode = it }, gridMode, onGridModeChanged),
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

/**
 * Takes every touch that reaches this composable and is not handled by something inside it, so the
 * screen underneath cannot be operated through the camera.
 *
 * A `Dialog` gave this for free: a window of its own receives the input and nothing below it is
 * reachable. Drawing in the Activity's own window instead (2026-09-19), a plain `Box` with a
 * background is **not a hit-test target** — `background` is a draw modifier and attaches no pointer
 * input — so touches fell through to whatever the screen had composed there. `AvailabilityScreen`'s
 * Camera button kept firing under the open camera until this existed.
 *
 * The pointer events are consumed on the default (main) pass, which reaches this container only
 * after everything nested inside it has had the event and declined it. So the shutter, and every
 * control the bands hold, still work; what this catches is the rest of the screen.
 */
private fun Modifier.swallowTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
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
internal const val CAMERA_COUNTDOWN_TAG = "in-app-camera-countdown"
internal const val CAPTURE_FAILED_MESSAGE = "That photo didn't save. Try again."

/**
 * The strip's chips for this session, in order along the edge. The flash chip only when the bound
 * camera has a flash unit: it would hide itself anyway, but a chip that composes nothing would
 * still take a slot. Read in composition, so the chip arrives when the bind reports the unit. The
 * grid chip always, after it (2026-09-22): every camera can draw a grid.
 */
private fun stripChips(
    session: CameraCaptureSession,
    timerMode: TimerMode,
    onTimerModeChanged: (TimerMode) -> Unit,
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
): List<StripChip> = buildList {
    if (session.hasFlashUnit) add { deviceRotation, displayRotation -> FlashChip(session, deviceRotation, displayRotation) }
    add { deviceRotation, displayRotation -> TimerChip(timerMode, onTimerModeChanged, deviceRotation, displayRotation) }
    add { deviceRotation, displayRotation -> GridChip(gridMode, onGridModeChanged, deviceRotation, displayRotation) }
}
