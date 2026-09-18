package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Which of the camera dialog's arrangements a session uses — chosen once when the camera opens and
 * held for the life of the dialog. Owner's requirement, device check on `51882cb`, step 3.4:
 * nothing in the camera layout moves while the camera is open, and in a landscape window the
 * shutter is on the **charger-port edge of the device**, never along the bottom.
 *
 * ## The rule is a physical edge, not a screen side (device check v4 run, 2026-09-17)
 *
 * The shutter belongs on the device's charger-port edge in every orientation. That edge is fixed
 * in the device's own anatomy — screen facing the user, port down, punch-hole up — so rotating the
 * phone never moves it physically, only in screen coordinates. The reason is a motor habit rather
 * than aesthetics: a user learns where the shutter is on the phone, and a shutter that lands
 * somewhere else defeats that. On the S26 Ultra it also lands under the punch-hole cut-out.
 *
 * **This used to be written as "the right edge", and that is true of only one of the two
 * landscapes.** The first version of this function took `windowIsLandscape: Boolean` and had no
 * rotation input at all, so both landscapes got the same screen side and exactly one of them was
 * necessarily wrong. Observed on the device at `Surface.ROTATION_270` (port on the user's left,
 * shutter drawn on the right, i.e. the punch-hole end); `ROTATION_90` was correct by coincidence
 * of which landscape was written down. See `docs/audits/` for the run.
 *
 * ## Mapping a rotation to the port edge, and the assumption in it
 *
 * Android exposes no API for where the charger port is. What it exposes is the display's rotation
 * from the device's natural orientation, so this maps **the natural-orientation bottom edge** and
 * assumes the port is on it — true of a portrait-natural phone, which is what this app targets
 * (`minSdk` 26, phone layouts throughout). At `ROTATION_90` that edge is the screen's right; at
 * `ROTATION_270` it is the screen's left. A landscape-natural device — some tablets — would invert
 * the mapping, and this has been confirmed on exactly one device, so it is written here as an
 * assumption rather than a fact.
 *
 * Portrait needs no such branch: with the window locked, `BottomCenter` *is* the natural-bottom
 * edge whatever the phone is doing, which is why opening in portrait was already correct at all
 * four rotations while opening in landscape was not.
 *
 * ## Why this is decided from the setting and the window's shape at open, and from nothing else
 *
 * The window lock ([LockWindowOrientation]) is what makes the choice safe to hold. With "Lock
 * camera to portrait" **off** the window is `SCREEN_ORIENTATION_LOCKED` — pinned to whatever it
 * was when the camera opened, so its shape *and its rotation* at open are its shape and rotation
 * for the whole session, and the arrangement follows them. With the setting **on** the window is
 * forced portrait, so the arrangement is portrait whatever the window's shape was at the moment of
 * opening: reading the window there would see the pre-flip landscape and hold the wrong answer.
 * The function is pure so every combination is tested without a window.
 *
 * Where the platform ignores the lock (Android 16 on screens 600dp and wider, for this app's API
 * target) the window can turn after open and the arrangement is held anyway — that is the
 * requirement, no reflow, not an oversight.
 */
internal enum class CameraArrangement {
    /** Count and shutter along the bottom, controls turning in place. (Done, formerly top-left, was removed 2026-09-18.) */
    Portrait,

    /**
     * A landscape window at `Surface.ROTATION_90`, where the device's port edge is the screen's
     * **right**: shutter on the right, vertically centred, count to its left.
     */
    LandscapePortRight,

    /**
     * A landscape window at `Surface.ROTATION_270`, where the device's port edge is the screen's
     * **left**: the mirror of [LandscapePortRight], shutter on the left with the count to its
     * right. The case the 2026-09-17 device-check run found drawn on the wrong edge.
     */
    LandscapePortLeft,
}

/**
 * The arrangement for a session opening now. [displayRotation] is a `Surface.ROTATION_*` value,
 * the window's own rotation at open — not the sensor's reading, which is a different quantity and
 * moves during a session (see `CameraCaptureSession.deviceRotation` and [rotateWithDevice]).
 *
 * Unrecognised rotations fall to [CameraArrangement.LandscapePortRight] rather than throwing: the
 * caller is a layout, and an arrangement is needed for whatever the platform reports. `ROTATION_0`
 * and `ROTATION_180` cannot reach the landscape branches through the real call site, since a
 * portrait-natural phone reporting either of those has a portrait window and
 * [windowIsLandscape] is then false; they are defined here so the function is total and so the
 * test can state what each of the four produces.
 */
