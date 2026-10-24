# Way home parts 0 and 1: completion report (dispatch 2026-10-23-09 Amendment 1)

**Coder:** Hazel (Opus). **Spec:** RECORD -1589; the go is RECORD -1590. **Base:** origin/pre-main `adcbedb19`. Pre-main was checked against the remote before starting, and again before this report; it had not moved.

**Nothing visible changes.** No production code calls the new code yet. That comes in part 2, which ships with step 7.

## What landed

| PR | Branch | Commit | What it does |
|---|---|---|---|
| #388 | `way-home-split` | `7629d5874` | Part 0: a pure-move split of `TrackRecordingViewModel.kt` |
| #389 | `way-home-domain` | `956c133d9` | Part 1, failing first: the tests, against stubs |
| #389 | `way-home-domain` | `425972bbc` | Part 1: the code |

- **Branches and state:** both branches are pushed and their trees are clean. Both PRs are open into pre-main, mergeable and green.
- **Stacking:** #389 is cut from `way-home-split`, so its diff includes part 0 until #388 merges.
- **Merges:** merging waits for Sorrel's review and the owner's word.

## Part 0: what moved, and why

`TrackRecordingViewModel.kt` went from 958 to **852 lines**.

**What moved:**
- **`TrackRecordingLocationFixes.kt`** (new, 140 lines): the live-fix collection and `lastGatedGpsFix`.
  - This is the one self-contained job the ViewModel still ran inline. It follows the pattern the Phase 2.7 split used for the poll and the route tick (-1439).
  - `beginLocationTracking()` stays a ViewModel function, so the ViewModel still decides when the collection starts and stops (-1450).
- **`TrackRecordingRowReads.kt`** (new, 76 lines): the two reads of a track's own row, for take-up and for the resync. Their log lines are unchanged.
  - What follows from each answer stays in `takeUpRunningRecording` and `resyncRecordingState`.
- **The docs that explain the moved code went with it.** The "why breadcrumbs are polled" section went to `TrackRecordingPoller.kt`.

**The file is still over the ~800-line limit.** What is left is mostly the start and stop decision functions: record, take-up, return and resync. RECORD -1450 keeps those in the ViewModel. Getting under 800 would mean moving one of them, and that is the planner's call.

## Part 1: what was built

