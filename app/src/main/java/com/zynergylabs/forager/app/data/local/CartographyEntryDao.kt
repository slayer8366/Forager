package com.zynergylabs.forager.app.data.local

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

    @Query("SELECT * FROM cartography_entry_track_paths WHERE entryId = :entryId")
    abstract suspend fun getTrackPaths(entryId: String): List<CartographyEntryTrackPathEntity>

    /**
     * F3: writes [path] for every ref row naming [trackId], in one statement, so the lookup of which entries have
     * the track and the writes cannot disagree. No `kept` and no `isDraft` condition, on purpose: a withheld
     * decision or a draft's ref is still a ref row, and what the reader draws is decided by `kept` at read time
     * (`GetCartographyEntryMapDataUseCase`), not here. `INSERT OR REPLACE` makes it idempotent.
     */
    @Query(
        """
        INSERT OR REPLACE INTO cartography_entry_track_paths (entryId, trackId, path)
        SELECT entryId, trackId, :path FROM cartography_entry_track_refs WHERE trackId = :trackId
        """,
    )
    abstract suspend fun copyTrackPathToEveryRef(trackId: String, path: ByteArray)

    @Query("DELETE FROM cartography_entry_track_paths WHERE entryId = :entryId")
    abstract suspend fun deleteTrackPathsForEntry(entryId: String)

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
     * J8: sets one entry's `shownOnMap` and no other column, so showing an entry on the map never
     * rewrites its text, decisions or edit stamp. Returns the rows changed: 0 when no entry [id] exists.
     */
    @Query("UPDATE cartography_entries SET shownOnMap = :shown WHERE id = :id")
    abstract suspend fun setShownOnMap(id: String, shown: Boolean): Int

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

    /** Removes an entry's own row, all five of its kept-item ref tables' rows for it, and its saved track paths (F3). */
    @Transaction
    open suspend fun deleteEntryAndRefs(id: String) {
        deleteTrackPathsForEntry(id)
        deleteTrackRefsForEntry(id)
        deleteWaypointRefsForEntry(id)
        deleteOfflineRegionRefsForEntry(id)
        deleteFindRefsForEntry(id)
        deletePhotoRefsForEntry(id)
        deleteEntryById(id)
    }

    // kept = 1: a withheld decision means the user explicitly excluded this track from the entry,
    // so it does not "appear in" it for 4b's own warning purposes — see the follow-up dispatch's
    // point 2 and CartographyEntryTrackRefEntity's own doc comment on row-presence vs. kept.
    // isDraft = 0 (joined against cartography_entries, not a stored column on this table): an
    // unfinished, uncommitted draft's decisions are not yet a real reference either — see the
    // draft-lifecycle dispatch's own decision 3. A draft can (and, before that dispatch's fix,
    // routinely did) accumulate as an invisible orphan; counting its refs would warn the user away
    // from deleting something loudest exactly when orphans have piled up, which is the failure this
    // condition exists to prevent. The warning still never blocks deletion — it only informs.
    @Query(
        """
        SELECT COUNT(*) FROM cartography_entry_track_refs
        INNER JOIN cartography_entries ON cartography_entries.id = cartography_entry_track_refs.entryId
        WHERE cartography_entry_track_refs.trackId = :trackId
            AND cartography_entry_track_refs.kept = 1
            AND cartography_entries.isDraft = 0
        """,
    )
    abstract suspend fun countEntriesReferencingTrack(trackId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cartography_entry_waypoint_refs
        INNER JOIN cartography_entries ON cartography_entries.id = cartography_entry_waypoint_refs.entryId
        WHERE cartography_entry_waypoint_refs.waypointId = :waypointId
            AND cartography_entry_waypoint_refs.kept = 1
            AND cartography_entries.isDraft = 0
        """,
    )
    abstract suspend fun countEntriesReferencingWaypoint(waypointId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM cartography_entry_offline_region_refs
        INNER JOIN cartography_entries ON cartography_entries.id = cartography_entry_offline_region_refs.entryId
        WHERE cartography_entry_offline_region_refs.offlineRegionId = :offlineRegionId
            AND cartography_entry_offline_region_refs.kept = 1
            AND cartography_entries.isDraft = 0
        """,
    )
    abstract suspend fun countEntriesReferencingOfflineRegion(offlineRegionId: Long): Int

    // isDraft = 0 (joined against cartography_entries): same draft-lifecycle-dispatch reasoning as
    // the three queries above — an abandoned, uncommitted draft's attached photo is not yet a real
    // reference either. No `kept = 1` here: photo refs have no kept concept (see
    // CartographyEntryPhotoRefEntity's own doc comment), attached is the whole state.
    @Query(
        """
        SELECT COUNT(*) FROM cartography_entry_photo_refs
        INNER JOIN cartography_entries ON cartography_entries.id = cartography_entry_photo_refs.entryId
        WHERE cartography_entry_photo_refs.photoId = :photoId
            AND cartography_entries.isDraft = 0
        """,
    )
    abstract suspend fun countEntriesReferencingPhoto(photoId: String): Int
}
