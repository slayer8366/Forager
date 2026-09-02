package com.forager.app.ui.log

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.data.repository.RoomCartographyEntryRepository
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.CommitCartographyEntryUseCase
import com.forager.app.domain.ComputeTrackStatisticsUseCase
import com.forager.app.domain.CreateCartographyEntryUseCase
import com.forager.app.domain.DeleteCartographyEntryUseCase
import com.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.forager.app.domain.GetCartographyEntriesUseCase
import com.forager.app.domain.GetDerivedTripUseCase
import com.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.SaveCartographyEntryUseCase
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [CartographyViewModel] over a real, in-memory Room database — same reasoning
 * [GetDerivedTripUseCaseTest] gives for using the real repositories rather than hand-built fakes:
 * the withhold/keep curation logic reads real day-scoped queries, and a fake could silently
 * re-implement the same mistake those queries might have.
 *
 * Covers: every trip-report candidate starts kept (owner decision — "withholding is a first-class
 * operation, not a filter"); [CartographyViewModel.onSetTrackDecision] withholds then restores;
 * finishing and deleting an entry never gates on [com.forager.app.domain.model.CartographyEntry.text]
 * or any kept-item count (`amendment-2b-optional-writing.md`); reopening an entry (Stage 2b follow-up
 * dispatch, point 2) reloads a fresh trip report, preserves existing decisions unchanged, and offers a
 * newly-appeared candidate as undecided rather than silently including or excluding it — both
 * directions (re-keep a withheld item, withhold a kept one) available on that same reopen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var database: ForagerDatabase
    private lateinit var viewModel: CartographyViewModel

    /**
     * Draft-lifecycle dispatch's own required fixture fix. A literal constant here
     * (`idGenerator = { "entry-1" }`) is what let the real defect — [CartographyViewModel.onStartEntry]
     * minting an unrelated new id on every call, with nothing to dedupe or resume — ship through 837
     * green tests: even a test calling `onStartEntry` twice would have had both calls collapse onto
     * the same row via Room's `REPLACE`, silently exercising a scenario the production `idGenerator`
     * (a real `UUID.randomUUID()`) can never produce. Distinct per call is all that's needed here — see
     * [CreateCartographyEntryUseCase]'s own doc comment for why a test fixes `idGenerator` at all.
     */
    private var nextEntryId = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // A direct (synchronous, same-thread) executor for both query and transaction work — Room's
        // real executors run on genuine background threads, which advanceUntilIdle()'s *virtual*
        // scheduler cannot wait for once a viewModelScope.launch body crosses that boundary (unlike
        // GetDerivedTripUseCaseTest, which awaits the same real Room work directly in the test's own
        // suspend body, with no separate launched coroutine or virtual-time scheduler in the way).
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        )
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        val cartographyEntryRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
        val mushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao())
        val trackRepository = RoomTrackRepository(database.trackDao())
        val waypointRepository = RoomWaypointRepository(database.waypointDao())

        viewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(cartographyEntryRepository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(cartographyEntryRepository),
            createEntry = CreateCartographyEntryUseCase(cartographyEntryRepository, now = { FIXED_NOW }, idGenerator = { "entry-${nextEntryId++}" }),
            saveEntry = SaveCartographyEntryUseCase(cartographyEntryRepository),
            commitEntry = CommitCartographyEntryUseCase(cartographyEntryRepository, now = { FIXED_NOW }),
            deleteEntry = DeleteCartographyEntryUseCase(cartographyEntryRepository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = mushroomLogRepository,
                trackRepository = trackRepository,
                waypointRepository = waypointRepository,
                offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(StubOfflineMapRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            now = { FIXED_NOW },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `starting a new entry keeps every one of that day's candidates by default`() = runTest(dispatcher) {
        val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.5, -122.6), date = DAY).copy(isDraft = false)
        RoomMushroomLogRepository(database.mushroomLogDao()).save(find).getOrThrow()

        val waypoint = Waypoint(id = "waypoint-1", lat = 45.5, lng = -122.6, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = dayStartMillis())
        RoomWaypointRepository(database.waypointDao()).save(waypoint).getOrThrow()

        val trackRepository = RoomTrackRepository(database.trackDao())
        val track = Track(id = "track-1", name = "Ridge Loop", startedAtEpochMillis = dayStartMillis(), endedAtEpochMillis = dayStartMillis() + 60_000L, points = emptyList())
        trackRepository.create(track).getOrThrow()
        trackRepository.appendPoints(
            trackId = track.id,
            points = listOf(TrackPoint(lat = 45.5, lng = -122.6, altitude = null, accuracyMeters = null, timestampEpochMillis = dayStartMillis())),
        ).getOrThrow()
        trackRepository.end(track.id, dayStartMillis() + 60_000L).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()

        val entry = viewModel.uiState.value.editingEntry
        assertEquals(listOf("find-1"), entry?.findDecisions?.map { it.findId })
        assertEquals(listOf("waypoint-1"), entry?.waypointDecisions?.map { it.waypointId })
        assertEquals(listOf("track-1"), entry?.trackDecisions?.map { it.trackId })
    }

    @Test
    fun `withholding a kept track keeps its decision row but flips it, and deciding again restores it`() = runTest(dispatcher) {
        val trackRepository = RoomTrackRepository(database.trackDao())
        val track = Track(id = "track-1", name = "Ridge Loop", startedAtEpochMillis = dayStartMillis(), endedAtEpochMillis = dayStartMillis() + 60_000L, points = emptyList())
        trackRepository.create(track).getOrThrow()
        trackRepository.end(track.id, dayStartMillis() + 60_000L).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.editingEntry?.trackDecisions?.first { it.trackId == "track-1" }?.kept)

        viewModel.onSetTrackDecision("track-1", kept = false)
        advanceUntilIdle()
        assertEquals(
            "withholding is a deliberate, persisted decision, not a removal back to undecided",
            false,
            viewModel.uiState.value.editingEntry?.trackDecisions?.first { it.trackId == "track-1" }?.kept,
        )

        viewModel.onSetTrackDecision("track-1", kept = true)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.editingEntry?.trackDecisions?.first { it.trackId == "track-1" }?.kept)
    }

    @Test
    fun `reopening an entry preserves its decisions and offers a newly-appeared candidate as undecided`() = runTest(dispatcher) {
        val trackRepository = RoomTrackRepository(database.trackDao())
        val track1 = Track(id = "track-1", name = "Ridge Loop", startedAtEpochMillis = dayStartMillis(), endedAtEpochMillis = dayStartMillis() + 60_000L, points = emptyList())
        trackRepository.create(track1).getOrThrow()
        trackRepository.end(track1.id, dayStartMillis() + 60_000L).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val entryId = viewModel.uiState.value.editingEntry!!.id

        viewModel.onSetTrackDecision("track-1", kept = false)
        advanceUntilIdle()

        viewModel.onCloseEntry()

        // A second track for the same day, created only after the entry already existed.
        val track2 = Track(id = "track-2", name = "New Spur", startedAtEpochMillis = dayStartMillis() + 120_000L, endedAtEpochMillis = dayStartMillis() + 180_000L, points = emptyList())
        trackRepository.create(track2).getOrThrow()
        trackRepository.end(track2.id, dayStartMillis() + 180_000L).getOrThrow()

        // onOpenEntry looks the entry up in uiState's own entries/draftEntries lists, so refresh
        // those from the database first — the same thing a real navigate-away-and-back would do.
        viewModel.loadEntries()
        advanceUntilIdle()

        viewModel.onOpenEntry(entryId)
        advanceUntilIdle()

        val reopened = viewModel.uiState.value.editingEntry!!
        assertEquals(
            "a withheld decision must survive reopening unchanged, never silently reverted",
            false,
            reopened.trackDecisions.first { it.trackId == "track-1" }.kept,
        )
        assertTrue(
            "track-2 has no decision on this entry yet, so it must not be silently included",
            reopened.trackDecisions.none { it.trackId == "track-2" },
        )
        assertTrue(
            "track-2 must still be offered, as a live candidate, to decide on",
            viewModel.uiState.value.candidatesForEditingEntry?.tracks.orEmpty().any { it.id == "track-2" },
        )

        // Both directions available on the same reopen: re-keep what was withheld, withhold what's new.
        viewModel.onSetTrackDecision("track-1", kept = true)
        viewModel.onSetTrackDecision("track-2", kept = false)
        advanceUntilIdle()

        val redecided = viewModel.uiState.value.editingEntry!!
        assertEquals(true, redecided.trackDecisions.first { it.trackId == "track-1" }.kept)
        assertEquals(false, redecided.trackDecisions.first { it.trackId == "track-2" }.kept)
    }

    /** `amendment-2b-optional-writing.md`: selection alone is a complete act of authorship — finishing a wordless entry must succeed, not be blocked or read as incomplete. */
    @Test
    fun `finishing a wordless entry with kept items succeeds and commits it`() = runTest(dispatcher) {
        val waypoint = Waypoint(id = "waypoint-1", lat = 45.5, lng = -122.6, altitude = null, name = "Trailhead", note = "", createdAtEpochMillis = dayStartMillis())
        RoomWaypointRepository(database.waypointDao()).save(waypoint).getOrThrow()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val draft = viewModel.uiState.value.editingEntry!!
        assertEquals("", draft.text)
        assertTrue(draft.isDraft)

        viewModel.onFinishEntry()
        advanceUntilIdle()

        val committed = viewModel.uiState.value.editingEntry!!
        assertFalse(committed.isDraft)
        assertEquals("", committed.text)
        assertEquals(listOf("waypoint-1"), committed.waypointDecisions.map { it.waypointId })
        assertEquals(listOf(committed), viewModel.uiState.value.entries)
        assertTrue(viewModel.uiState.value.draftEntries.isEmpty())
    }

    // --- Draft-lifecycle dispatch regression tests -------------------------------------------------
    // The bug: onStartEntry mints an unrelated new id on every call (the production idGenerator is a
    // real UUID.randomUUID(), never reused), and onCloseEntry used to just null out editingEntry with
    // no trace of the draft it had just saved to disk — so backing out made the draft invisible, the
    // only visible affordance was "+" again, and repeated back-out-then-restart minted an
    // ever-growing pile of orphaned drafts that all surfaced at once the next time loadEntries()
    // happened to run (e.g. after a later commit) — reading as "commit duplicated the entry."

    @Test
    fun `closing an unfinished entry surfaces it as a resumable draft, and reopening then closing again does not duplicate it`() = runTest(dispatcher) {
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val draftId = viewModel.uiState.value.editingEntry!!.id

        viewModel.onCloseEntry()

        assertEquals(
            "the abandoned draft must be visible in the Drafts list immediately, not only after some later loadEntries() call",
            listOf(draftId),
            viewModel.uiState.value.draftEntries.map { it.id },
        )
        assertEquals("closing must not leave a stale editingEntry", null, viewModel.uiState.value.editingEntry)

        // Resume it — the same row, via the same list the user would tap in the real UI.
        viewModel.onOpenEntry(draftId)
        advanceUntilIdle()
        assertEquals(draftId, viewModel.uiState.value.editingEntry?.id)

        viewModel.onCloseEntry()

        assertEquals(
            "resuming the same draft and closing it again must not duplicate it in the Drafts list",
            listOf(draftId),
            viewModel.uiState.value.draftEntries.map { it.id },
        )
    }

    /**
     * Amendment decision 2: no by-date uniqueness constraint — a morning outing and an evening outing
     * are a real case. The fix for symptom 1 is making the first draft *visible*, not *forbidding* a
     * second one; a user who taps "+" again while it's plainly visible is making an informed choice.
     */
    @Test
    fun `starting a second entry for the same date is allowed and produces two independent visible drafts`() = runTest(dispatcher) {
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val firstId = viewModel.uiState.value.editingEntry!!.id
        viewModel.onCloseEntry()

        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val secondId = viewModel.uiState.value.editingEntry!!.id
        viewModel.onCloseEntry()

        assertTrue("the second entry must be a genuinely different row", firstId != secondId)
        assertEquals(
            setOf(firstId, secondId),
            viewModel.uiState.value.draftEntries.map { it.id }.toSet(),
        )
    }

    @Test
    fun `committing a draft flips the same row with no orphan left behind`() = runTest(dispatcher) {
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val draftId = viewModel.uiState.value.editingEntry!!.id

        viewModel.onFinishEntry()
        advanceUntilIdle()

        assertEquals(listOf(draftId), viewModel.uiState.value.entries.map { it.id })
        assertTrue(
            "no orphan draft should remain once the entry it came from is committed",
            viewModel.uiState.value.draftEntries.isEmpty(),
        )
    }

    /**
     * Symptom 3, re-examined per the amendment: it was inferred, not confirmed, as a consequence of
     * symptom 1 (a second entry for the same day independently re-deriving the same day's records as
     * kept, reading as "auto-fill"). With symptoms 1/2 fixed, a genuinely new entry's own *authored*
     * state — [CartographyEntry.text]/[CartographyEntry.tags], never carried over by anything in this
     * ViewModel — starts empty regardless. `editingEntry` deliberately still points at the just-
     * committed entry until [onCloseEntry] — see that function's own doc comment and the amendment's
     * "stay on the entry you just finished may be intended" note — so this starts a new entry only
     * after closing, the same as a real back-out-then-add would.
     */
    @Test
    fun `starting a new entry after commit begins empty, not carrying over the committed entry's text or tags`() = runTest(dispatcher) {
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        viewModel.onTextChanged("Great chanterelle patch off the ridge trail.")
        viewModel.onTagsChanged(listOf("chanterelle", "ridge trail"))
        advanceUntilIdle()

        viewModel.onFinishEntry()
        advanceUntilIdle()
        assertEquals("Great chanterelle patch off the ridge trail.", viewModel.uiState.value.editingEntry?.text)

        viewModel.onCloseEntry()
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()

        val nextDraft = viewModel.uiState.value.editingEntry!!
        assertEquals("", nextDraft.text)
        assertEquals(emptyList<String>(), nextDraft.tags)
    }

    @Test
    fun `deleting an entry removes it from both entries and drafts`() = runTest(dispatcher) {
        viewModel.onStartEntry(DAY)
        advanceUntilIdle()
        val draftId = viewModel.uiState.value.editingEntry!!.id

        viewModel.onDeleteEntry(draftId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.none { it.id == draftId })
        assertTrue(viewModel.uiState.value.draftEntries.none { it.id == draftId })
    }

    private fun dayStartMillis(): Long = DAY.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 8, 1)
        const val FIXED_NOW = 10_000L
    }
}

/** Reports no regions on disk — this test file never exercises offline-region trip-report coverage, only tracks/waypoints/finds. */
private object StubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> =
        error("not used by this test")

    override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used by this test")

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}
