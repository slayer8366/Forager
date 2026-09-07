# Completion report — Return estimate, Items 1–3 (build)

**Dispatch:** `dispatch-return-estimate-items-1-3.md` (supersedes the first return-estimate build
dispatch where the two differ; carries the walk's results and the three rulings on PR #77).
**Base:** `main` at `8eacc91`, plus PR #77 (`622fe2d`, doc-only). **Built on** `claude/new-session-b7z9bg`
as three commits in the order the dispatch asked for if splitting — Item 3 first, then Items 1 and
2 on the real columns: `f70d3a6` (Item 3), `873e569` (Items 1 and 2), and this report. Nothing
merged. **No UI**, by the first dispatch's own rule, which this one does not override: the number
and its tests, no countdown, no HUD row, no display anywhere — the surface comes with the alert.

**Stop conditions, checked before building — none met:**

1. *The estimate's data path goes through the filtered read seam.* The only in-app reader of a
   recording's points is `TrackRecordingViewModel.beginPolling`, through `trackRepository.getById`
   (`ui/track/TrackRecordingViewModel.kt:278`), which is `RoomTrackRepository.toDomain` — the one
   mapping where `excludeNetworkProviderFixes` runs. The service's unflushed buffer (up to 20
   points) is unfiltered but readable by nothing outside the service. `pathHome` and
   `returnWalkingTime` take a `Track`, not a point list, so a future caller cannot hand them points
   by another route without constructing a `Track` by hand — which a fake would, and the field's
   doc says what that means. Verified, not assumed.
2. *The 14→15 migration is two nullable columns and a straightforward step* — a table rebuild,
   which is the established shape here (every bump since 8→9 rebuilds, because the shared entity
   leaks new columns into every legacy fixture), with rowids preserved and the one index recreated.
3. *Point differencing works for tracks predating the migration* — a stored point has always
   carried `lat`, `lng` and `timestampEpochMillis`, which is all it needs.

Schema version 15 checked against every branch on `origin` before being claimed (24 branches; the
highest was 14, on `main` and five others).

---

## What was built

### Item 3 — speed columns and migration 14→15 (`f70d3a6`)

