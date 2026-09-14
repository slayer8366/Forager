package com.zynergylabs.forager.app.photo

import android.app.Application
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [scrubPhotoMetadata] — owner's design, 2026-09-14. Real JPEG bytes throughout, and every
 * assertion is about the file on disk rather than about a value the code returned.
 *
 * **This is testable here in a way the platform redaction never was.** `FilePhotoStoreTest`'s own
 * doc comment records why it could not assert a persisted capture carries no GPS: the claim rested
 * on `MediaProvider`'s redaction, which Robolectric does not run, so the assertion would only have
 * proved Robolectric's `file://` handling. The scrub is this app's own code operating on ordinary
 * bytes, so asserting on the result proves the thing itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoMetadataScrubTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun jpeg(name: String, configure: (ExifInterface) -> Unit = {}): File {
        val file = File(context().filesDir, "scrub/$name").apply { parentFile?.mkdirs() }
        file.writeBytes(Base64.getDecoder().decode(JPEG_40X20_BASE64))
        ExifInterface(file.absolutePath).apply { configure(this); saveAttributes() }
        return file
    }

    /** The entropy-coded image data: SOS marker to end of file. What must survive byte for byte. */
    private fun scanData(bytes: ByteArray): ByteArray {
        var i = 2
        while (i < bytes.size - 1) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xDA.toByte()) return bytes.copyOfRange(i, bytes.size)
            i++
        }
        return ByteArray(0)
    }

    /** Inserts a raw segment straight after SOI, so a real APPn/COM can be put in a fixture without a library that would normalise it. */
    private fun File.spliceSegment(marker: Int, payload: ByteArray) {
        val bytes = readBytes()
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()) + payload
        writeBytes(bytes.copyOfRange(0, 2) + segment + bytes.copyOfRange(2, bytes.size))
    }

    private fun markers(bytes: ByteArray): List<Int> = buildList {
        var i = 2
        while (i < bytes.size - 1 && bytes[i] == 0xFF.toByte()) {
            val marker = bytes[i + 1].toInt() and 0xFF
            add(marker)
            if (marker == 0xDA) return@buildList
            if (i + 3 >= bytes.size) return@buildList
            i += 2 + (((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF))
        }
    }

    // ── The headline claim ───────────────────────────────────────────────────────────────────

    @Test
    fun `GPS is gone, orientation survives, and the image itself is untouched`() {
        val file = jpeg("gps.jpg") { exif ->
            exif.setLatLong(45.5, -122.6)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        }
        val scanBefore = scanData(file.readBytes())
        assertEquals("precondition: the fixture really does carry a coordinate", "45.5, -122.6", ExifInterface(file.absolutePath).latLong?.joinToString())

        val outcome = scrubPhotoMetadata(file)

        assertEquals(ScrubOutcome.Scrubbed(orientationReapplied = true), outcome)
        val exif = ExifInterface(file.absolutePath)
        assertNull("the coordinate must be gone", exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals("orientation is put back", ExifInterface.ORIENTATION_ROTATE_90, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        assertArrayEquals("the compressed image data must be identical: this is lossless", scanBefore, scanData(file.readBytes()))
    }

    /**
     * The allowlist's whole point: it removes what nobody enumerated. These four are *not* named
     * anywhere in the production code, so a denylist built from a list of "identifying tags" could
     * only have caught them by having thought of each one first.
     */
    @Test
    fun `every other EXIF tag goes too, not only the ones a denylist would have named`() {
        val file = jpeg("tags.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_MAKE, "ACME")
            exif.setAttribute(ExifInterface.TAG_MODEL, "Phone X")
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:14 10:30:00")
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "a private note")
        }

        scrubPhotoMetadata(file)

        val exif = ExifInterface(file.absolutePath)
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
    }

    /**
     * "None is invented" is asserted on the **segment list**, not on a read of the tag, because
     * `ExifInterface` will answer for `TAG_ORIENTATION` on a file that has no EXIF whatsoever:
     * probed here 2026-09-14, the bare fixture reports `0` (`ORIENTATION_UNDEFINED`) with
     * `hasAttribute` true and no APP1 in the file at all. So a tag read cannot tell "absent" from
     * "present and undefined" — which is exactly why [scrubPhotoMetadata] reads it with
     * `ORIENTATION_UNDEFINED` as its own default and treats both as nothing to put back.
     */
    @Test
    fun `a photo with no orientation tag is still scrubbed, and none is invented`() {
        val file = jpeg("plain.jpg") { exif -> exif.setLatLong(1.0, 2.0) }

        val outcome = scrubPhotoMetadata(file)

        assertEquals(ScrubOutcome.Scrubbed(orientationReapplied = false), outcome)
        assertFalse("no EXIF segment should have been written back", markers(file.readBytes()).contains(0xE1))
        val exif = ExifInterface(file.absolutePath)
        assertNull(exif.latLong)
        assertEquals(
            "whatever is reported must be 'undefined', never a rotation this code made up",
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1),
        )
    }

    // ── The allowlist, segment by segment ────────────────────────────────────────────────────

    @Test
    fun `a COM comment is dropped`() {
        val file = jpeg("comment.jpg")
        file.spliceSegment(marker = 0xFE, payload = "photographer: someone".toByteArray())
        assertTrue("precondition", markers(file.readBytes()).contains(0xFE))

        scrubPhotoMetadata(file)

        assertFalse("a comment segment carries free text and must not survive", markers(file.readBytes()).contains(0xFE))
    }

    /**
     * Kept on purpose, and asserted because it survives *two* steps: the segment rewrite, and
     * `ExifInterface.saveAttributes()` putting orientation back. If that second step discarded
     * other APP segments, a Display P3 photo would come out of this colour-shifted, which for an
     * app built to judge cap and gill colour is a real defect rather than a cosmetic one.
     */
    @Test
    fun `an ICC colour profile survives both the strip and the orientation reapply`() {
        val file = jpeg("icc.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        }
        file.spliceSegment(marker = 0xE2, payload = "ICC_PROFILE".toByteArray() + byteArrayOf(0) + ByteArray(16) { 7 })

        scrubPhotoMetadata(file)

        val after = file.readBytes()
        assertTrue("the colour profile must still be there", markers(after).contains(0xE2))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
    }

    /** JFIF is structural and stays; JFXX is the APP0 *extension*, which can carry a thumbnail, so it goes. */
    @Test
    fun `the JFIF header stays but a JFXX thumbnail extension is dropped`() {
        val file = jpeg("jfxx.jpg")
        file.spliceSegment(marker = 0xE0, payload = "JFXX".toByteArray() + byteArrayOf(0, 0x10) + ByteArray(8) { 3 })
        assertEquals("precondition: two APP0 segments now", 2, markers(file.readBytes()).count { it == 0xE0 })

        scrubPhotoMetadata(file)

        assertEquals("exactly the JFIF one survives", 1, markers(file.readBytes()).count { it == 0xE0 })
    }

    @Test
    fun `no APP1 is left behind when there was no orientation to put back`() {
        val file = jpeg("app1.jpg") { exif -> exif.setAttribute(ExifInterface.TAG_MAKE, "ACME") }
        assertTrue("precondition: the fixture has an EXIF segment", markers(file.readBytes()).contains(0xE1))

        scrubPhotoMetadata(file)

        assertFalse("nothing was reapplied, so no metadata segment should exist at all", markers(file.readBytes()).contains(0xE1))
    }

    // ── Never destructive ────────────────────────────────────────────────────────────────────

    @Test
    fun `a non-JPEG is reported as such and left byte for byte alone`() {
        val file = File(context().filesDir, "scrub/notajpeg.bin").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        val outcome = scrubPhotoMetadata(file)

        assertEquals(ScrubOutcome.NotAJpeg, outcome)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), file.readBytes())
    }

    @Test
    fun `a truncated JPEG is refused rather than rewritten into something shorter`() {
        val whole = Base64.getDecoder().decode(JPEG_40X20_BASE64)
        // Keeps the SOI so it still looks like a JPEG, but cuts mid-segment so the walk cannot finish.
        val file = File(context().filesDir, "scrub/truncated.jpg").apply { parentFile?.mkdirs(); writeBytes(whole.copyOfRange(0, 12)) }
        val before = file.readBytes()

        val outcome = scrubPhotoMetadata(file)

        assertEquals(ScrubOutcome.Failed, outcome)
        assertArrayEquals("a file we could not parse must come out exactly as it went in", before, file.readBytes())
    }

    @Test
    fun `no temp file is left beside a scrubbed photo`() {
        val file = jpeg("tidy.jpg") { exif -> exif.setLatLong(1.0, 2.0) }

        scrubPhotoMetadata(file)

        val strays = file.parentFile!!.listFiles()!!.filter { it.name.endsWith(".scrub") }
        assertTrue("left behind: $strays", strays.isEmpty())
    }

    private companion object {
        /** The same 40×20 baseline JPEG [PhotoOrientationTest] uses; it carries a JFIF APP0, which the allowlist keeps. */
        const val JPEG_40X20_BASE64 =
            "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAAUACgBAREA/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/9oACAEBAAA/APn+iiigD//Z"
    }
}
