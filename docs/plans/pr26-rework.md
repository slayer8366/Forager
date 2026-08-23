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

Six workstreams, one dispatch each, **a build-and-test gate between every one** — bundling them
means a 45+-file diff nobody can review. Workstream 6 (the entry-level tile-capture feature) is
new relative to the original scoping and depends on Workstreams 2 and 5 being done first (it
needs the renumbered migration and the relational `OfflineRegionEntity`/log-entry link to exist).

```
1. Reapply onto main
2. Migration renumbering (5 → 6)
3. OfflineMapRepository reconciliation
4. Drift corrections
5. OfflineRegionEntity relational design + delete-block flow
6. Entry-level tile capture (new feature)
7. Error-presentation compliance
```

Workstream 7 goes last because it touches sites the earlier steps move around (the migration
renumber changes file paths and test fixtures; the relational-design and capture workstreams
add brand-new error sites that themselves need error-presentation treatment; doing it earlier
would mean redoing it).

---

## Workstream 1 — Reapply onto `main`

**Decision:** reapply, not rebase (owner decision, 2026-08-23). Take current `main`
(`3b6021b`), port PR #26's feature across as fresh commits, using
`claude/plan-implementation-rjzmkr` as reference material only — do not `git rebase` or replay
its 10 commits. Cut a new branch from `main` at `3b6021b` for this work.

**What ports across largely as-is** (confirmed unaffected by drift, per the pulse):
- `OfflineRegionDao.kt`, `OfflineRegionEntity.kt` (schema/version numbers change — see
  Workstream 2, not this one).