**The cancel fix (#382 M1), in `routing/BRouterTrailRouter.kt`.** When a route is cancelled, the router:
1. terminates the engine;
2. waits, non-cancellably, until the engine has stopped;
3. only then releases the one-route lock and rethrows the cancellation.

A cancel logs nothing. The blocking search now runs on the router's own single engine thread, so a cancel can be heard even when the caller's dispatcher has only one thread. The engine is still built on the router's dispatcher. The report suggested `invokeOnCancellation`, but that only exists inside `suspendCancellableCoroutine`; this is the public-API equivalent.

**A trail snap behind one interface, in two forms** (`domain/routing/TrailSnapper.kt`, `routing/BRouterTrailSnapper.kt`):
- `SNAP` routes from the point to itself.
- `FULL_ROUTE` routes from the point to a second point.

Both read the first point of the route the engine returns. Before building, I checked what the release jar does when a route's two ends are the same point. It returns no error and one point: the matched point on the trail. This was checked on the laptop JVM, which is not Gradle. So the snap needs only BRouter's public result. BRouter's own single-point modes keep the matched point in protected or local fields, so they could not be used.

**The way home, in `domain/wayhome/`** (no Android):
- `WayHome` and its three parts:
  - the walker's track back to the leave point;
  - the trail route, with straight joints at each end of at most 25 m;
  - the walker's track from the entry point back to the start, then on to the origin waypoint.
- **Choosing the routes:** the region used is the one covering the track's first point (`routeDataForPoint`).
- **Scanning for the trail:**
  - the entry scan runs oldest point first, the leave scan newest first;
  - points are tested every 20 m along the track (provisional);
  - only points inside the region's rectangle are tested;
  - a point counts as on a trail within `WAY_HOME_TRAIL_JOIN_METERS = 25.0` (provisional).
- **Checking the route:**
  - every point of the trail route must lie inside the rectangle;
  - the trail route must be at most `WAY_HOME_DETOUR_RATIO = 1.5` (provisional) times the walker's own track between the same two points.
- **The notes:** five, in -1589's wording.
- **Logging:** every fallback is logged once per call. The log gives the reason in words and never a position.

**Not touched:** the map, the HUD, sundown, the alert rules, the camera, waypoint navigation, the Worker, and PR #386's files.

## Evidence

**Part 0:**
- **Full suite** (#388, run 40062777044): 720 suites, 5,412 tests, 0 failures, 24 skipped. That is identical to pre-main `adcbedb19` (run 40054316928).
- **A green pure move proves little on its own,** so I checked that the suite actually exercises the moved code, filtered to `ui.track.*` and `service.*`:
  - **Check a:** I removed the moved `returnToStart(point)` call (run 40064335384). Two `TrackRecordingViewModelTest` cases failed: "expected:<180.0> but was:<-1.0>".
  - **Check b:** I made `rowSaysEnded` never answer yes (run 40064339092). Two `TrackRecordingServiceTest` stop-from-the-shade cases failed, on their own messages.
  - Both builds compiled with no `e:` lines.

**Part 1, failing first** (run 40063662761, on `956c133d9`):
- The build compiled with no `e:` lines. It ran 48 tests and 30 failed, which is every new test.
- The cancel test failed on "the cancelled route's engine was terminated".
- The other 29 failed on `NotImplementedError` from the stubs.

**Part 1, full suite** (#389, run 40064180614): 724 suites, 5,442 tests, 0 failures, 24 skipped. That is 30 more tests than pre-main.

**Revert checks.** Each was a one-line edit on its own branch, cut from `425972bbc`. Each run's log was checked for `e:` lines before its results were read, and none had any. Every failure is one that its revert could produce:

| # | Edit | Result |
|---|---|---|
| 1 | Delete `engine.terminate()` | The cancel test fails: "the cancelled route's engine was terminated" |
| 2 | Any matched trail counts as near, whatever the distance | 10 failures, all from the join radius: leave and entry points move off the trail, and never-near walks route |
| 3 | No length check | Only the 1.5× test fails: expected `LEAVES_AREA`, got a way home |
| 4 | No rectangle check on the route | Only the outside-the-rectangle test fails |
| 5 | Far end without the track | Only the origin test fails, on its list of points |
| 6 | No fallback log | 14 fallback tests fail on "logged once: [] expected:<1>" |
| 7 | Test points outside the rectangle | Only "the point 3 km north … was not tested" fails |

`git rev-parse` confirms the forward branch is still `425972bbc` after the checks. The revert branches (`way-home-revert-1` to `-7`, `way-home-split-check-a` and `-b`) are left on the remote, not deleted.

**What the tests hold:**
- `BRouterTrailRouterCancelTest`: a real `RoutingEngine` built from the real request, with only its search replaced by a wait that ends on terminate. After the cancel, the engine is terminated, the cancel returns within 2 s, the next real route succeeds, and nothing is logged.
- `BRouterTrailSnapperTest`, on the real engine and fixture. Both methods:
  - put a trail node within 1 m of itself;
  - match the same off-trail point to the same trail point, 34.3 m away;
  - fail a point with nothing in range as `EndOutsideData(START)`;
  - agree with the start of the router's own route.
- `FindWayHomeOnBRouterTest`, on the real engine:
  - a walk out along the fixture's trail, then 200 m west, gives the same `WayHome` by both methods: track back to the turn, trail to the first point, a trail length within ±20% of the distance walked out;
  - a walk never near a trail gives `NEVER_NEAR_TRAIL` by both methods.
- `FindWayHomeUseCaseTest` (21 tests, on fakes):
  - the arithmetic of the parts and joints;
  - the newest leave point wins;
  - 24.9 m counts as on the trail and 25.5 m does not;
  - every region state and every router and snapper failure maps to its note;
  - both area checks, with the 1.5× boundary tested at 749 m and 751 m;
  - one log per fallback.

## Findings for the planner

1. **The split stopped at 852 lines.** See part 0.
2. **`FULL_ROUTE` needs a point it can route to.**
   - The entry scan routes toward the walker's most recent point. If that point is more than 250 m from any way, every `FULL_ROUTE` test fails as `EndOutsideData(END)`, and the result is `NO_ROUTE_FOUND`. `SNAP` has no such dependency.
   - This counts against `FULL_ROUTE` alongside the timing.
3. **"No trail routes here" now covers routes that were never fetched, are being fetched, or are missing.** That is -1589's "missing data", applied as written. But `RegionRouteData.NotFetched`'s own doc says it is "never presented as 'no routes here'" (`domain/routedata/RegionRouteData.kt:23-25`). The owner should confirm which holds.
4. **The length check can fire falsely on a very short track.** If the walker's track between the two points is a few metres, the joints alone (up to 50 m) can make the trail route more than 1.5× longer. The walker would then see "leaves your downloaded area" when it doesn't. Nothing guards against this: no data shows the case, so I didn't build logic for it. It should be watched on a walk.
5. **The figures are provisional and untuned:** 25 m, 1.5×, and the 20 m test spacing.
6. **The router's threading contract changed.** The engine's search now runs on its own thread, not on the dispatcher it is given. The existing threading tests still pass unchanged. Sorrel should look at this.

## Not tested

- **No phone.** The S22 timing of `SNAP` against `FULL_ROUTE` is still to do, and it decides which method ships (-1589). It waits for the planner to hand over the phone. The only timing I have is from the laptop JVM, one warm run each, which is not a measurement: about 4 ms for a snap and 37 ms for a full route.
- **Real tracks.** Nothing here has run on a real recorded track or under canopy, so whether 25 m fits real trail geometry is untested.
- **Cancelling during waypoint matching or file reads.** BRouter does not check the terminate flag there, so a cancel then waits until the next pass of the search. That is not tested.
- **Production wiring.** None of this is wired into the app yet, which is by design. Part 2 brings the entry-point tests through the Return tap.

## Device items (for the S22, when it is handed over)

1. **Time both methods.** Run each one over a recorded off-trail walk inside a downloaded area with routes. Note the total scan time and the number of engine calls for each.
   - If `SNAP` is clearly cheaper, ship `SNAP`.
   - If they are close, the `FULL_ROUTE` limitation in finding 2 still favours `SNAP`.
2. **The rest waits for part 2:** the dotted and solid line, the HUD distance and arrival, as the dispatch lists them.

## Addendum: Amendment 2 (RECORD -1592, "Two honest notes")

This supersedes finding 3 above. **Finding 3 is closed:** each kind of missing route data now gets a true note, which keeps the rule at `RegionRouteData.kt:23-25`.

| Region's route data | Note |
|---|---|
| Never fetched (`NotFetched`) | "Trail routes not downloaded · following your track" |
| Fetched and failed (`Missing`) | "Trail routes not downloaded · following your track" |
| Being fetched (`Fetching`) | "Getting trail routes… · following your track" |
| No downloaded region covers the start | "No trail routes here · following your track" |
| The server has none for the area (`NoneHere`) | "No trail routes here · following your track" |
| The build lacks the start's square | "No trail routes here · following your track" |

**One reading of the ruling for the owner to confirm.** The ruling names never-fetched routes, not failed fetches. I gave a failed fetch "not downloaded" too, because a failed fetch also means the routes are not on the phone. That is not the same as there being none.

**One unreachable case.** Stored routes reported as not stored can't happen through `routeDataForPoint`. If it ever does, it reads as unreadable routes and is logged.

**Commits:**
- `303ff5233`: the two notes and the four tests, failing first.
- `860a69e38`: the mapping.

**Evidence:**
- **Failing first** (run 40071226588): it compiled cleanly. The suite ran 24 tests and 3 failed. Never-fetched, fetching and fetch-failed each expected their new note and got `NO_TRAIL_ROUTES`.
- **Revert checks** (cut from `860a69e38`; both compiled cleanly):
  - Run 40071638715 mapped never-fetched back to the old note. Only the never-fetched test failed.
  - Run 40071644561 mapped fetching back to the old note. Only the fetching test failed.
- **Full suite** (#389, run 40071638070): 724 suites, 5,445 tests, 0 failures, 24 skipped. The combined old test was split into four.

**The split at 852 lines** is accepted for now, and will be revisited when part 2 adds to the file.

## Addendum: Amendment 3 (RECORD -1597, -1598; Sorrel's review of #389)

Three fixes to #389, from Sorrel's review (`prompts/preserved/2026-10-24-sorrel-388-389-review.md`, records-after-173 932fdd0f1). The owner's go is RECORD -1598.

**Base.** origin/pre-main was `073fc77bd` when I fetched, which matches the dispatch. I merged it into `way-home-domain` as `92811e301`, with no conflicts and no rebase. That merge's own PR run (40078416754) was cancelled when I pushed the fixes, because a newer push to the branch cancels the run in flight. The full-suite run below is on a head that includes the merge.

**Commits:**
- `92811e301`: merge of origin/pre-main `073fc77bd`.
- `0f74300c0`: the tests, failing first.
- `6ed43928a`: fixes 2 and 3, plus the two optional Lows.
- This addendum.

### Fix 1, M-389-1: the cancel test now pins the wait for the engine to stop (test only)

`BRouterTrailRouterCancelTest` follows Sorrel's shape. Once terminated, the held search keeps going for `STOPPING_MILLIS = 300` ms and then sets `stopped` as its last act (`:64-65`). Straight after `cancelAndJoin()`, the test asserts that `stopped` is true and that the cancel took at least 300 ms (`:82-83`). The existing checks still apply: the engine was terminated, the cancel took under 2 s, the next route is found, and nothing is logged. No production code changed.

- **Failing first:** not possible as a forward run, because the join is already in the code. The new assertion passed in the failing-first run, as expected. Its evidence is the revert check.
- **Revert check 1** (`way-home-a3-revert-1`, run 40078813878): I deleted the `withContext(NonCancellable) { run.join() }` line (`BRouterTrailRouter.kt:175`). The test failed on "the cancel waited for the engine to stop before it returned", which is the new assertion and the only failure. The earlier assertions held, as they should: `terminate()` still runs, and the next route still waits its turn on the single engine thread.

### Fix 2, M-389-2: a trail part under 100 m gives the track home with no note

`WAY_HOME_SHORT_TRAIL_METERS = 100.0` is provisional and named (`WayHome.kt:131`). Its doc gives the reason and the source of the figure (RECORD -1598, the planner's figure). In `FindWayHomeUseCase.kt:95-97`, when `trailMeters` (the engine route plus both joints) is under 100 m, the result is `TrackOnly(null)`, logged once as "no note". The check runs after the rectangle check and before the 1.5× check.

New test class `FindWayHomeShortTrailPartTest` (90 lines, 4 tests):
- **A walk that crosses a trail:** it runs south to north, with the first and last near points 20 m either side of the trail. The trail part is 25 m of route plus about 41.2 m of joints, 66.2 m in all, which is over 1.5× the 40 m of track. Result: `TrackOnly(null)`, not `LEAVES_AREA`, and the router was asked once.
- **The joints count:** 60 m of route plus the joints comes to about 101.2 m, so the length check runs and returns `LEAVES_AREA`.
- **Both sides of the boundary,** with no joints and 30 m of track: 99 m gives `TrackOnly(null)`, 100 m gives `LEAVES_AREA` (the check is "shorter than", so exactly 100 m is not short), and so does 101 m.
- **The order:** a short trail part outside the rectangle still gives `LEAVES_AREA`.

Evidence:
- **Failing first** (run 40078652887): the 99 m test and the crossing test failed, each "expected:<TrackOnly(note=null)> but was:<TrackOnly(note=LEAVES_AREA)>". The other two passed, as they should today: they guard behaviour the fix must keep.
- **Revert check 2** (`way-home-a3-revert-2`, run 40078816050): `if (false && trailMeters < …)`, so the check never runs. Exactly those two tests failed, with the same messages.
- **Revert check 3** (`way-home-a3-revert-3`, run 40078817546): the check measures `route.lengthMetres` alone, without the joints. Only "the joints count toward the short length" failed: "expected:<TrackOnly(note=LEAVES_AREA)> but was:<TrackOnly(note=null)>".

### Fix 3, L-389-2: a failed fetch that only an app update can fix says so

New note `ROUTES_NEED_APP_UPDATE`: "Trail routes need an app update · following your track" (`WayHome.kt:72`). `Missing(reason)` maps to it when `reason.needsAppUpdate()` is true (`FindWayHomeUseCase.kt:142-147`). That is the same function the Offline maps row uses (`RegionRouteData.kt:152-153`, `OfflineRegionRouteLine.kt:34`), so the two can't drift apart. It is true for `NO_APP_KEY` and `FORBIDDEN` only. Every other `Missing` reason stays "Trail routes not downloaded · following your track" (-1595).

**Checked against -1589 and the row before building:**
- -1589's list of notes has no app-update case, so the new note adds to the list and replaces nothing. -1597 is the ruling that adds it.
- The row reads "Map only. Trail routes need an app update." (`OfflineRegionRouteLine.kt:66`). The new note uses the same words, "Trail routes need an app update", in the shape of the other notes.
- I found no clash.

**Tests** (`FindWayHomeUseCaseTest`):
- The old fetch-failed test listed `NO_APP_KEY` as "not downloaded". It now loops over every `RouteDataMissingReason` except the two and expects "not downloaded" for each.
- A new test expects the app-update text for `NO_APP_KEY` and for `FORBIDDEN`, one log each. It compares the note's text, not the enum constant, so the failing-first build compiled without the new constant.

Evidence:
- **Failing first** (run 40078652887): the new test failed on `NO_APP_KEY`: expected "Trail routes n[eed an app update]…", got "Trail routes n[ot downloaded]…". The loop over the other reasons passed, which is correct today. It is a guard, not a fail-first test.
- **Revert check 4** (`way-home-a3-revert-4`, run 40078819190): `if (false)` in place of `needsAppUpdate()`. Only the new test failed, with the same message.

### The optional Lows

- **L-389-4:** a two-line comment above "the engine runs on the router's own thread" (`BRouterTrailRouterTest.kt`). It says the test now proves where the engine is *built*, and that the search runs on `brouter-engine`.
- **L-389-8:** a comment on `trackPart`. Every caller passes `from >= to`, so the reversed branch is not reached. It is kept so the function does what its name says.
- I left the other Lows alone, as the dispatch said.

### How the runs were read

- **Failing first:** run 40078652887, on `0f74300c0`. I pushed it to the throwaway branch `way-home-a3-failfirst` and ran it filtered to `BRouterTrailRouterCancelTest` and `domain.wayhome.*`, so the merge's run on `way-home-domain` would not be cancelled. That run was cancelled later anyway (see Base).
- **Revert checks:** each revert branch differs from the forward commit `6ed43928a` by exactly its one line. I checked this with `git diff 6ed43928a origin/way-home-a3-revert-N`. The branches were made one on top of the next, but each diff against the forward commit is that single edit.
- **Compile errors:** every run's build log had zero `e:` lines, and each run compiled its tests (`compileDebugUnitTestKotlin`) before running them.
- **Results:** read from that run's own JUnit XML, inside its `unit-test-report` artifact. Each CI run is a fresh checkout, so no result could be left over from an earlier run.
- **Counts:**
  - Failing first: 30 tests, 3 failures.
  - Revert 1: 1 test, 1 failure.
  - Reverts 2 to 4: 29 tests each, failing 2, 1 and 1.
  - The cancel class runs alone in revert 1, which is why its count is 1.
- **Restoring the files:** I copied both edited files to `/tmp/a3-save` before the first revert edit and restored them from those copies after each one. Afterwards the files' SHA-1s matched the saved copies, `HEAD` and `origin/way-home-domain` were both `6ed43928a`, and a grep found `run.join()`, the short check and the `needsAppUpdate()` mapping all present.
- The revert branches are left on the remote, not deleted.

**Full suite** (#389, run 40079203529, on `6ed43928a`): `success`, every job. 731 suites, 5,485 tests, 0 failures, 24 skipped, counted from the run's JUnit XML; zero `e:` lines in its log. Pre-main `073fc77bd` (run 40078250926) is 726 suites, 5,447 tests, 0 failures, 24 skipped. The difference, 38 tests, is #389's 33 from before plus this amendment's 5 (the four in `FindWayHomeShortTrailPartTest` and the app-update test).

### Finding for the owner: a real trail shortcut under 100 m is now dropped

Fix 2 is built as ruled. One consequence the ruling's framing ("the track home with no note") does not cover:
- A walker crosses a trail at A, bushwhacks a 2 km loop, and crosses the same trail again at B, 80 m along the trail from A.
- The trail part B→A is 80 m. Before this fix it passed the 1.5× check (80 m against 2 km of track) and was a 1.9 km shortcut.
- Now it is under 100 m, so the way home is the 2 km loop, with no note.
- The case needs the walker's *first* contact with any routable way to be at A. A walk that starts at a trailhead or on a road has its entry point there, its trail part is long, and the rule does not fire.

So it bites only when the walk starts off the network. That is plausible for a forager, but I have no data on how often it happens. I did not guard against it: no walk shows it, and the ruling is explicit. The owner's option (b), applying the ratio only when the track between the points is over N m, would not drop this case. I'm reporting it so the owner can weigh it before part 2 wires the use case. No production code calls it yet (`git grep "FindWayHomeUseCase("` in `app/src/main` finds only its own declaration; its doc says so at `FindWayHomeUseCase.kt:28-29`).

### Confirmed vs inferred

**Confirmed** (read, or run on CI):
- the base, `073fc77bd`;
- the merge, conflict-free;
- the three fixes as above;
- each failing-first and revert result, from that run's XML, with zero `e:` lines;
- the forward change present after the revert checks;
- the note wording against -1589, -1592, -1597 and `OfflineRegionRouteLine.kt:66`;
- that `needsAppUpdate()` is the row's own predicate.

**Inferred** (not run):
- That a joint never exceeds 25 m on the real engine. Each joint runs from a track point within 25 m of its *snap* to the first or last point of a *separate* route request. I take it that BRouter matches the same point the same way both times, which `BRouterTrailSnapperTest`'s "the snap is where the router's own route starts" supports on the fixture.
- So Sorrel's bound for a crossing, up to 30 m of route plus 50 m of joints, about 80 m, stays under 100 m.

### Could not determine

- How often a true trail part under 100 m is a real shortcut (the finding above), or how often a crossing trips the 1.5× check on a real walk. No real walk has been run.
- The S22 timing, and the cost of a long scan with no trail on it (L-389-5). The phone is not mine in this stage.

### Premises that were wrong

- **None in the dispatch.** The base, the file locations, `needsAppUpdate()`, the row's wording and -1589's notes all matched the code.
- **One nuance on the geometry behind 100 m.** By my arithmetic, a *straight* crossing with ideal matching does not trip the 1.5× check: the trail part is about (cos θ + sin θ) times the track between the points, at most 1.41×. The false alarm comes from walking *beside* a trail within 25 m, from a wiggling track, or from an engine route longer than the straight line between the two matched points. The crossing test therefore uses an engine route of 25 m between points 10 m apart, standing for a trail that bends at the crossing. The fix is still needed, and the 100 m figure still covers Sorrel's 50 to 80 m. This changes the example, not the ruling.

### Decided beyond scope

- **Where the short check sits.** It runs after the rectangle check, not before it. The dispatch says "before the 1.5× check", so a short route outside the cut data still reads `LEAVES_AREA`. A test pins this order.
- **"Shorter than" means strictly under.** Exactly 100 m goes on to the length check. A test pins this.
- **Logging.** The short case is logged once, as "no note", with the trail part's metres, as every `TrackOnly` result is. A short trail part is a fallback to the track, so CLAUDE.md's logging rule applies.
- **A new test class** for fix 2, rather than growing `FindWayHomeUseCaseTest` (now 303 lines), per the modular-code skill.
- **The guard loop in fix 3** covers every other `RouteDataMissingReason`, not only the three the old test listed.
- **The comments** for L-389-4 and L-389-8 are worded as above.

## Addendum: Amendment 4 (RECORD -1600, option (b) replaces (c))

**This replaces Amendment 3's fix 2** and closes its open item 1, the looped walk that lost its shortcut. Fixes 1 and 3 of Amendment 3 stand unchanged.

**Base.** origin/pre-main was still `073fc77bd` (`git ls-remote`), already merged into `way-home-domain`, so no new merge was needed. I started from `89ae07b97`.

**Commits:**
- `65ebfda70`: the tests, failing first.
- `7e4dd4f31`: the rule.
- This section.

### The rule

- `WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS = 100.0` is provisional and named (`WayHome.kt`). Its doc gives the reason and the rejected alternative, option (c).
- The length check is now `trackBetween > 100 m && trailMeters > 1.5 × trackBetween` (`FindWayHomeUseCase.kt:97`). At or under 100 m walked between the two trail points, the check is skipped and the route is used as found. "Over 100 m" is read as strictly over.
- The rectangle check is unchanged and still runs first.
- `WAY_HOME_SHORT_TRAIL_METERS` and its `TrackOnly(null)` result are removed. No reference is left in `app/src`, checked by grep.
- `TrackOnly(null)`'s doc is back to its two cases from before Amendment 3.

### Tests

`FindWayHomeShortWalkTest` replaces `FindWayHomeShortTrailPartTest`. It has 4 tests, and each along-trails case also asserts that nothing was logged.
- **The false-warning case:** a walk crossing a trail, with 40 m walked between the near points and a trail part of 66.2 m (25 m of route plus about 41.2 m of joints). It gets the route, with a trail part of 66.2 m, and no "leaves your downloaded area" note.
- **The loop case:** the walk starts 190 m off the trail and crosses it at 100 m east. It loops about 2,040 m, crosses again at 160 m east, then walks 180 m south. It keeps the 80 m trail shortcut: 60 m of route plus two 10 m joints. Track back 180 m, track to start 180 m, total 440 m, not the loop.
- **Both sides of 100 m,** with a 200 m trail part on the trail and no joints: 98 m walked skips the check and gets the route; 102 m walked runs it and gets `LEAVES_AREA`.
- **A short route outside the rectangle** (98 m walked, one route point 20 km north) still gets `LEAVES_AREA`.

### Evidence

- **Failing first** (run 40082554090, on `65ebfda70`, throwaway branch `way-home-a4-failfirst`, filtered to `domain.wayhome.*`): zero `e:` lines, and the tests compiled. 29 tests ran and 3 failed, the predicted three:
  - The crossing and the loop: "along trails, not TrackOnly(note=null)". Their logs say "the trail part, 66 m / 80 m, is under 100.0 m", which is Amendment 3's rule.
  - The 98 m side: "along trails, not TrackOnly(note=LEAVES_AREA)", logged as "200 m, is more than 1.5 times … 98 m".
  - The rectangle test passed, as it should today. It is a guard.
- **Revert check 1** (`way-home-a4-revert-1`, run 40082620678): `if (true && trailMeters > …)`, so there is no floor. Zero `e:` lines. 29 tests, 2 failures, both `LEAVES_AREA` where a route was expected:
  - the crossing, logged as "66 m … 39 m";
  - the 98 m side.
  - The loop kept its shortcut, as it should, because its ratio passes.
- **Revert check 2** (`way-home-a4-revert-2`, run 40082622304): Amendment 3's rule put back as one line, `if (trailMeters < 100.0) return trackOnly(null, "short trail part")`. Zero `e:` lines. 29 tests, 2 failures, the crossing and the loop, each `TrackOnly(note=null)`. Their log reads "short trail part", a string that exists only in this revert, so the result is from this build.
- **How the revert branches were made.** This time I built them with git plumbing (a temporary index, `commit-tree`) from an edited copy in `/tmp/a4-work`, so the working tree was never edited. The forward file was saved to `/tmp/a4-save` first. Afterwards its SHA-1 matched the saved copy, `git status` was clean, and `HEAD` was `7e4dd4f31`. Each revert branch differs from `7e4dd4f31` by one line (`git diff --stat`). The branches are left on the remote.
- **Full suite** (#389, run 40083121709, on `7e4dd4f31`): `success`, every job, on the second attempt. 731 suites, 5,485 tests, 0 failures, 24 skipped, counted from the run's own test-result files; zero `e:` lines. That is the same count as Amendment 3: four tests replaced four. On the first attempt, only the Android lint job failed. It could not install Android SDK Build-Tools 36 ("Error reading Zip content from a SeekableByteChannel"), which is a download failure on the runner and has nothing to do with this change. I re-ran only that job, and it passed. No test or check was skipped.

### Confirmed vs inferred

**Confirmed:**
- each result above, from that run's own test-result files, with zero `e:` lines;
- that each failure is one its edit could cause;
- that the removed rule has no references left.

**Inferred:**
- That 100 m walked covers the false-warning cases on real walks. Walking beside a trail within 25 m for longer than about 100 m, with a trail part over 1.5× of it, could still trip the check. That would need an engine route much longer than the walker's track beside the same trail, which I take to be rare.

### Could not determine

- How often either case happens on real walks. No real walk has been run, and the S22 timing is still to do.

### Premises that were wrong

- None. The dispatch's description of the loop case and of the rule matched the code.

### Decided beyond scope

- "Over 100 m" is strict: exactly 100 m skips the check. The tests sit at 98 m and 102 m, not at exactly 100 m, because the walked metres come from geodesic distances, which are not exact.
- I renamed the test class to `FindWayHomeShortWalkTest`, since the old name described the removed rule.
- The loop test's walk starts off the network. That is the only shape in which the old rule bit, as Amendment 3's finding said.
