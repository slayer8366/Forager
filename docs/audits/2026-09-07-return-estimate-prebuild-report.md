# Pre-build report: the return estimate — how long the walk back takes

**Type:** report-before-building, on every item the dispatch marks so. **Nothing built: no product code, no tests, no schema change, no dependency.**
**Date:** 2026-09-07. **Dispatch:** `dispatch-return-estimate.md` (Items 1–4), first of the light-budget sequence.
**Base:** `main` at `bc728119b31b86467413912ef1db2f569bfbc91b` — the merge of PR #75 (Stage 2e-ii and the plate corrections), fetched after that merge. **Branch:** `claude/new-session-b7z9bg`, restarted from this `main` (`git checkout -B … origin/main`, force-with-lease) because its previous history was merged in #75; that satisfies both the dispatch's "branch off `main`" and this session's own pinned branch name. **No collision with 2e-ii**: it is already in the base, and nothing below touches the map style or the entry report screen.

Every claim names a file and location on `bc72811`, or is marked inferred. The light-budget pulse (`2026-09-06-light-budget-pulse.md`) was re-derived, not trusted; where it still holds it is said so, and the one place it has moved is called out.

---

## The two things that carry the most weight, answered first

**Can moving speed be separated from elapsed speed using stored points? Yes, with one caveat that is not fatal.** The sampler writes nothing while the user is still (`LocationSampler.kt:20-23`: both the interval *and* the distance threshold must be met), so every stop is a time gap between two stored points that are a few metres apart. A pair spanning a ten-minute photograph has an implied speed of centimetres per second; a pair on a walk has metres per second. Excluding pairs below a floor separates the two — §2.4 gives the arithmetic. The caveat: a GPS outage under canopy and a run of timestamp-filtered points leave gaps of the same shape, and stored points alone cannot tell a stop from an outage (§2.6). Excluding those gaps too does not bias the *speed* — it only removes data — so the ruling's stability property survives. **And there is a better instrument sitting unread**: the platform reports a Doppler speed on every GPS fix, the tracker discards it, and it would answer "was the phone moving at that instant" per point rather than per pair (§2.1). This report proposes building on it and stops there, as the dispatch asked.

**Can the estimate be made confidently short under realistic input? Yes, in four ways, and every one of them needs a decision before code.** Listed loudly in §5: nearest-point snapping on switchbacks skips whole legs; the timestamp filter's exclusions shorten the recorded path beneath the walk actually taken; a pace measured going out downhill is applied to a return uphill; and a fresh estimate from a handful of points has no basis at all. Item 3's degraded form exists for the last; the first three are design choices in Item 1 and the elevation question.

---

## 1. Item 1 — distance home along the track

### What exists, re-derived

- **Cumulative distance along a track:** `ComputeTrackStatisticsUseCase` (`domain/ComputeTrackStatisticsUseCase.kt:39-101`), a forward pass summing `GeoDistance.metersBetween` over consecutive points. Retrospective only; nothing calls it during recording. Unchanged since the pulse.
- **Distance back:** straight-line only — `ComputeReturnToStartUseCase` (`:8-24`, `distanceMeters = GeoDistance.metersBetween(currentLatLng, startLatLng)` from the current point to `start: TrackPoint`), and the HUD's own straight line to the origin waypoint (`NavigationHud.kt`). No path-along-track computation exists. Unchanged.
- **The points are in memory every 15 s during recording:** `TrackRecordingViewModel.beginPolling` re-reads the whole track (`TrackRecordingViewModel.kt:274-291`: `trackRepository.getById(trackId)` then `delay(POLL_INTERVAL_MILLIS)`, `POLL_INTERVAL_MILLIS = 15_000L` at `:492`) and holds `track.points` as `breadcrumbPoints`. The service buffers accepted points and flushes at `FLUSH_BATCH_SIZE = 20` (`TrackRecordingService.kt:61, :128-129, :252`). Unchanged.
- **The read is filtered.** `RoomTrackRepository.toDomain` applies `excludeNetworkProviderFixes` and carries `excludedPointCount` (`RoomTrackRepository.kt:66-79`). **New since the pulse**, and it matters for §1.2 and §5.
- `GeoDistance` has `metersBetween`, `boundingBox`, `circlePolygonPoints`, `boundingRegion`, `initialBearingDegrees` (`GeoDistance.kt:37-153`). No projection of a point onto a segment; no nearest-point search.

### 1.1 How the current position maps onto the track — the choice, and which way each errs

Three candidates, with the property the dispatch cares about (which direction the error runs):

| Mapping | Distance home | Where it is wrong | Direction |
|---|---|---|---|
| **Most recent stored point** (`points.last()`) | cumulative length of the whole recorded track, plus the hop from the fix to that point | After a double-back, the user has already walked part of the way home; the whole outbound-and-back length is charged again | **Long** — safe |
| **Nearest stored point** by `metersBetween` | cumulative length up to that point, plus the hop | On switchbacks, a loop that passes near itself, or two sides of a ravine: the nearest point can be on a different leg — 20 m away horizontally, a whole leg away on foot. The estimate then skips that leg | **Short** — the dangerous one |
| **Nearest point among a recent suffix** (e.g. the last N points, or points whose cumulative distance is within some window of the current end) | as nearest, restricted | Avoids snapping onto a leg walked an hour ago; still short if the switchback is recent | Short, less often |

Two facts sharpen this. First, **the live fix is known to 50 m at worst** (`LIVE_FIX_MAX_ACCURACY_METERS = 50f`, `LiveFixGate.kt:51`), and the recording's own points to 30–100 m depending on mode, so "nearest" is a comparison between two uncertain positions; a nearest-point choice can flip between legs from one fix to the next, and with it the estimate. Second, the hop from the fix to the chosen point is itself a straight line and belongs in the total (§1.2).

**Not decided.** What the code makes easy: all three are a few lines over the list the ViewModel already holds. What nothing makes easy: knowing you are on a switchback. **Inferred, offered for the owner:** the most-recent-point mapping is the only one whose error is never short, and its overstatement after a double-back is bounded by the length of the doubled-back leg, which the user has just walked and knows about.

### 1.2 Off the track, and gaps in it

