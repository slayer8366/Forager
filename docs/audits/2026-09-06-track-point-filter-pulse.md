# Pulse: track point sampling, and where a display-time filter would go

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Type:** read-only survey (owner-directed pulse, from a device pass). **Nothing built; no product code, no tests, no schema change, no migration.**
**Date:** 2026-09-06. **Base:** `main` at `41ce4e1412a3fae52089c7c1fe0a0cc6e98f818f` (confirmed: `git rev-parse origin/main`). Branch `claude/new-session-102gri` sits on it with one prior docs-only commit (`fcca1be`, the light-budget pulse) and this report.

Every claim names a file and line on `41ce4e1`, or is marked inferred. Nothing from the prior pulse is carried forward without being re-derived here; where the prior pulse was wrong, this says so.

---

## Which case this repo is in — stated first

| Question | Answer |
|---|---|
| Is accuracy persisted per point? | **Yes.** `track_points.accuracyMeters REAL` (nullable), since the table was created (`Migrations.kt:115-123`, `MIGRATION_4_5`); mapped both ways in `RoomTrackRepository.kt:78-95`. Existing tracks carry it. |
| Is the provider persisted per point? | **No.** Not on `TrackPoint`, not on `LocationFix.Update`, not on the entity. `AndroidLocationTracker.toFix()` never reads `Location.provider` (`AndroidLocationTracker.kt:68-74`). **Neither can speed, bearing, or any other platform field be used on existing tracks.** |
| Is there a bypass-proof read path? | **Yes, already.** Every read of track points in the app goes through `RoomTrackRepository`, and inside it through one private mapping: `entity.toDomain(dao.getPointsForTrack(id))` (`RoomTrackRepository.kt:25, :30, :36`). No consumer touches `TrackDao` or `track_points` directly (§3). |

So: **a display-time filter using accuracy, implied speed and excursion shape can be applied at one existing seam, retroactively, to every track ever recorded. A filter that needs the provider cannot — for old or new tracks — until a schema change.** That is the shape: one dispatch for a filter on what is stored, a separate schema change if the provider is wanted, and (§5) a third finding about what recording currently throws away.

**And the scale question, plainly:** if the fan's points are in the database, `ComputeTrackStatisticsUseCase` already sums every spoke out and back (`ComputeTrackStatisticsUseCase.kt:61-66`), the Cartography snapshot has already **persisted** that sum (`CartographyEntryTrackRefEntity.distanceMeters`, §6), and the trip report displays it rounded to whole kilometres. I cannot establish the count from the code — it needs the device's database or a GPX export — but the arithmetic is: each spoke of length *d* adds 2*d*; dozens of spokes over 100 m is kilometres. See §6.

---

## 1. Settle the contradiction — the sampler, quoted

`domain/LocationSampler.kt:22-36`:

```kotlin
fun shouldAccept(lastAccepted: TrackPoint?, candidate: TrackPoint): Boolean {
    val accuracy = candidate.accuracyMeters
    if (accuracy != null && accuracy > mode.maxAcceptableAccuracyMeters) return false
    if (lastAccepted == null) return true

    val elapsedMillis = candidate.timestampEpochMillis - lastAccepted.timestampEpochMillis
    if (elapsedMillis < mode.minIntervalMillis) return false

    val distanceMeters = GeoDistance.metersBetween(
        LatLng(lastAccepted.lat, lastAccepted.lng),
        LatLng(candidate.lat, candidate.lng),
    )
    return distanceMeters >= mode.minDistanceMeters
}
```

Four rules, in order: (1) reported accuracy worse than the mode's ceiling → reject (null passes); (2) first point → accept; (3) less than the mode's interval since the **last accepted** point → reject; (4) at least the mode's distance from the **last accepted** point → accept. **There is no movement test.** Nothing looks at speed, at the previous *rejected* fix, or at the shape of recent points; "distance from the last accepted point" is the only thing that distinguishes standing still from walking.

