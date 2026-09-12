# Phase 0 inventory: the features the store description promises

**Date:** 2026-09-11
**Dispatch:** "Features the store description promises", Phase 0 (read-only)
**Base:** `claude/beta-signup-website-g3t91u`, cut from `main` at `4957675`
**Scope:** read-only. No code, test or dependency changes.

---

## §2 settlement

- **Repository:** `slayer8366/Forager`, confirmed by the owner. `slayer8366/forager-bak` is
  stale, confirmed by the owner and by the repository being archived on GitHub.
- **Branch:** the dispatch left this blank ("owner fills in"). This session's assigned branch
  was used. If a different branch was intended, this report's base is wrong and it should be
  re-cut, not patched.
- **Retired package root:** a ruling is recorded
  (`docs/audits/2026-09-09-package-rename-completion-report.md`, indexed in
  `docs/audits/README.md`), with a CI gate against the retired term. The term is not written
  here; package identifiers were masked before any of them reached this session's output.
- **Base state:** `ForagerDatabase` is at **version 15**. `FungiIndexDatabase` at **1**. The
  migration chain runs `3_4` through `14_15`, contiguous.

### The `MIGRATION_4_5` collision is resolved, by sequencing

| Migration | Creates |
|---|---|
| `MIGRATION_4_5` | `tracks`, `track_points`, `waypoints` |
| `MIGRATION_5_6` | `offline_regions` |
| `MIGRATION_10_11` | `cartography_entries` + `_find_refs`, `_photo_refs`, `_track_refs`, `_waypoint_refs`, `_offline_region_refs` |

The two competing v4→v5 bodies became 4→5 and 5→6. CLAUDE.md's note that this is "still
unresolved, with 51 commits of drift" is out of date and should be corrected there.

---

## §3.1 Stranded-work check

`forager-bak` was cloned at depth 1 (HEAD `2d68998`, 2026-09-09) and compared by file tree and
symbol name, not by `git log`. It holds **433 `.kt` files; this repository holds 433**. For the
two claims reported absent below, neither exists there either. Nothing is stranded. This is a
copy, not a divergent line of work.

---

## §3.2 / §3.3 Per claim

| ID | Claim | Status | Where |
|---|---|---|---|
| C1 | iNat species search | **Present** | `data/remote/INaturalistApi.kt:35-39` |
| C2 | Fruiting lag **and its chart** | **Present** | `ui/availability/AvailabilityResultsUi.kt:373-397` |
| C3 | Track recording, live breadcrumbs | **Present** | `ui/map/MapSlot.kt:316` → `ui/map/SightingsMap.kt:184` |
| C4 | Waypoint **navigation** | **Absent** | no selection path; see below |
| C5 | Return to start | **Present**, but see `returnWalkingTime` | `ui/track/TrackRecordingViewModel.kt:490` |
| C6 | Sundown alerts | **Absent**, with a tested calculator sitting unused | `domain/CivilTwilight.kt` |
| C7 | Offline maps | **Present** | `map/MapLibreOfflineMapRepository.kt` |
| C8 | Waypoints | **Present** | `ui/track/TrackRecordingViewModel.kt:365,440` |
| C9 | Journal links | **Present** | five ref tables, all with DAO readers |
| C10 | Cartography | **Present, reachable, tested** | `ui/log/Cartography*` (45 files name it) |
| C11 | GPS accuracy | **Partial** | `domain/LiveFixGate.kt:68`, `sensor/AndroidCompassProvider.kt` |
| C12 | Location lifecycle | **Gated** | `ui/availability/AvailabilityViewModel.kt:129,161` |

### C1 iNaturalist search

`INaturalistApi.kt` sends `radius` (km), `month`, and one of `iconic_taxa` / `taxon_id`
(mutually exclusive per call, `:15`), plus `without_taxon_id`. Fungi is the only category, an
owner decision recorded at `ui/availability/AvailabilitySearchUi.kt:498`. Reachable through
`data/repository/INaturalistMushroomRepository.kt:16`.

### C2 Fruiting lag, including the chart the dispatch could not find

