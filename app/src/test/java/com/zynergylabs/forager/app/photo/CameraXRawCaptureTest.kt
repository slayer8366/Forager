package com.zynergylabs.forager.app.photo

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The copies out of the camera's images for a RAW shot (`photo/CameraXRawCapture.kt`), on hand-built images: what a
 * phone's buffer holds is device-only, but the row stride, the byte order and the refusal of anything that is not a
 * one-plane 16-bit RAW frame are rules, and these hold them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraXRawCaptureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a padded RAW frame is copied row by row, padding left out, in the platform's byte order`() {
        val width = 3
        val height = 2
        val rowStride = 10 // 6 bytes of samples and 4 of padding per row
        val buffer = ByteBuffer.allocateDirect(rowStride * height).order(ByteOrder.nativeOrder())
        val expected = shortArrayOf(100, 200, 1023, 4, 5, 6)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            for (col in 0 until width) buffer.putShort(expected[row * width + col])
            buffer.putShort(0x7777).putShort(0x7777) // padding that must not be copied
        }
        buffer.rewind()

        val plane = copyRawPlane(FakeImage(ImageFormat.RAW_SENSOR, width, height, buffer, rowStride, step = 2, time = 42L))

        assertArrayEquals(expected, plane.samples)
        assertEquals(42L, plane.timestampNanos)
        assertEquals(3, plane.width)
        assertEquals(2, plane.height)
    }

    @Test
    fun `anything that is not a 16-bit RAW frame is refused, not guessed at`() {
        val buffer = ByteBuffer.allocateDirect(16)
        val notRaw = runCatching { copyRawPlane(FakeImage(ImageFormat.JPEG, 2, 2, buffer, 4, 2, 0)) }
        assertTrue(notRaw.exceptionOrNull()?.message.orEmpty().contains("not a RAW_SENSOR"))
        val packed = runCatching { copyRawPlane(FakeImage(ImageFormat.RAW_SENSOR, 2, 2, buffer, 4, 1, 0)) }
        assertTrue(packed.exceptionOrNull()?.message.orEmpty().contains("pixel stride"))
    }

    @Test
    fun `a JPEG image's bytes are written as they are`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); position(3) } // position must not matter
        val target = File(tmp.root, "sub/shot.jpg")

        writeJpegImage(FakeImage(ImageFormat.JPEG, 1, 1, buffer, 0, 0, 0), target)

        assertArrayEquals(bytes, target.readBytes())
    }
}

private class FakeImage(
    private val imageFormat: Int,
    private val w: Int,
    private val h: Int,
    private val data: ByteBuffer,
    private val stride: Int,
    private val step: Int,
    private val time: Long,
) : ImageProxy {
    override fun close() = Unit
    override fun getCropRect(): Rect = Rect(0, 0, w, h)
    override fun setCropRect(rect: Rect?) = Unit
    override fun getFormat(): Int = imageFormat
    override fun getHeight(): Int = h
    override fun getWidth(): Int = w
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(
        object : ImageProxy.PlaneProxy {
            override fun getRowStride(): Int = stride
            override fun getPixelStride(): Int = step
            override fun getBuffer(): ByteBuffer = data
        },
    )
    override fun getImageInfo(): ImageInfo = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp(): Long = time
        override fun getRotationDegrees(): Int = 0
        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }
    override fun getImage(): Image? = null
}
