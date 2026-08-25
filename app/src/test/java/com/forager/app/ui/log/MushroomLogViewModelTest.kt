package com.forager.app.ui.log

import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.MushroomLogRepository
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [MushroomLogUiState.saveErrorMessage]'s clearing rule — "cleared on dismiss or on the next
 * successful save, whichever comes first" (docs/error-presentation-spec.md) — over the real
 * [MushroomLogViewModel] and real use cases, against an in-memory [MushroomLogRepository]/
 * [PhotoStore] fake rather than a hand-built stand-in for the ViewModel's own state.
 *
 * [MushroomLogViewModel] had no test file before this one — every `onFailure` branch still calls
 * [android.util.Log.w] directly (untouched here; the section (a) fix intentionally left this file
 * alone — see the ErrorLog seam's own doc comment), which is unmocked and throws under a plain JVM
 * test. Robolectric is used for exactly that reason, not because this ViewModel needs Compose —
 * there is none here. The dispatcher setup otherwise mirrors [com.forager.app.ui.track.TrackRecordingViewModelTest]'s
 * plain-JVM shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MushroomLogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeMushroomLogRepository = FakeMushroomLogRepository(),
        photoStore: FakePhotoStore = FakePhotoStore(),
    ) = MushroomLogViewModel(
        getEntries = GetMushroomLogEntriesUseCase(repository),
        createEntry = CreateMushroomLogEntryUseCase(repository, today = { LocalDate.of(2026, 8, 1) }, idGenerator = { "new-entry" }),
        saveEntry = SaveMushroomLogEntryUseCase(repository),
        deleteEntry = DeleteMushroomLogEntryUseCase(repository),
        addPhoto = AddPhotoToLogEntryUseCase(photoStore, repository),
        removePhoto = RemovePhotoFromLogEntryUseCase(repository),
        getGalleryPhotos = GetGalleryPhotosUseCase(repository),
    )

    private val entry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))

    @Test
    fun `a failed save sets saveErrorMessage, and the next successful save clears it`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry), saveShouldFail = true)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onEntryEdited(entry.copy(notes = "first attempt"))
        advanceUntilIdle()
        assertEquals("Couldn't save your changes.", vm.uiState.value.saveErrorMessage)

        repository.saveShouldFail = false
        vm.onEntryEdited(entry.copy(notes = "second attempt"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.saveErrorMessage)
    }

    @Test
    fun `onSaveErrorDismissed clears saveErrorMessage`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry), saveShouldFail = true)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onEntryEdited(entry.copy(notes = "will fail"))
        advanceUntilIdle()
        assertEquals("Couldn't save your changes.", vm.uiState.value.saveErrorMessage)

        vm.onSaveErrorDismissed()
        assertNull(vm.uiState.value.saveErrorMessage)
    }

    /**
     * The design decision this task made and the prompt didn't cover: [saveErrorMessage] is set by
     * five different write sites (start/save/delete/add photo/remove photo), not one — unlike
     * [com.forager.app.ui.track.TrackRecordingViewModel.startRecording]'s single-site
     * `startRecordingErrorMessage`. "The next successful save" is read broadly here: any of the
     * five succeeding clears a message left by any of the others, not only a repeat of the same
     * one. This is the write site that isn't literally "save" proving that reading holds.
     */
    @Test
    fun `a successful delete clears a saveErrorMessage left by an earlier failed save`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry), saveShouldFail = true)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onEntryEdited(entry.copy(notes = "will fail"))
        advanceUntilIdle()
        assertEquals("Couldn't save your changes.", vm.uiState.value.saveErrorMessage)

        repository.saveShouldFail = false
        vm.onDeleteEntry(entry.id)
        advanceUntilIdle()
        assertNull(vm.uiState.value.saveErrorMessage)
    }

    /**
     * L1's reversal, at the ViewModel level (see [DeleteMushroomLogEntryUseCase]'s own doc comment
     * for the full history) — under gallery ownership, deleting an entry removes it from the list
     * and drops its photo *references*, but the gallery photo itself survives, and
     * [com.forager.app.domain.PhotoStore] is never called at all any more. Was:
     * "deleting an entry with photos deletes both its row and its photo files."
     */
    @Test
    fun `deleting an entry with photos removes it from the list but leaves the gallery photo and PhotoStore untouched`() = runTest(dispatcher) {
        val photo = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val repository = FakeMushroomLogRepository(initial = listOf(entryWithPhoto))
        val photoStore = FakePhotoStore()
        val vm = viewModel(repository, photoStore)
        advanceUntilIdle()

        vm.onDeleteEntry(entryWithPhoto.id)
        advanceUntilIdle()

        assertEquals(emptyList<MushroomLogEntry>(), vm.uiState.value.entries)
        assertTrue("the gallery photo itself must survive the entry that referenced it being deleted", repository.galleryPhotoIds.contains(photo.id))
        assertEquals("PhotoStore.delete must never be called by entry deletion any more", emptyList<LogPhoto>(), photoStore.deletedPhotos)
    }

    @Test
    fun `deleting an entry with no photos succeeds and never touches PhotoStore`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        val photoStore = FakePhotoStore()
        val vm = viewModel(repository, photoStore)
        advanceUntilIdle()

        vm.onDeleteEntry(entry.id)
        advanceUntilIdle()

        assertEquals(emptyList<MushroomLogEntry>(), vm.uiState.value.entries)
        assertEquals(emptyList<LogPhoto>(), photoStore.deletedPhotos)
    }

    // "a photo file that fails to delete still lets the entry deletion succeed" — removed, not
    // rewritten: the scenario it covered is no longer reachable. DeleteMushroomLogEntryUseCase
    // never calls PhotoStore at all any more (see its own doc comment on why), so there is no
    // photo-file-deletion step left for this test to prove resilience against.

    /** Workstream G2: [MushroomLogViewModel.loadGalleryPhotos] runs alongside [MushroomLogViewModel.loadEntries] on init, independently populating [MushroomLogUiState.galleryPhotos]. */
    @Test
    fun `the gallery photos load on init, independent of the entry list`() = runTest(dispatcher) {
        val photo = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val repository = FakeMushroomLogRepository(initial = listOf(entryWithPhoto))
        val vm = viewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(photo), vm.uiState.value.galleryPhotos.map { it.photo })
        assertEquals(false, vm.uiState.value.isLoadingGalleryPhotos)
    }

    /**
     * Without this, a freshly added photo would only show up in [PhotoGalleryScreen] after the
     * ViewModel is recreated — see [MushroomLogViewModel.onAddPhoto]'s own inline comment on why
     * this refresh exists.
     */
    @Test
    fun `adding a photo refreshes the gallery so the new photo appears without a restart`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        val photoStore = FakePhotoStore()
        val newPhoto = LogPhoto(id = "new-photo", relativePath = "photos/new-photo.jpg", createdAtEpochMillis = 2_000L)
        photoStore.persistResult = Result.success(newPhoto)
        val vm = viewModel(repository, photoStore)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)

        vm.onAddPhoto(object : PhotoSource {})
        advanceUntilIdle()

        assertEquals(listOf(newPhoto), vm.uiState.value.galleryPhotos.map { it.photo })
    }
}

