# Migrations asserted against the committed schemas: completion report

Dispatch: `dispatch-wire-migration-tests.md` (planner: Claude, no repo access; executor: this
session). Date: 2026-09-13. Branch: `claude/new-session-vto65i`, the head of open PR #102, at the
owner's instruction ("Redo this on PR 102 instead"). A first attempt on `claude/ios-port-feasibility-mvsjcr`
(PR #101) was reverted whole by a revert commit at the owner's instruction before this one began.

**No production code change.** The diff against the dispatch base is three files: one catalog
entry, one `testImplementation` line, and one test class. Nothing is pre-authorized to merge.

---

## §0 Base

- `git log --oneline -1 origin/main`: `175b050 Merge pull request #100 from slayer8366/claude/new-session-pd5wfd`.
- `git fetch --all` ran before this report; `git branch -r` lists 55 refs (54 branches plus `origin/HEAD`).
- Branch: `claude/new-session-vto65i`. Dispatch base on it: `5aee450`, the head of PR #102 when this
  dispatch began here. Every claim below marked "at `5aee450`" was checked against that commit.
- PR #102: **open, not merged**, base `main` at `175b050`, read through the GitHub API during this
  session. Its `mergeable_state` read `unstable` because CI was running on the commit just pushed;
  that is not a conflict.
- Commits this dispatch added to the branch, oldest first: `294a5ef`, `dbbc4bb`, `ae3a259` (the gate
  probe, three rounds, kept in history under push-before-you-tidy), `cd48a9e` (the suite, probe
  deleted), `512403a` (two scaffolding fixes), `e307f81` (every column seeded and compared).

## §1 The premises, re-derived at `5aee450`

| Premise | Result | How |
|---|---|---|
| Twelve schema files, 4 through 15 | **Held.** | `ls app/schemas/com.zynergylabs.forager.app.data.local.ForagerDatabase/` lists `4.json` to `15.json`, twelve files, no `3.json`. |
| `MigrationTestHelper` appears in zero files | **Held.** | `git grep -l MigrationTestHelper 5aee450` finds 0 files. Positive controls on the same commit: `LegacyForagerDatabaseV` finds 21 files, `Room.databaseBuilder` finds 15. `room-testing` / `room.testing` finds 0, so the artifact was on no classpath either. |
| Nothing reads the schema files | **Held for tests; the build writes them.** | Room's own reader is the helper above, and it is absent. Whether anything else opens that directory was not searched for separately; nothing in `app/src/test` at `5aee450` names it, by the grep above. |
| Eleven hand-written fixture tests | **Held as a count of files.** | Eleven `*MigrationTest.kt` files at `5aee450` each declare a `LegacyForagerDatabaseVn` (V3, V4, V5, V6, V7, V8, V9, V10, V12, V13, V14; no V11). They hold thirteen `@Test` methods between them; `CartographyEntryMigrationTest` has two, one of which is about the 4b deletion warning rather than the migration. `ForagerDatabaseDestructiveFallbackTest` declares a twelfth fixture, V2, and is not a migration test. |
| The registered chain is unbroken | **Held.** | `ForagerDatabase.kt:188-191` registers `MIGRATION_3_4` through `MIGRATION_14_15`, twelve contiguous steps, all defined in `Migrations.kt`. |
| `allowBackup="false"` stands | **Held.** | `AndroidManifest.xml:122`, with the standing-decision comment at `:67`. |
| No export/import exists | **Held for the database.** | A GPX track export exists (`GpxCodec.kt`, `TrackRepository.kt`); it is not a copy of the database and cannot restore one. No database export or import path was found. |
| No tester has run a migration | **Could not be determined here.** | Room creates a fresh install at the current version, 15, so a fresh install runs none; whether any installed device predates 15 is not visible from a repository. |

Nothing had been wired since the dispatch was written, so the dispatch's stop condition ("if it has
since been wired, say so and stop") did not fire.

## §2 Can `MigrationTestHelper` run here at all

**Yes, under Robolectric in `test`, on one condition that the shipped setup does not meet: the
schema files must be reachable through the app's `AssetManager`.** Established by a probe class
that lived on this branch for three commits and was deleted once the answer was recorded.

- **Artifact:** `androidx.room:room-testing`, added at the catalog's pinned `room` version (2.8.4) as
  `androidx-room-testing` in `gradle/libs.versions.toml` and as `testImplementation` in
  `app/build.gradle.kts`. It was on no classpath before (see §1).
- **`androidTest`:** not needed, and this project has none: `app/src` holds `main` and `test` only.
  There is no instrumented test infrastructure to fall back to.
- **CI:** `ci.yml` runs `testDebugUnitTest` on every pull request, so the new class runs there with
  everything else. **Run 34746417192 on `512403a` completed green** (read after the first draft of
  this report), so the class runs and passes on GitHub Actions with the id and 7→8 fixes in; run
  34746709707 on `e307f81`, the every-column version, was still in progress when this was pushed.
- **Not a fourth blind spot.** Four one-line reverts of migration code each failed the suite on a
  message naming that edit (§3, "Proof the suite bites").

**The probe results, as the JUnit XML carried them** (commits `294a5ef`, `dbbc4bb`, `ae3a259`):

1. The application context and the Instrumentation context under Robolectric list the same 16
   root asset entries and are one `AssetManager` (`sameAssetManager=true`). So the helper's own
   lookup, which tries the Instrumentation context and falls back to the target context, sees
   whatever the app's assets hold and nothing more.
2. The helper as shipped: `FileNotFoundException: Cannot find the schema file in the assets
   folder`. AGP merges no assets into a unit-test run; a `sourceSets.test.assets` entry, tried in
   the reverted first attempt, was inert.
3. `AssetManager.addAssetPath("<repo>/app/schemas")`: cookie 0, the directory is refused.
4. A zip of the schema files built at test time, added the same way: cookie 3. With the entries at
   the zip root, `4.json` was still not found; with the entries under an `assets/` prefix, `4.json`
   read back (first 8 bytes checked).
5. With that zip installed, `helper.createDatabase(name, 4)` built version 4 from `4.json`. My
   hand-typed seed row then failed on a column `foundOnEpochDay` that version 4 never had (it has
   `foundOn`), which is why the suite generates every seed row from the schema JSON rather than
   from anyone's memory of a version.

The route is `SchemaAssets` in the test class: zip `app/schemas/**/*.json` under `assets/`, call
`addAssetPath` by reflection, and fail the test loudly if the cookie is 0. Nothing ships.

## §3 What the tests do, and every result

`app/src/test/java/com/zynergylabs/forager/app/data/local/SchemaMigrationTest.kt`, twelve tests.

For each migration 4→5 through 14→15, `migrate(from, to, migration, …)`:

1. `helper.createDatabase("m$from.db", from)` builds version N **from `N.json`** (`:141`).
2. One row per table at N, **every column filled** with a value of its declared affinity
   (INTEGER 1, REAL 1.5, TEXT `"<column>-1"`, a TEXT `id` as `"<table>-1"`), nullable columns
   included, with per-test overrides for the values a test wants to watch. Generated from `N.json`.
3. `helper.runMigrationsAndValidate("m$from.db", to, true, migration)` (`:142`) runs the migration
   and has Room validate the result against `N+1.json`. **This is invoked, not assumed:** revert
   probe A below fails inside it with Room's own `Migration didn't properly handle` message.
4. Every table seeded at N still has its row at N+1, and **every column present at both N and N+1
   reads back exactly what was seeded**, compared by name (`assertEverySeededValueSurvived`).
   Tables new at N+1 must be empty unless the test declares them filled (7→8 fills
   `log_entry_photos`, one row per old `log_photos.entryId`). Then the test's own assertions on the
   columns the migration adds or transforms.

A thirteenth test runs the whole chain 4→15 with `ALL_MIGRATIONS`, validated against `15.json`,
and applies the same every-column comparison to the four tables that exist at version 4.

**Result: every migration 4→5 through 14→15 passes, and so does the chain.** Final run of the
class: 12 tests, 0 failures, 0 errors, JUnit XML timestamp `2026-09-13T08:02:17Z`.

| Migration | Result | What the test watches beyond the generic checks |
|---|---|---|
| 4→5 | pass | three track tables appear empty |
| 5→6 | pass | `offlineRegionId` added null; `offline_regions` appears empty |
| 6→7 | pass | `lat`/`lng` carried through the NOT NULL drop (45.4301, -122.2869) |
| 7→8 | pass | old `entryId` becomes one `log_entry_photos` row; `createdAtEpochMillis` null |
| 8→9 | pass | `isDraft` = 0, `draftOfEntryId` null for a pre-existing entry |
| 9→10 | pass | rows untouched by the index migration |
| 10→11 | pass | six cartography tables appear empty |
| 11→12 | pass | `relativePath` carried, `latitude`/`longitude` null |
| 12→13 | pass | names carried, `trackId`/`originWaypointId` null |
| 13→14 | pass | `trackId` carried, `designation` null |
| 14→15 | pass | `timestampEpochMillis` carried, both speed columns null |
| 4→15 chain | pass | `lat` carried, `isDraft` = 0, one cross-reference row |
| 3→4 | **not covered here** | no `3.json` exists; `MushroomLogMigrationTest` covers it from a hand-written V3 fixture (§4) |

**The road to green was two defects in my scaffolding, neither in a migration**, recorded because
the dispatch asked for every failure:

- Run 1 (`07:52:19Z`): 10 failures, all `SQLITE_MISMATCH`. My seeder wrote the string `"<table>-1"`
  into any column named `id`; `track_points.id` (v5 on) and `offline_regions.id` (v6 on) are INTEGER
  primary keys, and a string in a rowid alias is a mismatch, not a coercion. Affinity now decides
  before the column name. 4→5 and the chain had passed because version 4 has no INTEGER id.
- Run 2 (`07:54:25Z`): 1 failure, 7→8, my "every new table starts empty" check against the one
  migration that fills a table it creates. The test now declares the filled table.
- Run 3 (`07:55:06Z`): 12/12. Run 4 (`08:02:17Z`): 12/12 after the every-column comparison landed.

**Proof the suite bites.** Four one-line edits to `Migrations.kt`, each run through the class,
each restored from a copy saved before the first edit (`cmp` against that copy before each edit,
`git diff --quiet` clean after each restore), and the build log's compile-error count read before
any XML was, per the revert-runner rule in `CLAUDE.md`. Compile errors: 0 in all four.

| Probe | Edit | Failures | Message | XML timestamp |
|---|---|---|---|---|
| A | 14→15 stops adding `speedAccuracyMetersPerSecond` | 2/12: 14→15, chain | `Migration didn't properly handle: track_points` (Room's validation) | `07:56:43Z` |
| B | 6→7 copies `lat` as NULL | 2/12: 6→7, chain | `null cannot be cast to non-null type kotlin.Double` at the `lat` read | `07:57:16Z` |
| C | 12→13 copies `note` as NULL | 1/12: 12→13 | `NOT NULL constraint failed: waypoints_new.note` | `08:03:05Z` |
| C2 | 12→13 copies nullable `altitude` as NULL | 1/12: 12→13 | `waypoints.altitude after 12->13 expected:<1.5> but was:<null>` | `08:03:41Z` |

