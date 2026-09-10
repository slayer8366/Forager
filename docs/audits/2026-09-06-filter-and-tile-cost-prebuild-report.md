# Pre-build report: GPS spike filter at the read seam, and the stale tile ceiling

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Dispatch:** "Two data corrections: GPS spikes in recorded tracks, and a stale tile ceiling"
(planner, owner-directed). **Base confirmed:** `main` at `909c8ea` (the merge of PR #69), branch
`claude/new-session-102gri` restarted from it. **Baseline suite on that base, this session:** 1155
tests, 0 failed, 24 skipped.

Parts A and B are report-before-building and nothing of either is built here. Part C was authorised
and is built in the same commit as this report; its section says what changed and how it was
verified.

Every claim below names a file and line on `909c8ea` or is marked as inferred.

---

## Part A — the filter at the read seam

### A1.0 The seam premise, re-verified

The pulse's load-bearing premise holds. Every read of `track_points` goes through one query,
`TrackDao.getPointsForTrack` (`app/src/main/java/com/zynergylabs/forager/app/data/local/TrackDao.kt:47`), and
its only callers are the three read methods of `RoomTrackRepository` — `getAll` (`:25`), `getById`
(`:30`), `getForDay` (`:36`) — which all pass the result through the single private mapping
`TrackEntity.toDomain(points)` (`RoomTrackRepository.kt:61`). A grep of `app/src/main` for
`track_points` and `TrackPointEntity` outside the DAO, the entity, the repository and the database
class finds only migration SQL and doc comments (`Migrations.kt:88–126`, `WaypointEntity.kt:9`,
`MushroomLogEntryEntity.kt:141`, `TrackEntity.kt:9`). No consumer bypasses the mapping.

The consumers, by what they read (every one goes through `TrackRepository`):

| Consumer | Read | What it does with `points` |
|---|---|---|
| `TrackRecordingViewModel.beginPolling` (`ui/track/TrackRecordingViewModel.kt:253`) | `getById` every 15 s while recording | `breadcrumbPoints` → the live line on the map; `breadcrumbPoints.firstOrNull()` is the return-to-start target when no origin waypoint exists (`:417–418`) |
| `GetTracksUseCase` → `TrackExportList` (`ui/track/TrackExportPanel.kt:54,104`) | `getAll` | "N points" subtitle; the `Track` handed to `TrackGpxExporter.write` (`:116`) |
| `GetDerivedTripUseCase` (`domain/GetDerivedTripUseCase.kt:42`) | `getForDay` | the day's tracks → Cartography candidates, `CartographyViewModel.toDecision` (`ui/log/CartographyViewModel.kt:113,228,482`), the edit screen's fresh statistics (`CartographyEntryEditScreen.kt:491`), `GetTripReportOfflineRegionsUseCase.candidateCoordinates` (`:35`) |
| `GetCartographyEntryMapDataUseCase` (`:43`) | `getById` | the entry map's track geometry |
| `GetTrackOriginWaypointUseCase` (`:22`) | `getById` | reads `originWaypointId` only; points pass through unused |

**GPX export goes through the seam** (A2 item 4): `TrackGpxExporter.write(track)` takes a `Track`
(`export/TrackGpxExporter.kt:28`) that `TrackExportPanel` got from `GetTracksUseCase`, and
`GpxCodec.encode` walks `track.points` (`domain/GpxCodec.kt:38`). It would therefore export the
*filtered* points, not the raw ones. Nothing in the export format changes; what changes is which
points reach it. Said plainly because it is a consequence the owner may or may not want: a GPX file
is the one place a raw track could otherwise leave the device, and after A1 it cannot.

**The off-track heuristic** (out of scope, but the dispatch says Part A changes what it sees):
`DetectOffTrackUseCase` consumes distances to the return target (`TrackRecordingViewModel.kt:417–423`),
and the target is the origin waypoint when one exists, else the first breadcrumb. The first point of
a track can never be excluded by the rule proposed below (it needs a predecessor), so the heuristic's
input is unchanged for every track; the only Part A effect on it is via the live fix it is given,
which Part A does not touch.

### A1.1 Rule one — proposed form

**Vocabulary.** Two fixes P and Q with reported accuracies `aP`, `aQ` (metres). Their error discs
overlap iff `d(P, Q) ≤ aP + aQ`. Two honest fixes of the *same* place can be at most `aP + aQ`
apart (each at the far edge of its own disc), so `d(P, Q) > aP + aQ` is the geometric meaning of
"an excursion beyond the reported accuracy" when both ends carry their own figure. Call
`d(P, Q) > aP + aQ` **"beyond"** and its negation **"within"**.

**The rule.** Walk the stored points in timestamp order. Keep a running *last kept point* A. For
each candidate B with a successor C (the next stored point after B):

> B is excluded iff B is **beyond** A **and** B is **beyond** C **and** C is **within** A.

Everything else is kept, and whenever B is kept it becomes the new A. The first point of a track is
always kept (no A); the last point is always kept (no C). A point whose `accuracyMeters` is `null`,
or whose neighbour's is, cannot be evaluated and is kept — "unsupported, so untouched", not a
guessed default (CLAUDE.md, errors and failure paths); it is logged at debug level once per read
when it happens, so the fallback is visible.

**What it compares, and against whose accuracy.** Displacement from the last *kept* point, not from
the previous *raw* point, against the *sum* of the two reported radii. Reference = last kept: if the
reference were the previous raw point, a spike's return leg would be measured from the spike and
would itself look like an excursion, so the return would be lost too. Sum of radii rather than
either alone: the owner's observation is "well beyond the circle", and two circles are in play; the
sum is the loosest reading of that, which is the conservative direction for a filter whose worse
error is dropping honest points. Android's `accuracy` is a 68 % radius, so honest fixes leave their
disc about a third of the time; two discs' radii summed give margin against that without a
constant. There is no tunable in this rule.

**Whether it needs the out-and-back shape.** Yes — displacement alone is not enough, and the
owner's framing ("an excursion that does not return is someone walking") is right. On BALANCED
(15 s, 15 m, 50 m ceiling; `domain/model/TrackRecordingMode.kt:28`) a walker at 1.5 m/s moves 22 m
between samples and two honest 15 m fixes may add 30 m to that: 52 m of legitimate displacement,
which a displacement-only test at `aA + aB = 30 m` would drop. The third clause — C is within A —
is what protects movement: a walker's C is two samples from A and is not within it. The starburst's
spoke is exactly a B beyond both neighbours while the neighbours agree with each other.

**What it does and does not catch — stated so the owner can judge it against the photographs.**

- A single-sample spike (out and back within one sampling interval) of any length beyond the summed
  radii: excluded. If every spoke in the starburst is one point, the starburst is gone.
- A spoke of two or more consecutive far points (out, further out, back): **kept**. With A the last
  kept point, the triple (A, B1, B2) fails "B1 beyond B2" because B1 and B2 agree with each other,
  so B1 is kept and becomes A, and from there the return looks like a walk. Extending the rule to
  runs needs a cap on how long an out-and-back may last before it is someone's walk, and that cap
  is a number this project does not have — it is rule two's data. I have not looked for it in the
  images; the owner has them. **If the photographed spokes are multi-point, rule one as proposed
  will not remove them, and the owner should know that before authorising it.**
- Two consecutive spikes in different directions (A, B far NE, C far SW, D near A): both kept, for
  the same reason.
- An honest single-sample detour — the walker steps 30 m out to look at something and is back at
  the same spot by the next fix, with 5 m accuracy on HIGH — is indistinguishable from a spike and
  is excluded. This is the dangerous-direction error, and it is bounded: only isolated single points
  can ever be lost, and any excursion the recorder spent two or more samples on survives whole.
  Under the sampler's own rules a single-sample detour is already at most one interval long
  (5/15/60 s by mode).

**What happens to the point.** It is not in the `List<TrackPoint>` the repository returns, so it is
not drawn, not counted, not exported, not a Cartography candidate coordinate. It stays in
`track_points` untouched — the filter is a pure function over the list `getPointsForTrack` returned,
applied inside `TrackEntity.toDomain` (`RoomTrackRepository.kt:61`). There is no exclusion flag, no
column, no second table, no write. The exclusion "lives" nowhere but in the read path's output,
which is the point: a better rule later changes what every existing track shows the next time it is
read.

**What a filtered-out point does to its neighbours.** Nothing is inserted. A and C become adjacent
in the returned list, so the drawn line joins them directly, the statistics distance counts
`d(A, C)`, and the duration is unchanged (timestamps of A and C are both real). **The assumption
that makes:** between A's timestamp and C's the recorder was at or near A — which is exactly what
"C is within A" says the fixes claim, so the assumption is the rule's own third clause, not an
extra one. For the stationary starburst that is the truth; for the honest single-sample detour it
understates the path by the detour, which is the bounded loss stated above.

**One visible behaviour during live recording.** A spike is excluded only once its successor exists.
The ViewModel polls the repository every 15 s (`POLL_INTERVAL_MILLIS`), and the service flushes
every 20 points or 30 s, so a live spike can be drawn as the track's current endpoint for up to one
flush plus one poll and then vanish when the return point lands. That is the breadcrumb data
changing; nothing animates, re-measures or re-fits the map.

### A1.2 Structure, so rule two joins without rework

A domain object `TrackReadFilter` (`app/src/main/java/com/zynergylabs/forager/app/domain/`, no Android
imports) holding an ordered `List<TrackPointRule>`, where a rule is `fun interface TrackPointRule {
fun apply(points: List<TrackPoint>): List<TrackPoint> }`. `TrackReadFilter.apply(points)` folds the
rules in order. Rule one is `SpikeRule` (name to be settled in the build), and the filter's list
has exactly one element. `RoomTrackRepository` takes the filter as a constructor parameter with the
production default, so a test can hand it a pass-through and assert the raw rows, and the per-
consumer tests use the real one. Rule two, when its numbers exist, is a second `TrackPointRule`
appended to the list — no change to the repository, the seam, or rule one. No stub, no flag, no
column, no disabled entry: the list is just length one until then.

Rejected: a `Boolean`-returning per-point predicate (rule two is about a triple's *shape* over time,
not one point); folding rule one into `TrackEntity.toDomain` inline (would make rule two a second
inline edit at the seam, which is the rework the dispatch wants avoided).

### A1.3 Cost

Rule one is one pass, one `GeoDistance.metersBetween` (a haversine — two `sin`, a `cos` pair, an
`atan2`, a `sqrt`) per kept-candidate pair plus one for the A–C check when the first two clauses
hold, so ≈ 1–2 haversines per point. `ComputeTrackStatisticsUseCase` already does exactly one
haversine per point on the same lists (`domain/ComputeTrackStatisticsUseCase.kt`, pairwise loop),
and `breadcrumbFeatureCollection` walks them again to build geometry, so the filter at most doubles
a per-read cost that already exists and is not the expensive part of a read (the Room query is).

**Points per recording, from the sampler's floors** (`TrackRecordingMode.kt:27–29`; the interval is a
minimum, so these are ceilings):

