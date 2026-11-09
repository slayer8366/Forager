package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.AddPhotoToGalleryUseCase
import com.zynergylabs.forager.app.domain.AddPhotoToLogEntryUseCase
import com.zynergylabs.forager.app.domain.CommitDraftEntryUseCase
import com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.GetDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.MushroomLogRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PhotoStore
import com.zynergylabs.forager.app.domain.PullPhotoIntoEntryUseCase
import com.zynergylabs.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.zynergylabs.forager.app.domain.SaveMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.StartEditingLogEntryUseCase
import com.zynergylabs.forager.app.domain.UpdatePhotoLocationUseCase
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-312, item 9: from the moment the user deletes a find, a photo or a region until Undo
 * brings it back, the Maps tab never draws it. Not while Undo shows, not in the window between the Undo
 * snackbar ending and the database delete finishing, and not after a map-records load that read the row
 * before the delete finishes after it. A failed delete draws it again; so does Undo.
 *
 * Through the real [AvailabilityScreen] (`MapLayersTestScreen`), the real [AvailabilityViewModel] and the
 * real [MushroomLogViewModel], with every delete held open on a gate the test releases, so the window can
 * be looked at. What the map is drawn is what the screen hands it: [BubbleMapSlot.content]'s own marker
 * lists, read after the screen has settled. Nothing here copies the screen's filter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapRecordsDeletedNotDrawnTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val store = HeldStore()
    private val regions = HeldRegions(listOf(1L, 2L))

    /** Set by a test to make the next map-records read stop after it has read, as a slow read does. */
    private var heldLoad: CompletableDeferred<Unit>? = null

    private lateinit var availability: AvailabilityViewModel
    private lateinit var log: MushroomLogViewModel

    private fun show() {
        availability = mapLayersViewModel(
            getMapRecords = {
                val read = MapRecords.NONE.copy(
                    findMarkers = store.findMarkers(),
                    photoMarkers = store.photoMarkers(),
                    offlineRegionCircles = regions.circles(),
                )
                heldLoad?.await()
                read
            },
            offlineRegions = regions.summaries(),
            offlineMapRepository = regions,
        )
        log = MushroomLogViewModel(
            getEntries = GetMushroomLogEntriesUseCase(store),
            getDraftEntries = GetDraftEntriesUseCase(store),
            createEntry = CreateMushroomLogEntryUseCase(store, today = { LocalDate.of(2026, 9, 30) }, idGenerator = { "find-new" }),
            startEditingEntry = StartEditingLogEntryUseCase(store, idGenerator = { "draft-of-find" }),
            saveEntry = SaveMushroomLogEntryUseCase(store),
            commitDraftEntry = CommitDraftEntryUseCase(store),
            deleteEntry = DeleteMushroomLogEntryUseCase(store),
            addPhoto = AddPhotoToLogEntryUseCase(HeldPhotoStore, store),
            addPhotoToGallery = AddPhotoToGalleryUseCase(HeldPhotoStore, store),
            removePhoto = RemovePhotoFromLogEntryUseCase(store),
            getGalleryPhotos = GetGalleryPhotosUseCase(store),
            pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(store),
            deleteGalleryPhoto = DeleteGalleryPhotoUseCase(store, HeldPhotoStore),
            locationProvider = HeldNoLocation,
            updatePhotoLocation = UpdatePhotoLocationUseCase(store),
            // As MainActivity wires them (MushroomLogViewModelFactory.kt).
            onFindDeleted = { id -> availability.onFindDeleted(id) },
            onPhotoDeleted = { id -> availability.onPhotoDeleted(id) },
        )
        composeRule.setContent {
            val logState by log.uiState.collectAsState()
            MapLayersTestScreen(viewModel = availability, mapSlot = map.slot, store = AbsentForecastCellStore, logUiState = logState)
        }
        composeRule.waitForIdle()
        availability.onMapShown()
        composeRule.waitForIdle()
    }

    private fun finds(): List<String> = map.content!!.findMarkers.map { it.recordId }
    private fun photos(): List<String> = map.content!!.photoMarkers.map { it.recordId }
    private fun circles(): List<String> = map.content!!.offlineRegionCircles.map { it.recordId }

    private fun settle() = composeRule.waitForIdle()

    // ---- finds ----

    @Test
    fun `a find is not drawn in the window between its Undo ending and its delete finishing`() {
        show()
        assertEquals("positive control: both finds are drawn", listOf("find-a", "find-b"), finds())
        log.requestDeleteEntry("find-a")
        settle()
        assertEquals("the Undo window hides it", listOf("find-b"), finds())
        val gate = CompletableDeferred<Unit>().also { store.deleteGate = it }

        log.commitDeleteEntry("find-a")
        settle()

        assertEquals("positive control: the delete has started and is held, so the row is still in the store", listOf("find-a"), store.deleteStarted)
        assertEquals("find-a is drawn in the commit window", listOf("find-b"), finds())
        gate.complete(Unit)
        settle()
        assertEquals("the delete finished", listOf("find-b"), store.findMarkers().map { it.recordId })
        assertEquals("and it is still not drawn", listOf("find-b"), finds())
    }

    @Test
    fun `a find is not drawn when a load that read it before the delete finishes after the delete`() {
        show()
        log.requestDeleteEntry("find-a")
        settle()
        val load = CompletableDeferred<Unit>().also { heldLoad = it }
        availability.onMapShown()
        settle()
        heldLoad = null
        log.commitDeleteEntry("find-a")
        settle()
        assertEquals("positive control: the delete finished while the load was still held", listOf("find-b"), store.findMarkers().map { it.recordId })

        load.complete(Unit)
        settle()

        assertEquals("positive control: the late load did put its (stale) snapshot in", listOf("find-a", "find-b"), availability.uiState.value.mapRecords.findMarkers.map { it.recordId })
        assertEquals("find-a came back when the late load finished", listOf("find-b"), finds())
    }

    @Test
    fun `a find whose delete fails is drawn again`() {
        show()
        store.deleteShouldFail = true
        log.requestDeleteEntry("find-a")
        settle()
        log.commitDeleteEntry("find-a")
        settle()

        assertEquals("positive control: the delete was tried", listOf("find-a"), store.deleteStarted)
        assertEquals("the find is still saved, so it is drawn", listOf("find-a", "find-b"), finds())
    }

    @Test
    fun `Undo draws a find again`() {
        show()
        log.requestDeleteEntry("find-a")
        settle()
        assertEquals("positive control: hidden while pending", listOf("find-b"), finds())

        log.undoDeleteEntry("find-a")
        settle()

        assertEquals(listOf("find-a", "find-b"), finds())
    }

    // ---- photos ----

    @Test
    fun `a photo is not drawn in the window between its Undo ending and its delete finishing`() {
        show()
        assertEquals("positive control", listOf("photo-a", "photo-b"), photos())
        log.requestDeleteGalleryPhoto("photo-a")
        settle()
        assertEquals("the Undo window hides it", listOf("photo-b"), photos())
        val gate = CompletableDeferred<Unit>().also { store.deleteGate = it }

        log.commitDeleteGalleryPhoto("photo-a")
        settle()

        assertEquals("positive control: the delete has started and is held", listOf("photo-a"), store.deleteStarted)
        assertEquals("photo-a is drawn in the commit window", listOf("photo-b"), photos())
        gate.complete(Unit)
        settle()
        assertEquals("and it is still not drawn once the delete finished", listOf("photo-b"), photos())
    }

    @Test
    fun `a photo is not drawn when a load that read it before the delete finishes after the delete`() {
        show()
        log.requestDeleteGalleryPhoto("photo-a")
        settle()
        val load = CompletableDeferred<Unit>().also { heldLoad = it }
        availability.onMapShown()
        settle()
        heldLoad = null
        log.commitDeleteGalleryPhoto("photo-a")
        settle()
        assertEquals("positive control: the delete finished while the load was held", listOf("photo-b"), store.photoMarkers().map { it.recordId })

        load.complete(Unit)
        settle()

        assertEquals("positive control: the late load put its snapshot in", listOf("photo-a", "photo-b"), availability.uiState.value.mapRecords.photoMarkers.map { it.recordId })
        assertEquals("photo-a came back when the late load finished", listOf("photo-b"), photos())
    }

    /**
     * The album's other delete, at once after its dialog with no Undo window (`onDeleteGalleryPhoto`): the same late
     * load. There is no pending marker on this path, so only the committed id keeps the stale snapshot off the map.
     */
    @Test
    fun `a photo deleted at once is not drawn when a load that read it before the delete finishes after the delete`() {
        show()
        val photo = log.uiState.value.galleryPhotos.single { it.photo.id == "photo-a" }
        val load = CompletableDeferred<Unit>().also { heldLoad = it }
        availability.onMapShown()
        settle()
        heldLoad = null
        log.onDeleteGalleryPhoto(photo)
        settle()
        assertEquals("positive control: the delete finished while the load was held", listOf("photo-b"), store.photoMarkers().map { it.recordId })

        load.complete(Unit)
        settle()

        assertEquals("positive control: the late load put its snapshot in", listOf("photo-a", "photo-b"), availability.uiState.value.mapRecords.photoMarkers.map { it.recordId })
        assertEquals("photo-a, deleted at once, came back when the late load finished", listOf("photo-b"), photos())
    }

    @Test
    fun `a photo whose delete fails is drawn again`() {
        show()
        store.deleteShouldFail = true
        log.requestDeleteGalleryPhoto("photo-a")
        settle()
        log.commitDeleteGalleryPhoto("photo-a")
        settle()

        assertEquals("positive control: the delete was tried", listOf("photo-a"), store.deleteStarted)
        assertEquals(listOf("photo-a", "photo-b"), photos())
    }

    @Test
    fun `Undo draws a photo again`() {
        show()
        log.requestDeleteGalleryPhoto("photo-a")
        settle()
        assertEquals("positive control: hidden while pending", listOf("photo-b"), photos())

        log.undoDeleteGalleryPhoto("photo-a")
        settle()

        assertEquals("Undo drew the photo again", listOf("photo-a", "photo-b"), photos())
    }

    // ---- regions ----

    @Test
    fun `a region is not drawn in the window between its Undo ending and its delete finishing`() {
        show()
        assertEquals("positive control", listOf("1", "2"), circles())
        availability.requestDeleteOfflineRegion(1L)
        settle()
        assertEquals("the Undo window hides it", listOf("2"), circles())
        val gate = CompletableDeferred<Unit>().also { regions.deleteGate = it }

        availability.commitDeleteOfflineRegion(1L)
        settle()

        assertEquals("positive control: the delete has started and is held", listOf(1L), regions.deleteStarted)
        assertEquals("region 1 is drawn in the commit window", listOf("2"), circles())
        gate.complete(Unit)
        settle()
        assertEquals("and it is still not drawn once the delete finished", listOf("2"), circles())
    }

    @Test
    fun `a region is not drawn when a load that read it before the delete finishes after the delete`() {
        show()
        availability.requestDeleteOfflineRegion(1L)
        settle()
        val load = CompletableDeferred<Unit>().also { heldLoad = it }
        availability.onMapShown()
        settle()
        heldLoad = null
        availability.commitDeleteOfflineRegion(1L)
        settle()
        assertEquals("positive control: the delete finished while the load was held", listOf("2"), regions.circles().map { it.recordId })

        load.complete(Unit)
        settle()

        assertEquals("positive control: the late load put its snapshot in", listOf("1", "2"), availability.uiState.value.mapRecords.offlineRegionCircles.map { it.recordId })
        assertEquals("region 1 came back when the late load finished", listOf("2"), circles())
    }

    @Test
    fun `a region whose delete fails is drawn again`() {
        show()
        regions.deleteShouldFail = true
        availability.requestDeleteOfflineRegion(1L)
        settle()
        availability.commitDeleteOfflineRegion(1L)
        settle()

        assertEquals("positive control: the delete was tried", listOf(1L), regions.deleteStarted)
        assertTrue("an error was reported", availability.uiState.value.offlineRegionsErrorMessage != null)
        assertEquals(listOf("1", "2"), circles())
    }

    @Test
    fun `Undo draws a region again`() {
        show()
        availability.requestDeleteOfflineRegion(1L)
        settle()
        assertEquals("positive control: hidden while pending", listOf("2"), circles())

        availability.undoDeleteOfflineRegion(1L)
        settle()

        assertEquals("Undo drew the region again", listOf("1", "2"), circles())
    }
}

