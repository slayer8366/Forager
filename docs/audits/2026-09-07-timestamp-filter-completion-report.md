# Completion report: exclude network-provider fixes from recorded tracks, by the clock they carry

Follows `2026-09-07-timestamp-filter-prebuild-report.md`. Same dispatch, same branch
(`claude/new-session-102gri` off `main` at `49b65c7`). Built as the owner decided; the owner's
corrections to the proposal are applied and listed first.

## What the owner decided

- **`excludedPointCount` as a read-computed domain field: yes.** Not a column — derived at the seam
  where the exclusion happens, so it cannot drift from the rule that produced it.
- **Threshold as proposed** (≤ 1 survivor of ≥ 2 stored, or > 75 % of ≥ 10 stored).
- **Copy, two corrections:** the Records subtitle appears **only when the threshold trips** — the
  ordinary user never sees the words "network fixes"; and the live Snackbar's "isn't being drawn"
  overstated the 76 % case, so it either fires only on the near-total case or is reworded to be
  true in both. **My call: reworded**, and it fires on the fraction condition (see Item 3).
- **The origin waypoint finding** matters more than "out of scope": it is **queued as its own small
  dispatch** — the origin is seeded from the live fix stream, and that seeding could check the fix's
  own timestamp before accepting it. **Not built here.** Recorded below so it is not lost.
- **The four GPX exports are the last unfiltered evidence and stay off version control.** The
  repository is public and they are precise traces of the owner's own area. Recorded, and `*.gpx`
  is now in `.gitignore` with that reason on it — the one line beyond the letter of "record it",
  taken because it is the cheapest way to make the decision hold.

## What was built

### Item 1 — the rule, at the seam

- **`domain/NetworkProviderFix.kt`** (new, pure): `TrackPoint.isNetworkProviderFix()` =
  `timestampEpochMillis % 1_000L != 0L`; `excludeNetworkProviderFixes(points)`;
  `Track.storedPointCount`; the two threshold predicates `isMostlyNetworkFixes()` (> 0.75 of ≥ 10)
  and `hasNoUsablePoints()` (≤ 1 survivor of ≥ 2); `networkFixExclusionIsLarge()`;
  `networkFixExclusionNote(track)` (the row copy, `null` in the ordinary case); the two constants;
  and `NETWORK_FIXES_RECORDING_NOTICE`. The file's doc carries the evidence chain, the
  `Location.getTime()` fact, why the seam and not the source, and the device-property limit.
- **`domain/model/Track.kt`**: `excludedPointCount: Int = 0`, documented as derived at the seam,
  never a column.
- **`data/repository/RoomTrackRepository.kt`**: `TrackEntity.toDomain(rows)` maps the rows, applies
  the rule, and sets `excludedPointCount = stored.size − kept.size`. The only change to the seam;
  nothing is written.
- **Not touched:** the sampler, the service, the tracker, the DAO, any row.

### Item 2 — Cartography's snapshot

- **`CartographyViewModel.onOpenEntry`** now publishes the entry **after** the day's trip report
  loads: every track decision is recomputed from the track as the seam returns it
  (`CartographyEntry.withRecomputedTrackSnapshots`, new, pure); if anything changed the entry is
  written back through `saveEntry` (a failure is logged and the corrected figures are still shown —
  the next open recomputes again), the in-memory lists are updated, and the entry is published with
  `hasUnsavedChanges = false`. If the trip report cannot load, the entry opens uncorrected with the
  existing error message rather than not at all. A decision whose track is gone keeps its figure.
- **`CartographyEntryTrackRefEntity`'s doc** now carries the exception beside its rule: distance,
  duration and point count are recomputed on open and written back; a cached distance is not
  authored content and the cached number was wrong; name, kept and row existence are never touched.