**Wandered off.** The user is 200 m from every stored point. Nothing today defines "off the track" — `DetectOffTrackUseCase` (`DetectOffTrackUseCase.kt`) is a different heuristic entirely: a window of three straight-line distances to the *start*, firing when the last exceeds the first by 25 m, i.e. "moving away from the origin", not "away from the path". Options the code makes equally easy: (a) add the straight-line hop from the fix to the mapped point and say nothing — short if there is a canyon between; (b) add the hop and flip to the degraded form once the hop exceeds a threshold (§3); (c) withhold. **Reported, not decided.** The hop threshold, if there is one, is the owner's.

**Gaps from filtered points.** This is where the merged timestamp filter reaches this feature. `excludeNetworkProviderFixes` drops every stored point whose timestamp has sub-second millis (`NetworkProviderFix.kt:41`); the survivors are what `Track.points` contains, and the cumulative sum along them **bridges each gap with a straight line**. A walk of 300 m around a bend recorded as ten points, seven of them excluded, sums as one or two chords — **shorter than the ground walked**. `Track.excludedPointCount` says how many went, not where (`Track.kt:46-47`); `isMostlyNetworkFixes()` fires at >75 % of ≥10 stored (`NetworkProviderFix.kt:61, :83-84`) and `hasNoUsablePoints()` at ≤1 survivor of ≥2 stored (`:66`). So the app can know the path is *probably* under-measured, and by roughly what fraction, but not by how many metres. **This is a short-direction error and it is systematic on any phone whose GPS fixes carry milliseconds** (the filter's own doc records that possibility, `NetworkProviderFix.kt:34-38`). Reported for §3 as a degrade trigger and for §5.

### 1.3 Cost, and whether it can be incremental

**Point counts** (sampler ceilings, `TrackRecordingMode.kt:27-29`; both thresholds required, so the interval bounds the rate): HIGH_ACCURACY 720/h, BALANCED 240/h, BATTERY_SAVER 60/h. An eight-hour day at HIGH_ACCURACY is at most 5 760 points; the pulse's four-hour figure of ~2 900 still holds. Real tracks filed in this repo are tiny (57 points across three exports).

**From scratch, per evaluation:** one nearest-point search is one `metersBetween` per point (a handful of trig calls each); one prefix sum is the same. At 5 760 points that is on the order of ten thousand haversines — **sub-millisecond to low-millisecond on any phone**, once per 15 s poll. Cost is not the constraint; the pulse's conclusion stands.

**Incremental is possible and cheap, because points only ever append.** The poll replaces `breadcrumbPoints` wholesale, but the new list is the old list plus a suffix (Room returns `ORDER BY timestampEpochMillis ASC`; the service only inserts). A prefix-sum array keyed on list size can be extended by the new tail in O(new points) and never recomputed. That is not needed for speed; it is worth having for **stability** — a prefix array that is extended, not rebuilt, cannot change the distance-to-point-k between polls, so the estimate can only move because the user moved. **Reported as an option; not required by cost.**

**What bounds currency, unchanged from the pulse:** the 15 s poll and the 20-point flush (up to ~100 s of HIGH_ACCURACY points not yet on disk). The live fix is fresher than the track; the hop from fix to mapped point (§1.1) is how the two meet.

### 1.4 The origin — what a track with none should do is the owner's

Two different things are called "origin":

- **`Track.originWaypointId`** (`Track.kt:43`): an explicit pointer to the waypoint the HUD created at record-start; `null` for every track from before the HUD and for any track whose origin creation failed. `GetTrackOriginWaypointUseCase` returns `null` for all three absence cases and never substitutes the first point (`GetTrackOriginWaypointUseCase.kt:12-15`). The HUD reads it as "No origin waypoint for this track" and shows no target (`NavigationHud.kt:345`).
- **`points.first()`**: the track's first surviving stored point. Every track with a usable point has one. `ComputeReturnToStartUseCase` uses it, and the HUD's own doc calls the origin waypoint "what the navigation HUD will call to find its target" — a target, not the path's end.

The path *along the track* ends at `points.first()` by construction; the waypoint is somewhere near it (seeded from the same fix stream — the first fix that passes the mode's accuracy gate, `TrackRecordingViewModel.kt:294-297`), or not near it at all if that fix was a network fix (queued separately, out of scope). So the question "what does this do with no origin" splits: **the path length needs no waypoint**; **the last hop from `points.first()` to the waypoint** is a straight line that exists only when the waypoint does. Options: end at the first point and say nothing; end at the first point and add the hop when there is a waypoint; withhold without a waypoint (the HUD's current stance for its target). **Reported, not decided.** Note that a track whose every point was filtered has no first point either (`hasNoUsablePoints`), which is a §3 condition, not an origin question.

### 1.5 Elevation — what the data supports

- `TrackPoint.altitude: Double?` is stored per point when the fix reported one (`TrackPoint.kt`); `LocationFix.Update.altitude` likewise (`LocationTracker.kt:42`). The live fix carries it and the HUD shows it in metres.
- `ComputeTrackStatisticsUseCase` already computes hysteresis-filtered gain and loss (`ElevationHysteresis.THRESHOLD_METERS = 4.0`, `:109-110`) with the doc's stated reason: consumer GPS altitude noise is ±10–15 m and a phone sitting still would otherwise bank hundreds of fictional metres. It also reports `elevationCoverage`, the fraction of points with an altitude, because a gap resets the reference rather than bridging it.
- So **the data supports**: the net difference between here and the origin (already computed straight-line, `ComputeReturnToStartUseCase.kt:15-16`), and a cumulative *loss on the way out* along the return segment — which is the *gain on the way back* — by running the same hysteresis pass over the mapped suffix in reverse. Both are only as good as coverage, and coverage on a real track is unknown here.
- **What a correction would look like, report only:** the customary form is Naismith's rule — add an hour per 600 m of ascent (a fixed constant, not from this codebase) — applied to the return's ascent. It needs the reversed hysteresis pass above and a decision on what to do at low coverage. **Not built, per the dispatch.** Flagged in §5 as one of the four short-direction errors: a pace measured on a descending outbound leg, applied to an ascending return, is short by exactly the amount a correction would add.

---

## 2. Item 2 — pace, modelled honestly

### 2.1 The instrument question, first

**The platform surface, verified against the installed SDK** (`javap` on `/opt/android-sdk/platforms/android-37.1/android.jar`, `android.location.Location`):

```
public boolean hasSpeed();
public float getSpeed();
public boolean hasSpeedAccuracy();
public float getSpeedAccuracyMetersPerSecond();
public float getBearing();  public float getBearingAccuracyDegrees();
```

`hasSpeedAccuracy`/`getSpeedAccuracyMetersPerSecond` were added at API 26, which is this app's `minSdk = 26` (`app/build.gradle.kts:122`) — so **both speed and its accuracy are available on every device this app runs on**, with no version branch.

**What the app does with them: nothing.** `AndroidLocationTracker.toFix()` maps five fields — `latitude, longitude, altitude?, accuracy?, time` — and drops `speed`, `speedAccuracy`, `bearing` and `provider` on the floor (`AndroidLocationTracker.kt:68-74`). `LocationFix.Update` has the same five fields (`LocationTracker.kt:39-45`), as does `TrackPoint`. `grep -rn "\.speed\|hasSpeed\|speedAccuracy" app/src/main`: no hits. The pulse's finding holds.

**Does the platform populate it?** From the SDK and the code: `hasSpeed()` is the platform's own statement that the value is meaningful for that fix; the GNSS provider supplies a Doppler-derived speed with essentially every fix on a receiver that has one, and the network provider supplies none. **That last clause is worth more than it looks**: on this app's tester phone the network provider is the source of the sub-second-stamped fixes the timestamp filter exists to exclude. `hasSpeed() == false` on a fix is a second, independent tell for "not a GNSS fix", and `Location.getProvider()` — also discarded today — is the direct one. **Only a device answers whether this phone's GNSS fixes carry `hasSpeed() == true` and what the accuracy reads at walking pace**; the tracker registers both providers (`AndroidLocationTracker.kt:53-56`), so a device log of `(provider, hasSpeed, speed, speedAccuracy)` per fix is the whole experiment, and it is one walk.

**Does it settle "moving"?** Largely, yes. A fix whose reported speed is below a floor is the receiver saying the phone was not moving *at that instant*, independent of position error — the property the dispatch names. Two things it does not settle on its own: Doppler speed at walking pace is noisy (an accuracy of a few tenths of a metre per second against a walking speed of one to two), so a single fix's speed is not a pace, only its average over accepted points is; and it is per-fix, so the sampler's own behaviour matters — **the sampler already writes no points while the phone is still**, so the stored points are moving points by construction, and a per-point speed averaged over them is a moving average without any pair-exclusion logic at all. The one case that needs care is the first accepted point after a stop, whose Doppler speed may be a slow start; a floor handles it.

**Is accuracy reported for speed, and should it gate?** Yes, `getSpeedAccuracyMetersPerSecond()` (one-sigma, per the platform's contract), present on every supported API level. A gate analogous to the 50 m rule is available: discard a speed sample whose accuracy exceeds some fraction of the speed itself, or an absolute ceiling. The threshold is the owner's; the shape (gate at the seam, `null` passes as "not reported") is `acceptLiveFix`'s (`LiveFixGate.kt:54-57`).

**What persisting it costs:**

| Touch | What | Cost |
|---|---|---|
| `LocationFix.Update`, `TrackPoint` | `speedMetersPerSecond: Float?`, `speedAccuracyMetersPerSecond: Float?`, defaulted `null` so no existing constructor site changes (10 in `app/src/main`, 38 in `app/src/test`) | small |
| `AndroidLocationTracker.toFix()` | read `hasSpeed()/speed`, `hasSpeedAccuracy()/speedAccuracy` | trivial |
| `TrackPointEntity` + `Migrations.kt` | two nullable `REAL` columns, `ALTER TABLE track_points ADD COLUMN …`, **`ForagerDatabase.version = 14 → 15`** (`ForagerDatabase.kt:153`) with a migration test in the existing shape | the version bump is the part CLAUDE.md's collision pitfall is about: confirm 14 is still current on `main` at build time |
| `RoomTrackRepository` | two mapping functions (`:80-100`) | trivial |
| `TrackGpxExporter` | GPX 1.1 has no `<speed>` in the core `trkpt` schema; leaving it out is correct, adding it means an extension namespace | none unless wanted |
| Readers | the pace computation | the point of it |

**Not a dormant column**: the pace is its reader, in the same change. The provider name is a third column the same reasoning would justify (the filter dispatch recorded it as the discriminator it could not have), but it is not this dispatch's to add.

**Proposal, and stop.** If a device walk shows `hasSpeed() == true` with an accuracy under about half a metre per second on GNSS fixes, **build the pace on persisted Doppler speed**, averaged over accepted points, gated on its own accuracy, with the point-differencing method of §2.4 as the fallback when speed is absent (`hasSpeed() == false` on a stored point, or every point of an older track). That is a better design than the one the dispatch describes, for the reason the dispatch gives: it does not inherit position error. It costs one migration, and the owner said they would authorise that as part of this work. **This report stops here on the instrument question**, per the dispatch. If the walk shows speed absent or its accuracy worse than the walking speed itself, §2.4 is the design.

### 2.2 What is derivable from stored points, precisely

| Quantity | Derivable? | How, and the catch |
|---|---|---|
| Distance between consecutive stored points | yes | `metersBetween`; the sampler guarantees ≥ the mode's minimum distance |
| Time between consecutive stored points | yes | timestamps; ≥ the mode's minimum interval |
| Implied speed per pair | yes | distance ÷ time; **includes any stop inside the pair** |
| Moving pace | **yes, by exclusion** | average over pairs whose implied speed is above a floor (§2.4); or distance-weighted, which the dispatch's "distance ÷ moving speed" wants |
| Stop count | approximately | pairs whose time exceeds the interval by a margin; **cannot be told from a GPS outage or a filtered run** (§2.6) |
| Stop duration | approximately | the excess time in those pairs; same caveat |
| Instantaneous speed | **no** | not stored (§2.1) |
| Whether a given point was recorded while moving | by construction, mostly | the sampler's distance threshold means a point is written only after ≥ N m of displacement; the first point after a stop is the exception |

### 2.3 Speed is not persisted today; the cost is in §2.1

Deferred to the instrument section, as the dispatch directs.

### 2.4 "Moving" from stored points — what the data supports, and the threshold it needs

**The arithmetic the sampler fixes.** In HIGH_ACCURACY a pair of consecutive stored points is ≥ 5 s apart and ≥ 5 m apart; while a person walks continuously at 1–1.5 m/s the sampler accepts a point about every 5 s, so a walking pair's implied speed is around 1 m/s and cannot be below 5 m ÷ (time). A pair that spans a stop has the same few metres over the stop's duration: a 30 s pause gives ~0.15 m/s, a two-minute photograph ~0.04 m/s, a ten-minute one ~0.008 m/s. In BALANCED (15 s / 15 m) the walking pair is again ~1 m/s and a stop pair is 15 m over the stop. **So a floor anywhere in the band between roughly 0.3 m/s (a slow shuffle, 1 km/h) and 0.7 m/s cleanly separates every stop longer than about half a minute from walking, in every mode.** Shorter pauses — a ten-second look at a mushroom — blur into the walking pair and lower its speed slightly, which is the safe direction.

**The threshold is the owner's.** What the data says: the floor should sit below the slowest walking pace the app should still call "moving" over rough ground (a person picking their way at 0.5 m/s is moving), and above the implied speed of the shortest stop that should be excluded. Those two bound it; a value near 0.4–0.5 m/s satisfies both in every mode, and is offered as a starting point, not a decision. **Worked by hand, for the record**: 5 m in 5 s = 1.0 m/s (walking); 5 m in 12 s = 0.42 m/s (very slow); 5 m in 60 s = 0.083 m/s (one-minute stop); 15 m in 15 s = 1.0; 15 m in 300 s = 0.05.

**Distance-weighted, not pair-averaged.** "Distance left ÷ average moving speed" wants total moving distance ÷ total moving time over the kept pairs, not the mean of per-pair speeds; the latter over-weights short slow pairs. Stated so a test's expected value is worked the right way.

### 2.5 The starting default, and "enough"

**The default is decided**: 3.2 km/h, stored metric, with the imperial source and the safety reasoning at the constant. Nothing to report except where: a domain constant beside the estimator, the same shape `LIVE_FIX_MAX_ACCURACY_METERS` and `ElevationHysteresis.THRESHOLD_METERS` take.

**"Enough" to replace it — options, not a decision:**

- **A minimum moving distance** (e.g. 200 m of kept-pair distance): the measured speed then rests on at least that much walking. At 1 m/s that is a little over three minutes of walking, whatever the mode.
- **A minimum number of kept pairs** (e.g. 20): mode-dependent in time (100 s in HIGH_ACCURACY, five minutes in BALANCED, twenty in BATTERY_SAVER).
- **Both**, whichever is later.

Distance is the better-behaved of the two because it means the same thing in every mode. **The dispatch's own sentence is the test**: "a confident number from three minutes of walking would be a lie" — so the bar should be set where the owner would call the number honest, and §3's degraded form covers everything before it.

**Both sides must be asserted** (the dispatch's verification list): the default applies below the bar and stops applying above it. Both are one pinned-input test each.

### 2.6 What the timestamp filter does to this

- **Gaps.** A run of excluded points between two survivors is a time gap that looks like a stop. Excluding the pair by the §2.4 floor drops it from the average — which is *correct for speed* (no walking data was lost, only the pair) and *wrong for stop counting* (it was not a stop). The estimate the owner ruled on needs only speed, so the filter does not distort it; a stop count, if anyone later wants one, is distorted.
- **Shortened distance.** §1.2: the path is under-measured across a gap. That is a distance error, not a pace error, and it is short.
- **A track that is mostly excluded** (`isMostlyNetworkFixes`) or has no usable points (`hasNoUsablePoints`) has too little to measure from: a §3 condition, with the existing predicates as the trigger.

### 2.7 At the start of a trip

Before the bar in §2.5: the default. At the bar: a speed from a few minutes of walking, which is a small sample from the flattest, freshest part of the day. **That number is honest about what it measured and dishonest about the day** — the walk out is not the walk back. §3 is where this goes: the estimate should read "at least" until the sample is large enough that the owner would stand behind it, and the owner sets that.

---

## 3. Item 3 — degrade honestly: the conditions, for the owner to threshold

The precedents this follows, quoted so the shape is fixed before thresholds are picked:

- **The accuracy-aware distance:** `formatDistanceWithAccuracy` prints `"within 16 ft"` inside the error circle and `"≈ 10 m"` when rounding to a step no finer than the accuracy (`DistanceUnit.kt`, doc: "the number was not wrong, its resolution was a lie").
- **The held fix ageing:** `FixFreshness.STALE` → "Last fix 45 s ago", `LOST` → "No fix for 5 min" (`NavigationHud.kt:390-391`), with the refusal recorded at `LiveFixGate.kt:40-46` ("trades an honest silence for a confident lie").
- **The suppressed needle:** "Compass unreliable" with the needle withheld (`NavigationHud.kt:332`), entered and left across a band so it does not flicker (`CompassTrustJudge.kt:19`).

**Conditions that should trigger "at least X"**, each with what the code already knows:

| Condition | Signal available today | Notes |
|---|---|---|
| Too little moving data for a measured pace | the §2.5 bar not yet met | the estimate uses the default; it is "at least" because the default is deliberately slow *and* because nothing has been measured |
| The pace sample is small but past the bar | kept-pair distance between the bar and some larger figure | a second, looser threshold, or none; owner's |
| Stale or lost fix | `FixFreshness.STALE` / `LOST` (30 s / 5 min today) | the hop from the fix to the track is unknown; the track length is not |
| Far off the track | the §1.1 hop exceeds a threshold (relative to accuracy, or absolute) | no threshold exists; the hop itself is computable |
| Path under-measured by the filter | `excludedPointCount > 0`; `isMostlyNetworkFixes()`; `hasNoUsablePoints()` | the first says "some", the second "most", the third "nothing" |
| Few surviving points at all | `points.size` below some floor | distinct from the pace bar: a track can be long and sparse in BATTERY_SAVER |
| No origin waypoint | `originWaypointId == null` | only matters if the last hop is included (§1.4) |
| Low altitude coverage | `elevationCoverage` | only matters once an elevation correction exists; report only now |

**A band, not a line, for anything the estimate can cross back and forth** — the compass precedent. A user hovering at the off-track threshold should not see the label flicker.

**Stopped here.** Every threshold is the owner's. The wording is not this dispatch's either (no UI), but "at least" as the form is decided.

---

## 4. Item 4 — rainfall in millimetres, and what else was missed

### 4.1 Every rainfall display, and no shared formatter

All seven format inline, each with its own `String.format`; none shares anything:

| Site | Text | Precision |
|---|---|---|
| `AvailabilityResultsUi.kt:460` | `"${"%.1f".format(totalMm)}mm of rain in the last 14 days"` | 0.1 mm |
| `:491` | `"${"%.1f".format(todaysForecast.precipitationMm)}mm of rain forecast today."` | 0.1 mm |
| `:563` | `"${"%.0f".format(mostRecentRain.totalMm)}mm of rain ending "` … | 1 mm |
| `:570` | `"${"%.1f".format(window.precipitationDuringWindowMm)}mm more rain forecast during the window"` | 0.1 mm |
| `:585` | `"${"%.1f".format(et0)}mm evapotranspiration since the rain"` | 0.1 mm — evapotranspiration, the same unit |
| `AvailabilityPureFunctions.kt:266` | `"${"%.0f".format(reason.requiredTotalMm)}mm this search treats as a soaking event — the "` | 1 mm |
| `:267` | `"wettest run reached ${"%.0f".format(reason.largestRunTotalMm)}mm."` | 1 mm |

No `mm` string anywhere else in `ui/`. (The grep was over `\bmm\b`, `millimet`, `rainfall`, `precipitation`.)

### 4.2 What unit the data is in, and who else reads it

**Millimetres, from Open-Meteo's defaults.** Neither API declaration passes a unit parameter (`OpenMeteoApi.kt:30-38` sends `latitude, longitude, daily, hourly, past_days, forecast_days, timezone`; `OpenMeteoArchiveApi.kt:23-26` likewise) — Open-Meteo's documented defaults are `precipitation_unit=mm`, `temperature_unit=celsius`, `wind_speed_unit=kmh` (general knowledge of that API, not from this repo). The models carry it as mm by name: `ConditionsSummary.totalPrecipitationMm`, `TripWindow.totalMm`, `precipitationDuringWindowMm`, `evapotranspirationSinceRainMm`, `DailyWeather.precipitationMm`.

**Other consumers, all in millimetres and all correct as they are:** `FruitingPatternAssumptions.RAIN_DAY_MIN_MM = 2.0` and `SOAKING_EVENT_MIN_TOTAL_MM = 10.0` (`FruitingPatternAssumptions.kt:30, :41`), `OpenMeteoWeatherProvider.SIGNIFICANT_RAIN_MM` (aliased to the first, `:86`), the rain-lag and trip-window logic that compares against them. **These must not change**: they are domain thresholds, and the dispatch's fix is at the display site only, following `DistanceUnit`'s own rule ("Distance *values* stay kilometers everywhere else in this app, always … only the label a user reads converts", `DistanceUnit.kt` doc).

### 4.3 Precision in inches — a proposal, and stop

Millimetres carry one useful digit past the point at these magnitudes; inches carry two. 1 mm = 0.039 in, so:

- **Tenths of an inch** for anything at or above 0.05 in (≈1.3 mm): 12 mm → "0.5 in"; 38 mm → "1.5 in"; 2 mm → "0.1 in".
- **Below that, a trace floor, not "0.0 in"**: "< 0.1 in" — a printed zero would say "no rain" about a day the domain logic may count as a rain day (the 2 mm threshold is 0.08 in, which rounds to 0.1, so the two agree at the boundary, but 1 mm does not).
- The 1 mm sites (`:563`, `:266-267`) become tenths too; "12mm" and "0.5 in" carry the same information.
- The spacing follows `formatDistanceMeters`'s convention (a space before the unit: "0.5 in"), which the current "12mm" does not — a chance to align, flagged, not decided.

**Proposed; the owner decides.** A `formatRainfallMm(mm: Double, unit: DistanceUnit): String` beside `formatDistanceMeters`, reading the same preference, is the shape; whether `DistanceUnit` is the right preference to read is §4.4's question.

### 4.4 The finding that matters most: there is no single place that answers "which units does this person read"

`DistanceUnit` is the only unit preference in the app (`DistanceUnitPreferenceRepository`, DataStore-backed). It is read by exactly the distance displays and the radius default (`formatDistanceKm`, `formatDistanceMeters`, `formatDistanceWithAccuracy`, `defaultOfflineMapRadiusKm`). Everything else with a unit is fixed metric, found by grepping `ui/` for `" m"`, `°C`, `mm`, `km/h`, `mph`:

| Quantity | Where | Shown as | US reader expects |
|---|---|---|---|
| Rainfall, evapotranspiration | the seven sites in §4.1 | mm | inches |
| Soil temperature | `AvailabilityResultsUi.kt:581`, `"Soil temperature: ${"%.1f".format(temp)}°C"` | °C | °F |
| Elevation (live fix) | `NavigationHud.kt:338`, `"${it.roundToInt()} m"`; compass strip `AvailabilityScreen.kt:4293` | metres | feet |
| Elevation difference to start | `AvailabilityScreen.kt:4327`, `"+${it.roundToInt()} m"` | metres | feet |
| Wind | nothing displays it | — | — |
| Area | nothing displays it | — | — |
| Track distance in Records/Cartography | `CartographyEntryEditScreen.kt:648-649` via `formatDistanceKm` | per unit ✓ | ✓ |
| Coordinates | MGRS, unitless | ✓ | ✓ |

So four quantities miss, in three units (length below a kilometre, precipitation depth, temperature), and the app's one preference is named for distance. **The structural finding:** the preference is doing the job of a units *system* under the name of one dimension. Each new display re-decides, and three of them decided "metric" by not deciding. Two shapes are possible and both are the owner's: widen `DistanceUnit`'s meaning to "imperial vs metric" (it already picks the radius default, which is not a distance display), or introduce a `UnitSystem` preference that `DistanceUnit` derives from. Either way the elevation sites are the same defect as rainfall and could take the same fix; **not fixed here**, per the dispatch.

---

## 5. Where the estimate can be confidently short — said loudly

1. **Nearest-point snapping on switchbacks, loops and ravines** (§1.1). The estimate skips every leg between the snapped point and the true position on the path. Bounded only by the track's geometry; on a 200 m switchback stack it is 200 m per leg skipped. **Avoidable by mapping choice.**
2. **The timestamp filter's gaps** (§1.2, §2.6). The recorded path is chords across excluded runs; the walk was the arc. On a phone whose GNSS fixes carry milliseconds, most of the track is excluded and the path collapses toward a straight line — `isMostlyNetworkFixes` will say so, and the estimate must degrade when it does. **Detectable, not correctable.**
3. **Pace from the outbound leg applied to the return** (§1.5, §2.7). Downhill out, uphill back: the measured moving speed is faster than the return will be, by the elevation's worth. **Correctable later; must be labelled now.**
4. **A measured speed from too little walking** (§2.5). Three minutes on a flat trail can read 1.4 m/s; the day averages less. **The default and the "at least" form exist for this; the bar decides when they stop applying.**

And one that is *long*, listed so it is not mistaken for a fifth: the most-recent-point mapping after a double-back overstates by the doubled leg. Safe.

---

## Test and skip counts

Not run for this report — nothing changed. `main` at `bc72811` is PR #75's head plus the merge commit; that head's CI run (`Build, test, publish APK`, success) and the pre-merge local run both report **159 suites, 1224 tests, 0 failures, 0 errors, 24 skipped**, the 24 being the CI allowlist's identity set. That matches the dispatch's baseline exactly (the plate pulse's 1201 baseline was `main` before #75).

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed from the code on `bc72811`:** the five-field fix and point types and the tracker's mapping that discards speed, bearing and provider; the sampler's two-threshold rule; the statistics pass and its hysteresis; the straight-line return use case; the origin use case's three `null` cases; the 15 s poll and 20-point flush; the read seam's exclusion and its three predicates and thresholds; the off-track window's shape; `GeoDistance`'s function set; the schema version and the point entity's columns; the constructor-site counts; the seven rainfall sites, the soil-temperature site and the three elevation sites with their exact strings; the API declarations' lack of unit parameters; the domain rain thresholds; the HUD's freshness strings and the compass band. **Confirmed from the installed SDK by `javap`:** `Location.hasSpeed/getSpeed/hasSpeedAccuracy/getSpeedAccuracyMetersPerSecond/getBearing`. **Confirmed from git:** `main`'s SHA after the merge.

**Inferred:** that GNSS fixes on this app's target devices report `hasSpeed() == true` and the network provider does not (platform behaviour; device only — **confirmed on the owner's device, 344/344, by the walk: see the addendum below**; still one device); Doppler speed accuracy at walking pace (**measured by the walk: 0.72 m/s on moving fixes**); Open-Meteo's default units (its documentation, not this repo); the implied-speed arithmetic in §2.4 (worked by hand from the mode constants); Naismith's rule as the customary elevation correction; haversine cost per evaluation (order-of-magnitude reasoning, not measured).

### What I could not determine

- Whether this phone's fixes carry a usable Doppler speed — the one device question that decides §2.1's proposal. One walk with a per-fix log answers it. **Answered by the walk (addendum below): populated on every GPS fix; per-fix accuracy 0.72 m/s, above the ~0.5 m/s bar §2.1 named — see the addendum for what that leaves open.**
- Real altitude coverage on real tracks, which decides whether an elevation correction is ever worth building.
- The geometry of the owner's actual trails (switchbacks or not), which decides how much §5's first error matters in practice.
- Whether the sampler's first-point-after-a-stop carries a slow Doppler speed often enough to need its own handling.

### Premises in this dispatch that were wrong

- **"Track points are already in memory during recording, because the ViewModel re-reads the whole track every 15 seconds. Cost is poll lag and a 20-point flush buffer, not computation."** Still true.
- **"The sampler writes no points while stationary."** Still true, and it is the property §2.4 rests on.
- **"Pace: nothing exists. No point type carries speed; the tracker discards `Location.speed`."** Still true.
- **None found wrong.** The pulse's ground holds; the one addition is the read seam, which the dispatch itself anticipated.
- One clarification: the dispatch says "1224 tests, 24 skipped — confirm against current `main`". That was this branch's count when the dispatch was written and is `main`'s count now that #75 is merged.

### Anything I decided that this dispatch did not cover

- **Branching.** The dispatch said branch off `main`; this session is pinned to `claude/new-session-b7z9bg`. Because #75 merged that branch, restarting it from the new `main` satisfies both; done, and said at the top.
- **Reading `AvailabilityScreen.kt`** at the three elevation-display lines and the compass strip only, to answer Item 4's search; seams F and G were not touched or characterised.
- **Running no tests**, since nothing changed; the baseline is cited from the merged head's CI and the pre-merge local run rather than re-run here.
- **§2.4's suggested floor band and §4.3's precision** are offered as starting points with the arithmetic shown, explicitly not as decisions.

---

## Addendum: owner rulings, and the three numbers proposed in return

Recorded the same day, after the owner read the report. Rulings are the owner's; the proposals are this session's, with the reasoning, and stop where the owner's decision is still needed.

### Rulings

1. **Position-to-track mapping: most-recent-point.** "Its error is never short, and that's the only property that matters in a safety input." To be recorded at the site, because nearest-point will look like an obvious improvement to someone later.
2. **The off-track hop: straight line, and it triggers the degraded form beyond a threshold** (proposed below). The only thing available, and short by nature; anything past the threshold reads "at least X".
3. **The origin's last hop: include it** when an origin waypoint exists and differs from the first surviving point. Omitting it is short.
4. **Moving floor: 0.5 m/s**, the middle of the 0.3–0.7 m/s band §2.4 established, with the band itself recorded at the constant so a later tuner knows the bounds. **The constant's comment must also carry (Action 5 of the walk findings, `2026-09-07-fix-log-walk-findings.md`):** on the owner's walk of 2026-09-07 the floor was insensitive across the whole band — 0.3, 0.5 and 0.7 m/s kept 217, 211 and 194 of 289 GPS fixes with the kept average moving by 0.01 m/s per step — because the speed histogram is bimodal (a cluster at 0.0, a cluster around 1.0, the floor in the trough); **1.0 m/s is the boundary of the safe range**: it kept 40 of 289, discarding 86 % of a real walk. One walk, one walker.
5. **"Enough": in accumulated moving time**, not point count. Owner's instinct five minutes; the number and reasoning proposed below.
6. **Degrade thresholds:** proposed below.
7. **Inch precision: tenths with a trace floor**, as proposed. Built (completion report).
8. **Units system: introduce the preference and derive distance from it.** Widening the distance preference's meaning would keep a wrong name on a growing responsibility, and this is the third miss. Built here; rainfall converted; soil temperature and elevation wait on it, reported (completion report).
9. **Speed columns: authorised, conditional on the walk.** The per-fix log first (built, completion report). If Doppler speed is populated, build on it; the `hasSpeed() == false` tell for network fixes is worth recording either way. **Condition met (addendum below): populated on 289/289 GPS fixes, `hasSpeed=false` on 55/55 network fixes. Authorised, with one number the addendum puts to the owner before the build — ruled the same day: the averaged reading governs, the per-fix bar is retired (addendum).**
10. **Naismith stays reported.** The alert's margin absorbs terrain; a correction now would double-count.

### Proposal 1 — the off-track hop threshold

**Enter the degraded form when the hop exceeds 50 m; leave it below 25 m.**

- **Why 50 m:** it is the live-fix gate's own ceiling (`LIVE_FIX_MAX_ACCURACY_METERS = 50f`). A hop shorter than the worst position error the app will display cannot be told from that error, so it must not change the label; a hop longer than it cannot be explained by error and is a real departure from the path. It is also at most one accuracy circle in every recording mode (30/50/100 m), so a user walking the track in BATTERY_SAVER does not trip it on jitter alone.
- **Why a band, and why 25 m:** the compass-reliability precedent (`CompassTrustJudge`: enter above one figure, leave below a lower one) — a user hovering near the threshold must not see the label flicker between "≈" and "at least" on successive fixes. Half the entry figure is a wide enough gap to cover two fixes' worth of jitter at the gate's ceiling.
- **Worked cases:** a user 20 m off the track under 12 m accuracy — plain figure, the hop is within noise; 80 m off — "at least", and the estimate includes the 80 m; back to 40 m — still "at least" (inside the band); 20 m — plain again.
- **What it does not do:** distinguish 80 m across a meadow from 80 m across a ravine. Nothing can. The label is the honesty; the hop is added either way.

### Proposal 2 — "enough" moving time to replace the default: **five minutes**

The owner's instinct survives the data. The reasoning, per recording mode, from the sampler's own constants:

| Mode | Interval | Kept pairs in 5 min of walking (≈ 300 m at 1 m/s) | What the measured speed rests on |
|---|---|---|---|
| HIGH_ACCURACY | 5 s | ~60 | sixty independent steps; a per-step position error of ~5–10 m over 5 m steps is large, but sixty of them average to a few percent |
| BALANCED | 15 s | ~20 | twenty steps of ~15 m; a few percent again |
| BATTERY_SAVER | 60 s | ~5 | five steps of ~60 m; the thinnest, but each step is long enough that position error is a small fraction of it |

Three further reasons five minutes is the right order and not, say, one or fifteen:

- **Below it the default is doing the honest job.** 3.2 km/h is deliberately slow; during the first five minutes nobody is near a turnaround, so the cost of the default being slow is nil and the cost of a bad early measurement is a wrong number the user might act on.
- **At the switch the estimate can move, and five minutes bounds the move.** A typical measured walking speed on a trail is 3.6–5 km/h (general knowledge, not from this codebase); replacing 3.2 km/h with 4.5 km/h shortens the estimate by ~30 % in one step. That is a visible jump and, because the measured figure is faster, it is a jump in the short direction — which is why the switch must not happen on thin data, and why §3's "at least" should persist for a while past it (Proposal 3).
- **With Doppler speed, five minutes is generous.** Sixty samples at ~0.3 m/s accuracy give a standard error under 0.05 m/s; one minute would already do. Five minutes is chosen for the point-differencing fallback and for BATTERY_SAVER, where it is the minimum that gives five kept pairs. One bar for every mode and both instruments, rather than a table the user cannot see.

**The default-speed constant's comment must carry (Action 4 of the walk findings):** on the owner's walk of 2026-09-07 the average Doppler speed of the 211 GPS fixes at or above the 0.5 m/s floor was **0.890 m/s = 1.99 mph**, against the 2 mph default argued from foraging behaviour before any data existed. One walk, one walker — an agreement, not a calibration. The same walk turned 4.8 min of wall clock into 3.5 min of moving time, so at that stop ratio the five-minute bar needs about seven minutes of walking and a real trip, with a forager stopping at finds, longer — the default will be the figure in use far more often than the bar suggests, which is why this measurement matters.

**A note the build must carry:** GPS jitter inflates a differenced path — a stationary receiver "walks" a few metres between fixes, and a moving one records a slightly longer path than the ground walked. That inflates measured speed (short direction) *and* the remaining path length (long direction), and the two partly cancel in distance ÷ speed. Doppler speed does not inflate, so with it the remaining path's inflation is uncancelled and the estimate leans long. Both are safe or neutral; recorded so nobody "corrects" one without the other.

### Proposal 3 — degrade thresholds, per condition

The form is decided ("at least X"). The proposed triggers, each with its signal and the reasoning:

| Condition | Trigger | Reasoning |
|---|---|---|
| No measured pace yet | moving time < 5 min (Proposal 2) | the figure is from the default, which is slow on purpose but unmeasured |
| Pace freshly measured | moving time between 5 and **15 min** | the switch just moved the estimate, possibly by ~30 % in the short direction, on the day's flattest early leg; three times the bar is enough walking to include some variation of ground |
| Stale fix | `FixFreshness.STALE` (≥ 30 s, existing) | the hop from fix to track is unknown; the track length is not |
| Lost fix | `FixFreshness.LOST` (≥ 5 min, existing) | **withhold the estimate entirely**, matching the HUD, which withholds the distance — a number with no position under it is not "at least", it is a guess |
| Off the track | hop > 50 m, band to 25 m (Proposal 1) | the hop is a straight line and short by nature |
| Path under-measured by the filter | `excludedPointCount ≥ 10 %` of stored points | one excluded point among hundreds is one chord; a tenth of the track excluded is a bend flattened somewhere. The fraction is the tunable; the simpler alternative (any exclusion → "at least") is stricter and also defensible |
| Mostly excluded | `isMostlyNetworkFixes()` (> 75 % of ≥ 10) | already computed; the track is chords |
| No usable points | `hasNoUsablePoints()` | **withhold**: there is no path to measure |
| Few surviving points | fewer than **10** stored survivors | the same floor `MOSTLY_NETWORK_FIXES_MIN_STORED_POINTS` uses; below it the "path" is a handful of chords whatever the mode |
| No origin waypoint | — | **no degrade**: the path ends at the first point and nothing is omitted; the last hop exists only when the waypoint does |

**Two things this table does not do, on purpose:** it does not stack — one trigger is enough, and the label is the same; and it does not touch the walking-time-not-arrival-time distinction, which is a labelling rule the surface must carry and this input must record at the code, per the ruling.

**Stopped here.** The three proposals are the owner's to accept or move.

### Owner acceptance (same day)

All three proposals accepted as stated: the 50 m / 25 m off-track band, five minutes of accumulated moving time, and the per-condition degrade table including the two withhold cases. The DataStore fallback reading the legacy key is confirmed as the right call — silent continuity for a tester who chose kilometres, and the same file means no migration to get wrong. The edit to one function inside `AvailabilityScreen.kt` is fine: seams F and G are held because moving the HUD before stage two would mean moving it twice, and that reasoning does not extend to a units control; the callback rename stays the split's job.

**On the revert check's finding:** the owner ranks it above the build — a test that passed with the fallback removed because imperial is also the default was asserting a coincidence, the exact pattern CLAUDE.md warns about, caught here by reading the runner's count against the prediction rather than by the runner. **It is the second time this project has found a test that could not distinguish two mechanisms** (the first: the compass-reliability revert whose "failures" belonged to a different edit — CLAUDE.md, Testing). Asserting the migration log line is the accepted fix.

**Next:** the instrument walk, `adb logcat -s ForagerFix`, on the owner's device. Two things decide the speed columns: whether GNSS fixes carry `hasSpeed=true` with accuracy under about half a metre per second, and whether `hasSpeed=false` lines up with `provider=network`. The second is worth having regardless, as an independent confirmation of the timestamp rule from a different signal.

### Walk result (same day, filed on `8eacc91`)

The instrument walk was done on the owner's device with the per-fix log from PR #76; the planner's
analysis and the coder's reproduction of every figure from the raw log are in
`2026-09-07-fix-log-walk-findings.md`. What it settles for this report:

- **The provider/`hasSpeed` correlation is confirmed, not inferred: 344/344 unique fixes, gps→`true`
  289, network→`false` 55, zero exceptions.** The "Inferred" line above and the "could not
  determine" bullet are amended in place to say so. The condition on ruling #9 is met and the
  instruction to wait on the walk is withdrawn. **What is kept:** this is one device. It is not
  established across hardware, and the beta is the place that settles that — the same standing the
  timestamp discriminator has, which this walk confirmed again, independently, on the same 344
  fixes (`hasSpeed` and `time % 1000 == 0` never disagree).
- **The one thing the walk did not settle as §2.1 framed it:** §2.1's bar was an accuracy "under
  about half a metre per second"; the device reports 0.72 m/s on every moving fix, 0.07 on nearly
  every stopped one. Averaged over the five-minute bar that is a standard error near 0.05 m/s, which
  is what Proposal 2 wanted; per fix it is useless. Whether the averaged reading is enough to build
  the pace on Doppler speed, as proposed, is put to the owner in the findings file's coder's note
  and is the first question of the Items 1–3 dispatch, not decided here. **Ruled the same day
  (owner): the averaged reading governs — not because √N buys 0.05 m/s (consecutive Doppler fixes
  share receiver, geometry and multipath, so their errors are correlated and 0.05 is a floor, not
  an estimate) but because the model never consumes a per-fix speed, only one average over five
  minutes of moving time; a bar written for per-fix use was aimed at something the design does not
  do, so §2.1's bar is retired rather than recorded as failed. For the build: no confidence
  interval from `speedAccuracy`, which the walk shows to be a two-state flag (0.72 moving, 0.01–0.07
  stopped) and not a per-fix measurement; validate the average against point differencing over the
  same window instead, since the differencing path exists anyway as the fallback. Full text in the
  findings file's coder's note.**
- **A finding the dispatch did not ask for and that outranks the one it did:** GPS horizontal
  accuracy on this device is a constant, `3.7900925`, on all 289 GPS fixes. The live-fix gate and
  the HUD's honest-precision formatter both read a field with no signal in it on this device; the
  notes are on `acceptLiveFix` and `formatDistanceWithAccuracy`, and the beta trip report now asks
  the one question a tester can answer about it. Nothing in this report's proposals reads the
  accuracy field except Proposal 1's *reasoning* for 50 m, which stands on the gate's ceiling as a
  number, not on any fix's reported accuracy.
- **Unchanged by the walk, per the findings' Action 6:** the 0.5 m/s floor, the five-minute bar, and
  the no-stop-allowance reasoning (the walk broke into 18 runs, the longest stop 28 s — stopping is
  the normal conduct the ruling assumed). The two measurements the constants' comments must carry
  are recorded above on ruling #4 and Proposal 2, since neither constant exists in code yet.