| Mode | Min interval | Max points / hour | 4 h | 8 h |
|---|---|---|---|---|
| HIGH_ACCURACY | 5 s | 720 | 2 880 | 5 760 |
| BALANCED | 15 s | 240 | 960 | 1 920 |
| BATTERY_SAVER | 60 s | 60 | 240 | 480 |

An 8-hour HIGH_ACCURACY track is under 6 000 points; ~12 000 haversines is well under a millisecond
of arithmetic on any phone this app targets. The heaviest caller is the recording poll — the whole
track every 15 s via `getById` — which already reads and maps all points each time; the filter adds
a fraction of that. **Not material**, and I am saying so before building rather than after; the
build will include a timing note from the unit test on a 6 000-point synthetic track so the claim
has a number behind it rather than an estimate.

`Track`'s own doc comment (`domain/model/Track.kt`) already commits to "a multi-hour track is at
most a few thousand points … well within what a single Room query and an in-memory list handle";
the filter stays inside that envelope.

### A2 — Cartography's stored distance

**Where the snapshot lives and who reads it.** `CartographyEntryTrackRefEntity(entryId, trackId,
name, distanceMeters, durationMillis, pointCount, kept)` (`data/local/CartographyEntryEntity.kt:64–72`),
written from `TrackDecision` by `RoomCartographyEntryRepository.save` (`:37`, mapping `:92`), which
gets its numbers from `CartographyViewModel.toDecision` → `ComputeTrackStatisticsUseCase(points)`
(`CartographyViewModel.kt:482–489`) at decision time. Read back by `trackSubtitle(distanceMeters,
durationMillis, unit)` on the edit screen (`CartographyEntryEditScreen.kt:481`) and the report screen
(`CartographyEntryReportScreen.kt:487`), which rounds to whole kilometres (`:628–636`).

