# PR #26 Rework — Multi-region offline map management

**Status (2026-08-25):** in progress. Workstreams 0, A, B, L1–L4 (and G1–G3, landed alongside them)
are closed — see the Sequencing block below for commits and PRs. L5 and L6 remain scoped, not yet
dispatched. Each workstream below is still meant to be picked up cold, one dispatch at a time, by a
coder session with no memory of the planning conversation that produced it.

**Depends on:** PR #34 (error-presentation spec) — merged, `main` at `3b6021b`. PR #26 itself
(`claude/plan-implementation-rjzmkr`) is still open, unmerged, and stale against current `main`
by 51 commits as of the pulse this plan is built from.

**Source documents, not stored in this repo:**
- `SCOPING_pr26_rework.md` — the original planner scoping document. §§1–4 of that document
  (what PR #26 delivers, the five original workstreams, what doesn't change, sequencing) are
  summarized inline below where a coder session needs them; not reproduced verbatim.
- The 2026-08-22 pulse of `claude/plan-implementation-rjzmkr`, read at `main` = `3b6021b` —
  the evidence base for every codebase claim below (file paths, line counts, quoted code).

**Decisions this plan assumes** — made by the project owner on 2026-08-23, recorded in full in
[`docs/audits/2026-08-23-pr26-rework-scoping-decisions.md`](../audits/2026-08-23-pr26-rework-scoping-decisions.md)
plus follow-ups answered directly against this plan's own open-questions list (see that file's
"What is still open" section, now resolved below in Workstream L). Do not re-litigate these; if
one turns out to be wrong once implementation starts, stop and report rather than silently
picking a different answer.

---

## Sequencing

Re-derived 2026-08-22 from a dependency-graph pulse against `main` and #26's fork point, after
the original seven-workstream split (cut by *activity*, not by *what types what*) closed its
first workstream at roughly a third of its stated scope. The original split, verbatim, and why
it was superseded, is archived at
[`docs/audits/2026-08-23-pr26-rework-seven-workstream-split-archive.md`](../audits/2026-08-23-pr26-rework-seven-workstream-split-archive.md) —
per this repo's audits convention, that file is a point-in-time record, not edited further; this
plan is the current source.

```
0.  Foundation files          [CLOSED — claude/task-hwj91a @ 1726b90]
A.  Schema and migration      [CLOSED — claude/task-hwj91a @ 6530d7a]
B.  The contract migration    [CLOSED — claude/task-hwj91a @ 66d53b8]
L1. Photo file cleanup on entry delete   [CLOSED — claude/task-hwj91a @ 11017a6, PR #39]
L2. The centre-pin picker                [CLOSED — claude/task-hwj91a @ aa60f2d, PR #39]
L3. Optional foundAt                     [CLOSED — claude/task-hwj91a @ f761e55, PR #39]
L4. The new entry flow                   [CLOSED — three dispatches: L4a (entry creation
                                           routes straight to the edit form) @ 51dbfa6, PR #39;
                                           L4b/L4b-R/L4b-R2 (persisted drafts, corrected twice)
                                           merged from claude/l4b-persisted-drafts via PR #40 —
                                           see docs/qc/dispatches/reports/ for all three reports]
L5. Photo location capture
L6. Tile capture
```

(G1–G3 — the photo-gallery ownership inversion, via `MIGRATION_7_8` — also landed on
`claude/task-hwj91a` alongside L1–L4, via commits `51be094`/`bcb9d0b`/`0e2198b`. This plan doc does
not itself scope G1–G3 as a workstream; they are recorded here only so this Sequencing block's
commit range is complete, not as a claim that a fuller G1–G3 scoping section exists elsewhere in
this file.)

**A and B ran serial, A first — by owner decision (2026-08-22), not by dependency.** The
dependency graph made A and B independent of each other (A touches persistence, B touches the
repository contract and its consumers; neither's surface references the other's). Owner chose
serial anyway: A is small, B is the risky, atomic, many-file unit, and landing A first kept
bisection against a known-good `main` if B went wrong. Both are closed.

