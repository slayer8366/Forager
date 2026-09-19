package com.zynergylabs.forager.app.photo

import android.app.Application
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [reapplyIntendedOrientation] on real JPEG files, asserted by reading the tag back with
 * `ExifInterface` and, where the file must not change, by comparing its bytes. The fixture is the
 * scrub test's 40×20 JPEG ([PhotoMetadataScrubTest.JPEG_40X20_BASE64]); its pixel size is read
 * from its own frame header first, so the size every other case relies on is checked, not taken
 * from the constant's name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntendedOrientationTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun newFile(): File = File(app.cacheDir, "intended/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }

    /** The fixture on disk; [tag] written through ExifInterface's own writer when given, untagged otherwise. */
    private fun jpeg(tag: Int? = null): File {
        val file = newFile()
        file.writeBytes(Base64.getDecoder().decode(PhotoMetadataScrubTest.JPEG_40X20_BASE64))
        if (tag != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, tag.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun tagOf(file: File): Int = ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)

    @Test
    fun `the fixture's frame header says 40 by 20, with and without an EXIF segment in front of it`() {
        assertEquals(FRAME, readJpegPixelSize(jpeg()))
        assertEquals(FRAME, readJpegPixelSize(jpeg(tag = ExifInterface.ORIENTATION_NORMAL)))
    }

    @Test
    fun `not a JPEG, or no file, reads as no size without throwing`() {
        val notJpeg = newFile().apply { writeBytes(ByteArray(64) { 0x2A }) }
        assertNull(readJpegPixelSize(notJpeg))
        assertNull(readJpegPixelSize(File(app.cacheDir, "intended/absent.jpg")))
    }

    @Test
    fun `the HAL's normal tag becomes the intended quarter turn`() {
        val file = jpeg(tag = ExifInterface.ORIENTATION_NORMAL)

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 90, captureResolution = FRAME)

        assertEquals(
            OrientationReapplyOutcome.Rewritten(fromTag = ExifInterface.ORIENTATION_NORMAL, toTag = ExifInterface.ORIENTATION_ROTATE_90),
            outcome,
        )
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, tagOf(file))
        assertEquals("the display path reads the new tag as the intended turn", PhotoOrientation(90, mirrored = false), readPhotoOrientation(file))
        assertEquals("the pixels are as they were", FRAME, readJpegPixelSize(file))
    }

    @Test
    fun `no tag at all and an intended quarter turn, rewritten from undefined`() {
        val file = jpeg()
        assertEquals("precondition: the fixture carries no orientation tag (ExifInterface answers undefined, not the default, for a file with no EXIF)", ExifInterface.ORIENTATION_UNDEFINED, tagOf(file))

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 90, captureResolution = FRAME)

        assertEquals(
            OrientationReapplyOutcome.Rewritten(fromTag = ExifInterface.ORIENTATION_UNDEFINED, toTag = ExifInterface.ORIENTATION_ROTATE_90),
            outcome,
        )
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, tagOf(file))
    }

    @Test
    fun `already the intended tag, kept, and the bytes are untouched`() {
        val file = jpeg(tag = ExifInterface.ORIENTATION_ROTATE_90)
        val before = file.readBytes()

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 90, captureResolution = FRAME)

        assertEquals(OrientationReapplyOutcome.Kept(ExifInterface.ORIENTATION_ROTATE_90), outcome)
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `intending no turn on an untagged file, kept, and no tag is invented`() {
        val file = jpeg()
        val before = file.readBytes()

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 0, captureResolution = FRAME)

        assertEquals(OrientationReapplyOutcome.Kept(ExifInterface.ORIENTATION_UNDEFINED), outcome)
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `pixels transposed against the capture, the HAL's own rotation, declined, tag untouched`() {
        val file = jpeg(tag = ExifInterface.ORIENTATION_NORMAL)
        val before = file.readBytes()

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 90, captureResolution = Size(FRAME.height, FRAME.width))

        assertTrue("$outcome", outcome is OrientationReapplyOutcome.Declined)
        assertEquals(ExifInterface.ORIENTATION_NORMAL, (outcome as OrientationReapplyOutcome.Declined).tag)
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `pixels matching neither the capture nor its transpose, declined`() {
        val file = jpeg(tag = ExifInterface.ORIENTATION_NORMAL)

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 90, captureResolution = Size(4000, 3000))

        assertTrue("$outcome", outcome is OrientationReapplyOutcome.Declined)
        assertEquals(ExifInterface.ORIENTATION_NORMAL, tagOf(file))
    }

    @Test
    fun `a rotation that is not a right angle is declined, not rounded`() {
        val file = jpeg(tag = ExifInterface.ORIENTATION_NORMAL)

        val outcome = reapplyIntendedOrientation(file, intendedRotationDegrees = 45, captureResolution = FRAME)

        assertTrue("$outcome", outcome is OrientationReapplyOutcome.Declined)
        assertEquals(ExifInterface.ORIENTATION_NORMAL, tagOf(file))
        assertNull(exifOrientationTagFor(45))
    }

    @Test
    fun `a file that is not a JPEG is declined, not thrown`() {
        val file = newFile().apply { writeBytes(ByteArray(10)) }
        assertTrue(reapplyIntendedOrientation(file, intendedRotationDegrees = 90, captureResolution = FRAME) is OrientationReapplyOutcome.Declined)
    }

    @Test
    fun `the four right angles round-trip through the display path's decomposition`() {
        for (degrees in listOf(0, 90, 180, 270)) {
            val tag = exifOrientationTagFor(degrees) ?: error("no tag for $degrees")
            assertEquals(PhotoOrientation(degrees, mirrored = false), PhotoOrientation.fromExifTag(tag))
        }
    }

    private companion object {
        val FRAME = Size(40, 20)
    }
}