**Is there a stationary suppression?** Only rule 4, and only against genuine jitter. A fix that lands within `minDistanceMeters` of the last accepted point (5 m in HIGH_ACCURACY, 15 m in BALANCED, 30 m in BATTERY_SAVER — §5) is refused, so a phone whose fixes wander less than that writes nothing. **Anything that lands further away than that is, to this sampler, movement.** A 40 m spike passes rule 4 outright; the next fix back at the true position is 40 m from the spike, so it passes too. The sampler cannot tell a spike-and-return from a walk-there-and-back, because it was not built to.

**The prior pulse's claim was wrong as stated.** It said "the sampler writes no points while stationary" and "a stationary user produces no points at all." That is true only while the fixes themselves stay within the distance threshold. It is false the moment a fix jumps, and the screenshots show fixes jumping. I wrote that claim; it was derived from the sampler's doc comment ("a stationary period doesn't keep writing points once the interval elapses with no real movement", `TrackRecordingMode.kt:8-11`) rather than from the code path a bad fix takes. Corrected here.

**Reconstructing the figure from the code — two spokes out and back, drawn in one tick.** With testers on HIGH_ACCURACY (`MainActivity.kt:425-431`; 5 s / 5 m / 30 m):

1. Accepted point A at the true position. A fix at B, 40 m away, reporting accuracy ≤ 30 m, arrives ≥ 5 s later: rule 1 passes (accuracy under the ceiling), rule 3 passes, rule 4 passes (40 ≥ 5). **Written.**
2. The next fix back at A, ≥ 5 s later: 40 m from B. **Written.** That is one spoke, out and back.
3. Repeat: a second spoke.
4. **"In one tick":** points are not drawn as they are accepted. The service buffers them and flushes every `FLUSH_BATCH_SIZE = 20` points or every `FLUSH_INTERVAL_MILLIS = 30_000L` (`TrackRecordingService.kt:129, :140, :252-253`); the ViewModel re-reads the whole track from Room every `POLL_INTERVAL_MILLIS = 15_000L` (`TrackRecordingViewModel.kt:249-257, :458`). A 30 s flush at a 5 s cadence is up to six points landing at once — two spokes (four points) in a single redraw is exactly the batch shape.

