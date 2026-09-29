package com.zynergylabs.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Journal Stage 2b's authored entity — see [com.zynergylabs.forager.app.domain.model.CartographyEntry]'s own doc
 * comment for why this is a separate table family from [MushroomLogEntryEntity], not an extension of
 * it. [tags] is a single delimited column (see [com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository]
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
    /**
     * J8: whether the entry's kept records are highlighted on the Maps tab — see
     * [com.zynergylabs.forager.app.domain.model.CartographyEntry.shownOnMap]. Added by `MIGRATION_15_16`,
     * which writes `0` for every existing row; not indexed, since it is read with the rows the Entries
     * feed already loads, never queried on its own. No SQL default, like [isDraft] (`MIGRATION_8_9`):
     * every write goes through this entity, which always carries it.
     */
    val shownOnMap: Boolean,
) {
    companion object {
        const val TAG_DELIMITER: String = "␟"
    }
}

/**
 * One track **decision** (kept or withheld) and its snapshot — the "anything the entry displays as
 * text is snapshotted" rule made concrete for [com.zynergylabs.forager.app.domain.model.Track]. Composite primary
 * key on `(entryId, trackId)`: an entry decides on a given track at most once, and `entryId` being the
 * key's leading column already gives "every ref row for this entry" a usable index without a separate
 * `@Index` — see [com.zynergylabs.forager.app.data.local.LogEntryPhotoCrossRef]'s identical `(entryId, photoId)`
 * precedent. The separate `@Index` on [trackId] is what Records' 4b deletion warning queries against
 * ("does any entry *keep* this track") — the reverse direction the composite key alone doesn't serve.
 *
 * **Row presence, not [kept], is what distinguishes "decided" from "not yet decided."** A row here
 * means the user has made a call on this track for this entry, one way or the other; no row for a
 * given `(entryId, trackId)` means the candidate is new since the last decision was made (or the entry
 * was created) and is offered, not shown as included — dispatch follow-up point 2. Withheld rows carry
 * the exact same snapshot columns kept rows do: an entry must never silently change what it shows on
 * reopen, and a withheld candidate later removed from Records still needs its own name/distance/etc.
 * to render as "withheld" rather than a dangling id.
 *
 * **One deliberate exception to "never silently change on reopen" (timestamp-filter dispatch, owner
 * decision):** `distanceMeters`, `durationMillis` and `pointCount` are recomputed from the track on
 * every open (`CartographyViewModel.onOpenEntry`) and written back when they differ. They cache a
 * computation, not a choice the user made, and the cached number was wrong once the read seam
 * started excluding network-provider fixes — showing a stale sum beside a fresh one would put two
 * disagreeing numbers on screen on purpose. The rule stands for everything authored: `name`, `kept`,
 * and the row's existence are never touched by this. A row whose track has since been deleted has
 * nothing to recompute from and keeps its figure. `pointCount`'s first reader is the "no usable
 * points" row suffix this same dispatch added.
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
    val kept: Boolean,
)

/** One waypoint decision and its snapshot — see [CartographyEntryTrackRefEntity]'s doc comment for the shape and reasoning this mirrors. */
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
    val kept: Boolean,
)

/** One offline-region decision and its snapshot — see [CartographyEntryTrackRefEntity]'s doc comment for the shape and reasoning this mirrors. */
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
    val kept: Boolean,
)

/**
 * One find decision and its snapshot — see [CartographyEntryTrackRefEntity]'s doc comment for the
 * shape and reasoning this mirrors. Unlike the other three ref types, 4b's deletion warning (dispatch
 * section 4b, unchanged by either amendment) names only track/waypoint/offline-region, so no warning
 * is wired to find deletion in `RecordsTab`'s Finds submenu — the [findId] index still exists here for
 * the same "does any entry keep this find" query, kept for symmetry and available to a future dispatch
 * that extends 4b to finds, but nothing in 2b calls it yet. See the disclosure report for this gap.
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
    val kept: Boolean,
)

/**
 * One manually-attached standalone photo — `amendment-2b-optional-writing.md`: "standalone photos,
 * attached manually, are what make a wordless entry possible... treat photo attachment as
 * load-bearing for the entry surface."
 *
 * **Not a pure reference, on reconsideration.** A photo is neither text nor something drawn on a
 * map, so the snapshot rule's own two cases don't name it directly — but a wordless entry can
 * consist mostly of attached photos, so a bare `(entryId, photoId)` row would let deleting a
 * [com.zynergylabs.forager.app.domain.model.GalleryPhoto] silently gut such an entry with no explanation, exactly
 * the failure the snapshot rule exists to prevent. [attachedAtEpochMillis] — when the user attached
 * it, not [com.zynergylabs.forager.app.domain.model.LogPhoto.createdAtEpochMillis], which can be `null` for a
 * migrated photo — is the minimum that lets the entry say "a photo was attached here on this date, no
 * longer available" rather than a dangling id that resolves to nothing. Same composite-key shape as
 * [com.zynergylabs.forager.app.data.local.LogEntryPhotoCrossRef] (`log_entry_photos`), the existing many-to-many
 * entry-photo precedent this mirrors, just for [CartographyEntryEntity] instead of
 * [MushroomLogEntryEntity] and with this one snapshot column that precedent doesn't carry.
 *
 * Also behind 4b's deletion warning now, alongside track/waypoint/offline-region — see
 * [com.zynergylabs.forager.app.domain.CartographyEntryRepository.countEntriesReferencingPhoto] and
 * a photo delete's warning (first the removed Photo Gallery screen's confirm dialog, now the album's Undo snackbar).
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

/**
 * The saved path of a track an entry kept, written when the track is deleted (F3, dispatch
 * 2026-09-28-195; owner, 2026-09-29: "Option B", "All recommended"). One row per (entry, track) ref
 * row, so it is untouched by draft saves and by list loads (the pulse's option O2,
 * `docs/audits/2026-09-29-kept-track-path-pulse.md`). [path] is [com.zynergylabs.forager.app.domain.TrackPathCodec]'s
 * bytes: the track's read-seam-filtered lat/lng in timestamp order.
 *
 * **Written only when the track is deleted** ([com.zynergylabs.forager.app.domain.DeleteTrackUseCase]), for every
 * ref row naming it; a row therefore means "the track this entry kept is gone, and this is the line it drew". No
 * backfill was needed: no track had ever been deletable before F1. While the track exists the entry draws it live.
 *
 * **Zero `@ForeignKey`**, for the reason [CartographyEntryTrackRefEntity] records. The row is deleted with its
 * entry ([CartographyEntryDao.deleteEntryAndRefs]) and by nothing else: a saved path is not cleaned up when a save
 * drops the ref it belonged to, and no path is written for a ref that is added after its track was deleted
 * (a decision the edit screen cannot add for a track that is not in Records).
 */
@Entity(
    tableName = "cartography_entry_track_paths",
    primaryKeys = ["entryId", "trackId"],
    indices = [Index(value = ["trackId"])],
)
data class CartographyEntryTrackPathEntity(
    val entryId: String,
    val trackId: String,
    val path: ByteArray,
)