Probe B ran before the every-column comparison existed; its message is the raw cast, not a named
assertion, and the same edit would now also fail by name. Probe C fired on the schema's own
constraint rather than on my comparison, which is why C2 was run against a nullable column: only
the value comparison can see that one, and it did.

**What C and C2 also showed: the chain test did not fail either time.** `waypoints` is created at
version 5, so the chain, which seeds version 4, carries it through empty. The chain test proves
that the four version-4 tables survive all twelve steps with every value intact, and nothing about
rows in tables created later. The per-migration tests seed every table at N, so no table is empty
in them. Recorded as a limit of the chain test, not fixed here.

## §4 The fixture tests, kept, and what each side covers

Not deleted, per the dispatch. What they cover that the new tests do not:

- **3→4.** `MushroomLogMigrationTest` starts from `LegacyForagerDatabaseV3`; the new suite cannot
  start below 4 because no `3.json` exists.
- **Reading through production code after migrating.** Every fixture test opens the migrated file
  as `ForagerDatabase` through `Room.databaseBuilder(...).addMigrations(...)` and reads through
  the real DAOs and repositories (`trackDao().getPointsForTrack`, `repository.getById`, entry
  relations with photos). That checks the entity classes at version 15 against the migrated file,
  which the new suite never does: it reads with raw SQL.
