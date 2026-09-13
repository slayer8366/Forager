package com.zynergylabs.forager.app.ui.log

import android.app.Application
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [viewerSampleSize] is the arithmetic behind [VIEWER_MAX_EDGE_PX] — the one place the viewer bounds
 * how much of a full-resolution photo it decodes. Pure integers, no bitmap, no Robolectric: the
 * decode call itself is a `BitmapFactory` pass-through that Robolectric's legacy shadow fakes (see
 * [DecodedPhotoTest]'s own doc comment), so the honest headless check is of the number handed to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoViewerDecodeTest {

    private fun jpegFixture(name: String, orientationTag: Int?): File {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.filesDir, "photos/$name").apply { parentFile?.mkdirs() }
        file.writeBytes(java.util.Base64.getDecoder().decode(JPEG_40X20_BASE64))
        if (orientationTag != null) {
            ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientationTag.toString()); saveAttributes() }
        }
        return file
    }

    // ── EXIF-orientation-display dispatch ───────────────────────────────────────────────────

    @Test
    fun `a rotate-90 photo decodes untouched with a 90 degree draw rotation, and displays with swapped dimensions`() {
        val photo = decodeBoundedPhoto(jpegFixture("viewer-r90.jpg", ExifInterface.ORIENTATION_ROTATE_90), maxEdgePx = 4096)

        assertEquals(90, photo.rotationDegrees)
        assertEquals("the bitmap itself is not turned", listOf(40, 20), listOf(photo.bitmap.width, photo.bitmap.height))
        assertEquals("but it displays turned", listOf(20, 40), listOf(photo.displayWidth, photo.displayHeight))
    }

    @Test
    fun `a rotate-270 photo displays with swapped dimensions too`() {
        val photo = decodeBoundedPhoto(jpegFixture("viewer-r270.jpg", ExifInterface.ORIENTATION_ROTATE_270), maxEdgePx = 4096)
        assertEquals(270, photo.rotationDegrees)
        assertEquals(listOf(20, 40), listOf(photo.displayWidth, photo.displayHeight))
    }

    @Test
    fun `a mirrored orientation is baked into the bitmap and leaves no draw rotation`() {
        val photo = decodeBoundedPhoto(jpegFixture("viewer-transpose.jpg", ExifInterface.ORIENTATION_TRANSPOSE), maxEdgePx = 4096)
        assertEquals(0, photo.rotationDegrees)
        assertEquals("transpose turns the bitmap itself", listOf(20, 40), listOf(photo.bitmap.width, photo.bitmap.height))
    }

    @Test
    fun `an untagged photo decodes with no rotation and its own dimensions`() {
        val photo = decodeBoundedPhoto(jpegFixture("viewer-plain.jpg", null), maxEdgePx = 4096)
        assertEquals(0, photo.rotationDegrees)
        assertEquals(listOf(40, 20), listOf(photo.displayWidth, photo.displayHeight))
    }

    /** ContentScale.Fit fits the unrotated 40×20 into 100×100 at 2.5; rotated it is 20×40, fitting at 2.5 as well — ratio 1. In a 100×30 viewport: unrotated fit 1.5, rotated fit min(100/20, 30/40) = 0.75 — ratio 0.5. */
    @Test
    fun `the rotation fit correction is the ratio of the rotated fit to the unrotated fit`() {
        assertEquals(1f, viewerRotationFit(40, 20, 0, 100, 30), 0f)
        assertEquals(1f, viewerRotationFit(40, 20, 180, 100, 30), 0f)
        assertEquals(1f, viewerRotationFit(40, 20, 90, 100, 100), 1e-6f)
        assertEquals(0.5f, viewerRotationFit(40, 20, 90, 100, 30), 1e-6f)
        assertEquals(0.5f, viewerRotationFit(40, 20, 270, 100, 30), 1e-6f)
        assertEquals("nothing to fit yet", 1f, viewerRotationFit(40, 20, 90, 0, 0), 0f)
    }

    @Test
    fun `a photo already inside the limit is not sampled`() {
        assertEquals(1, viewerSampleSize(width = 4032, height = 3024, maxEdgePx = 4096))
        assertEquals(1, viewerSampleSize(width = 4096, height = 4096, maxEdgePx = 4096))
        assertEquals(1, viewerSampleSize(width = 100, height = 100, maxEdgePx = 4096))
    }

    @Test
    fun `one pixel over the limit halves`() {
        assertEquals(2, viewerSampleSize(width = 4097, height = 100, maxEdgePx = 4096))
    }

    @Test
    fun `the longest edge is what is bounded, whichever axis it is on`() {
        // A 50 MP sensor's 8160×6120: halving brings 8160 to 4080, inside the limit.
        assertEquals(2, viewerSampleSize(width = 8160, height = 6120, maxEdgePx = 4096))
        assertEquals(2, viewerSampleSize(width = 6120, height = 8160, maxEdgePx = 4096))
    }

    /**
     * First written expecting 16385 to need 8: it does not, because the decoder floors — 16385 / 4
     * is 4096 wide, inside the limit — and the function's integer division matches that floor. The
     * expectation was the wrong half of that first run, so the case is kept with the decoder's
     * arithmetic and a genuinely over-the-edge width added beside it.
     */
    @Test
    fun `sample sizes climb by powers of two until the floored edge fits`() {
        assertEquals(4, viewerSampleSize(width = 16384, height = 100, maxEdgePx = 4096))
        assertEquals(4, viewerSampleSize(width = 16385, height = 100, maxEdgePx = 4096))
        assertEquals(8, viewerSampleSize(width = 16388, height = 100, maxEdgePx = 4096))
    }

    @Test
    fun `a non-positive limit is rejected rather than looping`() {
        assertThrows(IllegalArgumentException::class.java) { viewerSampleSize(width = 10, height = 10, maxEdgePx = 0) }
    }

    private companion object {
        /** A 40×20 baseline JPEG with no EXIF segment, written once by the JDK's `ImageIO` (which the Android unit-test classpath does not carry) and embedded here. */
        const val JPEG_40X20_BASE64 = "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAUACgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooAKKKKACiiigAooooAKKKKACiiigD//Z"
    }
}
