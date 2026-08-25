package com.forager.app.domain

import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeleteGalleryPhotoUseCase] against hand-written fakes — Workstream G3. Covers the ordering
 * choice (rows first, file last) and its own failure path directly, at the use-case level; the
 * real Room-backed row/cross-reference deletion is [com.forager.app.data.repository.RoomMushroomLogRepositoryTest]'s
 * to prove.
 */
class DeleteGalleryPhotoUseCaseTest {

    private val photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)

    @Test
    fun `deletes the row before the file, and reports success`() = runTest {
        val calls = mutableListOf<String>()
        val repository = DeleteFakeMushroomLogRepository(
            onDeletePhotoFromGallery = { calls += "row" },
        )
        val photoStore = DeleteFakePhotoStore(onDelete = { calls += "file" })

        val result = DeleteGalleryPhotoUseCase(repository, photoStore)(photo)

        assertTrue(result.isSuccess)
        assertEquals(listOf("row", "file"), calls)
    }

    @Test
    fun `a failed file deletion still reports overall success, and reports the failure via the callback`() = runTest {
        val fileFailure = RuntimeException("permission denied")
        val repository = DeleteFakeMushroomLogRepository()
        val photoStore = DeleteFakePhotoStore(deleteResult = Result.failure(fileFailure))
        var reported: Throwable? = null

        val result = DeleteGalleryPhotoUseCase(repository, photoStore)(photo) { error -> reported = error }

        assertTrue("the rows are already gone — a leftover file is a leak, not a reason to fail", result.isSuccess)
        assertEquals(fileFailure, reported)
    }

    @Test
    fun `a failed row deletion fails the whole operation and never attempts the file delete`() = runTest {
        val rowFailure = RuntimeException("database busy")
        val repository = DeleteFakeMushroomLogRepository(deletePhotoResult = Result.failure(rowFailure))
        var fileDeleteCalled = false
        val photoStore = DeleteFakePhotoStore(onDelete = { fileDeleteCalled = true })

        val result = DeleteGalleryPhotoUseCase(repository, photoStore)(photo)

        assertTrue(result.isFailure)
        assertEquals(rowFailure, result.exceptionOrNull())
        assertTrue("a row-delete failure must not risk deleting a file whose row still points at it", !fileDeleteCalled)
    }
}

private class DeleteFakeMushroomLogRepository(
    private val deletePhotoResult: Result<Unit> = Result.success(Unit),
    private val onDeletePhotoFromGallery: () -> Unit = {},
) : MushroomLogRepository {
    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(emptyList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> = Result.success(Unit)
    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> = Result.success(Unit)
    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> {
        if (deletePhotoResult.isSuccess) onDeletePhotoFromGallery()
        return deletePhotoResult
    }
}

private class DeleteFakePhotoStore(
    private val deleteResult: Result<Unit> = Result.success(Unit),
    private val onDelete: () -> Unit = {},
) : PhotoStore {
    override suspend fun persist(source: com.forager.app.domain.model.PhotoSource): Result<LogPhoto> =
        Result.failure(UnsupportedOperationException("not exercised by this use case"))

    override suspend fun delete(photo: LogPhoto): Result<Unit> {
        onDelete()
        return deleteResult
    }
}
