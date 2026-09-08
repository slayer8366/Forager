# Pre-build report: Pass 2 instrumentation — the speed comparison log line

**Type:** report-before-building. **Nothing built: no product code, no test, no schema change.**
**Date:** 2026-09-08. **Dispatch:** "Pass 2 instrumentation: speed comparison log line" (owner upload, 2026-09-07).

**Where the code under report lives, and where this file lives — read first.** Every file this
report quotes exists only on `claude/new-session-b7z9bg` at `c914a30` — PR #77's head, open
against `main`. `main` (`8eacc91`) has no `MovingPace.kt`, no `ReturnWalkingTime.kt`, no speed
columns, and `ForagerDatabase.version = 14`; a grep of `main` for `speedMetersPerSecond` finds only
the `ForagerFix` log line in `AndroidLocationTracker`. This session's pinned branch,
`claude/new-session-3x1aba`, was created from `main`, so this report is committed on a branch that
carries none of the code it describes. That is fine for a report. **It is not fine for the build:
the instrumentation branch the dispatch asks for has to be cut from `c914a30`, not from `main`,
and this session's branch is not that.** The dispatch's own branch line ("same family as the held
fix-logging branch") names no branch that exists on `origin`: there is no branch with "fix",
"log" or "logging" in its name; the per-fix `ForagerFix` log itself is on `main` (merged in #76),
and the only branches beyond `main` that touch this area are PR #77's and PR #78's
(`claude/apk-a-signed-8eacc91`, the schema-14 device-check APK). Which branch the owner means is
a stop-and-ask, not something to resolve here (CLAUDE.md, base-branch pitfall). Line numbers below
are on `c914a30` unless a file is said to be identical on `main`.

---

## The two findings that change the dispatch, first

**1. There is no call site.** `movingPace` has exactly one caller, `returnWalkingTime`
(`domain/ReturnWalkingTime.kt:67`), and `returnWalkingTime` has **no caller in `app/src/main`
at all** — `git grep` finds only its definition. The file says so on purpose ("No surface exists
yet, on purpose", `:20`), and the Items 1–3 completion report's hand-off says it in one line:
"The estimate and both its halves exist with no caller." So the dispatch's Need 1 — "there is
currently no log line at the call site" — understates it: on a device today nothing invokes the
pace, on any track, ever. A log line inside `movingPace` would be a line nothing reaches. The
observation point the dispatch wants needs a caller first, and choosing that caller is the
substantive decision below (§A.2).

**2. The dispatch contradicts itself on the withdrawn field.** "Owner ruling — scope narrowed"
withdraws the "which instrument the model actually used" field; the final section, "Decided
beyond scope", says of the same field "**In scope; build it**" under a ruling dated the same day
that "the passes merge". Both cannot govern. This report treats the field as **withdrawn** —
the ruling that narrows scope is the one written as a ruling, the other reads as a stale draft
paragraph — but that is a reading, not a resolution. For the record, the field is one enum read
away (`MovingPace.source`, `domain/MovingPace.kt:185-189`) and costs nothing to include; the
owner's answer decides one token in the proposed format, not the design.

---

## A. Call sites

### A.1 Where the average is computed and where it is consumed

| What | Where | Lines |
|---|---|---|
| The averages, both instruments | `fun movingPace(points: List<TrackPoint>): MovingPace` | `domain/MovingPace.kt:98-147` |
| The result and its derived values | `data class MovingPace` — `differencingSpeed`, `dopplerSpeed`, `source`, `governingMovingMillis`, `speedMetersPerSecond`, `comparison` | `:150-220` |
| The comparison | `val comparison: PaceComparison?` — Doppler and differencing over the Doppler-carrying intervals only, with `intervals`, `movingMillis`, `ratio` | `:209-219`, `:225-233` |
| The consumer | `returnWalkingTime(track, origin, current, fixFreshness, previousHopBand)` — `val pace = movingPace(track.points)` at `:67`; divides by `pace.speedMetersPerSecond` at `:87` | `domain/ReturnWalkingTime.kt:57-90` |
| The consumer's consumer | **none** in `app/src/main` | — |

The points `movingPace` sees are `Track.points` — the filtered list from the one read mapping,
`RoomTrackRepository.toDomain` (`data/repository/RoomTrackRepository.kt:66-79`). Nothing in the
pace reads a stored row directly.

### A.2 Single site or a fan

**A single function with a single caller and zero production callers — no fan.** The one place
the filtered points are already in memory on a cadence during a recording is
`TrackRecordingViewModel.beginPolling` (`ui/track/TrackRecordingViewModel.kt:274-291`):
`trackRepository.getById(trackId)` every `POLL_INTERVAL_MILLIS = 15_000L` (`:492`), the result
held as `breadcrumbPoints`. The Items 1–3 completion report names this poll as where the alert
dispatch would wire `returnWalkingTime`. Two ways to give Pass 2 a call site, for the owner:

- **The pace alone, from the poll.** `movingPace(track.points)` needs nothing but the points.
  One call per poll, per recording; no dependency on the current fix, `FixFreshness` or the hop
  band; no dependency on the alert dispatch. This is the narrow option and the one this report
  proposes (§F).
- **The whole estimate, from the poll.** Wiring `returnWalkingTime` needs the live fix, the HUD's
  freshness classification and a `HopBand` held between polls — the alert dispatch's work, by
  the completion report's own hand-off. Building it here for a log line is the alert dispatch by
  another name.

Two places that look like call sites and are not:

- **Cartography's "recompute on open"** (`ui/log/CartographyViewModel.kt:167`,
  `withRecomputedTrackSnapshots` at `:534-544`) runs `ComputeTrackStatisticsUseCase` — distance,
  duration, elevation — and never `movingPace`. Need 2's plan, "open the entry so it recomputes",
  would not have run the pace even if it had run. The dispatch already demoted Pass 1 for a
  different reason; this is a second, independent one.
- **The `ForagerFix` per-fix log** (`location/AndroidLocationTracker.kt:53-61`; on `main` three lines higher, otherwise identical on
  `main`) is upstream of the sampler and of storage. It sees every raw fix, three times (§E);
  it never sees a stored point, a filtered list or an average.

### A.3 Which instrument is primary — the selection logic, quoted

```kotlin
// domain/MovingPace.kt:185-189
val source: PaceSource get() = when {
    dopplerMovingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS -> PaceSource.DOPPLER
    movingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS -> PaceSource.DIFFERENCING
    else -> PaceSource.DEFAULT
}
```

So the question "does differencing run as primary with Doppler as fallback, or the reverse" has
a more specific answer than either:

- **Both instruments are computed on every call, unconditionally.** The loop (`:109-134`)
  accumulates `movingMillis`/`movingMeters` (differencing, every moving interval) and
  `dopplerWeightedSum`/`dopplerMovingMillis`/`dopplerMovingMeters` (Doppler, the moving intervals
  whose end point carries a counted speed) in one pass. Nothing is skipped because the other
  instrument exists.
- **Selection is by the bar, per instrument, Doppler first.** Doppler *governs* when its own
  moving time reaches five minutes (`MEASURED_PACE_MIN_MOVING_MILLIS = 5 × 60 × 1000`, `:287`);
  otherwise differencing governs when all moving time does; otherwise the default. A track with
  no stored speed at all (every pre-15 row; the 221 rows in the schema-15 capture, all `null`)
  has `dopplerMovingMillis == 0` and lands on differencing or the default. There is no
  "Doppler failed, fall back" path; there is "Doppler has not yet earned it".
- **Two differencing averages exist, not one.** `differencingSpeed` (`:173`) is over *all*
  moving intervals; `comparison.differencingSpeed` (`:213`) is over the Doppler-carrying
  intervals only. The dispatch's "differencing average over the same window" is the second. On a
  track where every moving interval carries a counted Doppler sample the two coincide; on a
  track that straddles the migration or a device that populates speed sporadically they do not
  (the completion report's "partial coverage" test, `MovingPaceTest.kt:172-186`, is exactly that
  case: ten Doppler intervals against forty). The record should carry both, labelled (§D).

### A.4 Existing debug surfaces

| Surface | What it is | Reusable for this? |
|---|---|---|
| `ForagerFix` (`AndroidLocationTracker.kt:53-61`, tag at `:106`) | `Log.d`, one line per raw platform fix, `key=value` fields, `null` printed where `has*()` is false | **As a shape, yes; as a tag, no.** Same `key=value` grammar means the walk's parser needs one more tag filter, not a second grammar. A different tag because the record is a different thing (one per pace evaluation, not one per fix) and because `adb logcat -s` filters by tag. |
| `ErrorLog` (`domain/ErrorLog.kt`, identical on `main`) | `fun interface ErrorLog { fun w(tag, message, error: Throwable) }` — the one logging seam the plain-JVM ViewModels have; production impl is `Log.w` in `MainActivity.kt:46`, test default a no-op (`TrackRecordingViewModel.kt:110`) | **As a pattern, yes; as an instance, no.** It has one method, it requires a `Throwable`, and it is warning-level. A pace record is not an error. The same shape — an owned `fun interface`, injected, no-op by default in tests, `Log.d`-backed in `MainActivity` — is what keeps `android.util.Log` out of the ViewModel (the doc comment records the 23 tests that broke when it was tried). |
| Settings → Crash logs (`ui/crash/CrashLogPanel.kt`) | Read-only list of files `CrashUncaughtExceptionHandler` wrote, view or share | **No.** It lists crash files; it is not a sink, and giving it one is a feature, not instrumentation. |
| The domain layer | No file under `domain/` imports `android.util.Log` (completion report, confirmed there and by grep here) | **No.** The completion report's decided-beyond-scope 8 and owner ruling 3: domain code does not log; the consumer logs. |

**No existing surface carries a structured per-evaluation record.** The proposal (§F) adds one
owned sink in `ErrorLog`'s shape and one tag, and nothing else.

---

## B. Feasibility of the required fields

Every field is on `MovingPace` or on the list the caller already holds. Nothing needs the loop
changed.

| Field | Available? | Where |
|---|---|---|
| Doppler average over the window | yes | `comparison.dopplerSpeed` (= `dopplerWeightedSpeedSum / dopplerMovingMillis`, `:212`); `null` until one counted sample exists (`comparison == null`, `:218`) |
| Differencing average over **the same** window | yes | `comparison.differencingSpeed` (= `dopplerMovingMeters / (dopplerMovingMillis / 1000)`, `:213`). Same intervals by construction: the three Doppler accumulators are updated together in one branch (`:126-131`). |
| Differencing average over all moving intervals | yes, and worth carrying alongside | `differencingSpeed` (`:173`) — the figure that governs when `source == DIFFERENCING`; not the same window (§A.3) |
| Sample count: counted | yes | `pointsCounted` (`:170`) |
| Sample count: null | yes | `pointsWithoutSpeed` (`:166`) |
| Sample count: below the moving floor | yes | `pointsBelowFloor` (`:168`) |
| Doppler cleared the five-minute bar | derivable, one comparison | `dopplerMovingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS` — the first arm of `source` |
| Differencing cleared the five-minute bar | derivable, one comparison | `movingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS` — the second arm. **Carry both booleans explicitly, not `source`**: `source` collapses "differencing yes, Doppler no" into `DIFFERENCING` and "both yes" into `DOPPLER`; the disagreement the dispatch calls a result is only visible with both. |
| Moving time | yes | `movingMillis` (all moving intervals, `:156`) and `dopplerMovingMillis` (the Doppler window, `:160`) |
| Wall-clock span | **not on `MovingPace`; available at the call site** | `points.last().timestampEpochMillis - points.first().timestampEpochMillis` over the same filtered list — the same window the pace saw. `Track.startedAtEpochMillis` is a different clock (the recording's, before the first stored point) and `endedAtEpochMillis` is `null` while recording; report the points' span and, separately, the track's start. |

**Two accounting notes the parser must know, from the loop:**

- The three sample counts sum to `intervalsMoving`, not to the point count (`:170`'s own
  comment): only a point that *ends a moving interval* is examined for a sample. A point ending a
  stopped interval — the point after a photograph — is in none of the three. So
  `intervalsExamined - intervalsMoving` is "intervals skipped as stopped **or** as non-positive
  duration" (`:113`, `intervalMillis <= 0 → continue`, counted in `intervalsExamined` only).
  The record should carry `points`, `intervalsExamined` and `intervalsMoving` so the parser can
  close the books: `points - 1 = intervalsExamined`, `intervalsExamined = intervalsMoving +
  skipped`, `intervalsMoving = counted + nullSpeed + belowFloor`. A record that does not balance
  is a parser bug or a replay on the wrong point set.
- **On every track recorded before schema 15 — the reference track included — every sample is
  `nullSpeed`.** The capture confirmed `speedMetersPerSecond` is `null` on all 221 rows. So the
  annotated track `3aec7001-…` replays the differencing side only: `counted=0`,
  `doppler=null`, `diffSame=null`, `ratio=null`, `dopplerBar=false`. That is the correct output
  for that input and the replay harness should expect it; the Doppler side of Pass 2 needs a
  track recorded on a schema-15 build.

---

## C. Cadence — where the 5 s is configured

The dispatch's measurement (minimum raw stored gap 5.0 s, both tracks, all 221 rows) is not
re-derived here. What the code says about where it comes from:

**The platform request is 1 Hz, both providers, no distance floor.**
`AndroidLocationTracker` (`location/AndroidLocationTracker.kt:73-75`; on `main` the same lines sit three higher):

```kotlin
locationManager.requestLocationUpdates(provider, MIN_UPDATE_INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper())
// MIN_UPDATE_INTERVAL_MILLIS = 1_000L   (:103)
```

for each of `GPS_PROVIDER` and `NETWORK_PROVIDER` that is enabled (`:71-72`). The class's own
comment calls this "only a ceiling on how much raw, unfiltered work this stream does" and says the
sampling decision is the sampler's. The walk findings measured this stream at exactly 1000 ms
between consecutive GPS fixes, 288 of 288.

**The decimation is a recording-side filter, in the domain, before storage.**
`LocationSampler.shouldAccept` (`domain/LocationSampler.kt:29-42`, only a doc comment differs
from `main`):

```kotlin
val accuracy = candidate.accuracyMeters
if (accuracy != null && accuracy > mode.maxAcceptableAccuracyMeters) return false
if (lastAccepted == null) return true
val elapsedMillis = candidate.timestampEpochMillis - lastAccepted.timestampEpochMillis
if (elapsedMillis < mode.minIntervalMillis) return false
val distanceMeters = GeoDistance.metersBetween(...)
return distanceMeters >= mode.minDistanceMeters
```

Three rules, in order: an accuracy ceiling, then an interval floor **and** a distance floor, both
measured against the last *accepted* point, not the last fix. The service runs it on every fix
(`service/TrackRecordingService.kt:116-127`) and only accepted candidates reach `pendingPoints`.

**The interval is fixed per mode, not adaptive.** `TrackRecordingMode`
(`domain/model/TrackRecordingMode.kt:27-29`, identical on `main`):

| Mode | `minIntervalMillis` | `minDistanceMeters` | `maxAcceptableAccuracyMeters` |
|---|---|---|---|
| `HIGH_ACCURACY` | 5 000 | 5 | 30 |
| `BALANCED` | 15 000 | 15 | 50 |
| `BATTERY_SAVER` | 60 000 | 30 | 100 |

Nothing reads battery, speed, or fix quality to change them; the file labels them "adjustable
assumptions, not measured facts".

**Which mode the owner's tracks were recorded in.** The one production start is
`MainActivity.kt:415`, `trackRecordingViewModel.startRecording(TrackRecordingMode.HIGH_ACCURACY)`,
an explicit field-test override of the ViewModel's `BALANCED` default (`:162`); the service also
defaults to `BALANCED` only if the intent's mode extra is missing (`TrackRecordingService.kt:81`).
So the recorded mode is `HIGH_ACCURACY`, whose 5 000 ms floor with a strict `<` rejection (a gap of
exactly 5 000 ms is accepted) is **consistent with** the measured 5.0 s minimum gap. Consistent
with, not derived from: the measurement stands on its own.

**A consequence for the comparison, stated so it is not lost.** The Doppler sample at a stored
point is the receiver's speed *at that instant* (one 1 Hz fix), weighted by the ≥ 5 s interval it
ends. The differenced speed over the same interval is a mean over those ≥ 5 s. So the comparison is
one instantaneous reading against one 5-second mean, per interval, both then duration-weighted over
the window — not a 1 Hz Doppler average against anything. The walk's 0.890 m/s "average Doppler
speed" was over 1 Hz fixes and is a third quantity again.

### C2. What the exclusion filter tests — the predicate, quoted

```kotlin
// domain/NetworkProviderFix.kt:40  (identical on main)
fun TrackPoint.isNetworkProviderFix(): Boolean = timestampEpochMillis % 1_000L != 0L

// :43
fun excludeNetworkProviderFixes(points: List<TrackPoint>): List<TrackPoint> = points.filterNot { it.isNetworkProviderFix() }
```

applied at the one read mapping (`RoomTrackRepository.kt:66-79`):

```kotlin
val stored = rows.map(TrackPointEntity::toDomain)
val kept = excludeNetworkProviderFixes(stored)
return Track(..., points = kept, ..., excludedPointCount = stored.size - kept.size)
```

**The predicate is the clock's fractional-millisecond field. It reads no accuracy, no interval, no
provider, no speed.** The prior record's "sub-second" was this field — the code's own doc comment
uses the same word for it ("every point whose stored timestamp had non-zero milliseconds",
`:10-15`) — and never the inter-point interval; the dispatch is right that the phrase was
ambiguous and right that the interval reading of it is false.

**Why the two properties were confounded in the capture, from the code rather than the rows.**
Accuracy is gated at *write* time, by the sampler's first rule, at the mode's ceiling — 30 m in
`HIGH_ACCURACY`. A network fix worse than 30 m was never stored; one at or under 30 m was stored
and is then excluded at *read* time by its clock. So every stored-and-excluded row is a network fix
that passed the write-side accuracy gate, which is the population the dispatch describes
(12.6–29.8 m, all under 30). That the observed range sits under the ceiling is consistent with this
mechanism; it is not derived from it and the rows were not re-examined here. The two gates are
different code on different sides of storage, and they behave differently on a different device
exactly as the dispatch warns: a phone whose GPS clock carries milliseconds loses its GPS points at
read, whatever their accuracy; a phone reporting the constant `3.7900925` (the owner's, all 289 GPS
fixes) has a write-side gate that does nothing to GPS and everything to network fixes — already
recorded on `LiveFixGate.kt:51-66` and `LocationSampler.kt:23-27` on the branch.

---

## D. Format — proposed, not implemented

One line per evaluation, one tag, the `key=value` grammar `ForagerFix` already uses so the walk's
parser gains a tag filter and a field list, not a second grammar. `null` is printed as the literal
`null` on exactly the fields listed as nullable, and nowhere else — the walk's parser hazard
(coder's note, walk findings) was a parser that expected a number in every field; the field list
below says which may not be. No prose anywhere in the line. A schema version first, so a later
field change does not silently re-shape old logs.

```
ForagerPace: v=1 track=3aec7001-1fac-454d-b716-fde89abf79f4 call=7 at=1789200015000 mode=HIGH_ACCURACY
  points=135 stored=195 excluded=60 first=1788790000000 last=1788791344000 wallMs=1344000
  examined=134 moving=101 movingMs=1010000 movingM=733.0 diffAll=0.7257
  dopplerMs=0 dopplerM=0.0 doppler=null diffSame=null ratio=null
  counted=0 nullSpeed=101 belowFloor=0 dopplerBar=false diffBar=true
  floor=0.5 barMs=300000
```

(Wrapped here for reading; on the device it is one line. The numbers are illustrative of the
reference track's *shape* — 135 kept of 195, all speeds `null` — not a claimed replay result;
`moving`, `movingMs` and the averages are made up and will be replaced by the first real record.)

| Field | Source | Nullable |
|---|---|---|
| `v` | literal `1` | no |
| `track` | `Track.id` | no |
| `call` | per-recording counter, from 1, held by the caller | no |
| `at` | the caller's clock at emission (`CurrentTimeProvider`, already injected into the ViewModel at `:112`) | no |
| `mode` | the active recording's `TrackRecordingMode.name` | no |
| `points` / `stored` / `excluded` | `track.points.size` / `track.storedPointCount` / `track.excludedPointCount` | no |
| `first` / `last` / `wallMs` | first and last kept point's `timestampEpochMillis`; their difference. `0`/`0`/`0` with no points | no |
| `examined` / `moving` | `intervalsExamined` / `intervalsMoving` | no |
| `movingMs` / `movingM` / `diffAll` | `movingMillis` / `movingMeters` / `differencingSpeed` | `diffAll` |
| `dopplerMs` / `dopplerM` | `dopplerMovingMillis` / `dopplerMovingMeters` | no |
| `doppler` / `diffSame` / `ratio` | `comparison?.dopplerSpeed` / `comparison?.differencingSpeed` / `comparison?.ratio` | all three, together |
| `counted` / `nullSpeed` / `belowFloor` | `pointsCounted` / `pointsWithoutSpeed` / `pointsBelowFloor` | no |
| `dopplerBar` / `diffBar` | `dopplerMovingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS` / `movingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS` | no |
| `floor` / `barMs` | the two constants, so a log from a later tuning is self-describing | no |
| *(withdrawn)* `source` | `MovingPace.source.name` — one token, if the owner's answer to finding 2 is "in scope" | — |

**Replay contract.** Export `track_points` for `track`, apply `excludeNetworkProviderFixes`
(or equivalently keep rows with `timestampEpochMillis % 1000 == 0`), order by timestamp, run
`movingPace` over exactly that list: every field from `points` onward must reproduce **exactly**
(the arithmetic is deterministic double math over the same inputs; a tolerance would hide a
different point set). The first three identities in §B are the parser's self-check before it
trusts a record. `points`/`stored`/`excluded` are the check that the record saw the filtered
set and not the raw rows — the check Pass 1 could not make.

**Why per poll and not once per track.** The bar being crossed — `diffBar` turning true at some
`call`, `dopplerBar` later or never — is itself a Pass 2 result, and a single end-of-track line
loses it. At 15 s a two-hour recording is ~480 lines, all `Log.d`, filterable by tag; the same
order as the fix log over the same walk, and one collector, not three.

---

## E. The three-listener hazard, and what the format does about it

**Where the three come from, on the code.** `LocationTracker.fixes` is a `callbackFlow`
(`AndroidLocationTracker.kt:35-78`); every *collection* of it registers a fresh
`LocationListener` on every enabled provider. Three places collect it during a recording:

| Collector | Line |
|---|---|
| `TrackRecordingService` (the one that stores points) | `service/TrackRecordingService.kt:116` |
| `AvailabilityViewModel` (the compass strip / HUD live fix) | `ui/availability/AvailabilityViewModel.kt:143` |
| `TrackRecordingViewModel` (return-to-start, origin seeding) | `ui/track/TrackRecordingViewModel.kt:306` |

Each registration's `onLocationChanged` runs the `ForagerFix` `Log.d` (`:53-61`), so one platform
fix is logged three times from one PID — the walk's 1022 lines for 344 fixes. **Storage is not
tripled**: only the service's collector runs the sampler and writes, and each fix reaches it once.
So the stored points, the read seam, and anything computed from `Track.points` — the pace
included — are already single-counted. The multiplier is a property of the fix log, not of the
data the pace sees.

**What the proposed record does about it.**

- **It is emitted from one collector, once per poll, not per fix.** The `ForagerPace` line is
  written from `beginPolling`, which runs in one `TrackRecordingViewModel` per recording. It is not
  in any `onLocationChanged`. The three-listener multiplication cannot reach it by the mechanism
  that triples the fix log.
- **It is still self-deduplicating, in case a second emitter ever appears.** `(track, call)` is
  unique per recording by construction; `(track, points, last)` identifies the *window* — two
  records with the same `points` and `last` were computed over the same point list (no flush landed
  between polls, which at a 20-point / 30 s flush against a 15 s poll happens every other poll),
  and a parser collapsing on that key drops both accidental duplicates and no-new-data polls. A
  record with the same `(track, call)` and different fields is a bug, not a duplicate.
- **The fix log's own rule survives unchanged.** Any analysis that joins `ForagerPace` back to
  `ForagerFix` — to see which raw fixes fed a window — must first dedupe the fix log on
  `(time, provider, acc, speed)` as the walk findings say, then keep only `time % 1000 == 0` fixes
  with `time` in `[first, last]`. The pace record does not and cannot dedupe the fix log for it.

**Not in scope, and not touched by the proposal:** reducing the three collectors to one. It stays
queued with the duplicate-listener item as the walk findings left it.

---

## F. Proposed diff — description only, nothing written

On a branch cut from `c914a30` (PR #77's head), not from `main` (see the top of this report):

1. **`domain/PaceLog.kt` (new).** `fun interface PaceLog { fun record(line: String) }` — the
   `ErrorLog` shape: owned, injected, no `android.util.Log` in the domain or the ViewModel. One
   constant, `PACE_LOG_TAG = "ForagerPace"`, beside `ForagerFix`'s. Production instance in
   `MainActivity` next to `androidErrorLog` (`:46`): `PaceLog { Log.d(PACE_LOG_TAG, it) }`.
   Default in `TrackRecordingViewModel`'s constructor: a no-op, like `errorLog` (`:110`).
2. **`domain/MovingPace.kt` (addition, no change to the loop or the class).** A pure formatter,
   `fun MovingPace.toLogRecord(track: Track, call: Int, atEpochMillis: Long, mode: TrackRecordingMode): String`,
   producing §D's line. Pure so the format is asserted in a plain-JVM test by construction — every
   field against a hand-built `MovingPace`, the three `null`s present together and only together,
   the three §B identities holding — and so the ViewModel test only has to assert that the line
   reached the sink.
3. **`ui/track/TrackRecordingViewModel.beginPolling` (one call added, after `:278-279`).** After
   `getById` succeeds with a non-null track: `paceLog.record(movingPace(track.points).toLogRecord(track, ++paceCall, currentTime.nowEpochMillis(), active.mode))`.
   `paceCall` reset to 0 in `startRecording`. Cost: one haversine per kept point per 15 s, the
   figure the pre-build report reasoned sub-millisecond for thousands of points; `pathHome` was
   accepted on the same reasoning.
4. **Tests:** the formatter's (new), and one on `TrackRecordingViewModel` through
   `runRecordingTest` (the poll-loop rule, CLAUDE.md) with a recording fake that captures lines,
   asserting the first poll's record field-for-field against the fake repository's points.
   Reverted-variant check: the formatter test with the `doppler`/`diffSame`/`ratio` trio
   emitted as `0.0` instead of `null` — the walk's parser failure in miniature — must fail on
   exactly that assertion.

**Untouched, by the dispatch's own list:** which instrument governs (`source` and the bar are
read, never written); the predicate, the floor, the bar; every existing test and the skip
allowlist; `ForagerFix`'s format; the three collectors.

**What the owner has to answer before any of it is written:**

1. Which branch is "the held fix-logging branch", and is cutting from `c914a30` what is meant
   (top of this report).
2. The `source` field: withdrawn, or "in scope; build it" (finding 2).
3. Per poll (proposed) or once per recording end.
4. Whether §A.2's narrow option — the pace from the poll, without `returnWalkingTime` — is
   acceptable as Pass 2's caller, given that the estimate itself still has none and the alert
   dispatch owns wiring it.

---

## Test and skip counts

**Not run — nothing changed.** The branch under report, `c914a30`, reports in its own completion
report **1277 tests, 0 failures, 24 skipped**, the 24 being the CI allowlist's identity set
(`2026-09-07-track-distance-label-completion-report.md`). `main` at `8eacc91` is the Item 4
completion report's count. Neither is re-verified here; no build was made and the sandbox has no
`/dev/kvm`, so no device result of any kind is claimed.

---

## Required disclosure

### Confirmed vs. inferred

**Confirmed from the code on `c914a30`** (read in full, by `git show` of the branch's files):
`movingPace`'s loop and every accumulator; `MovingPace`'s derived values, `source`, and
`comparison`; `returnWalkingTime`'s one call to `movingPace` and its degrade set; the absence of
any production caller of `returnWalkingTime` (`git grep` over `app/src/main`); the poll loop,
its interval and its `getById`; the read seam's mapping and `excludedPointCount`; the predicate;
the sampler's three rules in order; the three mode constants; the 1 Hz platform request on both
providers with `0f` distance; the three collectors of `fixes`; `ErrorLog`'s single method and
its production and test instances; that `CartographyViewModel`'s recompute calls
`ComputeTrackStatisticsUseCase` and never `movingPace`; `MainActivity`'s explicit
`HIGH_ACCURACY` start; that no branch on `origin` other than `claude/new-session-b7z9bg` carries
`version = 15` or the speed columns (every remote ref grepped). **Confirmed from GitHub:** PR #77
open, head `c914a30`, base `main`; PR #78 open from `claude/apk-a-signed-8eacc91`; PR #76 closed
(merged as `8eacc91`). **Confirmed from the audit record on the branch:** the owner's ruling that
domain code does not log and the first consumer logs the comparison per track (completion report,
ruling 3); the walk's three-listener count and dedupe key.

**Inferred:** that the "held fix-logging branch" is PR #77's branch or a local branch never
pushed — no branch on `origin` matches the description; that the 60 excluded rows' accuracy range
sitting under 30 m is the write-side gate's doing — consistent with the sampler's first rule and
the recorded mode, not re-derived from the rows; that the "in scope; build it" paragraph is a
stale draft rather than a later ruling; the ~480-lines-per-two-hours figure (15 s poll, arithmetic).

### Could not determine

- **Whether point differencing has ever run as primary on real data.** Still unowned, as the
  dispatch says. This report adds only that there is no path by which it *could* have run on a
  device: no caller exists, and the Cartography recompute never reaches the pace.
- Which branch the owner's "held fix-logging branch" is.
- Whether the dispatch's last section or its scope ruling is the later text.
- Any device behaviour. No `/dev/kvm`.

### Premises in this dispatch that were wrong

- **"There is currently no log line at the call site."** There is no call site. `returnWalkingTime`
  has no production caller; `movingPace` is reached only through it. The observation point needs a
  caller before it needs a line (§A.2).
- **"Even via the Cartography entry, both stored tracks would recompute."** They recompute
  distance statistics (`ComputeTrackStatisticsUseCase`), not the pace. The entry route was never a
  route to the differencing path, independently of the by-construction-agreement argument the
  dispatch already makes.
- **Point differencing has one average.** It has two — over all moving intervals, and over the
  Doppler-carrying ones — and only the second is "the same window" as Doppler (§A.3). A record
  carrying one figure labelled "differencing" would be ambiguous on any partially-covered track.
- **The scope ruling and the final section disagree** on the withdrawn field (finding 2). Not a
  code fact; a defect in the dispatch text, reported rather than resolved.
- **"Branch: … same family as the held fix-logging branch."** No such branch is on `origin`, and
  this session's pinned branch is cut from `main`, which carries none of the code. Reported at the
  top as the stop-and-ask it is.

### Decided beyond scope

- **Reading the field as withdrawn** for the purposes of the proposed format, with the cost of the
  other answer stated (one token).
- **Proposing the narrow caller** (the pace from the poll, not the whole estimate) rather than
  presenting it as one of two equals: the alert dispatch owns the estimate's wiring by the
  completion report's hand-off, and duplicating that for a log line would be the wider change the
  owner's ruling 3 declined ("logging from domain code to no consumer is how a log nobody reads
  gets written" — the same argument runs against building the consumer for the log's sake).
- **Committing this report on `claude/new-session-3x1aba`**, cut from `main`, rather than waiting
  for the branch question — a report that lives only in a session transcript is not recorded
  (CLAUDE.md, push before you tidy), and a docs-only commit on a `main`-based branch collides with
  nothing. The build must not follow it there.

---

## Addendum: owner rulings (2026-09-08), and what followed

### Rulings

1. **The no-call-site finding is the fourth recorded instance of the CLAUDE.md family**, one
   level up from the other three: Pass 1 was designed to detect whether point differencing had
   run on real data, when `returnWalkingTime` has no production caller and the answer was
   available from the code the whole time — a value was checked where the question was the
   caller. Distinguishing feature: the cheap question, "who calls this?", would have closed it
   immediately. Recorded in CLAUDE.md (Testing) as instance (4). **Point differencing has never
   run in production.** Not "could not tell": has not.
2. **The contradiction in the dispatch is the owner's, and traceable.** Earlier in that session
   the dispatch briefly carried a "kept separate" ruling the owner did not write and could not
   account for; the disclosure was overwritten but the body kept the field, so the copy the coder
   read said both. The actual ruling: **the passes merge, the field is in scope, build it.** Any
   copy of that dispatch not from the owner's session is suspect. (`source=` is in the record.)
3. **Branch: `c914a30`.** The pinned branch was cut from `main` and had no pace code. Done by
   merging `origin/claude/new-session-b7z9bg` (`c914a30`) into `claude/new-session-3x1aba` —
   a merge commit, not a rewrite, so the pushed report commit is kept as pushed (CLAUDE.md: history
   on a remote is editable, unpushed work is not; a merge risks neither). The only conflict was two
   rows appended to `docs/audits/README.md`; both kept, this report's last.
4. **Per-poll records.** Replay needs the sequence, and one-per-recording collapses exactly the
   disagreement cases — the two bar booleans — that the comparison exists to surface.
5. **The pace alone, from the 15 s poll.** Wiring the full estimate would put a production caller
   on `returnWalkingTime`, which is product work that prejudges the path-home ruling. Instrument
   the pace; leave the estimate unreachable until path-home comes back.
6. **The accuracy finding carries forward.** The sampler gates at 30 m on write, which is why the
   capture could not separate accuracy from timestamp. The seam predicate reads only the
   timestamp, so the proxy-for-provider concern the owner raised on the GPX report stands, and the
   30 m write gate does not protect against it: a device whose GPS clock carries milliseconds
   loses its GPS points at read regardless of their accuracy, and a network fix under the
   ceiling is stored regardless of its clock. Neither gate is a provider test; only a stored
   provider column would be (the pre-build report's "third column the same reasoning would
   justify", not this dispatch's to add).

### The beta-signing commits, relative to #77 — facts for the decision, which is the owner's

The owner asked that where these land be decided before two branches diverge. What exists:

| Where | Commit(s) | Content |
|---|---|---|
| PR #77 (`claude/new-session-b7z9bg`), and now this branch by the merge | `5f78323` "Stable debug signing identity for CI and device checks" | `app/build.gradle.kts` +22 (the `signingConfigs` block and `debug { signingConfig = … }`), `app/debug.keystore` (2 666 bytes) |
| PR #77, and now this branch | `495725b` "Build guard: release must never sign with the committed debug keystore" | `app/build.gradle.kts` +42 (`verifyReleaseNeverSignsWithDebugKeystore`), the completion report's beta-signing section, a README row |
| PR #78 (`claude/apk-a-signed-8eacc91`, open against `main`) | `bd5429e` | **exactly `5f78323`'s content on `8eacc91`**: the same 22-line `build.gradle.kts` hunk, and `app/debug.keystore` **byte-identical** to #77's (`git diff` between the two refs on that path is empty). The guard is **not** in #78. |

So the two branches carry one identical change and one that only #77 has. Git will merge the
identical hunk and the identical binary cleanly whichever lands first; the divergence risk is
only if either copy is edited before the other lands. Options, not a decision: merge #78 first
(the device-check APK's identity on `main` now, schema 14), then #77 brings the guard; or merge
#77 and close #78 as contained. Either way the guard should not be separated from the keystore
for long — it is what makes the committed key safe to have.

### What was then built (same day) — see the completion section below
