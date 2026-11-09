# Route plotting steps 1 and 2: the first stop, 2026-10-30

Hazel (coder), for dispatch `prompts/preserved/2026-10-30-02-route-plotting-steps-1-2.md` (RECORD -1826) and the plan
`docs/plans/2026-10-29-waypoint-route-plotting-plan.md`, both read from `origin/records-after-173`. **Look only: no app
code is written.** Everything below waits for the planner's amendment.

Each claim is marked **read** (file and line, at the base below), **inferred**, or **could not determine**.

## Base

- Branch `route-plot-data`, worktree `~/.cache/forager-wt/route-plot-data`, cut from `origin/pre-main`.
- `origin/pre-main` = `21d846b9e54d6b7f8815f934435a72dda0c808fa` (`git ls-remote origin refs/heads/pre-main`, 10:56 UTC
  2026-10-30). **Matches the dispatch's 21d846b9e.** All file and line citations below are at this commit.

## Mechanism prediction (coder profile, "Picking up a task", 3)

- **Step 1.** A rebuild migration 22→23, in its own file, copies `planned_trips` into a table whose `date` is nullable
  (SQLite cannot drop a NOT NULL in place). The same migration creates two new route tables keyed by trip. The domain's
  `PlannedTrip.date` becomes `LocalDate?`. It carries an optional route, and every reader of the date handles null:
  - the sort;
  - the Trip Planner row;
  - the bubble;
  - the crowded-stack list row;
  - the overlay feature.

  The backup gains two OWNED TableSpecs, owned by `planned_trips`. Existing rows keep their dates because the
  migration's `INSERT … SELECT` names `date` explicitly. The tests prove that by value, through the migration test
  harness and then through Room and the repository.
- **Step 2.** A domain use case lists the downloaded regions once. For each consecutive pair of stops it calls
  `routeDataForPoints`, then the engine's `TrailRouter.route`, then the area check, the same chain as
  `FindWayToWaypointUseCase`. A failure at any link becomes a straight leg carrying its reason, logged once through
  `ErrorLog`. The total is the sum of the legs' metres.

## Premises in the dispatch and plan, re-checked

Every line of the plan's "What exists" was re-read at the base. The dispatch's points were checked as follows.

| Premise | Status |
|---|---|
| `TrailRouter.route(start, end, routeData)`, `domain/routing/TrailRouter.kt:15-28` | **Confirmed**, `TrailRouter.kt:15-28`. Its doc's "Nothing calls this in production yet" (`:12`) is **stale**: `FindWayToWaypointUseCase.kt:70` calls it, through `TrailEngineSource`. |
| "It finds route data for each leg (`FindRouteDataForPoints`)" | **Wrong in detail.** No class has that name. The file `domain/routedata/FindRouteDataForPoints.kt` holds a top-level function `routeDataForPoints(from, to, regions)` (`:26`), which needs the region list (`List<OfflineRegionSummary>`). The caller reads that list itself, from `OfflineMapRepository.listRegions()` (`OfflineMapRepository.kt:81`), as `FindWayToWaypointUseCase.kt:58` does. |
| The function returns `InDifferentAreas` or `NoRegionCovers` otherwise (plan) | **Incomplete.** There is a fourth outcome, `NotUsable(state)` (`FindRouteDataForPoints.kt:64`): a region holds both ends but its routes cannot be used. The state is one of: never fetched, fetching, damaged, needs an app update, none on the server, or a square missing. A straight leg needs a reason for each of these. |
| "routes each leg in turn (`TrailRouter`)" | **Incomplete.** The router is reached only through `TrailEngineSource.engine()` (`domain/routing/TrailEngine.kt:15-23`), which can fail when the profile cannot be installed. The existing two-point use case also checks that the route has two or more points (`FindWayToWaypointUseCase.kt:75`) and that the route stays inside the region's area (`:76-78`). A plotted leg needs the same three checks, or a reason why not. |
| Trip is `PlannedTrip(id, name, location, date)`, `PlannedTrip.kt:19-24` | **Confirmed.** |
| Date required at `SavePlannedTripUseCase.kt:31`, the NOT NULL column, `AvailabilityMapOverlaysUi.kt:162` | **Confirmed**: the past-date check is at `:31`, `date TEXT NOT NULL` is in `22.json`, and the picker's confirm returns at `:162` when no date is selected. |
| "Rows and the bubble" are the places that print the date (`AvailabilityTripsWaypointsUi.kt:148`, `MapBubbles.kt:320`) | **Incomplete.** There are three more: the crowded-stack list row, `CrowdedListRows.kt:56`; the delete button's accessibility label, `AvailabilityTripsWaypointsUi.kt:164` ("Remove planned trip for {date}"); and the overlay feature's `snippet`, `OverlayFeatures.kt:161`. No production code reads the snippet (`git grep snippet` finds only writers and comments), but `SightingsMapOverlayDataTest.kt:340` asserts it. |
| Sort by date, `GetPlannedTripsUseCase.kt:24` | **Confirmed.** It puts today's trips first, then the rest by date ascending. |
| Trips backed up as a RECORD table, `JournalTables.kt:52-54` | **Confirmed.** |
| `version = 22` (`ForagerDatabase.kt:206`), latest migration 21→22 (`Migrations.kt:1243`), Migrations.kt 1266 lines | **Confirmed.** Also `SCHEMA_VERSION = 22` at `ForagerDatabase.kt:271` and `ALL_MIGRATIONS` at `:264-269`; both change. |
| Stored-line precedent, `TrackPathCodec.kt:20-40` | **Confirmed**: 16 bytes per point, raw doubles. |
| "Test the migration with Room's migration test helper" | **Confirmed available**: `SchemaMigrationTest.kt` uses `MigrationTestHelper` against the exported schemas (`:57-62`, `:235-251`), with `room-testing` pinned (`libs.versions.toml:106`). |