**L1–L6 replace Workstreams C and D**, per the owner's 2026-08-22 design change — see "Workstream
L — Log & Location rework" below for why, and
[`docs/audits/2026-08-24-workstream-c-and-d-archive.md`](../audits/2026-08-24-workstream-c-and-d-archive.md)
for C and D's original text. L1, L2, and L3 are mutually independent and independent of anything
in A/B beyond what's already landed — any of the three can be dispatched first. L2 and L3 both
land before L4; L4 depends on L2 and L3; L5 depends on L4; L6 depends on L3 and L5 and owns the
bridge between the mushroom-log domain and `OfflineMapRepository` — see L6's own section for why
wiring that bridge twice, independently, would merge cleanly and still not work.

**B reports at internal checkpoints, not one gate at the end** — owner decision (2026-08-22), see
Workstream B's own checkpoint list.

---

## Workstream 0 — Foundation files (CLOSED)

**Status:** landed, `claude/task-hwj91a` @ `1726b90`.

Ten files ported verbatim from PR #26, plus `androidx.datastore:datastore-preferences:1.2.1`
which `main`'s build config lacked: `OfflineRegionDao.kt`, `OfflineRegionEntity.kt`,
`MapPreferencesRepository.kt`, `DataStoreMapPreferencesRepository.kt` + test,
`EstimateOfflineTileCount.kt` + test, `OfflineRegionStaleness.kt` + test, `MapLibreStorage.kt`.

Compile green; three test classes verified against JUnit XML (5/5, 6/6, 5/5), not the build log
alone.

`ensureMapLibreStorageOutsideCache()` sits unreferenced until Workstream B wires its call site.
**Expected — do not "fix" it.**

This was the only genuinely independent unit in the original split: ten files, zero forward
references.

---

## Workstream A — Schema and migration

**Depends on:** Workstream 0 (landed). No forward dependency on B or any of L1–L6.
**Runs:** first, before B, by owner decision.

Old WS2 and WS5's persistence half, **merged**. They co-write one `MIGRATION_5_6` body — a forced
merge, not an orderable dependency. Split, one would have to reopen the other's landed migration.

**Surface:** `ForagerDatabase.kt` (entity list assembled alongside
`TrackEntity`/`TrackPointEntity`/`WaypointEntity`), `Migrations.kt` (`MIGRATION_5_6`, version →
6, including the nullable FK from `MushroomLogEntryEntity` to `offline_regions`),
`MushroomLogEntryEntity.kt`, `app/schemas/.../6.json`, `OfflineRegionMigrationTest.kt` (new, must
build a real v5 fixture), `MushroomLogMigrationTest.kt` (migration list re-derived).

**Context worth carrying:** #26's design doc assumed offline-regions and trips would land in one
shared `MIGRATION_4_5`. They didn't — trips arrived via #32/#33 with its own `MIGRATION_4_5`.
That broken assumption is why this workstream exists.

**Gate:** migration runs against a real v5 fixture; both migration tests green; full compile.

**Out of scope:** the delete-block *flow* — dropped entirely under the later design change, see
Workstream L; nothing is at risk during a delete under the current design, so there is no blocking
logic left to build. This workstream lands the FK column only.

---

## Workstream B — The contract migration

**Depends on:** Workstream 0 (landed) and Workstream A (by owner sequencing, not by type
dependency).
**Reports:** at each checkpoint below, not only at the end.

Old WS3 + WS4 + WS1's deferred surface + WS7, as one **atomic** unit. Nothing here compiles
without the rest of it. This is not a scoping preference — it is what the 13-consumer
`OfflineMapInfo` footprint forces.