**A2.2 — whether anything else persists a derived distance or count.** Searched, not trusted:
`grep` of `app/src/main` for `distanceMeters`, `pointCount`, `durationMillis` as persisted fields
finds only this entity (and its `Migrations.kt:644` DDL). `OfflineRegionEntity` has no `tileCount`
column (`data/local/OfflineRegionEntity.kt:30–46`; tile counts are read live, see B3). `WaypointEntity`
and `MushroomLogEntryEntity` persist positions, not derived distances. So: **the Cartography track
ref is the only persisted derived value**, and the "trip report" the pulse named is the same row read
on a second screen, not a second store. One thing found on the way: the snapshot's `pointCount` is
written (`CartographyViewModel.kt:489`) and mapped to the domain (`RoomCartographyEntryRepository.kt:98,107`)
but **displayed nowhere** — no reader in `app/src/main`. Pre-existing, not Part A's to fix; noted
because the recompute would be updating a number nothing shows.

**A tension the dispatch should know about before deciding how.** The entity's own doc comment
(`CartographyEntryEntity.kt:49–53`) states the rule the snapshot exists to serve: *"an entry must
never silently change what it shows on reopen."* Recomputing the snapshot is a deliberate exception
to that rule — the number it cached was wrong, per the owner — but it is an exception to a written
rule, and the build should say so in that doc comment rather than leave the two contradicting each
other.

**A2.1 — when the recompute happens; the trade-offs.**

1. **On next open of the entry (lazy, with write-back).** When the edit or report screen loads an
   entry, recompute every track ref whose track still exists from the (now filtered) repository
   read, and if the stored figures differ, persist the corrected refs before publishing the entry
   to the screen. *Cost:* one filtered read per kept track per open — the edit screen already does
   this read for its candidate rows. *Leaves stale:* rows for entries never reopened, which nobody
   sees, and rows whose track has since been deleted (no points to recompute from — those keep the
   unfiltered figure, and this is unavoidable under store-raw since the raw points are gone with the
   track). *Window:* none on screen — the recompute completes before the first frame that shows the
   numbers. **Recommended.**
2. **One pass at app start / database open** over every ref row with a live track. *Cost:* reads
   every kept track's points once, on the main thread's coroutine budget at startup; bounded by the
   number of kept tracks, which is small today, but it runs whether or not the user ever opens
   Cartography. *Leaves stale:* nothing with a live track. *Repeats:* every future filter change
   needs another pass and a way to know it has run (a version marker — a new persisted value).
3. **At Room migration time.** Not possible as a migration: migrations are SQL over the schema;
   the filter is Kotlin over points. Would be option 2 wearing a migration number and forcing a
   schema version bump for no schema change. Rejected.
4. **Stop reading the snapshot when the track is alive** (display the fresh figure whenever the
   track exists, fall back to the snapshot only when it is gone). Fixes the screens without a write
   and without touching the "never silently change" rule's storage — but it is not what the owner
   decided ("recompute it"), so it is listed, not proposed.

