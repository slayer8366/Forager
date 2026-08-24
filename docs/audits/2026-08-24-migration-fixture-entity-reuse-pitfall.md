# Migration test fixtures: production-entity reuse breaks on altered (not just added) entities

Found while implementing `MIGRATION_5_6` (Workstream A, PR #26 rework). Structural, not
specific to this migration — will recur for any future migration that changes an
*existing* entity, in whichever legacy fixture predates that change.

## The pattern, and why it's normally correct

`MushroomLogMigrationTest`, `TrackWaypointMigrationTest`, and `OfflineRegionMigrationTest`
each declare a `LegacyForagerDatabaseVn` — a `@Database` frozen at an old version, built
from the **same production entity/DAO classes** the real app used at that version, not a
hand-copied schema. That's deliberate: a hand-copied fixture schema can silently drift
from what a real install actually wrote, and the test would stop meaning anything without
anyone noticing. Sharing the class is what keeps the fixture honest.

## Where it breaks

Room generates a fixture's `CREATE TABLE` from the entity class's **current** fields, not
from any historical snapshot. Every migration before `MIGRATION_5_6` only ever added
brand-new tables (`MIGRATION_3_4`: `mushroom_log_entries`/`log_photos`; `MIGRATION_4_5`:
`tracks`/`track_points`/`waypoints`), so a legacy fixture predating one of those additions
simply didn't include that entity in its `entities = [...]` list — no conflict possible.

`MIGRATION_5_6` is the first migration in this codebase to alter an *already-existing*
entity: it adds `offlineRegionId` (plus an index) to `MushroomLogEntryEntity`, which
`LegacyForagerDatabaseV4` (version 4) and `LegacyForagerDatabaseV5` (version 5) both
already include in their entity lists, because `mushroom_log_entries` existed at those
versions too. Once `offlineRegionId` landed on the shared production class, Room started
generating *both* legacy fixtures' tables with the column (and its index) already present
— a real version-4 or version-5 install never had it. When the test then reopened that
file as the real `ForagerDatabase` and ran `MIGRATION_5_6`, its own
`ALTER TABLE ... ADD COLUMN offlineRegionId` failed with `duplicate column name`.

## The fix

In both `TrackWaypointMigrationTest.kt` and `OfflineRegionMigrationTest.kt`, immediately
after seeding through the legacy DB and before closing it:

```kotlin
legacyDb.openHelper.writableDatabase.execSQL("DROP INDEX `index_mushroom_log_entries_offlineRegionId`")
legacyDb.openHelper.writableDatabase.execSQL("ALTER TABLE `mushroom_log_entries` DROP COLUMN `offlineRegionId`")
```

**Order matters.** SQLite validates an index against the table's current column set on
every subsequent statement, so dropping the column while the index still references it
fails with `no such column` instead of succeeding. The index has to go first.

This restores the fixture to its true pre-migration shape before handing the file to the
real migration path, rather than either (a) making `MIGRATION_5_6` tolerate a pre-existing
column — which would mask a real bug and violates this repo's "no speculative correction
logic" rule — or (b) hand-copying a frozen schema for the fixture, which reintroduces the
drift risk the shared-class pattern exists to prevent, and for `MushroomLogEntryEntity`
specifically would mean reimplementing `RoomMushroomLogRepository.toEntity()`'s enum
mapping just to produce seed values.

Both sites carry this reasoning in their own doc comments (see the `DROP INDEX`/
`DROP COLUMN` lines in each file) — this audit exists so the *general* shape of the
problem has a home independent of either instance, for whoever hits it next.

## What a future migration author needs to do

Any migration that adds a column (or otherwise alters the shape) of an entity that
**already existed** at an earlier fixture's frozen version needs the identical
drop-index([es])-then-drop-column(s) treatment added to every legacy fixture whose version
predates the change — right after seeding, before closing that legacy `RoomDatabase`. A
fixture that only ever *adds new entities* at each version needs no such treatment, since
a not-yet-existing entity was never in that fixture's `entities = [...]` list to begin
with. `MushroomLogMigrationTest`'s `LegacyForagerDatabaseV3` is the standing example: it
never included `MushroomLogEntryEntity` at all, so it needed nothing when the entity later
gained `offlineRegionId` — the bug only reaches a fixture that already declares the
entity being changed.

If a future entity alteration reuses a column/index name already documented here, or the
pattern otherwise recurs, treat this file as the reference rather than re-deriving it from
scratch — and if the fix shape changes, write a new dated audit rather than editing this
one, per this directory's own convention.
