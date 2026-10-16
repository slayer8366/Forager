package com.zynergylabs.forager.app.photo.engine

import android.annotation.SuppressLint
import android.view.Surface
import androidx.camera.core.impl.utils.CameraOrientationUtil
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.photo.FlashMode
import com.zynergylabs.forager.app.photo.exifOrientationTagFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The engine's rules against today's camera (dispatch 2026-10-16-02: "Orientation-tag mapping, reusing today's tests where
 * they apply"). The orientation rule is checked against CameraX's own function, called here as the oracle, for every
 * rotation, both sensor mountings and both facings: the prototype's tags are right exactly when they are today's. The tag
 * for each angle is main's `exifOrientationTagFor`, whose own tests (`IntendedOrientationTest`) already hold it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngineRulesTest {

    private val rotations = listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)

    @SuppressLint("RestrictedApi") // the library's own rule, read as the oracle; nothing in the app calls it
    @Test
    fun `every shot asks for the rotation CameraX asks for, for both sensor mountings and both facings`() {
        for (sensor in listOf(90, 270)) {
            for (facing in CameraFacing.entries) {
                for (rotation in rotations) {
                    val cameraX = CameraOrientationUtil.getRelativeImageRotation(
                        CameraOrientationUtil.surfaceRotationToDegrees(rotation),
                        sensor,
                        facing == CameraFacing.Back,
                    )
                    assertEquals("sensor $sensor, $facing, rotation $rotation", cameraX, jpegOrientationDegrees(sensor, rotation, facing))
                    assertNotNull("a right angle always has a tag", exifOrientationTagFor(jpegOrientationDegrees(sensor, rotation, facing)))
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Test
    fun `surface rotations are CameraX's degrees`() {
        for (rotation in rotations) assertEquals(CameraOrientationUtil.surfaceRotationToDegrees(rotation), surfaceRotationDegrees(rotation))
    }

    @Test
    fun `the JPEG size is today's standard size, never the largest, closest lower then higher, four by three first`() {
        val phone = listOf(PixelSize(8160, 6120), PixelSize(4000, 3000), PixelSize(4000, 2252), PixelSize(3264, 2448), PixelSize(1920, 1080))
        assertEquals(PixelSize(4000, 3000), chooseJpegSize(phone))
        // Nothing at or under the bound in 4:3: the closest above.
        assertEquals(PixelSize(4080, 3060), chooseJpegSize(listOf(PixelSize(8160, 6120), PixelSize(4080, 3060))))
        // No 4:3 size at all: the same rule over every size.
        assertEquals(PixelSize(3840, 2160), chooseJpegSize(listOf(PixelSize(7680, 4320), PixelSize(3840, 2160))))
        assertNull(chooseJpegSize(emptyList()))
    }

    @Test
    fun `the viewfinder size is at most 1920 by 1080 in area, in the photo's aspect where there is one`() {
        val sizes = listOf(PixelSize(1920, 1080), PixelSize(1440, 1080), PixelSize(1280, 960), PixelSize(4000, 3000))
        assertEquals(PixelSize(1440, 1080), choosePreviewSize(sizes, PixelSize(4000, 3000)))
        assertEquals(PixelSize(1920, 1080), choosePreviewSize(listOf(PixelSize(1920, 1080), PixelSize(1280, 720)), PixelSize(4000, 3000)))
        assertNull(choosePreviewSize(listOf(PixelSize(4000, 3000)), PixelSize(4000, 3000)))
    }

    @Test
    fun `the viewfinder fills its view and crops, as today's framing does, for a sideways-mounted sensor`() {
        // A 1440x1080 buffer from a 90-degree sensor is 1080 wide and 1440 tall in the natural portrait frame (3:4).
        // A 1080x2340 portrait view is taller than 3:4: the height fills it, the width overflows and is cropped.
        val tall = fillCenterScale(viewWidth = 1080, viewHeight = 2340, bufferWidth = 1440, bufferHeight = 1080, sensorOrientation = 90)
        assertEquals(1f, tall.scaleY, 1e-4f)
        assertEquals(2340f * 3f / 4f / 1080f, tall.scaleX, 1e-4f)
        // A square view: the width fills it, the height overflows.
        val square = fillCenterScale(viewWidth = 1000, viewHeight = 1000, bufferWidth = 1440, bufferHeight = 1080, sensorOrientation = 90)
        assertEquals(1f, square.scaleX, 1e-4f)
        assertEquals(1000f * 4f / 3f / 1000f, square.scaleY, 1e-4f)
        // A sensor mounted along the natural frame keeps the buffer's own aspect (4:3 wide).
        val upright = fillCenterScale(viewWidth = 1000, viewHeight = 1000, bufferWidth = 1440, bufferHeight = 1080, sensorOrientation = 0)
        assertEquals(1000f * 4f / 3f / 1000f, upright.scaleX, 1e-4f)
        assertEquals(1f, upright.scaleY, 1e-4f)
        assertTrue("never shrinks below the view", tall.scaleX >= 1f && square.scaleY >= 1f)
    }

    @Test
    fun `each flash mode is decision B's table in platform terms`() {
        assertEquals(FlashRequest(AeMode.On, torch = false), flashRequest(FlashMode.Off))
        assertEquals(FlashRequest(AeMode.OnAutoFlash, torch = false), flashRequest(FlashMode.Auto))
        assertEquals(FlashRequest(AeMode.OnAlwaysFlash, torch = false), flashRequest(FlashMode.On))
        assertEquals("the torch is lit and the capture fires no flash", FlashRequest(AeMode.On, torch = true), flashRequest(FlashMode.Torch))
    }

    @Test
    fun `the precapture sequence runs for flash on always, for auto only when the preview says the flash is needed, never otherwise`() {
        assertTrue(needsPrecapture(FlashMode.On, PlatformStates.AE_CONVERGED))
        assertTrue(needsPrecapture(FlashMode.On, null))
        assertTrue(needsPrecapture(FlashMode.Auto, PlatformStates.AE_FLASH_REQUIRED))
        assertFalse(needsPrecapture(FlashMode.Auto, PlatformStates.AE_CONVERGED))
        assertFalse("no reading yet: CameraX does not fire the flash on a guess", needsPrecapture(FlashMode.Auto, null))
        assertFalse(needsPrecapture(FlashMode.Off, PlatformStates.AE_FLASH_REQUIRED))
        assertFalse(needsPrecapture(FlashMode.Torch, PlatformStates.AE_FLASH_REQUIRED))
    }
}
