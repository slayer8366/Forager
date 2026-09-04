package com.forager.app.data.repository

import com.forager.app.data.local.CartographyEntryDao
import com.forager.app.data.local.CartographyEntryEntity
import com.forager.app.data.local.CartographyEntryEntity.Companion.TAG_DELIMITER
import com.forager.app.data.local.CartographyEntryFindRefEntity
import com.forager.app.data.local.CartographyEntryOfflineRegionRefEntity
import com.forager.app.data.local.CartographyEntryPhotoRefEntity
import com.forager.app.data.local.CartographyEntryTrackRefEntity
import com.forager.app.data.local.CartographyEntryWaypointRefEntity
import com.forager.app.domain.CartographyEntryRepository
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.FindDecision
import com.forager.app.domain.model.OfflineRegionDecision
import com.forager.app.domain.model.PhotoAttachment
import com.forager.app.domain.model.TrackDecision
import com.forager.app.domain.model.WaypointDecision
import java.time.LocalDate

/** Room-backed [CartographyEntryRepository]; the only place the entity family and [CartographyEntry] meet. */
class RoomCartographyEntryRepository(
    private val dao: CartographyEntryDao,
) : CartographyEntryRepository {

    override suspend fun getAll(): Result<List<CartographyEntry>> = runCatchingCancellable {
        dao.getAllCommitted().map { it.toDomain() }
    }

    override suspend fun getAllDrafts(): Result<List<CartographyEntry>> = runCatchingCancellable {
        dao.getAllDrafts().map { it.toDomain() }
    }

    override suspend fun getById(id: String): Result<CartographyEntry?> = runCatchingCancellable {
        dao.getById(id)?.toDomain()
    }

    override suspend fun save(entry: CartographyEntry): Result<Unit> = runCatchingCancellable {
        dao.upsertEntryWithRefs(
            entity = entry.toEntity(),
            trackRefs = entry.trackDecisions.map { it.toEntity(entry.id) },
            waypointRefs = entry.waypointDecisions.map { it.toEntity(entry.id) },
            offlineRegionRefs = entry.offlineRegionDecisions.map { it.toEntity(entry.id) },
            findRefs = entry.findDecisions.map { it.toEntity(entry.id) },
            photoRefs = entry.photos.map { it.toEntity(entry.id) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = runCatchingCancellable {
        dao.deleteEntryAndRefs(id)
    }

    override suspend fun countEntriesReferencingTrack(trackId: String): Result<Int> = runCatchingCancellable {
        dao.countEntriesReferencingTrack(trackId)
    }

    override suspend fun countEntriesReferencingWaypoint(waypointId: String): Result<Int> = runCatchingCancellable {
        dao.countEntriesReferencingWaypoint(waypointId)
    }

    override suspend fun countEntriesReferencingOfflineRegion(offlineRegionId: Long): Result<Int> = runCatchingCancellable {
        dao.countEntriesReferencingOfflineRegion(offlineRegionId)
    }

    override suspend fun countEntriesReferencingPhoto(photoId: String): Result<Int> = runCatchingCancellable {
        dao.countEntriesReferencingPhoto(photoId)
    }

    private suspend fun CartographyEntryEntity.toDomain(): CartographyEntry = CartographyEntry(
        id = id,
        date = LocalDate.parse(date),
        text = text,
        tags = if (tags.isEmpty()) emptyList() else tags.split(TAG_DELIMITER),
        isDraft = isDraft,
        updatedAtEpochMillis = updatedAtEpochMillis,
        findDecisions = dao.getFindRefs(id).map { it.toDomain() },
        trackDecisions = dao.getTrackRefs(id).map { it.toDomain() },
        waypointDecisions = dao.getWaypointRefs(id).map { it.toDomain() },
        offlineRegionDecisions = dao.getOfflineRegionRefs(id).map { it.toDomain() },
        photos = dao.getPhotoRefs(id).map { it.toDomain() },
    )
}

private fun CartographyEntry.toEntity(): CartographyEntryEntity = CartographyEntryEntity(
    id = id,
    date = date.toString(),
    text = text,
    tags = tags.joinToString(TAG_DELIMITER),
    isDraft = isDraft,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun TrackDecision.toEntity(entryId: String) = CartographyEntryTrackRefEntity(
    entryId = entryId,
    trackId = trackId,
    name = name,
    distanceMeters = distanceMeters,
    durationMillis = durationMillis,
    pointCount = pointCount,
    kept = kept,
)

private fun CartographyEntryTrackRefEntity.toDomain() = TrackDecision(
    trackId = trackId,
    name = name,
    distanceMeters = distanceMeters,
    durationMillis = durationMillis,
    pointCount = pointCount,
    kept = kept,
)

private fun WaypointDecision.toEntity(entryId: String) = CartographyEntryWaypointRefEntity(
    entryId = entryId,
    waypointId = waypointId,
    name = name,
    lat = lat,
    lng = lng,
    kept = kept,
)

private fun CartographyEntryWaypointRefEntity.toDomain() = WaypointDecision(
    waypointId = waypointId,
    name = name,
    lat = lat,
    lng = lng,
    kept = kept,
)

private fun OfflineRegionDecision.toEntity(entryId: String) = CartographyEntryOfflineRegionRefEntity(
    entryId = entryId,
    offlineRegionId = offlineRegionId,
    name = name,
    lat = lat,
    lng = lng,
    radiusKm = radiusKm,
    kept = kept,
)

private fun CartographyEntryOfflineRegionRefEntity.toDomain() = OfflineRegionDecision(
    offlineRegionId = offlineRegionId,
    name = name,
    lat = lat,
    lng = lng,
    radiusKm = radiusKm,
    kept = kept,
)

private fun FindDecision.toEntity(entryId: String) = CartographyEntryFindRefEntity(
    entryId = entryId,
    findId = findId,
    foundOn = foundOn.toString(),
    ownIdentification = ownIdentification,
    hasPhotos = hasPhotos,
    kept = kept,
)

private fun CartographyEntryFindRefEntity.toDomain() = FindDecision(
    findId = findId,
    foundOn = LocalDate.parse(foundOn),
    ownIdentification = ownIdentification,
    hasPhotos = hasPhotos,
    kept = kept,
)

private fun PhotoAttachment.toEntity(entryId: String) = CartographyEntryPhotoRefEntity(
    entryId = entryId,
    photoId = photoId,
    attachedAtEpochMillis = attachedAtEpochMillis,
)

private fun CartographyEntryPhotoRefEntity.toDomain() = PhotoAttachment(
    photoId = photoId,
    attachedAtEpochMillis = attachedAtEpochMillis,
)
