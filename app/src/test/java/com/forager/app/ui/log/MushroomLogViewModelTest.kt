package com.forager.app.ui.log

import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.MushroomLogRepository
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
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
        removePhoto = RemovePhotoFromLogEntryUseCase(photoStore, repository),
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
}

private class FakeMushroomLogRepository(
    initial: List<MushroomLogEntry> = emptyList(),
    var saveShouldFail: Boolean = false,
) : MushroomLogRepository {
    private val entries = initial.associateByTo(LinkedHashMap()) { it.id }

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(entries.values.toList())

    override suspend fun save(entry: MushroomLogEntry): Result<Unit> {
        if (saveShouldFail) return Result.failure(RuntimeException("save failed"))
        entries[entry.id] = entry
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        entries.remove(id)
        return Result.success(Unit)
    }
}

/** Not exercised by these tests' assertions — none of them add or remove a photo. */
private class FakePhotoStore : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> =
        Result.failure(UnsupportedOperationException("photo persistence not exercised by this test"))

    override suspend fun delete(photo: LogPhoto): Result<Unit> =
        Result.failure(UnsupportedOperationException("photo deletion not exercised by this test"))
}
