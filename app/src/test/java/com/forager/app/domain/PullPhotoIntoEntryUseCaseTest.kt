package com.forager.app.domain

import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PullPhotoIntoEntryUseCase] against a hand-written fake — Workstream G3. The dispatch's own
 * gate item ("no new file and no new `log_photos` row") is asserted here at the type level (this
 * use case has no [PhotoStore] dependency at all — there is no persist path to accidentally route
 * through) and against the real Room database in [com.forager.app.data.repository.RoomMushroomLogRepositoryTest].
 */
class PullPhotoIntoEntryUseCaseTest {

    private val entry = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
    private val photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)

    @Test
    fun `pulling a gallery photo into an entry attaches a reference only, and returns the entry with it added`() = runTest {
        val repository = PullFakeMushroomLogRepository()

        val result = PullPhotoIntoEntryUseCase(repository)(entry, photo)

        val updated = result.getOrThrow()
        assertEquals(listOf(photo), updated.photos)
        assertEquals(listOf(entry.id to photo.id), repository.attached)
        assertTrue("must never touch the gallery-add path — the photo already exists", repository.addedToGallery.isEmpty())
    }

    @Test
    fun `pulling the same photo twice into the same entry does not duplicate it in the returned entry`() = runTest {
        val repository = PullFakeMushroomLogRepository()
        val entryWithPhoto = PullPhotoIntoEntryUseCase(repository)(entry, photo).getOrThrow()

        val result = PullPhotoIntoEntryUseCase(repository)(entryWithPhoto, photo)

        assertEquals(listOf(photo), result.getOrThrow().photos)
    }

    @Test
    fun `reports failure when attaching fails`() = runTest {
        val failure = RuntimeException("database busy")
        val repository = PullFakeMushroomLogRepository(attachResult = Result.failure(failure))

        val result = PullPhotoIntoEntryUseCase(repository)(entry, photo)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}

private class PullFakeMushroomLogRepository(
    private val attachResult: Result<Unit> = Result.success(Unit),
) : MushroomLogRepository {
    val addedToGallery = mutableListOf<LogPhoto>()
    val attached = mutableListOf<Pair<String, String>>()

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(emptyList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> {
        addedToGallery += photo
        return Result.success(Unit)
    }

    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> {
        if (attachResult.isSuccess) attached += entryId to photoId
        return attachResult
    }

    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))

    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))

    override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))
}
