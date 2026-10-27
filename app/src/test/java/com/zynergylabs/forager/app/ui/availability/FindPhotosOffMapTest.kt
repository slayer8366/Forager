package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMapRecordsUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.mapLayerSwitchTag
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-27-05 (RECORDs -1734 and -1735): a photo a saved, located find links to has no map icon of its own;
 * standalone photos follow the Photos switch as before. Through the real Maps tab ([AvailabilityScreen], by
 * `MapLayersTestScreen`) over a real database and the real [GetMapRecordsUseCase] on the read paths the app container
 * builds it from, reading what the screen hands the map ([BubbleMapSlot.content] and the render mode's
 * layers). What MapLibre then draws from those lists is device-only. One test per ruling of Amendment 1, plus the
 * owner's step path and the Photos switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class FindPhotosOffMapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    // A database of the test's own, closed after it. Over the app container's database, five of twelve map reads never
    // landed across CI runs 40556724310 and 40558339626, cause not established.
    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomMushroomLogRepository

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
        repository = RoomMushroomLogRepository(database.mushroomLogDao())
    }

    @After
    fun closeDatabase() = database.close()
    private val map = BubbleMapSlot(emptyList())
    private lateinit var availability: AvailabilityViewModel

    private val day = LocalDate.of(2026, 10, 20)

    private fun saveFind(id: String, at: LatLng?, draft: Boolean = false) = runBlocking {
        repository.save(MushroomLogEntry.draft(id = id, location = at, date = day).copy(isDraft = draft)).getOrThrow()
    }

    private fun addPhoto(id: String, lat: Double, lng: Double, toEntry: String? = null) = runBlocking {
        repository.addPhotoToGallery(LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1L, latitude = lat, longitude = lng)).getOrThrow()
        if (toEntry != null) repository.attachPhotoToEntry(toEntry, id).getOrThrow()
    }

    private fun show(cartography: CartographyUiState = CartographyUiState()) {
        // The read paths AppContainer builds getMapRecordsUseCase from, over this test's database, with no offline maps:
        // the app's offline store is MapLibre's, whose native library Robolectric cannot load (UnsatisfiedLinkError, CI run
        // 40556724310).
        val getMapRecords = GetMapRecordsUseCase(
            getFinds = GetMushroomLogEntriesUseCase(repository),
            getTracks = GetTracksUseCase(RoomTrackRepository(database.trackDao())),
            getPhotos = GetGalleryPhotosUseCase(repository),
            offlineMapRepository = RigNoOfflineMaps,
        )
        availability = mapLayersViewModel(getMapRecords = { getMapRecords() })
        composeRule.setContent {
            MapLayersTestScreen(viewModel = availability, mapSlot = map.slot, store = AbsentForecastCellStore, cartographyUiState = cartography)
        }
        composeRule.waitForIdle()
        reload()
    }

    /**
     * The Maps tab shown again, as a tab switch does, and its records read; [loaded] says when the read has landed. The
     * class's first test took 12.9 s on CI run 40555150432, cause not established (MapLibre's offline store was then in
     * the path), hence 30 s. A wait that
     * runs out says what the map, the ViewModel and the database held, so a stall is not read as a wrong answer.
     */
    private fun reload(loaded: (MapOverlayContent) -> Boolean = { it.findMarkers.isNotEmpty() || it.photoMarkers.isNotEmpty() }) {
        availability.onMapShown()
        try {
            composeRule.waitUntil(30_000) { map.content?.let(loaded) == true }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            val stored = runBlocking { repository.getAll().getOrNull()?.map { "${it.id} draft=${it.isDraft} at=${it.foundAt}" } }
            throw AssertionError(
                "the map's records never landed: handed finds=${map.content?.findMarkers?.map { it.recordId }} " +
                    "photos=${map.content?.photoMarkers?.map { it.recordId }}; ViewModel finds=" +
                    "${availability.uiState.value.mapRecords.findMarkers.map { it.recordId }} photos=" +
                    "${availability.uiState.value.mapRecords.photoMarkers.map { it.recordId }} failures=" +
                    "${availability.uiState.value.mapRecords.failures}; stored finds=$stored\n" + threadDump(),
                timeout,
            )
        }
        composeRule.waitForIdle()
    }

    private fun finds() = map.content!!.findMarkers.map { it.recordId }
    private fun photos() = map.content!!.photoMarkers.map { it.recordId }.sorted()

    @Test
    fun `a find with two photos draws one find icon and no photo icons`() {
        saveFind("find-1", LatLng(45.50, -122.60))
        addPhoto("ph-a", 45.5003, -122.6004, toEntry = "find-1")
        addPhoto("ph-b", 45.4998, -122.5997, toEntry = "find-1")

        show()

        assertEquals("one find icon", listOf("find-1"), finds())
        assertEquals("no photo icons for the find's photos", emptyList<String>(), photos())
    }

    @Test
    fun `a standalone photo draws with Photos on and not with Photos off, beside a find that hides its own`() {
        saveFind("find-1", LatLng(45.50, -122.60))
        addPhoto("ph-of-find", 45.5003, -122.6004, toEntry = "find-1")
        addPhoto("standalone", 45.52, -122.62)

        show()

        assertEquals("only the standalone photo is handed to the map", listOf("standalone"), photos())
        assertEquals("Photos is on", true, map.renderMode?.layers?.stateOf(MapLayerIds.PHOTOS)?.visible)

        composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.PHOTOS)).performScrollTo().performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals("Photos off hides the photo layer", false, map.renderMode?.layers?.stateOf(MapLayerIds.PHOTOS)?.visible)
        assertEquals("the find is untouched by the Photos switch", true, map.renderMode?.layers?.stateOf(MapLayerIds.FINDS)?.visible)
    }

    /** Amendment 1, "No icon anywhere (Recommended)": also attached to a shown journal entry, still no icon and no ring. */
    @Test
    fun `a photo linked to a find and to a shown journal entry has no icon and no journal ring`() {
        saveFind("find-1", LatLng(45.50, -122.60))
        addPhoto("ph-both", 45.5003, -122.6004, toEntry = "find-1")
        addPhoto("ph-entry-only", 45.52, -122.62)
        val entry = CartographyEntry.draft(id = "entry-1", date = day, updatedAtEpochMillis = 1L).copy(
            isDraft = false,
            shownOnMap = true,
            photos = listOf(PhotoAttachment("ph-both", 1L), PhotoAttachment("ph-entry-only", 1L)),
        )

        show(CartographyUiState(entries = listOf(entry)))

        assertEquals(listOf("ph-entry-only"), photos())
        assertEquals(
            "positive control: the entry's standalone photo is ringed, and the find's is not",
            listOf("ph-entry-only"),
            map.content!!.journalHighlights.photoMarkers.map { it.recordId },
        )
    }

    /** Amendment 1, "Show its photos (Recommended)": a find with no location hides nothing. */
    @Test
    fun `a find with no location leaves its located photos on the map as standalone photos`() {
        saveFind("unlocated", at = null)
        addPhoto("ph-a", 45.5003, -122.6004, toEntry = "unlocated")

        show()

        assertEquals(emptyList<String>(), finds())
        assertEquals(listOf("ph-a"), photos())
    }

    /** Amendment 1, "Hide only after Save (Recommended)": a draft's photos keep their icons until Save. */
    @Test
    fun `a draft find's photos keep their icons until it is saved`() {
        saveFind("new-find", LatLng(45.50, -122.60), draft = true)
        addPhoto("ph-a", 45.5003, -122.6004, toEntry = "new-find")

        show()
        assertEquals("while a draft, the photo is drawn", listOf("ph-a"), photos())

        runBlocking {
            repository.commitDraft("new-find", MushroomLogEntry.draft(id = "new-find", location = LatLng(45.50, -122.60), date = day).copy(isDraft = false)).getOrThrow()
        }
        reload { it.findMarkers.isNotEmpty() }

        assertEquals("saved, the find draws", listOf("new-find"), finds())
        assertEquals("and its photo no longer does", emptyList<String>(), photos())
    }

    /** Amendment 1, "Fine, they reappear (Recommended)": after the Undo window, a deleted find's photos are standalone. */
    @Test
    fun `a deleted find's photos come back as standalone photos once its delete is committed`() {
        saveFind("find-1", LatLng(45.50, -122.60))
        addPhoto("ph-a", 45.5003, -122.6004, toEntry = "find-1")
        addPhoto("ph-b", 45.4998, -122.5997, toEntry = "find-1")
        show()
        assertEquals("positive control: hidden while the find is saved", emptyList<String>(), photos())

        // The delete the Journal commits when its Undo window ends (MushroomLogViewModel.commitDeleteEntry calls this use
        // case); that the Journal's delete reaches the map is MushroomLogViewModelWiringTest's.
        runBlocking { DeleteMushroomLogEntryUseCase(repository)("find-1").getOrThrow() }
        reload { it.photoMarkers.isNotEmpty() }

        assertEquals("the find is gone", emptyList<String>(), finds())
        assertEquals("its photos are standalone again", listOf("ph-a", "ph-b"), photos())
    }

    /** Every thread's stack, for a wait that ran out: which coroutine or query it stalled on. */
    private fun threadDump(): String = Thread.getAllStackTraces().entries.joinToString("\n") { (thread, frames) ->
        "-- ${thread.name} (${thread.state})\n" + frames.take(25).joinToString("\n") { "   at $it" }
    }
}