| Site | Change |
|---|---|
| `domain/model/TrackPoint.kt` | `speedMetersPerSecond: Float? = null`, `speedAccuracyMetersPerSecond: Float? = null`. Doc states the `null` rule (pre-migration rows, network fixes, GPX imports) and names the reader. |
| `domain/LocationTracker.kt` | The same two fields on `LocationFix.Update`, defaulted; a new `LocationFix.Update.toTrackPoint()` — the one place a fix becomes a candidate point, pulled out of the service so it can be asserted without a Robolectric recording. |
| `location/AndroidLocationTracker.kt` | `toFix()` reads `speed` under `hasSpeed()` and `speedAccuracyMetersPerSecond` under `hasSpeedAccuracy()`; unreported travels as `null`, never a zero that would read as "stopped". |
| `service/TrackRecordingService.kt` | The candidate point is `fix.toTrackPoint()`. |
| `data/local/TrackPointEntity.kt` | The two nullable `REAL` columns, not indexed (the only reader loads a whole track's points and never filters on speed in SQL). |
| `data/local/Migrations.kt` | `MIGRATION_14_15`: rebuild of `track_points` with an explicit source column list, `NULL, NULL` for the new columns, `id` copied, `index_track_points_trackId` recreated. |
| `data/local/ForagerDatabase.kt` | `version = 15`, the migration registered, the doc paragraph. `app/schemas/.../15.json` exported by the build and committed. |
| `data/repository/RoomTrackRepository.kt` | Both mappings carry both fields. |

The ten existing migration tests' chains each gained `MIGRATION_14_15`, as every prior bump did
(the target database is now version 15, so a chain that stopped at 14 could not open it). That is
the one edit to existing tests; no assertion changed.

**Constructor sites:** `TrackPoint` is constructed in 5 main files and 17 test files,
`LocationFix.Update` in 8; the defaults mean none changed. GPX import
(`domain/GpxCodec.kt:88`) builds points with no speed — a GPX 1.1 track point has no speed
element — so an imported track is a differencing track, which is correct.

### Item 1 — path home (`domain/PathHome.kt`, `873e569`)

`pathHome(track, current, origin): PathHome?` — the most-recent-point mapping with the ruling and
the reason nearest-point is refused recorded at the function; the hop with the three-band rule
(omit under 25 m, add to 50 m, add and mark `hopIsFar` above); the origin's last leg whenever the
track has an origin, with the network-fix weakness recorded and not fixed; `null` with no points.
`PathHome.totalMeters` is what the walking time divides. One haversine per consecutive pair —
sub-millisecond for thousands of points, recomputed on the 15 s poll rather than per fix; points
only append, so the sum could be kept incrementally, and is not until a caller needs it.

### Item 2 — pace (`domain/MovingPace.kt`, `873e569`)

`movingPace(points): MovingPace`. The design, in the order the function's doc records it:

- **"Moving" is decided per interval between stored points** — implied speed at or above the
  0.5 m/s floor. The sampler writes nothing while stationary, so a stop is one long interval of a
  few metres and drops out. Moving time and the differenced distance sum over moving intervals
  only.
- **Point differencing is first-class.** It is the instrument for every pre-migration track and
  for any device that reports no speed, and it is the other side of the comparison on every
  track, so it runs on every track. Tested as the primary path on a track with no stored speeds,
  with every point reported as skipped for Doppler.
- **A Doppler sample** is the stored speed at the point that ends a moving interval, weighted by
  that interval's duration, counted only if it is itself at or above the floor. The point after a
  stop carries a moving speed but ends the stop's interval, so it is not a sample. This is what
  "over the same window" means: both instruments average over the same moving intervals.
- **Every point examined for a sample is counted** — `pointsCounted`, `pointsWithoutSpeed`,
  `pointsBelowFloor` — and the counts ride on the result; every test that establishes a property
  about speeds asserts them. The dispatch's generalised parsing hazard, built in.
- **The five-minute bar applies per instrument.** Doppler governs when the moving intervals that
  carry a counted sample reach it; otherwise differencing governs when all moving intervals do;
  otherwise the default. `MovingPace.source` says which, `governingMovingMillis` how much.
- **`speedAccuracyMetersPerSecond` is persisted and read by nothing.** No interval, no error bar,
  no weighting — the ruling and its reasoning are on the function.
- **`MovingPace.comparison`** — Doppler and differencing over the same intervals, with the
  interval count and moving time, and a ratio. Reported; nothing gates on it. **It has no surface
  and no log line yet**: the value is on the result for the consumer that does not exist yet.
  Recorded under "decided beyond scope".

The four constants carry what the dispatch required their comments to carry: the floor's band, its
measured insensitivity and the 1.0 m/s boundary; the default's deliberate slowness, the imperial
figure as the source of the rounding, and the walk's 1.99 mph; the five-minute bar and the walk's
warning about it; the fifteen-minute settled window from the accepted degrade table.

### The model — `domain/ReturnWalkingTime.kt` (`873e569`)

`returnWalkingTime(track, origin, current, fixFreshness): ReturnWalkingTime` — `Estimate(path,
pace, walkingMillis, degradeReasons)` with `isAtLeast`, or `Withheld(reason)`. The walking-time-not-
arrival-time distinction, the no-padding condition and the confidently-short failure mode are
recorded at the function. The degrade table is the owner-accepted one (pre-build report,
Proposal 3), one trigger enough, none stacking:

| Reason | Trigger | Signal |
|---|---|---|
| `NO_MEASURED_PACE` | governing moving time under 5 min | `MovingPace.source == DEFAULT` |
| `PACE_FRESHLY_MEASURED` | measured, under 15 min | `governingMovingMillis` |
| `STALE_FIX` | `FixFreshness.STALE` | caller-supplied, the HUD's own classification |
| `FAR_FROM_TRACK` | hop over 50 m | `PathHome.hopIsFar` |
| `PATH_UNDER_MEASURED` | excluded ≥ 10 % of stored | `Track.excludedPointCount` / `storedPointCount` |
| `MOSTLY_NETWORK_FIXES` | > 75 % of ≥ 10 | `isMostlyNetworkFixes()`, existing |
| `FEW_POINTS` | fewer than 10 survivors | the same constant `isMostlyNetworkFixes` uses |
| **withheld** `NO_FIX` | `LOST`, or no fix at all | matches the HUD withholding the distance |
| **withheld** `NO_USABLE_POINTS` | `hasNoUsablePoints()`, or no points | there is no path to measure |
| *(none)* | no origin waypoint | the path ends at the first point; nothing omitted |

Nothing in Items 1–3 reads a fix's reported accuracy. The 50 m hop threshold is the live-fix
gate's ceiling *as a number*, not any fix's field.

---

## Tests

| Class | Tests | What it pins |
|---|---|---|
| `TrackPointSpeedMigrationTest` (new) | 1 | A real version-14 file (fixture `LegacyForagerDatabaseV14`, both leaked columns dropped first) through `MIGRATION_14_15`: the legacy point survives with `null` in both speed fields, every other field intact, rowid 7 preserved; a point with speed and one without round-trip after |
| `RoomTrackRepositoryTest` (+1) | 10 | Speed and accuracy round-trip; `null` reads back as `null` in both, pinned per field |
| `AndroidLocationTrackerTest` (+1) | 4 | A GPS `Location` with `speed`/`speedAccuracyMetersPerSecond` set delivers both on the `Update`; the existing network-fix test pins the `null` side |
| `LocationFixToTrackPointTest` (new) | 2 | Field for field, both directions of the `null` rule |
| `PathHomeTest` (new) | 7 | Straight track; **the doubled-back track** (555.975 m walked against 111.195 m straight — the never-short property asserted directly); the three hop bands; the origin leg and its zero case; no points and one point |
| `MovingPaceTest` (new) | 11 | Differencing as the primary path with every point reported skipped; both sides of the bar; Doppler governing with the comparison reported; **the photographer's eight-minute stop**; **stability across an inserted stop, both instruments**; the floor at and below; the Doppler floor; duration weighting; partial coverage yielding to differencing with counts; the point after a stop; empty and single |
| `ReturnWalkingTimeTest` (new) | 11 | The settled case with no degrade; the default before the bar; each degrade reason per condition; both withhold cases; the origin leg without degrade; **the estimate not moving when a stop is inserted with the same distance remaining**, on a differencing track deliberately (a pinned Doppler value cannot move whatever the floor does) |

**Every expected value is worked by hand from pinned inputs**, never from the function: points step
due north, where the haversine collapses to `R × Δlat` exactly (IUGG mean radius, so one degree of
latitude is 111 195.080 m); 0.000135° every 15 s is 15.011336 m and 1.0007557 m/s; 61 such points
are 900.68015 m, which at a stored `0.9f` (0.899999976 as a double) is 1000.7557 s, asserted as
`1_000_756L`. The default is 3.2 / 3.6 = 0.888… m/s, asserted as the literal. Each figure in the
tests' comments is the arithmetic, not a description of it.

**The chain edits:** ten migration tests gained one migration in their `addMigrations(...)` list.

## Verification

### Reverted-variant checks

Runner discipline, per CLAUDE.md: each revert is a one-line edit applied by exact string
replacement (asserted to match once); the file is copied aside **before** the edit and restored
**from that copy**, never from git; the gradle log is checked for compile errors before any XML is
read, and XML older than the run's start is refused; after restoring, the file is compared byte for
byte with the saved copy; `git status` after the last restore was clean. Every run compiled; every
XML was fresh; every restore was identical. The same seven classes ran each time (`PathHomeTest`,
`MovingPaceTest`, `ReturnWalkingTimeTest`, `RoomTrackRepositoryTest`, `TrackPointSpeedMigrationTest`,
`AndroidLocationTrackerTest`, `LocationFixToTrackPointTest`; 46 tests), green on the forward build
first (the baseline run in the same runner).

| Revert (one line) | Predicted failures | Actual | Match | The failure's message, which only this edit produces |
|---|---|---|---|---|
| A `PathHome`: hop never omitted (`< HOP_OMIT_BELOW_METERS` → `< 0.0`) | 1 | 1 | yes | `expected:<0.0> but was:<20.015114442068285>` — the 20 m hop counted |
| B `PathHome`: hop never far (`> HOP_DEGRADE_ABOVE_METERS` → `> Double.MAX_VALUE`) | 2 | 2 | yes | `expected:<[FAR_FROM_TRACK]> but was:<[]>` |
| C `MovingPace`: floor removed (`< MOVING_SPEED_FLOOR…` → `< 0.0`) | 5 | 5 | yes | stability: `expected:<1.0007557…> but was:<0.555975…>` — the stop folded in; walking time `expected:<1200000> but was:<1680000>` — eight minutes of stop added; photographer `expected:<39> but was:<40>` |
| D `MovingPace`: bar zeroed (`MEASURED_PACE_MIN_MOVING_MILLIS = 0L`) | 5 | **9** | **no — see below** | `expected:<DIFFERENCING> but was:<DOPPLER>` ×2, `expected:<DEFAULT> but was:<DOPPLER>` ×2, `NullPointerException` ×5 |
| E `ReturnWalkingTime`: stale degrade removed (`== STALE` → `== LOST`) | 1 | 1 | yes | `expected:<[STALE_FIX]> but was:<[]>` |
| F `RoomTrackRepository`: read maps speed to `null` | 2 | 2 | yes | the full `TrackPoint` list with `speedMetersPerSecond=null` where `0.87` / `0.96` were stored |
| G `AndroidLocationTracker`: `speedMetersPerSecond = null` | 1 | 1 | yes | the `Update` with `speedMetersPerSecond=null` against `0.96` |
| H `toTrackPoint()`: speed dropped | 1 | 1 | yes | the `TrackPoint` with `speedMetersPerSecond=null` against `0.96` |

**Revert D did not match, and the discrepancy is mine, not the runner's.** I predicted that a
zero bar would let differencing and Doppler qualify early, failing the five tests that assert the
default below five minutes. What a zero bar actually does is make `dopplerMovingMillis >= 0L` true
for *every* track, including one with no Doppler sample at all, so every track became
Doppler-sourced and `dopplerSpeed!!` threw where no sample existed — nine failures, four on the
source and five NPEs. **Each of the nine is a failure this edit produces and no other edit could**
(no other revert touches `source`; the NPEs are all in `speedMetersPerSecond`'s `!!`), the build
compiled, and the XML was fresh — so the check ran and its result is attributable; what was wrong
was my reading of my own guard. Recorded rather than re-predicted after the fact. It says one
thing about the code worth keeping: `source` is safe only because the real bar is positive, and a
future edit that lowers it to zero would crash rather than degrade. Not changed here — a zero bar
is not a value anyone would set, and a guard for it would be speculative logic — but noted at the
constant's reader for whoever tunes it.

**Not revertible in one line, and so not checked this way:** the most-recent-point rule. A
nearest-point variant needs the track distance summed up to the nearest index, several lines; the
doubled-back test asserts the whole-track figure (555.975 m) against the straight-line one
(111.195 m), which is the property, and would fail against any nearest-point implementation that
measured from the nearest index. Said plainly: that test's bite is argued, not demonstrated.

### Full suite

Run on the forward build after the reverts (`1bde79a`'s tree), read from the JUnit XML, every file
written by this run (compile log clean, exit 0):

| Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| 165 | **1271** | 0 | 0 | 24 |

The dispatch's baseline is 1237 tests, 24 skipped; this build adds 34 (1 + 1 + 1 + 2 + 7 + 11 +
11, per the table above), and 1237 + 34 = 1271. **The 24 skipped tests are the CI allowlist's
identity set exactly** — compared as `(classname, name)` pairs against `SKIPPED_TESTS_ALLOWLIST`
parsed from `.github/workflows/ci.yml`: nothing skipped that is not listed, nothing listed that did
not skip. The skip count was not touched. `JournalTabTest`'s "From Album" flake did not fire on
this run. Device verification: blocked, no `/dev/kvm`; the migration on a real install, the tracker
on a real `LocationManager`, and the first Doppler-versus-differencing comparison on a real track
are the owner's.


---

## Required disclosure

### Confirmed vs. inferred

**Confirmed from the code on `8eacc91`/`622fe2d`:** the ViewModel's only track read and its
route through the repository mapping; the five-field fix and point types and every constructor
site count above; the sampler's rule; `GeoDistance.metersBetween`'s haversine and radius; the
existing migration pattern and the fixture leak it exists for; the schema export location and the
generated `15.json`; the `FixFreshness` enum and its thresholds; `isMostlyNetworkFixes`,
`hasNoUsablePoints`, `storedPointCount` and their constants; that no domain file imports
`android.util.Log` (so the comparison could not be logged from domain code without the `ErrorLog`
pattern). **Confirmed from git:** no branch on `origin` above version 14. **Confirmed from the raw
walk log, independently of the dispatch:** the 289/55 split, min 0.00 / median 0.81 / max 1.55 m/s
on GPS fixes, the accuracy constant.

**Inferred:** that consecutive stored points' Doppler samples are usefully independent enough for
a duration-weighted average (the owner's ruling says √N is a floor, not an estimate — nothing here
assumes otherwise, but the average is still an average); that the point after a stop is better
excluded than included as a Doppler sample (a judgement, below); the 0.5 m/s implied-speed floor's
behaviour on BATTERY_SAVER's 60 s intervals, where the walk's 1 Hz data says nothing directly.

### Could not determine

- Whether Doppler and differencing agree on real tracks. The comparison is built and tested on
  hand-built tracks where the answer is known by construction (0.9 against 1.0008); its value is
  in the first real track, which no sandbox produces.
- Whether the sampler's first point after a stop carries a slow Doppler speed often enough that
  excluding it as a sample (it ends the stop's interval) matters. Built so it does not matter: the
  interval decides, the sample only contributes if the interval was moving.
- The on-device cost of the 15 s recompute on a multi-hour track. Reasoned sub-millisecond, not
  measured.
- Device behaviour of any kind — no `/dev/kvm`.

### Premises in this dispatch that were wrong

- **None found wrong.** The baseline SHAs, the schema version, the 344/289/55 counts, the speed
  min/median/max, and "the tracker discards speed" all held on the code and the log.
- **One difference between this dispatch and the accepted pre-build proposals, noted rather than
  resolved:** the pre-build's Proposal 1 (accepted) gave the hop a hysteresis band — enter the
  degraded form above 50 m, leave below 25 m — so the label would not flicker on successive fixes
  near the threshold. This dispatch states three plain bands (omit under 25, add to 50, degrade
  above 50) with no memory between calls, and it governs where the two differ, so that is what
  was built: `hopIsFar` is a pure function of the current hop. If the flicker the band was meant
  to prevent shows up on a device, the band is a small change at `PathHome.hopIsFar` with state
  held by the caller.

### Decided beyond scope

Every judgement the dispatch did not make, in the order they were made:

1. **`LocationFix.Update.toTrackPoint()`** — the service's inline mapping moved into a domain
   function so the speed carry-through has a test without a Robolectric recording. The service's
   behaviour is unchanged.
2. **The Doppler sample definition** — the stored speed at the point *ending* a moving interval,
   duration-weighted, with the floor applied to the sample as well as to the interval; the point
   after a stop is not a sample. The dispatch says which fixes count (at or above the floor) and
   that moving time is fixes past the floor; on stored points sampled every 5–60 s a "fix's"
   moving time is its interval's, and the interval is what the stop rule already classifies. The
   alternative (every stored speed at or above the floor, unweighted) would count the post-stop
   point and would have no moving-time meaning of its own.
3. **The bar per instrument** — Doppler governs on its own five minutes of counted intervals,
   differencing on all moving intervals, else the default. The dispatch sets one bar and names two
   instruments; it does not say which governs when both qualify (Doppler, the reason the column
   exists) or when Doppler covers part of a track (differencing, until Doppler has five minutes of
   its own — a track straddling the migration, or a sporadic device, does not get its pace from a
   handful of samples).
4. **The full degrade table**, not only the hop — this dispatch names the hop's degrade and says
   the model is `distance ÷ speed`; the owner-accepted table from the pre-build report supplies the
   rest, and building the model without it would have shipped a number that could be confidently
   short under six of the seven conditions the owner ruled on.
5. **No fix at all is withheld as `NO_FIX`**, the same as a lost one — the table has no row for
   "no fix yet"; a walking time with no position under it is the lost case exactly.
6. **`intervalMillis <= 0` intervals are skipped** — duplicate or out-of-order stamps have no
   duration; the DAO orders by timestamp, so this is defensive, and it counts in
   `intervalsExamined` but nowhere else.
7. **`PATH_UNDER_MEASURED` at "≥ 10 %"** — the accepted table says `≥ 10 %`; the boundary is
   inclusive and tested at 7 of 68 (10.3 %) against 6 of 67 (9.0 %).
8. **The comparison has no surface and no log line.** Domain code here does not log (`ErrorLog.kt`
   records why), and no consumer exists yet; "report the comparison" is met by the value being on
   the result, asserted in tests, and nowhere else. The consumer that surfaces the estimate should
   log it — recorded at `MovingPace.comparison`.
9. **The origin leg is added even when it is zero** (origin at the first point) — `0.0`, not
   `null`, so "has an origin" and "the leg's length" stay two facts.
10. **The migration chains** — ten existing tests edited by one token each, as every prior bump
    did; recorded here because the standing rule says modifying a test is never in scope unless
    asked, and this dispatch asked for the migration, which cannot be built without it.

---

## What the next dispatch inherits

- The estimate and both its halves exist with no caller. The alert dispatch wires
  `returnWalkingTime` to the track poll, supplies `FixFreshness` from the HUD's own
  classification, and decides what "at least" looks like on screen.
- The Doppler/differencing comparison is on every `MovingPace`; the first consumer should log it
  per track so real-track divergence is seen.
- The duplicate-listener count (three) and the accuracy constant are unchanged by this work.
