package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one [com.forager.app.domain.model.MushroomLogEntry]. Room annotations
 * are data-layer only — never `domain/`, per CLAUDE.md — so this type, [LogPhotoEntity],
 * [MushroomLogDao] and `RoomMushroomLogRepository` stay in `data/`, and that repository is the only
 * place this ever meets the domain type.
 *
 * ## Real columns, not a blob
 *
 * Every enum-shaped field gets its own column rather than one serialized blob for the whole entry
 * (the way [CachedSearchEntity.entriesJson] holds a variable-length list that genuinely doesn't fit
 * columns) — per the plan's own call: a blob cannot be filtered or counted, and this table is field
 * notes someone may eventually want to query ("every entry with a volva", "entries with a chocolate
 * spore print").
 *
 * ## Encoding [com.forager.app.domain.model.Observed] and [com.forager.app.domain.model.Feature] as columns
 *
 * - An `Observed<SomeEnum>` field (e.g. [capShape]) is one nullable `TEXT` column holding the
 *   enum's `name`: `null` means [com.forager.app.domain.model.Observed.NotObserved], any other
 *   value means `Recorded` with that enum constant. This works because there is no other reason for
 *   the column to be null — unlike [Feature], [Observed] has no independent "explicitly absent"
 *   state that null could be confused with.
 * - A `Feature<T>` field (e.g. [annulusState]/[annulusValue]) needs two columns because it has
 *   three states, not two: a `NOT NULL` `*State` discriminator (`PRESENT`/`ABSENT`/`NOT_OBSERVED`)
 *   plus a nullable `*Value` column that only means anything when the state is `PRESENT`.
 * - A sealed choice wrapped in `Observed` (hymenophore, stipe, host association, spore print
 *   colour) uses a nullable `*Kind` discriminator the same way a bare `Observed<Enum>` uses its one
 *   column — null is [com.forager.app.domain.model.Observed.NotObserved] — with the variant's own
 *   sub-fields in their own columns, populated only when `*Kind` selects that variant. Which
 *   sub-fields apply for which `*Kind` is enforced in code
 *   (`RoomMushroomLogRepository.toDomain()`/`.toEntity()`), not by the schema — Room/SQLite has no
 *   way to express "these columns are only valid together" — so those two functions are the only
 *   place a corrupt combination (e.g. a `GILLS` kind with a `stipeBase` set) could be produced or
 *   silently accepted, and they don't: `toDomain()` reads only the sub-fields that apply to the
 *   `*Kind` it dispatches on.
 */
@Entity(tableName = "mushroom_log_entries", indices = [Index("offlineRegionId")])
data class MushroomLogEntryEntity(
    @PrimaryKey val id: String,
    /** `null` together with [lng] exactly when [com.forager.app.domain.model.MushroomLogEntry.foundAt] is `null` — see that field's own doc comment. Nullable as of [MIGRATION_6_7]. */
    val lat: Double?,
    val lng: Double?,
    /** ISO-8601 (`yyyy-MM-dd`) — see [PlannedTripEntity.date]. */
    val foundOn: String,
    val entryNotes: String,
    val ownIdentification: String?,

    // --- Sync state (LogSyncState) — see that type's doc comment: only DRAFT is ever written by
    // this codebase today, but the columns exist from the start per the project owner's decision
    // not to retrofit sync state later.
    /** DRAFT / UPLOADING / UPLOADED / FAILED. */
    val syncStateKind: String,
    val syncProgress: Float?,
    val syncRemoteObservationId: String?,
    val syncUploadedAtEpochMillis: Long?,
    val syncFailureReason: String?,

    // --- Cap (CapSection)
    val capShape: String?,
    val capSurface: String?,
    /** PRESENT / ABSENT / NOT_OBSERVED. */
    val capDecorationsState: String,
    /** Comma-joined [com.forager.app.domain.model.CapDecoration] names; meaningful only when [capDecorationsState] is PRESENT. */
    val capDecorationsValue: String?,
    val capMargin: String?,
    val capNotes: String,

    // --- Hymenophore (HymenophoreSection)
    /** null = NotObserved; else GILLS / PORES / TEETH / SMOOTH_OR_WRINKLED. */
    val hymenophoreKind: String?,
    /** Meaningful only when [hymenophoreKind] is GILLS. */
    val gillAttachment: String?,
    val gillSpacing: String?,
    val gillEdge: String?,
    val hymenophoreNotes: String,

    // --- Stipe (StipeSection)
    /** null = NotObserved; else ABSENT / PRESENT. */
    val stipeKind: String?,
    /** Meaningful only when [stipeKind] is PRESENT. */
    val stipePosition: String?,
    val stipeInterior: String?,
    val stipeBase: String?,
    val stipeNotes: String,

    // --- Veil remnants (VeilSection)
    /** PRESENT / ABSENT / NOT_OBSERVED. */
    val annulusState: String,
    val annulusValue: String?,
    val volvaState: String,
    val volvaValue: String?,
    val veilNotes: String,

    // --- Context / flesh (ContextFleshSection)
    val fleshTexture: String?,
    val colorChangeState: String,
    val colorChangeValue: String?,
    val exudateState: String,
    val exudateValue: String?,
    val contextFleshNotes: String,

    // --- Spore print (SporePrintSection)
    /** null = NotObserved; else WHITE / CREAM / PINK_SALMON / OCHRE / RUST / CHOCOLATE_BROWN / PURPLE_BROWN / BLACK / OTHER. */
    val sporePrintColorKind: String?,
    /** Meaningful only when [sporePrintColorKind] is OTHER. */
    val sporePrintOtherText: String?,
    /** ISO-8601; non-null exactly when [sporePrintColorKind] is non-null. */
    val sporePrintReadOn: String?,
    val sporePrintNotes: String,

    // --- Host & substrate (HostSubstrateSection)
    /** null = NotObserved; else MYCORRHIZAL / DEAD_WOOD / SOIL_OR_LITTER / DUNG / OTHER. */
    val associationKind: String?,
    /** Meaningful only when [associationKind] is MYCORRHIZAL or DEAD_WOOD. */
    val associationHostSpecies: String?,
    /** Meaningful only when [associationKind] is OTHER. */
    val associationOtherText: String?,
    val forestType: String?,
    val hostHealth: String?,
    val hostSubstrateNotes: String,

    // --- Offline region reference (Workstream A, docs/plans/pr26-rework.md)
    /**
     * The [OfflineRegionEntity] this entry's tile capture belongs to, if any — `null` until
     * Workstream B's capture mechanism sets it, and left `null` permanently for an entry that
     * never gets one (e.g. logged offline and never re-synced).
     *
     * **Deliberately an indexed column, not a `@ForeignKey`** (owner decision, 2026-08-22) —
     * matching this database's only other precedent for a cross-table reference,
     * `track_points.trackId` in [MIGRATION_4_5]. Nothing about a log entry may change as a side
     * effect of something happening to a region it references: a mushroom log entry is removed
     * only by direct deletion from the log itself. `SET_NULL` would have the database edit an
     * entry as a side effect of a region delete; `RESTRICT` would throw where the app wants to
     * show a dialog instead. Leaving this column unconstrained means a region delete can proceed
     * (or be handled entirely in app code, per Workstream C) without SQLite ever touching this
     * table — a dangling id here is accepted and expected, not a corruption to guard against.
     */
    val offlineRegionId: Long? = null,

    /**
     * Persisted-uncommitted state — owner decision, 2026-08-22 (Workstream L4b): "a draft is an
     * entry row marked uncommitted," a discriminator column on this table rather than a second
     * table or a change-list. **Unrelated to [com.forager.app.domain.model.LogSyncState.Draft]**,
     * which is an iNaturalist upload-sync state — this column is about whether the row itself has
     * been committed to the log at all, independent of sync. `true` while an edit session is live
     * (autosaved on every field change, same cadence as before this column existed) or while a
     * crash left one orphaned; `false` once Save, an incidental exit, or a pre-[MIGRATION_8_9] row
     * commits it. See [MushroomLogViewModel]'s own doc comment for the full state machine.
     */
    val isDraft: Boolean,
)