- **Writing after migrating.** `LogPhotoMigrationTest` shares a new photo between two entries after
  the chain; `TrackPointSpeedMigrationTest` inserts points with and without speed. The new suite
  writes nothing after a migration.

What the new tests cover that the fixtures do not:

- **Version N as it actually was.** A `LegacyForagerDatabaseVn` is a developer's copy of N, and
  several reuse production entity classes with caveats recorded in their own comments. `N.json` is
  what Room generated from the entity classes at the commit that shipped N.
- **Validation at every step.** Room validates a fixture run once, on opening the final file
  against the version-15 entity classes. The new suite has Room validate each N+1 against
  `N+1.json`, so a migration that produces the right final shape by way of a wrong intermediate
  one is caught at the step that is wrong.
- **Every column, by name.** The fixtures assert the values their authors thought of. The new suite
  compares every column that exists at both N and N+1.
- **11→12 with a row in `log_photos` at version 11.** No fixture starts at 11. Whether the fixture
  chains that pass through 11→12 (those starting at 10 or earlier) carry a `log_photos` row through
  it was not checked; stated as unverified rather than as a gap.

Which stay is the owner's ruling. My reading: the 3→4 test and the DAO/repository round-trips are
coverage the new suite does not replace; the rest of what the fixtures assert is now asserted more
strictly by the schema-backed tests.