**A2.3 — what the edit screen shows during any window.** First, a correction to the premise: the
edit screen does **not** show a stored distance *beside* a freshly computed one for the same track.
`mergeDecisionRows` (`CartographyEntryEditScreen.kt:552–564`) renders each track once — a decided
track from its snapshot, an undecided candidate from a fresh `ComputeTrackStatisticsUseCase` call
(`:481` vs `:491`). The two methods coexist on one screen across *different* rows, and the report
screen shows snapshots only. So today, after A1 and before A2, the disagreement would be: a decided
track reading its old unfiltered kilometres one row above an undecided track reading filtered ones,
and the same decided track reading differently on the entry map (filtered geometry) than in its
subtitle (unfiltered km). With option 1 there is no on-screen window: the screen's first render
already carries the recomputed values. With option 2 the window is "between install and the pass
finishing", during which an opened entry would show old figures for a few hundred milliseconds at
most. Whole-kilometre rounding also means most starbursts will not move the displayed number at
all — a 40 m spike times twenty spokes is 1.6 km of phantom distance, which does show, but a single
spike does not.

**A2.4 — GPX.** Through the seam; exports filtered points; format untouched. See A1.0.

### A — verification plan (not run; for the owner to check against the dispatch's list)

- `TrackReadFilterTest`: a hand-built stationary track with three single-point spokes at hand-
  computed offsets (e.g. 150 m N, 90 m E, 200 m SW of a 20 m-accuracy anchor, latitude offsets
  computed as `metres / 111 195` and longitude offsets divided by `cos(lat)` by hand) loses exactly
  the three spoke points; an honest-wander track with 12 points inside 50 m discs loses nothing; a
  two-point spoke is kept (documenting the limitation as a pinned behaviour, so a later run-rule
  changes a test on purpose); null accuracy on either neighbour keeps the point; first and last
  points are never removed; a track of 0, 1 and 2 points returns unchanged.
- `RoomTrackRepositoryTest` with in-memory Room: rows inserted, read back filtered, then
  `getPointsForTrack` asserted row-for-row identical to what was inserted (nothing on disk changes).
- Per-consumer: `TrackRecordingViewModelTest` (breadcrumbs after a poll), the export list's point
  count, `GetDerivedTripUseCase`, `GetCartographyEntryMapDataUseCase`, `GetTripReportOfflineRegionsUseCase`,
  `TrackGpxExporter` output — each asserting the spoke coordinates are absent from that consumer's
  actual output.
- Cartography: after recompute the ref's `distanceMeters` equals the filtered statistics to the
  metre, and the edit screen's decided row and the report's row read the same rounded string.
- Zero/one surviving point: cannot happen from rule one (first and last always survive), so the
  reachable minimum is the track's own size. **Report rather than guess:** a stored track of one
  point already renders no line today (`breadcrumbFeatureCollection` needs two), and the filter
  does not create a new case.
- Each new test run with the rule's third clause inverted, confirming the honest-wander test and
  the spike test fail for the predicted reasons, with the build log checked for compile errors
  before results are read.

---

## Part B — the worker serves zoom 15

### B1 — the constant, and what else hard-codes 14

`SERVED_MAX_ZOOM = 14.0` at `domain/OfflineMapRepository.kt:133`; `MAX_ZOOM = 15.0` at `:106`;
`TILE_COUNT_LIMIT = 6000L` at `:81`; `MIN_ZOOM = 10.0` at `:105`.

**Nothing else in `app/src/main` hard-codes the served ceiling.** A grep for `14`, `SERVED_MAX_ZOOM`,
`maxzoom`, `OVERFLOW_MAX_ZOOM` and `MAX_ZOOM` finds, outside comments: the two `estimateServed…`
call sites (`AvailabilityViewModel.kt:927`, `AvailabilityOfflineMapsUi.kt:205`) which go through the
constant; `MapLibreOfflineMapRepository.kt:101,107,119,128,138` which use `MAX_ZOOM` for the
definition and the stored metadata; `GetCartographyEntryOfflineRegionUseCase.kt:42` testing membership
at `MAX_ZOOM`; `GetTripReportOfflineRegionsUseCase.kt:28` testing at each region's stored `maxZoom`;
`ui/map/Basemap.kt:146` `maxZoom = 15` for the PMTiles basemap's style JSON (the *rendering* ceiling,
which already matched the worker's source). The worker's `OVERFLOW_MAX_ZOOM = 15`
(`server/pmtiles-worker/src/index.ts:371`) is the server-side truth this constant mirrors and is out
of scope. Two `14`s in tests are parameters, not ceilings (`EstimateOfflineTileCountTest.kt:14,43,44,51,52,68,77`
pass `maxZoom = 14.0` to the general function; `OfflineRegionMigrationTest.kt:130` stores 14.0 as a
migration fixture). Three comment references to the old fact will be updated with the constant:
`OfflineMapRepository.kt:110–132` (its own comment), `AvailabilityViewModel.kt:924–926` and
`AvailabilityOfflineMapsUi.kt:203–204` ("the zoom the deployed source actually serves, not MAX_ZOOM").

