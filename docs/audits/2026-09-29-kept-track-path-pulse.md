# Kept track path pulse (read-only, at `c43e2e9d`)

**Date:** 2026-09-29.

**Recorded by:** the planner, from a read-only pulse's hand-back. It is condensed, with the citations kept.

**What was read:** `origin/journal-redesign` at `c43e2e9d`, and F1's wip branch at `9d794552`. Nothing was built or run. Paths are shortened to `…/app/…` = `app/src/main/java/com/zynergylabs/forager/app/`.

## Findings
1. **How an entry keeps a track.**
   - There are five ref tables keyed (entryId, recordId), with no @ForeignKey (`…/data/local/CartographyEntryEntity.kt:73-175`).
   - A track snapshot holds name, distance, duration, point count and kept. **It holds no path** (`:82-90`).
   - Snapshots are written:
     - at entry start, when every one of the day's tracks is auto-kept (`CartographyViewModel.kt:146-157`);
     - on a decision (`:324-331`);
     - with their figures recomputed on every open while the track exists (`:203-233`).
   - Saving replaces all five ref tables on every draft change (`CartographyEntryDao.kt:91-111`).
   - **The entry map draws kept tracks only from live points** (`GetCartographyEntryMapDataUseCase.kt:51-72`). A deleted track draws nothing.
   - The card thumbnail and the tapped-track bubble also read live tracks (`CartographyEntryCard.kt:334-346`; `MapBubbles.kt:258`). Waypoints already have a snapshot fallback there (`:144-149`); tracks have none.
2. **Track points** are stored per point in `track_points` (`TrackPointEntity.kt:31-42`).
   - The read-seam filter drops network fixes in `RoomTrackRepository.toDomain` "and nowhere else" (`:87-100`).
   - A delete removes the points and then the row, in one transaction (`TrackDao.kt:70-74`).
   - No per-entry copy of points exists anywhere.
3. **The database is at version 16** (`ForagerDatabase.kt:167`). Migrations run 3→4 through 15→16, and `openForRestore` has no destructive fallback.
   - Every one of the 134 origin refs was checked for version 17 or later, or for a MIGRATION_16_*: **none has one.** origin/main is at 15.
   - The V12-V15 fixtures declare entity classes directly, so a new column on an existing entity needs a table rebuild (`Migrations.kt:942-947`). A new entity does not.
4. **Backup.** `cartography_entry_track_refs` has `needs = tracks` (`JournalTables.kt:60-63`).
   - Replace copies every row verbatim.
   - Merge drops an owned row whose needed record is missing (`RoomJournalBackup.kt:361-384`), so **a kept snapshot of a deleted track is lost by a Merge.** That is decision 8 of the backup report, pinned by `danglingRows` (`JournalBackupTest.kt:726-737`).
5. **F1's track delete** (at wip 9d794552, uncompiled) leaves refs and snapshots in place and draws no line. Its tests pin "draws no line", which Option B inverts.

## Options for storing the path
All three need `MIGRATION_16_17`, a reader in the same change, and no @ForeignKey.

- **O1: a new table with one row per point.** It costs about 170-200 bytes a point.
- **O2: a new table with one row per ref,** (entryId, trackId, path), with the path encoded. It costs about 16 bytes a point. It is untouched by draft saves and list loads.
- **O3: a path column on the ref table.** It needs a table rebuild, and it would be loaded for every entry and rewritten on every keystroke.

**When to copy:**
- **At keep-time:** it doubles storage, can copy a track still recording, and needs a SQL backfill that would restate the read-seam rule.
- **At delete-time,** inside `DeleteTrackUseCase` before the delete: it stores only what is needed, and needs no backfill, because no track has ever been deletable.

**The pulse's recommendation: O2, with a delete-time copy.**
- The table is `cartography_entry_track_paths(entryId, trackId, path)`.
- It stores the read-seam-filtered lat/lng in time order.
- It is written for every ref row naming the track.
- The reader uses the live track, else the stored path.

## Decisions it raised
- (a) Merge must stop dropping track refs whose track is missing; otherwise Option B is lost on every Merge.
- (b) Whether the card thumbnail and the tapped-track bubble also use the path.
- (c) Whether drafts get it too.
- (d) Sequencing against F1.
- (e) The delete-data wording.

Also noted: finds too draw live and vanish on delete. That is out of scope.

## Could not determine
- real point counts;
- SQLite byte sizes;
- whether F1's wip compiles;
- whether the edit screen shows a toggle for a decided track whose record is gone.