## §5 Versions 1 and 2

- **What the code does.** `ForagerDatabase.create` (`ForagerDatabase.kt:183-198`) registers 3→4
  onward and chains `fallbackToDestructiveMigration(true)` **only when `isDebug`**, defaulting to
  `BuildConfig.DEBUG` (`:195`). On a release build a version-1 or version-2 file makes Room throw at
  open for the missing path; on a debug build the file is dropped and recreated at 15. Both branches
  are asserted, from a `LegacyForagerDatabaseV2` fixture, in `ForagerDatabaseDestructiveFallbackTest`
  (`:54` throws under the release path, `:68` wipes under the debug path). The class's own doc
  comment (`:122-140`) records that the fallback used to be unconditional and why it is not.
- **Whether such a file can exist in the wild: could not be determined from the repository.** The
  dispatch's reason (no AAB uploaded before build 628) is a Play Console fact, not visible here.
  The only in-repo statement is the same doc comment, written 2026-08-27: "nothing has been
  distributed". If that held, and every distributed build since carried version 15, no
  version-1 or version-2 file exists on any device, but the "if" is the owner's to confirm.
- A correction to my own earlier note on this: I had written that a version-1 or 2 file "drops
  data". That is the debug behaviour only. A release build throws.

## §6 Windows

Measured on this run's Robolectric sandboxes under `/tmp`, which Robolectric names the same way on
every host: `robolectric-` + the first **120** characters of `<ClassName>_<method name with spaces as
underscores>` + a 20-digit suffix, and the database under it at
`<sandbox>/com.zynergylabs.forager.app-dataDir/databases/<name>.db`.

- The eleven fixture tests' class-plus-method names run 124 to 184 characters, so every one is
  truncated to 120: a 152-character directory element for each.
- The new tests' names run 75 to 98 characters: directory elements of 107 to 130, **22 to 45
  characters shorter**.