**Surface:**
- `domain/OfflineMapRepository.kt` — **explicitly assigned to this workstream.** Declares
  `OfflineRegionSummary` (replacing the deleted `OfflineMapInfo`: `downloadedAtEpochMillis` →
  `createdAtEpochMillis`, gains `id`/`name`/`minZoom`/`maxZoom`), the redesigned four-case
  `OfflineMapStatus` (`Idle`/`Succeeded`/`Downloading`/`Failed`, replacing
  `NotDownloaded`/`Downloading`/`Downloaded`/`Failed`), the renamed interface
  (`delete`→`deleteRegion`, `getStatus`→`listRegions`, `download` returning
  `Result<OfflineRegionSummary>`), and the zoom constants moved to companion values.
- `MapLibreOfflineMapRepository.kt` — five remaining `onError`/`IOException` sites still
  carrying raw SDK text, plus the surrounding method rework. Take `main`'s `15.0` for
  `OFFLINE_MAX_ZOOM`.
- `MapLibreOfflineRegionMetadata.kt` — `RegionMetadata` 2→5 fields, and its doc comment. Breaks
  three call sites in `MapLibreOfflineMapRepository.kt`, which is why it lives here and not in
  Workstream 0.
- `MapLibreOfflineRegionMetadataTest.kt` — the 3-line construction-call edit, which only
  compiles once the production file widens.
- `AvailabilityViewModel.kt` — `loadOfflineMapStatus`, `onDownloadOfflineMaps`,
  `onDeleteOfflineMaps` (→ `onDeleteOfflineRegion(id: Long)`) rewritten in full; `toUiStatus()`
  deleted with its logic moved inline; `mapPreferencesRepository` wired as the 15th constructor
  parameter.
- `AvailabilityUiState.kt` — all six offline fields as one diff.
- `AvailabilityScreen.kt` — `OfflineRegionsSection` (new), and replacement of `OfflineMapsPanel`
  and `OfflineMapStatusContent` (**named `OfflineDownloadStatusContent` on #26's branch — a
  rename, not an edit**).
- `AppContainer.kt` and `MainActivity.kt` — **previously unowned by any workstream.** Wiring for
  the DAO constructor param and the new ViewModel methods.
- Every test fake implementing the old interface — 11 test files construct `OfflineMapInfo`.

**Error presentation folds in here, it does not follow.** A separate compliance pass would mean
knowingly writing `error.message ?: "..."` sites and fixing them later. Apply PR #34's treatment
as this is written, including the belief-changing classification for download-failure vs.
region-list-load failure, per `docs/error-presentation-spec.md`.

**Checkpoints — report each, do not proceed past a red one:**
1. `domain/OfflineMapRepository.kt` declarations compile alone.
2. `MapLibreOfflineMapRepository.kt` + `MapLibreOfflineRegionMetadata.kt` + its test compile
   against the new declarations.
3. `AvailabilityViewModel.kt` + `AvailabilityUiState.kt` compile.
4. The three composables compile.
5. All test fakes updated; full test suite green.
6. Full compile + full test run, verified against JUnit XML.

**Replacement discipline — the failure this exists to prevent.** `OfflineMapsPanel` and
`OfflineMapStatusContent` are live, shipped, single-region code on `main`, carrying accumulated
detail (e.g. the comment on the `Downloaded` branch explaining the zoom 10–14 archive vs.
zoom-15 live-fetch split). Before overwriting either: diff `main`'s version against #26's,
identify what `main` carries that #26 doesn't, and **report per item** whether it is
intentionally superseded by the multi-region design or must be carried forward. Do not resolve
these unilaterally.

**Gate:** all six checkpoints green, plus the replacement-discipline report accepted.

---

## Workstream L — Log & Location rework

**Replaces Workstreams C and D**, superseded by owner decision (2026-08-22) — see
[`docs/audits/2026-08-24-workstream-c-and-d-archive.md`](../audits/2026-08-24-workstream-c-and-d-archive.md)
for C and D's original text and the full reasoning. In short: C's actual deliverable (the
delete-confirmation dialog) shipped early inside Workstream B, and under the new design nothing is
at risk during a delete, so there is nothing left for C to block. D's trigger moved from
delete-time to location-set: capture-on-delete made an entry's own journal tile contingent on a
moment that might never come, or might come with no signal; capture-on-location-set makes every
located entry self-sufficient from the start.

