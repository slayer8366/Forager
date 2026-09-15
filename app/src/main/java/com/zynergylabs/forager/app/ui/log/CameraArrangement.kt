package com.zynergylabs.forager.app.ui.log

/**
 * Which of the camera dialog's two arrangements a session uses — chosen once when the camera
 * opens and held for the life of the dialog. Owner's requirement, device check on `51882cb`, step
 * 3.4: nothing in the camera layout moves while the camera is open, and in a landscape window the
 * shutter is on the right edge, never along the bottom.
 *
 * ## Why this is decided from the setting and the window's shape at open, and from nothing else
 *
 * The window lock ([LockWindowOrientation]) is what makes the choice safe to hold. With "Lock
 * camera to portrait" **off** the window is `SCREEN_ORIENTATION_LOCKED` — pinned to whatever it
 * was when the camera opened, so its shape at open *is* its shape for the whole session, and the
 * arrangement follows it. With the setting **on** the window is forced portrait, so the arrangement
 * is portrait whatever the window's shape was at the moment of opening: reading the window there
 * would see the pre-flip landscape and hold the wrong answer. The two inputs are the setting and
 * the shape; the function is pure so both columns are tested without a window.
 *
 * Where the platform ignores the lock (Android 16 on screens 600dp and wider, for this app's API
 * target) the window can turn after open and the arrangement is held anyway — that is the
 * requirement, no reflow, not an oversight.
 */
internal enum class CameraArrangement {
    /** Today's layout: Done top-left, count and shutter along the bottom, controls turning in place. */
    Portrait,

    /** Shutter on the right edge, vertically centred, count beside it; Done top-left; nothing turns. */
    Landscape,
}

internal fun cameraArrangement(lockToPortrait: Boolean, windowIsLandscape: Boolean): CameraArrangement =
    if (!lockToPortrait && windowIsLandscape) CameraArrangement.Landscape else CameraArrangement.Portrait
