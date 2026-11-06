package com.zynergylabs.forager.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.log.CartographyViewModel
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

/**
 * Shared by the live-count tests (dispatch 2026-10-28-08, RECORD -1780 and -1781): the real [AppContainer] over the
 * real database, as [MushroomLogViewModelWiringTest] uses it, and the real-time wait that suite settled on. The
 * counts come from Room's own change tracking on its own threads, so a test waits on the main looper for them
 * rather than advancing a test dispatcher.
 */
internal object LiveCounts {

    val app: ForagerApplication get() = ApplicationProvider.getApplicationContext()
    val container: AppContainer get() = app.container

    /** Waits up to 10 s for [condition], idling the main looper between looks; throws naming [what] if it never holds. */
    fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    /**
     * [awaitUntil] without the throw, for each test's last wait: a count that never changes then fails on the test's own
     * assertion and message, which names the stale count, rather than on a timeout.
     */
    fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
    }

    /**
     * The journal's ViewModel as `createCartographyViewModel` builds it, from the container's own use cases, except the
     * trip report's offline regions: the container's is the map library's offline store, which these tests do not
     * stand up. Nothing here reads them.
     */
    fun cartographyViewModel(): CartographyViewModel {
        val c = container
        return CartographyViewModel(
            c.getCartographyEntriesUseCase,
            c.getCartographyDraftEntriesUseCase,
            c.createCartographyEntryUseCase,
            c.saveCartographyEntryUseCase,
            c.getCartographyEntryUseCase,
            c.commitCartographyEntryUseCase,
            c.deleteCartographyEntryUseCase,
            c.getDerivedTripUseCase,
            GetTripReportOfflineRegionsUseCase(NoOfflineStore),
            c.computeTrackStatisticsUseCase,
            c.setCartographyEntryShownOnMapUseCase,
        )
    }

    /** The Maps tab's ViewModel holding [regions], its journal-entry counts wired as `createAvailabilityViewModel` wires them. */
    fun availabilityViewModel(regions: List<OfflineRegionSummary>): AvailabilityViewModel =
        mapLayersViewModel(offlineRegions = regions, entryReferenceCounts = container.entryReferenceCountSource)

    fun save(entry: CartographyEntry) = runBlocking { container.cartographyEntryRepository.save(entry).getOrThrow() }

    fun stored(id: String): CartographyEntry? = runBlocking { container.cartographyEntryRepository.getById(id).getOrThrow() }

    /** Deletes [id] through the journal's real delete, the report's Delete dialog's own call, and waits for it to land. */
    fun deleteThroughJournal(journal: CartographyViewModel, id: String) {
        journal.onDeleteEntry(id)
        awaitUntil("entry '$id' is deleted") { stored(id) == null }
    }

    /**
     * Opens draft [id], lets [decide] change it (each change autosaves the draft), waits until [saved] holds of the
     * stored draft, then finishes it with the editor's Finish entry and waits for the commit to land.
     */
    fun finishThroughJournal(journal: CartographyViewModel, id: String, decide: (CartographyViewModel) -> Unit, saved: (CartographyEntry) -> Boolean) {
        awaitUntil("the journal has loaded draft '$id'") { journal.uiState.value.draftEntries.any { it.id == id } }
        journal.onOpenEntry(id)
        awaitUntil("draft '$id' is open") { journal.uiState.value.editingEntry?.id == id }
        decide(journal)
        awaitUntil("draft '$id' has autosaved the change") { stored(id)?.let(saved) == true }
        journal.onFinishEntry()
        awaitUntil("entry '$id' is committed") { stored(id)?.isDraft == false }
    }

    val DAY: LocalDate = LocalDate.of(2026, 9, 20)

    fun committed(id: String): CartographyEntry = CartographyEntry.draft(id = id, date = DAY, updatedAtEpochMillis = 1_000L).copy(isDraft = false)

    fun draft(id: String): CartographyEntry = CartographyEntry.draft(id = id, date = DAY, updatedAtEpochMillis = 1_000L)

    val silentLog = ErrorLog { _, _, _ -> }

    val REGION = OfflineRegionSummary(
        id = 7L, name = "Ridge", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0,
        tileCount = 10, sizeBytes = 1000L, createdAtEpochMillis = 1_758_300_000_000L,
    )
}

private object NoOfflineStore : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by the live-count tests"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline deletes are not exercised by the live-count tests"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}
