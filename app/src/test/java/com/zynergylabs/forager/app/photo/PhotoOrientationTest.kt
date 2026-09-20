package com.zynergylabs.forager.app.photo

import android.app.Application
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EXIF-orientation-display dispatch. The fixtures are real JPEGs: a 40×20 baseline JPEG (embedded
 * below; the Android unit-test classpath carries no `ImageIO` to write one), then given an
 * orientation tag by `ExifInterface.saveAttributes()` — the one place in this repository that
 * writes EXIF, and it is a test. Robolectric's legacy `BitmapFactory` shadow
 * reads a real JPEG's dimensions (it fakes only the pixels), so a decode here is 40×20 and a
 * turned copy's dimensions are what `Bitmap.createBitmap` with a matrix produces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoOrientationTest {

    private fun jpegFixture(name: String, orientationTag: Int?): File {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.filesDir, "photos/$name").apply { parentFile?.mkdirs() }
        file.writeBytes(java.util.Base64.getDecoder().decode(JPEG_40X20_BASE64))
        if (orientationTag != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientationTag.toString())
                saveAttributes()
            }
        }
        return file
    }

    @Test
    fun `a rotate-90 tag reads as a 90 degree clockwise turn`() {
        assertEquals(PhotoOrientation(90, mirrored = false), readPhotoOrientation(jpegFixture("r90.jpg", ExifInterface.ORIENTATION_ROTATE_90)))
    }

    @Test
    fun `all eight defined values map, and the mirrored four say so`() {
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to PhotoOrientation(0, false),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to PhotoOrientation(0, true),
            ExifInterface.ORIENTATION_ROTATE_180 to PhotoOrientation(180, false),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to PhotoOrientation(180, true),
            ExifInterface.ORIENTATION_TRANSPOSE to PhotoOrientation(90, true),
            ExifInterface.ORIENTATION_ROTATE_90 to PhotoOrientation(90, false),
            ExifInterface.ORIENTATION_TRANSVERSE to PhotoOrientation(270, true),
            ExifInterface.ORIENTATION_ROTATE_270 to PhotoOrientation(270, false),
        )
        expected.forEach { (tag, orientation) ->
            assertEquals("tag $tag", orientation, readPhotoOrientation(jpegFixture("tag-$tag.jpg", tag)))
        }
    }

    @Test
    fun `no tag, an undefined tag, an unknown value, and a non-JPEG all display unrotated`() {
        assertEquals(PhotoOrientation.NORMAL, readPhotoOrientation(jpegFixture("untagged.jpg", null)))
        assertEquals(PhotoOrientation.NORMAL, readPhotoOrientation(jpegFixture("undefined.jpg", ExifInterface.ORIENTATION_UNDEFINED)))
        assertEquals(PhotoOrientation.NORMAL, readPhotoOrientation(jpegFixture("unknown.jpg", 9)))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notAnImage = File(context.filesDir, "photos/bytes.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(PhotoOrientation.NORMAL, readPhotoOrientation(notAnImage))
        assertEquals(PhotoOrientation.NORMAL, readPhotoOrientation(File(context.filesDir, "photos/missing.jpg")))
    }

    @Test
    fun `turning a 40x20 bitmap by 90 or 270 swaps its dimensions, by 0 or 180 keeps them`() {
        val file = jpegFixture("dims.jpg", null)
        fun decode() = BitmapFactory.decodeFile(file.absolutePath)!!
        assertEquals(listOf(40, 20), decode().let { listOf(it.width, it.height) })

        val r90 = decode().oriented(PhotoOrientation(90, mirrored = false))
        assertEquals("90° must swap", listOf(20, 40), listOf(r90.width, r90.height))
        val r270 = decode().oriented(PhotoOrientation(270, mirrored = false))
        assertEquals("270° must swap", listOf(20, 40), listOf(r270.width, r270.height))
        val transpose = decode().oriented(PhotoOrientation(90, mirrored = true))
        assertEquals("transpose must swap", listOf(20, 40), listOf(transpose.width, transpose.height))
        val r180 = decode().oriented(PhotoOrientation(180, mirrored = false))
        assertEquals("180° keeps", listOf(40, 20), listOf(r180.width, r180.height))
    }

    @Test
    fun `NORMAL returns the same bitmap and recycles nothing, any other orientation recycles the source`() {
        val file = jpegFixture("recycle.jpg", null)
        val same = BitmapFactory.decodeFile(file.absolutePath)!!
        assertTrue(same === same.oriented(PhotoOrientation.NORMAL))
        assertFalse(same.isRecycled)

        val source = BitmapFactory.decodeFile(file.absolutePath)!!
        val turned = source.oriented(PhotoOrientation(90, mirrored = false))
        assertTrue("the source is released once the turned copy exists", source.isRecycled)
        assertFalse(turned.isRecycled)
    }

    @Test
    fun `reading the orientation does not modify the file`() {
        val file = jpegFixture("untouched.jpg", ExifInterface.ORIENTATION_ROTATE_90)
        val before = file.readBytes()
        val modifiedBefore = file.lastModified()
        readPhotoOrientation(file)
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(modifiedBefore, file.lastModified())
    }

    private companion object {
        /** A 40×20 baseline JPEG with no EXIF segment, written once by the JDK's `ImageIO` (which the Android unit-test classpath does not carry) and embedded here. */
        const val JPEG_40X20_BASE64 = "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAUACgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooAKKKKACiiigAooooAKKKKACiiigD//Z"
    }
}
