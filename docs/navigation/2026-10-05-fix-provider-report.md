# Every fix carries its true source (dispatch 2026-09-28-527): report

**Status: built on `fix-provider`, not merged. The owner answered the three stops in RECORD -558 (Amendment 1). Tests, revert checks and the full suite are below. The S22 step has not run: it needs the owner's word.** The dispatch is
`prompts/preserved/2026-10-05-01.md`. It was written against `a9dfdd59`. -516 has merged since
(PR #174), so this was read at `4b72b25c`, where `fix-provider` is cut. App paths are relative to
`app/src/main/java/com/zynergylabs/forager/app/`. Line numbers are as of `4b72b25c`.

## Verify before building

### Citations re-checked

These still hold at `4b72b25c`: `LiveFixGate.kt:71-74`, `AndroidLocationTracker.kt:89-97`
(`toFix()`), `LocationTracker.kt:45-53` (`LocationFix.Update`, with no provider field),
`NetworkProviderFix.kt:48`, `OffTrackJudge.kt:61,70`, and `TrackRecordingViewModel.kt:896` and
`:855-867`. Some of -510's table lines have moved:

| Path | Was (at `d4bd00bf`) | Now |
|---|---|---|
| Live gate in `ui/availability/AvailabilityViewModel.kt` | `:266-277` | `:269-285` |
| `NavigationHud.kt` `arrivedAtStart` | `:440-443` | `:447-450` |
| `straightLineToTarget` and `nextStraightLine` | `:450-488` | `:457-495` |
| `navigationReadout` | `:509` | `:516` |
| `arrivedAtStart` call in `AvailabilityCompactMapUi.kt` | `:594` | `:599` |
| Strip's `liveLocation` and `liveAltitudeMeters` | `:871-872` | `:879-880` |
| `AvailabilityScreen.kt` waypoint line | `:989-997` | `:992-1000` |
| Service collector in `TrackRecordingService.kt` | `:138-156` | `:148-170` |

These have not moved: `MushroomLogViewModelFactory.kt:39`, `MushroomLogViewModel.kt:403-420` and
`AvailabilityCompactScaffold.kt:1152`.

### 1. Every path a live fix takes to something that acts

**Where `liveFix` is set.** It is written in one place only, `AvailabilityViewModel.kt:280`. That
happens when `acceptLiveFix` passes (`:277`). A refused fix goes to `approximateFix` (`:295`). Every
reader of `liveFix`, `liveLocation` and `liveAltitudeMeters` in `main/` was found by grep, and each
one is downstream of that line. So a provider check added at `:277` covers all of them together:

- **Arrival:** `arrivedAtStart`, for "Arrived" and the ring.
- **The waypoint's line:** `nextStraightLine`.
- **A new find's location:** `freshDeviceLocation`.
- **Display:** the HUD readout and the strip.

**Display paths need no change.** The map dot is fed by the app from `shownPosition`
(`domain/ShownPosition.kt:46-59`, `ui/map/MapPosition.kt:51`). True north reads `headingFix`, which
is `liveFix ?: approximateFix ?: lastKnownFix` (`AvailabilityUiState.kt:359`). Once a network fix
goes to `approximateFix` rather than `liveFix`, both pick it up as approximate: the dot shows the
approximate look and the strip shows approximate text.

**What changes for a network fix of 50 m or better.** Today it replaces `liveFix` and can displace a
fresh GPS fix. Afterwards it is held only as `approximateFix`. `shownPosition` already prefers any
precise fix that is not lost (`:52`), so the network fix cannot take over from GPS.

**`TrackRecordingViewModel` has its own stream (`:880-905`).** On that stream:

- **`lastGatedFix`** (`:896-897`): the first-fix rule of `LocationSampler` for the active mode,
  with any provider allowed. It seeds the origin waypoint (`:899`) and `RouteHome` (`:859`). A
  network fix within the mode's ceiling reaches both today. The provider check goes at `:896`.
- **`lastGatedFix` also feeds the recording's sundown countdown** (`updateSundown`, `:825-838`, from
  `40f4c59c`). Neither the dispatch nor -510's table lists this reader. See stop 2.
- **`returnToStart(point)`** (`:902`) takes every fix. It is display only: the return control's
  distance text and its content description (`AvailabilityMapControlsUi.kt:235,566-578`).

