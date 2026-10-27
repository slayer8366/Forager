package com.zynergylabs.forager.app.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomKeptTrackPathRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-27-05 (RECORDs -1734 and -1735) on an entry's own map: [GetCartographyEntryMapDataUseCase] is the only
 * way an entry's geometry reaches a map (its class comment), so its photo markers are what the entry map draws. Over a
 * real in-memory database, with the gallery photos read from it as the screen's are, one test per ruling of Amendment 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindPhotosOffEntryMapTest {

    private lateinit var database: ForagerDatabase
    private lateinit var log: RoomMushroomLogRepository
    private val logged = mutableListOf<String>()

    private val day = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
        log = RoomMushroomLogRepository(database.mushroomLogDao())
    }

    @After
    fun tearDown() = database.close()

    private fun useCase(finds: MushroomLogRepository = log) = GetCartographyEntryMapDataUseCase(
        RoomTrackRepository(database.trackDao()),
        finds,
        RoomKeptTrackPathRepository(database.cartographyEntryDao()),
        ErrorLog { _, message, _ -> logged += message },
    )

    private suspend fun find(id: String, at: LatLng?, draft: Boolean = false) {
        log.save(MushroomLogEntry.draft(id = id, location = at, date = day).copy(isDraft = draft)).getOrThrow()
    }

    private suspend fun photo(id: String, toFind: String? = null) {
        log.addPhotoToGallery(LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1L, latitude = 45.5, longitude = -122.6)).getOrThrow()
        if (toFind != null) log.attachPhotoToEntry(toFind, id).getOrThrow()
    }

    /** An entry attaching [photoIds], drawn as the entry map draws it. */
    private suspend fun drawnPhotos(vararg photoIds: String, finds: MushroomLogRepository = log): List<String> {
        val entry = CartographyEntry.draft(id = "entry-1", date = day, updatedAtEpochMillis = 1L)
            .copy(isDraft = false, photos = photoIds.map { PhotoAttachment(it, 1L) })
        return useCase(finds)(entry, log.getAllPhotos().getOrThrow()).photoMarkers.map { it.recordId }
    }

    /** Amendment 1, "No icon anywhere (Recommended)". */
    @Test
    fun `a photo the entry attaches that a saved, located find links to is not drawn, and a standalone one is`() = runTest {
        find("find-1", LatLng(45.5, -122.6))
        photo("ph-of-find", toFind = "find-1")
        photo("standalone")

        assertEquals(listOf("standalone"), drawnPhotos("ph-of-find", "standalone"))
        assertEquals(emptyList<String>(), logged)
    }

    /** Amendment 1, "Show its photos (Recommended)". */
    @Test
    fun `a photo of a find with no location is drawn`() = runTest {
        find("unlocated", at = null)
        photo("ph-a", toFind = "unlocated")

        assertEquals(listOf("ph-a"), drawnPhotos("ph-a"))
    }

    /** Amendment 1, "Hide only after Save (Recommended)". */
    @Test
    fun `a photo of a draft find is drawn until the find is saved`() = runTest {
        find("new-find", LatLng(45.5, -122.6), draft = true)
        photo("ph-a", toFind = "new-find")
        assertEquals("while a draft", listOf("ph-a"), drawnPhotos("ph-a"))

        log.commitDraft("new-find", MushroomLogEntry.draft(id = "new-find", location = LatLng(45.5, -122.6), date = day).copy(isDraft = false)).getOrThrow()

        assertEquals("once saved", emptyList<String>(), drawnPhotos("ph-a"))
    }

    /** Amendment 1, "Fine, they reappear (Recommended)". */
    @Test
    fun `a photo of a deleted find is drawn again`() = runTest {
        find("find-1", LatLng(45.5, -122.6))
        photo("ph-a", toFind = "find-1")
        assertEquals("positive control: hidden while the find is saved", emptyList<String>(), drawnPhotos("ph-a"))

        log.delete("find-1").getOrThrow()

        assertEquals(listOf("ph-a"), drawnPhotos("ph-a"))
    }

    @Test
    fun `when the finds cannot be read, linked photos are left out, standalone ones drawn, and the failure is logged`() = runTest {
        find("find-1", LatLng(45.5, -122.6))
        photo("ph-of-find", toFind = "find-1")
        photo("standalone")
        val failing = object : MushroomLogRepository by log {
            override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.failure(IOException("finds unreadable"))
        }

        assertEquals(listOf("standalone"), drawnPhotos("ph-of-find", "standalone", finds = failing))
        assertEquals(1, logged.size)
    }
}
