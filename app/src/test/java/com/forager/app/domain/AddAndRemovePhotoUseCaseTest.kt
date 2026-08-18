package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AddPhotoToLogEntryUseCase]/[RemovePhotoFromLogEntryUseCase] against hand-written fakes — what's
 * worth verifying here is the ordering these two classes exist to get right: persist (or delete)
 * happens before the entry is saved with the updated photo list, and a failed persist must not
 * attach a photo the store doesn't actually have.
 */
class AddAndRemovePhotoUseCaseTest {

    private val entry = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
    private object Source : PhotoSource

    @Test
    fun `AddPhotoToLogEntryUseCase persists the photo, attaches it, and saves the updated entry`() = runTest {
        val photoStore = FakePhotoStore(persistResult = Result.success(LogPhoto(id = "p1", relativePath = "photos/p1.jpg")))
        val repository = FakeMushroomLogRepository()

        val result = AddPhotoToLogEntryUseCase(photoStore, repository)(entry, Source)

        val updated = result.getOrThrow()
        assertEquals(listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg")), updated.photos)
        assertEquals(updated, repository.saved.single())
    }

    @Test
    fun `AddPhotoToLogEntryUseCase does not attach or save anything when persist fails`() = runTest {
        val failure = RuntimeException("disk full")
        val photoStore = FakePhotoStore(persistResult = Result.failure(failure))
        val repository = FakeMushroomLogRepository()

        val result = AddPhotoToLogEntryUseCase(photoStore, repository)(entry, Source)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertTrue("a failed persist must not reach the repository", repository.saved.isEmpty())
    }

    @Test
    fun `RemovePhotoFromLogEntryUseCase deletes the photo, detaches it, and saves the updated entry`() = runTest {
        val photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg")
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val photoStore = FakePhotoStore(deleteResult = Result.success(Unit))
        val repository = FakeMushroomLogRepository()

        val result = RemovePhotoFromLogEntryUseCase(photoStore, repository)(entryWithPhoto, photo)

        val updated = result.getOrThrow()
        assertTrue(updated.photos.isEmpty())
        assertEquals(updated, repository.saved.single())
    }

    @Test
    fun `RemovePhotoFromLogEntryUseCase does not detach or save anything when delete fails`() = runTest {
        val photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg")
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val failure = RuntimeException("permission denied")
        val photoStore = FakePhotoStore(deleteResult = Result.failure(failure))
        val repository = FakeMushroomLogRepository()

        val result = RemovePhotoFromLogEntryUseCase(photoStore, repository)(entryWithPhoto, photo)

        assertTrue(result.isFailure)
        assertTrue("a failed delete must not reach the repository", repository.saved.isEmpty())
    }
}

private class FakePhotoStore(
    private val persistResult: Result<LogPhoto> = Result.success(LogPhoto(id = "unused", relativePath = "unused")),
    private val deleteResult: Result<Unit> = Result.success(Unit),
) : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = persistResult
    override suspend fun delete(photo: LogPhoto): Result<Unit> = deleteResult
}

private class FakeMushroomLogRepository : MushroomLogRepository {
    val saved = mutableListOf<MushroomLogEntry>()
    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(saved.toList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> {
        saved.add(entry)
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        saved.removeAll { it.id == id }
        return Result.success(Unit)
    }
}
