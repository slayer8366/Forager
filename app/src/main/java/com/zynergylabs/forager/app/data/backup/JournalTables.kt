package com.zynergylabs.forager.app.data.backup

/**
 * What the backup knows about the database's tables, because `ForagerDatabase` declares no `@ForeignKey`
 * (CLAUDE.md, Room-for-relations pitfall): the links between records are plain columns, so a restore has to
 * be told which column names which record. **Stated once, here, and guarded by a test that reads the exported
 * schema** so a new table cannot be left out unnoticed.
 *
 * A table is one of three kinds, and the kind is what a Merge does with its rows:
 * - [Kind.RECORD]: a record with its own id (`waypoints`, `tracks`, `offline_regions`, `log_photos`,
 *   `mushroom_log_entries`, `cartography_entries`). Inserted when its id is absent; an id already present is
 *   skipped and the phone's copy wins.
 * - [Kind.OWNED]: rows that belong to one record ([TableSpec.owner]). Inserted only with their owner, and only
 *   when every record they name ([TableSpec.needs]) exists on the phone afterwards; otherwise dropped, so no row
 *   dangles. `track_points` carries an auto-generated id that is a per-phone counter, so it is inserted without it.
 * - links on a RECORD ([TableSpec.softLinks]): a nullable column naming another record. Set to NULL when the
 *   target does not exist after the merge, the rule the app already applies when a target is deleted.
 */
internal object JournalTables {

    enum class Kind { RECORD, OWNED }

    /** [column] on the owning table holds the id of a record in [table] (whose key column is [targetKey]). */
    data class Reference(val column: String, val table: String, val targetKey: String = "id")

    data class TableSpec(
        val name: String,
        val kind: Kind,
        /** The primary key columns as the schema declares them. */
        val keyColumns: List<String>,
        /** `track_points.id`: an auto-generated key that is never carried across phones. */
        val autoKey: Boolean = false,
        /** OWNED only: the column naming the owning record, and that record's table. */
        val owner: Reference? = null,
        /** OWNED only: the other records a row names; each must exist on the phone or the row is dropped by a Merge. */
        val needs: List<Reference> = emptyList(),
        /** RECORD only: nullable link columns, set NULL by a Merge when their target is not on the phone. */
        val softLinks: List<Reference> = emptyList(),
    )

    /** The journal tables, records first, then what hangs off them: the order a Merge inserts in. */
    val journal: List<TableSpec> = listOf(
        TableSpec("waypoints", Kind.RECORD, listOf("id"), softLinks = listOf(Reference("trackId", "tracks"))),
        TableSpec("tracks", Kind.RECORD, listOf("id"), softLinks = listOf(Reference("originWaypointId", "waypoints"))),
        TableSpec("offline_regions", Kind.RECORD, listOf("id")),
        TableSpec("log_photos", Kind.RECORD, listOf("id")),
        TableSpec(
            "mushroom_log_entries", Kind.RECORD, listOf("id"),
            softLinks = listOf(Reference("offlineRegionId", "offline_regions"), Reference("draftOfEntryId", "mushroom_log_entries")),
        ),
        TableSpec("cartography_entries", Kind.RECORD, listOf("id")),
        // Backed up and restored (owner, "4 A"). Its id is a UUID (SavePlannedTripUseCase), not a per-phone counter, so the
        // ordinary id rule holds: on a Merge an id already present is skipped and the phone's copy wins.
        TableSpec("planned_trips", Kind.RECORD, listOf("id")),
        TableSpec("track_points", Kind.OWNED, listOf("id"), autoKey = true, owner = Reference("trackId", "tracks")),
        TableSpec(
            "log_entry_photos", Kind.OWNED, listOf("entryId", "photoId"),
            owner = Reference("entryId", "mushroom_log_entries"), needs = listOf(Reference("photoId", "log_photos")),
        ),
        // No `needs` (F3, dispatch 2026-09-28-195; owner, 2026-09-29: "Option B", "All recommended"): a track ref is kept by a
        // Merge even when its track is on neither phone, because the entry keeps its snapshot and, in
        // cartography_entry_track_paths, the line the track drew. This reverses the backup report's decision 8 for track
        // refs only; the waypoint, region and find refs below keep it. Alternative rejected: keeping `needs` and
        // exempting rows that have a saved path, which would still drop a ref whose track was deleted before F3 saved
        // paths existed, losing its snapshot for no reason (the snapshot needs nothing from the track).
        TableSpec(
            "cartography_entry_track_refs", Kind.OWNED, listOf("entryId", "trackId"),
            owner = Reference("entryId", "cartography_entries"),
        ),
        // The saved path of a kept track (F3): journal data owned by its entry, with no `needs`, so it arrives with
        // its entry whether or not the track is on the phone. Not soft-linked to `tracks`: the track is gone by design.
        TableSpec(
            "cartography_entry_track_paths", Kind.OWNED, listOf("entryId", "trackId"),
            owner = Reference("entryId", "cartography_entries"),
        ),
        TableSpec(
            "cartography_entry_waypoint_refs", Kind.OWNED, listOf("entryId", "waypointId"),
            owner = Reference("entryId", "cartography_entries"), needs = listOf(Reference("waypointId", "waypoints")),
        ),
        TableSpec(
            "cartography_entry_offline_region_refs", Kind.OWNED, listOf("entryId", "offlineRegionId"),
            owner = Reference("entryId", "cartography_entries"), needs = listOf(Reference("offlineRegionId", "offline_regions")),
        ),
        TableSpec(
            "cartography_entry_find_refs", Kind.OWNED, listOf("entryId", "findId"),
            owner = Reference("entryId", "cartography_entries"), needs = listOf(Reference("findId", "mushroom_log_entries")),
        ),
        TableSpec(
            "cartography_entry_photo_refs", Kind.OWNED, listOf("entryId", "photoId"),
            owner = Reference("entryId", "cartography_entries"), needs = listOf(Reference("photoId", "log_photos")),
        ),
    )

    /** Tables in the schema that are deliberately not journal data, and why (ruling 3 B does not list them). */
    val excluded: Map<String, String> = mapOf(
        "cached_searches" to "a rebuildable cache of network results, not journal data",
    )
}