**-516's watcher.** `SundownWatch.onFix` (`domain/SundownWatch.kt:131-136`) is called with a raw
`TrackPoint` from the service (`TrackRecordingService.kt:156`). It sets `newestGpsFix` by the
timestamp rule (`:134`). `newestGpsFix` drives the walk back and the "arrived home" stop (`:187`).
The sunset is taken from a fix of any provider, which is the owner's exception and is left alone.

**A second, one-shot path that neither table lists:** `LocationProvider.getCurrentLocation()`
(`location/AndroidLocationProvider.kt`). It asks GPS and the network at once and returns
**whichever answers first**. It applies no accuracy gate. Its `LocationResult.Success` carries no
accuracy and no provider. Its callers:

- **Acts, and writes data:** `MushroomLogViewModel.requestAndPatchCaptureFix` (`:1022-1040`). The
  position is written to the photo row, and for the camera's find path it is promoted to the find's
  `foundAt` (`:1016`). Indoors or at a cold start the network usually answers first: the class's
  own doc comment says so. **So a camera find can be saved at a network position of any accuracy
  today.** See stop 1.
- **Sets the species search region:** `AvailabilityViewModel.useCurrentLocation` (`:449`). That
  search uses a radius in kilometres.
- **Display or camera centring:** `locateMe` (`:488`), the default centre for offline maps
  (`:1034`) and the cartography entry's centring (`CartographyEntryReportScreen.kt:327`).

### 2. The service's stream

The service holds the `LocationFix.Update` at the moment it calls `toTrackPoint()`
(`TrackRecordingService.kt:151`). **Proposal:** the service passes the live provider as an argument
beside the point, and nothing stores it:

- **Calls:** `returnWatch.onFix(candidate, fix.provider)` and `sundownWatch.onFix(candidate,
  fix.provider)`.
- **Sundown watch:** sets `newestGpsFix` only when the provider is GPS.
- **Off-track judge:** gets the reading's provider, so `next(reading, provider)` at `:69-70` judges
  the live reading by its provider. The stored path at `:61` keeps the timestamp rule, because a
  stored point has no provider.
- **No column is added.** The sampler and the stored track are unchanged.

### 3. Unknown providers

The logs were read for this. I counted `provider=` across every `ForagerFix` line in the 56 files
under `~/Zynergy/device-evidence/` that contain the tag. Only `gps` (76,425 lines) and `network`
(4,944 lines) appear. Nothing else appears: no `fused`, `passive` or `null`.

In the code, live updates are requested from `GPS_PROVIDER` and `NETWORK_PROVIDER` only
(`AndroidLocationTracker.kt:71-75`). `PASSIVE_PROVIDER` is read only for the last known position
(`AndroidLastKnownLocationSource.kt:68`), which is display only. So nothing on main receives
`fused` or `passive` as a live fix.

**Proposal:** the planner's default. An unknown provider never acts and may be shown, as
approximate.

### 4. The disagreement check

**Proposal:** tag `ForagerFixRule`, at debug level, in `AndroidLocationTracker.onLocationChanged`
beside the existing `ForagerFix` line. It logs only when provider is GPS and the milliseconds are
not zero, or provider is network and the milliseconds are zero. An unknown provider has nothing to
disagree with, so it is not logged.

During a recording it fires once per collector, as `ForagerFix` does. Each collector registers its
own listener: three do during a recording (`TrackRecordingViewModel.kt:656`). See the counts below.

### 5. The S22's own logs, recounted

**The dispatch's counts are log lines, not fixes. Two things inflate them:**

- **The desk folder holds three overlapping captures.** `logcat.txt` has 1,643 lines, `dot.txt`
  1,317 and `dot2.txt` 1,766. Together that is 4,726, which matches the dispatch. The union of the
  three is 1,766 distinct fixes. Fixes were counted as distinct by provider, accuracy and
  timestamp. The first two files are contained in `dot2.txt`.
- **During a recording each fix is logged once per collector:** up to three times on the walks.

Distinct fixes:

| Capture | GPS fixes | GPS accuracy | GPS ≤ 50 m | Network fixes | Network accuracy | Network ≤ 50 m |
|---|---|---|---|---|---|---|
| Desk (union) | 1,514 | 18.7–267.7 m | 222 | 252 | 15.2–100 m | 5 |
| `2026-10-03-owner-walk` | 648 (607 at 3.79 m) | 3.79–9.9 m | 648 | 81 | 12.5–600 m | 11 |
| `2026-10-03-walk-t21` | 387 (369 at 3.79 m) | 3.79–20.1 m | 387 | 60 | 16.7–500 m | 5 |
| `2026-10-03-walk-zoom` | 700 (639 at 3.79 m) | 3.79–12.7 m | 700 | 93 | 11.6–500 m | 83 |

Walk totals: 1,735 GPS fixes, of which 1,615 report exactly 3.79 m, and 234 network fixes. The
dispatch's line counts were 4,614 and 4,460.

**The timestamp rule against the provider.** The dispatch says it agreed on every fix. That holds
for the desk only: 1,766 of 1,766 distinct fixes. **On the walks it disagreed 4 times in 1,969:**

- three GPS fixes stamped with milliseconds (463, 479 and 602 ms; accuracy 8.8 m, 11.8 m and
  9.9 m);
- one network fix on a whole second (300 m).

The rule is not exact even on the S22. The disagreement log is worth having.

Not read: `2026-10-03-walk-t21/a1-replay-real-watch/log.txt`, a replay rather than a capture.

**For the `LiveFixGate.kt` comment.** Both sentences the dispatch names are wrong on these counts:
"carries no signal on that device's GPS path" and "never rejects a GPS fix". Indoors, GPS reports
18.7–267.7 m, and 1,292 of 1,514 desk GPS fixes are over 50 m. 3.79 m is the outdoor best case.
The limits: one phone, one desk session, three walks.

## Stop-and-ask, for the owner