private class FakeMushroomLogRepository(
    initial: List<MushroomLogEntry> = emptyList(),
    var saveShouldFail: Boolean = false,
) : MushroomLogRepository {
    private val entries = initial.associateByTo(LinkedHashMap()) { it.id }
    private val galleryPhotos = mutableMapOf<String, LogPhoto>()
    private val crossRefs = mutableSetOf<Pair<String, String>>()

    val galleryPhotoIds: Set<String> get() = galleryPhotos.keys

    init {
        initial.forEach { entry ->
            entry.photos.forEach { photo ->
                galleryPhotos[photo.id] = photo
                crossRefs += entry.id to photo.id
            }
        }
    }

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(
        entries.values.map { entry ->
            val photos = crossRefs.filter { it.first == entry.id }.mapNotNull { galleryPhotos[it.second] }
            entry.copy(photos = photos)
        },
    )

    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(
        galleryPhotos.values.map { photo ->
            GalleryPhoto(photo = photo, referencingEntryIds = crossRefs.filter { it.second == photo.id }.map { it.first })
        },
    )

    override suspend fun save(entry: MushroomLogEntry): Result<Unit> {
        if (saveShouldFail) return Result.failure(RuntimeException("save failed"))
        entries[entry.id] = entry
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        entries.remove(id)
        crossRefs.removeAll { it.first == id }
        return Result.success(Unit)
    }

    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> {
        galleryPhotos[photo.id] = photo
        return Result.success(Unit)
    }

    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> {
        crossRefs += entryId to photoId
        return Result.success(Unit)
    }

    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> {
        crossRefs -= entryId to photoId
        return Result.success(Unit)
    }
}

private class FakePhotoStore(
    var persistResult: Result<LogPhoto> = Result.failure(UnsupportedOperationException("photo persistence not exercised by this test")),
) : PhotoStore {
    val deletedPhotos = mutableListOf<LogPhoto>()

    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = persistResult

    override suspend fun delete(photo: LogPhoto): Result<Unit> {
        deletedPhotos += photo
        return Result.success(Unit)
    }
}
