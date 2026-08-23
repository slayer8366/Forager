# PR #26 Rework — Multi-region offline map management

**Status:** scoped and decided, **not dispatched**. Each workstream below is meant to be picked
up cold, one dispatch at a time, by a coder session with no memory of the planning
conversation that produced it.

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
"What is still open" section, now resolved below in Workstream 6). Do not re-litigate these; if
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
0. Foundation files          [CLOSED — claude/task-hwj91a @ 1726b90]
A. Schema and migration
B. The contract migration
C. Delete-block flow
D. Entry-level tile capture
```

**A and B run serial, A first — by owner decision (2026-08-22), not by dependency.** The
dependency graph makes A and B independent of each other (A touches persistence, B touches the
repository contract and its consumers; neither's surface references the other's). Owner chose
serial anyway: A is small, B is the risky, atomic, many-file unit, and landing A first keeps
bisection against a known-good `main` if B goes wrong. C depends on both A (the FK) and B (the
composable and the interface); D depends on A and C.

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

**Depends on:** Workstream 0 (landed). No forward dependency on B, C, or D.
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

**Out of scope:** the delete-block *flow* (that's C). This workstream lands the FK column, not
the UI or the blocking logic.

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

## Workstream C — Delete-block flow

**Depends on:** A (the FK) and B (the composable and the interface).

WS5's UI half. Two-step delete-block: when a region has referencing log entries, name them and
require explicit confirmation, extending `OfflineRegionsSection`'s `pendingDeleteRegion`.
Delete-block logic in `AvailabilityViewModel.kt`.

**Design constraint from #26's own design doc:** do not promise a specific reclaimed amount on
delete. Sizes don't sum to disk usage (tiles are shared/dedup'd), and deleting one of two
overlapping regions frees much less than its reported size.

**Gate:** compile + tests green; the two-step flow exercised in a Compose test.

---

## Workstream D — Entry-level tile capture

**Depends on:** A and C.

A ~1m-radius `download()` call per referencing entry, triggered only after C's delete-block
confirmation, blocking the region delete on any capture failure. New user-facing string in
`strings.xml`, matching the existing state-phrased register (`track_recording_needs_location` is
the model).

**Note:** this workstream's original text called `download(name, region, onProgress)`
"unchanged." That is true only after B lands — against `main`'s current signature it is a
breaking reference.

**Gate:** compile + tests green; capture failure demonstrably blocks the delete.

---

## Payoff gap — recorded, not scoped

A finding that sits above this plan, not inside any one workstream:

**The rework does not make the feature visible.** MapLibre is now the sole renderer (the
osmdroid removal landed on `main` at `2a590dc`/`7b82588`), including the offline-region picker.
But live rendering pulls four live *raster* sources (USGS Topo, USGS Imagery Topo, OpenTopoMap,
OSM Standard), and the PMTiles archive is reachable only through a private `OFFLINE_STYLE_URL`
inside `MapLibreOfflineMapRepository.kt`. A user downloads a region and never sees their own
downloaded tiles anywhere. Workstreams 0–D, fully executed, leave this true.

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
  including the `PreferenceDataStoreFactory` doc comment verbatim (see Workstream 1).
- The feature set from the original scoping doc's §1 stays — per-region download, per-region
  delete, the Downloaded Maps list, staleness badging. None of it is up for renegotiation.
- `onOfflineMapsOpened()`'s GPS re-centering and cold-start region-list refresh stay — both
  encode findings that would be expensive to rediscover (a cold-start race where MapLibre's
  native store isn't finished initializing when the ViewModel first loads it). Carry the
  existing doc comment explaining the race through the reapply.