While rescoping when capture happens, the owner also chose to fix two adjacent things now, while
the code touching this area is still young and few call sites depend on it — rather than defer
them and pay to convert twice: **every long-press-to-place-a-location gesture in the app is
replaced by one new interaction** (L2), and **`MushroomLogEntry.foundAt` becomes optional** (L3),
since the new entry flow no longer requires a location up front.

Found via two read-only scoping pulses (2026-08-22): the C/D rescoping pulse (superseded before
any dispatch was written from it once the design changed further) and the Log & Location rework
scoping pulse that this split is derived from.

### Owner decisions (2026-08-22)

1. **A log entry can exist without a location.**
2. **Adding an entry goes to the entry page, not to location placement.** That page carries
   **Add Location**, **Camera**, and **Gallery** buttons.
3. **Adding a photo also goes to the entry page**, with a prompt offering the photo's location
   metadata if present.
4. **In-app capture strips EXIF GPS and uses the device location ping instead.** Imported photos
   keep their metadata; it is read and offered.
5. **Fixed centre pin, pan the map underneath, OK/Cancel — replaces long-press at every site.**
   **This is not a style preference.** The stated reason: dragging a marker with a finger occludes
   the target — the same problem long-press has, since a fingertip sits directly on top of the
   point being placed either way. A pinned centre marker keeps the finger off the target entirely,
   because the finger drives the map underneath a marker that never moves. Carry this rationale
   forward wherever this decision is cited; it is an accessibility decision with a stated reason,
   not an aesthetic one.
6. **Deleting a log entry deletes its photo files** — fixes a live bug where only the database
   rows are removed today.
7. **Tile capture fires when an entry acquires or changes a location**, not at entry creation and
   not at region-delete time. **On change, the new tile is captured and confirmed successful
   before the old one is deleted** — the same "never leave the operation's target in a worse state
   than before it started" principle already applied to region deletion elsewhere in this rework,
   not a new judgment call. A capture that fails mid-swap must leave the entry with its prior
   tile intact, not with neither.
8. **The map tab's "Log a find" follows the journal's new flow, not immediate commit** — it no
   longer creates an entry the instant a location is picked; it goes to the entry page like every
   other entry-creation path, per decision 2.

### The six pieces

**L1 — Photo file cleanup on entry delete.**
**Depends on:** nothing. Can ship immediately, independent of every other piece here.
Fixes a live, already-shipping bug: `MushroomLogDao.deleteEntryAndPhotos` removes the `log_photos`
and `mushroom_log_entries` rows but never calls `PhotoStore.delete()`, so a deleted entry's photo
files are orphaned on disk permanently. `MushroomLogViewModel.onDeleteEntry(id)` already holds the
full entry — including its `photos: List<LogPhoto>` and each one's `relativePath` — before calling
delete, so no new DAO query is required to know what to remove; call `PhotoStore.delete(photo)`
for each from wherever the entry's photo list is already in hand.
**Gate:** compile + tests green; a Compose/ViewModel test confirms the photo files are gone after
delete, not just the rows.

