package com.zynergylabs.forager.app.ui.map.fanout

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMapRecordsUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.ui.availability.RigNoOfflineMaps
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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
 * Dispatch 2026-10-27-05 (RECORDs -1734 and -1735): the fan and spiral group only what the map draws (the probe asks the
 * rendered markers, [MapProbe.markersInBox]), so a photo a saved, located find links to leaves every group with its icon.
 * The records come from the real [GetMapRecordsUseCase] over a real database, are placed in the scene exactly as the
 * map's sources would hold them, and the real [MapTapHandler] is tapped on the find.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindPhotosLeaveTheFanTest {

    private lateinit var database: ForagerDatabase
    private lateinit var log: RoomMushroomLogRepository
    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private val order = orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT)
    private val handler = MapTapHandler(fan, scene, { order }, sinks).also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java)
            .allowMainThreadQueries().build()
        log = RoomMushroomLogRepository(database.mushroomLogDao())
    }

    @After
    fun tearDown() = database.close()

    private fun find(id: String, at: LatLng) = runBlocking {
        log.save(MushroomLogEntry.draft(id = id, location = at, date = LocalDate.of(2026, 10, 20)).copy(isDraft = false)).getOrThrow()
    }

    private fun photo(id: String, at: LatLng, toFind: String? = null) = runBlocking {
        log.addPhotoToGallery(LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1L, latitude = at.lat, longitude = at.lng)).getOrThrow()
        if (toFind != null) log.attachPhotoToEntry(toFind, id).getOrThrow()
    }

    /** The records the Maps tab would draw, placed in the scene as the finds and photos sources hold them. */
    private fun drawRecords() {
        val records = runBlocking {
            GetMapRecordsUseCase(
                getFinds = GetMushroomLogEntriesUseCase(log),
                getTracks = GetTracksUseCase(RoomTrackRepository(database.trackDao())),
                getPhotos = GetGalleryPhotosUseCase(log),
                offlineMapRepository = RigNoOfflineMaps,
            )()
        }
        records.findMarkers.forEach { scene.add(MapLayerIds.FINDS, it.recordId, it.at.lat, it.at.lng) }
        records.photoMarkers.forEach { scene.add(MapLayerIds.PHOTOS, it.recordId, it.at.lat, it.at.lng) }
    }

    private fun tapOn(at: LatLng) = handler.onMapTap(at, scene.xPx(at.lng), scene.yPx(at.lat))

    private val spot = LatLng(45.0, -122.0)
    private fun near(dLat: Double, dLng: Double) = LatLng(spot.lat + dLat, spot.lng + dLng)

    @Test
    fun `a find with its own photos is a lone marker - no fan, its bubble opens`() {
        find("find-1", spot)
        photo("ph-a", near(0.0001, 0.0001), toFind = "find-1")
        photo("ph-b", near(-0.0001, 0.0001), toFind = "find-1")
        drawRecords()

        tapOn(spot)

        assertFalse("the find's photos are not drawn, so there is no stack", fan.isOpen)
        assertEquals(listOf("feature:${MapLayerIds.FINDS}:find-1"), sinks.events)
    }

    @Test
    fun `a stack groups the find and a standalone photo, never the find's own photos`() {
        find("find-1", spot)
        photo("ph-a", near(0.0001, 0.0001), toFind = "find-1")
        photo("ph-b", near(-0.0001, 0.0001), toFind = "find-1")
        photo("standalone", near(0.0001, -0.0001))
        drawRecords()

        tapOn(spot)

        assertTrue("positive control: the find and the standalone photo still stack", fan.isOpen)
        assertEquals(
            setOf(FanKey(MapLayerIds.FINDS, "find-1"), FanKey(MapLayerIds.PHOTOS, "standalone")),
            fan.members.map { it.key }.toSet(),
        )
    }
}
