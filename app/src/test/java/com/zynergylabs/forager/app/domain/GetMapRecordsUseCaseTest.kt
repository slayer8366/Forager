package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GetMapRecordsUseCase] (map layers L0b, B2): every saved record the Maps tab draws, gathered through
 * the read paths the Journal and Records already use ([GetMushroomLogEntriesUseCase],
 * [GetTracksUseCase], [GetGalleryPhotosUseCase], [OfflineMapRepository.listRegions]), headless, with
 * fakes behind those paths so each rule is set up by exactly the rows it is about.
 */
class GetMapRecordsUseCaseTest {

    private val day = LocalDate.of(2026, 9, 20)

    private fun find(id: String, at: LatLng?, isDraft: Boolean = false, draftOf: String? = null): MushroomLogEntry =
        MushroomLogEntry.draft(id = id, location = at, date = day).copy(isDraft = isDraft, draftOfEntryId = draftOf)

    private fun point(lat: Double, lng: Double) = TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L)

    private fun track(id: String, points: List<TrackPoint>, endedAt: Long? = 9_000L) =
        Track(id = id, name = id, startedAtEpochMillis = 1_000L, endedAtEpochMillis = endedAt, points = points)

    private fun photo(id: String, lat: Double?, lng: Double?) =
        GalleryPhoto(LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = 1_000L, latitude = lat, longitude = lng), emptyList())

    private fun region(id: Long, lat: Double, lng: Double, radiusKm: Int) = OfflineRegionSummary(
        id = id, name = "Region $id", region = Region(lat, lng, radiusKm), minZoom = 10.0, maxZoom = 15.0,
        tileCount = 10, sizeBytes = 100L, createdAtEpochMillis = 1_000L,
    )

    private fun useCase(
        entries: Result<List<MushroomLogEntry>> = Result.success(emptyList()),
        photos: Result<List<GalleryPhoto>> = Result.success(emptyList()),
        tracks: Result<List<Track>> = Result.success(emptyList()),
        regions: Result<List<OfflineRegionSummary>> = Result.success(emptyList()),
    ): GetMapRecordsUseCase {
        val log = FakeLogRepository(entries, photos)
        return GetMapRecordsUseCase(
            getFinds = GetMushroomLogEntriesUseCase(log),
            getTracks = GetTracksUseCase(FakeTrackRepository(tracks)),
            getPhotos = GetGalleryPhotosUseCase(log),
            offlineMapRepository = FakeOfflineMapRepository(regions),
        )
    }

    @Test
    fun `every saved find with a location is a marker carrying its id, and no draft is`() = runTest {
        val records = useCase(
            entries = Result.success(
                listOf(
                    find("saved", LatLng(45.5, -122.5)),
                    find("saved-no-location", at = null),
                    find("new-draft", LatLng(45.6, -122.6), isDraft = true),
                    // A re-edit's draft is its own row; the committed row it points at is what draws.
                    find("re-edit-draft", LatLng(46.0, -123.0), isDraft = true, draftOf = "saved"),
                ),
            ),
        )()

        assertEquals(listOf(RecordPoint("saved", LatLng(45.5, -122.5))), records.findMarkers)
        assertEquals(emptyList<MapRecordReadFailure>(), records.failures)
    }

    @Test
    fun `a track that resolves to zero points gives no polyline, and every ended track in Records draws with its id`() = runTest {
        val records = useCase(
            tracks = Result.success(
                listOf(
                    track("walked", listOf(point(45.1, -122.1), point(45.2, -122.2))),
                    track("every-point-excluded", emptyList()),
                    track("one-point", listOf(point(45.4, -122.4))),
                ),
            ),
        )()

        assertEquals(
            listOf(
                RecordPolyline("walked", listOf(LatLng(45.1, -122.1), LatLng(45.2, -122.2))),
                RecordPolyline("one-point", listOf(LatLng(45.4, -122.4))),
            ),
            records.trackPolylines,
        )
    }

    /**
     * The owner's ruling on Q6 (planner message 3 on dispatch 2026-09-28-03): "Leave it out". A track
     * with no end time is being recorded; the Recording trail layer draws it, and it joins Tracks once
     * it has ended.
     */
    @Test
    fun `the track being recorded is left out of Tracks until it has ended`() = runTest {
        val recording = track("recording", listOf(point(45.3, -122.3), point(45.31, -122.31)), endedAt = null)

        val whileRecording = useCase(tracks = Result.success(listOf(recording)))()
        assertEquals(emptyList<RecordPolyline>(), whileRecording.trackPolylines)
        assertEquals("leaving it out is not a failure", emptyList<MapRecordReadFailure>(), whileRecording.failures)

        val ended = useCase(tracks = Result.success(listOf(recording.copy(endedAtEpochMillis = 9_000L))))()
        assertEquals(listOf("recording"), ended.trackPolylines.map { it.recordId })
    }

    /**
     * The planner's ruling on Q7 (message 3): a record in its Undo window leaves the map at once, as it
     * leaves Records. The pending ids live in the ViewModels (one per kind, J4), so the screen drops
     * them from the records it hands the map.
     */
    @Test
    fun `a find, photo or offline region with a pending delete is left out, and nothing else is`() = runTest {
        val records = useCase(
            entries = Result.success(listOf(find("kept", LatLng(45.5, -122.5)), find("deleting", LatLng(45.6, -122.6)))),
            photos = Result.success(listOf(photo("kept", 45.4, -122.4), photo("deleting", 45.3, -122.3))),
            tracks = Result.success(listOf(track("walked", listOf(point(45.1, -122.1))))),
            regions = Result.success(listOf(region(7L, 45.0, -122.0, 10), region(8L, 46.0, -121.0, 10))),
        )()

        val visible = records.withoutPending(findId = "deleting", photoId = "deleting", offlineRegionId = "8")

        assertEquals(listOf("kept"), visible.findMarkers.map { it.recordId })
        assertEquals(listOf("kept"), visible.photoMarkers.map { it.recordId })
        assertEquals(listOf("7"), visible.offlineRegionCircles.map { it.recordId })
        assertEquals(records.trackPolylines, visible.trackPolylines)
        assertEquals("nothing pending: nothing left out", records, records.withoutPending(null, null, null))
    }

    @Test
    fun `an album photo with no coordinate gives nothing, one with both draws with its id`() = runTest {
        val records = useCase(
            photos = Result.success(
                listOf(
                    photo("located", 45.4, -122.4),
                    photo("unlocated", null, null),
                    photo("latitude-only", 45.4, null),
                    photo("longitude-only", null, -122.4),
                ),
            ),
        )()

        assertEquals(listOf(RecordPoint("located", LatLng(45.4, -122.4))), records.photoMarkers)
    }

    @Test
    fun `every offline region draws as a circle with its id in decimal`() = runTest {
        val records = useCase(regions = Result.success(listOf(region(7L, 45.0, -122.0, 10), region(12L, 46.0, -121.0, 25))))()

        assertEquals(
            listOf(RecordRegion("7", Region(45.0, -122.0, 10)), RecordRegion("12", Region(46.0, -121.0, 25))),
            records.offlineRegionCircles,
        )
    }

    @Test
    fun `one kind failing is reported and drawn as absent while every other kind still draws`() = runTest {
        val entries = Result.success(listOf(find("saved", LatLng(45.5, -122.5))))
        val photos = Result.success(listOf(photo("located", 45.4, -122.4)))
        val tracks = Result.success(listOf(track("walked", listOf(point(45.1, -122.1), point(45.2, -122.2)))))
        val regions = Result.success(listOf(region(7L, 45.0, -122.0, 10)))

        MapRecordKind.entries.forEach { failing ->
            val error = IOException("$failing unreadable")
            val records = useCase(
                entries = if (failing == MapRecordKind.FINDS) Result.failure(error) else entries,
                photos = if (failing == MapRecordKind.PHOTOS) Result.failure(error) else photos,
                tracks = if (failing == MapRecordKind.TRACKS) Result.failure(error) else tracks,
                regions = if (failing == MapRecordKind.OFFLINE_REGIONS) Result.failure(error) else regions,
            )()

            assertEquals("$failing: the failure is reported with its own error", listOf(MapRecordReadFailure(failing, error)), records.failures)
            assertEquals("$failing: finds", if (failing == MapRecordKind.FINDS) 0 else 1, records.findMarkers.size)
            assertEquals("$failing: photos", if (failing == MapRecordKind.PHOTOS) 0 else 1, records.photoMarkers.size)
            assertEquals("$failing: tracks", if (failing == MapRecordKind.TRACKS) 0 else 1, records.trackPolylines.size)
            assertEquals("$failing: offline regions", if (failing == MapRecordKind.OFFLINE_REGIONS) 0 else 1, records.offlineRegionCircles.size)
        }
    }

    private class FakeLogRepository(
        private val entries: Result<List<MushroomLogEntry>>,
        private val photos: Result<List<GalleryPhoto>>,
    ) : MushroomLogRepository {
        override suspend fun getAll() = entries
        override suspend fun getAllPhotos() = photos
        override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> = error("not used")
        override suspend fun save(entry: MushroomLogEntry): Result<Unit> = error("not used")
        override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> = error("not used")
        override suspend fun delete(id: String): Result<Unit> = error("not used")
        override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> = error("not used")
        override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> = error("not used")
        override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> = error("not used")
        override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> = error("not used")
        override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> = error("not used")
    }

    private class FakeTrackRepository(private val tracks: Result<List<Track>>) : TrackRepository {
        override suspend fun getAll() = tracks
        override suspend fun getById(id: String): Result<Track?> = error("not used")
        override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> = error("not used")
        override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> = error("not used")
        override suspend fun create(track: Track): Result<Unit> = error("not used")
        override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = error("not used")
        override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = error("not used")
        override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> = error("not used")
        override suspend fun delete(id: String): Result<Unit> = error("not used")
    }

    private class FakeOfflineMapRepository(private val regions: Result<List<OfflineRegionSummary>>) : OfflineMapRepository {
        override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
        override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
        override suspend fun listRegions() = regions
        override suspend fun listNotDownloadedRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())

        override suspend fun replaceRegion(oldId: Long, newId: Long): Result<Unit> = Result.success(Unit)
    }
}