**L2 — The centre-pin picker.**
**Depends on:** nothing structurally. Lands before L4.
A fixed Compose overlay pinned to screen centre, the map panning underneath it, committed with
OK/Cancel — see decision 5 for why this shape specifically, not a draggable marker. Produces a
`LatLng` at exactly the point every current long-press site already consumes one, so converting a
site is a call-site change, not a new data flow. **Five sites convert**, not the three or four a
first look might find: the offline-region picker (`OfflineMapsPanel`), the map tab's long-press
(`MapTab`), the compact/fullscreen map tab's long-press and its icon-stack "+" button
(`CompactMapTab`), the medium/expanded results pane's own instance of the same menu, and
**`JournalTab.kt`'s own picker** (`LogEntryLocationPicker`) — the one that sits in a different
file from the other four and is easiest to miss on a search scoped to `AvailabilityScreen.kt`
alone. Trip-planning and waypoint-dropping keep their own confirm dialogs (`TripDatePickerDialog`,
`WaypointNameDialog`) unchanged — decision 5 replaces the *gesture* that leads into them, not
those features themselves. **Nothing in this codebase currently draws a draggable or centred
marker** — every existing marker (waypoints, planned trips, area labels) renders as a
`GeoJsonSource` + `SymbolLayer` whose whole feature set is replaced wholesale on every state
change, not an individually interactive annotation, so this is new interaction plumbing, not an
extension of what `SightingsMap.kt` already does.
**Gate:** compile + tests green; all five sites converted, none left on long-press.

