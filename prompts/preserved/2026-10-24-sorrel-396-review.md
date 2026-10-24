# Sorrel's review of PR #396: way home part 2, PR C (what the walker sees)

**Reviewer:** Sorrel (Claude Fable 5.1, read from the reviewer's own environment). **Planner:** Rowan. **Coder:** Hazel (Opus).
**PR:** #396, `way-home-part2-visible`, head `18e038a9c` (the dispatch named `a45a52a77` or newer; since then `f3bef5cb9` added one test file, `SundownWatchWayHomeTest.kt`, and two merges from `way-home-part2-domain` brought in PR B's `WayHomeAhead.kt` needle fix, `82da028e2`, and its `returnWalkingTimeAlong` no-points withhold, `bedb6d809`, both reviewed under #395), into `way-home-part2-domain` `bedb6d809` (#395). The base is an ancestor of the head. PR C's own main-source diff against its base is byte-identical at `a45a52a77`, `e00a20557` and `18e038a9c` (`md5sum` of each: `54b9cff5…`): 14 main files, 6 test files. `origin/pre-main` is `c83a06722`, the base the dispatch named, unchanged. The head moved three times while this review was written; every claim below was re-checked against `18e038a9c`.
**Read:** CLAUDE.md; dispatch `prompts/preserved/2026-10-24-10.md` with Amendment 1 (records-after-173); RECORD -1586, -1589, -1595, -1597, -1612, -1613, -1614; Hazel's report `docs/audits/2026-10-24-10-way-home-part-2-report.md` (way-home-part2-report `818974203`); the PR body; every file in the diff, and the PR B files it calls (`WayHomeWatch.kt`, `WayHomeProgress.kt`, `WayHomeAhead.kt`, `WayHomeRouting.kt`, `RouteLine.kt`, `ReturnWalkingTime.kt`); the fail-first run's JUnit XML and build log, read from the API. Nothing run locally; no phone; no code changed. Every `file:line` below was checked by `grep -n` against the head's sources; app paths are under `app/src/main/java/com/zynergylabs/forager/app/`, tests under `app/src/test/java/com/zynergylabs/forager/app/`.
**Rules for this read:** the dispatch asked for a hike-fitness verdict; the owner hiked before this review finished (Rowan), so that verdict is moot and only merge-readiness is given, plus what the S22 device check should look at. What could mislead a walker was still weighed first.

## Verdict

**Merge-ready: yes, with the L items tidied first and M-396-1 answered by the owner.** The code does what the step path (-1612) and Amendment 1 say; the fail-first run and five revert checks each bite for the reason their edit predicts, on builds that compiled; the full suite is green on the head (5,527 tests, run 40123967847); `TrackRecordingViewModel.kt` holds at 864 with wiring only; `AvailabilityViewModel.kt` and `AvailabilityScreen.kt` are untouched. Two conditions on sequence rather than code: #395 merges first and this PR is retargeted to pre-main (L-396-9), and the S22 device check (below) runs before or with the merge, since the dotted layer's drawing and the swap's frame are device-only. Nothing in this PR is Blocking.

## 1. The step path, read against the code

| Step (-1612) | Where | Holds? |
|---|---|---|
| Before Return, sundown uses the track | `AppContainer.kt:449`: `wayHomeFor` returns a `PathHome` only from `wayHomeWatch.progressFor(id)`, which is `null` unless `isReturning(id)` (`WayHomeWatch.kt:116-117`). `SundownWatch.kt:296-297` falls to `returnWalkingTime` on `null`. | Yes |
| Tap Return → at once, your track back | `TrackRecordingViewModel.kt:748` calls `wayHomeWatch.find` after `returnWatch.startReturn` succeeds; `find` publishes `WayHomeShown(id, gen, result = null)` (`WayHomeWatch.kt:165`); with no line the tick runs `findRouteHome(track, …)` as before (`TrackRecordingRouteTicks.kt:101-107`). | Yes |
| Silently worked out, no spinner | Nothing in the diff draws a spinner or a "working" state; the `null` result is invisible (`TrackRecordingUiState.kt:106`). | Yes |
| Swaps in when ready | Collector at `TrackRecordingViewModel.kt:368`: `copyWayHome()` then `routeTicks.updateRouteHome()` on each publish, so the swap does not wait for the 5 s tick. The line comes from `wayHomeAhead` (`WayHomeAhead.kt:26-42`). | Yes |
| Dotted / solid / dotted, one colour | `aheadCollection` emits one feature per run with `part` = track/trail (`RouteHomeLayers.kt:144-152`); the solid layer and its casing filter `part != "track"`, the dotted layer `part == "track"` with `line-dasharray [0, 2]` and round caps, the same colour and width, no casing (`:182-183`, `:196-199`, `AHEAD_DOTS` at `:104`). For the track home there is no `part` property; `neq(get(part), "track")` is true for a missing property, so it draws solid as before. | Yes in the data; the drawing is device-only (MapLibre is not in Robolectric) |
| HUD distance and sundown from the route shown | HUD: `routeMeters = place.remainingMeters` (`WayHomeAhead.kt:34`), the 25 m trail allowance included (`WayHomeProgress.kt:131`). Sundown: `returnWalkingTimeAlong(track, path, freshness)` over the same `WayHomePlace` (`SundownWatch.kt:297`; `ReturnWalkingTime.kt:77-81`). Both read one placement, `WayHomeWatch.progress`, set from the service's raw fixes (`TrackRecordingService.kt:239` → `RecordingWatches.kt:34` → `WayHomeWatch.onFix`, `:122`). | Yes |
| Stray over 50 m → one silent re-route | `judgeStray` (`WayHomeWatch.kt:139-158`): over 50 m (`HOP_FAR_ENTER_ABOVE_METERS`, `PathHome.kt:170`) launches a job if armed, no job is running, and no re-route has fallen back; re-arms under 45 m (`:140`). During the re-route the old `WayHomeShown` stays (only `firstOfReturn` publishes a `null` result, `:165`). | Yes |
| No route → your track plus one note | `wayHomeNote = (wayHome.result as? TrackOnly)?.note` (`TrackRecordingUiState.kt:189`) → `returnRouteOf(routeHome, note)` (`MainActivity.kt:672`) → the status line, in the state that was empty (`NavigationReadout.kt:183`). The eight texts in `WayHome.kt:58-87` match the dispatch's list word for word. Short forms drop " · following your track"; the leaves-area short form is "Route leaves your downloaded area" (`NavigationHudText.kt:66-73`), the owner's words (-1613). | Yes |
| Arrive → "Arrived", with a ring | Not changed by this PR. `ReturnWatch` ends the return; `isReturning` false → `progressFor` null → sundown back to the track; `copyFromWatch` ends the tick (`TrackRecordingViewModel.kt:445-447`). The `arrived` branch of the status line precedes the note (`NavigationReadout.kt:174`). | Yes |

**Order of the status line** (`NavigationReadout.kt:167-185`): lost fix, stale fix, arrived, approaching, pending/withheld route's straight-line note, then the way-home note. So a fallback note is hidden while the route is pending or withheld, and under a stale fix. That is the rule the report proposed (§5) and the dispatch accepted; noted as L-396-3 only so the device steps expect it.

## 2. Flash, wrong line, stale route

- **Swap:** one `_uiState.update` sets `routeHome` and `routeLine` together (`TrackRecordingRouteTicks.kt:110`); `nextRouteLine` replaces `atReturn` once when `generation` changes (`RouteLine.kt:41-44`; the track's generation is 0, `RouteHome.kt:46`; a way home's is the watch's, never 0). So the grey line becomes the way home at the swap and at a re-route, and the track again after a fallback. Before the swap the frame holds the track line; there is no empty frame. Whether MapLibre can draw the passed and ahead sources a frame apart is unverified here, as in Hazel's report §2; device-only.
- **Stop:** `stopReturn` → `wayHomeWatch.stop` and `routeTicks.end()`, which nulls `routeHome` and `routeLine` (`TrackRecordingViewModel.kt:785-787`; `TrackRecordingRouteTicks.kt:66-69`). `clearRecordingState` → `stop` (`:564`) and `wayHome = null` in the one update (`:571`). The service's `stopRecording` and `onDestroy` call `end` (`TrackRecordingService.kt:384`, `:163`).
- **Second Return:** `stopReturn` then `startReturn` → `find` cancels the job in flight and bumps the generation (`WayHomeWatch.kt:163-164`); the cancelled job cannot publish (`ensureActive()` at `:175`, then the generation check at `:177`). `TrackRecordingWayHomeTest` pins the cancel and the new line (`:164-184`).
- **Waypoint pause:** `TrackRecordingReturnLeg.pause()` is `stopReturn()`, resume is `startReturn()` (`TrackRecordingReturnLeg.kt:14-15`), so both are the paths above.
- **A reopened screen:** `copyFromWatch` calls `copyWayHome()` (`TrackRecordingViewModel.kt:441`), so a recreated Activity reads the held way home at once, as -1613 asked; `copyWayHome` keeps it only for this screen's own track (`TrackRecordingWatchCopies.kt:89-94`).
- **Arrival:** `wayHome` stays in `uiState` after arrival (nothing clears it until the next `find`/`stop`/`end`). Nothing reads it then: the tick has ended, the status line is in its arrived state, and `progressFor` is `null`. Not a defect; recorded so nobody reads a stale `wayHome` field as a bug.

## 3. Touch

This PR adds **no composable**. Its visible changes are MapLibre style layers (`RouteHomeLayers.kt`), which are not in the Compose tree, and text in the status line's existing slot. So CLAUDE.md's `Surface` pitfall has nothing new to bite on.

Amendment 1 item 6 asked for a rig and a coordinate-touch test. `AvailabilityScreenWayHomeRigTest.kt` composes the real `TrackRecordingViewModel` into the real `AvailabilityScreen` wired as `MainActivity` wires it (`:121-173`), taps Return by `performTouchInput { click(centre) }` on the pill's measured centre (`:188-190`), asserts the map slot was handed `[TRACK, TRAIL, TRACK]` (`:194-198`), then long-presses at up to 16 real points (two rows, 6 dp and 20 dp below the HUD's measured bottom, 8 across; `:208`), skipping any point inside a clickable node (`:204`), requiring at least 8 sampled (`:221`, `MIN_SAMPLED` at `:236`) and each to reach the map's `onLongPress` (`:217`). That is a coordinate touch, sampled at several points, as the amendment and CLAUDE.md ask.

Two limits, stated so the test is not read as more than it is: the map slot is a test `Box`, so the way-home **layers are not drawn** (the amendment's "with the way-home layers drawn" cannot be met in Robolectric; what is asserted is the data handed to the slot); and the touches are below the HUD only, not across the map. Both are fine for this PR, which adds no composable; L-396-4.

## 4. The service and the process-held watch

- `WayHomeWatch` is built in `AppContainer` on `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (`AppContainer.kt:425-433`), beside `ReturnWatch` and `SundownWatch`.
- **Screen off / Activity gone:** the service's collector hands every raw fix to `wayHomeOnFix` under `guardWatch` (`TrackRecordingService.kt:239`; `RecordingWatches.kt:34`); `onFix` places the walker and judges the stray (`WayHomeWatch.kt:122-136`); sundown's tick runs from the service and reads `progressFor`. Nothing in this path needs the Activity. The Activity going away calls nothing on the watch (confirmed: `MainActivity.kt` changes one line, `:672`).
- **Stop cancels:** `stop(trackId)` and `end(trackId?)` both `forget()`: cancel the job, bump the generation, null `shown` (`WayHomeWatch.kt:104-113`, `:208-217`). Called from `stopReturn`, `clearRecordingState`, the service's `stopRecording` and `onDestroy`.
- **Locks:** `WayHomeWatch.lock` is taken by the service thread (`onFix`), main (`find`/`stop`), the job's end, and sundown's tick (`progressFor`). Inside it the only outside read is `returnWatch.state.value`, a `StateFlow`, no lock. No nested watch lock, so no ordering to get wrong.
- **Install:** `WayHomeRouting.find` installs the profile once per process on IO under a `Mutex`; a failed install returns `TrackOnly(NO_ROUTE_FOUND)`, logged, and the next Return tries again (`WayHomeRouting.kt:41-56`).

## 5. Logging

Every fallback has one log line and none carries a position:
- the eight `TrackOnly` cases and the cap: `FindWayHomeUseCase.trackOnly` (`:186-187`), with region ids and metres, no coordinates (PR B's code; `data.square` at `:157` is a route-data square index, not a fix);
- read failure / missing track: `WayHomeWatch.kt:191`, `:194`, track id only;
- unexpected exception in the job: `:172`;
- off the line (sundown back to the track): once, on the transition into `OffLine` (`:131-133`);
- stray not re-routed: once per reason until it changes (`:153`);
- profile install failed: `WayHomeRouting.kt:49`.
A cancel is not logged, by design (`WayHomeWatch.kt:169-170`). `WayHomeRouting`'s install failure is logged on each Return that retries it, which is once per attempt, not once per process; acceptable, and the report said so.

## 6. Sizes and untouched files

`wc -l` at `f3bef5cb9`: `TrackRecordingViewModel.kt` **864** (852 + 12: an import, a constructor input, two lambda lines, one collector line, one `copyWayHome()` call, two `stop` calls, one `find` call, `wayHome = null` in the clear update; wiring only, as -1611 requires). New files: `AvailabilityScreenWayHomeRigTest.kt` 238, `TrackRecordingWayHomeTest.kt` 190, `WayHomeTestRecording.kt` 96, `SundownWatchWayHomeTest.kt` 78, `RouteHomeWayHomeLayersTest.kt` 48, `NavigationWayHomeNoteTest.kt` 47; all under 400. `AvailabilityViewModel.kt` (761) and `AvailabilityScreen.kt` (1,023) have an empty diff against the base. Other sizes match the PR body (`AppContainer.kt` 502, `SundownWatch.kt` 450, `TrackRecordingService.kt` 556, `RouteHomeLayers.kt` 273, `NavigationReadout.kt` 239, `MainActivity.kt` 736).

## 7. Tests

**Real entry points.** `TrackRecordingWayHomeTest` drives `startReturn()`, `stopReturn()` and `stopRecording()`, the functions `MainActivity.kt:669` and the return leg call, with a real `ReturnWatch` and a real `WayHomeWatch` whose search the test answers (`:94-108`); recordings are stopped in a `finally` (`:78-84`, the stall rule). The rig taps the real pill by coordinates. `SundownWatchWayHomeTest` goes through `SundownWatch.begin`/`onFix`/`tick` and reads the shown start-back time, with a figure the test first proves differs from the track's by over a minute (`:67-68`). `NavigationWayHomeNoteTest` goes through `navigationReadout` and covers all eight notes and the stale-fix precedence. `RouteHomeWayHomeLayersTest` asserts the `part` properties and the coordinates of each feature.

**Fail first, run 40108513307** on `650f989f1` (`way-home-part2-visible-failfirst`, one commit on `a45a52a77`), read from the API:
- the build log has no Kotlin `e:` compile lines (the 175 matches of `e: ` are inside words such as "Range: "; none begins `e: file`), and `BUILD SUCCESSFUL` for the APK before the test task;
- the test task ran a filtered set: "12 tests completed, 6 failed";
- the three reverts and the six failures, each one its revert can produce:
  - `find` not called (`TrackRecordingViewModel.kt:748`): `TrackRecordingWayHomeTest` × 3 ("the first search was cancelled expected:<1> but was:<0>"; "List is empty" at `answers.single()`; "expected:<1> but was:<0>" at `answers.size`), and the rig ("the map is handed the way home, dotted and solid expected:<[TRACK, TRAIL, TRACK]> but was:<null>");
  - `aheadCollection` always one feature (`RouteHomeLayers.kt:145`, `|| true`): `RouteHomeWayHomeLayersTest` ("expected:<[track, trail, track]> but was:<[null]>");
  - status line ignores the note (`NavigationReadout.kt:183`): `NavigationWayHomeNoteTest` ("expected:<[No trail routes here · following your track]> but was:<[]>").
  A filtered run of the new classes, not the whole suite, which is enough for fail-first. `SundownWatchWayHomeTest` was added after it (`f3bef5cb9`) and has no fail-first evidence (L-396-5). Run 40108331519, which the PR body says did not compile, is not cited here either.

**Green run and revert checks.** See "CI" below.

**Not covered by a unit test**, as the PR body says: the service's `wayHomeOnFix` call (the service tests replace the watches), and MapLibre's drawing of the dotted layer. Both device-only.

**Revert checks for C:** five, read under "CI" below. All compiled, all failed for their own reason, all forward lines present on the head.

## Findings

No Blocking.

- **M-396-1 — Off the line, the HUD withholds for the rest of the return; only coming back within 45 m restores it.** `WayHomeWatch.judgeStray` (`WayHomeWatch.kt:139-158`) re-routes once per stray and not while a re-route has fallen back; `placeOnWayHome` returns `OffLine` past 50 m (`WayHomeProgress.kt:124`, `:127`) and `wayHomeAhead` turns that into `Withheld(OFF_ROUTE)` (`WayHomeAhead.kt:27`), "Unable to calculate route" with Try again, where Try again (`TrackRecordingViewModel.kt:795-797`) recomputes against the same `OffLine`. The tick never falls back to the walker's own track while an `AlongTrails` result is shown (`TrackRecordingRouteTicks.kt:101-107`). So a walker who strays twice and keeps going sees no distance until they return to within 45 m of the line, where today's track route would still give one. Sundown does fall back to the track (`AppContainer.kt:449` gives `null` on `OffLine`). This is the behaviour Hazel's report §4 described ("the honest state, not a loop") and the owner accepted with "Accept all" (-1613), so it is **not a defect against the dispatch**; it is graded M because it is a state the walker can read as broken ("Don't make me think") and it is a regression in availability against the track route. For the owner: is this what you want, or should the HUD show the track figure, as sundown does, while off the line?
- **M-396-2 — Sundown's hop-band hysteresis is frozen while the way home is used.** `SundownWatch.kt:318` updates `hopBand` only when `alongWayHome == null`. While the way home is in use the stored band is the one from before the swap; when the walker goes `OffLine` and sundown returns to `returnWalkingTime(track, …, band)`, it starts from that stale band. Small (the band re-settles on the next tick), and the comment at `:317` says it is deliberate. Say so in the completion report, or carry the track's band on each tick anyway.
- **L-396-3 — A fallback note is hidden while the route is pending or withheld, and under a stale fix** (`NavigationReadout.kt:167-185`). By the report's rule; the device steps should expect "Unable to calculate route" and no note in the far band.
- **L-396-4 — The rig's touch test covers the strip below the HUD with a fake map slot** (`AvailabilityScreenWayHomeRigTest.kt:117-119`, `:202-222`): nothing is drawn, so the amendment's "with the way-home layers drawn" is met in data, not in pixels, and only the two rows under the HUD are touched. Enough for a PR that adds no composable; not evidence about MapLibre or the rest of the map.
- **L-396-5 — `SundownWatchWayHomeTest` (`f3bef5cb9`) was added after the fail-first run.** Closed in substance by revert check c5 (run 40109056481), which reverts `SundownWatch.kt:296` and fails exactly its along case with the track's figure. Recorded, no action.
- **L-396-6 — Unused imports in `TrackRecordingWayHomeTest.kt`** (`:3-45`: `AbandonedTrackSweepOnce`, `AlertAudibility`, `AlertAudibilityState`, `CreateWaypointUseCase`, `DeleteTrackUseCase`, `DeleteWaypointUseCase`, `EndAbandonedTracksUseCase`, `GetTrackOriginWaypointUseCase`, `GetTracksUseCase`, `GetWaypointsUseCase`, `InMemoryKeptTrackPaths`, `LocationTracker`, `RingerMode`, `StartTrackUseCase`, `TrackRepository`, `WaypointRepository`, `Track`, `TrackPointRecord`, `Waypoint`, `ZoneOffset`, `Flow`, `MutableSharedFlow`, left from before the factory moved to `WayHomeTestRecording.kt`). Compiles; tidy before merge.
- **L-396-7 — The PR's code commit is named "wip"** (`f7148e452`). Disclosed in the PR body and left by push-before-you-tidy; the merge commit's message should carry the real description.
- **L-396-8 — `WayHomeRouting` logs the install failure on every Return that retries it** (`WayHomeRouting.kt:49`), not once per process. Matches the report's "the next Return tries again"; once per attempt is defensible. No change asked.
- **L-396-9 — Base.** The PR targets `way-home-part2-domain`. After #395 merges, retarget to pre-main before merging, so the merge commit lands where the dispatch says.

## What the S22 device check should look at

Hazel's report §8 lists the device steps; these are the ones this review adds or sharpens, each because a unit test cannot see it.

1. **The dotted layer itself.** `line-dasharray [0, 2]` with round caps (`RouteHomeLayers.kt:104`, `:196-199`) is meant to draw dots one width long, two apart. Check it reads as dots, not a faint or broken line, in day and night; and that the casing-less dotted run meets the cased solid run cleanly at the joints (a dot sits on the solid line's end, `WayHomeAhead.kt` shares the joining node).
2. **The swap, frame by frame.** A screen recording through the swap and through a re-route: the passed (grey) and ahead sources are two `setGeoJson` calls, and whether MapLibre ever shows one updated and the other not is unverified (section 2).
3. **Off the line (M-396-1).** Stray over 50 m from the trail part once: one re-route, one log line. Stray again without coming back within 45 m: expect "Unable to calculate route" with Try again doing nothing new, and sundown's start-back time still moving (from the track). Confirm that is what shows, so the owner can rule on it with the screen in front of them.
4. **The note's precedence (L-396-3).** With a track-only result, let the fix go stale (stand still with the phone covered for over a minute): the stale-fix line should replace the note, and the note should come back when the fix is fresh.
5. **Screen off.** With a trail way home shown, screen off for a few minutes while walking the trail part, then unlock: sundown's "start back by" should have moved with the trail figure (the service feeds `WayHomeWatch.onFix` and reads `progressFor`; no unit test covers the service's call).
6. **The off-track false alert** while on the trail part more than about 40 m from the outbound track for 15 s (report §7; -1614). Expected until step 7; note it, do not file it.
7. **The HUD figure on the trail part** is at least 25 m over (the never-short allowance). Check it never reads *under* a paced-out distance.
8. **Trail routes need an offline area downloaded on a build from #383 on** (-1614); without one the notes are the only visible change.

## What I did not check

- The MapLibre drawing: whether `[0, 2]` with round caps renders as dots on the S26, how the casing-less dotted run reads over the solid run's casing, and whether the passed and ahead sources ever draw a frame apart. Device only.
- Any device behaviour: the S22 check in the dispatch, the timing run, battery, the engine's real latency on a cold `.rd5`.
- PR B's domain logic (`WayHomeWatch`, `WayHomeProgress`, `WayHomeAhead`, the cap) beyond what PR C's wiring calls. That is #395's review, running in another Sorrel session.
- Nothing was run locally; the suite's state on the head is run 40123967847's, read from its artifact.
- Hazel's revert checks for A and B: #394's and #395's reviews. Only C's five are read here.
- Whether `TrackRecordingServiceTest`'s fakes of `RecordingWatches` still cover every call now that `wayHomeOnFix` exists (the PR body says the service's fix call is not covered; taken as read).

## CI

**Green run 40123967847 on `18e038a9c`** (created 19:09 UTC, read from the API at 19:35 UTC): every job `success` (Build, test, publish APK; Android lint (debug); Release compiles and packages; Route-data cutter; Tile Worker). Its `unit-test-report` artifact: **5,527 tests, 0 failures, 0 errors**, counted from the JUnit XML, not the summary line. The runs on `a45a52a77` (40108552109), `f3bef5cb9` (40109026168) and `e00a20557` (40109484404) were cancelled by the pushes that followed and are not cited.

**Revert checks c1 to c5**, branches `way-home-c-revert-c1…c5`, each one commit on `f3bef5cb9`, each a filtered run of the new classes (10 tests). For each: the test job's own log (jobs API) has `BUILD SUCCESSFUL` for the APK and no `e: file` line, so the reverted build compiled and the results are its own; the failure message is one that edit predicts and the others would not produce; and the forward line is present on `18e038a9c` (grep, line given).

| Check | Edit | Run | Failures | Forward line on head |
|---|---|---|---|---|
| c1 | `find` not called (`TrackRecordingViewModel.kt`) | 40109038127 | `TrackRecordingWayHomeTest` × 3 ("the first search was cancelled expected:<1> but was:<0>"; "List is empty" at `answers.single()`; `answers.size` "expected:<1> but was:<0>") and the rig ("…dotted and solid expected:<[TRACK, TRAIL, TRACK]> but was:<null>") | `:748` |
| c2 | `aheadCollection` always one feature (`RouteHomeLayers.kt`, `\|\| true`) | 40109042678 | `RouteHomeWayHomeLayersTest` ("expected:<[track, trail, track]> but was:<[null]>") | `:145` |
| c3 | status line ignores the note (`NavigationReadout.kt`) | 40109046862 | `NavigationWayHomeNoteTest` ("expected:<[No trail routes here · following your track]> but was:<[]>") | `:183` |
| c4 | the tick never uses the way home (`TrackRecordingRouteTicks.kt`, `if (false && …)`) | 40109050495 | `TrackRecordingWayHomeTest` swap ("expected:<[TRACK, TRAIL, TRACK]> but was:<[]>") and the rig (same, `<[]>`) | `:103` |
| c5 | sundown never reads the way home (`SundownWatch.kt`, `alongWayHome = null`) | 40109056481 | `SundownWatchWayHomeTest` along case ("1791073460741 within a second of 1791072160449": the track's start-back time, 22 min from the along one); the "with none" case passes, as it should | `:296` |

c4 is the check that tells `find` from the tick: c1's rig failure is `null` (no route line at all, because nothing after the first tick recomputes), c4's is `[]` (a line, with no parts). c5 closes L-396-5 below for the along case. The fail-first run 40108513307 is described in section 7.
