package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [cameraArrangement]: the window's shape and the window's rotation. Pure. (Its first input, "Lock camera to portrait", was
 * removed in dispatch 2026-10-13-03, with the test `setting on is portrait whatever the window was when the camera opened`.)
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
            cameraArrangement(windowIsLandscape = false, displayRotation = Surface.ROTATION_0),
        )
        assertEquals(
            "inverted portrait: " + portEdgeOf(CameraArrangement.Portrait),
            CameraArrangement.Portrait,
            cameraArrangement(windowIsLandscape = false, displayRotation = Surface.ROTATION_180),
        )

        // RECORD -958 (the owner: "Yes", the camera to look the same both ways): both landscapes get ROTATION_90's arrangement,
        // the shutter on the screen's right. Was: "The two landscapes are mirror images and must not share an answer", with
        // ROTATION_270 expected as LandscapePortLeft.
        assertEquals(
            "ROTATION_90: " + portEdgeOf(CameraArrangement.LandscapePortRight),
            CameraArrangement.LandscapePortRight,
            cameraArrangement(windowIsLandscape = true, displayRotation = Surface.ROTATION_90),
        )
        assertEquals(
            "ROTATION_270, the same as ROTATION_90 (RECORD -958): " + portEdgeOf(CameraArrangement.LandscapePortRight),
            CameraArrangement.LandscapePortRight,
            cameraArrangement(windowIsLandscape = true, displayRotation = Surface.ROTATION_270),
        )
    }

    @Test
    fun `the two landscapes resolve to the same arrangement (RECORD -958)`() {
        // RECORD -958 reverses this test. Was `the two landscapes never resolve to the same arrangement`, assertNotEquals(ninety,
        // twoSeventy), "a landscape arrangement that ignores the rotation puts the shutter on the punch-hole edge in one of the two":
        // the owner has since ruled the layout identical at both rotations; where the photo's orientation comes from is untouched
        // (the sensor, not this function).
        val ninety = cameraArrangement(windowIsLandscape = true, displayRotation = Surface.ROTATION_90)
        val twoSeventy = cameraArrangement(windowIsLandscape = true, displayRotation = Surface.ROTATION_270)
        assertEquals("one layout at both rotations", ninety, twoSeventy)
    }

    @Test
    fun `an unrecognised rotation still yields a landscape arrangement rather than throwing`() {
        // The caller is a layout and needs an answer for whatever the platform reports.
        assertEquals(
            CameraArrangement.LandscapePortRight,
            cameraArrangement(windowIsLandscape = true, displayRotation = 99),
        )
    }
}

/**
 * [portEdge] and [punchHoleEdge]: the two physical edges as window edges, per arrangement. Every
 * expectation is a sentence in device anatomy — where the charger port is in that hold — and the
 * punch-hole edge is asserted as the port edge's opposite, never as its own table.
 */
class CameraEdgesTest {

    private fun arrangementAt(rotation: Int, landscape: Boolean) =
        cameraArrangement(windowIsLandscape = landscape, displayRotation = rotation)

    @Test
    fun `the port edge is the window's bottom in either portrait hold`() {
        assertEquals("upright: port at the bottom", ScreenEdge.Bottom, portEdge(arrangementAt(Surface.ROTATION_0, landscape = false)))
        assertEquals("upside down, window still locked: still the window's bottom", ScreenEdge.Bottom, portEdge(arrangementAt(Surface.ROTATION_180, landscape = false)))
    }

    @Test
    fun `the shutter's edge is the window's right at ROTATION_90 and ROTATION_270 alike`() {
        // RECORD -958: was `the port edge is the window's right at ROTATION_90 and left at ROTATION_270`, expecting ScreenEdge.Left at
        // ROTATION_270. The arrangement no longer follows the port, so the edge the shutter is drawn on is the right at both.
        assertEquals("ROTATION_90", ScreenEdge.Right, portEdge(arrangementAt(Surface.ROTATION_90, landscape = true)))
        assertEquals("ROTATION_270 (RECORD -958)", ScreenEdge.Right, portEdge(arrangementAt(Surface.ROTATION_270, landscape = true)))
    }

    @Test
    fun `the punch-hole edge is the port edge's opposite in every arrangement`() {
        for (arrangement in CameraArrangement.entries) {
            assertEquals(arrangement.name, portEdge(arrangement).opposite, punchHoleEdge(arrangement))
            org.junit.Assert.assertNotEquals("never the same edge: $arrangement", portEdge(arrangement), punchHoleEdge(arrangement))
        }
    }

    @Test
    fun `opposite is an involution and horizontal edges are top and bottom`() {
        for (edge in ScreenEdge.entries) {
            assertEquals(edge, edge.opposite.opposite)
            assertEquals(edge == ScreenEdge.Top || edge == ScreenEdge.Bottom, edge.isHorizontal)
        }
    }
}