**`min(MAX_ZOOM, SERVED_MAX_ZOOM)` becomes a no-op today, and I propose keeping it.** With both at
15.0 the expression selects 15.0 either way, so it protects nothing at this moment. It is not dead:
it encodes that two *different* facts bound the estimate — what the app asks for and what the
server advertises — and the day either moves (the worker regressing to 14, or `MAX_ZOOM` rising to
16 ahead of the worker) it is the one line that keeps shown, gated and downloaded counts together
without a code change. Removing it would mean re-deriving that seam under pressure the next time
the two diverge, which is how this constant went stale. Its comment will say plainly that it is
currently a no-op and why it stays.

**One existing assertion necessarily changes.** `EstimateOfflineTileCountTest` "the served-ceiling
estimate matches the download's own enumeration at three radii spanning the slider" (`:34–38`) pins
68/485/4772 through `estimateServedOfflineTileCount`, which reads the constant. Raising it makes
that test fail by construction — the dispatch's cross-check figures 224/1781/18696 are what it will
read. This is B1's own consequence, not a silenced test; recorded here so it is authorised
explicitly rather than absorbed.

### B2 — the largest radius that fits 6000 at zoom 15

**Method.** A Python re-derivation from the slippy-map definition, not a call into the code under
test: bounding box from the centre by the equirectangular rule `GeoDistance.boundingBox` uses
(`GeoDistance.kt:69–83`; mean radius 6 371 008.8 m, longitude span divided by `cos(lat)`), tile
column `floor((lng+180)/360·2^z)`, tile row `floor((1 − ln(tan φ + sec φ)/π)/2·2^z)`, tiles per zoom
= columns × rows, summed over z = 10…15. Cross-check against the previous work's figures at the
owner's coordinates (45.357, −122.607): **5/15/50 km → 68/485/4772 at ceiling 14 and 224/1781/18696
at ceiling 15 — all six reproduced exactly.**

**B2.1 — which latitude.** The archive is a continental-US extract, `--bbox=-124.85,24.40,-66.87,49.60`
(`server/pmtiles-worker/README.md:52–53,63–64`): the worker serves nothing north of 49.60°N, so that
is the worst case the app supports and the latitude I sized for. The picker accepts any latitude
(`AvailabilityViewModel.kt:906`), but a pin outside the archive downloads no tiles regardless of the
slider.

Tile counts also depend on where the centre sits relative to tile edges, by up to one extra column
and one extra row per zoom, so a single global maximum has to fit the *worst alignment*, not one
lucky centre. For each radius I took the maximum over a 40 × 40 sweep of the centre across one
zoom-15 tile in both axes ("worst" below), alongside the point estimate at the owner's longitude
("point").

