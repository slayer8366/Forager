package com.forager.app.domain

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.FindDecision
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.OfflineRegionDecision
import com.forager.app.domain.model.PhotoAttachment
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackDecision
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.WaypointDecision
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [GetCartographyEntryMapDataUseCase] against a real, in-memory Room database — the same reasoning
 * [GetDerivedTripUseCaseTest] gives for using the real [RoomTrackRepository]/[RoomMushroomLogRepository]
 * rather than hand-rolled fakes: a dangling reference here (this use case's whole reason to exist,
 * per its own doc comment) is exactly the case a fake could accidentally get right by construction —
 * a real database simply never having a row for an id proves the "not there" case honestly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GetCartographyEntryMapDataUseCaseTest {

    private lateinit var database: ForagerDatabase
    private lateinit var trackRepository: RoomTrackRepository
    private lateinit var mushroomLogRepository: RoomMushroomLogRepository
    private lateinit var useCase: GetCartographyEntryMapDataUseCase

    private val baseEntry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ForagerDatabase::class.java,
        ).build()
        trackRepository = RoomTrackRepository(database.trackDao())
        mushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao())
        useCase = GetCartographyEntryMapDataUseCase(trackRepository, mushroomLogRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing kept resolves to an entirely empty result`() = runTest {
        val result = useCase(baseEntry, galleryPhotos = emptyList())

        assertTrue(result.isEmpty)
        assertTrue(result.allPoints.isEmpty())
    }

    @Test
    fun `two kept tracks resolve to two separate polylines, each with its own points`() = runTest {
        val trackOne = Track(
            id = "track-1",
            name = "Ridge Loop",
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 2_000L,
            points = listOf(TrackPoint(lat = 45.20, lng = -122.50, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L)),
        )
        val trackTwo = Track(
            id = "track-2",
            name = "Creek Trail",
            startedAtEpochMillis = 3_000L,
            endedAtEpochMillis = 4_000L,
            points = listOf(TrackPoint(lat = 46.00, lng = -123.00, altitude = null, accuracyMeters = null, timestampEpochMillis = 3_000L)),
        )
        trackRepository.create(trackOne).getOrThrow()
        trackRepository.appendPoints(trackOne.id, trackOne.points).getOrThrow()
        trackRepository.create(trackTwo).getOrThrow()
        trackRepository.appendPoints(trackTwo.id, trackTwo.points).getOrThrow()

        val entry = baseEntry.copy(
            trackDecisions = listOf(
                TrackDecision(trackId = "track-1", name = "Ridge Loop", distanceMeters = 100.0, durationMillis = 1_000L, pointCount = 1, kept = true),
                TrackDecision(trackId = "track-2", name = "Creek Trail", distanceMeters = 200.0, durationMillis = 1_000L, pointCount = 1, kept = true),
            ),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertEquals(2, result.trackPolylines.size)
        assertEquals(listOf(LatLng(45.20, -122.50)), result.trackPolylines[0])
        assertEquals(listOf(LatLng(46.00, -123.00)), result.trackPolylines[1])
    }

    @Test
    fun `a kept track deleted from Records draws nothing and does not error`() = runTest {
        // Never inserted into the database at all -- the dangling-reference case this use case
        // exists to tolerate, per its own doc comment.
        val entry = baseEntry.copy(
            trackDecisions = listOf(
                TrackDecision(trackId = "deleted-track", name = "Gone Now Loop", distanceMeters = 100.0, durationMillis = 1_000L, pointCount = 1, kept = true),
            ),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertTrue(result.trackPolylines.isEmpty())
        assertTrue(result.isEmpty)
    }

    @Test
    fun `a withheld (not kept) track is never resolved, even if it still exists`() = runTest {
        val track = Track(id = "withheld-track", name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = 2_000L, points = emptyList())
        trackRepository.create(track).getOrThrow()

        val entry = baseEntry.copy(
            trackDecisions = listOf(
                TrackDecision(trackId = "withheld-track", name = null, distanceMeters = 0.0, durationMillis = 0L, pointCount = 0, kept = false),
            ),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertTrue(result.trackPolylines.isEmpty())
    }

    @Test
    fun `a kept find with a resolved coordinate becomes a find marker`() = runTest {
        val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.5, -122.5), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)
        mushroomLogRepository.save(find).getOrThrow()

        val entry = baseEntry.copy(
            findDecisions = listOf(
                FindDecision(findId = "find-1", foundOn = LocalDate.of(2026, 8, 1), ownIdentification = "Chanterelle", hasPhotos = false, kept = true),
            ),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertEquals(listOf(LatLng(45.5, -122.5)), result.findMarkers)
    }

    @Test
    fun `a kept find with no location produces no find marker, not an error`() = runTest {
        val find = MushroomLogEntry.draft(id = "find-no-location", location = null, date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)
        mushroomLogRepository.save(find).getOrThrow()

        val entry = baseEntry.copy(
            findDecisions = listOf(
                FindDecision(findId = "find-no-location", foundOn = LocalDate.of(2026, 8, 1), ownIdentification = null, hasPhotos = false, kept = true),
            ),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertTrue(result.findMarkers.isEmpty())
    }

    @Test
    fun `a kept find deleted from Records draws nothing and does not error`() = runTest {
        val entry = baseEntry.copy(
            findDecisions = listOf(
                FindDecision(findId = "deleted-find", foundOn = LocalDate.of(2026, 8, 1), ownIdentification = "Gone now", hasPhotos = false, kept = true),
            ),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertTrue(result.findMarkers.isEmpty())
    }

    @Test
    fun `waypoints and offline regions resolve directly from the entry's own snapshot, no repository involved`() = runTest {
        val entry = baseEntry.copy(
            waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.4, lng = -122.4, kept = true)),
            offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 1L, name = "Ridge Region", lat = 45.6, lng = -122.6, radiusKm = 10, kept = true)),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertEquals(listOf(LatLng(45.4, -122.4)), result.waypointMarkers)
        assertEquals(1, result.offlineRegionCircles.size)
        assertEquals(45.6, result.offlineRegionCircles.single().lat, 0.0)
        assertEquals(-122.6, result.offlineRegionCircles.single().lng, 0.0)
        assertEquals(10, result.offlineRegionCircles.single().radiusKm)
    }

    @Test
    fun `a photo with a resolved coordinate becomes a photo marker`() = runTest {
        val entry = baseEntry.copy(photos = listOf(PhotoAttachment(photoId = "p1", attachedAtEpochMillis = 1_000L)))
        val galleryPhotos = listOf(
            GalleryPhoto(
                photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L, latitude = 45.7, longitude = -122.7),
                referencingEntryIds = emptyList(),
            ),
        )

        val result = useCase(entry, galleryPhotos)

        assertEquals(listOf(LatLng(45.7, -122.7)), result.photoMarkers)
    }

    @Test
    fun `a photo with no coordinate is skipped silently, not an error`() = runTest {
        val entry = baseEntry.copy(photos = listOf(PhotoAttachment(photoId = "p1", attachedAtEpochMillis = 1_000L)))
        val galleryPhotos = listOf(
            GalleryPhoto(
                photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L, latitude = null, longitude = null),
                referencingEntryIds = emptyList(),
            ),
        )

        val result = useCase(entry, galleryPhotos)

        assertTrue(result.photoMarkers.isEmpty())
    }

    @Test
    fun `a photo whose gallery row is missing entirely is skipped silently`() = runTest {
        val entry = baseEntry.copy(photos = listOf(PhotoAttachment(photoId = "missing", attachedAtEpochMillis = 1_000L)))

        val result = useCase(entry, galleryPhotos = emptyList())

        assertTrue(result.photoMarkers.isEmpty())
    }

    @Test
    fun `allPoints flattens every resolved category together`() = runTest {
        val entry = baseEntry.copy(
            waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.4, lng = -122.4, kept = true)),
            offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 1L, name = "Ridge Region", lat = 45.6, lng = -122.6, radiusKm = 10, kept = true)),
        )

        val result = useCase(entry, galleryPhotos = emptyList())

        assertEquals(setOf(LatLng(45.4, -122.4), LatLng(45.6, -122.6)), result.allPoints.toSet())
    }
}