1. **Camera finds take the first answer from GPS or the network, with no accuracy gate.** This is
   item 1's one-shot path. It is outside the paths the dispatch names, and it is the one place
   besides `freshDeviceLocation` where a find's location is written from a fix. The options:
   - **(a)** Leave it alone in this dispatch, and record it as an uncovered failure against -519
     for a dispatch of its own.
   - **(b)** Bring it in now. The one-shot result would carry its provider, and the camera path
     would refuse a network answer. That reverses the deliberate "race both" choice
     (`AndroidLocationProvider.kt`, `awaitFirstLocation`'s doc comment). It also needs a fallback,
     or under canopy a camera find gets no location.

   **Recommend (a).** The fallback is a design question in its own right.

   The search region (`useCurrentLocation`) is left as is under either option: it searches at
   kilometre scale.
2. **Making `lastGatedFix` GPS-only would blank the recording's sundown countdown indoors.**
   `ComputeSundownCountdownUseCase.kt:42` returns `NoPositionYet` without a position. Today a
   network fix within the mode's ceiling gives the countdown its place. The options:
   - **(a)** Split the field. The origin waypoint and `RouteHome` read a GPS-only gated fix. The
     countdown keeps reading what it reads today: gated by the mode, any provider.
   - **(b)** Make all three GPS-only, and accept "no position yet" indoors.

   **Recommend (a).** The countdown is unchanged, and it matches -516's "sunset from any fix"
   exception. It also means nobody sees a countdown that looks broken indoors.
3. **The provider field's default on `LocationFix.Update`.** It is built at 47 sites in 26 test
   files and at two sites in `main/`. The options:
   - **(a)** No default. The compiler finds every site, and each test fixture says GPS explicitly.
   - **(b)** Default to unknown. That is honest, but every fixture meant as a precise fix silently
     turns approximate, and it surfaces as unrelated test failures.
   - **(c)** Default to GPS. That is a guess, and the dispatch rules guesses out.

   **Recommend (a).** It is a mechanical edit to fixtures only, and no assertion changes.

## The owner's answers (RECORD -558, relayed by the planner)

The answers are quoted from the record on `records-after-173` (`ababdeef`). That record was read
before acting on them.

1. **Camera finds:** "Later, on the list (Recommended)". This dispatch does not change them. They are
   recorded as L7 on the build list and as an uncovered failure under -519.
2. **The recording's countdown:** "Keep it working (Recommended)". The origin waypoint and the route
   home take GPS only. The countdown keeps reading any fix.
3. **Places that build a fix:** "Must always say (Recommended)". There is no default provider, and
   test fixtures say GPS.

Two things the planner accepted while the work was under way:

- **The end waypoint takes GPS only** (under the disclosures below).
- **Replays of saved walks keep the timestamp rule.**

## What was built

| Commit | What |
|---|---|
| `f7824f15`, `80c3f2e0` | This report: verification, then the end waypoint the verification missed |
| `dbaa8efe`, `26559f02` | Stage A: the provider field and the failing tests, with no behaviour change |
| `68261b76` | Stage B: the behaviour |

**Every fix carries its provider.** `domain/FixProvider.kt` is new: GPS, network or unknown, and
`mayAct` is true for GPS only.

- `LocationFix.Update.provider` has no default.
- Both `Location` mappers fill it through `AndroidLocationTracker.fixProviderOf`: the live tracker,
  and the last known source. The platform's GPS and network constants map to themselves. Anything
  else, `null` included, maps to unknown.
- It is never stored. `toTrackPoint()` is unchanged, and so is the schema.

**The live gate** (`domain/LiveFixGate.kt`, `acceptLiveFix`) refuses any fix whose provider is not
GPS, before the accuracy test. The collector already held a refused fix as -510's approximate
reading. So a 15 m network fix now shows as the approximate dot, with the strip's or the HUD's
"Approximate…" words. It never becomes `liveFix`, so these never read it:

- "Arrived";
- the waypoint's line;
- a new find's location.

The doc comment is corrected with the recounted figures and their limits.

**`TrackRecordingViewModel`** now has `lastGatedGpsFix` beside `lastGatedFix`. It is the same
mode-ceiling rule, for GPS fixes only, and these read it:

- the origin waypoint;
- the end waypoint;
- the route home.

`lastGatedFix`, of any provider, is read only by the sundown countdown (RECORD -558).

**The service's stream.** The service passes `fix.provider` beside each point to `ReturnWatch.onFix`
and `SundownWatch.onFix`.

- `SundownWatch` sets its GPS fix (the walk back, and "arrived home") by provider.
- `OffTrackJudge.next(reading, provider)` judges a live reading by its provider. With no provider
  (a stored point in a replay) it judges by the timestamp rule, as before. The path is stored points,
  and keeps the timestamp rule.
- `isNetworkProviderTimestamp` is the timestamp rule itself, on a bare timestamp.
  `TrackPoint.isNetworkProviderFix()` now calls it, so there is still one rule.

**The disagreement log.** Tag `ForagerFixRule`, at debug level, in
`AndroidLocationTracker.onLocationChanged`. It logs only when a GPS fix has milliseconds or a network
fix lands on the whole second, for example `provider=gps timestampRule=network time=…`. During a
recording it logs once per collector, as `ForagerFix` does.

**Doc comments** were brought up to date in four places:

- `SundownWatch`: the walk back reads the provider, and its failure direction.
- `OffTrackJudge`: which rule judges which reading.
- `AvailabilityUiState.approximateFix`: what the gate refuses.
- `MushroomLogViewModel.freshDeviceLocation`: the find path reads GPS only, and the camera path
  does not come through it.

**Not touched:**

- satellite status;
- the 50 m value;
- -510's display rules;
- -516's alert rules, beyond how GPS is told from network;
- `MovingPace` and `ReturnWalkingTime`;
- the schema;
- the sampler;
- poor GPS readings in Battery-saver tracks and in the off-track judge;
- the camera's one-shot path (L7).

## Tests

There are 31 new tests, all through real entry points. 47 existing fixtures gained a provider, and
39 existing watch calls gained one. Every line removed from an existing test returns with only a
provider added, so no assertion changed. That was checked mechanically.

| Where | Tests |
|---|---|
| `AndroidLocationTrackerTest` | A 15 m GPS fix arrives as GPS, field for field. A 15 m network fix arrives as network, field for field. Fused, passive, an unheard-of name and a missing one are all unknown. The rule log fires for a GPS fix stamped with ms, and for a network fix on the whole second. It stays silent for a GPS fix on the whole second and for a network fix with ms; each of those has a control showing the fix was logged at all. |
| `AndroidLastKnownLocationSourceTest` | A fused fix held by the passive provider is carried as unknown. This is the one real path another name can arrive by. |
| `AvailabilityViewModelLiveFixTest` | A 15 m network fix, and a 15 m unknown one, never become the gated fix and are held as approximate. A 15 m GPS fix becomes the gated fix. A network fix after a GPS fix leaves the GPS fix held. |
| `AvailabilityScreenApproximatePositionTest` | At the waypoint, a 15 m network fix and a 15 m unknown one: no "Arrived", no ring, no line, the approximate dot, the HUD's approximate message and no needle. A 15 m GPS fix: "Arrived", the ring and the precise dot. |
| `MushroomLogViewModelWiringTest` | Through `createMushroomLogViewModel`, as `MainActivity` builds it: a find gets no location from a network or unknown fix, and gets the GPS fix's location. |
| `TrackRecordingViewModelTest` | No origin from a network or unknown fix, and a GPS fix after it does seed one. The end waypoint is the last GPS fix, not a later network fix. The route home is not searched from a network or unknown fix, and is from a GPS fix. |
| `TrackRecordingSundownTest` | A 15 m network fix still gives the countdown its place. This guards answer 2: it passes before and after, and r3 shows it can fail. |
| `SundownWatchTest` | A GPS fix stamped with ms gives the walk back. A network fix on the whole second, or an unknown fix, gives none. |
| `ApproximateReadingNeverDecidesTest` | While returning, network readings on the whole second, or unknown readings, never go off-track or alert. GPS readings stamped with ms count, and walking off alerts once. |

**Red run at stage A** (`26559f02`, the nine classes): 118 tests, 20 failures. All 20 are new tests,
each failing on its own claim:

- a network or unknown fix became the gated fix, the origin, the end, the route-home source or a
  find's location;
- the timestamp rule decided instead of the provider;
- the rule log was empty.

The 11 new tests that passed are the expected ones:

- the provider-mapping tests (the mapping is part of the field);
- the countdown guard;
- the GPS controls.

The first compile at stage A failed on two watch calls the sweep had missed: a method reference
`watch::onFix`, and a receiver call inside an extension. Both were fixed (now GPS) before the red
run, and the compile log was clean when the results were read.

**Green at stage B** (the nine classes): 118 tests, 0 failures. That was after one correction to a
new test. It asserted the strip's note while navigating, where the HUD stands instead, as -510's own
tests read it. It now asserts the HUD's "Approximate, finding GPS…" and no needle. Every assertion
before that line had passed.

### Revert checks

Eight checks, run by `revert.py`. Each one:

- saves a copy of the file, applies a one-line revert and runs the named classes;
- refuses to read results if the build log has a compile error;
- restores from the saved copy, never from git;
- confirms by hash, and by finding the forward line, that the forward change is back.

All eight restored, and `git status` was clean afterwards.

| Check | Revert | Result |
|---|---|---|
| r1 | Gate ignores the provider | 7 fail: the 3 ViewModel tests, the 2 screen tests, and 2 wiring tests ("expected null, but was LatLng(45.52, -122.68)") |
| r2 | Recording: any provider seeds the origin | 5 fail: origin from network or unknown, end at 45.5, route home `Ahead` from network or unknown |
| r3 | Countdown reads the GPS-only fix | 1 fails: "NoPositionYet cannot be cast to Known" |
| r4 | Sundown watch back on the timestamp rule | 3 fail: About ↔ Unknown |
| r5 | Judge ignores the provider | 3 fail: the three new off-track tests |
| r6 | Rule log never fires | 2 fail: the two disagreement tests, "but was: []" |
| r7 | `toFix` drops the provider (unknown) | 6 fail: four whole-fix comparisons and the two log tests (unknown never disagrees) |
| r8 | Unknown names map to GPS | 2 fail: "provider fused expected UNKNOWN but was GPS", and the passive test |

Each failure is one that edit can produce.

## Suite

The full suite ran once, at stage B (`68261b76`): `:app:testDebugUnitTest`. The build log has no
compile errors.

- **After:** 3,750 tests, 0 failures, 24 skipped.
- **Before:** 3,719, derived and not run. It is 3,750 less the 31 new `@Test`s, since the diff from
  `4b72b25c` adds 31 and removes none. The suite was not run on `main` in this dispatch: one Gradle
  build at a time, and the dispatch asks for the suite once.

No skip was added. `@Ignore` appears 51 times under `app/src/test` at `4b72b25c` and at `HEAD`.

## The S22 step (not run: needs the owner's word)

The dispatch's step, ordered so the cheap check comes first. It runs on the S22 with
`adb logcat -s ForagerFix ForagerFixRule`.

1. **Indoors at the desk**, a waypoint placed at the desk, the Maps tab open.
   - **Pass:** while logcat shows a network fix of 50 m or better arriving, the dot is the
     approximate one, and the strip says "Approximate location, finding GPS…". Navigating to the
     desk waypoint never shows "Arrived" and draws no line.
   - **Evidence:** screenshots, and the logcat lines.
2. **Outdoors.**
   - **Pass:** a GPS fix takes over as before: the normal dot and the strip's coordinates.
   - **Evidence:** a screenshot, and the logcat line with `provider=gps`.
3. **Observation, not a gate:** whether any `ForagerFixRule` line appears, and how many against the
   `ForagerFix` lines.

## Against RECORD -519

| Path | Failure covered | What fills the gap without GPS | Left uncovered |
|---|---|---|---|
| The live gated fix: "Arrived", the waypoint's line, a new find's location | A network fix of 50 m or better posing as precise | -510's approximate position, for display only; failing that, the last known position, greyed; failing that, "Location services unavailable". No "Arrived", no line, a find without a location. | A GPS fix that is poor but under 50 m (satellite status, -526) |
| Recording: origin and end waypoints, the route home | The same | No origin or end waypoint ("validly absent", as before); the route home stays a dash until a GPS fix | A Battery-saver GPS fix up to 100 m still seeds them (kept by -526) |
| Recording's sundown countdown | None: it deliberately reads any fix (-558) | Its sunset is right to seconds from a network fix | None new |
| -516's sundown watch, the walk back | A network fix whole-second stamped, read as GPS | -516's fallback: leave-by is sunset minus the margin, and the walk back is "unknown" | None new |
| The off-track judge, the live reading | The same | No judgement until GPS (as for network before) | Poor GPS readings count, the line widened by their accuracy (kept by -526) |
| **Camera finds (the one-shot `LocationProvider`)** | **Not covered** | — | **A camera find can still be saved at a network position of any accuracy.** L7 on the build list (-558). |

## Disclosure

- **Confirmed vs inferred.**
  - Confirmed by test, through real entry points and revert-checked: every behaviour in "Tests".
  - Confirmed by reading and grep: every path in "Verify before building".
  - Confirmed by script: the counts. The scripts are kept with the evidence, outside the repository,
    at `~/Zynergy/device-evidence/2026-10-05-fix-provider/`; they read no positions into anything
    committed.
  - Inferred: that the network usually answers first for camera finds. That comes from
    `AndroidLocationProvider`'s own comment, not a measurement.
  - Not seen at all: anything on the S22 with this build.
- **Could not determine.**
  - What the S22 returns from `getLastKnownLocation(PASSIVE_PROVIDER)`: no log records it.
  - Whether `ForagerFixRule` fires on the S22 at the rate the logs suggest (4 in 1,969 on walks):
    that needs the device step.
- **Premises that were wrong.**
  - The dispatch's counts were log lines, not fixes.
  - "Agreed on 4,726 of 4,726" holds for the desk only; the walks disagree 4 times.
  - The dispatch's paths missed the one-shot `LocationProvider` path (camera finds) and the
    countdown's read of `lastGatedFix`.
  - **This verification step itself missed a reader.** The end waypoint, made when a recording stops,
    is built from `lastGatedFix` (`TrackRecordingViewModel.kt:594` at `4b72b25c`). It was found while
    the tests were being written. It takes GPS only under the dispatch's rule 2, since it saves a
    position. The planner accepted that, matching the owner's answer for the origin waypoint
    (RECORD -558).
  - The walk folders are `2026-10-03-walk-t21` and `2026-10-03-walk-zoom`.
  - My own first sweep of watch calls missed two (a method reference and a receiver call). The
    compiler found them.
- **Decided beyond scope.**
  - Test fixtures that already stood for network readings say network, not GPS: 9 of 47. Answer 3
    said fixtures say GPS. Those nine are refused by accuracy already, so the result in their tests
    is the same, and saying GPS would have mislabelled them.
  - The off-track judge's provider parameter is nullable, with `null` meaning a stored point. This is
    the dispatch's rule 4, and it lets replays of saved walks run unchanged; the planner agreed.
  - `ForagerFixRule`'s message format.