/** Finds and album photos kept in memory. A delete records that it started, waits on [deleteGate] if one is set, then removes or fails. */
private class HeldStore : MushroomLogRepository {
    private val finds = linkedMapOf(
        "find-a" to heldFind("find-a", 45.30),
        "find-b" to heldFind("find-b", 45.31),
    )
    private val photos = linkedMapOf("photo-a" to heldPhoto("photo-a"), "photo-b" to heldPhoto("photo-b"))
    val deleteStarted = mutableListOf<String>()
    var deleteGate: CompletableDeferred<Unit>? = null
    var deleteShouldFail = false

    fun findMarkers(): List<RecordPoint> = finds.values.mapNotNull { e -> e.foundAt?.let { RecordPoint(e.id, it) } }
    fun photoMarkers(): List<RecordPoint> = photos.values.map { RecordPoint(it.photo.id, LatLng(it.photo.latitude!!, it.photo.longitude!!)) }

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(finds.values.toList())
    override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> = Result.success(emptyList())
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(photos.values.toList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> = unsupported()
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> = unsupported()
    override suspend fun delete(id: String): Result<Unit> {
        deleteStarted += id
        deleteGate?.await()
        if (deleteShouldFail) return Result.failure(RuntimeException("delete failed"))
        finds.remove(id)
        return Result.success(Unit)
    }
    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> = unsupported()
    override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> = unsupported()
    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> = unsupported()
    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> = unsupported()
    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> {
        deleteStarted += photoId
        deleteGate?.await()
        if (deleteShouldFail) return Result.failure(RuntimeException("delete failed"))
        photos.remove(photoId)
        return Result.success(Unit)
    }

    private fun unsupported(): Result<Unit> = Result.failure(UnsupportedOperationException("not part of this test's path"))
}

private fun heldFind(id: String, lat: Double) =
    MushroomLogEntry.draft(id = id, location = LatLng(lat, -122.6), date = LocalDate.of(2026, 9, 20)).copy(isDraft = false)

private fun heldPhoto(id: String) = GalleryPhoto(
    photo = LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1L, latitude = 45.3, longitude = -122.6),
    referencingEntryIds = emptyList(),
)

