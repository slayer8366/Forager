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
 * [AddPhotoToLogEntryUseCase]/[RemovePhotoFromLogEntryUseCase] against hand-written fakes.
 *
 * [AddPhotoToLogEntryUseCase] still has ordering worth verifying: persist, then add to the
 * gallery, then attach to the entry — a failed step must not let a later one run (a failed persist
 * must not reach the gallery; a failed gallery-add must not attach a reference to a photo that was
 * never actually added).
 *
 * [RemovePhotoFromLogEntryUseCase] is reversed as of gallery ownership (owner decision,
 * 2026-08-22): it detaches the reference only now, never deletes the photo's file or its gallery
 * row — see that class's own doc comment for why this is a correction to a model that changed
 * underneath it, not a bug fix. [PhotoStore] is no longer one of its dependencies at all.
 */
class AddAndRemovePhotoUseCaseTest {

    private val entry = MushroomLogEntry.draft(id = "e1", location = LatLng(45.0, -122.0), date = LocalDate.of(2026, 8, 1))
    private object Source : PhotoSource
    private val photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)

    @Test
    fun `AddPhotoToLogEntryUseCase persists the photo, adds it to the gallery, attaches it, and returns the updated entry`() = runTest {
        val photoStore = FakePhotoStore(persistResult = Result.success(photo))
        val repository = FakeMushroomLogRepository()

        val result = AddPhotoToLogEntryUseCase(photoStore, repository)(entry, Source)

        val updated = result.getOrThrow()
        assertEquals(listOf(photo), updated.photos)
        assertEquals(listOf(photo), repository.addedToGallery)
        assertEquals(listOf(entry.id to photo.id), repository.attached)
    }

    @Test
    fun `AddPhotoToLogEntryUseCase does not touch the gallery or attach anything when persist fails`() = runTest {
        val failure = RuntimeException("disk full")
        val photoStore = FakePhotoStore(persistResult = Result.failure(failure))
        val repository = FakeMushroomLogRepository()

        val result = AddPhotoToLogEntryUseCase(photoStore, repository)(entry, Source)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertTrue("a failed persist must not reach the gallery", repository.addedToGallery.isEmpty())
        assertTrue("a failed persist must not attach anything", repository.attached.isEmpty())
    }

    @Test
    fun `AddPhotoToLogEntryUseCase does not attach when adding to the gallery fails`() = runTest {
        val failure = RuntimeException("disk full")
        val photoStore = FakePhotoStore(persistResult = Result.success(photo))
        val repository = FakeMushroomLogRepository(addToGalleryResult = Result.failure(failure))

        val result = AddPhotoToLogEntryUseCase(photoStore, repository)(entry, Source)

        assertTrue(result.isFailure)
        assertTrue("a failed gallery-add must not attach a reference to a photo the gallery doesn't have", repository.attached.isEmpty())
    }

    @Test
    fun `RemovePhotoFromLogEntryUseCase detaches the reference and returns the updated entry, never touching PhotoStore`() = runTest {
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val repository = FakeMushroomLogRepository()

        val result = RemovePhotoFromLogEntryUseCase(repository)(entryWithPhoto, photo)

        val updated = result.getOrThrow()
        assertTrue(updated.photos.isEmpty())
        assertEquals(listOf(entry.id to photo.id), repository.detached)
    }

    @Test
    fun `RemovePhotoFromLogEntryUseCase reports failure and returns nothing when detach fails`() = runTest {
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val failure = RuntimeException("database busy")
        val repository = FakeMushroomLogRepository(detachResult = Result.failure(failure))

        val result = RemovePhotoFromLogEntryUseCase(repository)(entryWithPhoto, photo)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}

private class FakePhotoStore(
    private val persistResult: Result<LogPhoto> = Result.success(LogPhoto(id = "unused", relativePath = "unused", createdAtEpochMillis = 0L)),
) : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = persistResult
    override suspend fun delete(photo: LogPhoto): Result<Unit> =
        Result.failure(UnsupportedOperationException("PhotoStore.delete is not exercised by either use case under test here"))
}

private class FakeMushroomLogRepository(
    private val addToGalleryResult: Result<Unit> = Result.success(Unit),
    private val detachResult: Result<Unit> = Result.success(Unit),
) : MushroomLogRepository {
    val addedToGallery = mutableListOf<LogPhoto>()
    val attached = mutableListOf<Pair<String, String>>()
    val detached = mutableListOf<Pair<String, String>>()

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> {
        if (addToGalleryResult.isSuccess) addedToGallery += photo
        return addToGalleryResult
    }

    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> {
        attached += entryId to photoId
        return Result.success(Unit)
    }

    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> {
        if (detachResult.isSuccess) detached += entryId to photoId
        return detachResult
    }
}