**L3 — Optional `foundAt`.**
**Depends on:** nothing. Lands before L4.
Smaller than it first looks: log entries are not plotted on any map, not clustered by DBSCAN, and
not distance-sorted against anything — those systems (`ui/map/`, `ClusterForagingAreasUseCase.kt`,
`GeoDistance.kt`) operate exclusively on `Sighting`/`ForagingArea`, never on `MushroomLogEntry`.
The real surface: three display sites need a null branch (`LogEntryDetailScreen.kt`,
`LogEntryListScreen.kt`, `LogEntryReportScreen.kt`, each just a `"Found at ${lat}, ${lng}"` string
today); `MushroomLogEntryEntity.lat`/`lng` are non-null `Double` columns, so this needs a real
migration (`MIGRATION_6_7`, current database version is 6) making both columns nullable; and 13
call sites construct `MushroomLogEntry.draft(id, location, date)` with `location` as a required
positional parameter, needing a default or nullable update.
**Record the legacy-fixture warning explicitly when this lands:** this migration alters an
*existing* entity (`MushroomLogEntryEntity`), the same shape of change `MIGRATION_5_6` was
(Workstream A) — the legacy fixture in whichever migration test predates this column
(`MushroomLogMigrationTest`'s `LegacyForagerDatabaseV3` does not include this entity and is
unaffected; any fixture that does include it will need the same drop-index-then-column treatment
`MIGRATION_5_6`'s fixtures needed, in that order). This is the **second** occurrence of this exact
pattern in this rework — see
[`docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`](../audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md).
Two occurrences make it a pattern worth checking for by habit on every future migration that
touches an existing entity, not a one-off surprise each time.
**Gate:** compile + tests green; `MIGRATION_6_7` runs against a real v6 fixture, verified against
JUnit XML.

**L4 — The new entry flow.**
**Depends on:** L2 and L3.
Both the journal "+" and the map tab's "Log a find" (decision 8) route to the entry page rather
than a location-placement screen. `LogEntryDetailScreen` gains an **Add Location** button
alongside its existing Camera/Gallery row. **Deletes `JournalTab.kt`'s `LogEntryLocationPicker`
composable and its `pickingLocation` state machine** — the picker this rework's own scoping pulse
found to be, ironically, the only site with a real confirm/cancel step before this split — along
with the test coverage for it (`JournalTabTest.kt`'s cases exercising `LogEntryLocationPicker`/
`pickingLocation`). Trip-planning and waypoint-dropping's own dialogs are unaffected (see L2).
**Gate:** compile + tests green; an entry can be created and saved with no location, then given
one later via Add Location.

**L5 — Photo location capture.**
**Depends on:** L4.
Adds and pins `androidx.exifinterface` (not currently a dependency — pin its version against the
real artifact once added, per CLAUDE.md). **Verify against that real artifact whether it can read
GPS tags from a `content://` `InputStream` or only from a `File`/`FileDescriptor` — do not assume
from documentation.** This project's own standing discipline is `javap` against the pinned jar,
not trusting a library's docs; that discipline applies here as much as it did to the MapLibre SDK
claims elsewhere in this rework. The in-app-capture-vs-import distinction is **currently erased**:
both `TakePicture` (camera) and `PickVisualMedia` (gallery) results wrap into the identical
`ContentUriPhotoSource(uri)`, a one-field type with no "which path" tag — that distinction must be
preserved (a new field, a second `PhotoSource` implementation, or equivalent) before capture-strip
vs. import-read can be told apart at the point where either would happen. **Photo capture never
blocks on a missing location** — if coordinates are available at capture time, take them; if not,
the photo still saves and the user sets the location manually afterward (per decision 3/4's
prompt-offering framing, not a hard requirement).
**Gate:** compile + tests green; an EXIF-bearing imported photo offers its coordinates; an in-app
capture never carries GPS in its saved file.

**L6 — Tile capture.**
**Depends on:** L3 and L5. The largest piece.
**Owns the bridge** between the mushroom-log domain and `OfflineMapRepository` — confirmed twice,
across two separate scoping pulses, that `MushroomLogViewModel` and `AvailabilityViewModel` are
entirely separate `ViewModel` instances today with no mutual dependency in either direction, and
neither `MushroomLogRepository`/`RoomMushroomLogRepository`/`MushroomLogDao` nor
`CreateMushroomLogEntryUseCase`/`DeleteMushroomLogEntryUseCase` references `OfflineMapRepository`
or `OfflineRegionDao` anywhere. Both capture-on-location-set and capture-cleanup-on-entry-delete
need that exact same missing edge. **If two dispatches wire this bridge independently, they will
merge cleanly and still not work** — the same "two branches each individually correct, merge
without conflict, still broken" collision shape CLAUDE.md's own known-pitfalls section already
names for this rework (the schema-version precedent). This workstream decides where the bridge
lives (a new use case in `AppContainer` holding both repositories; a repository-level dependency;
a ViewModel-level one — three shapes with different blast radii, not evaluated against each other
by either scoping pulse) and every other piece that needs it uses that one answer.
Also in scope: `offlineRegionId` (Workstream A's column) is invisible above the Room entity layer
today — absent from the domain `MushroomLogEntry` model, from `RoomMushroomLogRepository`'s
mappers, and from any partial-update query — needs a real write path, not just a read. A
pending-capture signal needs representing: a single nullable id cannot distinguish "capture owed"
from "not applicable," since both currently collapse onto the same `null`. A connectivity signal
does not exist anywhere in this codebase (confirmed: zero `ConnectivityManager`/`NetworkCallback`
matches). A background-job mechanism is needed for the offline-then-reconnect case; WorkManager is
not currently a project dependency (independently flagged as absent twice already in this
codebase's own comments, for two different features) and no other background-job pattern besides
`TrackRecordingService`'s foreground `Service` exists — a pattern that file's own doc comment
describes as fitted to continuous, user-visible, user-initiated work, which this capture job is
not.
**Gate:** compile + tests green; a location-set/-changed entry acquires a tile without user action
beyond setting the location; a delete removes both the entry's photos (L1) and its capture region;
`docs/error-presentation-spec.md`'s belief-changing classification applied to every new failure
path this introduces.

### Open decisions to record as open

Not resolved by either scoping pulse or this dispatch — flagged so a future dispatch doesn't
silently pick one without the owner's input:

1. **Empty-location display wording** (L3) — what the three display sites show when `foundAt` is
   `null`.
2. **How a coordinate crosses the `PhotoStore` boundary** (L5) — a new field on `LogPhoto`, a new
   `PhotoStore` method returning it alongside `persist()`, or a wrapper return type from `persist()`
   itself. Three shapes reported by the scoping pulse, none chosen.
3. **Pending-capture-state representation** (L6) — a nullable timestamp column, an enum column, or
   a separate table.
4. **Tile budget (L6, blocking — not just an open preference).** Entry-captures share the same
   6000-tile `TILE_COUNT_LIMIT` budget with user-picked regions, with no filter distinguishing
   them in either the running total or the pre-flight estimate. That pre-flight warning exists
   only inside the user-initiated download UI path (`AvailabilityViewModel.onDownloadOfflineMaps`)
   — a background capture job would never pass through it. The native SDK ceiling
   (`OfflineManager.setOfflineMapboxTileCountLimit`) is already confirmed non-enforcing by
   hardware testing recorded on `OfflineMapRepository.TILE_COUNT_LIMIT`'s own doc comment. A
   background capture would therefore see none of the three safeguards a user-initiated download
   gets. This blocks L6 from being gated as "done" without an explicit owner answer on what a
   budget-exhausted background capture should do — silently skip, queue, or surface somewhere the
   user will actually see it.

---

## Payoff gap — recorded, not scoped

A finding that sits above this plan, not inside any one workstream:

**The rework does not make the feature visible.** MapLibre is now the sole renderer (the
osmdroid removal landed on `main` at `2a590dc`/`7b82588`), including the offline-region picker.
But live rendering pulls four live *raster* sources (USGS Topo, USGS Imagery Topo, OpenTopoMap,
OSM Standard), and the PMTiles archive is reachable only through a private `OFFLINE_STYLE_URL`
inside `MapLibreOfflineMapRepository.kt`. A user downloads a region and never sees their own
downloaded tiles anywhere. Workstreams 0 through L6, fully executed, leave this true.

**Standing constraint (owner, 2026-08-22):** the PMTiles Worker serves offline *downloads* only
and is never a live tile server. Where a downloaded region exists, the live map should render
that region's **local** tiles — never fetching from the Worker during browsing. Outside covered
regions, live raster remains the fallback. **This is a cost ceiling, not a technical
preference:** the Worker is on a free tier and general browsing traffic would exceed it.
Revisitable when subscriptions cover the cost, at which point Worker-served live browsing gets
added. Do not relax this as an implementation detail.

Feasibility was scoped against the pinned `maplibre-android-sdk 13.5.0` artifact.
`MapLibre.setConnected()` is too blunt — it delegates to a single process-wide
`ConnectivityReceiver` and would kill the raster fallback too. `FileSource.setResourceTransform`
is the viable path: a per-resource hook that receives the URL and a `Resource.Kind` tag, so it
can discriminate rather than block indiscriminately. **Unresolved and requiring device
observation:** what MapLibre does when `onURL` returns something unfetchable, and whether
overzoom past the archive's maxzoom 14 works under blocked-network conditions. Precedent for not
trusting the API name: this project's own `TILE_COUNT_LIMIT` finding.

Also unresolved: coverage lookup needs a **bounds rectangle**, but `OfflineRegionSummary` carries
a centre+radius **circle**, which under-reports at corners per the design doc's own Coverage
section. And `SightingsMap.kt`'s `setStyle` path rebuilds all overlays from scratch and
re-centers the camera, so a coverage-boundary style swap would be visibly disruptive under
current code.

Sequencing: strictly downstream of Workstream B. **Not yet scoped as a workstream — owner
decision pending.**

---

## What explicitly does not change

- `MapPreferencesRepository`/`DataStoreMapPreferencesRepository` stay as PR #26 built them,
  including the `PreferenceDataStoreFactory` doc comment verbatim (see Workstream 0).
- The feature set from the original scoping doc's §1 stays — per-region download, per-region
  delete, the Downloaded Maps list, staleness badging. None of it is up for renegotiation.
- `onOfflineMapsOpened()`'s GPS re-centering and cold-start region-list refresh stay — both
  encode findings that would be expensive to rediscover (a cold-start race where MapLibre's
  native store isn't finished initializing when the ViewModel first loads it). Carry the
  existing doc comment explaining the race through the reapply.