| Radius | 49.60°N worst | 49.60°N point | 45.357°N worst | 45.357°N point (owner) |
|---|---|---|---|---|
| 8 km (miles default) | 696 | 656 | 562 | 532 |
| 10 km (km default) | 1 007 | 980 | 869 | 824 |
| 24 km | 5 246 | 5 120 | 4 405 | 4 304 |
| **25 km** | **5 718** | **5 594** | **4 860** | **4 770** |
| 26 km | 6 075 | 6 008 | 5 246 | 5 136 |
| 27 km | 6 680 | — | 5 589 | — |
| 28 km | 7 019 | — | 6 075 | — |
| 50 km (today's max) | 22 028 | — | 18 802 | 18 696 |

Per-zoom breakdown of the 25 km case at 49.60°N, point estimate: 3×3 + 5×5 + 8×9 + 16×17 + 32×33 +
64×65 = 9 + 25 + 72 + 272 + 1 056 + 4 160 = 5 594. Zoom 15 alone is 74 % of it.

**So the largest radius that fits everywhere the archive reaches is 25 km** (worst-case 5 718 ≤
6 000; 26 km is 6 075 and over). The dispatch's unverified "28 km at 45°N" was a point estimate at a
comfortable latitude and alignment; it would be refused at the archive's northern edge and can be
refused at 45°N with an unlucky centre (6 075 worst). At the owner's own latitude the honest number
is 27 km worst-case, 28 km on a lucky centre — but the slider is one global number.

**Is 25 km uselessly small?** It is half of today's 50 km, and today's 50 km was never real: at
ceiling 14 the gate let it through at 4 772 tiles, and after B1 that same region would honestly cost
18 696. A 25 km radius is a 50 km-wide circle, roughly 1 960 km² — an hour's drive across, and
about three times the per-unit defaults' radius. It does not make the feature feel broken to me,
but I am not the one walking with it; the owner asked to be told, and this is the number.

**B2.2 — roundness.** 25 km reads as **"25 km"** and **"16 mi"** (`formatDistanceKm` rounds
25 × 0.621371 = 15.53). The imperial-round alternative is **24 km → "15 mi"** (14.91), costing
5 246 worst-case at 49.60°N. Both fit. The precedent is the current 50 km reading "31 mi" — metric
round, imperial not — and the same reasoning applies: a round number in one unit is enough, and the
km value is the one stored, gated and estimated. **Proposed: 25 km / "16 mi".** If the owner would
rather the miles reading be round, 24 km / "15 mi" costs 472 fewer worst-case tiles and is equally
one constant.

**B2.3 — the per-unit defaults.** 8 km ("5 mi") now costs 532 tiles at the owner's latitude and up
to 696 at the archive's northern edge — 8.9–11.6 % of the budget; 10 km costs 824–1 007 — 13.7–16.8 %.
At ceiling 14 those were 152–194 and 224–278 (2.5–4.6 %). So a default-radius region went from
"one of ~25–40 that fit" to "one of ~6–11 that fit". Neither is an unreasonable share for a single
default region, but the *budget's own comment* (`OfflineMapRepository.kt:73–79`) reasons that 6000
"holds about nine 15 km regions" — at zoom 15 a 15 km region is 1 781 tiles, so it now holds
three. That comment goes stale with B1 and will be updated to the new arithmetic. **I am not
changing the defaults**; the dispatch says come back first, and I have nothing beyond these numbers
to argue either way.

**B2.4 — making the reversal cheap, and whether the maximum can be derived.**

Today the offline slider's range is `Region.MIN_RADIUS_KM..Region.MAX_RADIUS_KM`
(`AvailabilityOfflineMapsUi.kt:196–197`) and `onOfflineMapRadiusChanged` clamps with
`Region.clampRadiusKm` (`AvailabilityViewModel.kt:647`) — **the same constant the iNaturalist search
radius uses** (`AvailabilityViewModel.kt:180`, and the two search sliders' literal `1f..50f` at
`AvailabilitySearchUi.kt:436,813`, plus `GeoDistance.kt:140` and `SavePlannedTripUseCase.kt:16`).
Shrinking `Region.MAX_RADIUS_KM` would shrink the search radius too, which nothing in this dispatch
asks for. **Decision the dispatch does not make, flagged:** I intend a *separate* offline-map
maximum and to leave `Region.MAX_RADIUS_KM = 50` for search. If the owner wants the search radius
to move with it, that is a different change.

*Stated constant (proposed):* `OfflineMapRepository.MAX_RADIUS_KM = 25`, beside `TILE_COUNT_LIMIT`
and `SERVED_MAX_ZOOM` so the three numbers that constrain each other sit in one companion object;
the slider's `valueRange`/`steps` and the ViewModel clamp read it; `loadOfflineMapPreferences`
(`AvailabilityViewModel.kt:743`) restores a last-picked radius that may be up to 50 from before this
change and must clamp it too. Its comment states the arithmetic above and the latitude it was sized
for. **Plus a guard test that makes it impossible for the constant to go stale silently:** at
49.60°N the worst-alignment served estimate for `MAX_RADIUS_KM` is ≤ `TILE_COUNT_LIMIT` and for
`MAX_RADIUS_KM + 1` it exceeds it, with the expected counts pinned as the hand-derived literals
above rather than recomputed. When the budget rises, whoever raises it changes one constant, and CI
tells them the new maximum if they forgot the second.

*Derived (considered):* compute the maximum at startup as the largest `r` in `1..50` whose
worst-alignment estimate at 49.60°N fits the budget. It is cheap (≤ 50 × 6 zooms × a small sweep)
and cannot go stale against the budget or the ceiling. But it needs two more inputs that *can* go
stale: the archive's northern latitude (another client constant encoding a server fact — it moves
if the extract is ever rebuilt with a different bbox) and a definition of "worst alignment" that
has to be argued in code rather than in a table. The result would also be a number nobody can read
off the source, in a UI whose slider label is the first thing a user sees. **I recommend the stated
constant with the guard test** — it gives the same protection against silent staleness (a failing
build instead of a wrong number) and stays legible. If the owner prefers the derivation, the guard
test's arithmetic is the derivation and the build is not much different; say which.

### B3 — regions downloaded before the deploy

**B3.1 — what a pre-deploy region claims versus what it contains.** Every region's Room row and its
MapLibre metadata blob store `maxZoom = OfflineMapRepository.MAX_ZOOM` = 15.0
(`MapLibreOfflineMapRepository.kt:119,128`; `MapLibreOfflineRegionMetadata.kt:36,55`), and the
`OfflineTilePyramidRegionDefinition` was created with 15.0 (`:101`). A region downloaded while the
worker advertised `maxzoom: 14` contains zooms 10–14 only — MapLibre clamped the pyramid to the
source's advertised ceiling and reported the download *complete*, which is why the pre-flight/actual
mismatch was 3.7× and not a failure. Raising the constant changes nothing about those rows: they
said 15 before and say 15 after. What changes is that "15" becomes *true* for regions downloaded
from now on and stays *false* for the old ones — and there is nothing in the row that tells the two
apart except `createdAtEpochMillis` against a deploy date the app does not know.

The one place the stored `maxZoom` is turned into a *claim about contents* is the region row's
label (`AvailabilityOfflineMapsUi.kt:438–441`): *"Ready to zoom 15: zoom 10–14 from the archive,
zoom 15 detail fetched live from Protomaps when this region downloaded — a region that shows here
has both, since a zoom-15 fetch failure fails the whole download rather than silently completing
without it."* For a pre-deploy region every clause after the colon is false: no zoom-15 detail was
fetched, nothing failed, and the download did silently complete without it — the exact failure the
sentence says cannot happen. This label was already wrong before today (the pulse called it
"overstating by one level"); B1 does not make it worse, but B1 is the moment it becomes true for
some rows and false for others, on the same list, with no visible difference between them.