- `MapPreferencesRepository.kt` / `DataStoreMapPreferencesRepository.kt` and their test — **port
  verbatim, including the doc comment on why `PreferenceDataStoreFactory.create` is used instead
  of the `by preferencesDataStore(name = ...)` singleton delegate** (it breaks Robolectric
  isolation across `@Test` methods — a debugging session already paid for on the original
  branch, now also cited in `CLAUDE.md`'s "Room for data that relates; DataStore for flat
  settings" rule). This pattern is settled; do not redesign it.
- `EstimateOfflineTileCount.kt`, `OfflineRegionStaleness.kt`, `MapLibreStorage.kt` and their
  tests.
- The `OfflineMapsPanel` / `OfflineRegionsSection` / `OfflineMapStatusContent` UI in
  `AvailabilityScreen.kt` (confined to lines ~1191–1690 of PR #26's version — nothing in the
  List/Map/Seasonal tabs, no dialogs, no compact map scaffold).

**What needs rework, not just porting** — each is its own workstream below: the migration
number, five of six `IOException`/`onError` sites in `MapLibreOfflineMapRepository.kt`,
`OFFLINE_MAX_ZOOM`, 14 test files' constructor arguments, `OfflineRegionEntity`'s relational
design, and every `error.message ?: "..."` site this feature touches.

**Gate before moving to Workstream 2:** the reapplied code compiles against current `main`
(it will not pass all tests yet — Workstreams 2–4 aren't done — but it should compile, modulo
the known-broken sites named above).

---

## Workstream 2 — Migration renumbering

PR #26 declares `ForagerDatabase.version = 5` with `MIGRATION_4_5` creating `offline_regions`.
`main` already has `version = 5` via a *different* `MIGRATION_4_5` (from PR #32/#33), creating
`tracks` / `track_points` / `waypoints`. Both are individually correct against the same v4 base
and would merge cleanly and produce a wrong database — this is the exact collision CLAUDE.md's
"Verify your base branch before you start" rule was written to record.

**Required changes:**
- `offline_regions` becomes `MIGRATION_5_6`, `ForagerDatabase.version = 6`.
- `ForagerDatabase.kt`'s `entities` list keeps `TrackEntity`/`TrackPointEntity`/`WaypointEntity`
  (from `main`) *and* adds `OfflineRegionEntity` — PR #26's branch predates tracks/waypoints
  entirely, so this list needs assembling fresh, not diffed mechanically.
- The committed schema JSON moves from `app/schemas/com.forager.app.data.local.ForagerDatabase/5.json`
  (which is track/waypoint data on `main`, not PR #26's offline-region data) to a new `6.json`
  capturing the offline-region shape.
- `OfflineRegionMigrationTest` must build a real **version-5** database (matching `main`'s
  current tracks/waypoints schema) and migrate it to version 6 — not version-4-to-5 as PR #26's
  copy does. This is not a find-and-replace: the test fixture's starting shape changes, not just
  the version numbers in it.
- `MushroomLogMigrationTest`'s one-line change (`.addMigrations(MIGRATION_3_4)` →
  `.addMigrations(MIGRATION_3_4, MIGRATION_4_5)` in PR #26) needs re-deriving against `main`'s
  current migration list, which by this point also includes whatever migration added
  tracks/waypoints.

**Gate:** `:app:testDebugUnitTest` passes for every migration test (`MushroomLogMigrationTest`,
the existing tracks/waypoints migration test on `main`, and the new `OfflineRegionMigrationTest`
against a real version-5 fixture).

---

## Workstream 3 — `OfflineMapRepository` reconciliation

The overlap between PR #26 and PR #34 (already on `main`) is narrower than it looks:

- **PR #34** changed only the `onError` callback inside
  `MapLibreOfflineMapRepository.downloadToCompletionSuspend` — raw SDK diagnostic text
  (`"${error.reason}: ${error.message}"`) now goes to `Log.w`, and the thrown `IOException`
  carries the fixed literal `"Offline map download failed."` This is already on `main`; do not
  redo it, only preserve it through the reapply.
- **PR #26** doesn't touch that callback at all — its copy is byte-identical to the pre-#34
  baseline. It rewrites the *surrounding* methods (`download`/`delete`/`getStatus` →
  `download`/`deleteRegion`/`listRegions`), adds the `offlineRegionDao` constructor dependency,
  and moves `OFFLINE_MAX_ZOOM`/`OFFLINE_MIN_ZOOM` from private constants to
  `OfflineMapRepository.MIN_ZOOM`/`MAX_ZOOM` companion constants.

**Required work:** apply PR #34's treatment — log the SDK diagnostic text via `Log.w`, throw a
fixed-literal `IOException` — to the **other five** `onError`/`IOException(...)` sites in this
file that PR #26 carries unchanged from the pre-#34 baseline. (The pulse enumerated six such
sites total; only the `downloadToCompletionSuspend` one is already fixed on `main`.) Each fixed
literal should describe what failed in the same neutral, state-describing register as
`"Offline map download failed."` — e.g. a status-check failure, a delete failure, a region-list
read failure — not a single reused string for all five.

**Gate:** grep `MapLibreOfflineMapRepository.kt` for `error.reason`/`error.message`/any raw SDK
string reaching an `IOException`'s message — zero hits. `:app:testDebugUnitTest` passes.

---

## Workstream 4 — Drift corrections

- **`OFFLINE_MAX_ZOOM`:** PR #26's base had `14.0`; `main`'s current value is `15.0` (changed in
  an unrelated commit neither PR touches). Take `main`'s value — `15.0` — when porting
  `OfflineMapRepository.MAX_ZOOM`.
- **`AvailabilityViewModel` constructor:** not stale on the other 14 parameters — `main`'s
  constructor is the same 14 PR #26's base had (tracks/waypoints live in the separate
  `TrackRecordingViewModel`, untouched by this feature). PR #26 adds `mapPreferencesRepository`
  as a 15th; this addition still applies cleanly. Re-verify the exact current parameter list and
  order in `AvailabilityViewModel.kt` on `main` before wiring the 15th in, rather than trusting
  this document's memory of it.
- **14 modified test files** (`AvailabilityScreen*`/`AvailabilityViewModel*`) carry
  constructor-argument diffs against PR #26's base — re-derive each against `main`'s current
  constructor signatures rather than reapplying the stored diff mechanically.
  `AvailabilityViewModelOfflineMapsTest.kt` is substantially rewritten in PR #26 (+371 lines) and
  needs the most attention; verify it against the reconciled `OfflineMapRepository` interface
  from Workstream 3, not PR #26's original.