/** Offline regions kept in memory, with the same held delete. */
private class HeldRegions(ids: List<Long>) : OfflineMapRepository {
    private val regions = ids.map {
        OfflineRegionSummary(
            id = it, name = "region $it", region = Region(45.3, -122.6, 10), minZoom = 8.0, maxZoom = 12.0,
            tileCount = 1, sizeBytes = 1L, createdAtEpochMillis = 1L,
        )
    }.associateByTo(LinkedHashMap()) { it.id }
    val deleteStarted = mutableListOf<Long>()
    var deleteGate: CompletableDeferred<Unit>? = null
    var deleteShouldFail = false

    fun summaries(): List<OfflineRegionSummary> = regions.values.toList()
    fun circles(): List<RecordRegion> = regions.values.map { RecordRegion(it.id.toString(), it.region) }

    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun deleteRegion(id: Long): Result<Unit> {
        deleteStarted += id
        deleteGate?.await()
        if (deleteShouldFail) return Result.failure(RuntimeException("delete failed"))
        regions.remove(id)
        return Result.success(Unit)
    }
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions.values.toList())
}

private object HeldPhotoStore : PhotoStore {
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = Result.failure(UnsupportedOperationException("not part of this test's path"))
    override suspend fun delete(photo: LogPhoto): Result<Unit> = Result.success(Unit)
}

private object HeldNoLocation : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}
