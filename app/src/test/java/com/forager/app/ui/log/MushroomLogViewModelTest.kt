package com.forager.app.ui.log

import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CommitDraftEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteGalleryPhotoUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetDraftEntriesUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.MushroomLogRepository
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.PullPhotoIntoEntryUseCase
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
import com.forager.app.domain.StartEditingLogEntryUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [MushroomLogUiState.saveErrorMessage]'s clearing rule — "cleared on dismiss or on the next
 * successful save, whichever comes first" (docs/error-presentation-spec.md) — and the full
 * standalone-draft lifecycle (Workstream L4b-R) over the real [MushroomLogViewModel] and real use
 * cases, against an in-memory [MushroomLogRepository]/[PhotoStore] fake rather than a hand-built
 * stand-in for the ViewModel's own state.
 *
 * [MushroomLogViewModel] had no test file before Workstream L4b — every `onFailure` branch still
 * calls [android.util.Log.w] directly (unmocked, throws under a plain JVM test), which is why this
 * is Robolectric rather than a plain-JVM test — not because this ViewModel needs Compose; there is
 * none here.
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
        getDraftEntries = GetDraftEntriesUseCase(repository),
        createEntry = CreateMushroomLogEntryUseCase(repository, today = { LocalDate.of(2026, 8, 1) }, idGenerator = { "new-entry" }),
        startEditingEntry = StartEditingLogEntryUseCase(repository, idGenerator = { "draft-of-entry-1" }),
        saveEntry = SaveMushroomLogEntryUseCase(repository),
        commitDraftEntry = CommitDraftEntryUseCase(repository),
        deleteEntry = DeleteMushroomLogEntryUseCase(repository),
        addPhoto = AddPhotoToLogEntryUseCase(photoStore, repository),
        removePhoto = RemovePhotoFromLogEntryUseCase(repository),
        getGalleryPhotos = GetGalleryPhotosUseCase(repository),
        pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(repository),
        deleteGalleryPhoto = DeleteGalleryPhotoUseCase(repository, photoStore),
    )

    // isDraft = false: every test below seeds this as an already-committed, pre-existing entry
    // (loaded via getEntries()/GetMushroomLogEntriesUseCase, which excludes drafts) unless it
    // specifically exercises the draft lifecycle itself, in which case it says so.
    private val entry = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)

    /**
     * Reworked for Workstream L4b-R (was: called `onEntryEdited` directly on the committed [entry]
     * itself). Under the standalone-draft model that would overwrite the committed row in place —
     * exactly the bug this dispatch corrects — so every real edit session now goes through
     * [MushroomLogViewModel.onStartEditingEntry] first, which creates the separate draft row
     * [MushroomLogViewModel.onEntryEdited] actually writes to.
     */
    @Test
    fun `a failed save sets saveErrorMessage, and the next successful save clears it`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draft = vm.uiState.value.editingEntry!!
        assertTrue("a re-edit's draft is a separate row from the committed entry", draft.id != entry.id)

        repository.saveShouldFail = true
        vm.onEntryEdited(draft.copy(notes = "first attempt"))
        advanceUntilIdle()
        assertEquals("Couldn't save your changes.", vm.uiState.value.saveErrorMessage)

        repository.saveShouldFail = false
        vm.onEntryEdited(draft.copy(notes = "second attempt"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.saveErrorMessage)
    }

    /** Reworked for the same reason as the test above — see its own doc comment. */
    @Test
    fun `onSaveErrorDismissed clears saveErrorMessage`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draft = vm.uiState.value.editingEntry!!

        repository.saveShouldFail = true
        vm.onEntryEdited(draft.copy(notes = "will fail"))
        advanceUntilIdle()
        assertEquals("Couldn't save your changes.", vm.uiState.value.saveErrorMessage)

        vm.onSaveErrorDismissed()
        assertNull(vm.uiState.value.saveErrorMessage)
    }

    /**
     * The design decision this task made and the prompt didn't cover: [saveErrorMessage] is set by
     * ten different write sites (start/start-editing/edit/save/cancel/delete/add photo/remove
     * photo/pull photo/delete gallery photo), not one — unlike
     * [com.forager.app.ui.track.TrackRecordingViewModel.startRecording]'s single-site
     * `startRecordingErrorMessage`. "The next successful save" is read broadly here: any of the
     * ten succeeding clears a message left by any of the others, not only a repeat of the same
     * one. This is the write site that isn't literally "save" proving that reading holds. Also
     * reworked (see the two tests above) to go through a real draft session rather than calling
     * `onEntryEdited` on the committed entry directly.
     */
    @Test
    fun `a successful delete clears a saveErrorMessage left by an earlier failed save`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draft = vm.uiState.value.editingEntry!!

        repository.saveShouldFail = true
        vm.onEntryEdited(draft.copy(notes = "will fail"))
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
     * this refresh exists. Reworked to open a real draft session first (Workstream L4b-R): photo
     * actions, like field edits, only make sense against a draft row, never a merely-viewed
     * committed entry — see [LogEntryDetailScreen], which only ever renders once editing has begun.
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
        vm.onStartEditingEntry()
        advanceUntilIdle()

        vm.onAddPhoto(object : PhotoSource {})
        advanceUntilIdle()

        assertEquals(listOf(newPhoto), vm.uiState.value.galleryPhotos.map { it.photo })
    }

    /** Workstream G3: pulling a gallery photo into the currently-editing draft references it only — never a new file, never a new gallery row. Reworked (Workstream L4b-R) to open a real draft session first — see the "adding a photo" test above for why. */
    @Test
    fun `pulling a gallery photo into the editing draft adds a reference only`() = runTest(dispatcher) {
        val existingPhoto = LogPhoto(id = "gallery-photo", relativePath = "photos/gallery-photo.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        repository.addPhotoToGallery(existingPhoto)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()

        vm.onPullPhoto(existingPhoto)
        advanceUntilIdle()

        // The reference lands on the draft; whether it's a reference-only write (no new file, no
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

    /** An entry left open in the background (viewed, not being edited — no tab switch closes [MushroomLogUiState.editingEntry]) must not keep showing a reference to a photo the gallery just deleted. */
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

    // --- Workstream L4b-R: standalone drafts, Save/Cancel/incidental-exit, crash recovery -------

    @Test
    fun `a freshly-started entry is its own standalone draft, invisible in entries until it is resolved`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()

        assertTrue("a brand-new entry must not appear in the committed list", vm.uiState.value.entries.isEmpty())
        assertEquals("new-entry", vm.uiState.value.editingEntry?.id)
        assertTrue(vm.uiState.value.editingEntry?.isDraft == true)
        assertNull("a brand-new entry's own draft has no parent", vm.uiState.value.editingEntry?.draftOfEntryId)

        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "something recorded"))
        advanceUntilIdle()

        assertTrue("still a draft while being edited, still invisible in the list", vm.uiState.value.entries.isEmpty())
        assertEquals("something recorded", vm.uiState.value.editingEntry?.notes)
    }

    @Test
    fun `Save commits a brand-new entry's draft in place, making it visible in the list under the same id`() = runTest(dispatcher) {
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
        assertTrue(vm.uiState.value.draftEntries.isEmpty())
        assertNull(vm.uiState.value.editingEntry)
        assertTrue("the row itself must be gone, not just filtered out", repository.getAll().getOrThrow().none { it.id == "new-entry" })
    }

    /**
     * The core correction this dispatch exists to make: re-editing a committed entry creates a
     * *separate* draft row (see [MushroomLogViewModel.onStartEditingEntry]) rather than flagging the
     * same row — proved here by asserting the committed row's content is unchanged *during* the
     * edit session, not merely restored afterward. This is gate item "a committed entry stays
     * visible in the log while it is being edited."
     */
    @Test
    fun `Cancelling a reopened existing entry deletes only its draft row, and the committed entry was never touched`() = runTest(dispatcher) {
        val original = entry.copy(notes = "original field notes")
        val repository = FakeMushroomLogRepository(initial = listOf(original))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(original.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draftId = vm.uiState.value.editingEntry!!.id
        assertTrue("a re-edit's draft is a separate row", draftId != original.id)
        assertEquals(original.id, vm.uiState.value.editingEntry?.draftOfEntryId)

        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "changed my mind about this"))
        advanceUntilIdle()
        // The committed row is untouched throughout the edit session, not just after Cancel.
        assertEquals("original field notes", vm.uiState.value.entries.single { it.id == original.id }.notes)

        vm.onCancelEditing()
        advanceUntilIdle()

        assertNull(vm.uiState.value.editingEntry)
        assertEquals("original field notes", vm.uiState.value.entries.single { it.id == original.id }.notes)
        assertTrue("the draft row itself must be gone", repository.getAll().getOrThrow().none { it.id == draftId })
    }

    /**
     * Save on a re-edit's draft copies its fields onto the parent, under the parent's own id —
     * never a second row alongside it.
     */
    @Test
    fun `Save on a reopened existing entry's draft commits its fields onto the parent, same id, draft row gone`() = runTest(dispatcher) {
        val original = entry.copy(notes = "original field notes")
        val repository = FakeMushroomLogRepository(initial = listOf(original))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(original.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draftId = vm.uiState.value.editingEntry!!.id
        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "updated after re-reading my notes"))
        advanceUntilIdle()

        vm.onSaveEntry()
        advanceUntilIdle()

        assertEquals(listOf(original.id), vm.uiState.value.entries.map { it.id })
        assertEquals("updated after re-reading my notes", vm.uiState.value.entries.single().notes)
        assertEquals(original.id, vm.uiState.value.editingEntry?.id)
        assertTrue("the draft row must be gone after commit", repository.getAll().getOrThrow().none { it.id == draftId })
    }

    /**
     * Corrected 2026-08-25 (L4b-R): the first L4b pass auto-*committed* here, which owner decision
     * 2026-08-25 explicitly forbids ("an incidental exit must never put an unsaved entry in the
     * log"). Leaving without answering now only persists the draft — already true from
     * [MushroomLogViewModel.onEntryEdited]'s own per-keystroke write — and closes the form.
     */
    @Test
    fun `leaving without answering persists a brand-new entry's draft without committing it`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()
        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "partial notes"))
        advanceUntilIdle()

        vm.onLeaveEditingIncidentally()

        assertTrue("an incidental exit must never commit an unsaved entry to the log", vm.uiState.value.entries.isEmpty())
        assertEquals(listOf("new-entry"), vm.uiState.value.draftEntries.map { it.id })
        assertTrue(vm.uiState.value.draftEntries.single().isDraft)
        assertNull("the form closes on an incidental exit", vm.uiState.value.editingEntry)
        assertTrue("the row is durably on disk, not just held in memory", repository.getAll().getOrThrow().any { it.id == "new-entry" && it.isDraft })
    }

    /**
     * Corrected 2026-08-25 (L4b-R): the first L4b pass auto-deleted an untouched draft here — an
     * unauthorized deletion the owner rejected ("Cancel is the only exit that discards"). This
     * exact scenario (tap "+", leave immediately) is now the same as any other incidental exit:
     * persisted as a draft, nothing deleted. The gate's own "tapping + and leaving immediately puts
     * nothing in the log" is satisfied because a draft never appears in the *log* (entries) at all —
     * not because it gets deleted.
     */
    @Test
    fun `leaving without answering an untouched brand-new entry persists it as a draft, never deletes it`() = runTest(dispatcher) {
        val repository = FakeMushroomLogRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onStartNewEntry(LatLng(45.0, -122.0), LocalDate.of(2026, 8, 1))
        advanceUntilIdle()

        vm.onLeaveEditingIncidentally()

        assertTrue("nothing appears in the log", vm.uiState.value.entries.isEmpty())
        assertEquals(listOf("new-entry"), vm.uiState.value.draftEntries.map { it.id })
        assertTrue("an incidental exit must never delete, touched or not", repository.getAll().getOrThrow().any { it.id == "new-entry" })
    }

    /**
     * The dispatch's own named hazard: photos attached during a re-edit's draft session land on
     * the draft's own id, and Save must repoint those cross-reference rows onto the parent
     * transactionally, or a reference silently disappears with a green suite.
     */
    @Test
    fun `a photo attached to a draft of an existing entry repoints onto the committed entry on Save`() = runTest(dispatcher) {
        val galleryPhoto = LogPhoto(id = "gallery-photo", relativePath = "photos/gallery-photo.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        repository.addPhotoToGallery(galleryPhoto)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draftId = vm.uiState.value.editingEntry!!.id

        vm.onPullPhoto(galleryPhoto)
        advanceUntilIdle()
        assertEquals(listOf(galleryPhoto), vm.uiState.value.editingEntry?.photos)

        vm.onSaveEntry()
        advanceUntilIdle()

        assertEquals(listOf(galleryPhoto), vm.uiState.value.entries.single { it.id == entry.id }.photos)
        assertTrue("the draft row itself must be gone", repository.getAll().getOrThrow().none { it.id == draftId })
        assertTrue("no orphaned cross-reference row for the draft id may remain", repository.crossRefEntryIds().none { it == draftId })
        assertTrue("the gallery photo itself must survive", repository.galleryPhotoIds.contains(galleryPhoto.id))
    }

    /** The Cancel counterpart of the repoint test above — see its own doc comment. */
    @Test
    fun `a photo attached to a draft of an existing entry is discarded on Cancel, never reaching the committed entry`() = runTest(dispatcher) {
        val galleryPhoto = LogPhoto(id = "gallery-photo", relativePath = "photos/gallery-photo.jpg", createdAtEpochMillis = 1_000L)
        val repository = FakeMushroomLogRepository(initial = listOf(entry))
        repository.addPhotoToGallery(galleryPhoto)
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(entry.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        val draftId = vm.uiState.value.editingEntry!!.id

        vm.onPullPhoto(galleryPhoto)
        advanceUntilIdle()

        vm.onCancelEditing()
        advanceUntilIdle()

        assertEquals(emptyList<LogPhoto>(), vm.uiState.value.entries.single { it.id == entry.id }.photos)
        assertTrue("the draft row and its own cross-reference must be gone", repository.getAll().getOrThrow().none { it.id == draftId })
        assertTrue("no orphaned cross-reference row for the draft id may remain", repository.crossRefEntryIds().none { it == draftId })
        assertTrue("the gallery photo itself must survive Cancel", repository.galleryPhotoIds.contains(galleryPhoto.id))
    }

    /**
     * The known hazard this dispatch calls out by name: G3's [MushroomLogViewModel.loadEntries]
     * used to re-derive [MushroomLogUiState.editingEntry] wholesale from a fresh repository read —
     * safe only because nothing uncommitted existed to lose at the time. A refresh firing mid-edit
     * (e.g. [MushroomLogViewModel.onDeleteGalleryPhoto]'s own success-path refresh) must not
     * silently discard in-progress typing. **Mutation-checked**: reverting `loadEntries`'s merge
     * back to a wholesale `editingEntry = editing` replacement (G3's original shape) was confirmed
     * by hand to turn this test red before restoring the fix — see the L4b-R report for the exact
     * before/after run.
     */
    @Test
    fun `a refresh triggered mid-edit preserves uncommitted edits`() = runTest(dispatcher) {
        val original = entry.copy(notes = "original")
        val repository = FakeMushroomLogRepository(initial = listOf(original))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOpenEntry(original.id)
        vm.onStartEditingEntry()
        advanceUntilIdle()
        vm.onEntryEdited(vm.uiState.value.editingEntry!!.copy(notes = "typing away, not saved yet"))
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
    fun `an orphaned draft from a crashed new entry is surfaced separately from committed entries and can be reinstated`() = runTest(dispatcher) {
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

    /**
     * Gate item: "after a process kill mid-edit of a committed entry, both the saved version and
     * the draft are present and the user can choose." Simulates the crash by seeding the fake
     * repository directly with both rows — a committed entry and a separate draft row pointing at
     * it — the same shape [MushroomLogViewModel.onStartEditingEntry] would have left behind mid-edit.
     */
    @Test
    fun `after a crash mid-edit of a committed entry, both the committed version and its orphaned draft survive and are choosable`() = runTest(dispatcher) {
        val committed = entry.copy(notes = "last saved before the crash")
        val orphanedDraft = committed.copy(id = "draft-of-entry-1", notes = "typed right before the crash", draftOfEntryId = committed.id, isDraft = true)
        val repository = FakeMushroomLogRepository(initial = listOf(committed, orphanedDraft))
        val vm = viewModel(repository)

        advanceUntilIdle()

        assertEquals("last saved before the crash", vm.uiState.value.entries.single { it.id == committed.id }.notes)
        assertEquals(listOf(orphanedDraft.id), vm.uiState.value.draftEntries.map { it.id })
        assertEquals("typed right before the crash", vm.uiState.value.draftEntries.single().notes)
        assertFalse(vm.uiState.value.entries.single().isDraft)

        vm.onOpenEntry(orphanedDraft.id)
        assertEquals("typed right before the crash", vm.uiState.value.editingEntry?.notes)

        vm.onCloseEntry()
        vm.onOpenEntry(committed.id)
        assertEquals("last saved before the crash", vm.uiState.value.editingEntry?.notes)
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

    /** Every distinct entry id currently holding at least one cross-reference row — used to assert no orphaned reference survives a draft's removal. */
    fun crossRefEntryIds(): Set<String> = crossRefs.map { it.first }.toSet()

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

    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> {
        entries[committed.id] = committed
        if (committed.id != draftId) {
            crossRefs.filter { it.first == draftId }.forEach { (_, photoId) -> crossRefs += committed.id to photoId }
            crossRefs.removeAll { it.first == draftId }
            entries.remove(draftId)
        }
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
