package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.FakeCameraGridModeRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The grid chip through its real entry point: the real [InAppCameraDialog] over the fake session,
 * its grid mode from a real [CameraGridModeViewModel] over [FakeCameraGridModeRepository], wired
 * the way `MainActivity` wires them. A tap writes the repository, and what the chip shows is what
 * the repository holds, not what was tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraGridChipTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private fun open(repository: FakeCameraGridModeRepository, session: FakeCameraCaptureSession = FakeCameraCaptureSession()) {
        val vm = CameraGridModeViewModel(repository::getGridMode, repository::setGridMode, ErrorLog { _, _, _ -> })
        composeRule.setContent {
            val mode by vm.mode.collectAsState()
            InAppCameraDialog(
                session = session,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                lockToPortrait = false,
                onPhotoCaptured = {},
                onDismiss = {},
                gridMode = mode,
                onGridModeChanged = vm::onGridModeChanged,
                levelProvider = FakeLevelProvider(),
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
    }

    private fun chip() = composeRule.onNodeWithTag(CAMERA_GRID_CHIP_TAG)

    @Test
    fun `a tap writes the next mode, and the chip then shows it, Off to Grid to Grid and Level to Off`() {
        val repository = FakeCameraGridModeRepository()
        open(repository)
        chip().assertContentDescriptionEquals(GRID_OFF_LABEL)

        for ((mode, label) in listOf(GridMode.Grid to GRID_ON_LABEL, GridMode.GridLevel to GRID_AND_LEVEL_LABEL, GridMode.Off to GRID_OFF_LABEL)) {
            chip().performClick()
            composeRule.waitForIdle()
            assertEquals("written to the repository", mode, repository.stored)
            chip().assertContentDescriptionEquals(label)
        }
    }

    @Test
    fun `a write that fails leaves the chip showing what is stored`() {
        val repository = FakeCameraGridModeRepository().apply { failWrites = true }
        open(repository)

        chip().performClick()
        composeRule.waitForIdle()

        assertEquals("the write was attempted", 1, repository.writes)
        chip().assertContentDescriptionEquals(GRID_OFF_LABEL)
    }

    @Test
    fun `the stored mode is what the chip shows when the camera opens`() {
        open(FakeCameraGridModeRepository(GridMode.GridLevel))
        chip().assertContentDescriptionEquals(GRID_AND_LEVEL_LABEL)
    }

    @Test
    fun `the grid chip comes after the flash chip`() {
        open(FakeCameraGridModeRepository())
        val flash = composeRule.onNodeWithTag(CAMERA_FLASH_CHIP_TAG).getBoundsInRoot()
        val grid = chip().getBoundsInRoot()
        assertTrue("after, along the strip: $flash then $grid", grid.left >= flash.right)
    }

    /** Real touches across the chip's bounds, not a semantic click (CLAUDE.md): each must reach it. */
    @Test
    fun `a real touch anywhere on the grid chip reaches it`() {
        val repository = FakeCameraGridModeRepository()
        open(repository)
        val b = chip().fetchSemanticsNode().boundsInRoot
        val inset = 0.2f
        val points = listOf(
            b.center,
            Offset(b.left + b.width * inset, b.top + b.height * inset),
            Offset(b.right - b.width * inset, b.top + b.height * inset),
            Offset(b.left + b.width * inset, b.bottom - b.height * inset),
            Offset(b.right - b.width * inset, b.bottom - b.height * inset),
        )
        points.forEachIndexed { i, point ->
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("touch ${i + 1} at $point reached the chip", i + 1, repository.writes)
        }
    }
}