## (a) The domain model for a plotted route

New pure-Kotlin types, with no Android and no Room, in a new `domain/route/` package. Each is a new file under 400 lines.

```kotlin
data class PlannedTrip(            // existing type, two changes
    val id: String,
    val name: String,
    val location: LatLng,          // a route trip's location = its first stop (see Q4)
    val date: LocalDate?,          // was LocalDate
    val route: PlottedRoute? = null,
)

data class PlottedRoute(val stops: List<RouteStop>, val legs: List<RouteLeg>) {
    val totalMeters: Double get() = legs.sumOf { it.meters }
    // invariant, checked at construction: legs.size == stops.size - 1 (or both empty)
}

/** A copy of a waypoint as it was when plotted: its name and position. No waypoint id is kept (nothing would read it). */
data class RouteStop(val name: String, val position: LatLng)

sealed interface RouteLeg {
    val line: List<LatLng>; val meters: Double
    /** Along trails: [line] runs stop → trail → stop. [trailFrom]..[trailTo] are the indexes of the trail part; the
     *  stretches before and after are the straight joins from each stop to its nearest trail point (see Q2). */
    data class Trail(override val line: List<LatLng>, override val meters: Double, val trailFrom: Int, val trailTo: Int) : RouteLeg
    /** The straight line between the two stops, and why there is no trail route. */
    data class Straight(override val line: List<LatLng>, override val meters: Double, val reason: StraightReason) : RouteLeg
}

enum class StraightReason {   // no on-screen text: the words are the owner's, at step 3
    NO_REGION_COVERS, IN_DIFFERENT_AREAS,                       // routeDataForPoints
    ROUTES_NOT_DOWNLOADED, ROUTES_FETCHING, ROUTES_NEED_APP_UPDATE,
    ROUTES_UNREADABLE, NO_TRAIL_ROUTES,                         // NotUsable states, and unreadable regions
    START_FAR_FROM_TRAIL, END_FAR_FROM_TRAIL, NO_ROUTE_FOUND,   // engine failures, and an engine that could not be built
    LEAVES_AREA,                                                // the area check
}
```

- **The leg's kind is the sealed type, not a stored flag.** A trail leg has no reason, and a straight leg has one, so
  there is no separate kind column that could contradict it (see (b)).
- **`StraightReason` is a new enum, not `WayToWaypointNote`.** That enum carries on-screen text written for a walker
  ("You're far from any trail", `WayToWaypoint.kt:68-69`), which is wrong for two waypoints.
