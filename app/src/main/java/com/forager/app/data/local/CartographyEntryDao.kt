package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access to the Cartography entry table and its four kept-item ref tables. One DAO spanning all
 * five, the same "one DAO, atomic compound writes" reasoning [MushroomLogDao] follows for the entry/
 * photo/cross-reference tables: replacing an entry's kept-item set has to remove the old ref rows and
 * insert the new ones in the same transaction as the entry row itself.
 */
@Dao
abstract class CartographyEntryDao {

    @Query("SELECT * FROM cartography_entries WHERE isDraft = 0")
    abstract suspend fun getAllCommitted(): List<CartographyEntryEntity>

    @Query("SELECT * FROM cartography_entries WHERE isDraft = 1")
    abstract suspend fun getAllDrafts(): List<CartographyEntryEntity>

    @Query("SELECT * FROM cartography_entries WHERE id = :id")
    abstract suspend fun getById(id: String): CartographyEntryEntity?

    @Query("SELECT * FROM cartography_entry_track_refs WHERE entryId = :entryId")
    abstract suspend fun getTrackRefs(entryId: String): List<CartographyEntryTrackRefEntity>

    @Query("SELECT * FROM cartography_entry_waypoint_refs WHERE entryId = :entryId")
    abstract suspend fun getWaypointRefs(entryId: String): List<CartographyEntryWaypointRefEntity>

    @Query("SELECT * FROM cartography_entry_offline_region_refs WHERE entryId = :entryId")
    abstract suspend fun getOfflineRegionRefs(entryId: String): List<CartographyEntryOfflineRegionRefEntity>

    @Query("SELECT * FROM cartography_entry_find_refs WHERE entryId = :entryId")
    abstract suspend fun getFindRefs(entryId: String): List<CartographyEntryFindRefEntity>

    @Query("SELECT * FROM cartography_entry_photo_refs WHERE entryId = :entryId")
    abstract suspend fun getPhotoRefs(entryId: String): List<CartographyEntryPhotoRefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEntry(entity: CartographyEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrackRefs(refs: List<CartographyEntryTrackRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWaypointRefs(refs: List<CartographyEntryWaypointRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOfflineRegionRefs(refs: List<CartographyEntryOfflineRegionRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFindRefs(refs: List<CartographyEntryFindRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPhotoRefs(refs: List<CartographyEntryPhotoRefEntity>)

    @Query("DELETE FROM cartography_entry_track_refs WHERE entryId = :entryId")
    abstract suspend fun deleteTrackRefsForEntry(entryId: String)

    @Query("DELETE FROM cartography_entry_waypoint_refs WHERE entryId = :entryId")
    abstract suspend fun deleteWaypointRefsForEntry(entryId: String)

    @Query("DELETE FROM cartography_entry_offline_region_refs WHERE entryId = :entryId")
    abstract suspend fun deleteOfflineRegionRefsForEntry(entryId: String)

    @Query("DELETE FROM cartography_entry_find_refs WHERE entryId = :entryId")
    abstract suspend fun deleteFindRefsForEntry(entryId: String)

    @Query("DELETE FROM cartography_entry_photo_refs WHERE entryId = :entryId")
    abstract suspend fun deletePhotoRefsForEntry(entryId: String)

    @Query("DELETE FROM cartography_entries WHERE id = :id")
    abstract suspend fun deleteEntryById(id: String)

    /**
     * Replaces [entity]'s stored kept-item set wholesale with [trackRefs]/[waypointRefs]/
     * [offlineRegionRefs]/[findRefs] — an entry's edit screen always writes its complete current
     * selection, never a delta, so "delete everything for this id, then insert what's current" is
     * simpler and cannot drift the way a diff-and-patch write could.
     */
    @Transaction
    open suspend fun upsertEntryWithRefs(
        entity: CartographyEntryEntity,
        trackRefs: List<CartographyEntryTrackRefEntity>,
        waypointRefs: List<CartographyEntryWaypointRefEntity>,
        offlineRegionRefs: List<CartographyEntryOfflineRegionRefEntity>,
        findRefs: List<CartographyEntryFindRefEntity>,
        photoRefs: List<CartographyEntryPhotoRefEntity>,
    ) {
        upsertEntry(entity)
        deleteTrackRefsForEntry(entity.id)
        deleteWaypointRefsForEntry(entity.id)
        deleteOfflineRegionRefsForEntry(entity.id)
        deleteFindRefsForEntry(entity.id)
        deletePhotoRefsForEntry(entity.id)
        insertTrackRefs(trackRefs)
        insertWaypointRefs(waypointRefs)
        insertOfflineRegionRefs(offlineRegionRefs)
        insertFindRefs(findRefs)
        insertPhotoRefs(photoRefs)
    }

    /** Removes an entry's own row and all five of its kept-item ref tables' rows for it. */
    @Transaction
    open suspend fun deleteEntryAndRefs(id: String) {
        deleteTrackRefsForEntry(id)
        deleteWaypointRefsForEntry(id)
        deleteOfflineRegionRefsForEntry(id)
        deleteFindRefsForEntry(id)
        deletePhotoRefsForEntry(id)
        deleteEntryById(id)
    }

    // kept = 1 only: a withheld decision means the user explicitly excluded this track from the
    // entry, so it does not "appear in" it for 4b's own warning purposes — see the follow-up
    // dispatch's point 2 and CartographyEntryTrackRefEntity's own doc comment on row-presence vs. kept.
    @Query("SELECT COUNT(*) FROM cartography_entry_track_refs WHERE trackId = :trackId AND kept = 1")
    abstract suspend fun countEntriesReferencingTrack(trackId: String): Int

    @Query("SELECT COUNT(*) FROM cartography_entry_waypoint_refs WHERE waypointId = :waypointId AND kept = 1")
    abstract suspend fun countEntriesReferencingWaypoint(waypointId: String): Int

    @Query("SELECT COUNT(*) FROM cartography_entry_offline_region_refs WHERE offlineRegionId = :offlineRegionId AND kept = 1")
    abstract suspend fun countEntriesReferencingOfflineRegion(offlineRegionId: Long): Int

    @Query("SELECT COUNT(*) FROM cartography_entry_photo_refs WHERE photoId = :photoId")
    abstract suspend fun countEntriesReferencingPhoto(photoId: String): Int
}