`domain/ComputeFruitingLagDistributionUseCase.kt` produces
`domain/model/FruitingLagDistribution.kt`, rendered by `SeasonalPatternContent`
(`AvailabilityResultsUi.kt:309`) and a **hand-rolled Compose `Canvas` bar chart** at `:373-397`,
with no charting dependency. Ten call sites in `main/`. Phase 5 as written is a UI that exists.

### C4 Waypoint navigation: absent

Searches run: `navigateTo|selectedWaypoint|targetWaypoint|navTarget` across `main/` (0 hits);
files containing both "waypoint" and any of `onSelect|onWaypointClick|chooseWaypoint` under
`ui/` (0 files); the same symbols across `forager-bak` (0 files). Bearing and distance machinery
exists (`domain/NavigationReadout.kt`, `domain/model/ReturnToStartInfo.kt:18`) but is wired only
to the fixed return target. A user cannot pick an arbitrary waypoint and navigate to it.

### C5 Return to start, and the caller that still is not there

`TrackRecordingViewModel.returnToStart` (`:490`) targets **the origin waypoint if one exists,
otherwise the first breadcrumb** (`:495-497`), an owner decision recorded in that comment. So:
both, with a precedence, not one or the other.

**`returnWalkingTime` still has no production caller, and the count that suggested otherwise was
mine.** A first pass counted nine occurrences of the symbol in `main/` and read that as callers.
Grepping for the call form `returnWalkingTime(` returns **one** occurrence in `main/`: its own
declaration at `domain/ReturnWalkingTime.kt:57`. The other eight are KDoc cross-references.
Nine call sites exist in `app/src/test/`. `ui/track/TrackRecordingUiState.kt:70` says it plainly:
the value "still has no caller, on purpose." CLAUDE.md was right. Phase 6 remains blocked on
exactly what it was blocked on.

### C6 Sundown: absent, and the reason it matters is the opposite of expected

Three notification channels are registered, all of them: `off_track_alert`,
`off_track_alert_v2`, `track_recording`. No sundown channel, no alert, no scheduling.

But `domain/CivilTwilight.kt` is a complete NOAA solar-position implementation in pure Kotlin,
with no Android imports and no network, tested in
`app/src/test/.../domain/CivilTwilightTest.kt` against midsummer, midwinter, and a London
midsummer-midnight case. It has **zero references in `main/`**. The map's night mode is decided
elsewhere (`ui/map/MapSlot.kt:310`, from `renderMode.night`).

