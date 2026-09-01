package com.forager.app.domain.model

import java.time.LocalDate

/**
 * The user's own account of one day — Journal Stage 2b, `amendment-2b-entry-definition.md`. **A new
 * entity, not [MushroomLogEntry].** A find is one mushroom's structured field record; a Cartography
 * entry is authored on top of a compiled [DerivedTrip] — what the user chose to keep from that day's
 * finds/tracks/waypoints/offline regions, plus their own writing. Nothing here is derived: unlike
 * [DerivedTrip], which is re-computed on every read and stores nothing, this is the durable, authored
 * result — "compiled input, authored output," in the amendment's own words.
 *
 * [isDraft] — "a draft is an unfinished entry" — is this entity's own draft concept, deliberately the
 * same English word as [MushroomLogEntry.isDraft]/[MushroomLogEntry.draftOfEntryId] and deliberately
 * a *different* mechanism: two independent lifecycles that happen to share a name, per the amendment's
 * explicit "coexist; do not unify them." Unlike a find's re-edit, which spawns a second, separate draft
 * row so the committed one keeps showing its last-saved values, a Cartography entry has no such
 * shadow-draft step — the dispatch never asked for one, and this entity's only stated draft/committed
 * transition is "unfinished" -> "finished" on the same row. Editing a committed entry writes to it in
 * place.
 *
 * [tags] is freeform text, no group entity — grouping is simply "entries sharing a tag string," done
 * in memory over whatever [tags] this entry (and every other) currently holds. Stored as a single
 * delimited column rather than a normalized table, per the dispatch's explicit "no tag table with its
 * own lifecycle."
 *
 * The four kept-item lists are the snapshot rule made concrete — "anything the entry displays as text
 * is snapshotted; anything it draws on a map is a reference." Each ref type carries its own display
 * text plus the id [MushroomLogEntry]/[Track]/[Waypoint]/[com.forager.app.domain.OfflineRegionMetadata]
 * would need for Stage 2c's map recall — never the full record, which the entry does not display and
 * does not own.
 */
data class CartographyEntry(
    val id: String,
    val date: LocalDate,
    val text: String,
    val tags: List<String>,
    val isDraft: Boolean,
    val updatedAtEpochMillis: Long,
    val keptFinds: List<KeptFindRef>,
    val keptTracks: List<KeptTrackRef>,
    val keptWaypoints: List<KeptWaypointRef>,
    val keptOfflineRegions: List<KeptOfflineRegionRef>,
) {
    companion object {
        /** A freshly-started, unwritten entry for [date] — every kept-item list empty, [isDraft] always `true`. Mirrors [MushroomLogEntry.draft]'s own shape: persisted immediately by its use case, not held only in memory. */
        fun draft(id: String, date: LocalDate, updatedAtEpochMillis: Long): CartographyEntry = CartographyEntry(
            id = id,
            date = date,
            text = "",
            tags = emptyList(),
            isDraft = true,
            updatedAtEpochMillis = updatedAtEpochMillis,
            keptFinds = emptyList(),
            keptTracks = emptyList(),
            keptWaypoints = emptyList(),
            keptOfflineRegions = emptyList(),
        )
    }
}

/** A kept find's own display text — [MushroomLogEntry] stays the source of truth; this is enough for the entry to read sensibly if that find is later deleted from Records. */
data class KeptFindRef(
    val findId: String,
    val foundOn: LocalDate,
    val ownIdentification: String?,
    val hasPhotos: Boolean,
)

/** A kept track's own display text — see [TrackStatistics] for where [distanceMeters]/[durationMillis]/[pointCount] are computed from at snapshot time. */
data class KeptTrackRef(
    val trackId: String,
    val name: String?,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointCount: Int,
)

/** A kept waypoint's own display text. */
data class KeptWaypointRef(
    val waypointId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

/** A kept offline region's own display text — [radiusKm] is the region's search-radius parameter, not a live tile count (see [com.forager.app.domain.OfflineRegionMetadata], which this is snapshotted from). */
data class KeptOfflineRegionRef(
    val offlineRegionId: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
)