**Which rule permits it:** rule 4 permits the spoke (it is far enough), rule 1 fails to stop it (the spike's *reported* accuracy is under the ceiling), and rule 3 sets the cadence. The pattern is fully reconstructible from the code; nothing about it is unexplained. What the code cannot say is *why* the fix jumped — §1a.

## 1a. Which provider produced each fix — the hypothesis, tested against the code

**The two-listener claim is correct.** `AndroidLocationTracker.kt:53-57`:

```kotlin
val requestedProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    .filter { locationManager.isProviderEnabled(it) }
requestedProviders.forEach { provider ->
    locationManager.requestLocationUpdates(provider, MIN_UPDATE_INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper())
}
```

Both providers, **one shared listener**, `MIN_UPDATE_INTERVAL_MILLIS = 1_000L` (`:80`), minimum displacement `0f`. The class doc says why: "Requests updates from every enabled provider (GPS and network) at once rather than picking one … a multi-hour recording can outlast a single provider losing its fix (GPS under canopy, say), and `LocationSampler` downstream already filters on reported accuracy regardless of which provider a fix came from" (`:24-27`). That last clause is the assumption the hypothesis breaks: the sampler filters on *reported* accuracy, and a network fix's reported accuracy is not a measure of how wrong it is.

**Merged into one stream with no distinction — yes.** `onLocationChanged(location: Location) { trySend(location.toFix()) }` (`:45-47`) and `toFix()` maps `latitude, longitude, altitude?, accuracy?, time` only (`:68-74`). `LocationFix.Update` has no provider field (`LocationTracker.kt:39-45`); `TrackPoint` has none (`TrackPoint.kt:13-19`); `TrackPointEntity` has none. **Nothing downstream — sampler, service, ViewModel, statistics, map — can tell a GPS fix from a network fix, and nothing recorded can be told apart after the fact.** That is the finding.

**Does the code support the hypothesis?** It is consistent with every observable: two sources at a 1 s floor each, interleaved through one listener, gated only by a reported accuracy the network provider is known to under-report, at a sampler cadence (rule 3) that accepts *whichever provider's fix arrives first once the interval has elapsed* — which alternates by timing, not by choice. A stationary user, a fixed Wi-Fi/cell centroid, and a sampler that treats any ≥ 5 m displacement as movement produce a same-bearing fan out and back, and the fan's spoke length is the centroid's distance from the user. The prior figure's "over 100 m" is well within what a cell-tower-derived fix does. **The code does not prove it**: with the provider unrecorded, no stored track can confirm which source produced which point, and the app has no logging of provider at all (no `Log` call names one).

**What accuracy a network fix reports — hardware only.** The code contains no provider-specific handling and no recorded value; the platform documents only that `Location.getAccuracy()` is the 68 %-confidence horizontal radius as estimated *by the provider*. Whether a given device's network provider reports, say, 20 m for a fix that is 120 m wrong is exactly the optimistic-accuracy behaviour the pulse names, and only a device log (with `location.provider` printed next to `accuracy` and the offset from a known position) establishes it. **That log is the single hardware test that decides this.**

**If the hypothesis is wrong** — a same-bearing fan without a second source would need a systematic GPS bias that switches on and off: multipath off one reflector (a rock face, a building, a vehicle) can bias fixes in a consistent direction, but it tends to *hold* the bias rather than alternate it every few seconds; the alternation is the signature of two sources. The code offers one more candidate: the one-shot locate-me path (`AndroidLocationProvider`) also races both providers (`AndroidLocationProvider.kt:49-51`), but its result feeds the camera, not the track. Reported for completeness; not the explanation for recorded points.

**For the filter's design, so it is not lost (reported, not built):** a filter on the reported error circle alone would pass every one of these fixes, because their reported circle is small. The thing that distinguishes them in the *stored* data is geometry and timing — out-and-back at walking-impossible implied speeds — not accuracy. And a fix at the source (choosing what to listen to, or tagging what arrives) is a different dispatch from a display-time filter; the tracker's own doc comment records the reason both providers were requested (canopy), so dropping network outright has a cost that reason names.

---

## 2. What is stored

**`TrackPoint`, every field** (`domain/model/TrackPoint.kt:13-19`):

```kotlin
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val timestampEpochMillis: Long,
)
```

**Entity** (`data/local/TrackPointEntity.kt:25-34`): the same five plus `@PrimaryKey(autoGenerate = true) val id: Long` and `val trackId: String`, `indices = [Index("trackId")]`. **Table DDL** (`Migrations.kt:115-123`): `id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackId TEXT NOT NULL, lat REAL NOT NULL, lng REAL NOT NULL, altitude REAL, accuracyMeters REAL, timestampEpochMillis INTEGER NOT NULL`.

- **Accuracy: persisted**, nullable, since the table's creation — the load-bearing answer is yes. `TrackPoint`'s own doc says why it was kept: "so statistics or a later re-filter can still see what the device actually reported" (`TrackPoint.kt:9-10`) — the re-filter this pulse is about was anticipated.
- **Speed, bearing, provider: none of them.** Confirmed: `toFix()` reads `latitude, longitude, hasAltitude()/altitude, hasAccuracy()/accuracy, time` (`AndroidLocationTracker.kt:68-74`). What the platform `Location` offers and this drops: `provider`, `speed` (+ `hasSpeed`), `bearing` (+ `hasBearing`), `speedAccuracyMetersPerSecond`, `bearingAccuracyDegrees`, `verticalAccuracyMeters`, `elapsedRealtimeNanos` (a monotonic clock immune to wall-clock jumps), `isMock`, and the provider-specific `extras` bundle (satellite count for GPS). Of these, **`provider` and `elapsedRealtimeNanos` are the two that bear on this problem**; `speed` is provider-estimated and would itself be suspect on a network fix.
- **GPX export drops accuracy too**: `encodeTrackPoint` writes `lat`, `lon`, `<ele>`, `<time>` (`GpxCodec.kt:47-49`); import sets `accuracyMeters = null` (`:98`). An exported track cannot be filtered on accuracy outside the app, and a re-imported one cannot be filtered inside it.

**Schema version: 14** (`ForagerDatabase.kt:153`, `exportSchema = true`), migrations `3_4` through `13_14` (`Migrations.kt:19-859`), exported schemas 4–14 under `app/schemas/com.zynergylabs.forager.app.data.local.ForagerDatabase/`. **Adding a column to `track_points` costs:** `MIGRATION_14_15` (`ALTER TABLE track_points ADD COLUMN …`, nullable so existing rows need no backfill), version 15, a new exported `15.json`, `TrackPointEntity`, `TrackPoint`, both mappers in `RoomTrackRepository`, `LocationFix.Update` and `toFix()` to carry it from the platform, `GpxCodec` if it is to survive export, every `TrackPoint(`/`LocationFix.Update(` constructor site in tests (`grep` finds them across the availability, track and domain test packages), and a migration test in the pattern of `TrackOriginWaypointMigrationTest` — noting `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`: legacy fixtures built from production entity classes break when an *existing* entity is altered, which this would be. Also CLAUDE.md's rule that no column lands without a reader in the same change, and its globally-unique-version collision warning (check `main`'s version at the time, not the one you started from).

