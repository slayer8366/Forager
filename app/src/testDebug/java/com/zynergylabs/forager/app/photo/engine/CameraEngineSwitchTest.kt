package com.zynergylabs.forager.app.photo.engine

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraEngineSeam
import com.zynergylabs.forager.app.photo.CameraXEngine
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The seam and the switch (dispatch 2026-10-16-02: "one engine factory, defaulting to CameraX"; "CameraX stays the
 * default"; "selected by a debug-only switch"). Read through the factory the camera dialog's slot calls,
 * `CameraEngineSeam.create`, so what is asserted is what the dialog would get.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraEngineSwitchTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val prototype = object : CameraEngine {
        override val session: CameraCaptureSession = FakeCameraCaptureSession()

        @Composable
        override fun Viewfinder(modifier: Modifier, held: Boolean) = Unit
    }

    @After
    fun backToDefault() {
        CameraEngineSwitch.select(CameraEngineChoice.CameraX) { prototype }
        CameraEngineSwitch.forceFallback = false
    }

    @Test
    fun `with nothing chosen the dialog gets today's CameraX engine`() {
        assertEquals(CameraEngineChoice.CameraX, CameraEngineSwitch.choice)
        assertNull(CameraEngineSeam.factory)
        assertTrue(CameraEngineSeam.create(app) is CameraXEngine)
    }

    @Test
    fun `choosing the prototype gives the dialog the prototype, and choosing CameraX again takes it away`() {
        CameraEngineSwitch.select(CameraEngineChoice.Platform) { prototype }
        assertEquals(CameraEngineChoice.Platform, CameraEngineSwitch.choice)
        assertSame(prototype, CameraEngineSeam.create(app))

        CameraEngineSwitch.select(CameraEngineChoice.CameraX) { prototype }
        assertEquals(CameraEngineChoice.CameraX, CameraEngineSwitch.choice)
        assertNull(CameraEngineSeam.factory)
        assertTrue(CameraEngineSeam.create(app) is CameraXEngine)
    }
}
