package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [InAppCameraViewModel] holds one value and two transitions; this pins them. What it cannot
 * pin, and what the class doc claims, is the platform contract that makes a ViewModel the right
 * holder: retained across a configuration change, cleared on a non-configuration destruction.
 * That is `ComponentActivity`'s behaviour, exercised on the device check ("Don't keep
 * activities" with the camera open, and a rotation with it open), not here.
 */
class InAppCameraViewModelTest {

    @Test
    fun `starts closed`() {
        assertNull(InAppCameraViewModel().target.value)
    }

    @Test
    fun `open records the surface that asked, and close clears it`() {
        val viewModel = InAppCameraViewModel()

        viewModel.open(InAppCameraTarget.LOG_ENTRY)
        assertEquals(InAppCameraTarget.LOG_ENTRY, viewModel.target.value)

        viewModel.close()
        assertNull(viewModel.target.value)
    }

    @Test
    fun `a second open replaces the target rather than stacking`() {
        val viewModel = InAppCameraViewModel()

        viewModel.open(InAppCameraTarget.ALBUM)
        viewModel.open(InAppCameraTarget.CARTOGRAPHY_ENTRY)

        assertEquals(InAppCameraTarget.CARTOGRAPHY_ENTRY, viewModel.target.value)
    }
}