---

## 3. Every consumer of track points

Found by search (`grep -rn "\.points\b\|breadcrumbPoints\|getPointsForTrack\|List<TrackPoint>" app/src/main`), not by recall:

| # | Consumer | File:line | Reads points via |
|---|---|---|---|
| 1 | **Live breadcrumb polyline** on the map | `TrackRecordingViewModel.beginPolling` (`:249-257`) → `TrackRecordingUiState.breadcrumbPoints` (`:28`) → `MainActivity.kt:437` `trackUiState.breadcrumbPoints.map { LatLng(it.lat, it.lng) }` → `MapOverlayContent.breadcrumbPoints` (`MapSlot.kt:115`) → `breadcrumbFeatureCollection` (`SightingsMap.kt:908-912`, one `LineString`) | `trackRepository.getById(trackId)` every 15 s → `track?.points` |
| 2 | **Track statistics** (distance, duration, elevation) | `ComputeTrackStatisticsUseCase.invoke(points)` (`:40-105`), called from `CartographyViewModel.toDecision` (`:482-491`) and `CartographyEntryEditScreen.kt:491` | on a `Track` obtained via `getForDay`/`getById` |
| 3 | **Cartography — decision rows** (edit screen) | `CartographyEntryEditScreen.kt:474-500`: decided rows read the **stored snapshot** `it.distanceMeters, it.durationMillis`; candidate rows **recompute** `ComputeTrackStatisticsUseCase()(track.points)` | stored snapshot / `Track.points` from the derived trip |
| 4 | **Cartography — kept-track polylines** on the entry's map | `GetCartographyEntryMapDataUseCase.kt:42-44` `trackRepository.getById(decision.trackId).getOrNull()?.points?.map { LatLng(it.lat, it.lng) }` → `MapOverlayContent.keptTrackPolylines` (`MapSlot.kt:170`) → `CartographyEntryReportScreen.kt:359` | `getById` |
| 5 | **Trip report — kept-track lines** | `CartographyEntryReportScreen.kt:485-488` `trackSubtitle(it.distanceMeters, it.durationMillis, …)` | **stored snapshot only** (`TrackDecision`, persisted as `CartographyEntryTrackRefEntity`) |
| 6 | **Trip report — offline-region candidates** | `GetTripReportOfflineRegionsUseCase.kt:33-37` `tracks.forEach { track -> track.points.forEach { … } }` | `DerivedTrip.tracks` ← `GetDerivedTripUseCase.kt:42` `trackRepository.getForDay(…)` |
| 7 | **Records tab** track list | `TrackExportPanel.kt:103-107` `track.points.size` ("N points · recording") | `GetTracksUseCase` → `repository.getAll()` |
| 8 | **GPX export** | `GpxCodec.kt:38` `track.points.forEach { append(encodeTrackPoint(point)) }` | the same `Track` as #7 |
| 9 | **Return-to-start fallback origin** | `TrackRecordingViewModel.kt:417-419` `uiState.value.originWaypoint?.asStartPoint() ?: uiState.value.breadcrumbPoints.firstOrNull()` | #1's polled list |
| 10 | **Off-track heuristic** | `DetectOffTrackUseCase` — **does not read track points at all**: it reads a list of straight-line *distances to the start* computed from live fixes (`TrackRecordingViewModel.kt:274-292, :396-430`) | not a points consumer |
| 11 | **Origin seeding** | `TrackRecordingViewModel.kt:284-289` — reads the live fix, gated by `LocationSampler(active.mode).shouldAccept(lastAccepted = null, …)` | not a stored-points consumer |
| 12 | **The sampler's own `lastAccepted`** | `TrackRecordingService.kt:110-133` — in memory, write side | not a read of the store |