- **Step 2's use case** is a new class with a working name of `PlotRouteUseCase`, built like
  `FindWayToWaypointUseCase`:
  - Its inputs are `OfflineMapRepository`, `TrailEngineSource` and `ErrorLog`.
  - `invoke(stops: List<RouteStop>): PlottedRoute`. It lists the regions once and routes the legs one after another,
    never in parallel, because the engine runs one route at a time (`BRouterTrailRouter.kt:199-201`).
  - It also has a public `leg(from, to, regions)`, so step 3 can add one leg per tap without re-routing earlier legs
    (see Q5).
  - Fewer than two stops gives no legs and a total of 0.
  - `CancellationException` is never caught.
  - Tests run on a fake `TrailRouter` inside a fake `TrailEngineSource`, with fake regions. There is one test per
    `StraightReason`, each asserting the reason and that one log line was written, plus the total.

## (b) The schema

**`planned_trips`, rebuilt.** The only change is that `date` loses NOT NULL.

| Column | Type | Read by |
|---|---|---|
| `id` | TEXT PK | as today |
| `name` | TEXT NOT NULL | as today |
| `lat`, `lng` | REAL NOT NULL | as today; for a route trip, the first stop |
| `date` | TEXT **NULL** (ISO `yyyy-MM-dd`) | `RoomPlannedTripRepository` to `PlannedTrip.date`, then the sort, the row, the bubble and the crowded row |

**`planned_trip_route_stops`, new.** One row per stop.

| Column | Type | Read by |
|---|---|---|
| `tripId` | TEXT NOT NULL | the repository's join in code (no `@ForeignKey`; CLAUDE.md, Room/DataStore pitfall) |
| `stopIndex` | INTEGER NOT NULL | the repository's ordering |
| `name` | TEXT NOT NULL | to `RouteStop.name` |
| `lat`, `lng` | REAL NOT NULL | to `RouteStop.position` |

- Primary key `(tripId, stopIndex)`. Its index serves `WHERE tripId = ?` and `WHERE tripId IN (…)` because `tripId`
  leads. No other index is needed.

**`planned_trip_route_legs`, new.** One row per leg.

| Column | Type | Read by |
|---|---|---|
| `tripId` | TEXT NOT NULL | as above |
| `legIndex` | INTEGER NOT NULL | ordering; leg *i* joins stop *i* to stop *i+1* |
| `path` | BLOB NOT NULL | `TrackPathCodec.decode` to `RouteLeg.line` (the existing codec, reused as is) |
| `meters` | REAL NOT NULL | `RouteLeg.meters`, then `PlottedRoute.totalMeters` |
| `trailFrom`, `trailTo` | INTEGER NULL | `RouteLeg.Trail`. Non-null exactly when the leg is a trail leg. |
| `straightReason` | TEXT NULL (enum name) | `RouteLeg.Straight.reason`. Non-null exactly when the leg is straight. |

- Primary key `(tripId, legIndex)`.
- A row whose nullable columns do not match one of the two shapes is refused when read: the trip loads without its
  route, and the refusal is logged. It is never guessed into a shape.
- The dispatch's "its kind (trail or straight)" is expressed by which of the two nullable columns is set, not by a third
  column. I can add an explicit `kind` column if the planner prefers.
- **Delete:** `RoomPlannedTripRepository.delete` removes the trip and its stops and legs in one `@Transaction`.
- **Backup:** add two `Kind.OWNED` TableSpecs, each `owner = Reference("tripId", "planned_trips")` and no `needs`,
  after `planned_trips` in `JournalTables.journal` (`JournalTables.kt:54`). With no `needs`, a route travels with its
  trip and depends on no waypoint, which is the "a deleted waypoint changes nothing" ruling. A Merge that skips a trip
  whose id the phone already has also skips that trip's stops and legs (`JournalTables.kt:13-15`), so a route is never
  half-merged. The schema guard test (`JournalBackupTest.kt:909-913`) forces this: a new table must be listed or
  excluded.

### The column reader rule, plainly

CLAUDE.md's Room/DataStore pitfall says every column lands with a reader in the same change. In step 1, the only
production readers of the route columns are the repository mapping and backup and restore. **Nothing writes a route
until step 3**, so in production both tables stay empty after step 1. The MIGRATION_21_22 precedent counted backup and
restore as readers (`Migrations.kt:1234-1235`). The planner should decide whether that is enough here (Q1).

### The height question

The dispatch asks for heights stored without a second migration, or a plain statement that step 4b brings its own.

- **H1, a nullable `heights` BLOB on `planned_trip_route_legs` now.** No migration in 4b, but it is a column with no
  reader until 4b, which the rule forbids. **Not recommended.**