- Database file names: fixtures 27 to 39 characters (`log-photo-migration-test.db` to
  `track-origin-waypoint-migration-test.db`); new `m4.db` to `m14.db` and `chain.db`, 5 to 8.
- Beyond `%TEMP%\`, the shortest fixture database path is 152 + 47 + 27 = 226 characters; the new
  ones are 159 to 185. So the new paths are **41 to 67 characters shorter than the shortest fixture
  path that fails today.**

**Whether that clears the ceiling: could not be determined from Linux.** The threshold is bounded
to [212, 260] as a total, the owner's `%TEMP%` prefix is unknown here, and the audits that recorded
the failure pool did not name the failing path element (the pool includes
`AvailabilityScreenSettingsPanelTest`, which opens no database, so the element may not be the
database file at all). If the fixtures fail by less than 41 characters, the new tests pass on the
owner's machine; if by more, they fail the same way, for the same reason, and the owner should
expect that until a Windows run says otherwise.

## §7 Disclosure

### 7.1 Confirmed by observation, versus inferred

Confirmed: everything in §1 marked held, with the command that held it; the probe results in §2
(read from JUnit XML on this branch); the twelve passes and the two scaffolding failures in §3 (read
from `TEST-com.zynergylabs.forager.app.data.local.SchemaMigrationTest.xml` after each run, with
`is not None` on the failure element); the four revert probes, with compile errors counted from the
build log first and the restore checked with `cmp` and `git diff --quiet`; the sandbox naming and
the name lengths in §6, measured from `/tmp` and the source; the fallback wiring in §5, from
`ForagerDatabase.kt` and its test.

Inferred: that Robolectric names sandboxes identically on Windows (the same
`org.robolectric.internal.AndroidSandbox` code runs there, not verified on a Windows host); that
CI runs the new class (from `ci.yml`, not yet from a green run on the final head); that the
fixture tests' reuse of production entities is a weaker statement of version N than `N.json` (from
their own caveat comments, not from a diff of the two).

### 7.2 Could not be determined

- Whether any tester has ever run a migration, or whether any version-1 or 2 file exists.
- Whether the new tests clear the Windows path ceiling (§6).
- Whether fixture chains carry a `log_photos` row through 11→12 (§4).
- CI's result on the final head `e307f81`: in progress when this was pushed. The previous head,
  `512403a`, with the same twelve tests in their earlier shape, was green (run 34746417192).

### 7.3 Premises in the dispatch that were wrong

None of the four named premises was wrong. Two need a precision: "eleven tests" is eleven files
holding thirteen `@Test` methods; and "the chain is unbroken" holds for 3→15, while the
schema-backed suite can only start at 4. One premise from my own earlier notes was wrong, the
unconditional "drops data" for versions 1 and 2 (§5).

### 7.4 Decided beyond this scope

- Seeding every column and comparing every shared column by name (`e307f81`) goes past the
  dispatch's "row counts, and the specific values that should have been carried". Done because
  the first shape left every nullable column null on both sides, which is exactly the check that
  cannot see the data that would fail it; probe C2 is the evidence the stronger shape was needed.
- Three work-in-progress probe commits stay in the branch's history, under push-before-you-tidy.
- The chain test's empty-later-tables limit is recorded and not fixed.
- No fixture test was touched, no `@Ignore`, no allowlist change.

### 7.5 Suite counts, from JUnit XML

| | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Before: CI run 34744450616 on `5aee450` (GitHub Actions) | 176 | 1394 | 0 | 0 | 24 |
| After: container on `512403a` | 177 | 1406 | 0 | 0 | 24 |
| After: container on `e307f81` (final tree) | 177 | 1406 | 0 | 0 | 24 |

Skip count 24 throughout, never adjusted. The delta is one suite and twelve tests, all
`SchemaMigrationTest`. The "before" is a GitHub Actions figure and the "after" a Linux container
figure; the two have matched on every earlier comparison in `docs/audits/` and the Windows pool is
absent from both by construction.