This is the second unreachable pure-domain function found in this inventory, alongside
`returnWalkingTime`, and it bears directly on the Phase 1 spec. That spec says to "use a
published algorithm (NOAA's solar calculator equations) or a pinned library". The NOAA equations
are already here, dependency-free. It also specifies a sealed result of `Times(sunset,
civilDusk)` / `NoSunset` / `NoSunrise`, and `CivilTwilight`'s own header records the decision
**not** to compute crossing times, precisely because that forces a polar special case, which
solar altitude does not have. Phase 1 as written would re-litigate a documented decision in the
opposite direction. That may still be right, since an alert needs a time and a night-mode switch
needs only a threshold, but it is a decision to make knowingly.

### C7 Offline maps

Download and delete through `domain/OfflineMapRepository.kt:47` /
`map/MapLibreOfflineMapRepository.kt:145`; render through the offline style. The 2026-09-11
MapLibre/PMTiles audit findings were noted and not acted on, per the dispatch.

### C8 Waypoints

`TrackRecordingViewModel.addWaypoint` (`:440`) and `createOriginWaypoint` (`:365`);
`TrackDao.updateOriginWaypointId` (`:62`).

### C9 Journal links

All five reference tables have DAO readers, not just columns:
`CartographyEntryDao` exposes `getWaypointRefs` (`:31`), `insertWaypointRefs` (`:49`),
`deleteWaypointRefsForEntry` (`:64`) and siblings. Reference counts in `main/`: find 10,
photo 12, track 14, waypoint 14, offline_region 10. Phase 2 is not blocked and looks built.

### C10 Cartography

45 source files name it. `CartographyScreen` is reached from `ui/log/JournalTab.kt:49,147,151`.
Five test classes drive it, including `CartographyScreenTest`, `CartographyViewModelTest` and
`CartographyEntryReportScreenMapTest`.

### C11 GPS accuracy: partial

Present, against the dispatch's G1 options:
- **(b) explicit operating limit:** `domain/LiveFixGate.kt:68`, `LIVE_FIX_MAX_ACCURACY_METERS = 50f`.
- **(e) provider surfaced:** network-provider exclusion is computed and shown to the user in at
  least four surfaces (`ui/track/TrackRecordingUiState.kt:98`, `ui/track/TrackExportPanel.kt:126`,
  `ui/log/CartographyEntryEditScreen.kt:636`, `ui/availability/AvailabilityScreen.kt:597`).
- **(d) compass status:** `sensor/AndroidCompassProvider.kt:175-177` maps the platform accuracy
  constants to `CompassStatus`; `domain/CompassTrustJudge.kt` acts on it. Whether a *prompt* is
  shown was not determined.
- **(a) live accuracy readout** and **(c) stationary averaging:** not determined.

`domain/NavigationReadout.kt:46` carries `APPROACHING_ACCURACY_MULTIPLIER = 2.0`.

### C12 Location lifecycle: report only

Gated. `ui/availability/AvailabilityViewModel.kt:129` states the subscription is released on
`ON_STOP` and re-acquired on `ON_START`; `:161` names the release. The 2026-09-09 finding of
no gating is out of date. The privacy policy sentence published 2026-09-11 is accurate.

---

## §3.4 Disclosure

### Confirmed by observation
Everything in the table above, each from a named file and line. Database versions and migration
bodies were read from `data/local/Migrations.kt` by parsing each migration's own block, after a
first attempt attributed `cartography_entries` to `MIGRATION_4_5` because the grep window
spanned several match sites.

### Could not be determined
- C11 (a) live accuracy readout and (c) stationary averaging.
- Whether the compass calibration **prompt** exists, as against the status mapping that feeds it.
- Where instrument-walk logs and tooling live. Searched `docs/` and the tree for
  `instrument`/`walk` filenames: **empty result, not a clean one**.
- Whether any C1-C11 feature is *correct*, as against present. This was an inventory.

### Premises that were wrong
1. **C10 Cartography, "nothing in any document, not found."** 45 files, six tables, five test
   classes, reachable from the Journal tab.
2. **C2, "calculation documented, chart not found."** The chart exists.
3. **C9, "not built as of that writing."** Five ref tables with readers.
4. **The `MIGRATION_4_5` collision.** Resolved by sequencing.
5. **C12.** The subscription is lifecycle-gated.
6. **The dispatch's §2.1 claim that CLAUDE.md "describes the move away from the original
   repository on legal grounds."** CLAUDE.md contains zero occurrences of "legal" and zero of
   "forager-bak"; its four "repositor" hits are `Repository`/`repositories` in the Room and
   DataStore sense. The legal ruling that exists concerns a **package root rename**, not a
   repository move. Two different things were conflated.
7. **Mine, not the dispatch's: that C5 was stale.** Reported early in this session as "9
   references in `main/`" and offered as evidence the no-caller finding had expired. It had not.
   Counting occurrences of a symbol is not counting calls to it, and the difference is the whole
   point of the reachability rule. Corrected above.

### Decided beyond scope
- Ran C4/C5/C6/C10 greps before Phase 0 was formally authorised, because §4 was about to commit
  six sessions to building and "who calls this?" costs one grep.
- Cloned `forager-bak` despite the owner calling it stale, because §3.1 requires the check before
  any claim is reported absent, and two claims were about to be.

---

## What this changes about the plan

Phases 2, 3 and 5 target features that already exist. Phase 1's spec contradicts a documented
decision in `CivilTwilight`. Phase 6's blocker is intact. Phase 4 is partly built.

The remaining genuine gaps are **C4 waypoint navigation** and **C6 the sundown alert itself**,
the second of which is wiring and a time-versus-altitude decision rather than an algorithm.

The §6 owner decisions should be re-read against this before any are answered: several ask the
owner to choose scope for features whose scope has already shipped.
