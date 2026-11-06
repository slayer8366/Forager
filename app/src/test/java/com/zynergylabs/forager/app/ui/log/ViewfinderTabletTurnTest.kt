package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.photo.CameraXCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
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
 * Dispatch 2026-10-17-08 (RECORD -1214, -1215; the owner: "Fix before release"). On the owner's tablet, every turn between
 * portrait and landscape with the camera open restarted the camera with a black flash. The owner's log showed why: the
 * viewfinder's `DisposableEffect` was keyed on "held right now", which is false at `ROTATION_0` and true at the other three,
 * so each turn that touched `ROTATION_0` detached and re-attached the surface provider and swapped the `PreviewView` between
 * a `SurfaceView` and a `TextureView`. On a device that is a new surface request, and the camera rebuilds its session.
 *
 * These tests drive the real [HoldNaturalOrientation] around the real [CameraXCaptureSession.Viewfinder], wired exactly as
 * `InAppCameraHost` wires it (`held = LocalNaturalOrientationHold.current`), and turn the display rotation the hold reads.
 * The Viewfinder attaches to a real, unbound CameraX [Preview]; what they read is how many times it attached
 * ([CameraXCaptureSession.viewfinderSurfaceAttachments]) and the `PreviewView`'s mode.
 *
 * **What these cannot show**, all device-only: CameraX's real surface request and session rebuild (no camera under
 * Robolectric), the platform's configuration change on a turn (the rotation the hold reads is set directly), whether the
 * picture is upright, and whether the `TextureView` at `ROTATION_0` looks the same as the `SurfaceView` did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewfinderTabletTurnTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private val session = CameraXCaptureSession(ApplicationProvider.getApplicationContext()).apply {
        installPreview(Preview.Builder().build())
    }

    private var rotation by mutableIntStateOf(Surface.ROTATION_0)

    private fun open(startAt: Int, multiWindow: Boolean = false) {
        Shadows.shadowOf(composeRule.activity).setInMultiWindowMode(multiWindow)
        rotation = startAt
        composeRule.setContent {
            HoldNaturalOrientation(windowIsLandscape = false, displayRotation = rotation, fullScreen = currentWindowIsFullScreen()) { _, _ ->
                session.Viewfinder(Modifier.fillMaxSize(), held = LocalNaturalOrientationHold.current)
            }
        }
        composeRule.waitForIdle()
        assertEquals("the viewfinder attached once on opening", 1, session.viewfinderSurfaceAttachments)
    }

    private fun turnTo(next: Int) {
        rotation = next
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "sw700dp-w700dp-h1000dp-port-mdpi")
    fun `on a tablet, full screen, turning 0 to 90 to 0 to 270 to 180 never re-attaches the viewfinder`() {
        open(Surface.ROTATION_0)
        for ((step, next) in listOf(Surface.ROTATION_90, Surface.ROTATION_0, Surface.ROTATION_270, Surface.ROTATION_180).withIndex()) {
            turnTo(next)
            assertEquals(
                "turn ${step + 1}, to rotation index $next, re-attached the viewfinder's surface (a new surface request and a camera restart on a device)",
                1,
                session.viewfinderSurfaceAttachments,
            )
            assertEquals("turn ${step + 1}: the natural-frame TextureView throughout", PreviewView.ImplementationMode.COMPATIBLE, session.viewfinderImplementationMode)
        }
    }

    @Test
    @Config(qualifiers = "sw700dp-w700dp-h1000dp-port-mdpi")
    fun `on a tablet, full screen, at ROTATION_0 the viewfinder already draws in the natural frame`() {
        open(Surface.ROTATION_0)
        assertEquals(
            "at ROTATION_0 the tablet's viewfinder is the TextureView it will need when turned, so the turn swaps nothing",
            PreviewView.ImplementationMode.COMPATIBLE,
            session.viewfinderImplementationMode,
        )
    }

    /** Phones are unchanged (the dispatch's scope): under 600 dp the viewfinder stays the SurfaceView it always was. */
    @Test
    @Config(qualifiers = "sw411dp-w411dp-h891dp-port-mdpi")
    fun `on a phone, at ROTATION_0, the viewfinder stays PERFORMANCE`() {
        open(Surface.ROTATION_0)
        assertEquals("a phone keeps the SurfaceView", PreviewView.ImplementationMode.PERFORMANCE, session.viewfinderImplementationMode)
    }

    /** Split screen is unchanged (RECORD -985): not full screen, so never in the natural frame, at any rotation. */
    @Test
    @Config(qualifiers = "sw700dp-w1000dp-h700dp-land-mdpi")
    fun `on a tablet in split screen, the viewfinder stays PERFORMANCE`() {
        open(Surface.ROTATION_90, multiWindow = true)
        assertEquals("split screen keeps the SurfaceView", PreviewView.ImplementationMode.PERFORMANCE, session.viewfinderImplementationMode)
    }
}
