package com.forager.app.photo

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val result = store.persist(ContentUriPhotoSource(Uri.fromFile(sourceFile)))

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

        val photoA = store.persist(ContentUriPhotoSource(Uri.fromFile(sourceA))).getOrThrow()
        val photoB = store.persist(ContentUriPhotoSource(Uri.fromFile(sourceB))).getOrThrow()

        assertFalse(photoA.relativePath == photoB.relativePath)
        assertArrayEquals(byteArrayOf(1), File(context.filesDir, photoA.relativePath).readBytes())
        assertArrayEquals(byteArrayOf(2), File(context.filesDir, photoB.relativePath).readBytes())
    }

    @Test
    fun `persist from a source that cannot be opened reports failure, not a path to a missing file`() = runTest {
        val missingSource = File(sourceDir, "never-existed.jpg")

        val result = store.persist(ContentUriPhotoSource(Uri.fromFile(missingSource)))

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
        val photo = store.persist(ContentUriPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()
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
    fun `persist stamps the injected clock's time, not left null`() = runTest {
        val clockedStore = FilePhotoStore(context, now = { 1_700_000_000_000L })
        val sourceFile = File(sourceDir, "capture.jpg").apply { writeBytes(byteArrayOf(7)) }

        val photo = clockedStore.persist(ContentUriPhotoSource(Uri.fromFile(sourceFile))).getOrThrow()

        assertEquals(1_700_000_000_000L, photo.createdAtEpochMillis)
    }
}
