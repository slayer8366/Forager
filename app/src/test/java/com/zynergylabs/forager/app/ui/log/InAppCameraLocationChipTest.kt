package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The strip's Location chip (decision B8 / Closed decision D, 2026-09-26), driven through the real
 * [InAppCameraDialog]: it shows and sets Settings' "Automatically Save Location to Photos", one
 * value, no per-shot override. The dialog is handed the value and the handler as parameters, the
 * way `AvailabilityScreen` hands them down; the path from the screen is
 * `AvailabilityScreenInAppCameraTest`'s.
 *
 * [stores] models a handler that stores what it is asked (as `onAutoSaveLocationToPhotosChanged`
 * does); with it off, the handler only records, so a chip keeping a copy of its own would show
 * the tap and one reading its parameter would not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppCameraLocationChipTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private var saveLocation by mutableStateOf(true)
    private var stores = true
    private val requests = mutableListOf<Boolean>()

    private fun setDialog(initial: Boolean) {
        saveLocation = initial
        composeRule.setContent {
            InAppCameraDialog(
                session = FakeCameraCaptureSession(),
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                lockToPortrait = false,
                onPhotoCaptured = {},
                onDismiss = {},
                gridMode = GridMode.Off,
                onGridModeChanged = {},
                autoSaveLocationToPhotos = saveLocation,
                onAutoSaveLocationToPhotosChanged = { requested ->
                    requests += requested
                    if (stores) saveLocation = requested
                },
                levelProvider = FakeLevelProvider(),
                viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
            )
        }
        composeRule.waitForIdle()
    }

    private fun chip() = composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG)

    @Test
    fun `the chip shows the setting, On`() {
        setDialog(initial = true)
        chip().assertContentDescriptionEquals("Save location: On")
    }

    @Test
    fun `the chip shows the setting, Off`() {
        setDialog(initial = false)
        chip().assertContentDescriptionEquals("Save location: Off")
    }

    @Test
    fun `a tap calls the handler with the toggled value, and the chip shows it once stored`() {
        setDialog(initial = true)

        chip().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(false), requests)
        chip().assertContentDescriptionEquals(LOCATION_OFF_LABEL)

        chip().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), requests)
        chip().assertContentDescriptionEquals(LOCATION_ON_LABEL)
    }

    @Test
    fun `the chip shows what is stored, not a copy of its own`() {
        stores = false
        setDialog(initial = true)

        chip().performClick()
        composeRule.waitForIdle()

        assertEquals("the handler was asked", listOf(false), requests)
        chip().assertContentDescriptionEquals(LOCATION_ON_LABEL)
    }

    @Test
    fun `each state has its own glyph and label`() {
        assertEquals(LocationGlyph(Icons.Filled.LocationOn, "Save location: On"), locationGlyph(true))
        assertEquals(LocationGlyph(Icons.Filled.LocationOff, "Save location: Off"), locationGlyph(false))
    }

    /**
     * **A finger on the Location chip reaches it**, over the viewfinder: real touches at screen
     * coordinates, five across the chip's own bounds (CLAUDE.md: a semantic click asserts wiring,
     * not routing, and a finger is not a point). Each touch reaches the handler with the toggle of
     * what is stored.
     */
    @Test
    fun `a real touch anywhere on the location chip reaches it, over the viewfinder`() {
        setDialog(initial = true)
        val bounds = chip().fetchSemanticsNode().boundsInRoot
        val inset = 0.2f
        val points = listOf(
            bounds.center,
            Offset(bounds.left + bounds.width * inset, bounds.top + bounds.height * inset),
            Offset(bounds.right - bounds.width * inset, bounds.top + bounds.height * inset),
            Offset(bounds.left + bounds.width * inset, bounds.bottom - bounds.height * inset),
            Offset(bounds.right - bounds.width * inset, bounds.bottom - bounds.height * inset),
        )
        val expected = listOf(false, true, false, true, false)

        points.forEachIndexed { i, point ->
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("touch ${i + 1} at $point reached the handler", expected.take(i + 1), requests)
        }
    }
}