- **H2, reserve a height slot in the leg's `path` BLOB.** A new codec would store `lat, lng, height` per point (24
  bytes), with NaN meaning "no height yet". 4b then fills the slot with an `UPDATE`, so there is no migration and no new
  column. Its costs:
  - It replaces `TrackPathCodec` with a new codec for routes.
  - It fixes heights at one per routed vertex. Step 4b's lookup may want samples by distance instead (the plan's
    Open-Meteo grid of about 90 m and its batch limit are still unverified, Addendum 2), and that choice is 4b's to
    make.
  - "Fill in later" needs a "heights pending" state per leg. All-NaN cannot tell "not fetched yet" from "fetched, no
    data", so H2 needs a header byte as well. That is the same no-reader problem moved inside a BLOB.
- **H3, step 4b brings its own migration (23→24)** for the heights in whatever shape its own first stop chooses, with
  its reader landing in the same change. Its cost is one more migration number, checked unclaimed at that time.

**Recommendation: H3.** The storage depends on decisions 4b has not made (sampling, the pending state, the source's
grid). H2 would fix them now with no data to fix them by. That is the speculative design CLAUDE.md ("Building") rules
out. **This is a planner's or owner's choice (Q3).**

## (c) The 22→23 migration and its test

**A new file**, `data/local/MigrationRouteTrips.kt`, holds `val MIGRATION_22_23`. `Migrations.kt` is not touched.
`ForagerDatabase.kt` changes in these places:
- `version = 23` (`:206`);
- the two new entities (`:188-204`);
- `ALL_MIGRATIONS` (`:264-269`);
- `SCHEMA_VERSION = 23` (`:271`);
- a new DAO accessor;
- the schema history in the class doc.

The migration's steps:
1. `CREATE TABLE planned_trips_new (id TEXT NOT NULL, name TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, date TEXT, PRIMARY KEY(id))`.
2. `INSERT INTO planned_trips_new (id, name, lat, lng, date) SELECT id, name, lat, lng, date FROM planned_trips`. The
   column list is explicit, so the copy does not depend on column order. It also works on the
   `LegacyForagerDatabaseVn` fixtures, which declare `PlannedTripEntity` directly (for example
   `MushroomLogMigrationTest.kt:123`, `CartographyEntryMigrationTest.kt:194`). Once the entity changes, those fixtures
   generate a nullable `date` themselves.
3. `DROP TABLE planned_trips`, then `ALTER TABLE planned_trips_new RENAME TO planned_trips`. `22.json` declares no index
   on it, so there is nothing to recreate.
4. `CREATE TABLE planned_trip_route_stops (…, PRIMARY KEY(tripId, stopIndex))` and
   `CREATE TABLE planned_trip_route_legs (…, PRIMARY KEY(tripId, legIndex))`, exactly as Room exports them to `23.json`.

A rebuild is needed because SQLite has no `ALTER COLUMN` to drop NOT NULL. That fits the rebuild pattern this file
already records (`Migrations.kt:1107-1111`).

**How the test proves existing trips survive.** All in Robolectric, on CI:
- **T1, the harness case.** Add `22 to 23` to `SchemaMigrationTest` (347 lines, staying under 400). It runs
  `migrate(22, 23, MIGRATION_22_23, overrides = planned_trips.date = "2026-11-14")`. The harness:
  - seeds every v22 table from `22.json`;
  - validates the result against `23.json` (`runMigrationsAndValidate`, `SchemaMigrationTest.kt:243`);
  - asserts every seeded column by name (`:260-272`), so a `date` copied as NULL fails as "planned_trips.date after
    22->23";
  - asserts the two new tables are empty (`:246-248`).

  Then its extra block inserts a trip with `date` NULL, which proves the column is now nullable.
- **T2, the whole chain** moves from `4 to 22` to `4 to 23` (`:221-229`).
- **T3, several real trips through to the domain.** A new file, `PlannedTripRouteMigrationTest.kt`, uses
  `MigrationTestHelper` and the existing `SchemaAssets`. It:
  - creates a real v22 database from `22.json`;
  - inserts three trips with distinct names, positions and dates;
  - migrates with `ALL_MIGRATIONS`;
  - opens the migrated file as `ForagerDatabase`;
  - reads the trips through `RoomPlannedTripRepository`.

  It asserts the three `PlannedTrip` values exactly (each date included, `route == null`). It then saves an undated
  trip with a two-stop route and reads it back as the same value. The first half is the dispatch's "real v22 database
  containing trips". The second half proves the new columns' reader.