- **File continuity:** confirmed nothing PR #26 modifies has been deleted or renamed on `main`
  as of the pulse — re-check this is still true at dispatch time, since more commits may have
  landed since.

**Gate:** `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` both clean.

---

## Workstream 5 — `OfflineRegionEntity` relational design + delete-block flow

**Decision (owner, 2026-08-23):** design the relationship now, in this migration, not
retrofitted later. `MushroomLogEntryEntity` gets a nullable foreign key to `offline_regions`
(column name and exact FK/index declaration are this workstream's to write — follow this
project's existing Room conventions, e.g. `MIGRATION_4_5`'s style for `mushroom_log_entries`/
`log_photos` in `Migrations.kt` on `main`), created as part of `MIGRATION_5_6` alongside the
table itself.

**How the link is set (owner decision, 2026-08-23):** automatic, not user-chosen. A log entry
created while its coordinates fall inside a downloaded region's bounds references that region.
The user should not have to think about offline-map bookkeeping while logging a find. If an
entry's coordinates fall inside more than one downloaded region, or inside none, that's this
workstream's to resolve with an explicit rule (e.g. nearest-center, or first-match, or simply
"no region, no link") — don't leave it undefined.

**Delete-block flow — two-step, both parts required:**

1. **Block by default, name the entries.** A region delete is refused while any log entry still
   references it. The refusal names the specific referencing entries — the error-presentation
   spec's "tell the user what's actually true" rule applies here the same as to a caught
   exception; a generic "this region is in use" message is not sufficient.
