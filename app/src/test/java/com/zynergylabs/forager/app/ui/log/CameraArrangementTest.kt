package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [cameraArrangement]: the setting, the window's shape at open, and the window's rotation at open.
 * Pure.
 *
 * The rotation column exists because the shutter belongs on the device's **charger-port edge**,
 * which is a physical edge, and the two landscapes put that edge on opposite screen sides. The
 * earlier version of this function had no rotation input, so both landscapes got the same screen
 * side and one of them was necessarily wrong — found on a device at `ROTATION_270`, where the
 * shutter drew on the punch-hole end. Every expectation below is therefore written as *which
 * physical edge the shutter lands on*, not as "left" or "right".
 */
class CameraArrangementTest {

    /** What each arrangement means physically, so the assertions below read as the rule rather than as enum names. */
    private fun portEdgeOf(arrangement: CameraArrangement): String = when (arrangement) {
        CameraArrangement.Portrait -> "the port edge is the window's bottom"
        CameraArrangement.LandscapePortRight -> "the port edge is the window's right"
        CameraArrangement.LandscapePortLeft -> "the port edge is the window's left"
    }

    @Test
    fun `all four rotations, setting off, the shutter lands on the device's port edge`() {
        // ROTATION_0 and ROTATION_180: a portrait-natural phone reports a portrait window, so the
        // portrait arrangement holds and its BottomCenter shutter is the natural-bottom edge
        // whatever the phone is doing. This is why opening in portrait was already correct at all
        // four rotations while opening in landscape was not.
        assertEquals(
            "upright portrait: " + portEdgeOf(CameraArrangement.Portrait),
            CameraArrangement.Portrait,
            cameraArrangement(lockToPortrait = false, windowIsLandscape = false, displayRotation = Surface.ROTATION_0),
        )
        assertEquals(
            "inverted portrait: " + portEdgeOf(CameraArrangement.Portrait),
            CameraArrangement.Portrait,
            cameraArrangement(lockToPortrait = false, windowIsLandscape = false, displayRotation = Surface.ROTATION_180),
        )

        // The two landscapes are mirror images and must not share an answer.
        assertEquals(
            "ROTATION_90, port on the user's right: " + portEdgeOf(CameraArrangement.LandscapePortRight),
            CameraArrangement.LandscapePortRight,
            cameraArrangement(lockToPortrait = false, windowIsLandscape = true, displayRotation = Surface.ROTATION_90),
        )
        assertEquals(
            "ROTATION_270, port on the user's left: " + portEdgeOf(CameraArrangement.LandscapePortLeft),
            CameraArrangement.LandscapePortLeft,
            cameraArrangement(lockToPortrait = false, windowIsLandscape = true, displayRotation = Surface.ROTATION_270),
        )
    }

    @Test
    fun `the two landscapes never resolve to the same arrangement`() {
        // The defect in one line: the old function could not tell these apart, and any future
        // change that collapses them reintroduces it.
        val ninety = cameraArrangement(lockToPortrait = false, windowIsLandscape = true, displayRotation = Surface.ROTATION_90)
        val twoSeventy = cameraArrangement(lockToPortrait = false, windowIsLandscape = true, displayRotation = Surface.ROTATION_270)
        org.junit.Assert.assertNotEquals(
            "a landscape arrangement that ignores the rotation puts the shutter on the punch-hole edge in one of the two",
            ninety,
            twoSeventy,
        )
    }

    @Test
    fun `setting on is portrait whatever the window was when the camera opened`() {
        // The window is about to be forced portrait; reading its pre-flip landscape shape would hold the wrong answer.
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            assertEquals(
                "setting on, rotation $rotation, landscape window",
                CameraArrangement.Portrait,
                cameraArrangement(lockToPortrait = true, windowIsLandscape = true, displayRotation = rotation),
            )
            assertEquals(
                "setting on, rotation $rotation, portrait window",
                CameraArrangement.Portrait,
                cameraArrangement(lockToPortrait = true, windowIsLandscape = false, displayRotation = rotation),
            )
        }
    }

    @Test
    fun `an unrecognised rotation still yields a landscape arrangement rather than throwing`() {
        // The caller is a layout and needs an answer for whatever the platform reports.
        assertEquals(
            CameraArrangement.LandscapePortRight,
            cameraArrangement(lockToPortrait = false, windowIsLandscape = true, displayRotation = 99),
        )
    }
}