internal fun cameraArrangement(
    lockToPortrait: Boolean,
    windowIsLandscape: Boolean,
    displayRotation: Int,
): CameraArrangement = when {
    lockToPortrait || !windowIsLandscape -> CameraArrangement.Portrait
    displayRotation == Surface.ROTATION_270 -> CameraArrangement.LandscapePortLeft
    else -> CameraArrangement.LandscapePortRight
}

/** A side of the screen, in screen coordinates: absolute, not start/end, because the edges it names are physical ones. */
internal enum class ScreenEdge {
    Top,
    Bottom,
    Left,
    Right,
    ;

    val opposite: ScreenEdge
        get() = when (this) {
            Top -> Bottom
            Bottom -> Top
            Left -> Right
            Right -> Left
        }
}

/**
 * Where the device's charger-port edge is on screen in [arrangement]: the edge the shutter is on.
 * Written down as a value for the first time here (camera-top-strip dispatch, 2026-09-18); until
 * then it was implicit in each arrangement's alignment. Carries this file's assumption, a
 * portrait-natural phone with the port on its natural bottom.
 */
internal fun portEdge(arrangement: CameraArrangement): ScreenEdge = when (arrangement) {
    CameraArrangement.Portrait -> ScreenEdge.Bottom
    CameraArrangement.LandscapePortRight -> ScreenEdge.Right
    CameraArrangement.LandscapePortLeft -> ScreenEdge.Left
}

/**
 * Where the device's punch-hole edge is on screen in [arrangement]: the camera's top strip goes
 * there. Simply the port edge's opposite, because the two are the device's opposite short edges
 * and the window is locked while the camera is open, so no separate logic is needed and none is
 * written. It inherits [cameraArrangement]'s one blind spot: a portrait window at
 * `Surface.ROTATION_180` is treated as `Portrait`, where screen-top would be the port edge. On the
 * AVD a window never reaches 180 (checked 2026-09-18); unverified on the reference device.
 */
internal fun punchHoleEdge(arrangement: CameraArrangement): ScreenEdge = portEdge(arrangement).opposite

/**
 * The camera screen's three regions (top-strip dispatch v2, owner's option A, 2026-09-18): the
 * **viewfinder region**, whatever the ratio produces; the **strip band** on its punch-hole side;
 * the **shutter band** on its charger-port side. Every control is anchored inside a band, never
 * against a screen edge, so a later aspect ratio is "the viewfinder region got smaller and the
 * bands grew" rather than a revisit of every position.
 *
 * **Exactly one ratio exists: full-bleed.** The viewfinder is the whole frame and both bands are
 * zero-thick, so each band sizes to its own content and that content overlaps onto the image —
 * which is where the controls have always been. No ratio setting, no second case. **That makes
 * the band model correct by construction and by review, not by a test that could fail**: with no
 * second ratio there is nothing to test the bands against, and nothing here should be read as
 * having verified them. What is tested is what can be — the shutter on the port edge, the strip
 * on the punch-hole edge, at every rotation.
 */
internal data class CameraRegions(
    /** The band the top strip lives in: on the device's punch-hole edge. */
    val stripEdge: ScreenEdge,
    /** The band the shutter and readout live in: on the device's charger-port edge. */
    val shutterEdge: ScreenEdge,
    /** Zero at full-bleed: the band sizes to its content, which overlaps the viewfinder. */
    val stripBandThickness: Dp,
    /** Zero at full-bleed, as above. */
    val shutterBandThickness: Dp,
)

/** The regions for [arrangement] at the one ratio that exists. See [CameraRegions]. */
internal fun cameraRegions(arrangement: CameraArrangement): CameraRegions = CameraRegions(
    stripEdge = punchHoleEdge(arrangement),
    shutterEdge = portEdge(arrangement),
    stripBandThickness = 0.dp,
    shutterBandThickness = 0.dp,
)