2. **Two explicit ways past the block:**
   - Edit or remove the referencing log entries directly (clears the reference; delete then
     proceeds normally through the existing `OfflineRegionsSection` confirmation dialog).
   - Or: a **separate, explicit confirmation** (owner decision, 2026-08-23) — not a silent
     auto-capture-then-delete. The dialog states which entries are affected and what will
     happen: *"This region is used by [named entries]. Delete it and keep their maps?"* Only on
     confirmation does Workstream 6's capture mechanism run per referencing entry, followed by
     the region delete. Extend `OfflineRegionsSection`'s existing `pendingDeleteRegion`
     confirmation dialog (PR #26) for this rather than building a new one.

**Rejected explicitly, do not implement:** cascading the delete to remove referencing log
entries, and silently nulling the reference with no user-visible record of what was lost.

**Gate:** a Robolectric test drives the real delete flow (button tap → dialog → confirm), not a
hand-called ViewModel method, per `CLAUDE.md`'s Testing section — asserts the block fires with
named entries when references exist, and that delete proceeds normally when none do.

---

## Workstream 6 — Entry-level tile capture (new feature)

Not in the original five-workstream scope — added 2026-08-23 specifically so the design
wouldn't go stale before being written down. Depends on Workstream 5's FK existing.

**Capture mechanism (owner decision):** a fresh small-radius download, reusing
`MapLibreOfflineMapRepository.download()` unchanged — call it with a **~1 meter radius** region
centered on the log entry's coordinates instead of a user-picked one. This is the same
`OfflineManager.createOfflineRegion` path the existing per-region download already uses, just a
smaller `Region`; no new MapLibre API surface, no `javap` verification needed. Each captured
entry gets its own `OfflineRegionEntity` row, independent of the region it's replacing coverage
for.

**Explicitly rejected:** extracting already-cached tiles for that point out of the big region's
store before it's wiped. Nothing in this codebase has verified MapLibre's `OfflineManager`/
`OfflineRegion` API exposes tile-level extraction (its interface is region-shaped — bounds and
zoom — not raw tile access); this project's own discipline (see `MapLibreOfflineMapRepository.kt`'s
and `MapPreferencesRepository`'s doc comments) is to check such a claim via `javap` against the
pinned `org.maplibre.gl:android-sdk` artifact before relying on it, and that check has not been
done. The fresh-download path avoids needing it and is confirmed feasible without further checks.

**Trigger:** runs once per referencing entry, only after the user confirms the Workstream 5
delete-block dialog ("...delete it and keep their maps?") — not at log-entry creation time, and
not automatically without confirmation.

**Failure handling (owner decision, 2026-08-23) — this is the one that matters:** a fresh
capture download needs network. If a capture fails and the region delete proceeded anyway, the
user would believe their entries' maps are preserved when they are not — belief-changing under
`docs/error-presentation-spec.md`'s own test, the same shape as a failed track recording.
**Block the region delete on any capture failure.** Do not proceed with partial success (some
entries captured, region deleted, others lost). Report in state terms, not as a generic error —
e.g. *"Needs a connection to save entry maps first."* — matching the register
`track_recording_needs_location` already established in `strings.xml` for the same kind of
"this requires connectivity/permission before the action can proceed" case.

**Gate:** a test exercises capture failure (simulated network failure on one of several
referencing entries) and asserts the region delete does not proceed, no entries are left with a
region deleted out from under them, and the failure message is state-phrased rather than
exception text.

---

## Workstream 7 — Error-presentation compliance

Last, because earlier workstreams create or move the sites this one treats.

**Confirmed passthrough sites in `AvailabilityViewModel.kt`** (PR #26's copy, as of the pulse):
all ten pre-existing `error.message ?: "..."` sites PR #34 already eliminated on `main`
(`taxonSearchErrorMessage`, `sightingsErrorMessage`, `seasonalPatternErrorMessage`,
`errorMessage`, `conditionsErrorMessage`, `tripWindowsErrorMessage`, `plannedTripsErrorMessage`
×3) will already be gone after Workstream 1's reapply, *provided the reapply is done against
current `main` rather than by porting PR #26's file wholesale* — verify this explicitly rather
than assuming the reapply got it right.

**New sites PR #26 introduces, not on `main` at all — fix these:**
```kotlin
_uiState.update { it.copy(offlineRegionsErrorMessage = error.message ?: "Couldn't read offline regions.") }   // loadOfflineRegions
_uiState.update { it.copy(offlineDownloadStatus = OfflineMapStatus.Failed(error.message ?: "Couldn't download offline maps.")) }  // onDownloadOfflineMaps
_uiState.update { it.copy(offlineRegionsErrorMessage = error.message ?: "Couldn't delete that region.") }     // onDeleteOfflineRegion
```
Replace each `error.message ?: "..."` with the fixed literal alone, per the spec, mirroring how
PR #34 treated the equivalent sites elsewhere in this same file.

**Belief-changing classification (owner decisions, 2026-08-23):**
- **A failed offline-map download is belief-changing.** Same shape as the track-recording case
  — a user who believes they have offline coverage for an area they're about to lose signal in
  is carrying a false assumption into the field. Render as a message phrased as state (matching
  `startRecordingErrorMessage`'s Toast-with-clearing shape, or `waypointsErrorMessage`'s inline
  error-color render — pick whichever this UI's existing `OfflineDownloadStatusContent` shape
  fits better), not a plain empty state.
- **A failed region-list load is not belief-changing.** Neutral empty state is sufficient —
  matching `conditionsErrorMessage`/`loadErrorMessage`'s "X unavailable" vocabulary from PR #34,
  not an error-colored render.
- **A failed entry-level tile capture is belief-changing** (Workstream 6) — covered there, not
  duplicated here; both workstreams' error sites should read as one consistent treatment by the
  time this workstream is done, not two independently-invented ones.

**Gate:** repeat the pulse's own verification — a whole-`app/src/main` grep for `.message`
reaching a state field or a rendered string, confirming every hit is either a domain field
carrying an already-fixed literal or a `Log.w`-only diagnostic read. `:app:testDebugUnitTest`
and `:app:assembleDebug` (including `verifyNothingTestOnlyReachesTheApk`) both clean.

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