- **Revert checks.** Each runs on its own scratch branch, under CLAUDE.md's runner rules:
  - `date` copied as `NULL` in step 2: T1 and T3 fail by name.
  - `date` left NOT NULL in step 1: the `23.json` validation fails, and so does T1's null insert.
  - The undated sort reverted.
  - The row and bubble null handling reverted.

## (d) The schema version

Checked 10:56 UTC 2026-10-30, after `git fetch origin`:
- `git log --remotes --oneline -S "Migration(22, 23)"` found nothing.
- `-S "MIGRATION_22_23"`, `-S "Migration(22,23)"` and `-S "22 to 23"` found nothing.
- `-S "version = 23"` found only `d1e55423d`, the RECORD and dispatch commit for this task (text, on
  `origin/records-after-173`).
- `ForagerDatabase.kt`'s `version =` line was read on every one of the 1137 refs under `refs/remotes` (origin and
  `fresh`). The highest is 22 (733 refs); 46 refs predate the file.
- No ref carries `app/schemas/…ForagerDatabase/23.json`.

**Version 23 is unclaimed.** It will be re-checked before the step 1 PR is opened.

## (e) An undated trip on screen, and its sort

No new words are shipped. Where a line would need words, I propose them below and they stay out of the build until the
owner gives them.

- **Trip Planner row** (`AvailabilityTripsWaypointsUi.kt:148`): the date line is left out, so the row reads name, then
  MGRS, then coordinates. `isToday` is false for an undated trip (`:102` compares with null).
- **Delete button's accessibility label** (`:164`): it is "Remove planned trip for {date}" today, which has no form
  without a date. **Owner's words needed (Q6).** My proposal is "Remove planned trip {name}" for undated trips only.
  Until the owner rules, I see no way to ship step 1 without a label of some kind for an undated row. This is a stop.
- **Bubble** (`MapBubbles.kt:318-323`, rendered at `MapFeatureBubble.kt:107-113`): `MapBubbleContent.Trip.date` becomes
  `String?`, and the line is left out when null, the same as the track bubble's `content.date?.let { BubbleLine(it) }`
  (`MapFeatureBubble.kt:100`).
- **Crowded-stack row** (`CrowdedListRows.kt:56`): `date = null` and `sortDate = null`. The row already renders without
  a date (`CrowdedStackList.kt:92`), and the list already sorts null dates last (`CrowdedListRows.kt:63`), as it does
  for undated photos and waypoints.
- **Overlay feature** (`OverlayFeatures.kt:161`): an undated trip gets no `snippet` property. Nothing in production
  reads it. `SightingsMapOverlayDataTest.kt:340` keeps its dated case, and an undated case is added.
- **Sort** (`GetPlannedTripsUseCase.kt:24`): today's trips first, then dated trips by date ascending, then undated
  trips. **Within the undated trips the order is not decided.** There is no creation time to sort by, and `getAll` has
  no `ORDER BY` (`PlannedTripDao.kt:11`), so storage order is not stable. My proposal is name, then id as a tie-break
  (Q7).
- **Existing date entry is unchanged.** The picker still requires a date (`AvailabilityMapOverlaysUi.kt:162`), and
  `SavePlannedTripUseCase` keeps its signature and its past-date rule. Nothing in step 1 creates an undated trip in
  production. The first writer is step 3's route save, which will go through a new use case with its caller.
- **The fakes compile unchanged.** About 25 test fakes implement `PlannedTripRepository`. Many sit in files over 800
  lines, for example `AvailabilityScreenMapIconStackTest.kt:3401` and `JournalPendingDeleteTest.kt:1543`. The interface
  therefore keeps its three methods: the route travels inside `PlannedTrip`, defaulting to `null`, and `save` persists
  it. None of those files is edited.

## (f) Files over 800 lines

