package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [cameraClosesAfterAbsence] and [InAppCameraViewModel]'s absence handling. Pure and headless: the
 * clock is a number passed in, never read, so there is no harness clock to pin and nothing that
 * could silently leave every case running at the same instant. The two sides of the threshold are
 * different literals in the test body, which is the strongest form of the positive control the
 * dispatch asked for — under and over cannot collapse into each other without the assertion
 * changing.
 */
class CameraAbsenceTest {

    private val threshold = CAMERA_ABSENCE_TIMEOUT_MILLIS

    @Test
    fun `the threshold is the owner's four minutes`() {
        // Stated as the arithmetic rather than the constant, so a change to the constant has to be
        // a deliberate edit here too.
        assertEquals(4L * 60 * 1000, threshold)
    }

    @Test
    fun `under the threshold the camera stays open`() {
        assertFalse(cameraClosesAfterAbsence(leftAtElapsedMillis = 1_000, returnedAtElapsedMillis = 1_000 + threshold - 1))
        // A picker round trip, which is the case this threshold exists to protect.
        assertFalse(cameraClosesAfterAbsence(leftAtElapsedMillis = 1_000, returnedAtElapsedMillis = 4_000))
    }

    @Test
    fun `at and over the threshold the camera closes`() {
        assertTrue("four minutes or more, so the boundary closes", cameraClosesAfterAbsence(1_000, 1_000 + threshold))
        assertTrue(cameraClosesAfterAbsence(1_000, 1_000 + threshold + 1))
        assertTrue(cameraClosesAfterAbsence(0, threshold * 10))
    }

    @Test
    fun `no recorded departure never closes anything`() {
        assertFalse(cameraClosesAfterAbsence(leftAtElapsedMillis = null, returnedAtElapsedMillis = Long.MAX_VALUE))
    }

    @Test
    fun `a negative interval is no absence rather than an enormous one`() {
        // elapsedRealtime cannot go backwards, but a caller could pass this and it must not close.
        assertFalse(cameraClosesAfterAbsence(leftAtElapsedMillis = 10_000, returnedAtElapsedMillis = 1_000))
    }

    @Test
    fun `a short absence leaves the session exactly as it was`() {
        val viewModel = InAppCameraViewModel()
        viewModel.open(InAppCameraTarget.LOG_ENTRY)

        viewModel.onLeftApp(1_000)
        viewModel.onReturnedToApp(1_000 + threshold - 1)

        assertEquals(InAppCameraTarget.LOG_ENTRY, viewModel.target.value)
    }

    @Test
    fun `a long absence closes the camera`() {
        val viewModel = InAppCameraViewModel()
        viewModel.open(InAppCameraTarget.ALBUM)

        viewModel.onLeftApp(1_000)
        viewModel.onReturnedToApp(1_000 + threshold)

        assertEquals(null, viewModel.target.value)
    }

    @Test
    fun `returning without having left changes nothing`() {
        val viewModel = InAppCameraViewModel()
        viewModel.open(InAppCameraTarget.CARTOGRAPHY_ENTRY)

        viewModel.onReturnedToApp(Long.MAX_VALUE)

        assertEquals(InAppCameraTarget.CARTOGRAPHY_ENTRY, viewModel.target.value)
    }

    @Test
    fun `a departure is consumed by its own return, so a later return cannot re-trigger it`() {
        val viewModel = InAppCameraViewModel()
        viewModel.open(InAppCameraTarget.LOG_ENTRY)

        viewModel.onLeftApp(1_000)
        viewModel.onReturnedToApp(2_000)
        // Still open, and the old departure must not be lying around to be re-measured.
        assertEquals(InAppCameraTarget.LOG_ENTRY, viewModel.target.value)

        viewModel.onReturnedToApp(1_000 + threshold * 5)

        assertEquals("a second ON_START with no ON_STOP between is not a four-minute absence", InAppCameraTarget.LOG_ENTRY, viewModel.target.value)
    }

    @Test
    fun `an absence recorded with the camera closed is not inherited by the next session`() {
        val viewModel = InAppCameraViewModel()

        viewModel.onLeftApp(1_000)
        viewModel.open(InAppCameraTarget.ALBUM)
        viewModel.onReturnedToApp(1_000 + threshold * 5)

        assertEquals("opening is a fresh session, not the tail of an old absence", InAppCameraTarget.ALBUM, viewModel.target.value)
    }

    @Test
    fun `opening again after a long absence clears the departure`() {
        val viewModel = InAppCameraViewModel()
        viewModel.open(InAppCameraTarget.LOG_ENTRY)
        viewModel.onLeftApp(1_000)

        viewModel.open(InAppCameraTarget.ALBUM)
        viewModel.onReturnedToApp(1_000 + threshold * 5)

        assertEquals(InAppCameraTarget.ALBUM, viewModel.target.value)
    }
}
