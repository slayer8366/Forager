package com.zynergylabs.forager.app.photo

import android.app.Application
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.PhotoSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FilePhotoStore] against real app-private storage under Robolectric's filesystem — not a fake —
 * covering both cases CLAUDE.md's error-handling rule asks for: a failed persist reports failure
 * rather than a path to a file that isn't there, and a failed/missing delete is a no-op, mirroring
 * [com.zynergylabs.forager.app.data.repository.RoomPlannedTripRepository]'s own delete-is-a-no-op convention.
 *
 * ## Photo-geodata coverage, and what this class cannot verify (@Config sdk = 36, well above the
 * `Build.VERSION_CODES.Q` guard [FilePhotoStore.readExifData] checks)
 *
 * [GalleryImportPhotoSource]'s EXIF read is exercised directly: [ExifInterface] is a pure
 * Kotlin/Java library operating on ordinary [File] bytes, so writing real EXIF tags onto a real
 * minimal JPEG ([minimalJpegWithExif]) and persisting it through a `file://` [Uri] exercises the
 * genuine read path, not a stand-in for it. [CameraCapturePhotoSource] never reading EXIF at all,
 * even when the source file happens to carry a GPS tag, is verified the same way.
 *
 * ## The GPS-free claim: why it was unwritten, and why it is written now (2026-09-14)
 *
 * This section used to record that a persisted capture carrying no GPS EXIF could not be asserted
 * here. The reasoning held for what it described: that claim rested on the platform-level redaction
 * an ordinary (non-`setRequireOriginal`) `ContentResolver.openInputStream` performs on a real
 * `content://` `MediaStore` `Uri` on API 29+, which lives inside the system `MediaProvider` that
 * Robolectric does not run. Every `Uri` here is a `file://` one, which `openInputStream` opens
 * directly with no redaction on any API level, so the assertion would have proved Robolectric's
 * `file://` handling and nothing else.
 *
 * What changed is not the harness. It is that the redaction was never reaching a capture in the
 * first place: [CameraCaptureFiles] hands back a `FileProvider` `Uri` over the app's own
 * `filesDir/captures` file, and redaction is a `MediaStore` behaviour, so a capture's stored copy
 * was a verbatim byte copy of whatever the camera app wrote. [scrubPhotoMetadata] now removes it in
 * this app's own code, on ordinary bytes, which is why the assertion below is a real one: it tests
 * the thing itself rather than a platform behaviour standing in for it. The remaining device-only
 * question is the one the scrub does not decide — whether a given camera app writes GPS at all —
 * and it no longer matters to the outcome, because the metadata goes either way.
 *
 * The import half is unchanged and still rests on the platform: an import is deliberately *not*
 * scrubbed (owner's ruling, 2026-09-14 — "Photos imported from outside the app are to remain
 * untouched"), so what a real `MediaStore` import carries is still a real-device question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FilePhotoStoreTest {

    private lateinit var context: Application
    private lateinit var store: FilePhotoStore
    private lateinit var sourceDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = FilePhotoStore(context)
        // A source location distinct from filesDir, standing in for wherever a camera capture or
        // gallery pick's content:// URI would actually resolve to.
        sourceDir = File(context.cacheDir, "photo-store-test-sources").apply { mkdirs() }
    }

    @Test
    fun `persist copies the source's bytes into app-private storage and returns a relative path`() = runTest {
        val sourceFile = File(sourceDir, "capture.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        val result = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceFile)))

        val photo = result.getOrThrow()
        val persistedFile = File(context.filesDir, photo.relativePath)
        assertTrue("persisted file should exist under filesDir", persistedFile.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), persistedFile.readBytes())
        // Never cacheDir — see FilePhotoStore's own doc comment on why (user data must survive).
        assertTrue(persistedFile.absolutePath.startsWith(context.filesDir.absolutePath))
    }

    @Test
    fun `two persists of different sources produce two distinct files, not a shared one`() = runTest {
        val sourceA = File(sourceDir, "a.jpg").apply { writeBytes(byteArrayOf(1)) }
        val sourceB = File(sourceDir, "b.jpg").apply { writeBytes(byteArrayOf(2)) }

        val photoA = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceA))).getOrThrow()
        val photoB = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceB))).getOrThrow()

        assertFalse(photoA.relativePath == photoB.relativePath)
        assertArrayEquals(byteArrayOf(1), File(context.filesDir, photoA.relativePath).readBytes())
        assertArrayEquals(byteArrayOf(2), File(context.filesDir, photoB.relativePath).readBytes())
    }

    @Test
    fun `persist from a source that cannot be opened reports failure, not a path to a missing file`() = runTest {
        val missingSource = File(sourceDir, "never-existed.jpg")

        val result = store.persist(CameraCapturePhotoSource(Uri.fromFile(missingSource)))

        assertTrue(result.isFailure)
    }

    @Test
    fun `persist rejects a PhotoSource this store doesn't understand`() = runTest {
        val result = store.persist(object : PhotoSource {})

        assertTrue(result.isFailure)
    }

    @Test
    fun `delete removes the persisted file`() = runTest {
        val sourceFile = File(sourceDir, "capture.jpg").apply { writeBytes(byteArrayOf(9)) }
        val photo = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceFile))).getOrThrow()
        val persistedFile = File(context.filesDir, photo.relativePath)
        assertTrue(persistedFile.exists())

        val result = store.delete(photo)

        assertTrue(result.isSuccess)
        assertFalse(persistedFile.exists())
    }

    @Test
    fun `deleting a photo whose file is already gone is a no-op, not a failure`() = runTest {
        val result = store.delete(LogPhoto(id = "never-persisted", relativePath = "photos/does-not-exist.jpg", createdAtEpochMillis = 0L))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `persist stamps the injected clock's time, not left null, when no EXIF timestamp is available`() = runTest {
        val clockedStore = FilePhotoStore(context, now = { 1_700_000_000_000L })
        val sourceFile = File(sourceDir, "capture.jpg").apply { writeBytes(byteArrayOf(7)) }

        val photo = clockedStore.persist(CameraCapturePhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertEquals(1_700_000_000_000L, photo.createdAtEpochMillis)
    }

    // --- Photo-geodata dispatch --------------------------------------------------------------

    @Test
    fun `a gallery import with EXIF GPS tags reads latitude and longitude`() = runTest {
        val sourceFile = minimalJpegWithExif(sourceDir, "with-location.jpg") { exif ->
            exif.setLatLong(45.5, -122.6)
        }

        val photo = store.persist(GalleryImportPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertEquals(45.5, photo.latitude!!, 0.0001)
        assertEquals(-122.6, photo.longitude!!, 0.0001)
    }

    @Test
    fun `a gallery import with no EXIF GPS tags stores a null location, not a fabricated one`() = runTest {
        val sourceFile = minimalJpegWithExif(sourceDir, "no-location.jpg") { /* no attributes set */ }

        val photo = store.persist(GalleryImportPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertNull(photo.latitude)
        assertNull(photo.longitude)
    }

    @Test
    fun `a gallery import with an EXIF capture timestamp uses it instead of the injected clock`() = runTest {
        val clockedStore = FilePhotoStore(context, now = { 1_700_000_000_000L })
        val sourceFile = minimalJpegWithExif(sourceDir, "with-timestamp.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:03:15 10:30:00")
        }
        val expectedEpochMillis = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse("2024:03:15 10:30:00")!!.time

        val photo = clockedStore.persist(GalleryImportPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertEquals(expectedEpochMillis, photo.createdAtEpochMillis)
    }

    @Test
    fun `a gallery import with no EXIF capture timestamp falls back to the injected clock`() = runTest {
        val clockedStore = FilePhotoStore(context, now = { 1_700_000_000_000L })
        val sourceFile = minimalJpegWithExif(sourceDir, "no-timestamp.jpg") { /* no attributes set */ }

        val photo = clockedStore.persist(GalleryImportPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertEquals(1_700_000_000_000L, photo.createdAtEpochMillis)
    }

    /**
     * The design guarantee the photo-geodata amendment's decision 3 exists to enforce: a camera
     * capture's location comes only from a live GPS fix (see [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel]),
     * never from whatever EXIF the captured file happens to carry — even when that file *does* carry
     * a GPS tag, as it might if the camera app itself location-tags its own output.
     */
    @Test
    fun `a camera capture never reads EXIF location, even when the source file carries one`() = runTest {
        val sourceFile = minimalJpegWithExif(sourceDir, "camera-with-embedded-location.jpg") { exif ->
            exif.setLatLong(45.5, -122.6)
        }

        val photo = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertNull("a camera capture's own EXIF is never consulted for location", photo.latitude)
        assertNull(photo.longitude)
    }

    // ── What the stored copy carries, now that the scrub is this app's own code ──────────────

    /**
     * The claim this class could not make before [scrubPhotoMetadata] existed — see the class doc.
     * Asserted on the persisted file's own bytes, through the real `persist` entry point, with a
     * source file that genuinely carries a coordinate.
     */
    @Test
    fun `a persisted capture carries no GPS EXIF`() = runTest {
        val sourceFile = minimalJpegWithExif(sourceDir, "capture-to-scrub.jpg") { exif ->
            exif.setLatLong(45.5, -122.6)
        }
        assertEquals("precondition: the source really is location-tagged", "45.5, -122.6", ExifInterface(sourceFile.absolutePath).latLong?.joinToString())

        val photo = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        val persisted = File(context.filesDir, photo.relativePath)
        val exif = ExifInterface(persisted.absolutePath)
        assertNull("the stored copy must not carry the coordinate the camera app wrote", exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    /**
     * The scrub's one exception, and the reason the whole thing is strip-*then-reapply* rather than
     * strip: a photo that came out of the store rotated wrongly would be a visible regression, so
     * the tag the display path reads has to survive persisting.
     */
    @Test
    fun `a persisted capture keeps the orientation the display path reads`() = runTest {
        val sourceFile = minimalJpegWithExif(sourceDir, "capture-rotated.jpg") { exif ->
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            exif.setLatLong(45.5, -122.6)
        }

        val photo = store.persist(CameraCapturePhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        val persisted = File(context.filesDir, photo.relativePath)
        assertEquals(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(persisted.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1),
        )
        assertNull("and the coordinate still goes", ExifInterface(persisted.absolutePath).latLong)
    }

    /**
     * Owner's ruling, 2026-09-14: "Photos imported from outside the app are to remain untouched."
     * The scrub is a capture-time step, not a store-wide one, and this is the assertion that keeps
     * it that way — an import's own metadata is the photographer's, and removing it would be
     * destroying data the app was only asked to hold.
     */
    @Test
    fun `a persisted import is left exactly as it was, metadata and all`() = runTest {
        val sourceFile = minimalJpegWithExif(sourceDir, "import-to-keep.jpg") { exif ->
            exif.setLatLong(45.5, -122.6)
            exif.setAttribute(ExifInterface.TAG_MAKE, "ACME")
        }
        val sourceBytes = sourceFile.readBytes()

        val photo = store.persist(GalleryImportPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        val persisted = File(context.filesDir, photo.relativePath)
        assertArrayEquals("an import is a byte copy, not a rewrite", sourceBytes, persisted.readBytes())
        val exif = ExifInterface(persisted.absolutePath)
        assertEquals("45.5, -122.6", exif.latLong?.joinToString())
        assertEquals("ACME", exif.getAttribute(ExifInterface.TAG_MAKE))
    }

    private fun minimalJpegWithExif(directory: File, name: String, configure: (ExifInterface) -> Unit): File {
        val file = File(directory, name).apply { writeBytes(Base64.getDecoder().decode(MINIMAL_JPEG_BASE64)) }
        val exif = ExifInterface(file.absolutePath)
        configure(exif)
        exif.saveAttributes()
        return file
    }

    private companion object {
        // A real, minimal 1x1 black JPEG — ExifInterface needs genuine JPEG markers (SOI/EOI) to
        // parse and to insert a fresh EXIF (APP1) segment into; an arbitrary byte array (as the
        // non-EXIF tests above use) is not a valid target for it.
        const val MINIMAL_JPEG_BASE64 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k="
    }
}
