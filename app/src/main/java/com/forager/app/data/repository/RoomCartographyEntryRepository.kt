package com.forager.app.data.repository

import com.forager.app.data.local.CartographyEntryDao
import com.forager.app.data.local.CartographyEntryEntity
import com.forager.app.data.local.CartographyEntryEntity.Companion.TAG_DELIMITER
import com.forager.app.data.local.CartographyEntryFindRefEntity
import com.forager.app.data.local.CartographyEntryOfflineRegionRefEntity
import com.forager.app.data.local.CartographyEntryTrackRefEntity
import com.forager.app.data.local.CartographyEntryWaypointRefEntity
import com.forager.app.domain.CartographyEntryRepository
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.KeptFindRef
import com.forager.app.domain.model.KeptOfflineRegionRef
import com.forager.app.domain.model.KeptTrackRef
import com.forager.app.domain.model.KeptWaypointRef
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
            trackRefs = entry.keptTracks.map { it.toEntity(entry.id) },
            waypointRefs = entry.keptWaypoints.map { it.toEntity(entry.id) },
            offlineRegionRefs = entry.keptOfflineRegions.map { it.toEntity(entry.id) },
            findRefs = entry.keptFinds.map { it.toEntity(entry.id) },
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

    private suspend fun CartographyEntryEntity.toDomain(): CartographyEntry = CartographyEntry(
        id = id,
        date = LocalDate.parse(date),
        text = text,
        tags = if (tags.isEmpty()) emptyList() else tags.split(TAG_DELIMITER),
        isDraft = isDraft,
        updatedAtEpochMillis = updatedAtEpochMillis,
        keptFinds = dao.getFindRefs(id).map { it.toDomain() },
        keptTracks = dao.getTrackRefs(id).map { it.toDomain() },
        keptWaypoints = dao.getWaypointRefs(id).map { it.toDomain() },
        keptOfflineRegions = dao.getOfflineRegionRefs(id).map { it.toDomain() },
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

private fun KeptTrackRef.toEntity(entryId: String) = CartographyEntryTrackRefEntity(
    entryId = entryId,
    trackId = trackId,
    name = name,
    distanceMeters = distanceMeters,
    durationMillis = durationMillis,
    pointCount = pointCount,
)

private fun CartographyEntryTrackRefEntity.toDomain() = KeptTrackRef(
    trackId = trackId,
    name = name,
    distanceMeters = distanceMeters,
    durationMillis = durationMillis,
    pointCount = pointCount,
)

private fun KeptWaypointRef.toEntity(entryId: String) = CartographyEntryWaypointRefEntity(
    entryId = entryId,
    waypointId = waypointId,
    name = name,
    lat = lat,
    lng = lng,
)

private fun CartographyEntryWaypointRefEntity.toDomain() = KeptWaypointRef(
    waypointId = waypointId,
    name = name,
    lat = lat,
    lng = lng,
)

private fun KeptOfflineRegionRef.toEntity(entryId: String) = CartographyEntryOfflineRegionRefEntity(
    entryId = entryId,
    offlineRegionId = offlineRegionId,
    name = name,
    lat = lat,
    lng = lng,
    radiusKm = radiusKm,
)

private fun CartographyEntryOfflineRegionRefEntity.toDomain() = KeptOfflineRegionRef(
    offlineRegionId = offlineRegionId,
    name = name,
    lat = lat,
    lng = lng,
    radiusKm = radiusKm,
)

private fun KeptFindRef.toEntity(entryId: String) = CartographyEntryFindRefEntity(
    entryId = entryId,
    findId = findId,
    foundOn = foundOn.toString(),
    ownIdentification = ownIdentification,
    hasPhotos = hasPhotos,
)

private fun CartographyEntryFindRefEntity.toDomain() = KeptFindRef(
    findId = findId,
    foundOn = LocalDate.parse(foundOn),
    ownIdentification = ownIdentification,
    hasPhotos = hasPhotos,
)
