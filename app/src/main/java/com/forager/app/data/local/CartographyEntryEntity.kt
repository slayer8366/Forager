package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Journal Stage 2b's authored entity — see [com.forager.app.domain.model.CartographyEntry]'s own doc
 * comment for why this is a separate table family from [MushroomLogEntryEntity], not an extension of
 * it. [tags] is a single delimited column (see [com.forager.app.data.repository.RoomCartographyEntryRepository]
 * for the exact encoding) — no normalized tag table, per the dispatch's explicit "no tag table with
 * its own lifecycle."
 *
 * [date] is indexed for the trip-report flow's own "does a draft already exist for this day" lookup;
 * [isDraft] is indexed since the Entries/Drafts submenus each query one partition of this table
 * exclusively — the same "indexed queries, not in-memory filtering" standing preference `MIGRATION_9_10`
 * established for [MushroomLogEntryEntity.foundOn] and the other day-scoped columns.
 */
@Entity(
    tableName = "cartography_entries",
    indices = [Index(value = ["date"]), Index(value = ["isDraft"])],
)
data class CartographyEntryEntity(
    @PrimaryKey val id: String,
    /** `LocalDayRange.foundOnKey` format (`yyyy-MM-dd`) — the day this entry is about, not when it was last edited. */
    val date: String,
    val text: String,
    /** Freeform tags joined with [TAG_DELIMITER] — see the repository's `toEntity`/`toDomain` for the split/join. */
    val tags: String,
    val isDraft: Boolean,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val TAG_DELIMITER: String = "␟"
    }
}

/**
 * One kept track's snapshot — the "anything the entry displays as text is snapshotted" rule made
 * concrete for [com.forager.app.domain.model.Track]. Composite primary key on `(entryId, trackId)`:
 * an entry keeps a given track at most once, and `entryId` being the key's leading column already
 * gives "every ref row for this entry" a usable index without a separate `@Index` — see
 * [com.forager.app.data.local.LogEntryPhotoCrossRef]'s identical `(entryId, photoId)` precedent. The
 * separate `@Index` on [trackId] is what Records' 4b deletion warning queries against ("does any
 * entry keep this track") — the reverse direction the composite key alone doesn't serve.
 *
 * **Zero `@ForeignKey`, by explicit standing rule** — see [MushroomLogEntryEntity.offlineRegionId]'s
 * own doc comment for the rationale this follows: nothing here may change as a side effect of
 * something happening to the referenced track, and any FK action would do exactly that.
 */
@Entity(
    tableName = "cartography_entry_track_refs",
    primaryKeys = ["entryId", "trackId"],
    indices = [Index(value = ["trackId"])],
)
data class CartographyEntryTrackRefEntity(
    val entryId: String,
    val trackId: String,
    val name: String?,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointCount: Int,
)

/** One kept waypoint's snapshot — see [CartographyEntryTrackRefEntity]'s doc comment for the shape and reasoning this mirrors. */
@Entity(
    tableName = "cartography_entry_waypoint_refs",
    primaryKeys = ["entryId", "waypointId"],
    indices = [Index(value = ["waypointId"])],
)
data class CartographyEntryWaypointRefEntity(
    val entryId: String,
    val waypointId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

/** One kept offline region's snapshot — see [CartographyEntryTrackRefEntity]'s doc comment for the shape and reasoning this mirrors. */
@Entity(
    tableName = "cartography_entry_offline_region_refs",
    primaryKeys = ["entryId", "offlineRegionId"],
    indices = [Index(value = ["offlineRegionId"])],
)
data class CartographyEntryOfflineRegionRefEntity(
    val entryId: String,
    val offlineRegionId: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
)

/**
 * One kept find's snapshot — see [CartographyEntryTrackRefEntity]'s doc comment for the shape and
 * reasoning this mirrors. Unlike the other three ref types, 4b's deletion warning (dispatch section
 * 4b, unchanged by either amendment) names only track/waypoint/offline-region, so no warning is wired
 * to find deletion in `RecordsTab`'s Finds submenu — the [findId] index still exists here for the same
 * "does any entry keep this find" query, kept for symmetry and available to a future dispatch that
 * extends 4b to finds, but nothing in 2b calls it yet. See the disclosure report for this gap.
 */
@Entity(
    tableName = "cartography_entry_find_refs",
    primaryKeys = ["entryId", "findId"],
    indices = [Index(value = ["findId"])],
)
data class CartographyEntryFindRefEntity(
    val entryId: String,
    val findId: String,
    val foundOn: String,
    val ownIdentification: String?,
    val hasPhotos: Boolean,
)

/**
 * One manually-attached standalone photo — `amendment-2b-optional-writing.md`: "standalone photos,
 * attached manually, are what make a wordless entry possible... treat photo attachment as
 * load-bearing for the entry surface."
 *
 * **Not a pure reference, on reconsideration.** A photo is neither text nor something drawn on a
 * map, so the snapshot rule's own two cases don't name it directly — but a wordless entry can
 * consist mostly of attached photos, so a bare `(entryId, photoId)` row would let deleting a
 * [com.forager.app.domain.model.GalleryPhoto] silently gut such an entry with no explanation, exactly
 * the failure the snapshot rule exists to prevent. [attachedAtEpochMillis] — when the user attached
 * it, not [com.forager.app.domain.model.LogPhoto.createdAtEpochMillis], which can be `null` for a
 * migrated photo — is the minimum that lets the entry say "a photo was attached here on this date, no
 * longer available" rather than a dangling id that resolves to nothing. Same composite-key shape as
 * [com.forager.app.data.local.LogEntryPhotoCrossRef] (`log_entry_photos`), the existing many-to-many
 * entry-photo precedent this mirrors, just for [CartographyEntryEntity] instead of
 * [MushroomLogEntryEntity] and with this one snapshot column that precedent doesn't carry.
 *
 * Also behind 4b's deletion warning now, alongside track/waypoint/offline-region — see
 * [com.forager.app.domain.CartographyEntryRepository.countEntriesReferencingPhoto] and
 * [com.forager.app.ui.log.PhotoGalleryScreen]'s own confirm dialog.
 */
@Entity(
    tableName = "cartography_entry_photo_refs",
    primaryKeys = ["entryId", "photoId"],
    indices = [Index(value = ["photoId"])],
)
data class CartographyEntryPhotoRefEntity(
    val entryId: String,
    val photoId: String,
    val attachedAtEpochMillis: Long,
)