- Nothing else persists a derived distance or count (re-searched; unchanged). `pointCount` now has
  its first reader (Item 3's "no usable points" suffix) — reported in the pre-build report as
  dormant; giving it a reader was the smallest way to keep an empty snapshot row from being silent.

### Item 3 — observable when it does a lot

- **Records row** (`TrackExportPanel.trackSubtitle`, now `internal`): "N points" as before;
  when large, **"N points · M more not shown (network fixes)"**; when emptied, **"No usable points —
  all M fixes were from the network provider"** in place of "0 points". Only when the threshold
  trips — asserted in the ordinary case.
- **Cartography rows** (`CartographyEntryEditScreen.trackExclusionSuffix`, new): candidate rows
  append the live track's note; decided rows append the live track's note when the day's tracks are
  loaded, else " · no usable points" when the snapshot's `pointCount` is 0; the report screen's rows
  use the snapshot path. Empty in the ordinary case.
- **Live recording** (`TrackRecordingViewModel.beginPolling`): the first poll that sees
  `isMostlyNetworkFixes()` on the active track sets `networkFixesNotice` once per recording (a
  `Boolean` in ViewModel state, reset on start; no column); `AvailabilityScreen` shows it through
  the map's existing Snackbar host, Long, no action, keyed on its id — the same shape as the
  trip-start warning, whose type is now `RecordingNotice` for both. Copy, true at 76 % and at 100 %:
  **"Most of this track's fixes look like network fixes rather than GPS, so little or none of it is
  being drawn. It is still being recorded."** It fires on the fraction condition only, not on
  "≤ 1 survivor of ≥ 2", because at a cold start the network provider typically answers before GPS
  and the first poll of an ordinary recording could see two stored points with one survivor; the ten-
  point minimum is what stops that.

### Queued, not built — the origin waypoint

`TrackRecordingViewModel.createOriginWaypoint` is fed by the live fix stream's first fix that passes
the mode's accuracy gate (`:309`); a network fix with 20 m accuracy passes the HIGH ceiling, so the
origin the HUD navigates back to can be a network fix, tens of metres off, and its waypoint carries
the app's clock (`createdAtEpochMillis`), so the rule cannot reach it afterwards. **Fixable going
forward: the seeding can test the fix's own `timestampEpochMillis` with the same predicate before
accepting it.** Owner: its own small dispatch. Not touched here.

## Tests

- **`NetworkProviderFixTest`** (new, pure): millis exactly zero kept (including `0L`); 1 / 500 / 999
  excluded; a mixed track loses exactly its sub-second points in stored order; an all-whole-second
  track loses nothing; an all-sub-second track comes back empty; the evidence tracks' own
  proportions (17/8, 32/10, 8/2, 33/0) are **not** large; 19 of 25 is, 18 of 25 is not, 8 of 10 is,
  8 of 9 is not (under the minimum); the no-usable-points cases; the row note's three literals.
- **`RoomTrackRepositoryTest`**: new seam test — `0L` kept, `2_001L` dropped, `5_000L` kept,
  `excludedPointCount == 1`, and the DAO's rows still `[0, 2001, 5000]` with their latitudes.
  **Two existing tests changed in their fixture data only** (see "Existing tests" below).
- **`NetworkFixExclusionPerConsumerTest`** (new, Robolectric, real in-memory Room): one stored track
  of five rows (three whole-second GPS points 0.001° apart, two sub-second fixes 3.3 km north);
  **per consumer, through its real entry point**: the repository (`getById`/`getAll`) returns the
  three survivors with count 2; `GetTracksUseCase` + the Records subtitle read "3 points ·
  recording" with no note (2 of 5 is ordinary); `TrackGpxExporter` writes three `<trkpt>`, no
  `45.03`, no `.500Z`/`.250Z`, both whole-second times present; `GetDerivedTripUseCase` returns the
  survivors; `GetCartographyEntryMapDataUseCase` draws the survivors; `GetTripReportOfflineRegionsUseCase`
  does **not** count a region whose footprint holds only the excluded points; and the live
  `TrackRecordingViewModel` poll's `breadcrumbPoints` are the survivors. **After every one: the
  five rows on disk, unchanged.**
- **`CartographyViewModelTest`**: a stale unfiltered-era snapshot (6789.0 m / 99 s / 5 points) is
  planted in the database and the list reloaded from disk; opening the entry shows and persists
  **222.39 m / 10 000 ms / 3 points** (by hand: two 0.001° steps of 111.19 m; the spikes excluded
  before summing), with no unsaved-changes flag.
- **`TrackExportSubtitleTest`** (new, pure): ordinary rows unchanged ("9 points" at 8 excluded;
  "0 points · recording"); "1 point · 12 more not shown (network fixes)"; "6 points · 19 more not
  shown (network fixes) · recording"; "No usable points — all 12 fixes were from the network
  provider".
- **`AvailabilityScreenSettingsPanelTest`**: the emptied track's row shows the literal and no
  "0 points" node.
- **`AvailabilityScreenMapIconStackTest`**: the notice shows once as a Snackbar (node count 1).
- **`TrackRecordingViewModelTest`**: the poll raises the notice once for a 3-survivor / 12-excluded
  track and does not re-issue it on the next poll; 8 of 17 raises nothing.

**Not covered by a test, said plainly:** the Cartography edit-screen and report-screen row suffixes
(`trackExclusionSuffix`) — the suffix function is a two-line pure function reading the tested note
and the snapshot count, but no screen test drives a Cartography row with an emptied track. The
Records row and the note itself are covered.

### Existing tests changed

Two tests in `RoomTrackRepositoryTest` used sub-second timestamps as arbitrary sample values
(`100L`/`200L`, and `i.toLong()` for 0…999), which the seam now excludes by design — they failed
with `expected:<[…]> but was:<[]>` and `expected:<1000> but was:<1>`. **Their fixture data moved to
whole seconds (`100_000L`/`200_000L`, `i × 1_000L`); their assertions are unchanged** (timestamp
ordering across insertion order; 1000 points batched cheaper than one at a time), with a comment
naming the reason. Not a silenced or weakened test; reported because the standing rule says an
existing test that starts failing is reported, and the change is fixture data, not a claim.

A third, found by the full suite rather than the targeted run: `TrackWaypointMigrationTest`'s
post-migration usability check seeded one point at `1_500L` and asserted the whole `Track` read
back through the real repository. Stamp moved to `1_000L`; assertion unchanged, comment added.
Three fixtures in total, all sample timestamps, no assertion touched.

## Reverted variants

Each a one-line sed on the source, the build log checked for `e:` / `compileDebugKotlin FAILED`
before results were read (none in any of the eight — every reverted build compiled and ran), the
source restored from a copy saved before the sed, and every forward edit grepped for afterwards
(all present — both CLAUDE.md failure modes).

| Revert | Predicted | Observed |
|---|---|---|
| Seam off (`val kept = stored`) | every per-consumer test, the seam test, the Cartography recompute | 7 failures: survivors lists show all five points, counts `expected:<3> but was:<5>`, recompute `expected:<222.39> but was:<12898.63>` (the unfiltered sum with both 3.3 km spikes) |
| Boundary (`% 1000 >= 0` — zero excluded too) | zero-kept, mixed, honest-track tests | 3 failures, exactly those |
| Fraction 0.75 → 0.2 | the evidence-proportion test, the 76 %/72 % test, the ViewModel's ordinary case | 3 failures; the ordinary 8-of-17 poll now raises the notice: `expected null, but was:<RecordingNotice(id=1, …)>` |
| Minimum stored 10 → 2 | the "8 of 9 is under the minimum" assertion | 1 failure, that test |
| Recompute removed (`val corrected = entry`) | the Cartography open test | 1 failure: `expected:<222.39> but was:<6789.0>` |
| `networkFixesNoticeShown = true` deleted | the once-per-recording assertion | 1 failure: "the notice must not be re-issued on the next poll" |
| Row note nulled in `trackSubtitle` | the two subtitle tests and the Records screen test | 3 failures: `expected:<1 point · 12 more…> but was:<1 point>`, `…but was:<0 points>`, and the screen node absent |
| Snackbar effect short-circuited | the icon-stack notice test | 1 failure: `ComposeTimeoutException … 5000 ms` |

Every failure names a value only its own revert could produce; none is a stale result.

## Full suite

First full run after the build: **1201 tests, 1 failed** — `TrackWaypointMigrationTest`'s "planned
trips and mushroom log entries survive the 4 to 5 migration intact, and the new tables are usable",
whose seeded sample point carried `timestampEpochMillis = 1_500L` and was read back through the real
repository (`expected … points=[TrackPoint(… 1500)], excludedPointCount=0 but was … points=[],
excludedPointCount=1`) — the same fixture class as the two repository tests, found only by the full
run. Its stamp moved to `1_000L`; the assertion (the whole `Track` equality, `excludedPointCount = 0`
included) is unchanged, with a comment naming the reason. Recorded below under "Existing tests
changed" as the third.

Second full run: **1201 tests, 0 failed, 24 skipped** (1178 + 23: 9 predicate, 3 subtitle, 5
per-consumer, 1 seam, 1 Cartography recompute, 1 Records row, 1 Snackbar, 2 ViewModel notice). Skip
set compared by (class, name) against the CI `SKIPPED_TESTS_ALLOWLIST` parsed from
`.github/workflows/ci.yml`: byte-identical, 24 = 24. The `JournalTabTest` "From Album" flake did not
fire in either run.

## Device checks the owner must walk

1. A previously starbursted track (A or B) draws clean and its distance figure drops; in
   Cartography, after reopening an entry that keeps it, the row reads the new figure.
2. **The airplane-mode control track (D) is unchanged** — same line, same distance, no suffix on
   its Records row. The honest-track case in the field.
3. A fresh recording on the owner's phone draws as before with no Snackbar.
4. Records: no row on the owner's device carries a "network fixes" suffix (A, B, C are 47 / 31 /
   25 %, all under the threshold).

## Required disclosure

**Confirmed:** everything under "What was built" and "Tests" by reading and running; the seam and
its callers on `49b65c7` (pre-build report); the by-hand figures 111.19 m per 0.001° of latitude at
the mean radius the app uses; that `CartographyEntry.draft` exists for building a test entry.

**Inferred:** the cold-start network-before-GPS ordering that motivates firing the live notice on
the fraction condition only (general behaviour of `LocationManager`, not measured here); that a
non-aligned device lands near 100 % excluded.

**Could not determine:** anything about a second device; whether the earlier radios-off excursion
carried a sub-second stamp.

**Premises in this dispatch that were wrong:** none found. One consequence stated in the pre-build
report and now in force: every GPX export is filtered from here on, so an export can no longer
evidence the rule.

**Decided without cover, flagged:** the reworded live notice and firing it on the fraction condition
only (the owner offered either); `*.gpx` in `.gitignore`; giving `pointCount` a reader; the
Cartography rows' fallback " · no usable points" wording; the fixture change in two existing
repository tests; leaving the Cartography row suffix without a screen test.
