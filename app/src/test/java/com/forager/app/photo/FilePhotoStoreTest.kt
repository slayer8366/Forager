package com.forager.app.photo

import android.app.Application
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource
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
 * [com.forager.app.data.repository.RoomPlannedTripRepository]'s own delete-is-a-no-op convention.
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
 * **Not verified here, and not verifiable in this harness:** the platform-level GPS-EXIF redaction
 * [FilePhotoStore]'s own doc comment describes — an ordinary (non-`setRequireOriginal`)
 * `ContentResolver.openInputStream` on a real `content://` `MediaStore` `Uri` returning bytes with
 * GPS EXIF stripped, on API 29+. That redaction is implemented inside the real system
 * `MediaProvider`, which Robolectric does not run; every `Uri` here is a `file://` `Uri` pointing at
 * a Robolectric-filesystem file, and `ContentResolver.openInputStream` resolves a `file://` scheme
 * by opening the path directly, with no redaction logic in that code path on any API level. A test
 * asserting the persisted copy carries no GPS EXIF would therefore only be proving Robolectric's own
 * `file://` handling, not the platform behavior [FilePhotoStore]'s design actually depends on — per
 * CLAUDE.md's own rule against a check that doesn't test what it claims to, that assertion is left
 * unwritten rather than written misleadingly. This is a real, reported gap: confirming the stored
 * copy is actually free of GPS EXIF on a real device is a real-device verification step, not
 * something any unit test run here can stand in for.
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
     * capture's location comes only from a live GPS fix (see [com.forager.app.ui.log.MushroomLogViewModel]),
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
