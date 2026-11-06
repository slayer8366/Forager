# The way home along trails, part 2 (what the walker sees): report before building (dispatch 2026-10-24-10)

**Author:** Hazel (app coder). **Status:** a report only. No app code written, no CI run, no phone, no PR.
**Read at:** origin/pre-main `c83a06722` (#389 merged), checked with `git ls-remote` before reading and again before writing (2026-10-24 15:10 UTC). It matches the dispatch's base. Every `file:line` is at that commit; app paths are under `app/src/main/java/com/zynergylabs/forager/app/`.
**Read first:** CLAUDE.md; the modular-code skill; dispatch 2026-10-24-10 (records-after-173 `dbb51fe85`); RECORD -1586, -1589, -1592, -1595, -1597, -1598, -1600, -1603, -1611, -1612; Sorrel's two #389 reviews; my reports `2026-10-23-09-way-home-along-trails-report.md` (way-home-report) and `2026-10-24-way-home-parts-0-1-completion-report.md`.

## The behaviour, restated (owner-confirmed step path, -1612)

```
Recording, before Return
  → sundown's walk-back time uses your track (as today)
Tap Return
  → at once: your track back, as today
  → silently, the trail route is worked out (no spinner)
  → it swaps in when ready:
       ┄┄┄ your track (dotted) to where you left the trail
       ━━━ the trail (solid, same colour) to where you joined it
       ┄┄┄ your track (dotted) to your start
  → the HUD distance and sundown use the route shown, app-wide
Stray more than 50 m from the line → it re-routes once, silently
No route possible → your track, plus one note (the eight notes in WayHome.kt:58-87)
Arrive → "Arrived", with a ring at your start
```

Plus -1603: when the length check is skipped (100 m or less walked between the trail points), a route over a set length is not used and the track is shown (section 5, the cap).

**Two things this step path does not settle, which I need ruled before building** (both in section 10.4): how the HUD counts down along the trail part without nearest-point projection (section 2), and whether the work outlives the Activity (section 1).

---

## 1. The production call

**Today nothing reaches the routing code.** `git grep` in `app/src/main` at `c83a06722`: `FindWayHomeUseCase(` only at its declaration (`domain/wayhome/FindWayHomeUseCase.kt:51`); `BRouterTrailRouter(` and `BRouterTrailSnapper(` are never constructed; and `RoutingProfile.install` (`routing/RoutingProfile.kt:38`), which copies the profile the engine reads, has **no caller either**. Part 2 must install the profile (blocking file work, off the main thread) before the first route. That was not in the dispatch.

**Proposal: a process-held `WayHomeWatch`, beside `ReturnWatch` and `SundownWatch` in `AppContainer`** (`AppContainer.kt:389`, `:415`). New file `domain/wayhome/WayHomeWatch.kt`. It holds:
- the way home for one track;
- its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, built in `AppContainer` and injected so tests pass a `TestScope`;
- at most one job;
- a generation number.

It publishes a `StateFlow` of `(trackId, generation, result)`; a `null` result means still working.

**Why process-held, not `viewModelScope`.** -1589's "app-wide" sundown needs the route with the screen off. Sundown runs from the service (`service/TrackRecordingService.kt:361-363`), and the return outlives the Activity in `ReturnWatch` (`domain/ReturnWatch.kt:53-60`). My earlier report's W1 (section 6) is this. Its W2, the ViewModel's scope, gives sundown and the HUD different figures in exactly the screen-off case.

**Where Return reaches it.** The call is made from `TrackRecordingViewModel.startReturn()` (`ui/track/TrackRecordingViewModel.kt:723-745`), straight after `returnWatch.startReturn` succeeds (`:726`). That function is what the Return control calls (`MainActivity.kt:669`) and what a resumed waypoint pause calls (`ui/track/TrackRecordingReturnLeg.kt:15`). The call is `wayHomeWatch.find(trackId, origin)`. So the decision to start stays in a ViewModel function (-1450).

`find` does three things:
1. cancels any job in flight (`Job.cancel()`, which does not suspend, so the main thread never waits);
2. bumps the generation and clears the held result;
3. launches a job that reads the track through the read seam and runs `FindWayHomeUseCase`. The read is `trackRepository.getById`, as `SundownWatch` reads it (`AppContainer.kt:418`).

**Threads.**
- The watch's scope is on `Dispatchers.Default`.
- `runEngine` switches to the router's dispatcher (`routing/BRouterTrailRouter.kt:122`).
- The search runs on the `brouter-engine` thread (`:170`, `:208-210`).
- Nothing touches the main thread except the call to `find`.

**How each path cancels it.**

| Path | Call | Effect |
|---|---|---|
| Second Return (stop, then Return) | `stopReturn()` (`:771-776`) calls `wayHomeWatch.stop(trackId)`; then `startReturn` calls `find` | old job cancelled; new generation |
| Stop, from the app | `clearRecordingState()` (`:546-564`) calls `stop` | cancelled, result dropped |
| Stop from the notification, or the service destroyed | the service's `stopRecording` (`TrackRecordingService.kt:378-380`) and `onDestroy` (`:160-162`) call `wayHomeWatch.end(trackId)` / `end(null)`, beside the three watches already ended there | cancelled, result dropped |
| Waypoint pause | `ReturnLeg.pause()` → `stopReturn()` (`TrackRecordingReturnLeg.kt:14`) | cancelled; resume recomputes, which matches "the resumed return draws its line afresh" (`:5-6`) |
| Arrival | `ReturnWatch` ends the return itself (`ReturnWatch.kt:252-253`) | the held result is no longer read, because every reader is gated on `isReturning` (section 3). It is dropped at the next `find`, `stop` or `end`. |
| **The Activity going away** | none | **does not cancel, by design.** The return goes on in `ReturnWatch`, and sundown keeps reading the route. A reopened screen takes the return up (`:591-630`) and reads the held route at once, with no recompute. |

**The dispatch's premise "the Activity going away cancels it" is the W2 design.** I am asking for it to be ruled rather than building either (section 10.3).

**L-389-3, the cancel's latency.**
- A cancel returns at once to whoever called it.
- The cancelled job itself waits, outside cancellation, for the engine to stop (`BRouterTrailRouter.kt:173-176`). If the engine is opening a cold `.rd5` or matching waypoints, that wait lasts until the first pass of the search, because BRouter reads `terminated` only in the search (Sorrel's review, L-389-3).
- The one-route lock is held through that wait. So a second Return's first snap waits behind it. This is silent and shows only as a later swap.

**A cancelled job never publishes.** The result is published under the watch's lock, only if the generation is still the job's own, and after `ensureActive()`. A cancel is not logged: it is not a failure (`BRouterTrailRouter.kt:127-128`).

**Wiring the engine.** New file `routing/WayHomeRouting.kt`, about 70 lines:
- builds `BRouterTrailRouter`, `BRouterTrailSnapper(router, SNAP)` (section 6) and `FindWayHomeUseCase` once;
- runs `RoutingProfile.install` on first use, inside the job, on `Dispatchers.IO`.

If the install fails, the result is `TrackOnly(NO_ROUTE_FOUND)`, logged once with the exception. `AppContainer` gains about 20 lines.

## 2. The swap

**Today's chain** (all read):
- route tick: `TrackRecordingRouteTicks.updateRouteHome` (`ui/track/TrackRecordingRouteTicks.kt:83-96`), every 5 s while returning;
- → `RouteHome` and `nextRouteLine` (`domain/RouteLine.kt:26-34`) into `TrackRecordingUiState`;
- → `MainActivity.kt:672-673`: `returnRouteOf(routeHome)` and `routeLine`;
- → the HUD (`ui/availability/NavigationHudReadout.kt:107-111`, `NavigationReadout.kt:113`, `:123`) and the map (`CompactMapMapSlot.kt:84`, `ui/map/RouteHomeLayers.kt:101-113`).

**Proposed: the chain stays, and a held way home changes what goes into it.**

1. **The ViewModel copies the watch** into a new `TrackRecordingUiState.wayHome` field.
   - The copy is made only for its own active track, as `copySundownLine` does (`ui/track/TrackRecordingWatchCopies.kt:78-83`).
   - It is collected in `init` beside the other watches (`TrackRecordingViewModel.kt:357-361`).
   - On a new result it calls `routeTicks.updateRouteHome()` at once, so the swap does not wait up to 5 s for the tick.
2. **The tick chooses its line.**
   - With a held `AlongTrails`, it builds the `RouteHome.Ahead` from the way home (new `domain/wayhome/WayHomeAhead.kt`).
   - Otherwise it builds it from the track, as today (`routeHome`, `domain/RouteHome.kt:140`).
   - The lookahead and corner guard (`RouteHome.kt:162-174`) move unchanged into a shared function (new `domain/RouteLookahead.kt`), so both lines aim the needle the same way: 25 m ahead (`:70`), within 10 m of the line.
3. **Dotted and solid.**
   - `RouteHome.Ahead` and `RouteLine` gain one defaulted field: the parts of the line ahead, each a run of points with a kind (track or trail). Empty means one solid line, today's.
   - On the map, the parts become features of the same `route-home-ahead` source with a `part` property.
   - Two line layers (and two casings) filter on it. Trail parts are solid, as today's ahead layer is (`RouteHomeLayers.kt:156-161`). Track parts are dotted, with the same azure (`:75-76`) and a `line-dasharray`.
   - The dash pattern and whether the casing dashes too are judged on the phone, as provisional as `STRAIGHT_DASHES` (`:89`).
   - The track-only line stays solid, as "your track back, as today" asks.
4. **The greyed walked part.**
   - `atReturn` is taken from the first route only (`RouteLine.kt:29`). Left so, the track route at Return would stay under the trail line in grey, and its unwalked stretches would read as "passed".
   - So `RouteLine` records which generation its `atReturn` came from, and `nextRouteLine` replaces `atReturn` once when the generation changes: at the swap, and at a re-route.
   - After that, what the bright line no longer covers is passed, as today.
5. **Distance and needle along the shown line.** See "The open question" below. The HUD figure is `routeMeters` (`NavigationHudReadout.kt:109`), labelled "by trail" (`NavigationReadout.kt:123`). That label is now literally true.
6. **Arrival: no change.**
   - `ReturnWatch` ends the return on a GPS fix inside the arrival circle, after the walker has left it (`ReturnWatch.kt:287-295`).
   - The ring sits at the start (`RouteHomeLayers.kt:109-111`), and the line ahead ends there.
   - The way home's line ends at the origin waypoint (`FindWayHomeUseCase.kt:104-114`), the same point.

**No flash.**
- The swap is one `_uiState.update` that sets `routeHome`, `routeLine` and `wayHome` together.
- The dotted and solid parts sit in one source, so the line ahead changes in one `setGeoJson` (`RouteHomeLayers.kt:190-192`). It cannot show half old and half new.
- Before the swap the line is today's track line, so there is never an empty frame.
- **Unverified:** whether MapLibre can draw a frame between the ahead and passed sources' updates. `setGeoJson` may parse off the main thread. The worst case is the grey line a frame late under the bright one. This is device-only (section 8).

### The open question: where the walker is on a trail line

Today the walker is placed at the track's **most recent stored point**, "never by a nearest-point projection" (owner ruling, `domain/PathHome.kt:15-24`). The reason: projection's error "can be short", and short is the direction that "gets someone caught out after dark".

A trail part is not the walker's track, so their stored points never lie on it. "Once, plus if you stray" (-1589) rules out the periodic re-route (my R2), which would have avoided the question. So the HUD needs a rule for the walker's place on the shown line.

| Option | What it does | Short? |
|---|---|---|
| **A (recommended)** | Place = the earliest vertex of the line, in walking order and not before the last place, within 25 m (`WAY_HOME_TRAIL_JOIN_METERS`, `WayHome.kt:110`) of the last GPS fix. If none is ahead, the earliest anywhere on the line. 25 to 50 m off: placed with the hop counted. Over 50 m: withheld, as the far hop band does today (`RouteHome.kt:147`). Remaining = line metres from the place, plus the counted hop (`PathHome.kt:123-135`'s bands). | Never short by more than 25 m. "Earliest" means a switchback leg further on is never chosen over the leg the walker is on. Turning back regresses the place once nothing ahead is near. |
| B | The way-home line joined to the walker's growing track at the 10 m self-join ε (`domain/TrackSelfJoin.kt:32`), shortest route from the most recent stored point. | Never short, but under canopy the walker's points often sit more than 10 m from OSM geometry (median 8.3 m, p90 16 m on roads, my report section 2). Then the route runs back along their own return track and the needle points the way they came. |
| C | Nearest-point projection. | Against the owner ruling. |
| D | Re-route every K s on the trail part (my earlier R2). | Not what -1589 chose. |

A is new placement logic in a safety input, so I would not build it without the owner's ruling (10.4). New file `domain/wayhome/WayHomeProgress.kt`, about 150 lines, pure.

## 3. Sundown, app-wide

**Where the walk-back figure is read today.**
- One production caller of `returnWalkingTime`: `SundownWatch.kt:289`, through `pathHome` over the stored track (`ReturnWalkingTime.kt:66`, `:87`).
- `BackByWatch` reads no walk-back figure. Rechecked: `AppContainer.kt:430-439` passes it no walking-time function.
- The HUD's figure is the route tick's, not sundown's.

**Where the "start back by" text comes from.** It is sundown's own (`SundownWatch` header, "What the screen reads from it"). So the figure on screen and the alert move together.

**Proposed.**
- `SundownWatch` gains one constructor input, `wayHomeFor: (trackId) -> WayHomeProgress?`, wired in `AppContainer` as:
  ```
  { id -> wayHomeWatch.progressFor(id)?.takeIf { returnWatch.state.value.let { it.trackId == id && it.isReturning } } }
  ```
- That lambda is the "Track until Return" rule. Before Return, while the route is still being worked out, on a track-only fallback, after arrival, and off the line, it gives `null`, and `:289` runs as today.
- With a placed way home, `:289` calls a new `returnWalkingTimeAlong(remainingMeters, hopBand, track, freshness)` in `ReturnWalkingTime.kt`. It keeps every degrade and withhold rule (`:64-82`), reading the track for pace and the track-quality reasons as today. Only the distance changes.
- The decision rules, `DecideSundownAlertUseCase` (`:302`), and the alert moments are not touched.
- **Where the progress comes from.** `WayHomeWatch` works out the walker's place on each GPS fix the service already hands the watches: a fourth call in `service/RecordingWatches.kt` beside `returnOnFix` (`:18`). So the HUD and sundown read one placement.
- **Off the line, logged.** When the walker is more than 50 m off the line, sundown falls back to the track figure. That fallback is logged once per stray (CLAUDE.md).

## 4. Re-routing

**How "more than 50 m from the line" is judged.**
- The distance is the straight line from a GPS fix to the nearest segment of the whole shown way-home line.
- It uses a GPS fix within the live-fix gate (`acceptLiveFix`, `domain/LiveFixGate.kt:80`: GPS only, reported accuracy 50 m or better), from the service's stream.
- It is judged in `WayHomeWatch`, only while `ReturnWatch` says this track is returning.
- 50 m is the step path's figure and the far hop band's (`PathHome.kt:170`). The re-arm figure is 45 m (`:173`), so a walker on the edge does not flicker.

**What a stray does.**
1. One re-route. The same job as Return's, on the track as now stored (so the stray is in it), with the origin given at Return.
2. The leave scan runs newest point first (`FindWayHomeUseCase.kt:75`). So the new line goes back along the stray to the last trail the walker was on, then home.
3. The line shown stays until the new one swaps in. This is silent, with no flash, as in section 2.

**What stops a loop.** Three things, each provisional:
1. **One job at a time.** A stray judged while a job runs starts nothing.
2. **Once per stray.** After a re-route, the next stray counts only once a GPS fix has been within 45 m of the line shown, new or old.
   - The stored track lags the walker by 27 to 40 m (`RouteHome.kt:97-100`), so a walker still heading away may stay more than 45 m from the new line. They then get no second re-route.
   - The HUD withholds ("Unable to calculate route", Try again, `NavigationReadout.kt:114`), and sundown uses the track (section 3). That is the honest state, not a loop.
3. **A fallback ends re-routing for this return.** If a re-route gives the track, the way home is the track, which always runs through the walker's newest point, and there is no trail line to stray from. The next Return tap tries again.

A suppressed stray is logged once, so a walker who "never re-routed" can be told apart from one who never strayed.

## 5. The notes

**Where they show.** In the navigation display's status line, in the state where that line is empty today. That state is a route ahead, a fresh fix, not approaching, not arrived (`NavigationReadout.kt:167-185`, the `else -> ""` at `:182`).
- The note rides on `ReturnRoute.Ahead`: `returnRouteOf(routeHome, note)`, the same line at `MainActivity.kt:672`.
- `navigationReadout` is the one builder for the portrait and landscape cards and the approximate-position HUD (`ApproximatePositionHud.kt:43`), so all of them get it.
- A stale or lost fix, "Approaching" and "Arrived" keep the line, as their rules say (`:168-180`).
- A note shows while the track is the way home for that reason, and it clears when a trail route swaps in or the return ends.
- **Short forms**, when the whole note does not fit (`NavigationHudFit`):
  - each "· following your track" note drops that tail;
  - "This route leaves your downloaded area. Download a larger area to route there." needs a short form. "Route leaves your downloaded area" would be new wording, so it is the owner's (10.4).
  - A note gets no empty form, so it is never dropped silently.
- `AvailabilityScreen.kt` (1,023 lines) and `AvailabilityViewModel.kt` (761) do not change. The note travels inside `ReturnRoute` (`AvailabilityScreenNavigation.kt:51`), and `AvailabilityViewModel` holds only the `ReturnLeg` (`:155`).

**Every fallback is logged once.**

| Fallback | Where it is logged | Note |
|---|---|---|
| The eight cases of `WayHomeResult.TrackOnly` | `FindWayHomeUseCase.trackOnly`, one `errorLog.w` per call, no positions (`:181-184`) | each case's note |
| The -1603 cap (below) | the same function | proposed: none |
| Profile install failed (section 1) | `WayHomeWatch` | `NO_ROUTE_FOUND` |
| The job threw something unexpected | `WayHomeWatch`, with the exception | `NO_ROUTE_FOUND` |
| Re-route suppressed by the loop stop (section 4) | `WayHomeWatch`, once per stray | none (the line stays) |
| Sundown off the line uses the track (section 3) | `WayHomeWatch` or `SundownWatch`, once per stray | none |

A cancel is not logged.

**The -1603 cap (provisional).**
- **Rule:** when the check is skipped (`trackBetween` at most 100 m, `FindWayHomeUseCase.kt:97`), a trail part over **150 m** is not used.
- **Why 150:** it is `WAY_HOME_DETOUR_RATIO × WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS` (1.5 × 100, `WayHome.kt:119`, `:133`). That is the longest trail part the ratio check accepts just over its 100 m floor, so the rule is continuous at 100 m and adds no new free number.
- **What it keeps:** the cases the floor was built for all fit under it. Sorrel's crossing is 50 to 80 m (M-389-2), the test crossing is 66.2 m, and the looped shortcut is 80 m (`FindWayHomeShortWalkTest`, Sorrel's recheck).
- **Which note:** I propose **none**, `TrackOnly(null)`, logged once.
  - The walker's own track between the points is 100 m or less: a short, known way. Nothing in it is for them to act on.
  - "Leaves your downloaded area" would be false, the mirror of M-389-2.
  - "Couldn't find a trail route" would also be false: one was found and refused.
  - This follows -1597's and -1598's no-note cases. The owner confirms (10.4).
- Code: about 8 lines in `FindWayHomeUseCase.kt` and a named constant with its reason in `WayHome.kt`.

## 6. Snap or full route: the timing run

SNAP is the provisional choice (L-389-1). FULL_ROUTE fails beyond 250 m from any way (`TrailJoins.kt:91`, Sorrel's L-389-1). Part 2 wires SNAP only (`WayHomeRouting.kt`). FULL_ROUTE stays behind the interface until the timing says otherwise.

**The run, at build time, with the phone the planner hands over:**
- **The build.** A throwaway branch, `way-home-timing`, cut from part 2's head and never merged.
  - CI builds its debug APK, which carries the routing key (`.github/workflows/ci.yml:116-119`).
  - It adds a debug-only broadcast receiver that runs `FindWayHomeUseCase` on a saved track by both methods in turn and logs the figures. It needs no new screen and does not touch pre-main.
  - Part 2 adds no migration, so its schema equals pre-main's. The S22's schema is compared before installing, per the S22 wipe of 2026-10-21 (-1526).
- **Walks to run it on:**
  1. the fixture-like walk: a saved walk that leaves a trail inside a downloaded area with routes (the device walk in section 8);
  2. L-389-5's long walk with no trail near: a long saved woods walk inside a downloaded area. If none exists, the S22's longest saved track with a region downloaded over it.
- **Figures to record, per method and walk:**
  - stored points;
  - points tested in each scan;
  - engine calls;
  - per-call ms (first call cold, then min, median and max), split by outcome (on a trail, nothing in range, other);
  - total wall time;
  - the result kind and metres;
  - `dumpsys meminfo` before and after;
  - the cancel latency: a second broadcast mid-scan, timed from cancel to the job's end.
- **Battery:** not measurable in a run this short. Recorded as not determined.
- **Decision rule** (as my completion report put it): ship SNAP unless FULL_ROUTE is clearly cheaper. FULL_ROUTE's 250 m failure counts against it whatever the timing.

## 7. The off-track alert

**Part 2 does not change it.** `ReturnWatch`, `OffTrackJudge` and their call sites are not in the file list (section 9). `WayHomeWatch` reads `ReturnWatch.state` and changes nothing in it.

**The false alert part 2 will give, for the step 7 dispatch to cite:**
- At Return, `ReturnWatch.startReturn` snapshots the kept points as the path (`domain/ReturnWatch.kt:187`).
- The judge measures every GPS fix against the start plus that path (`:255`). The limit is `OFF_TRACK_LINE_METERS = 40.0` plus the fix's reported accuracy (`domain/OffTrackJudge.kt:7-8`), held `OFF_TRACK_HOLD_MILLIS = 15_000` with at least 3 readings (`:11`, `:14`).
- So a walker following the solid trail part where it runs more than about 40 m from their outbound track, for 15 s, gets the OFF_TRACK alert: a notification and vibration (`ReturnWatch.kt:262-268`, which respects silence and the Settings checkbox). The Return control turns red (`ui/availability/TrailheadControls.kt:182`). They get this for following the app's own line.
- The same holds after a re-route, for the stretch along the walker's stray.
- Every pre-main debug build from part 2's merge until step 7 shows this. The S22 device steps expect it, and do not file it as a bug.

## 8. The test plan

Each forward test fails first against pre-main. The failure is read from that run's own JUnit XML, after the build log shows zero `e:` lines.
- Each revert is a one-line edit on its own branch, built from a saved copy, never restored from git. The forward change is confirmed present afterwards (`git diff --stat`, SHA-1 against the saved copy).
- A revert's failure is cited only if it is one that edit can produce.
- New test classes, not growth in `TrackRecordingViewModelTest.kt` (1,505 lines).

**Domain (headless).**

| Class | Holds | Revert |
|---|---|---|
| `FindWayHomeCapTest` | 149/151 m at 98 m walked; 102 m walked still runs the ratio; one log | delete the cap line: the 151 m case gets a route |
| `WayHomeProgressTest` (if A is ruled) | hairpin with legs 20 m apart: place stays on leg 1 (never ahead); turned back over 25 m: place regresses; 25 to 50 m: hop counted; over 50 m: withheld | "earliest" → "nearest": the hairpin case comes out short |
| `WayHomeAheadTest` | lookahead and corner guard on a joined line; the dotted/solid runs from the place; metres as the parts' sum | parts all one kind: the run assertion fails |
| `RouteLookahead` move | `RouteHomeTest` unchanged and green | none: a pure move. Sorrel checks the diff. |
| `WayHomeWatchTest` (TestScope, fake router that suspends until cancelled) | second `find` cancels the first and the first never publishes; `stop`, `end(id)` and `end(null)` cancel; publish only for the current generation; stray over 50 m re-routes once; no second re-route until within 45 m; one job at a time; a fallback ends re-routing; each suppression logged once | drop the generation check: the stale result is published |
| `RouteLine` swap | `atReturn` replaced once per generation, kept within one | keep the first `atReturn`: the grey line is the old track |
| `ReturnWalkingTimeAlongTest` + a `SundownWatch` case | before Return: the track figure; with a placed way home while returning: remaining ÷ pace; track-only or off the line: the track figure, with one log | gate the lambda on `arrivalJudgedFor` instead of `isReturning`: the after-arrival case changes |

**The real entry point.**
- **New `TrackRecordingWayHomeTest`, using the class's own `runRecordingTest` shape** (stops every recording in a `finally`; the CLAUDE.md stall rule, because the route tick is an unbounded loop).
  - It drives `vm.startReturn()`, the function `MainActivity.kt:669` calls on the Return tap. It uses a real `WayHomeWatch` on the test dispatcher and the real `FindWayHomeUseCase` over `WayHomeFakes`.
  - It asserts on `uiState`:
    - the track line at once;
    - after the job, the dotted/solid parts, `routeMeters` and `ReturnRoute`;
    - each fallback's note text and the track line;
    - a second Return giving a new generation with no stale publish;
    - Stop clearing everything.
  - Revert: the tick ignores the held way home. The parts assertion fails, naming the missing trail part.
- **Screen.**
  - `RouteHomeLayersTest` gains: the ahead source's features carry `part`, and both filters see their own runs.
  - A new `AvailabilityScreenWayHomeNoteTest` asserts the status line's note text at 360 and 384 dp, and the short form where the whole note does not fit.
  - Today no test composes the real `TrackRecordingViewModel` into the screen. The mapping lives in `MainActivity.kt:662-676`. Whether to add that rig is the planner's call. Without it, the Return tap's last hop (`onToggleReturning`) stays covered by the existing screen tests (`MapBarPressFeedbackTest.kt:104`).
- **Service.** `TrackRecordingServiceTest` gains: each raw fix reaches the way-home watch, guarded as the others are (`TrackRecordingService.kt:222`), and Stop and destroy end it.

**Device (S22, CI debug APK, the planner's handover):**
1. Schema check first. Then record a walk in a downloaded area with routes that leaves a trail, and tap Return. Check:
   - the track at once;
   - the swap with no flash: a screen recording, read frame by frame;
   - dotted and solid in one colour;
   - the HUD distance against the line;
   - the needle along the trail;
   - the grey passed part;
   - "Arrived" and the ring.
2. Stray more than 50 m off the trail part. Check one silent re-route and its log line. Return to within 45 m, stray again, and check a second re-route.
3. Screen off during the return. Sundown's "start back by" moves with the trail figure (read from the strip on unlock, and from the log).
4. Each reachable note:
   - outside any area: "No trail routes here";
   - a region whose routes are deleted: "not downloaded";
   - a region downloading: "Getting trail routes…";
   - no trail near: "No trail on your way out".
5. Expect the off-track false alert (section 7) on any trail part away from the track. Note it; do not file it.

Walks 1 to 3 need real trails. Whether mock locations can stand in is unverified.

## 9. Size and PRs

Line counts are `wc -l` at `c83a06722`. The "after" figures are my estimates: inferred, not measured.

| File | Now | After | What |
|---|---|---|---|
| `ui/track/TrackRecordingViewModel.kt` | 852 | ~864 | wiring only: constructor input, one collector, `find` in `startReturn`, `stop` in `stopReturn` and `clearRecordingState`, the tick's getter (-1611) |
| `ui/track/TrackRecordingRouteTicks.kt` | 106 | ~135 | chooses the held way home or the track |
| `ui/track/TrackRecordingWatchCopies.kt` | 102 | ~120 | `copyWayHome` |
| `ui/track/TrackRecordingUiState.kt` | 192 | ~205 | the `wayHome` field and note |
| `TrackRecordingViewModelFactory.kt` | 41 | 42 | one input |
| `AppContainer.kt` | 478 | ~500 | the watch, the routing, the sundown lambda |
| `service/RecordingWatches.kt` | 32 | ~37 | `wayHomeOnFix` |
| `service/TrackRecordingService.kt` | 552 | ~558 | one guarded call; `end` on stop and destroy |
| `domain/SundownWatch.kt` | 441 | ~452 | one input; the figure at `:289` |
| `domain/ReturnWalkingTime.kt` | 123 | ~165 | `returnWalkingTimeAlong` |
| `domain/RouteHome.kt` | 202 | ~185 | lookahead moved out; one defaulted field |
| `domain/RouteLine.kt` | 34 | ~60 | parts; `atReturn` per generation |
| `domain/wayhome/FindWayHomeUseCase.kt` | 192 | ~202 | the cap |
| `domain/wayhome/WayHome.kt` | 141 | ~158 | the cap's constant and reason |
| `ui/map/RouteHomeLayers.kt` | 230 | ~290 | `part` property, dotted layer and casing |
| `ui/availability/NavigationHudReadout.kt` | 111 | ~118 | the note on `ReturnRoute.Ahead` |
| `ui/availability/NavigationReadout.kt` | 235 | ~248 | the note in the status line and its short form |
| `MainActivity.kt` | 736 | 736 | `:672` passes the note, on the same line |
| new `domain/wayhome/WayHomeWatch.kt` | — | ~230 | job, generation, stray, loop stops |
| new `domain/wayhome/WayHomeProgress.kt` | — | ~150 | place, remaining, distance to the line |
| new `domain/wayhome/WayHomeAhead.kt` | — | ~110 | `RouteHome.Ahead` and parts from a way home |
| new `domain/RouteLookahead.kt` | — | ~60 | the lookahead and corner guard, moved |
| new `routing/WayHomeRouting.kt` | — | ~70 | the engine, the snapper (SNAP), the profile install |

- **Not touched:**
  - `AvailabilityViewModel.kt` (761), `AvailabilityScreen.kt` (1,023), `ReturnWatch.kt`, `OffTrackJudge.kt`, `BackByWatch.kt`;
  - waypoint navigation, the Worker, route download, the camera.
- **Limits:** no file passes 800 except `TrackRecordingViewModel.kt`, already over and held by -1611. Its additions are wiring only. Every new file is under 400 lines.
- **One file shared with another coder: none known.** The dev-rebase session works only in its own worktree.

**PRs, each into pre-main, merged in order, no switch (-1612):**
1. **PR A, the cap (-1603).** About 20 lines of domain code plus its test. Invisible, and small enough to review alone. It changes `FindWayHomeUseCase`, which nothing calls yet.
2. **PR B, the domain.**
   - Contents: `WayHomeWatch`, `WayHomeProgress`, `WayHomeAhead`, the `RouteLookahead` move, `RouteLine`, `returnWalkingTimeAlong`, `WayHomeRouting`, with their tests.
   - Still invisible: no production caller until C.
   - Why separate: the placement rule and the loop stops are the safety logic. Sorrel can review them apart from wiring, as #389 was.
   - It is blocked on the owner's ruling on section 2's placement.
3. **PR C, visible.**
   - Contents: `AppContainer`, the service, the ViewModel and its collaborators, the map layers, the HUD note, and sundown's input.
   - With: the entry-point and screen tests, then the S22 steps.
   - Why separate: C's phone check stays on what the walker sees.

Plus the throwaway `way-home-timing` branch (section 6), never merged.

The split matches #388/#389's, which reviewed cleanly.

## 10. The four disclosure sections

### 10.1 Confirmed vs inferred

**Confirmed by reading at `c83a06722`:**
- no production caller of `FindWayHomeUseCase`, `BRouterTrailRouter`, `BRouterTrailSnapper` or `RoutingProfile.install` (git grep);
- the route chain from tick to map and HUD, and where it starts and stops (section 2's lines);
- `SundownWatch.kt:289` is the only walk-back reader;
- the off-track judge's inputs and figures (section 7);
- `AvailabilityViewModel` holds only the `ReturnLeg`;
- `navigationReadout` is the one status builder;
- CI's debug APK has the routing key, and CI builds no androidTest APK (`ci.yml:120`, `:191`);
- the line counts in section 9.

**Inferred, not run:**
- every "after" size;
- that `Job.cancel()` plus the generation check never lets a stale result publish (to be pinned by `WayHomeWatchTest`);
- option A's "never short by more than 25 m";
- the per-fix cost of placement (a few thousand vertex distances, under a millisecond);
- that MapLibre draws no mixed frame;
- that a 150 m cap keeps every real crossing.

### 10.2 Could not determine

- The S22 cost of SNAP and FULL_ROUTE, and of a long no-trail scan (L-389-5). This needs the timing run.
- Battery, for any of this.
- How far OSM trails sit from GPS under canopy. That decides whether A's 25 m holds and whether B could work.
- Whether a saved long woods walk exists inside a downloaded area on the S22.
- Whether mock locations could stand in for the walks.

### 10.3 Premises that were wrong, or need correcting

- **"The Activity going away cancels it" (dispatch, section 1).** Under "app-wide" (-1589) the work has to outlive the Activity, as `ReturnWatch` and `SundownWatch` do. A cancel on Activity loss is the W2 design and leaves screen-off sundown on the track. I propose that it does not cancel. To be ruled.
- **"Once, plus if you stray" settles the routing, not the counting.** With no periodic re-route and no projection, the HUD needs a placement rule on the trail line that no ruling covers (section 2).
- **Part 2 is not only wiring the use case.** `RoutingProfile.install` has no caller, so part 2 also installs the engine's profile.
- **`TrackRecordingUiState.kt:174-176` says `returnWalkingTime` "still has no caller".** That is stale: `SundownWatch.kt:289` calls it. It is a doc fix in PR C.

### 10.4 Decided beyond scope, or left for the owner

Nothing is built. These are proposals, each needing a word before building:
1. **Placement on the trail line:** A (recommended), B, or another rule. It touches the "never short" ruling.
2. **Process-held work** that survives the Activity, versus `viewModelScope`.
3. **The cap at 150 m** (1.5 × 100) with **no note**.
4. **The stray judge:**
   - GPS within the live-fix gate;
   - 50 m to enter and 45 m to re-arm;
   - one job at a time;
   - once per stray;
   - a fallback ends re-routing for the return.
5. **Note placement:** the status line, and a short form for the leaves-area sentence ("Route leaves your downloaded area", new wording).
6. **The track-only line stays solid**, and only a trail way home draws its track parts dotted.
7. **Off the line by more than 50 m, sundown uses the track figure**, logged once.
8. **The timing run on a throwaway branch** with a debug-only broadcast receiver, never merged.
9. **A new test rig** composing the real ViewModel into the screen, or not (planner's call).