| File | Lines | Touched? | Plan |
|---|---|---|---|
| `data/local/Migrations.kt` | 1266 | No | The new migration goes in `MigrationRouteTrips.kt`. |
| `test/.../data/backup/JournalBackupTest.kt` | 950 | **Yes, unavoidably** | `JOURNAL_TABLE_NAMES` (`:946-950`) must list the two new tables, or the schema guard (`:909-913`) fails, and that guard is what keeps a table from being left out. **Split before adding:** one move-only commit takes `JOURNAL_TABLE_NAMES` and the guard test (`:908-918`) into a new `JournalTablesSchemaTest.kt`, with no behaviour change (its helpers `Phone.allTables` and `schemaPrimaryKeys` are already in `JournalBackupTestSupport.kt:129`, `:193`), and CI runs green on that commit alone. The route edit then lands in the new file and in `JournalBackupTestSupport.kt` (240 lines; the seed gains a stop row and a leg row beside `:180-181`, and `Phone.SCHEMA = 22` at `:124` becomes 23). The guard also pins `SCHEMA_VERSION` to the literal 22 (`JournalBackupTest.kt:916`); that line moves with it and becomes 23. The round-trip asserts at `:191`, `:199`, `:732` and `:780` loop over the list and need no edit. |
| `ui/availability/AvailabilityViewModel.kt` | 797 | No | Step 1 needs no ViewModel change. |
| `ui/availability/AvailabilityCompactScaffold.kt` | 1440 | No | |
| Fakes in files over 800 lines | many | No | See (e). |

Under 800 and touched in step 1:
- `ForagerDatabase.kt` (274);
- `AppContainer.kt` (535), one line if the repository takes a second DAO;
- `MapBubbles.kt` (508);
- `AvailabilityTripsWaypointsUi.kt` (315);
- `OverlayFeatures.kt` (224);
- `CrowdedListRows.kt`;
- `GetPlannedTripsUseCase.kt`, `PlannedTrip.kt`, `PlannedTripEntity.kt`, `PlannedTripDao.kt`,
  `RoomPlannedTripRepository.kt`, `JournalTables.kt`.

Step 2 touches no existing file except `AppContainer.kt`, and only if the planner wants the use case wired now. I
propose not wiring it, since it has no caller until step 3 (Q8).

## Questions for the planner (stops)

1. **Q1, readers.** In step 1 the route tables' only production readers are the repository mapping and backup and
   restore, and nothing writes them until step 3. Is that enough for the column reader rule, or should the tables land
   in step 3 with their writer? Moving them to step 3 changes the dispatch's split.
2. **Q2, joins.** A trail leg's ends: the engine starts and ends on the trail nearest each stop, up to 250 m away
   (`FindWayToWaypointUseCase.kt:32-38`). I propose the leg's line runs stop → trail → stop, with the joins straight
   (`trailFrom`/`trailTo` mark the trail part) and counted in the leg's length, as the way to a waypoint does
   (`:79-86`). The alternative is to store the trail part only and draw nothing between a stop and the trail. Step 4
   would draw the joins dashed; how they look on screen is the owner's.
3. **Q3, heights.** H3 (4b brings migration 23→24, recommended) or H2 (a height slot in the leg BLOB now)?
4. **Q4, a route trip's `location`.** I propose the first stop. It is NOT NULL and Directions uses it. The plan already
   lists "Directions to which stop" as an owner question before step 3. Step 1 only needs a value.
5. **Q5, the step 2 API.** Is the whole-route `invoke(stops)` plus a per-leg `leg(from, to, regions)` right? Step 3
   adds a leg per tap, and Undo removes one.
6. **Q6, words.** The undated delete button's accessibility label (see (e)). This blocks step 1 shipping.
7. **Q7, undated order.** By name, then id?
8. **Q8, wiring.** Step 2 leaves `PlotRouteUseCase` unwired in `AppContainer` until step 3 gives it a caller?
9. **Q9, a waypoint tapped twice in a row** (a zero-length leg). Does step 2 refuse it, or is that left to step 3's UI?
   I propose the use case returns a zero-metre straight leg, with no special case, and step 3 decides whether to allow
   the tap.

## What was not done or not verified

- No code was written and no build was run, as the dispatch requires.
- `BRouterTrailRouter.kt:199-201` was re-read: `MAX_RUNNING_TIME_MILLIS = 30_000L` and the process-wide
  `ONE_ROUTE_AT_A_TIME` mutex. The plan's citation stands.
- The cost of loading every trip's route BLOBs in `getAll` for the Trip Planner list is **not measured**. I infer it is
  small for a handful of routes. If it is not, step 4 can load the route on demand.
