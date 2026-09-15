package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertEquals
import org.junit.Test

/** [cameraArrangement], both columns: the setting and the window's shape at open. Pure. */
class CameraArrangementTest {

    @Test
    fun `setting off follows the window's shape at open`() {
        assertEquals(CameraArrangement.Landscape, cameraArrangement(lockToPortrait = false, windowIsLandscape = true))
        assertEquals(CameraArrangement.Portrait, cameraArrangement(lockToPortrait = false, windowIsLandscape = false))
    }

    @Test
    fun `setting on is portrait whatever the window was when the camera opened`() {
        // The window is about to be forced portrait; reading its pre-flip landscape shape would hold the wrong answer.
        assertEquals(CameraArrangement.Portrait, cameraArrangement(lockToPortrait = true, windowIsLandscape = true))
        assertEquals(CameraArrangement.Portrait, cameraArrangement(lockToPortrait = true, windowIsLandscape = false))
    }
}
