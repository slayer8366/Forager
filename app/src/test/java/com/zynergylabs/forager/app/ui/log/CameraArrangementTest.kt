package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import androidx.compose.ui.unit.dp
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

    /**
     * The camera top strip's rule, in the device's terms: at every window rotation the app can
     * open in with the setting off, the strip's edge is where the device's punch-hole edge is on
     * screen, per [deviceTopEdgeOnScreen]'s table (read from the platform, not derived from this
     * code). And it is never the shutter's edge.
     *
     * `ROTATION_180` is not in the first loop, deliberately: [cameraArrangement] treats a portrait
     * window at 180 as `Portrait`, putting the shutter at screen-bottom (the device's top there).
     * The strip, as the shutter's opposite, inherits that. The AVD never put a window at 180
     * (2026-09-18); unverified on the reference device. The upside-down *hold*, which does happen,
     * is a window at 0 and is the first case below, and `InAppCameraDialogTest` covers it with the
     * sensor turned.
     */
    @Test
    fun `the strip's edge is the device's punch-hole edge at each window rotation it can open in`() {
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_270)) {
            val windowIsLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            val arrangement = cameraArrangement(lockToPortrait = false, windowIsLandscape = windowIsLandscape, displayRotation = rotation)
            assertEquals("rotation $rotation ($arrangement)", deviceTopEdgeOnScreen(rotation), punchHoleEdge(arrangement))
        }
        for (arrangement in CameraArrangement.entries) {
            assertEquals("the strip is opposite the shutter in $arrangement", portEdge(arrangement).opposite, punchHoleEdge(arrangement))
        }
    }

    /**
     * The region model at the one ratio that exists. The strip band is on the punch-hole edge and
     * the shutter band on the port edge, in every arrangement; both are zero-thick. **This is the
     * degenerate case and the only one**: with no second ratio, nothing here can show the bands
     * would grow correctly, and this test does not claim to (see [CameraRegions]).
     */
    @Test
    fun `at full-bleed the bands are on the device's two short edges and zero-thick`() {
        for (arrangement in CameraArrangement.entries) {
            val regions = cameraRegions(arrangement)
            assertEquals("$arrangement strip band", punchHoleEdge(arrangement), regions.stripEdge)
            assertEquals("$arrangement shutter band", portEdge(arrangement), regions.shutterEdge)
            assertEquals("$arrangement: the two bands are opposite each other", regions.stripEdge.opposite, regions.shutterEdge)
            assertEquals(0.dp, regions.stripBandThickness)
            assertEquals(0.dp, regions.shutterBandThickness)
        }
    }
}