**B3.2 — does anything report coverage the region does not have?** Read narrowly — *coverage* as
the coverage checks define it — **no.** Both checks test whether a point's tile at some zoom lies
inside the region's *bounding-box footprint* (`OfflineTileMembership.kt:29–43`), computed from the
centre and radius, not from which tiles are on disk. The tile grid is nested, so a point inside the
box at zoom 15 is inside the box at zoom 14, and the zoom-14 tile containing it *was* downloaded.
So `GetTripReportOfflineRegionsUseCase` (at the row's `maxZoom`, `:28`) and
`GetCartographyEntryOfflineRegionUseCase` (at `MAX_ZOOM`, `:42`) each answer "this region's tiles
cover this point" correctly for a pre-deploy region — at zoom-14 detail. The "conservative rather
than wrong" reasoning holds unchanged because it never depended on the served ceiling: membership at
a finer zoom is a subset of membership at a coarser one, and the constant moving does not change
which tiles exist. On the rendering side, the offline style's source ceiling is 15 and vector tiles
overzoom, so a pre-deploy region viewed offline at zoom 15+ renders zoom-14 data stretched, not a
blank — less sharp than promised, never missing.

Read as the owner means it — *a user believing they have detail they do not, discovering it offline*
— **the row label is the one thing that misreports**, and it does so for every pre-deploy region,
today and after B1 alike. The consequence is one level of sharpness, not an absent map. So this item
does not close on point 2 alone: nothing misreports *coverage*, one label misreports *detail level*.

**B3.3 — recorded tile counts for budget accounting.** A premise correction first: tile counts are
**not recorded** anywhere. `OfflineRegionEntity` has no tile-count column (`OfflineRegionEntity.kt:30–46`),
and `listRegions` reads `status.completedTileCount` live from `OfflineManager` on every call
(`MapLibreOfflineMapRepository.kt:190,202`), as does `download()`'s returned summary (`:139`). So the
budget sums what is actually on disk right now — a pre-deploy region contributes its real zoom 10–14
count — and that is **correct for accounting, and stays correct** even if a region's contents ever
changed. They cannot change on their own: the only re-activation of a download is
`setDownloadState(STATE_ACTIVE)` inside `downloadToCompletionSuspend` (`:325`), called only from
`download()`, so an existing region is never re-enumerated against the newly advertised zoom 15.
Confirmed by reading, not assumed.

**B3.4 — options, one sentence each, no action taken.**

1. **Leave it.** Zero cost and no data change; the label keeps lying about detail level for old
   regions, in a list where new regions will tell the truth with identical wording.
2. **Mark it.** Needs a way to know which rows are pre-deploy: either a deploy date baked into the
   app (a third client constant encoding a server fact) or a per-region "served ceiling at
   download" column, which would have a real reader (the label) and so is not a dormant column,
   but is a schema change and cannot be back-filled for existing rows except by the same date
   heuristic or by comparing the live `completedTileCount` against the region's own zoom-10…14 vs
   zoom-10…15 estimates (a heuristic that is exact in practice — the two counts differ ~3.7× — but
   is still an inference about contents).
3. **Offer a re-download.** Delete and re-create the region at the new ceiling; costs ~3.7× the
   tiles, so a pre-deploy region larger than 25 km cannot be re-downloaded at all under B2, and a
   set of pre-deploy regions that fit 6000 at zoom 14 may not fit together at zoom 15 — the user
   would have to choose.

The owner decides. Nothing here is built.

---

## Part C — the no-sensor bearing text (built)

**Change.** `NavigationHud.kt`, `navigationReadout`'s `targetText` `when`: the final branch
`else -> "Bearing ${bearing…}° ${cardinal…}"` becomes `else -> ""`. The needle was already withheld
for this state (`targetArrowDegrees` requires a heading), so the column is now empty under its
dimmed icon exactly as in the approach and unreliable cases. The `else` branch is reached only when
`headingDegrees == null` and none of the earlier clauses hold, i.e. `TrueHeadingReading.NoSensor`
(`NeedsFix` cannot reach this function with a live fix). The class doc's "No sensor" bullet and the
inline ordering comment were updated to match; the earlier parenthetical saying no-sensor "still
shows the bearing as text … not changed here" was removed since it is no longer true.

**Tests changed (authorised — existing assertions).**

- `NavigationHudReadoutTest` — `no sensor - heading says so, target falls back to the absolute true
  bearing as text, no needle` → renamed `no sensor - heading says so, target shows neither needle nor
  bearing text`; its `assertEquals("Bearing 0° N", r.targetText)` is now `assertEquals("",
  r.targetText)`. The other four assertions in it (heading text, north arrow null, target arrow
  null, "0.7 mi") are unchanged.
- `AvailabilityScreenMapIconStackTest` — `with no compass sensor the HUD still shows the distance and
  the absolute true bearing` → renamed `with no compass sensor the HUD shows the distance and no
  bearing text`; `assertEquals("Bearing 0° N", textOfTag(NAVIGATION_HUD_TARGET_TAG))` is now
  `assertEquals("", …)` plus, as the dispatch asked, a node-count assertion:
  `onAllNodesWithText("Bearing", substring = true).assertCountEquals(0)` across the whole screen.
  The heading and "1.1 km" distance assertions are unchanged.

Why these two: they are the only two tests in the suite that assert the string "Bearing"
(`grep` of `app/src/test`), and each encoded the old behaviour as a requirement. No other test
touched.

**Verification.** Forward: `NavigationHudReadoutTest` and `AvailabilityScreenMapIconStackTest` together, 126 tests, 0 failed, 19 skipped (the icon-stack class's allowlisted entries). Reverted variant: the `else` branch restored to the old `"Bearing …"` string by a one-line sed, same two classes run, build log checked for `e:`/`compileDebugKotlin FAILED` (none — the reverted build compiled and ran), and exactly the two changed tests failed, each with `expected:<[]> but was:<[Bearing 0° N]>` — the message this revert and no other edit would produce. File restored afterwards and the forward diff re-confirmed. Full suite: 1155 tests, 0 failed, 24 skipped; the skip set compared by (class, name) against the CI `SKIPPED_TESTS_ALLOWLIST` parsed from `.github/workflows/ci.yml` — byte-identical, 24 = 24, nothing skipped outside the list and nothing listed that did not skip. The `JournalTabTest` "From Album" flake did not fire in this run.

---

## Device checks the owner must run (blocked here: no `/dev/kvm`)

Part C only, at this stage: navigate with the compass sensor absent (an emulator without a
rotation vector, or a device whose sensor is disabled) and confirm the target column shows the
dimmed icon with nothing under it while the distance slot still shows the distance. Parts A and B
device checks come with their builds; the dispatch's four are recorded here so they are not lost:
(1) a previously starbursted track draws clean and its distance dropped; (2) an ordinary walked
track is unchanged; (3) the tile estimate matches the actual download at the new slider maximum;
(4) a newly downloaded region renders sharper zoomed in offline.

---

## Required disclosure

### What I confirmed vs. what I inferred

Confirmed by reading `909c8ea`: the single read seam and its three callers; every `TrackRepository`
consumer and every `Track.points` reader; GPX export's path through the seam; the Cartography track
ref as the only persisted derived value and its two readers; `pointCount` having no reader; the
entity doc's "never silently change on reopen" rule; the sampler's four rules and the three modes'
floors; `SERVED_MAX_ZOOM`, `MAX_ZOOM`, `MIN_ZOOM`, `TILE_COUNT_LIMIT` and every site that reads them;
the absence of any other hard-coded served ceiling in `app/src/main`; the offline slider sharing
`Region.MAX_RADIUS_KM` with the search radius; the archive bbox in the worker README; both coverage
checks' geometry being footprint-based; tile counts being read live, not stored; the single
`setDownloadState` call site. Confirmed by computation: all six cross-check figures reproduced
exactly; the 25/26 km boundary at 49.60°N under a 40 × 40 alignment sweep.

Inferred: that MapLibre clamped pre-deploy downloads to the advertised 14 rather than fetching and
failing on 15 — this is what the constant's own comment records from the owner's device finding
("480 downloaded" against "~1774 estimated") and is consistent with completed status, but I did not
observe it; that vector overzoom renders a pre-deploy region at zoom 15+ from zoom-14 data rather
than blank — standard MapLibre behaviour with a `maxzoom: 15` source, not tested here; that the
photographed spokes are single-sample — I have not seen the images, and the rule's usefulness
depends on it; the filter's cost figure, which is arithmetic, not a measurement.

### What I could not determine

Whether the starburst spokes are one point or several (decides whether rule one as proposed removes
them); the deploy date that separates pre- and post-deploy regions on the owner's devices; whether
`OfflineManager` exposes per-zoom tile presence (I found no such API on `OfflineRegionStatus` in the
code this app uses and did not go to the SDK's bytecode for it); the worst-case alignment result to
better than a 40 × 40 sweep (the boundary has 282 tiles of slack at 25 km and 75 of excess at 26 km,
so a finer sweep cannot move it).

### Premises in this dispatch that were wrong

- "The edit screen already shows the stored distance beside a freshly computed one" — not for the
  same track. Each track is one row: decided rows read the snapshot, undecided rows compute fresh
  (`mergeDecisionRows`). The two methods share a screen, not a row.
- "Recorded tile counts for existing regions … were recorded from actual downloads" — they are not
  recorded at all; `listRegions` reads the live `completedTileCount` from `OfflineManager` each
  time. The conclusion the dispatch hoped for (accounting stays correct) is true, for a stronger
  reason.
- "The 6000 budget then holds roughly 28 km at 45°N" — 27 km worst-case at 45.357°N, 28 km only on
  a favourable centre, and 25 km at the archive's northern edge, which is the number that matters.
- "One branch, one existing test" (Part C) — one branch, **two** existing tests: the readout unit
  test and the screen test both pinned "Bearing 0° N".
- Minor: the pulse's "trip report" as a second persisted store — it is the same row read on a
  second screen.

### Anything I decided that this dispatch did not cover

- **Sum of the two accuracies as the "beyond" threshold** (rather than the larger, or a multiple).
  Argued above; the owner may prefer stricter.
- **A separate offline-radius maximum**, leaving the search radius at 50 km (B2.4). Flagged as a
  decision; not built.
- **Keeping `min(MAX_ZOOM, SERVED_MAX_ZOOM)`** as a currently-no-op seam (B1). Proposed, not built.
- **Null accuracy keeps the point and logs once per read** (A1.1). The dispatch did not say what to
  do with an unevaluable point; "unsupported, so untouched, and visibly so" is CLAUDE.md's rule, but
  the owner should confirm keeping rather than dropping is the intended direction.
- **The Part C doc-comment edits** beyond the one branch — three comments in `NavigationHud.kt`
  that described the old behaviour were updated so the file does not contradict itself. No
  behaviour beyond the one branch changed.
