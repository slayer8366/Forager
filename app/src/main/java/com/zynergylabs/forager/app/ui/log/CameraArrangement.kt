package com.zynergylabs.forager.app.ui.log

import android.view.Surface

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
 * The window lock (`LockWindowOrientation`, since removed) is what makes the choice safe to hold. With "Lock
 * camera to portrait" **off** the window is `SCREEN_ORIENTATION_LOCKED` — pinned to whatever it
 * was when the camera opened, so its shape *and its rotation* at open are its shape and rotation
 * for the whole session, and the arrangement follows them. With the setting **on** the window is
 * forced portrait, so the arrangement is portrait whatever the window's shape was at the moment of
 * opening: reading the window there would see the pre-flip landscape and hold the wrong answer.
 * The function is pure so every combination is tested without a window.
 *
 * **Superseded, 2026-09-18 — the arrangement follows the window.** This doc used to end: *"Where
 * the platform ignores the lock (Android 16 on screens 600dp and wider, for this app's API target)
 * the window can turn after open and the arrangement is held anyway — that is the requirement, no
 * reflow, not an oversight."* The owner's ruling reverses it: holding produces a landscape layout
 * in a portrait window, which is exactly the mismatch a background-and-return produced on the phone
 * (the platform re-resolves `SCREEN_ORIENTATION_LOCKED` when the Activity becomes visible again),
 * and the old ruling kept that state deliberately in the one place it is most visible, on the
 * largest screen. It was a note about something not under control, not a decision that a
 * mismatched layout is wanted. `InAppCameraDialog` now re-derives the arrangement whenever the
 * window's shape or rotation changes; "chosen once at open" above is no longer true, and the
 * stop-and-ask that caught the difference is recorded in `docs/audits/README.md`, 2026-09-18.
 *
 * **Superseded again, 2026-09-19 — the lock itself is gone for setting-off.** The window now
 * follows the device (`SCREEN_ORIENTATION_FULL_SENSOR`, [RequestWindowOrientation]) so the system
 * status bar can be on the phone's top edge in every hold, and the re-derivation above is what
 * happens on every turn rather than only across a return. The paragraph beginning "The window lock
 * is what makes the choice safe to hold" is history: the choice is not held.
 *
 * **Open finding, 2026-09-19 — there is no reverse-portrait case, and `FULL_SENSOR` makes one
 * reachable for the first time.** This function returns [CameraArrangement.Portrait] for *any*
 * non-landscape window, so at `ROTATION_180` the shutter goes to the screen's bottom — which is the
 * device's **punch-hole** edge in that hold — and the strip to the port edge. Exactly inverted, the
 * same shape as the 2026-09-17 landscape finding. It could not happen before: `SCREEN_ORIENTATION_LOCKED`
 * never produced a reverse-portrait window and `SCREEN_ORIENTATION_PORTRAIT` excludes one, which is
 * why "portrait was already correct at all four rotations" held. Measured on the emulator with the
 * window following the device: shutter at [446,2033]-[635,2222] of a 1080x2400 window at
 * `mRotation=2`. A fourth arrangement is what the owner's own rule requires; the owner has ruled to
 * add it, as its own dispatch after this window change — reverse portrait is the foraging gill-shot
 * hold, phone flipped end over end to get the lens near the ground, so excluding it is not an
 * option. **Still open as of this change.**
 *
 * **Setting on is correct by coincidence, not by handling.** With the setting on the lock requests
 * `PORTRAIT` and this function returns [CameraArrangement.Portrait] whatever the window is, so its
 * two window inputs are constant and cannot disagree with the window. It goes through the same
 * `remember` as setting off. It is not protected: the moment either input stops being constant —
 * a platform that ignores the portrait request, say — it is on the same path as everything else.
 */
internal enum class CameraArrangement {
    /** Today's layout: Done top-left, count and shutter along the bottom, controls turning in place. */
    Portrait,

    /**
     * A landscape window at `Surface.ROTATION_90`, where the device's port edge is the screen's
     * **right**: shutter on the right, vertically centred, count to its left; Done top-left.
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

/**
 * Which edge of the *window* a physical edge of the device is on, for one arrangement. Screen
 * coordinates rotate and device anatomy does not, so this is the one place the two are related:
 * everything that positions against the charger-port edge or the punch-hole edge asks here, and
 * nothing names a screen side directly.
 *
 * Valid for the arrangement the window currently has. The window turns with the device
 * ([RequestWindowOrientation], since 2026-09-19) and the arrangement is re-derived in the same
 * composition that sees the turn (`InAppCameraDialog`), so the mapping and the window never
 * disagree for longer than that; the earlier wording, "only valid while the window is locked",
 * described a window that was not supposed to turn at all.
 */
internal enum class ScreenEdge {
    Top, Bottom, Left, Right;

    val opposite: ScreenEdge
        get() = when (this) {
            Top -> Bottom
            Bottom -> Top
            Left -> Right
            Right -> Left
        }

    /** A top or bottom edge runs along the window's width; a left or right edge along its height. */
    val isHorizontal: Boolean get() = this == Top || this == Bottom
}

/**
 * The charger-port edge, as a window edge. This is the value the three arrangements always implied
 * — [CameraArrangement.Portrait]'s `BottomCenter` shutter, the two landscapes' `CenterEnd` and
 * `CenterStart` — named once so that the punch-hole edge can be its opposite rather than a fourth
 * hand-written case.
 *
 * `Portrait` covers `ROTATION_0` and `ROTATION_180` alike: with the window locked at either, the
 * window's bottom *is* the device's port edge, which is why opening in portrait has always been
 * right whichever way up the phone is held.
 */
internal fun portEdge(arrangement: CameraArrangement): ScreenEdge = when (arrangement) {
    CameraArrangement.Portrait -> ScreenEdge.Bottom
    CameraArrangement.LandscapePortRight -> ScreenEdge.Right
    CameraArrangement.LandscapePortLeft -> ScreenEdge.Left
}

/** The punch-hole edge: the port edge's opposite, by the device anatomy the spec fixes, and by nothing else. */
internal fun punchHoleEdge(arrangement: CameraArrangement): ScreenEdge = portEdge(arrangement).opposite
