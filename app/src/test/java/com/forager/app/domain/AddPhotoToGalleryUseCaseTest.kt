package com.forager.app.domain

import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AddPhotoToGalleryUseCase] against a hand-written fake — standalone-photos dispatch. Mirrors
 * [AddAndRemovePhotoUseCaseTest]'s own coverage of [AddPhotoToLogEntryUseCase]'s persist-then-add
 * ordering, minus the attach step this use case deliberately never takes: the whole point is
 * acquisition with no owning find.
 */
class AddPhotoToGalleryUseCaseTest {

    private object Source : PhotoSource
    private val photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)

    @Test
    fun `persists the photo, adds it to the gallery, and returns it, without attaching to anything`() = runTest {
        val photoStore = GalleryFakePhotoStore(persistResult = Result.success(photo))
        val repository = GalleryFakeMushroomLogRepository()

        val result = AddPhotoToGalleryUseCase(photoStore, repository)(Source)

        assertEquals(photo, result.getOrThrow())
        assertEquals(listOf(photo), repository.addedToGallery)
        assertTrue("must never attach — there is no entry to attach to", repository.attached.isEmpty())
    }

    @Test
    fun `does not touch the gallery when persist fails`() = runTest {
        val failure = RuntimeException("disk full")
        val photoStore = GalleryFakePhotoStore(persistResult = Result.failure(failure))
        val repository = GalleryFakeMushroomLogRepository()

        val result = AddPhotoToGalleryUseCase(photoStore, repository)(Source)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertTrue("a failed persist must not reach the gallery", repository.addedToGallery.isEmpty())
    }

    @Test
    fun `reports failure when adding to the gallery fails`() = runTest {
        val failure = RuntimeException("database busy")
        val photoStore = GalleryFakePhotoStore(persistResult = Result.success(photo))
        val repository = GalleryFakeMushroomLogRepository(addToGalleryResult = Result.failure(failure))

        val result = AddPhotoToGalleryUseCase(photoStore, repository)(Source)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}

private class GalleryFakePhotoStore(
    private val persistResult: Result<LogPhoto>,
) : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = persistResult
    override suspend fun delete(photo: LogPhoto): Result<Unit> =
        Result.failure(UnsupportedOperationException("PhotoStore.delete is not exercised by this use case"))
}

private class GalleryFakeMushroomLogRepository(
    private val addToGalleryResult: Result<Unit> = Result.success(Unit),
) : MushroomLogRepository {
    val addedToGallery = mutableListOf<LogPhoto>()
    val attached = mutableListOf<Pair<String, String>>()

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> =
        Result.failure(UnsupportedOperationException("getAllPhotos is not exercised by this use case"))
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> {
        if (addToGalleryResult.isSuccess) addedToGallery += photo
        return addToGalleryResult
    }

    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> {
        attached += entryId to photoId
        return Result.success(Unit)
    }

    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))

    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))

    override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))
}
