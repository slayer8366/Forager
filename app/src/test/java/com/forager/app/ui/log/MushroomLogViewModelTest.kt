package com.forager.app.ui.log

import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteGalleryPhotoUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.GetOrphanedDraftEntriesUseCase
import com.forager.app.domain.MushroomLogRepository
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.PullPhotoIntoEntryUseCase
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
        getOrphanedDraftEntries = GetOrphanedDraftEntriesUseCase(repository),
        createEntry = CreateMushroomLogEntryUseCase(repository, today = { LocalDate.of(2026, 8, 1) }, idGenerator = { "new-entry" }),
        saveEntry = SaveMushroomLogEntryUseCase(repository),
        deleteEntry = DeleteMushroomLogEntryUseCase(repository),
        addPhoto = AddPhotoToLogEntryUseCase(photoStore, repository),
        removePhoto = RemovePhotoFromLogEntryUseCase(repository),
        getGalleryPhotos = GetGalleryPhotosUseCase(repository),
        pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(repository),
        deleteGalleryPhoto = DeleteGalleryPhotoUseCase(repository, photoStore),
    )

    // isDraft = false: every test below seeds this as an already-committed, pre-existing entry
    // (loaded via getEntries()/GetMushroomLogEntriesUseCase, which now excludes drafts) unless it
    // specifically exercises the draft lifecycle itself, in which case it says so.
    private val entry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)

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
     * nine different write sites (start/edit/save/cancel/delete/add photo/remove photo/pull
     * photo/delete gallery photo), not one — unlike
     * [com.forager.app.ui.track.TrackRecordingViewModel.startRecording]'s single-site
     * `startRecordingErrorMessage`. "The next successful save" is read broadly here: any of the
     * nine succeeding clears a message left by any of the others, not only a repeat of the same
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

    /** Workstream G3: pulling a gallery photo into the currently-editing entry references it only — never a new file, never a new gallery row. */
    @Test
    fun `pulling a gallery photo into the editing entry adds a reference only`() = runTest(dispatcher) {
        val existingPhoto = LogPhoto(id = "gallery-photo", relativePath = "photos/gallery-photo.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        repository.addPhotoToGallery(existingPhoto)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)

        vm.onPullPhoto(existingPhoto)
        advanceUntilIdle()

        // The reference lands on the entry; whether it's a reference-only write (no new file, no
        // new gallery row) is PullPhotoIntoEntryUseCaseTest's own job to prove against a fake that
        // tracks addPhotoToGallery calls directly.
        assertEquals(listOf(existingPhoto), vm.uiState.value.editingEntry?.photos)
    }

    /** Workstream G3: closes the deletion gap — deleting refreshes both the gallery and the entry list. */
    @Test
    fun `deleting a gallery photo removes it from the gallery and refreshes entries`() = runTest(dispatcher) {
        val photo = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val repository = FakeMushroomLogRepository(initial = listOf(entryWithPhoto))
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onDeleteGalleryPhoto(GalleryPhoto(photo = photo, referencingEntryIds = listOf(entry.id)))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.galleryPhotos.isEmpty())
        assertEquals(emptyList<LogPhoto>(), vm.uiState.value.entries.single { it.id == entry.id }.photos)
    }

    /**
     * The dispatch's own required case: a file-deletion failure must still remove the rows (the
     * gallery photo disappears either way) and must be logged, not silently swallowed or allowed
     * to block the row deletion the user actually asked for and was warned about.
     */
    @Test
    fun `a file-deletion failure during gallery deletion still removes the rows`() = runTest(dispatcher) {
        val photo = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry.copy(photos = listOf(photo))))
        val photoStore = FakePhotoStore(deleteResult = Result.failure(RuntimeException("permission denied")))
        val vm = viewModel(repository, photoStore)
        advanceUntilIdle()

        vm.onDeleteGalleryPhoto(GalleryPhoto(photo = photo, referencingEntryIds = listOf(entry.id)))
        advanceUntilIdle()

        assertTrue("the row is gone even though the file delete failed", vm.uiState.value.galleryPhotos.isEmpty())
        assertEquals(listOf(photo), photoStore.deletedPhotos)
    }

    /** An entry left open in the background (no tab switch closes [MushroomLogUiState.editingEntry]) must not keep showing a reference to a photo the gallery just deleted. */
    @Test
    fun `deleting a gallery photo that the open editing entry references updates that entry too`() = runTest(dispatcher) {
        val photo = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)
        val entryWithPhoto = entry.copy(photos = listOf(photo))
        val repository = FakeMushroomLogRepository(initial = listOf(entryWithPhoto))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)

        vm.onDeleteGalleryPhoto(GalleryPhoto(photo = photo, referencingEntryIds = listOf(entry.id)))
        advanceUntilIdle()

        assertEquals(emptyList<LogPhoto>(), vm.uiState.value.editingEntry?.photos)
    }

    // --- Workstream L4b: persisted drafts, Save/Cancel/incidental-exit, crash recovery ----------

    @Test
    fun `a freshly-started entry is a draft, invisible in entries until it is resolved`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()

        assertTrue("a brand-new entry must not appear in the committed list", vm.uiState.value.entries.isEmpty())
        assertEquals("new-entry", vm.uiState.value.editingEntry?.id)
        assertTrue(vm.uiState.value.editingEntry?.isDraft == true)

        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "something recorded"))
        advanceUntilIdle()

        assertTrue("still a draft while being edited, still invisible in the list", vm.uiState.value.entries.isEmpty())
        assertEquals("something recorded", vm.uiState.value.editingEntry?.notes)
    }

    @Test
    fun `Save commits the currently-open entry, making it visible in the list`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()
        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "field notes"))
        advanceUntilIdle()

        vm.onSaveEntry()
        advanceUntilIdle()

        assertEquals(listOf("new-entry"), vm.uiState.value.entries.map { it.id })
        assertEquals(false, vm.uiState.value.entries.single().isDraft)
        assertEquals("field notes", vm.uiState.value.editingEntry?.notes)
    }

    @Test
    fun `Cancelling a brand-new entry deletes it, leaving nothing in the log`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()
        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "typed something before changing my mind"))
        advanceUntilIdle()

        vm.onCancelEditing()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.entries.isEmpty())
        assertNull(vm.uiState.value.editingEntry)
        assertTrue("the row itself must be gone, not just filtered out", repository.getAll().getOrThrow().none { it.id == "new-entry" })
    }

    @Test
    fun `Cancelling a reopened existing entry restores its last-saved content`() = runTest(dispatcher) {
        val original = entry.copy(notes = "original field notes")
        val repository = FakeMushroomLogRepository(initial = listOf(original))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(original.id)
        vm.onEntryEdited(original.copy(notes = "changed my mind about this"))
        advanceUntilIdle()
        assertEquals("changed my mind about this", vm.uiState.value.editingEntry?.notes)

        vm.onCancelEditing()
        advanceUntilIdle()

        // Cancel always closes the form, whether it deleted (a brand-new entry) or restored (this
        // case) — see MushroomLogViewModel.onCancelEditing's own doc comment; the restored content
        // is verified through entries, the list Cancel actually leaves the user looking at.
        assertNull(vm.uiState.value.editingEntry)
        assertEquals("original field notes", vm.uiState.value.entries.single { it.id == original.id }.notes)
        assertEquals(false, vm.uiState.value.entries.single { it.id == original.id }.isDraft)
    }

    @Test
    fun `leaving without answering commits a brand-new entry that has some content`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()
        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "partial notes"))
        advanceUntilIdle()

        vm.onLeaveEditingIncidentally()
        advanceUntilIdle()

        assertEquals(listOf("new-entry"), vm.uiState.value.entries.map { it.id })
        assertEquals(false, vm.uiState.value.entries.single().isDraft)
        assertNull("the form closes on an incidental exit", vm.uiState.value.editingEntry)
    }

    /** The dispatch's own gate: tapping "+" and leaving immediately puts nothing visible in the log. */
    @Test
    fun `leaving without answering an untouched brand-new entry deletes it`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()

        vm.onLeaveEditingIncidentally()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.entries.isEmpty())
        assertNull(vm.uiState.value.editingEntry)
        assertTrue(repository.getAll().getOrThrow().none { it.id == "new-entry" })
    }

    @Test
    fun `a photo pulled into a draft attaches on Save and the gallery photo survives either way`() = runTest(dispatcher) {
        val galleryPhoto = LogPhoto(id = "gallery-photo", relativePath = "photos/gallery-photo.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        repository.addPhotoToGallery(galleryPhoto)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onPullPhoto(galleryPhoto)
        advanceUntilIdle()

        vm.onSaveEntry()
        advanceUntilIdle()

        assertEquals(listOf(galleryPhoto), vm.uiState.value.entries.single { it.id == entry.id }.photos)
        assertTrue(repository.galleryPhotoIds.contains(galleryPhoto.id))
    }

    @Test
    fun `a photo pulled into a draft does not attach on Cancel, and the gallery photo survives`() = runTest(dispatcher) {
        val galleryPhoto = LogPhoto(id = "gallery-photo", relativePath = "photos/gallery-photo.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        repository.addPhotoToGallery(galleryPhoto)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onPullPhoto(galleryPhoto)
        advanceUntilIdle()
        assertEquals(listOf(galleryPhoto), vm.uiState.value.editingEntry?.photos)

        vm.onCancelEditing()
        advanceUntilIdle()

        assertEquals(emptyList<LogPhoto>(), vm.uiState.value.entries.single { it.id == entry.id }.photos)
        assertTrue("the gallery photo itself must survive Cancel", repository.galleryPhotoIds.contains(galleryPhoto.id))
    }

    /**
     * The known hazard this dispatch calls out by name: G3's [MushroomLogViewModel.loadEntries]
     * used to re-derive [MushroomLogUiState.editingEntry] wholesale from a fresh repository read —
     * safe only because nothing uncommitted existed to lose at the time. A refresh firing mid-edit
     * (e.g. [MushroomLogViewModel.onDeleteGalleryPhoto]'s own success-path refresh) must not
     * silently discard in-progress typing.
     */
    @Test
    fun `a refresh triggered mid-edit preserves uncommitted edits`() = runTest(dispatcher) {
        val original = entry.copy(notes = "original")
        val repository = FakeMushroomLogRepository(initial = listOf(original))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(original.id)
        vm.onEntryEdited(original.copy(notes = "typing away, not saved yet"))
        advanceUntilIdle()

        vm.loadEntries()
        advanceUntilIdle()

        assertEquals(
            "a refresh must not discard uncommitted edits",
            "typing away, not saved yet",
            vm.uiState.value.editingEntry?.notes,
        )
    }

    @Test
    fun `an orphaned draft from a crashed session is surfaced separately from committed entries and can be reinstated`() = runTest(dispatcher) {
        val committed = entry
        val orphanedDraft = MushroomLogEntry.draft(id = "orphaned", location = LatLng(46.0, -123.0), date = LocalDate.of(2026, 8, 2))
            .copy(notes = "typed before the crash")
        val repository = FakeMushroomLogRepository(initial = listOf(committed, orphanedDraft))
        val vm = viewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(committed.id), vm.uiState.value.entries.map { it.id })
        assertEquals(listOf(orphanedDraft.id), vm.uiState.value.draftEntries.map { it.id })

        vm.onOpenEntry(orphanedDraft.id)

        assertEquals("typed before the crash", vm.uiState.value.editingEntry?.notes)
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

    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> {
        galleryPhotos.remove(photoId)
        crossRefs.removeAll { it.second == photoId }
        return Result.success(Unit)
    }
}

private class FakePhotoStore(
    var persistResult: Result<LogPhoto> = Result.failure(UnsupportedOperationException("photo persistence not exercised by this test")),
    var deleteResult: Result<Unit> = Result.success(Unit),
) : PhotoStore {
    val deletedPhotos = mutableListOf<LogPhoto>()

    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = persistResult

    override suspend fun delete(photo: LogPhoto): Result<Unit> {
        deletedPhotos += photo
        return deleteResult
    }
}