**Write side, for completeness:** `TrackRecordingService` → `RecordTrackPointsUseCase` (`:13-16`) → `TrackRepository.appendPoints` → `dao.insertPoints` (`RoomTrackRepository.kt:43-46`, `TrackDao.kt:53-55`, `@Insert(onConflict = REPLACE)` on an autoincrement key, so REPLACE never fires).

### The question that decides the dispatch

**A single read path exists and everything already goes through it.** `TrackRepository` exposes three readers — `getAll`, `getById`, `getForDay` (`TrackRepository.kt:17-28`) — and `RoomTrackRepository` implements all three as `entity.toDomain(dao.getPointsForTrack(…))` (`:25, :30, :36`). `TrackDao.getPointsForTrack` (`:47-48`) has no caller outside that class, and no other code references the `track_points` table (`grep -rn "track_points" app/src/main` finds `TrackDao`, `TrackPointEntity`, `Migrations` and two doc comments). Consumers #1, #2, #3-candidates, #4, #6, #7, #8, #9 all receive a `Track` from one of those three methods; #3-decided and #5 read the persisted snapshot (§6), which was itself computed from a `Track` that came through the same path.

**So a filter applied inside `RoomTrackRepository` (or a `TrackRepository` decorator the container wires in) is bypass-proof today.** Cost to make one: none for the seam itself — it exists. What the seam does *not* give for free, and what becomes decisions:

- **Filtering there filters everything**, including GPX export (#8) and the point count (#7) — the "is the raw track ever viewable" question the pulse lists; a raw path would be a second, explicitly-named method, not the default.
- **It does not reach the snapshot** (#3-decided, #5) — §6.
- **It does not reach the write-side sampler** (#12) or origin seeding (#11), which see live fixes, not stored points; the sampler's `lastAccepted` would still be the raw last point, so the recording cadence is unaffected by a display filter — consistent with "store raw".
- **It does not reach the live HUD**, which uses `liveFix`, not points; the HUD's own 50 m gate is separate and stays so.

---

## 4. What a filter could use — what the data supports

- **Distance relative to accuracy:** supported, retroactively — `accuracyMeters` is on every stored point that had one (§2). Its limit is the one §1a names: a network fix's reported accuracy is small while its error is large, so this signal alone passes the fan.
- **Implied speed between consecutive points:** supported. `timestampEpochMillis` is `Location.time` in epoch milliseconds (`AndroidLocationTracker.kt:73`), read back `ORDER BY timestampEpochMillis ASC` (`TrackDao.kt:47`); consecutive accepted points are ≥ `minIntervalMillis` apart by rule 3. Two caveats, both inferred from the platform rather than the code: `Location.time` is the provider's wall-clock stamp and a network fix's stamp can predate its delivery (a cached scan), so arrival order and timestamp order can differ; and the wall clock can step. `elapsedRealtimeNanos` is the monotonic alternative and is not stored.
- **Out-and-back excursion detection:** nothing reasons over a window of consecutive *points*. The only rolling window is `DetectOffTrackUseCase` (`domain/DetectOffTrackUseCase.kt`): `if (recentDistancesMeters.size < WINDOW_SIZE) return false; val window = recentDistancesMeters.takeLast(WINDOW_SIZE); return window.last() - window.first() > NET_INCREASE_THRESHOLD_METERS` with `WINDOW_SIZE = 3`, threshold 25 m — a window over straight-line *distances to the start*, fed from live fixes, answering "am I getting further from home". **Coincidental, not reusable:** different input (scalars, not points), different question, different feed. `ComputeTrackStatisticsUseCase`'s consecutive-pair loop (`:61-66`) is the only code that walks a point list pairwise, and it sums; it does not judge.
- **Order, duplicates, batching:** points are appended in arrival order within a batch (`pendingPoints += candidate`, `TrackRecordingService.kt:127`) and batches are written in order by one coroutine, under a mutex (`:161-168`); reads are timestamp-ordered regardless. Duplicates: the autoincrement key makes `REPLACE` a no-op, so two identical rows are possible if two identical fixes arrive — nothing dedupes — but the sampler's rule 4 refuses a point within `minDistanceMeters` of the last accepted, so an exact duplicate cannot be *accepted* consecutively. **The 20-point / 30 s flush is confirmed** (`:129, :140, :252-253`); it changes when points become visible, not their order.

---

## 5. The recording gate as it stands

`domain/model/TrackRecordingMode.kt:30-32`:

```kotlin
HIGH_ACCURACY(minIntervalMillis = 5_000L, minDistanceMeters = 5f, maxAcceptableAccuracyMeters = 30f),
BALANCED(minIntervalMillis = 15_000L, minDistanceMeters = 15f, maxAcceptableAccuracyMeters = 50f),
BATTERY_SAVER(minIntervalMillis = 60_000L, minDistanceMeters = 30f, maxAcceptableAccuracyMeters = 100f),
```

Where the numbers came from, in the code's own words: "These numbers are labelled adjustable assumptions, not measured facts — chosen for a plausible multi-hour foraging walk and not yet checked against a real recorded track's battery draw" (`:16-19`). The live gate's 50 m was chosen separately for display and documented as deliberately not a reference to BALANCED (`domain/LiveFixGate.kt`).

**A fix rejected for recording is dropped, not written — on all four rules.** `TrackRecordingService.kt:125-131`: `if (sampler.shouldAccept(lastAccepted, candidate)) { lastAccepted = candidate; … pendingPoints += candidate … }` — there is no `else`. A fix that fails the accuracy ceiling, the interval, or the distance rule is gone; nothing records that it arrived. **Under the owner's "store raw" decision this is a finding, and it changes the shape of the work:** the store today is not raw, it is *sampled*; "filter at display time" starts from a stream that has already discarded, by accuracy, the fixes most likely to be the bad ones — and, by the interval and distance rules, most of the good ones. Whether "store raw" means removing the accuracy rule only, removing all sampling (with the storage and battery consequences the mode's doc comment worries about), or persisting rejected fixes in a separate stream, is a decision this pulse does not list. Flagged.

---

## 6. Retrofit

**Filtering tracks already recorded:** possible for anything built on position, accuracy and timestamp, because all three are stored for every point ever written (§2); impossible for anything built on provider, speed or bearing, for every track that exists today and every track recorded until a schema change. The seam (§3) applies retroactively by construction — old tracks read through the same repository.

**Derived values already persisted — yes, and they would disagree.** `CartographyEntryTrackRefEntity(entryId, trackId, name, distanceMeters, durationMillis, pointCount, kept)` (`CartographyEntryEntity.kt:60-73`), written from `TrackDecision` at decision time via `CartographyViewModel.toDecision`: `val stats = computeTrackStatistics(points); … distanceMeters = stats.distanceMeters, durationMillis = stats.durationMillis, pointCount = stats.totalPoints` (`:482-491`), persisted by `RoomCartographyEntryRepository` (`:96-106`). `TrackStatistics`' own doc says statistics are "always computed on demand from `Track.points` … never stored redundantly alongside the track" (`TrackStatistics.kt:4-5`) — true of the track, and then the Cartography entry stores a copy anyway, by design ("snapshot", `CartographyEntry.kt:102`). It is displayed in two places: the entry's decided rows (`CartographyEntryEditScreen.kt:481`) and the trip report (`CartographyEntryReportScreen.kt:487`), both through `trackSubtitle`, which rounds to **whole kilometres**: `val km = (distanceMeters / 1000.0).roundToInt()` (`:628-629`).

**The two-numbers failure already exists on one screen, before any filter:** the edit screen shows a decided track's *stored* distance beside a candidate track's *freshly computed* one (`:481` vs `:491-495`); today they agree only because nothing filters. A display-time filter at the repository seam would change #2, #3-candidates, #4, #6 and the map, and leave #3-decided and #5 at the unfiltered snapshot — a kept track reading "3 km" in the report while its polyline on the same screen is a dot. Whether snapshots are recomputed, versioned or left is the owner's listed decision; that they *will* disagree is established.

**Scale of the corruption I can establish from the code:** only the mechanism and the multiplier. Each accepted out-and-back spoke of length *d* adds 2*d* to `distanceMeters` (`ComputeTrackStatisticsUseCase.kt:61-66`, consecutive-pair sum, no filtering); under HIGH_ACCURACY the maximum accepted cadence is one point per 5 s, so a stationary hour of alternating fixes at 100 m adds up to 720 × 100 m = 72 km — an upper bound, not a measurement. The actual figure for the owner's tracks needs the device database or a GPX export (`TrackExportPanel`), and **GPX will show the spokes but not the accuracy** (§2), so the export cannot separate them by error circle either. **I cannot establish the count; the owner can, by counting spokes on the exported track or by reading `track_points` on the device.**

---

## Decisions this pulse does not list — flagged, not picked

- **What "store raw" means against a sampler that drops on four rules** (§5): accuracy rule only, all rules, or a second stream for rejected fixes.
- **Whether the provider is worth a schema change on its own**, before a filter exists to read it — it is the one field that would let the hypothesis be tested on real tracks going forward (§1a, §2).
- **Whether to log `provider` + `accuracy` at the tracker for the beta**, without storing it — the cheapest way to get the hardware answer §1a needs; a code change, so not done here.
- **The GPX export's role**: it is the only way a tester can hand over a track today, and it carries no accuracy; whether it should is a separate, small decision.
- **Overlap with the alert-delivery dispatch:** none in code touched — the off-track heuristic is not a points consumer (§3 #10). Reported as no overlap.

## Standing rules

**Tests as found on `41ce4e1`:** **1134 tests, 0 failures, 0 errors, 24 skipped** (`./gradlew :app:testDebugUnitTest --continue` on the unmodified tree, summed from the JUnit XML); the skipped set is byte-identical to the CI allowlist (24 entries). Matches the pulse's baseline of 1134 / 24. Nothing was modified to run it; the `JournalTabTest` "From Album" flake did not fire..

## Only hardware can answer

1. **The provider hypothesis**: `location.provider`, `location.accuracy`, and the offset from a surveyed position, logged per fix while stationary — does the network provider produce the same-bearing fixes, and what accuracy does it claim for them?
2. Whether `Location.time` on network fixes lags delivery on the owner's device (bears on implied-speed ordering).
3. The actual point count and summed distance of the fan tracks in the device database.

## Required disclosure

**Confirmed (read on `41ce4e1`):** the four sampler rules and the absence of any movement test; both providers on one listener at 1 s / 0 m; no provider, speed or bearing anywhere in the domain, entity or GPX; accuracy persisted since the table's creation; schema 14, migrations 3→14, exports 4–14; all twelve consumers and their access paths; the single repository seam with no bypass; the 20-point / 30 s flush and 15 s poll; rejected fixes dropped on all rules; the persisted Cartography snapshot and its two display sites; whole-km rounding in `trackSubtitle`.
**Inferred:** the reconstruction's cadence (whichever provider's fix arrives first after the interval); that network fixes under-report accuracy and that their `time` can lag (platform behaviour, not this code); the 72 km/h upper bound (arithmetic from the mode constants, not a measurement).
**Could not determine:** everything under "Only hardware can answer"; the true scale of existing corruption.
**Premises in this pulse that were wrong:** none found in *this* pulse. The prior pulse's premise it inherits — "the sampler writes no points while stationary" — was wrong as stated, and I wrote it; corrected in §1.
**Decided without cover:** nothing built; the ranking "provider is the one field worth a schema change on its own" is a judgement, stated as a flagged decision, not made.

---

# Addendum — owner's rulings and the two decisions handed back

**Owner's rulings (same day):** the sampler self-correction stands as a class of error worth remembering — a claim taken from a doc comment rather than from the path a bad fix takes. The Cartography distance snapshot is to be **recomputed**: it is a cache of a computation, not a record of a user decision, and the number it cached was wrong; leaving it would put the two-numbers failure on the edit screen deliberately. The hardware test (sit still, log provider + reported accuracy + offset per fix) is the owner's to run.

**Decision 1 — "store raw" versus filter-first: decouple; filter at the seam first.** The filter is what repairs the drawn line and the statistics, it applies to every track ever recorded through the one existing seam, and it does not touch recording. Storing raw helps only tracks recorded after it lands, and it is a volume question the mode's own doc comment already worries about: today HIGH_ACCURACY writes at most 720 points an hour; two providers at a 1 s floor with no sampling is up to 7 200 rows an hour, ten times the storage and a Room re-read every 15 s ten times the size (`beginPolling` loads the whole track). Doing the filter first also answers whether a later, better filter would even want the discarded fixes — if the filter on stored points already removes the fan, the case for keeping rejected fixes is weaker than it looks today. "Store raw" is a separate, later change with its own pre-build questions (which rules to lift; whether rejected fixes go to a second stream; the poll's cost at that volume).

**Decision 2 — the provider column: wait.** Three reasons. The no-dormant-columns rule: a filter for existing tracks cannot read it, so a filter that reads it only for new tracks is two code paths for one filter, and a column read by neither is not allowed. The hypothesis does not need it: the owner's per-fix log answers "is the network provider the source" without a schema change. And if the log confirms it, the fix belongs at the source — tagging or refusing network fixes in `AndroidLocationTracker` — at which point a stored provider is either unnecessary (refused) or arrives with its reader (tagged and filtered). So: log first; the column only if the source-side fix needs it, and then with its reader in the same change.

**One detail the snapshot recompute must settle, flagged for its dispatch:** `CartographyEntryTrackRefEntity` carries no foreign key by standing rule so that nothing in an entry changes as a side effect of the track — including the track being deleted (`CartographyEntryEntity.kt:55-58`). A recompute from the filtered track is only possible while the track exists; for an entry whose track has since been deleted, the snapshot is the only record. Recompute where the track exists and keep the snapshot as the fallback is the obvious shape; whether a fallback value known to be unfiltered is shown, marked, or withheld is the decision.

**The hardware log, so it is specified once:** in `AndroidLocationTracker.onLocationChanged`, per fix: `location.provider`, `location.accuracy` (and `hasAccuracy()`), `latitude`/`longitude`, `time`, `elapsedRealtimeNanos`; sitting still for several minutes at a surveyed point, then the offset of each fix from that point. Two providers interleaving with a same-bearing offset on one of them confirms the hypothesis; a single-provider log with the same fan refutes it. This is a code change (a temporary log line), so it is not done here; it is a one-line branch when the owner wants it.
